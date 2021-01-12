/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Device status collection
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimpleBinaryDeviceStateCollection extends HashMap<Integer, SimpleBinaryDeviceState> {
    private static final long serialVersionUID = -6637691081696263746L;

    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryDeviceStateCollection.class);

    /**
     * Device collection constructor
     *
     */
    public SimpleBinaryDeviceStateCollection() {
        super();
    }

    /**
     * Device collection constructor
     *
     * @param devices
     *            Device list
     */
    public void put(ArrayList<Integer> devices) {
        for (var i : devices) {
            put(i, new SimpleBinaryDeviceState());
        }
    }

    /**
     * Set state to device specified by address connected to given port
     *
     * @param deviceAddress
     *            Device address
     * @param state
     *            Device state
     */
    public boolean setDeviceState(Integer deviceAddress, SimpleBinaryDeviceState.DeviceStates state) {
        if (!this.containsKey(deviceAddress)) {
            this.put(deviceAddress, new SimpleBinaryDeviceState());
        }

        // retrieve device
        SimpleBinaryDeviceState deviceState = this.get(deviceAddress);
        // set internal state
        return deviceState.setState(state);
    }

    /**
     * Set state to all devices connected to specified port
     *
     * @param state
     *            Required state
     */
    public void setStateToAllConfiguredDevices(SimpleBinaryDeviceState.DeviceStates state) {
        // send data to event bus
        for (var item : entrySet()) {
            setDeviceState(item.getKey(), state);
        }
    }

    /**
     * @param deviceAddress
     *            Device address
     * @return Device state
     *
     * @since 1.9.0
     */
    public SimpleBinaryDeviceState.DeviceStates getDeviceState(Integer deviceAddress) {
        // retrieve device
        SimpleBinaryDeviceState deviceState = this.get(deviceAddress);

        if (deviceState == null) {
            return null;
        }

        // return device state
        return deviceState.getState();
    }
}
