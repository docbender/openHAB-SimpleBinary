/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.LinkedList;

import org.openhab.binding.simplebinary.internal.core.SimpleBinaryDeviceState.DeviceStates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Channel info
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimpleBinaryIPChannelInfoCollection extends LinkedList<SimpleBinaryIPChannelInfo> {
    private static final long serialVersionUID = 8037598025330106665L;
    private final SimpleBinaryDeviceStateCollection deviceStates;
    private final String deviceName;

    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryIPChannelInfoCollection.class);

    public SimpleBinaryIPChannelInfoCollection(SimpleBinaryDeviceStateCollection deviceStates, String deviceName) {
        this.deviceStates = deviceStates;
        this.deviceName = deviceName;
    }

    public SimpleBinaryIPChannelInfo addChannel(AsynchronousSocketChannel channel, ByteBuffer buffer,
            SimpleBinaryIRequestTimeouted timeoutEvent) {

        if (logger.isDebugEnabled()) {
            logger.debug("Adding channel...");
        }

        String channelIp = SimpleBinaryIPChannelInfo.retrieveAddress(channel).getAddress().getHostAddress();

        for (SimpleBinaryIPChannelInfo i : this) {
            if (i.getChannel() == null && i.hasIpConfigured()) {
                // assign only locked connection
                if (channelIp.equals(i.getIpConfigured())) {
                    i.assignChannel(channel, buffer, timeoutEvent);

                    deviceStates.setDeviceState(i.getDeviceId(), DeviceStates.CONNECTED);

                    if (logger.isDebugEnabled()) {
                        if (i.isIpLocked()) {
                            logger.debug("Channel is locked and already exists");
                        } else {
                            logger.debug("Channel exist in client configuration and ID={} is expected",
                                    i.getDeviceIdConfigured());
                        }
                    }

                    return i;
                }
            }
        }

        SimpleBinaryIPChannelInfo channelInfo = new SimpleBinaryIPChannelInfo(channel, buffer, this, timeoutEvent);
        this.add(channelInfo);

        if (logger.isDebugEnabled()) {
            logger.debug("New channel in collection created");
        }

        return channelInfo;
    }

    public boolean remove(SimpleBinaryIPChannelInfo o) {
        deviceStates.setDeviceState(o.getDeviceId(), DeviceStates.NOT_RESPONDING);

        return super.remove(o);
    }

    public SimpleBinaryIPChannelInfo getById(int id) {
        if (this.size() == 0) {
            return null;
        }

        for (SimpleBinaryIPChannelInfo i : this) {
            if (i.getDeviceId() == id) {
                return i;
            }
        }

        return null;
    }

    public void addConfiguredChannel(int deviceID, String ipAddress, boolean isIpLocked) {
        this.add(new SimpleBinaryIPChannelInfo(deviceID, ipAddress, isIpLocked, this) {
        });
    }
}
