/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.simplebinary.internal.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryChannel;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryDeviceState;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericDevice;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link simplebinaryHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
public class SimpleBinaryGenericHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(SimpleBinaryGenericHandler.class);

    private @Nullable SimpleBinaryGenericDevice connection = null;

    public final Map<ChannelUID, SimpleBinaryChannel> channels = new LinkedHashMap<ChannelUID, SimpleBinaryChannel>();

    private long errorSetTime;

    public SimpleBinaryGenericHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("{} - initialize. Channels count={}", thing.getLabel(), thing.getChannels().size());

        int errors = 0;

        // check configuration
        for (Channel channel : thing.getChannels()) {
            final ChannelUID channelUID = channel.getUID();
            final ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
            if (channelTypeUID == null) {
                errors++;
                logger.warn("{} - Channel {} has no type", thing.getLabel(), channel.getLabel());
                continue;
            }

            final SimpleBinaryChannel chConfig = channel.getConfiguration().as(SimpleBinaryChannel.class);
            chConfig.channelId = channelUID;
            chConfig.channelType = channelTypeUID;

            if (!chConfig.init(this)) {
                errors++;
                logger.warn("{} - channel configuration error {}, Error={}", thing.getLabel(), chConfig,
                        chConfig.getError());
                continue;
            }

            logger.debug("{} - channel added {}", thing.getLabel(), chConfig);

            channels.put(channelUID, chConfig);
        }

        var bridge = getBridge();

        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
        } else {
            // get connection and update status
            bridgeStatusChanged(bridge.getStatusInfo());
        }

        if (errors > 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Channel configuration error");
        }
        BridgeHandler handler;
        if (bridge != null && (handler = bridge.getHandler()) != null) {
            ((SimpleBinaryBridgeHandler) handler).updateConfig();
        }
    }

    @Override
    public void dispose() {
        for (SimpleBinaryChannel ch : channels.values()) {
            ch.clear();
        }
        channels.clear();
        connection = null;
        logger.debug("{} - device dispose", getThing().getLabel());

        Bridge bridge = getBridge();
        BridgeHandler handler;
        if (bridge != null && (handler = bridge.getHandler()) != null) {
            ((SimpleBinaryBridgeHandler) handler).updateConfig();
        }
    }

    /**
     * Update thing status by bridge status. Status is also set during initialization
     *
     * @param bridgeStatusInfo Current bridge status
     */
    @SuppressWarnings("null")
    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            connection = null;
            return;
        }
        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            connection = null;
            return;
        }

        SimpleBinaryBridgeHandler b = (SimpleBinaryBridgeHandler) (getBridge().getHandler());
        if (b == null) {
            logger.error("BridgeHandler is null");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }

        // bridge is online take his connection
        connection = b.connection;

        updateStatus(ThingStatus.ONLINE);
    }

    @SuppressWarnings({ "unused", "null" })
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("{} - Command {}({}) for channel {}", thing.getLabel(), command, command.getClass(), channelUID);

        // get cached values
        if (command instanceof RefreshType) {
            SimpleBinaryChannel channel = channels.get(channelUID);
            if (channel == null) {
                logger.warn("{} - cannot get value to refresh. Channel {} not found.", thing.getLabel(), channelUID);
            } else {
                State s = channel.getState();
                if (s != null) {
                    updateState(channelUID, s);
                }
            }
            return;
        }

        if (connection == null) {
            return;
        }

        if (!channels.containsKey(channelUID)) {
            logger.error("{} - command: Channel does not exists. ChannelUID={}", thing.getLabel(), channelUID);
            return;
        }
        SimpleBinaryChannel channel = channels.get(channelUID);

        if (channel.getCommandAddress() == null) {
            if (!channel.isMissingCommandReported()) {
                logger.warn(
                        "{} - command not completed. Channel does not have a command address specified. ChannelUID={}",
                        thing.getLabel(), channelUID);
            }
            return;
        }

        // discard command when device not responding
        if (connection.getDiscardCommand()) {
            var device = connection.getDevices().get(channel.getCommandAddress().getDeviceId());
            if (device != null && device.getState().getState() == SimpleBinaryDeviceState.DeviceStates.NOT_RESPONDING) {
                return;
            }
        }

        connection.sendData(channel, command);
    }

    @Override
    public void handleRemoval() {
        super.handleRemoval();
    }

    @Override
    public void updateState(ChannelUID channelUID, State state) {
        super.updateState(channelUID, state);
    }

    @Override
    public void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        super.updateStatus(status, statusDetail, description);
    }

    public void setError(String message) {
        errorSetTime = System.currentTimeMillis();
        var st = getThing().getStatusInfo();
        if (st.getStatus() == ThingStatus.OFFLINE && st.getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR
                && st.getDescription() != null && message.equals(st.getDescription())) {
            return;
        }

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
    }

    /**
     * Clear thing error if necessary
     */
    public void clearError() {
        // no error
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            return;
        }

        // minimum error time left
        if (System.currentTimeMillis() - errorSetTime > 10000) {
            updateStatus(ThingStatus.ONLINE);
        }
    }
}
