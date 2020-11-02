/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Status of communicated device
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimpleBinaryDeviceState {

    double packetLost = 0.0;
    ZonedDateTime lastCommunication = ZonedDateTime.now();
    Queue<ZonedDateTime> communicationOK = new LinkedList<ZonedDateTime>();
    Queue<ZonedDateTime> communicationError = new LinkedList<ZonedDateTime>();

    public enum DeviceStates {

        /**
         * Device state is unknown
         */
        UNKNOWN,
        /**
         * Device is connected and communicate with master
         */
        CONNECTED,
        /**
         * Device does not respond / disconnected
         */
        NOT_RESPONDING,
        /**
         * Device communicates but there is CRC error in communication (for both directions)
         */
        RESPONSE_ERROR,
        /**
         * Device communicates but there is same problem with data (unknown address, not supported telegram, error
         * during save data in slave, ...)
         */
        DATA_ERROR
    }

    private DeviceStates state = DeviceStates.UNKNOWN;
    private DeviceStates prevState = DeviceStates.UNKNOWN;
    private ZonedDateTime changedSince = ZonedDateTime.now();

    /**
     * Return last device state
     *
     * @return
     */
    public DeviceStates getState() {
        return state;
    }

    /**
     * Return previous state of device
     *
     * @return
     */
    public DeviceStates getPreviousState() {
        return prevState;
    }

    /**
     * Return date of last device state change
     *
     * @return
     */
    public ZonedDateTime getChangeDate() {
        return changedSince;
    }

    /**
     * Set actual device state
     *
     * @param state
     * @return
     */
    public boolean setState(DeviceStates state) {
        boolean changed = false;
        ZonedDateTime now = ZonedDateTime.now();
        // set state only if previous is different
        if (this.state != state) {
            this.prevState = this.state;
            this.state = state;
            this.changedSince = now;
            changed = true;
        }

        if (this.state == DeviceStates.CONNECTED) {
            communicationOK.add(now);
        } else {
            communicationError.add(now);
        }

        double oldPlValue = packetLost;

        calcPacketLost();

        if (this.state != DeviceStates.NOT_RESPONDING && this.state != DeviceStates.UNKNOWN) {
            lastCommunication = now;
        }

        return changed || oldPlValue != packetLost;
    }

    /**
     * Return packet lost in percentage
     *
     * @return
     */
    public double getPacketLost() {
        calcPacketLost();

        return packetLost;
    }

    /**
     * Calculate packet lost for specific time in past
     */
    private void calcPacketLost() {
        ZonedDateTime limitTime = ZonedDateTime.now().plusMinutes(-5);

        while (communicationOK.size() > 0 && communicationOK.element().isBefore(limitTime)) {
            communicationOK.remove();
        }
        while (communicationError.size() > 0 && communicationError.element().isBefore(limitTime)) {
            communicationError.remove();
        }

        if (communicationOK.size() == 0 && communicationError.size() == 0) {
            packetLost = 0;
        } else {
            packetLost = 100 * communicationError.size() / (communicationOK.size() + communicationError.size());
        }
    }

    /**
     * Return last communication time
     *
     * @return calendar
     */
    public ZonedDateTime getLastCommunication() {
        return lastCommunication;
    }
}
