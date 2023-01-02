/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.openhab.binding.simplebinary.internal.core.SimpleBinaryDeviceState.DeviceStates;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryPortState.PortStates;
import org.openhab.core.io.transport.serial.PortInUseException;
import org.openhab.core.io.transport.serial.SerialPort;
import org.openhab.core.io.transport.serial.SerialPortEvent;
import org.openhab.core.io.transport.serial.SerialPortEventListener;
import org.openhab.core.io.transport.serial.SerialPortIdentifier;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.io.transport.serial.UnsupportedCommOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serial device class
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimpleBinaryUART extends SimpleBinaryGenericDevice implements SerialPortEventListener {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryUART.class);
    /** Time for data line stabilization after data receive [ms] **/
    private static final long LINE_STABILIZATION_TIME = 0;

    /** port baud rate */
    private int baud = 9600;
    /** serial manager */
    private SerialPortManager serialPortManager;
    /** port id */
    private SerialPortIdentifier portId;
    /** port instance */
    private SerialPort serialPort;
    /** input data stream */
    private InputStream inputStream;
    /** output data stream */
    private OutputStream outputStream;

    /** buffer for incoming data */
    protected final SimpleBinaryByteBuffer inBuffer = new SimpleBinaryByteBuffer(256);
    /** store last sent data */
    protected SimpleBinaryItemData lastSentData = null;
    /** Last data receive time **/
    protected long receiveTime = 0;

    /** Flag indicating RTS signal will be handled */
    private boolean forceRTS = false;
    /** Flag indicating RTS signal will be handled on inverted logic */
    private boolean invertedRTS = false;
    /** Variable for count minimal time before reset RTS signal */
    private long sentTimeTicks = 0;
    /** Response timeout */
    protected final int timeout;
    /** timer measuring answer timeout */
    protected final Timer timer = new Timer();
    protected TimerTask timeoutTask = null;
    private final Object timeoutTaskLock = new Object();
    /** flag reading **/
    protected final AtomicInteger readingData = new AtomicInteger();
    /** current reading **/
    private int readingDataValue = 0;
    /** Flag indicating try to open port that is not presented in the system **/
    private boolean alreadyPortNotFound = false;

    /**
     * Constructor
     *
     * @param serialPortManager
     * @param deviceName
     * @param port
     * @param baud
     * @param simpleBinaryPollControl
     * @param forceRTS
     * @param invertedRTS
     * @param timeout
     * @param degradeMaxFailuresCount
     * @param degradeTime
     * @param discardCommand
     * @param syncCommand
     */
    public SimpleBinaryUART(SerialPortManager serialPortManager, String port, int baud,
            SimpleBinaryPollControl simpleBinaryPollControl, boolean forceRTS, boolean invertedRTS, int pollRate,
            Charset charset, int timeout, int degradeMaxFailuresCount, int degradeTime, boolean discardCommand,
            boolean syncCommand) {
        super(port, simpleBinaryPollControl, pollRate, charset, timeout, degradeMaxFailuresCount, degradeTime,
                discardCommand, syncCommand);

        this.baud = baud;
        // IFDEF_OH3.0 //
        this.forceRTS = forceRTS;
        this.invertedRTS = invertedRTS;
        // IFDEF_OH3.0
        this.timeout = timeout;
        this.serialPortManager = serialPortManager;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }

        super.dispose();

        timer.cancel();
    }

    /**
     * Return port hardware name
     *
     * @return
     */
    public String getPort() {
        return deviceID;
    }

    /**
     * Open serial port
     *
     * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#open()
     */
    @Override
    public Boolean open() {
        if (disposed) {
            return false;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} - Opening", this.toString());
        }

        portState.setState(PortStates.CLOSED);
        // set initial state for configured devices
        setStateToAllConfiguredDevices(DeviceStates.NOT_RESPONDING);
        // reset connected state
        // setConnected(false, null);
        waitingForAnswer.set(false);

        portId = null;

        try {
            // get port ID
            portId = serialPortManager.getIdentifier(this.deviceID);
            alreadyPortNotFound = false;
        } catch (Exception ex) {
            logger.error("{}: ", this.toString(), ex);
        } finally {
            if (portId == null) {
                portState.setState(PortStates.NOT_EXIST);

                if (!alreadyPortNotFound) {
                    var ports = getCommPortListString();
                    var errMsg = ports.length() == 0
                            ? String.format("%s not found. No port available.", this.toString())
                            : String.format("%s not found. Available ports: %s.", this.toString(), ports);
                    logger.warn(errMsg);

                    alreadyPortNotFound = true;
                } else {
                    logger.debug("{} still not found", this.toString());
                }

                setConnected(false, String.format("%s not found", this.toString()));
                return false;
            }
        }

        // initialize serial port
        try {
            serialPort = portId.open("openHAB", 1000);
            // get the input stream
            inputStream = serialPort.getInputStream();
            // get the output stream
            outputStream = serialPort.getOutputStream();
        } catch (PortInUseException e) {
            this.close();
            portState.setState(PortStates.NOT_AVAILABLE);

            var msg = String.format("%s is in use. Owner is {}", this.toString(), portId.getCurrentOwner());
            logger.error(msg);
            setConnected(false, msg);

            return false;
        } catch (Exception e) {
            this.close();
            var msg = String.format("%s: %s", this.toString(), e.getMessage());
            logger.error(msg);
            setConnected(false, msg);

            return false;
        }

        try {
            serialPort.addEventListener(this);
        } catch (TooManyListenersException e) {
            this.close();

            var msg = String.format("%s: %s", this.toString(), e.getMessage());
            logger.error(msg);
            setConnected(false, msg);

            return false;
        }

        // activate the DATA_AVAILABLE notifier
        serialPort.notifyOnDataAvailable(true);
        if (this.forceRTS) {
            // IFDEF_OH3.0 //
            // OUTPUT_BUFFER_EMPTY
            serialPort.notifyOnOutputEmpty(true);
            // IFDEF_OH3.0
        }

        try {
            // set port parameters
            serialPort.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
        } catch (UnsupportedCommOperationException e) {
            var msg = String.format("%s: %s", this.toString(), e.getMessage());
            logger.error(msg);
            setConnected(false, msg);

            this.close();
            return false;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Port {} is opened. Used class is {}", this.toString(), serialPort.getClass().getSimpleName());
        }

        portState.setState(PortStates.LISTENING);

        setConnected(true);
        return true;
    }

    /**
     * Return list of available port
     *
     * @return
     */
    private String getCommPortListString() {
        StringBuilder sb = new StringBuilder();
        @SuppressWarnings("null")
        Stream<SerialPortIdentifier> portList = serialPortManager.getIdentifiers();
        portList.filter(s -> s.getName().length() > 0).forEach((p) -> {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(p.getName());
        });

        return sb.toString();
    }

    /**
     * Close serial port
     *
     * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#close()
     */
    @Override
    public void close() {
        close(null);
    }

    /**
     * Close serial port with close reason specified. If reason is not null close is unexpected.
     */
    public void close(String reason) {
        if (serialPort != null) {
            serialPort.removeEventListener();
            try {
                inputStream.close();
                outputStream.close();

            } catch (Exception ex) {
                logger.error(ex.toString());
            }
            serialPort.close();
        }

        portState.setState(PortStates.CLOSED);
        setConnected(false, reason);
    }

    /*
     * Can send if we are not waiting for answer
     */
    @Override
    protected boolean canSend() {
        if (waitingForAnswer.get()) {
            return false;
        }

        return super.canSend();
    }

    /*
     * Can send if we are not waiting for answer.
     */
    @Override
    protected boolean canSend(int devId) {
        return canSend();
    }

    /**
     * Write data into device stream
     *
     * @param data
     *            Item data with compiled packet
     */
    @Override
    protected boolean sendDataOut(SimpleBinaryItemData data) {
        if (!isConnected()) {
            logger.debug("{} - Port is closed. Try to reopen.");
            if (!this.open()) {
                logger.warn("{} - Port is closed. Unable to send data to device {}.", this.toString(),
                        data.getDeviceId());
                return false;
            }
        }

        // data line stabilization
        if (LINE_STABILIZATION_TIME > 0) {
            while (Math.abs(System.currentTimeMillis() - receiveTime) <= LINE_STABILIZATION_TIME) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {

                }
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("{} - Sending data to device {} with length {} bytes", this.toString(), data.getDeviceId(),
                    data.getData().length);
            logger.debug("{} - data: {}", this.toString(),
                    SimpleBinaryProtocol.arrayToString(data.getData(), data.getData().length));
        }

        if (compareAndSetWaitingForAnswer()) {
            try {
                // set RTS
                if (this.forceRTS) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} - RTS set", this.toString());
                    }

                    // calc minimum time for send + currentTime + 0ms
                    sentTimeTicks = System.currentTimeMillis() + (((data.getData().length * 8) + 4) * 1000 / this.baud);

                    serialPort.setRTS(invertedRTS ? false : true);
                }

                // write string to serial port
                outputStream.write(data.getData());
                outputStream.flush();

                setLastSentData(data);
                logger.debug("{} - Device {} data sent.", toString(), data.getDeviceId());
            } catch (Exception e) {
                var msg = String.format("%s - Error while writing. %s.", this.toString(), e.toString());
                logger.error(msg, e);
                close(msg);

                cancelWaitingForAnswer();

                return false;
            }

            return true;
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("{} - Sending data to device {} discarted. Another send/wait is processed.",
                        this.toString(), data.getDeviceId());
            }

            return false;
        }
    }

    /**
     * Return last sent message
     *
     */
    public SimpleBinaryItemData getLastSentData() {
        return lastSentData;
    }

    /**
     * Set last sent data
     *
     */
    public void setLastSentData(SimpleBinaryItemData data) {
        lastSentData = data;
    }

    /**
     * Serial events
     *
     */
    @Override
    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
            case SerialPortEvent.BI:
            case SerialPortEvent.OE:
            case SerialPortEvent.FE:
            case SerialPortEvent.PE:
            case SerialPortEvent.CD:
            case SerialPortEvent.CTS:
            case SerialPortEvent.DSR:
            case SerialPortEvent.RI:
                break;
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                // reset RTS
                if (!this.forceRTS) {
                    break;
                }

                // IFDEF_OH3.0 //
                if (serialPort.isRTS() && !invertedRTS || !serialPort.isRTS() && invertedRTS) {
                    // comply minimum sending time. Added because on ubuntu14.04 event OUTPUT_BUFFER_EMPTY is called
                    // periodically even before some data are send.
                    if (System.currentTimeMillis() > sentTimeTicks) {
                        serialPort.setRTS(invertedRTS);

                        if (logger.isDebugEnabled()) {
                            logger.debug("{} - RTS reset", this.toString());
                        }
                    }
                }
                // IFDEF_OH3.0

                break;
            case SerialPortEvent.DATA_AVAILABLE:
                readingData.set(readingDataValue);
                readingDataValue = readingData.incrementAndGet();

                try {
                    while (inputStream.available() > 0) {
                        if (!readIncomingData()) {
                            break;
                        }
                    }

                    // check data
                    // check minimum length
                    while (inBuffer.position() > 3) {
                        // check if received data has valid address (same as sent data)
                        if (lastSentData != null && !checkDeviceID(inBuffer, getLastSentData().getDeviceId())) {
                            logger.error("{} - Address not valid: received/sent={}/{}", this.toString(),
                                    getDeviceID(inBuffer), getLastSentData().getDeviceId());
                            // flip buffer
                            inBuffer.flip();
                            // print details
                            printCommunicationInfo(inBuffer, lastSentData);
                            // clear buffer
                            inBuffer.clear();

                            logger.warn("{} - Address not valid: input buffer cleared", this.toString());

                            // set state
                            setDeviceState(getLastSentData().getDeviceId(), DeviceStates.DATA_ERROR_ADDRESS);

                            return;
                        }

                        int r = processData(inBuffer, getLastSentData());

                        if (r > 0 || r == ProcessDataResult.INVALID_CRC || r == ProcessDataResult.BAD_CONFIG
                                || r == ProcessDataResult.NO_VALID_ADDRESS || r == ProcessDataResult.UNKNOWN_MESSAGE) {
                            // waiting for answer and send block
                            cancelWaitingForAnswer();
                            // notify device
                            if (devices.containsKey(getLastSentData().getDeviceId())) {
                                synchronized (devices.get(getLastSentData().getDeviceId())) {
                                    devices.get(getLastSentData().getDeviceId()).notify();
                                }
                            }
                        } else if (r == ProcessDataResult.DATA_NOT_COMPLETED
                                || r == ProcessDataResult.PROCESSING_ERROR) {
                            break;
                        } else {
                            logger.warn("{} - Unexpected return code from processData(). Code=0x{}.", this.toString(),
                                    Integer.toHexString(r));
                        }

                        // check for new data
                        while (inputStream.available() > 0) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("{} - another new data - {}bytes", this.toString(),
                                        inputStream.available());
                            }
                            if (!readIncomingData()) {
                                break;
                            }
                        }
                    }
                    if (inputStream.available() > 0) {
                        logger.warn("{} - Still data in input buffer with size {}B", toString(),
                                inputStream.available());
                    }
                } catch (IOException e) {
                    logger.error("{} - Error receiving data: {}", toString(), e.getMessage());
                } catch (Exception ex) {
                    logger.error("{} - Exception receiving data: {}", toString(), ex.toString());
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    ex.printStackTrace(pw);
                    logger.error(sw.toString());
                } finally {
                    readingData.set(0);
                }
                break;
        }
    }

    /**
     * Read data from serial port buffer
     *
     * @throws IOException
     * @throws ModeChangeException
     */
    private boolean readIncomingData() throws IOException, ModeChangeException {

        byte[] readBuffer = new byte[32];
        int bytes = inputStream.read(readBuffer);

        receiveTime = System.currentTimeMillis();

        if (logger.isDebugEnabled()) {
            logger.debug("{} - received: {}", toString(), SimpleBinaryProtocol.arrayToString(readBuffer, bytes));
        }

        if (bytes + inBuffer.position() >= inBuffer.capacity()) {
            logger.error("{} - Buffer overrun", toString());
            return false;
        } else {
            inBuffer.put(readBuffer, 0, bytes);
        }

        return true;
    }

    /**
     * Check new data for all connected devices
     *
     */
    @Override
    public void checkNewData() {
        super.checkNewData();
    }

    @Override
    public String toString() {
        return "Port " + deviceID;
    }

    /**
     * Set waiting task for answer for slave device if waitingForAnswer not set.
     *
     * Return true if flag is set
     */
    protected boolean compareAndSetWaitingForAnswer() {
        if (waitingForAnswer.compareAndSet(false, true)) {
            synchronized (timeoutTaskLock) {
                timeoutTask = new TimerTask() {
                    @Override
                    public void run() {
                        dataTimeouted();
                    }
                };

                try {
                    timer.schedule(timeoutTask, timeout);
                } catch (IllegalStateException ex) {
                    logger.warn("{} - Cannot create timeout task. Task throw IllegalStateException. Thread={}",
                            this.toString(), Thread.currentThread().getId());
                    waitingForAnswer.set(false);
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    /**
     * Cancel waiting task for answer from slave device
     */
    protected void cancelWaitingForAnswer() {
        if (!waitingForAnswer.compareAndSet(true, false)) {
            logger.warn("{} - Device{} - cancelWaitingForAnswer(). waitingForAnswer already cancelled. Thread={}",
                    this.toString(), this.getLastSentData().getDeviceId(), Thread.currentThread().getId());
            return;
        }

        synchronized (timeoutTaskLock) {
            if (timeoutTask != null) {
                if (timeoutTask.cancel()) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} - Device{} - timeout task cancelled. Thread={}", this.toString(),
                                this.getLastSentData().getDeviceId(), Thread.currentThread().getId());
                    }
                } else {
                    logger.warn("{} - Device{} - timeout task already cancelled. Thread={}", this.toString(),
                            this.getLastSentData().getDeviceId(), Thread.currentThread().getId());
                }
                timeoutTask = null;
            }
        }
    }

    /**
     * Method processed after waiting for answer is timeouted
     */
    protected void dataTimeouted() {
        int address = this.getLastSentData().getDeviceId();
        int timeout = 5;

        while (readingData.get() > 0 && timeout-- > 0) {
            logger.warn("{} - Device{} - Receiving data timeouted but reading still active ({}). Thread={}",
                    this.toString(), address, readingData.get(), Thread.currentThread().getId());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logger.error("{} - dataTimeouted() Thread.sleep() error. Thread={}", this.toString(),
                        Thread.currentThread().getId());
            }
        }

        if (!waitingForAnswer.compareAndSet(true, false)) {
            logger.warn("{} - Device{} - dataTimeouted cancelled. waitingForAnswer not active. Thread={}",
                    this.toString(), address, Thread.currentThread().getId());
            return;
        }

        logger.warn("{} - Device{} - Receiving data timeouted. Thread={}", this.toString(), address,
                Thread.currentThread().getId());

        setDeviceState(address, DeviceStates.NOT_RESPONDING);

        inBuffer.flip();
        if (inBuffer.remaining() > 0) {
            // print details
            printCommunicationInfo(inBuffer, lastSentData);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("{} - Input buffer cleared", this.toString());
        }

        inBuffer.clear();

        if (devices.containsKey(getLastSentData().getDeviceId())) {
            synchronized (devices.get(getLastSentData().getDeviceId())) {
                devices.get(getLastSentData().getDeviceId()).notify();
            }
        }
    }
}
