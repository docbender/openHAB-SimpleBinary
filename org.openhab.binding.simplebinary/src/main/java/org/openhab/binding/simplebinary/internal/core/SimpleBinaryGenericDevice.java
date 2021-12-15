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
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
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
    protected volatile SimpleBinaryDeviceStateCollection devices;
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

    /** queue for commands */
    protected final ConcurrentLinkedDeque<SimpleBinaryItemData> commandQueue = new ConcurrentLinkedDeque<SimpleBinaryItemData>();
    /** Used pool control ex.: OnChange, OnScan */
    protected final SimpleBinaryPoolControl poolControl;
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

    protected final Lock processLock = new ReentrantLock();

    AtomicLong readed = new AtomicLong(0);
    AtomicLong readedBytes = new AtomicLong(0);
    long metricsStart = 0;

    /**
     * Constructor
     *
     * @param deviceName
     * @param simpleBinaryPoolControl
     */
    public SimpleBinaryGenericDevice(String deviceID, SimpleBinaryPoolControl simpleBinaryPoolControl, int pollRate,
            Charset charset) {
        this.deviceID = deviceID;
        this.poolControl = simpleBinaryPoolControl;
        this.devices = new SimpleBinaryDeviceStateCollection();

        this.charset = charset;
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
    protected void execute() {
        // check device for new data
        checkNewData();
        // check device for timeout connection (if implemented)
        checkConnectionTimeout();
    }

    @Override
    public void sendData(SimpleBinaryChannel channel, Command command) {
        channel.setCommand(command);
        try {
            SimpleBinaryItem data = SimpleBinaryProtocol.compileDataFrame(channel, command, charset);

            sendData(data);
        } catch (Exception ex) {
            logger.error("{} - sendData failure.", toString(), ex);
        }
    }

    /**
     * Add compiled data item to sending queue
     *
     * @param data
     */
    public void sendData(SimpleBinaryItemData data) {
        if (data == null) {
            logger.warn("{}: Nothing to send. Empty data", toString());
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{}: Adding command into queue", toString());
        }

        // check if item already exist in queue
        if (matchInQueueTarget(data, true)) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - sendData() - allready in queue. Replaced by new one.", toString());
            }
        }
        // add data
        commandQueue.add(data);

        processCommandQueue();
    }

    /**
     * Add compiled data item to sending queue at first place
     *
     * @param data
     * @throws InterruptedException
     */
    public void sendDataPriority(SimpleBinaryItemData data) throws InterruptedException {
        if (data != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("{}: Adding priority command into queue", toString());
            }

            // add data
            commandQueue.addFirst(data);

            processCommandQueue();
        } else {
            logger.warn("{}: Nothing to send. Empty data", toString());
        }
    }

    /**
     * Prepare request to check if device with specific address has new data
     *
     * @param deviceAddress Device address
     * @param forceAllDataAsNew Flag to force send all data from slave
     * @throws InterruptedException
     */
    protected void offerNewDataCheck(int deviceAddress, boolean forceAllDataAsNew) throws InterruptedException {
        SimpleBinaryItemData data = SimpleBinaryProtocol.compileNewDataFrame(deviceAddress, forceAllDataAsNew);

        if (logger.isDebugEnabled()) {
            logger.debug("{} - offerNewDataCheck() device={} force={}", toString(), deviceAddress, forceAllDataAsNew);
        }
        // check if packet already exist
        if (!matchInQueueDataPacket(data)) {
            commandQueue.addLast(data);
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - offerNewDataCheck() - allready in queue", toString());
            }
        }
    }

    /**
     * Put "check new data" packet of specified device in front of command queue
     *
     * @param deviceAddress Device address
     * @throws InterruptedException
     */
    protected void offerNewDataCheckPriority(int deviceAddress) throws InterruptedException {
        offerNewDataCheckPriority(deviceAddress, false);
    }

    /**
     * Put "check new data" packet of specified device in front of command queue
     *
     * @param deviceAddress Device address
     * @param forceAllDataAsNew Flag to force send all data from slave
     * @throws InterruptedException
     */
    protected void offerNewDataCheckPriority(int deviceAddress, boolean forceAllDataAsNew) throws InterruptedException {
        SimpleBinaryItemData data = SimpleBinaryProtocol.compileNewDataFrame(deviceAddress, forceAllDataAsNew);

        // is already there?
        if (!commandQueue.isEmpty()) {
            var first = commandQueue.getFirst().getData();
            if (first.length == 4) {
                boolean match = true;
                for (int i = 0; i < 4; i++) {
                    if (first[i] != data.getData()[i]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return;
                }
            }
            // is there "check" without force
            for (SimpleBinaryItemData qitem : commandQueue) {
                if (qitem.getData().length != 4) {
                    continue;
                }
                // remove it if match
                if (qitem.getData()[0] == data.getData()[0] && qitem.getData()[1] == (byte) 0xD0) {
                    commandQueue.remove(qitem);
                    break;
                }
            }
        }
        commandQueue.addFirst(data);
    }

    /**
     * Put data into command queue
     *
     * @param deviceAddress Device address
     * @param data Data
     * @throws InterruptedException
     */
    protected void offerDataPriority(SimpleBinaryItemData data) throws InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - offerData() device={}", toString(), data.deviceId);
        }

        commandQueue.addFirst(data);
    }

    /**
     * Put data into command queue
     *
     * @param deviceAddress Device address
     * @param data Data
     * @throws InterruptedException
     */
    protected void offerData(SimpleBinaryItemData data) throws InterruptedException {
        if (logger.isDebugEnabled()) {
            logger.debug("{} - offerData() device={}", toString(), data.deviceId);
        }
        // check if item already exist in queue
        if (matchInQueueTarget(data, true)) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - offerData() - allready in queue. Replaced by new one.", toString());
            }
        }
        commandQueue.addLast(data);
    }

    /**
     * Prepare request for read data of specific item
     *
     * @param itemConfig
     * @throws InterruptedException
     */
    protected void sendReadData(SimpleBinaryChannel item) {
        SimpleBinaryItemData data = SimpleBinaryProtocol.compileReadDataFrame(item.getStateAddress());

        // check if packet already exist
        if (!matchInQueueDataPacket(data)) {
            commandQueue.addLast(data);
        }
    }

    /**
     * Check if data packet is not already in queue
     *
     * @param item
     * @return same packet found
     */
    protected boolean matchInQueueDataPacket(SimpleBinaryItemData item) {
        if (commandQueue.isEmpty()) {
            return false;
        }

        for (SimpleBinaryItemData qitem : commandQueue) {
            if (qitem.getData().length != item.getData().length) {
                continue;
            }

            for (int i = 0; i < qitem.getData().length; i++) {
                if (qitem.getData()[i] != item.getData()[i]) {
                    break;
                }

                if (i == qitem.getData().length - 1) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if item for target device is not already in queue
     *
     * @param item
     * @return true if item was found
     */
    protected boolean matchInQueueTarget(SimpleBinaryItemData item, Boolean remove) {
        if (commandQueue.isEmpty()) {
            return false;
        }

        for (SimpleBinaryItemData qitem : commandQueue) {
            if (qitem.deviceId == item.deviceId && qitem.itemAddress == item.itemAddress) {
                if (remove) {
                    commandQueue.remove(qitem);
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Check if queue has data for specified device and send it
     *
     */
    protected void processCommandQueue(int thisDeviceOnly) {
        if (!isConnected() || disposed) {
            return;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("{} - Processing commandQueue - length {}. Only device {}. Thread={}", toString(),
                    commandQueue.size(), thisDeviceOnly, Thread.currentThread().getId());
        }
        // queue is empty -> exit
        if (commandQueue.isEmpty()) {
            return;
        }
        // no reply expected - no command processed
        if (!canSend(thisDeviceOnly) || !processLock.tryLock()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Processing commandQueue - waiting", this.toString());
            }
            return;
        }

        try {
            SimpleBinaryItemData dataToSend = null;

            for (SimpleBinaryItemData i : commandQueue) {
                if (i.deviceId == thisDeviceOnly) {
                    commandQueue.removeFirstOccurrence(i);
                    dataToSend = i;
                    break;
                }
            }
            if (dataToSend != null) {
                sendDataOut(dataToSend);
            }
        } catch (Exception e) {
            logger.error("{} - Processing commandQueue(int) - error: {}", this.toString(), e.getMessage());
        } finally {
            processLock.unlock();
        }
    }

    /**
     * Check if queue is not empty and send data to device
     *
     */
    protected void processCommandQueue() {
        if (!isConnected() || disposed) {
            return;
        }
        if (logger.isDebugEnabled()) {
            // if (logger.isDebugEnabled() || this instanceof SimpleBinaryIP) {
            logger.debug("{} - Processing commandQueue - length {}. Thread={}", toString(), commandQueue.size(),
                    Thread.currentThread().getId());
        }
        // queue is empty -> exit
        if (commandQueue.isEmpty()) {
            return;
        }
        // no reply expected or already in process
        if (!canSend() || !processLock.tryLock()) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Processing commandQueue - waiting", this.toString());
            }
            return;
        }

        try {
            SimpleBinaryItemData dataToSend = null;

            // check first command in queue
            SimpleBinaryItemData firstdata = commandQueue.pollFirst();
            if (firstdata == null) {
                return;
            }

            // state of command device
            DeviceStates state = devices.getDeviceState(firstdata.deviceId);

            if (logger.isDebugEnabled()) {
                logger.debug("{} - Processing commandQueue - first command deviceID={}", this.toString(),
                        firstdata.deviceId);
            }

            // check if device responds and there is lot of commands
            if (state != DeviceStates.NOT_RESPONDING && canSend(firstdata.deviceId)) {
                dataToSend = firstdata;
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "{} - Processing commandQueue - deviceID={} - device responding. Command will be sent.",
                            this.toString(), firstdata.deviceId);
                }
                // no other element
            } else if (commandQueue.isEmpty()) {
                if (canSend(firstdata.deviceId)) {
                    dataToSend = firstdata;
                } else {
                    commandQueue.addFirst(firstdata);
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} - Processing commandQueue - deviceID={} - cannot send data to device.",
                                this.toString(), firstdata.deviceId);
                    }
                }
                // queue not empty
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug(
                            "{} - Processing commandQueue - deviceID={} - device NOT responding. Command queue will be reordered.",
                            this.toString(), firstdata.deviceId);

                    String commands = firstdata.deviceId + "/" + Integer.toHexString(firstdata.messageId & 0xFF);
                    for (SimpleBinaryItemData i : commandQueue) {
                        commands += ";" + i.deviceId + "/" + Integer.toHexString(i.messageId & 0xFF);
                    }

                    logger.debug("{} - Processing commandQueue - before reorder queue(deviceID/messageID)={}",
                            this.toString(), commands);
                }
                // reorder queue - all commands from dead device put at the end of the queue
                List<SimpleBinaryItemData> deadData = new ArrayList<SimpleBinaryItemData>();
                List<SimpleBinaryItemData> otherData = new ArrayList<SimpleBinaryItemData>();

                SimpleBinaryItemData data = firstdata;
                deadData.add(data);

                // over all items until item isn't same as first one
                do {
                    data = commandQueue.poll();

                    if (data == null) {
                        break;
                    }

                    if (data.deviceId == firstdata.deviceId || !canSend(data.deviceId)) {
                        deadData.add(data);
                    } else {
                        otherData.add(data);
                        // commandQueue.offer(data);
                    }

                } while (!commandQueue.isEmpty());

                // TODO: at begin put "check new data"??? - but only if ONCHANGE. What if ONSCAN???

                if (logger.isDebugEnabled()) {
                    logger.debug("{} - Processing commandQueue - otherData.size()={},deadData.size()={}",
                            this.toString(), otherData.size(), deadData.size());
                }

                // first add commands from non dead device
                commandQueue.addAll(otherData);
                // add dead device data
                commandQueue.addAll(deadData);

                /*
                 * if (logger.isTraceEnabled()) {
                 * String commands = "";
                 * for (SimpleBinaryItemData i : commandQueue) {
                 * if (!commands.isEmpty()) {
                 * commands += ";";
                 * }
                 * commands += i.deviceId + "/" + Integer.toHexString(i.messageId & 0xFF);
                 * }
                 *
                 * logger.trace("{} - Processing commandQueue - after reorder queue(deviceID/messageID)={}",
                 * this.toString(), commands);
                 * }
                 */

                // put first command (no matter if device not responding)
                dataToSend = commandQueue.poll();

                if (dataToSend == null) {
                    return;
                }

                if (canSend(dataToSend.deviceId)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(
                                "{} - Processing commandQueue - after reorder first command will be sent. DeviceID={}",
                                this.toString(), dataToSend.deviceId);
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        // if (logger.isDebugEnabled() || this instanceof SimpleBinaryIP) {
                        logger.debug("{} - Processing commandQueue - cannot send data to deviceID={}", this.toString(),
                                dataToSend.deviceId);
                    }

                    commandQueue.addFirst(dataToSend);

                    return;
                }
            }

            if (dataToSend != null) {
                sendDataOut(dataToSend);
            }
        } catch (Exception e) {
            logger.error("{} - Processing commandQueue - error: {}", this.toString(), e.toString());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            logger.error("Stacktrace: {}", sw.toString());
        } finally {
            processLock.unlock();
        }
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
     * Resend last sent data
     *
     * @throws InterruptedException
     */
    protected void resendData(SimpleBinaryItemData lastData) throws InterruptedException {
        if (lastData != null) {
            if (lastData.getResendCounter() < MAX_RESEND_COUNT) {
                lastData.incrementResendCounter();
                sendDataPriority(lastData);
            } else {
                logger.warn("{} - Device {} - Max resend attempts reached.", this.toString(), lastData.getDeviceId());
                // set state
                setDeviceState(lastData.getDeviceId(), DeviceStates.RESPONSE_ERROR);
            }
        }
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

    /**
     * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#checkNewData()
     */
    @Override
    public void checkNewData() {
        if (!isConnected()) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} - checkNewData() in mode {} is called", toString(), poolControl);
        }

        if (poolControl == SimpleBinaryPoolControl.ONSCAN) {
            for (SimpleBinaryChannel item : stateItems) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} - checkNewData() channelId={}", toString(), item.channelId);
                }
                try {
                    this.sendReadData(item);
                } catch (Exception e) {
                    logger.error("{} - checkNewData() error: {} ", toString(), e.getMessage());
                }
            }
        } else if (poolControl == SimpleBinaryPoolControl.ONCHANGE) {
            // take every device and create for him command depend on his state
            for (var device : devices.entrySet()) {
                boolean forceAllValues = device.getValue().getState() == DeviceStates.UNKNOWN
                        || device.getValue().getState() == DeviceStates.NOT_RESPONDING
                        || device.getValue().getState() == DeviceStates.RESPONSE_ERROR;

                if (logger.isDebugEnabled()) {
                    logger.debug("{} - checkNewData() device={} force={}", toString(), device.getKey(), forceAllValues);
                }

                try {
                    if (forceAllValues) {
                        this.offerNewDataCheckPriority(device.getKey(), forceAllValues);
                    } else {
                        this.offerNewDataCheck(device.getKey(), forceAllValues);
                    }
                } catch (Exception e) {
                    logger.error("{} - checkNewData() error: {} ", toString(), e.getMessage());
                }
            }
        }

        processCommandQueue();

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
                processDecompiledData(itemData, lastSentData);

                readed.incrementAndGet();
                readedBytes.addAndGet(datasize - inBuffer.remaining());

                // compact buffer
                inBuffer.compact();

                return receivedID;
            } else {
                logger.warn("{} - Cannot decompile incoming data", toString());
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

            try {
                resendData(lastSentData);
            } catch (InterruptedException e) {
                logger.error(e.getMessage());
            }

            return ProcessDataResult.INVALID_CRC;

        } catch (NoValidItemInConfig ex) {
            logger.error("{} - {}", this.toString(), ex.getMessage());
            // print details
            printCommunicationInfo(inBuffer, lastSentData);
            // compact buffer
            try {
                inBuffer.compact();
            } catch (ModeChangeException e) {
                logger.error(e.getMessage());
            }

            // set state
            setDeviceState(receivedID, DeviceStates.DATA_ERROR);

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
     * @param itemData
     * @throws Exception
     *
     */
    protected void processDecompiledData(SimpleBinaryMessage itemData, SimpleBinaryItemData lastSentData)
            throws Exception {
        // message type control
        if (itemData instanceof SimpleBinaryItem) {
            /*
             * if (logger.isTraceEnabled()) {
             * logger.trace("{} - Incoming data", toString());
             * }
             */

            int deviceId = ((SimpleBinaryItem) itemData).getDeviceId();

            // get device state
            DeviceStates devstate = devices.getDeviceState(deviceId);
            if (devstate == DeviceStates.UNKNOWN || devstate == DeviceStates.NOT_RESPONDING
                    || devstate == DeviceStates.RESPONSE_ERROR) {
                sendAllItemsCommands();
            }
            // set state
            setDeviceState(deviceId, DeviceStates.CONNECTED);

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

                    /*
                     * // send data to other binded devices
                     * if (((SimpleBinaryItem) itemData).getConfig().devices.size() > 1) {
                     * logger.debug("{} - Resend received data to other devices(count={})", toString(),
                     * ((SimpleBinaryItem) itemData).getConfig().devices.size());
                     * SimpleBinaryBindingConfig cfg = ((SimpleBinaryItem) itemData).getConfig();
                     * for (DeviceConfig d : cfg.devices) {
                     * if (d.dataDirection != DataDirectionFlow.INPUT
                     * && (d.getDeviceAddress() != deviceId || !d.getPortName().equals(this.deviceName))) {
                     * logger.debug("{} - Resend to device {}", toString(), d);
                     * SimpleBinaryGenericDevice device = this.configuredDevices.get(d.deviceName);
                     * try {
                     * device.sendData(cfg.item.getName(), state, cfg, d);
                     * } catch (Exception ex) {
                     * logger.error(
                     * "{} - Resend received data to other devices failed: line:{}|method:{}|message:{}",
                     * toString(), ex.getStackTrace()[0].getLineNumber(),
                     * ex.getStackTrace()[0].getMethodName(), ex.toString());
                     *
                     * StringWriter sw = new StringWriter();
                     * PrintWriter pw = new PrintWriter(sw);
                     * ex.printStackTrace(pw);
                     * logger.error(sw.toString());
                     * }
                     * }
                     * }
                     * }
                     */
                }
            } catch (Exception ex) {
                logger.error("{} - {}", this.toString(), ex.getStackTrace()[0].getLineNumber(), ex);
            }

            if (lastSentData != null) {
                // if data income on request "check new data" send it again for new check
                if (lastSentData.getMessageType() == SimpleBinaryMessageType.CHECKNEWDATA) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} - Device {} Repeat CHECKNEWDATA command", toString(), deviceId);
                    }
                    // send new request immediately and without "force all data as new"
                    offerNewDataCheckPriority(lastSentData.deviceId);
                }
            }
        } else if (itemData instanceof SimpleBinaryMessage) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Device {} Incoming control message", toString(), itemData.getDeviceId());
            }

            if (logger.isDebugEnabled() && lastSentData != null && lastSentData.getItemAddress() >= 0) {
                logger.debug("{} - Device {} ItemAddress={}", toString(), itemData.getDeviceId(),
                        lastSentData.getItemAddress());
            }

            // set state
            setDeviceState(itemData.getDeviceId(), DeviceStates.CONNECTED);

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

                    resendData(lastSentData);
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

            // get device state
            DeviceStates devstate = devices.getDeviceState(itemData.getDeviceId());
            if (devstate == DeviceStates.UNKNOWN || devstate == DeviceStates.NOT_RESPONDING
                    || devstate == DeviceStates.RESPONSE_ERROR
                    || itemData.getMessageType() == SimpleBinaryMessageType.HI
                    || itemData.getMessageType() == SimpleBinaryMessageType.WANT_EVERYTHING) {
                sendAllItemsCommands();
            }
        }
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

            try {
                offerData(SimpleBinaryProtocol.compileDataFrame(item, item.getCommand(), charset));
            } catch (Exception ex) {
                logger.error("{} - sendAllItemsCommands(): line:{}|method:{}", toString(),
                        ex.getStackTrace()[0].getLineNumber(), ex.getStackTrace()[0].getMethodName());
            }
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
            onDeviceState.onDeviceStateUpdated(deviceId, devices.get(deviceId));
        }
    }

    protected void setStateToAllConfiguredDevices(SimpleBinaryDeviceState.DeviceStates state) {
        devices.setStateToAllConfiguredDevices(state);

        if (onDeviceState != null) {
            for (var d : devices.entrySet()) {
                onDeviceState.onDeviceStateUpdated(d.getKey(), d.getValue());
            }
        }
    }

    @Override
    public void setDataAreas(@NonNull ArrayList<Integer> devices,
            @NonNull ArrayList<@NonNull SimpleBinaryChannel> stateItems,
            @NonNull ArrayList<@NonNull SimpleBinaryChannel> commandItems) {
        this.devices.clear();
        this.devices.put(devices);
        this.stateItems = stateItems;
        this.commandItems = commandItems;
    }
}
