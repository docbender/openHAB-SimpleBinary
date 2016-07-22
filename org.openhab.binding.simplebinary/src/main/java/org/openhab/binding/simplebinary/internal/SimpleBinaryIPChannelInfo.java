/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Channel info
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
/**
 * @author tucek
 *
 */
public class SimpleBinaryIPChannelInfo {

    private AsynchronousSocketChannel channel = null;
    private SimpleBinaryByteBuffer buffer = null;
    private final SimpleBinaryIPChannelInfoCollection collection;
    private InetSocketAddress address = null;
    private ByteBuffer writeBuffer = null;
    private SimpleBinaryItemData lastSentData = null;

    private int configuredDeviceID = -1;
    private int receivedDeviceID = -1;
    private String configuredDeviceIP = "";

    /** timer measuring answer timeout */
    protected Timer timer = new Timer();
    protected TimeoutTask timeoutTask = null;
    /** flag waiting */
    protected AtomicBoolean waitingForAnswer = new AtomicBoolean(false);

    private SimpleBinaryIRequestTimeouted requestTimeouted;
    // = new SimpleBinaryIRequestTimeouted() {
    // @Override
    // public void timeoutEvent(SimpleBinaryIPChannelInfo chInfo) {
    //
    // }
    // };

    public SimpleBinaryIPChannelInfo(AsynchronousSocketChannel channel, ByteBuffer buffer,
            SimpleBinaryIPChannelInfoCollection collection, SimpleBinaryIRequestTimeouted timeoutEvent) {

        this.collection = collection;
        this.requestTimeouted = timeoutEvent;

        assignChannel(channel, buffer, timeoutEvent);

        // set address for use when device reconnected
        this.configuredDeviceIP = getIpAddress();
    }

    public SimpleBinaryIPChannelInfo(int deviceID, String ipAddress, SimpleBinaryIPChannelInfoCollection collection) {
        this.collection = collection;

        this.configuredDeviceID = deviceID;
        this.configuredDeviceIP = ipAddress;
    }

    public void assignChannel(AsynchronousSocketChannel channel, ByteBuffer buffer,
            SimpleBinaryIRequestTimeouted timeoutEvent) {
        this.channel = channel;
        this.buffer = new SimpleBinaryByteBuffer(buffer);
        this.requestTimeouted = timeoutEvent;

        // get connected channel address
        address = retrieveAddress(this.channel);
    }

    /**
     *
     */
    public static InetSocketAddress retrieveAddress(AsynchronousSocketChannel channel) {
        SocketAddress a = null;

        try {
            a = channel.getRemoteAddress();
        } catch (IOException e) {
        }

        if (a != null && (a instanceof InetSocketAddress)) {
            return ((InetSocketAddress) a);
        }

        return null;
    }

    public String getHostName() {
        if (address != null) {
            return address.getHostName();
        } else {
            return "";
        }
    }

    public String getIpAddress() {
        if (address != null) {
            return address.getAddress().getHostAddress();
        } else {
            return configuredDeviceIP;
        }
    }

    public int getPort() {
        if (address != null) {
            return address.getPort();
        } else {
            return -1;
        }
    }

    public int getDeviceId() {
        if (configuredDeviceID >= 0) {
            return configuredDeviceID;
        }

        return receivedDeviceID;
    }

    public int getDeviceIdConfigured() {
        return configuredDeviceID;
    }

    public int getDeviceIdReceived() {
        return receivedDeviceID;
    }

    public SimpleBinaryByteBuffer getBuffer() {
        return buffer;
    }

    public AsynchronousSocketChannel getChannel() {
        return channel;
    }

    public void remove() {
        collection.remove(this);
    }

    /**
     * Mark channel as closed
     */
    public void closed() {
        clearWaitingForAnswer();

        channel = null;
        address = null;
        buffer = null;
        writeBuffer = null;
        requestTimeouted = null;
        // TODO:
    }

    public ByteBuffer getWriteBuffer() {
        return writeBuffer;
    }

    public void setWriteBuffer(ByteBuffer buffer) {
        writeBuffer = null;
        writeBuffer = buffer;
    }

    public void clearWriteBuffer() {
        writeBuffer = null;
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
     * Set / reset waiting task for answer for slave device
     *
     * @param state
     */
    private void setWaitingForAnswer(boolean state) {
        waitingForAnswer.set(state);

        if (state) {
            if (timeoutTask != null) {
                timeoutTask.cancel();
            }

            timeoutTask = new TimeoutTask(this, requestTimeouted) {
                @Override
                public void run() {
                    timeoutTask = null;
                    // dataTimeouted
                    if (this.event != null) {
                        this.event.timeoutEvent(this.chInfo);
                    }

                    clearWaitingForAnswer();
                }
            };

            timer.schedule(timeoutTask, TimeoutTask.TIMEOUT);
        } else {
            if (timeoutTask != null) {
                timeoutTask.cancel();
                timeoutTask = null;
            }
        }
    }

    /**
     * Set waiting task for answer for target device if waitingForAnswer not set.
     * Return true if flag is set
     *
     */
    protected boolean compareAndSetWaitingForAnswer() {
        if (waitingForAnswer.compareAndSet(false, true)) {
            setWaitingForAnswer(true);

            return true;
        } else {
            return false;
        }
    }

    protected void clearWaitingForAnswer() {
        setWaitingForAnswer(false);
    }

    /**
     * TimerTask for timeout event
     *
     * @author Vita Tucek
     * @since 1.9.0
     *
     */
    class TimeoutTask extends TimerTask {

        /** Timeout for receiving data [ms] **/
        public static final int TIMEOUT = 2000;
        /** Channel info **/
        public final SimpleBinaryIPChannelInfo chInfo;
        /** Event **/
        public final SimpleBinaryIRequestTimeouted event;

        TimeoutTask(SimpleBinaryIPChannelInfo chInfo, SimpleBinaryIRequestTimeouted event) {
            this.chInfo = chInfo;
            this.event = event;
        }

        @Override
        public void run() {

        }
    }
}
