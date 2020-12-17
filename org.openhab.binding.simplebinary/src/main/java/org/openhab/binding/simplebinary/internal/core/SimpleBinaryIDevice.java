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

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.core.types.Command;

/**
 * Device interface
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public interface SimpleBinaryIDevice {

    public interface ConnectionChanged {
        public void onConnectionChanged(boolean connected, String reason);
    }

    public interface MetricsUpdated {
        public void onMetricsUpdated(long requests, long bytes);
    }

    public interface DeviceStateUpdated {
        public void onDeviceStateUpdated(int deviceId, SimpleBinaryDeviceState state);
    }

    /**
     * Open device connection
     *
     * @return
     */
    public Boolean open();

    /**
     * Close device connection
     *
     */
    public void close();

    /**
     * Release resources
     *
     */
    void dispose();

    /**
     * Send data to device
     *
     * @param channel
     *            Channel
     * @param command
     *            Command to send
     */
    public void sendData(SimpleBinaryChannel channel, Command command);

    /**
     * Check new data for all connected devices
     *
     */
    public void checkNewData();

    /**
     * Set read write areas
     *
     */
    public void setDataAreas(@NonNull ArrayList<Integer> devices,
            @NonNull ArrayList<@NonNull SimpleBinaryChannel> stateItems);

    /**
     * Function return device string representation
     */
    @Override
    public String toString();

    /**
     * Set method provided on connection changes
     */
    public void onConnectionChanged(ConnectionChanged onChangeMethod);

    /**
     * Set method provided on update metrics
     */
    public void onMetricsUpdated(MetricsUpdated onUpdateMethod);

    /**
     * Set method provided on update single device state
     */
    public void onDeviceStateUpdated(DeviceStateUpdated onUpdateMethod);
}
