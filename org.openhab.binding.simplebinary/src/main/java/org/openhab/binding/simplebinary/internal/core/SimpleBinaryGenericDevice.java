/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.BufferUnderflowException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryDeviceState.DeviceStates;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generic device class
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimpleBinaryGenericDevice implements SimpleBinaryIDevice {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryGenericDevice.class);
    private static final String THING_HANDLER_THREADPOOL_NAME = "SimpleBinary";
    protected final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool(THING_HANDLER_THREADPOOL_NAME);

    /** device ID ex.: , COM1, /dev/ttyS1, 192.168.1.1, ... */
    protected final String deviceID;
    /** defines maximum resend count */
    public final int MAX_RESEND_COUNT = 2;

    /** item config */
    protected volatile SimpleBinaryDeviceCollection devices;
    protected ArrayList<@NonNull SimpleBinaryChannel> stateItems;
    protected ArrayList<@NonNull SimpleBinaryChannel> commandItems;

    /** flag that device is connected */
    private volatile boolean connected = false;

    protected synchronized void setConnected(boolean connected) {
        setConnected(connected, null);
    }

    protected synchronized void setConnected(boolean connected, String reason) {
        this.connected = connected;

        if (onChange != null) {
            try {
                onChange.onConnectionChanged(connected, reason);
            } catch (Exception ex) {
                logger.error("{} - ", this.toString(), ex);
            }
        }

        setStateToAllConfiguredDevices(DeviceStates.NOT_RESPONDING);
    }

    /**
     * Check if port is opened
     *
     * @return
     */
    public synchronized boolean isConnected() {
        return connected;
    }

    /** response timeout [ms] */
    protected final int timeout;
    /** poll rate */
    protected final int pollRate;
    /** no response count before device is mark as degraded */
    protected final int degradeMaxFailuresCount;
    /** time as degraded device */
    protected final int degradeTime;
    /** command discarded by offline device */
    protected final boolean discardCommand;
    /** execute() call time */
    private long lastExecTime = 0;
    /** Used pool control ex.: OnChange, OnScan */
    protected final SimpleBinaryPollControl pollControl;
    /** State of socket */
    public SimpleBinaryPortState portState = new SimpleBinaryPortState();

    public class ProcessDataResult {
        public static final int DATA_NOT_COMPLETED = -1;
        public static final int PROCESSING_ERROR = -2;
        public static final int INVALID_CRC = -3;
        public static final int BAD_CONFIG = -4;
        public static final int NO_VALID_ADDRESS = -5;
        public static final int NO_VALID_ADDRESS_REWIND = -6;
        public static final int UNKNOWN_MESSAGE = -7;
        public static final int UNKNOWN_MESSAGE_REWIND = -8;
    }

    protected final Charset charset;
    protected boolean disposed = false;

    private @Nullable ScheduledFuture<?> periodicJob = null;

    // protected final Lock processLock = new ReentrantLock();
    protected final Lock execLock = new ReentrantLock();

    AtomicLong readed = new AtomicLong(0);
    AtomicLong readedBytes = new AtomicLong(0);
    long metricsStart = 0;
    private final SimpleBinaryICommandAdded eventCommandAdded;

    /**
     * Constructor
     *
     * @param deviceName
     * @param simpleBinaryPoolControl
     * @param pollRate
     * @param charset
     * @param timeout
     * @param degradeMaxFailuresCount
     * @param degradeTime
     * @param discardCommand
     */
    public SimpleBinaryGenericDevice(String deviceID, SimpleBinaryPollControl simpleBinaryPoolControl, int pollRate,
            Charset charset, int timeout, int degradeMaxFailuresCount, int degradeTime, boolean discardCommand) {
        this.deviceID = deviceID;
        this.pollControl = simpleBinaryPoolControl;
        this.devices = new SimpleBinaryDeviceCollection();
        this.timeout = pollRate;
        this.pollRate = pollRate;
        this.degradeMaxFailuresCount = degradeMaxFailuresCount;
        this.degradeTime = degradeTime;
        this.discardCommand = discardCommand;

        this.charset = charset;

        this.eventCommandAdded = new SimpleBinaryICommandAdded() {
            @Override
            public void event(SimpleBinaryDevice device) {
                if (execLock.tryLock()) {
                    try {
                        // send commands
                        sendDeviceCommands(device);
                    } catch (Exception ex) {
                        logger.error("{} - eventCommandAdded error.", toString(), ex);
                    } finally {
                        execLock.unlock();
                    }
                }
            }
        };

        if (pollRate > 0) {
            periodicJob = scheduler.scheduleAtFixedRate(() -> {
                execute();
            }, 500, pollRate, TimeUnit.MILLISECONDS);
        } else {
            scheduler.execute(() -> {
                while (!disposed) {
                    execute();
                }
            });
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        logger.debug("{} - Disposing...", toString());
        close();

        if (periodicJob != null) {
            periodicJob.cancel(true);
            periodicJob = null;
            logger.debug("{} - Periodic job cancelled", toString());
        }
    }

    /**
     * Open
     *
     * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#open()
     */
    @Override
    public Boolean open() {
        logger.warn("{} - Opening... cannot open generic device", toString());

        return false;
    }

    /**
     * Close
     *
     * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#close()
     */
    @Override
    public void close() {
        logger.warn("{} - Closing... cannot close generic device", toString());

        setConnected(false, null);
    }

    /**
     * Called at specified period
     */
    private void execute() {
        logger.debug("{} - execute()", toString());
        if (!execLock.tryLock()) {
            logger.debug("{} - execute already locked...", toString());
            return;
        }

        try {
            long execTime = System.currentTimeMillis();
            // prevent too fast calling
            if (Math.abs(lastExecTime - execTime) < pollRate / 2) {
                return;
            }
            lastExecTime = execTime;
            // check device for new data
            checkNewData();
            // check device for timeout connection (if implemented)
            checkConnectionTimeout();
        } catch (Exception ex) {
            logger.error("{} - execute failure.", toString(), ex);
        } finally {
            execLock.unlock();
        }
    }

    @Override
    public void sendData(SimpleBinaryChannel channel, Command command) {
        channel.setCommand(command);

        if (logger.isDebugEnabled()) {
            logger.debug("{}: Adding command into queue", toString());
        }

        addCommand(channel);
    }

    /**
     * Add command into command queue
     *
     * @param data
     */
    private void addCommand(SimpleBinaryChannel data) {
        SimpleBinaryAddress addr = data.getCommandAddress();
        if (addr == null) {
            logger.debug("{} - command address not specified for channel ID={}", toString(), data.channelId);
            return;
        }
        if (!devices.containsKey(addr.getDeviceId())) {
            logger.debug("{} - Device {} does not exist in device list (ChannelID={}). Command is not send.",
                    toString(), addr.getDeviceId(), data.channelId);
            return;
        }
        devices.get(addr.getDeviceId()).addCommand(data);
        if (this.eventCommandAdded != null) {
            this.eventCommandAdded.event(devices.get(devices.get(addr.getDeviceId())));
        }
    }

    /**
     * Prepare request for read data of specific item
     *
     * @param itemConfig
     * @throws InterruptedException
     */
    private boolean sendReadData(SimpleBinaryChannel item) {
        if (!devices.containsKey(item.getCommandAddress().getDeviceId())) {
            logger.error("{} - No device for command channelId={}", this.toString(), item.channelId);
            return false;
        }

        SimpleBinaryItemData data = SimpleBinaryProtocol.compileReadDataFrame(item.getStateAddress());

        return sendWait(devices.get(item.getCommandAddress().getDeviceId()), data);
    }

    protected boolean canSend() {
        return true;
    }

    protected boolean canSend(int devId) {
        return true;
    }

    /**
     * Write data into device stream
     *
     * @param data
     *            Item data with compiled packet
     * @return
     *         Return true when data were sent
     */
    protected boolean sendDataOut(SimpleBinaryItemData data) {

        logger.warn("{} - Generic device can't send data", this.toString());

        return false;
    }

    /**
     * Print communication information
     *
     */
    protected void printCommunicationInfo(SimpleBinaryByteBuffer inBuffer, SimpleBinaryItemData lastSentData) {
        try {
            // content of input buffer
            int position = inBuffer.position();
            inBuffer.rewind();
            byte[] data = new byte[inBuffer.limit()];
            inBuffer.get(data);
            inBuffer.position(position);

            logger.info("{} - Data in input buffer: {}", toString(),
                    (data.length == 0) ? "empty" : SimpleBinaryProtocol.arrayToString(data, data.length));

            if (lastSentData != null) {
                // last data out
                logger.info("{} - Last sent data: {}", toString(),
                        SimpleBinaryProtocol.arrayToString(lastSentData.getData(), lastSentData.getData().length));
            }
        } catch (ModeChangeException e) {
            logger.error(e.getMessage());
        }
    }

    protected boolean sendWait(SimpleBinaryDevice device, SimpleBinaryItemData data) {
        device.receivedMessage = SimpleBinaryMessageType.UNKNOWN;
        if (!sendDataOut(data)) {
            if (device.unresponsive(degradeMaxFailuresCount)) {
                logger.info("{} - Device {} is set off-scan", toString(), data.getDeviceId());
            }
            return false;
        }
        // wait for answer
        synchronized (device) {
            try {
                device.wait(timeout);
            } catch (InterruptedException ex) {
                logger.debug("{} - device.wait() interrupted", toString());
                return false;
            } catch (Exception ex) {
                logger.error("{} - device.wait() error", toString(), ex);
                return false;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("{} - Device {} notify message {}", toString(), data.getDeviceId(), device.receivedMessage);
        }
        if (device.receivedMessage == SimpleBinaryMessageType.UNKNOWN) {
            if (device.unresponsive(degradeMaxFailuresCount)) {
                logger.info("{} - Device {} is set off-scan", toString(), data.getDeviceId());
            }
            return false;
        } else if (device.receivedMessage == SimpleBinaryMessageType.RESEND) {
            if (data.getResendCounter() < MAX_RESEND_COUNT) {
                data.incrementResendCounter();
                logger.debug("{} - Device {} - Resend data for {}. time", this.toString(), data.getDeviceId(),
                        data.getResendCounter());
                return sendWait(device, data);
            } else {
                logger.warn("{} - Device {} - Max resend attempts reached.", this.toString(), data.getDeviceId());
                // set state
                setDeviceState(data.getDeviceId(), DeviceStates.RESPONSE_ERROR);
                return false;
            }
        }
        return true;
    }

    /**
     * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#checkNewData()
     */
    @Override
    public void checkNewData() {
        if (!isConnected()) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} - checkNewData() in mode {} is called", toString(), pollControl);
        }

        if (pollControl == SimpleBinaryPollControl.ONSCAN) {
            for (SimpleBinaryChannel item : stateItems) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} - checkNewData() onscan channelId={}", toString(), item.channelId);
                }
                if (devices.containsKey(item.getCommandAddress().getDeviceId())) {
                    var device = devices.get(item.getCommandAddress().getDeviceId());
                    if (device.isDegraded()) {
                        if (device.stillDegraded(degradeTime)) {
                            logger.debug("{} - Device {} is off-scan. Skip...", toString(),
                                    item.getCommandAddress().getDeviceId());
                            continue;
                        } else {
                            logger.info("{} - Device {} is back in-scan", toString(),
                                    item.getCommandAddress().getDeviceId());
                        }
                    }
                }
                if (!sendReadData(item)) {
                    logger.debug("{} - Item can not be read.", toString());
                    continue;
                }
            }
            // send commands
            for (var device : devices.entrySet()) {
                sendDeviceCommands(device.getValue());
            }
        } else if (pollControl == SimpleBinaryPollControl.ONCHANGE) {
            // take every device and create for him command depend on his state
            for (var device : devices.entrySet()) {
                // not responding for defined times -> degrade device for defined time
                // still degrade (off-scan) -> get next
                if (device.getValue().isDegraded()) {
                    if (device.getValue().stillDegraded(degradeTime)) {
                        logger.debug("{} - Device {} is off-scan. Skip...", toString(), device.getKey());
                        continue;
                    } else {
                        logger.info("{} - Device {} is back in-scan", toString(), device.getKey());
                    }
                }
                if (!canSend(device.getKey())) {
                    logger.debug("{} - Device {} can not send data", toString(), device.getKey());
                    continue;
                }
                // not responding -> force
                DeviceStates state = device.getValue().getState().getState();
                boolean forceAllValues = state == DeviceStates.UNKNOWN || state == DeviceStates.NOT_RESPONDING
                        || state == DeviceStates.RESPONSE_ERROR;

                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Device {} force={}", toString(), device.getKey(), forceAllValues);
                }
                // send "new data"
                SimpleBinaryItemData data = SimpleBinaryProtocol.compileNewDataFrame(device.getKey(), forceAllValues);
                do {
                    if (!sendWait(device.getValue(), data)) {
                        break;
                    }

                    if (device.getValue().receivedMessage == SimpleBinaryMessageType.DATA) {
                        // if data income on request "check new data" send it again for new check
                        if (logger.isDebugEnabled()) {
                            logger.debug("{} - Device {} Repeat CHECKNEWDATA command", toString(), device.getKey());
                        }
                        // send new request immediately and without "force all data as new"
                        data = SimpleBinaryProtocol.compileNewDataFrame(device.getKey(), false);
                    }
                } while (device.getValue().receivedMessage == SimpleBinaryMessageType.DATA);

                if (device.getValue().receivedMessage == SimpleBinaryMessageType.UNKNOWN) {
                    continue;
                }
                // send commands
                sendDeviceCommands(device.getValue());
            }
        }

        long diff;
        if ((diff = (System.currentTimeMillis() - metricsStart)) >= 5000 || metricsStart == 0) {
            long requests = (long) Math.ceil(readed.get() * 1000.0 / diff);
            long bytes = (long) Math.ceil(readedBytes.get() * 1000.0 / diff);

            metricsStart = System.currentTimeMillis();
            readed.set(0);
            readedBytes.set(0);

            if (onUpdate != null) {
                onUpdate.onMetricsUpdated(requests, bytes);
            }
        }
    }

    /**
     * @param device
     * @return
     */
    protected boolean sendDeviceCommands(SimpleBinaryDevice device) {
        while (!device.getCommandQueue().isEmpty()) {
            var channel = device.getCommandQueue().peek();
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Device {} send command item {}", toString(),
                        channel.getCommandAddress().getDeviceId(), channel.getCommandAddress().getAddress());
            }
            SimpleBinaryItemData data;
            try {
                data = SimpleBinaryProtocol.compileDataFrame(channel, charset);
            } catch (Exception ex) {
                logger.error("{} - {}", this.toString(), ex.getMessage());
                continue;
            }

            if (!sendWait(device, data)) {
                return false;
            }
            device.getCommandQueue().poll();
        }
        return true;
    }

    protected int getDeviceID(SimpleBinaryByteBuffer inBuffer) {
        try {
            // flip buffer
            inBuffer.flip();

            int receivedID = inBuffer.get();
            inBuffer.rewind();

            return receivedID;

        } catch (ModeChangeException ex) {
            logger.error("{} - Bad operation: {}", this.toString(), ex.getMessage());
        } finally {
            try {
                inBuffer.compact();
            } catch (ModeChangeException ex) {
                logger.error("{} - Bad operation: {}", this.toString(), ex.getMessage());
            }
        }

        return -1;
    }

    protected boolean checkDeviceID(SimpleBinaryByteBuffer inBuffer, int expectedID) {
        return expectedID == getDeviceID(inBuffer);
    }

    protected SimpleBinaryMessage verifyDataOnly(SimpleBinaryByteBuffer inBuffer) {
        try {
            // flip buffer
            inBuffer.flip();

            if (logger.isDebugEnabled()) {
                logger.debug("{} - Verifying data, lenght={} bytes", toString(), inBuffer.limit());
            }

            inBuffer.rewind();
            // decompile income message
            SimpleBinaryMessage itemData = SimpleBinaryProtocol.decompileData(inBuffer, stateItems, true);

            // is decompiled
            if (itemData != null) {
                return itemData;
            }
        } catch (Exception ex) {
            logger.error("{} - Verify data error: {}", toString(), ex.getMessage());
        } finally {
            try {
                inBuffer.compact();

                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Verify data OK, lenght={} bytes", toString(), inBuffer.position());
                }
            } catch (ModeChangeException ex) {
                logger.error("{} - Bad operation: {}", this.toString(), ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Process incoming data
     *
     * @param inBuffer Buffer with receiver data
     * @param lastSentData Last data sent to device
     *
     * @return Return device ID or error code when lower than 0
     */
    protected int processData(SimpleBinaryByteBuffer inBuffer, SimpleBinaryItemData lastSentData) {
        return processData(inBuffer, lastSentData, null);
    }

    /**
     * Process incoming data
     *
     * @param inBuffer Buffer with receiver data
     * @param lastSentData Last data sent to device
     * @param forcedDeviceId Id that replace received one
     *
     * @return Return device ID or error code when lower than 0
     */
    protected int processData(SimpleBinaryByteBuffer inBuffer, SimpleBinaryItemData lastSentData, Byte forcedDeviceId) {
        int receivedID = 0;

        try {
            // flip buffer
            inBuffer.flip();

            if (logger.isDebugEnabled()) {
                logger.debug("{} - Reading input buffer, lenght={} bytes. Thread={}", toString(), inBuffer.limit(),
                        Thread.currentThread().getId());
            }

            if (inBuffer.limit() == 0) {
                return ProcessDataResult.DATA_NOT_COMPLETED;
            }

            int datasize = inBuffer.remaining();
            receivedID = inBuffer.get();
            inBuffer.rewind();
            // decompile income message
            SimpleBinaryMessage itemData = SimpleBinaryProtocol.decompileData(inBuffer, stateItems, forcedDeviceId);

            // is decompiled
            if (itemData != null) {
                // process data
                SimpleBinaryMessageType mt = processDecompiledData(itemData, lastSentData);

                readed.incrementAndGet();
                readedBytes.addAndGet(datasize - inBuffer.remaining());

                // compact buffer
                inBuffer.compact();

                if (devices.containsKey(receivedID)) {
                    synchronized (devices.get(receivedID)) {
                        devices.get(receivedID).receivedMessage = mt;
                        devices.get(receivedID).notify();
                    }
                }

                return receivedID;
            } else {
                logger.debug("{} - Not whole packet. Waiting...", toString());
                // rewind at start position (wait for next bytes)
                inBuffer.rewind();
                // compact buffer
                inBuffer.compact();

                return ProcessDataResult.DATA_NOT_COMPLETED;
            }
        } catch (BufferUnderflowException ex) {
            logger.warn("{} - Buffer underflow while reading", toString());
            // print details
            printCommunicationInfo(inBuffer, lastSentData);

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            logger.warn("Underflow stacktrace: {}", sw.toString());

            logger.warn("Buffer mode {}, remaining={} bytes, limit={} bytes", inBuffer.getMode().toString(),
                    inBuffer.remaining(), inBuffer.limit());

            try {
                // rewind buffer
                inBuffer.rewind();
                logger.warn("Buffer mode {}, remaining after rewind {} bytes", inBuffer.getMode().toString(),
                        inBuffer.remaining());
                // compact buffer
                inBuffer.compact();
            } catch (ModeChangeException exep) {
                logger.error(exep.getMessage());
            }

            return ProcessDataResult.PROCESSING_ERROR;

        } catch (NoValidCRCException ex) {
            logger.error("{} - ", this.toString(), ex.getMessage());
            // print details
            printCommunicationInfo(inBuffer, lastSentData);
            // compact buffer
            try {
                inBuffer.compact();
            } catch (ModeChangeException e) {
                logger.error(e.getMessage());
            }

            // set state
            setDeviceState(receivedID, DeviceStates.RESPONSE_ERROR);

            if (devices.containsKey(receivedID)) {
                synchronized (devices.get(receivedID)) {
                    devices.get(receivedID).receivedMessage = SimpleBinaryMessageType.RESEND;
                    devices.get(receivedID).notify();
                }
            }

            return ProcessDataResult.INVALID_CRC;

        } catch (NoValidItemInConfig ex) {
            logger.info("{} - {}", this.toString(), ex.getMessage());
            // print details
            printCommunicationInfo(inBuffer, lastSentData);
            // compact buffer
            try {
                inBuffer.compact();
            } catch (ModeChangeException e) {
                logger.error(e.getMessage());
            }

            if (devices.containsKey(receivedID)) {
                synchronized (devices.get(receivedID)) {
                    devices.get(receivedID).receivedMessage = SimpleBinaryMessageType.DATA;
                    devices.get(receivedID).notify();
                }
            }

            return ProcessDataResult.BAD_CONFIG;

        } catch (UnknownMessageException ex) {
            logger.error("{} - Income unknown message: {}", this.toString(), ex.getMessage());
            // print details
            printCommunicationInfo(inBuffer, lastSentData);

            // only 4 bytes (minus 1 after delete first byte) is not enough to have complete message
            if (inBuffer.limit() < 5) {
                // clear buffer
                inBuffer.clear();

                logger.warn("{} - Income unknown message: input buffer cleared", this.toString());

                // set state
                setDeviceState(receivedID, DeviceStates.DATA_ERROR);

                return ProcessDataResult.UNKNOWN_MESSAGE;
            } else {
                logger.warn("{} - Income unknown message : {} bytes in buffer. Triyng to find correct message. ",
                        this.toString(), inBuffer.limit());
                try {
                    // delete first byte
                    inBuffer.rewind();
                    inBuffer.get();
                    inBuffer.compact();
                } catch (ModeChangeException e) {
                    logger.error(e.getMessage());
                }

                return ProcessDataResult.UNKNOWN_MESSAGE_REWIND;
            }

        } catch (ModeChangeException ex) {
            logger.error("{} - Bad operation: {}. Thread={}", this.toString(), ex.getMessage(),
                    Thread.currentThread().getId());

            inBuffer.initialize();

            // set state
            setDeviceState(receivedID, DeviceStates.DATA_ERROR);

            return ProcessDataResult.PROCESSING_ERROR;

        } catch (Exception ex) {
            logger.error(String.format("%s - Reading incoming data error: ", this.toString()), ex);
            // print details
            printCommunicationInfo(inBuffer, lastSentData);
            inBuffer.clear();

            // set state
            setDeviceState(receivedID, DeviceStates.DATA_ERROR);

            return ProcessDataResult.PROCESSING_ERROR;
        }
    }

    /**
     *
     * @param itemData
     * @param lastSentData
     * @return
     * @throws Exception
     */
    protected SimpleBinaryMessageType processDecompiledData(SimpleBinaryMessage itemData,
            SimpleBinaryItemData lastSentData) throws Exception {
        int deviceId = itemData.getDeviceId();
        // get device state
        DeviceStates devstate = devices.getDeviceState(deviceId);
        if (devstate == DeviceStates.UNKNOWN || devstate == DeviceStates.NOT_RESPONDING
                || devstate == DeviceStates.RESPONSE_ERROR || itemData.getMessageType() == SimpleBinaryMessageType.HI
                || itemData.getMessageType() == SimpleBinaryMessageType.WANT_EVERYTHING) {
            sendAllItemsCommands();
        }
        // set state
        setDeviceState(deviceId, DeviceStates.CONNECTED);
        // message type control
        if (itemData instanceof SimpleBinaryItem) {
            /*
             * if (logger.isTraceEnabled()) {
             * logger.trace("{} - Incoming data", toString());
             * }
             */

            try {
                State state = ((SimpleBinaryItem) itemData).getState();

                if (state == null) {
                    logger.warn("{} - Device {} Incoming data - Unknown item state", toString(), deviceId);
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} - Device {} Incoming data - channel:{}/state:{}", toString(), deviceId,
                                ((SimpleBinaryItem) itemData).getConfig().channelId, state);
                    }

                    ((SimpleBinaryItem) itemData).getConfig().setState(state);
                }
            } catch (Exception ex) {
                logger.error("{} - {}", this.toString(), ex.toString());
            }
        } else if (itemData instanceof SimpleBinaryMessage) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Device {} Incoming control message", toString(), itemData.getDeviceId());
            }

            if (logger.isDebugEnabled() && lastSentData != null && lastSentData.getItemAddress() >= 0) {
                logger.debug("{} - Device {} ItemAddress={}", toString(), itemData.getDeviceId(),
                        lastSentData.getItemAddress());
            }

            if (itemData.getMessageType() == SimpleBinaryMessageType.OK) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Device {} for item {} report data OK", toString(), itemData.getDeviceId(),
                            (lastSentData != null && lastSentData.getItemAddress() >= 0) ? lastSentData.getItemAddress()
                                    : "???");
                }
            } else if (itemData.getMessageType() == SimpleBinaryMessageType.RESEND) {
                if (lastSentData != null) {
                    logger.info("{} - Device {} for item {} request resend data", toString(), itemData.getDeviceId(),
                            lastSentData.getItemAddress());
                } else {
                    logger.warn("{} - Device {} request resend data. But nothing to resend.", toString(),
                            itemData.getDeviceId());
                }
            } else if (itemData.getMessageType() == SimpleBinaryMessageType.NODATA) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Device {} answer no new data", toString(), itemData.getDeviceId());
                }
            } else if (itemData.getMessageType() == SimpleBinaryMessageType.UNKNOWN_DATA) {
                logger.warn("{} - Device {} report unknown data", toString(), itemData.getDeviceId());
                // last data out
                if (lastSentData != null) {
                    logger.info("{} - Last sent data: {}", toString(),
                            SimpleBinaryProtocol.arrayToString(lastSentData.getData(), lastSentData.getData().length));
                }
                // set state
                setDeviceState(itemData.getDeviceId(), DeviceStates.DATA_ERROR);
            } else if (itemData.getMessageType() == SimpleBinaryMessageType.UNKNOWN_ADDRESS) {
                logger.warn("{} - Device {} for item {} report unknown address", toString(), itemData.getDeviceId(),
                        (lastSentData != null && lastSentData.getItemAddress() >= 0) ? lastSentData.getItemAddress()
                                : "???");
                // last data out
                if (lastSentData != null) {
                    logger.info("{} - Last sent data: {}", toString(),
                            SimpleBinaryProtocol.arrayToString(lastSentData.getData(), lastSentData.getData().length));
                }
                // set state
                setDeviceState(itemData.getDeviceId(), DeviceStates.DATA_ERROR);
            } else if (itemData.getMessageType() == SimpleBinaryMessageType.SAVING_ERROR) {
                logger.warn("{} - Device {} for item {} report saving data error", toString(), itemData.getDeviceId(),
                        (lastSentData != null && lastSentData.getItemAddress() >= 0) ? lastSentData.getItemAddress()
                                : "???");
                // last data out
                if (lastSentData != null) {
                    logger.info("{} - Last sent data: {}", toString(),
                            SimpleBinaryProtocol.arrayToString(lastSentData.getData(), lastSentData.getData().length));
                }
                // set state
                setDeviceState(itemData.getDeviceId(), DeviceStates.DATA_ERROR);
            } else if (itemData.getMessageType() == SimpleBinaryMessageType.HI) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Device {} says Hi", toString(), itemData.getDeviceId());
                }
            } else {
                logger.warn("{} - Device {} - Unsupported message type received: {}", toString(),
                        itemData.getDeviceId(), itemData.getMessageType().toString());

                // set state
                setDeviceState(itemData.getDeviceId(), DeviceStates.DATA_ERROR);
            }
        }

        return itemData.getMessageType();
    }

    /**
     * For device items provide send command to device
     */
    void sendAllItemsCommands() {
        if (commandItems == null) {
            return;
        }

        for (var item : commandItems) {
            if (item.getCommand() == null || item.getCommandAddress() == null) {
                continue;
            }

            logger.debug("{} - sendAllItemsCommands() {}/{}", toString(), item.getCommandAddress().getDeviceId(),
                    item.getCommandAddress().getAddress());
            // add command into device queue
            addCommand(item);
        }
    }

    /**
     * Check for device connection timeout
     */
    public void checkConnectionTimeout() {
    }

    @Override
    public String toString() {
        return "DeviceID " + deviceID;
    }

    private ConnectionChanged onChange = null;

    @Override
    public void onConnectionChanged(ConnectionChanged onChangeMethod) {
        onChange = onChangeMethod;
    }

    private MetricsUpdated onUpdate = null;

    @Override
    public void onMetricsUpdated(MetricsUpdated onUpdateMethod) {
        onUpdate = onUpdateMethod;
    }

    private DeviceStateUpdated onDeviceState = null;

    @Override
    public void onDeviceStateUpdated(DeviceStateUpdated onUpdateMethod) {
        onDeviceState = onUpdateMethod;
    }

    protected void setDeviceState(int deviceId, SimpleBinaryDeviceState.DeviceStates state) {
        if (!devices.setDeviceState(deviceId, state) && state != SimpleBinaryDeviceState.DeviceStates.CONNECTED) {
            return;
        }
        if (onDeviceState != null) {
            onDeviceState.onDeviceStateUpdated(deviceId, devices.get(deviceId).getState());
        }
    }

    protected void setStateToAllConfiguredDevices(SimpleBinaryDeviceState.DeviceStates state) {
        devices.setStateToAllConfiguredDevices(state);

        for (var d : devices.entrySet()) {
            if (onDeviceState != null) {
                onDeviceState.onDeviceStateUpdated(d.getKey(), d.getValue().getState());
            }
        }
    }

    @Override
    public void setDataAreas(@NonNull SimpleBinaryDeviceCollection devices,
            @NonNull ArrayList<@NonNull SimpleBinaryChannel> stateItems,
            @NonNull ArrayList<@NonNull SimpleBinaryChannel> commandItems) {
        this.devices = devices;
        this.stateItems = stateItems;
        this.commandItems = commandItems;
    }

    public interface SimpleBinaryICommandAdded {
        void event(SimpleBinaryDevice device);
    }
}
