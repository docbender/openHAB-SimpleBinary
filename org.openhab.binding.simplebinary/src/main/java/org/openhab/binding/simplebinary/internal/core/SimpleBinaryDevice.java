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

import java.util.ArrayList;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.openhab.binding.simplebinary.internal.handler.SimpleBinaryGenericHandler;

/**
 * The {@link SimpleBinaryDevice} class with device informations
 *
 * @author VitaTucek - Initial contribution
 */
public class SimpleBinaryDevice {
    private final int deviceId;
    private final SimpleBinaryDeviceState state;
    private boolean isDegraded = false;
    private long degradeTime = 0;
    private int failuresCounter = 0;
    /** received message type */
    protected final AtomicReference<SimpleBinaryMessageType> receivedMessage = new AtomicReference<SimpleBinaryMessageType>();
    /** things */
    private final ArrayList<SimpleBinaryGenericHandler> things = new ArrayList<SimpleBinaryGenericHandler>();
    /** queue for commands */
    private final ConcurrentLinkedDeque<SimpleBinaryChannel> commandQueue = new ConcurrentLinkedDeque<SimpleBinaryChannel>();

    public ConcurrentLinkedDeque<SimpleBinaryChannel> getCommandQueue() {
        return commandQueue;
    }

    /**
     * Construct device
     */
    public SimpleBinaryDevice(int id) {
        deviceId = id;
        state = new SimpleBinaryDeviceState();
        receivedMessage.set(SimpleBinaryMessageType.UNKNOWN);
    }

    /**
     * Return device ID
     *
     * @return
     */
    public int getDeviceId() {
        return deviceId;
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
     * Return things
     *
     * @return
     */
    public ArrayList<SimpleBinaryGenericHandler> getThingHandlers() {
        return things;
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
            failuresCounter = 0;
        }
        return false;
    }

    /**
     * Check if device is unresponsive
     *
     * @param maxFailures Max failure count
     */
    public boolean unresponsive(int maxFailures) {
        // 0 -> off-scan mode disabled
        if (maxFailures == 0) {
            return false;
        }
        if (isDegraded) {
            return true;
        }
        if (++failuresCounter >= maxFailures) {
            isDegraded = true;
            degradeTime = System.currentTimeMillis();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Mark online device
     */
    public void alive() {
        if (failuresCounter != 0) {
            failuresCounter = 0;
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
     * Add device specific thing
     *
     * @param thing
     */
    public SimpleBinaryDevice addThingHandler(SimpleBinaryGenericHandler thing) {
        if (!things.contains(thing)) {
            things.add(thing);
        }
        return this;
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
