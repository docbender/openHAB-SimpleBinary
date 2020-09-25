/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.util.Map;

import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericBindingProvider.DeviceConfig;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.types.Type;

/**
 * Device interface
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public interface SimpleBinaryIDevice {

    public interface ConnectionChanged {
        public void onConnectionChanged(boolean connected);
    }

    public interface MetricsUpdated {
        public void onMetricsUpdated(long requests, long bytes);
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
     * Send data to device
     *
     * @param itemName
     *            Item name
     * @param command
     *            Command to send
     * @param itemConfig
     *            Item config
     * @param deviceConfig
     *            Device config
     * @throws InterruptedException
     */
    public void sendData(String itemName, Type command, SimpleBinaryBindingConfig itemConfig, DeviceConfig deviceConfig)
            throws InterruptedException;

    /**
     * Check new data for all connected devices
     *
     */
    public void checkNewData();

    /**
     * Method to set binding configuration
     *
     * @param eventPublisher
     * @param itemsConfig
     * @param itemsInfoConfig
     */
    public void setBindingData(EventPublisher eventPublisher, Map<String, SimpleBinaryBindingConfig> itemsConfig,
            Map<String, SimpleBinaryInfoBindingConfig> itemsInfoConfig,
            Map<String, SimpleBinaryGenericDevice> configuredDevices);

    /**
     * Method to clear inner binding configuration
     */
    public void unsetBindingData();

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
}
