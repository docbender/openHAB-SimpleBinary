/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.util.Calendar;
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
    Calendar lastCommunication = null;
    Queue<Calendar> communicationOK = new LinkedList<Calendar>();
    Queue<Calendar> communicationError = new LinkedList<Calendar>();

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
    private Calendar changedSince = Calendar.getInstance();

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
    public Calendar getChangeDate() {
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
        Calendar now = Calendar.getInstance();
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

        lastCommunication = now;

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
        Calendar limitTime = Calendar.getInstance();
        limitTime.add(Calendar.MINUTE, -5);

        while (communicationOK.size() > 0 && communicationOK.element().before(limitTime)) {
            communicationOK.remove();
        }
        while (communicationError.size() > 0 && communicationError.element().before(limitTime)) {
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
    public Calendar getLastCommunication() {
        return lastCommunication;
    }
}
