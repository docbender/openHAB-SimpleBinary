/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.simplebinary.internal.core;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link SimpleBinaryDevice} class with device informations
 *
 * @author VitaTucek - Initial contribution
 */
public class SimpleBinaryDevice {
    private final SimpleBinaryDeviceState state;
    private boolean isDegraded = false;
    private long degradeTime = 0;
    private int retryCounter = 0;

    protected volatile SimpleBinaryMessageType receivedMessage = SimpleBinaryMessageType.UNKNOWN;

    /** timer measuring answer timeout */
    protected final Timer timer = new Timer();
    protected TimeoutTask timeoutTask = null;
    /** flag waiting */
    protected final AtomicBoolean waitingForAnswer = new AtomicBoolean(false);
    protected SimpleBinaryIRequestTimeouted requestTimeouted;
    /** queue for commands */
    protected final ConcurrentLinkedDeque<SimpleBinaryChannel> commandQueue = new ConcurrentLinkedDeque<SimpleBinaryChannel>();

    public ConcurrentLinkedDeque<SimpleBinaryChannel> getCommandQueue() {
        return commandQueue;
    }

    /**
     * Return state object
     *
     * @return
     */
    public SimpleBinaryDeviceState getState() {
        return state;
    }

    /**
     * Construct device
     */
    public SimpleBinaryDevice() {
        this.state = new SimpleBinaryDeviceState();
    }

    /**
     * Construct device
     *
     * @param timeoutEvent
     */
    public SimpleBinaryDevice(SimpleBinaryIRequestTimeouted timeoutEvent) {
        this();
        this.requestTimeouted = timeoutEvent;
    }

    /**
     * Check if device is degraded
     *
     * @return
     */
    public boolean isDegraded() {
        return isDegraded;
    }

    /**
     * Check if device is degraded by degrade period
     *
     * @param degradePeriod
     * @return
     */
    public boolean stillDegraded(final int degradePeriod) {
        if (isDegraded && System.currentTimeMillis() - degradeTime < degradePeriod) {
            return true;
        }
        if (isDegraded) {
            isDegraded = false;
            retryCounter = 0;
        }
        return false;
    }

    /**
     * Check if device is unresponsive
     *
     * @param maxRetry Max retry count
     */
    public boolean unresponsive(int maxRetry) {
        if (isDegraded) {
            return true;
        }
        if (retryCounter >= maxRetry) {
            isDegraded = true;
            degradeTime = System.currentTimeMillis();
            return true;
        } else {
            retryCounter++;
            return false;
        }
    }

    /**
     * Add command into device queue
     *
     * @param command
     */
    public void addCommand(SimpleBinaryChannel command) {
        // remove previous command with same channelID
        if (!commandQueue.isEmpty()) {
            for (SimpleBinaryChannel cmd : commandQueue) {
                if (cmd.channelId == command.channelId) {
                    commandQueue.remove(cmd);
                    break;
                }
            }
        }
        // add command into queue
        commandQueue.add(command);
    }

    /**
     * TimerTask for timeout event
     *
     * @author Vita Tucek
     * @since 1.9.0
     *
     */
    class TimeoutTask extends TimerTask {
        /** Channel info **/
        public final SimpleBinaryDevice device;
        /** Event **/
        public final SimpleBinaryIRequestTimeouted event;

        TimeoutTask(SimpleBinaryDevice device, SimpleBinaryIRequestTimeouted event) {
            this.device = device;
            this.event = event;
        }

        @Override
        public void run() {
        }
    }
}
