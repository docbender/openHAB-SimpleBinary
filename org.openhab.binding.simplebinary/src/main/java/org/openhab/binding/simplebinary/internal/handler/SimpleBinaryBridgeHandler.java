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

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simatic.internal.simatic.SimaticChannel;
import org.openhab.binding.simplebinary.internal.SimpleBinaryBindingConstants;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericDevice;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SimpleBinaryBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
public class SimpleBinaryBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(SimpleBinaryBridgeHandler.class);

    public @Nullable SimpleBinaryGenericDevice connection = null;

    // bridge channels
    protected @Nullable ChannelUID chVersion, chTagCount, chRequests, chBytes;

    public SimpleBinaryBridgeHandler(Bridge bridge) {
        super(bridge);

        // retrieve bridge channels
        getThing().getChannels().forEach((channel) -> {
            if (channel.getChannelTypeUID().equals(SimpleBinaryBindingConstants.CHANNEL_TYPE_VERSION)) {
                chVersion = channel.getUID();
            } else if (channel.getChannelTypeUID().equals(SimpleBinaryBindingConstants.CHANNEL_TYPE_TAG_COUNT)) {
                chTagCount = channel.getUID();
            } else if (channel.getChannelTypeUID().equals(SimpleBinaryBindingConstants.CHANNEL_TYPE_REQUESTS)) {
                chRequests = channel.getUID();
            } else if (channel.getChannelTypeUID().equals(SimpleBinaryBindingConstants.CHANNEL_TYPE_BYTES)) {
                chBytes = channel.getUID();
            }
        });
    }

    @Override
    public void initialize() {
        updateState(chVersion, new StringType(SimpleBinaryBindingConstants.VERSION));

        if (connection == null) {
            return;
        }

        // react on connection changes
        connection.onConnectionChanged((connected) -> {
            if (connected) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            }
        });

        connection.onMetricsUpdated((requests, bytes) -> {
            updateState(chRequests, new DecimalType(requests));
            updateState(chBytes, new DecimalType(bytes));
        });

        // temporarily status
        updateStatus(ThingStatus.UNKNOWN);

        // background initialization
        scheduler.execute(() -> {
            while (!connection.open()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                connection.reconnectWithDelaying();
            }
        });
    }

    @Override
    protected void updateState(@Nullable ChannelUID channel, State state) {
        if (channel == null) {
            logger.error("{} - updateState(...) channelID is null for state={}", getThing().getLabel(), state);
            return;
        }
        // logger.debug("{} - update channelID={}, state={}", getThing().getLabel(), channel, state);

        super.updateState(channel, state);
    }

    @Override
    public void dispose() {
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
        logger.debug("{} - bridge has been stopped", getThing().getLabel());
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("{} - Command {} for channel {}", thing.getLabel(), command, channelUID);

        // get cached values
        if (command instanceof RefreshType) {
            logger.error("{} - command: RefreshType not implemented", thing.getLabel());
            // updateState(channelUID, value);
        }
    }

    /**
     * Update bridge configuration by all things channels
     */
    public void updateConfig() {
        int channelCount = 0;
        int stateChannelCount = 0;

        for (Thing th : getThing().getThings()) {
            var h = ((SimpleBinaryGenericHandler) th.getHandler());
            if (h == null) {
                continue;
            }
            channelCount += h.channels.size();
            for (SimpleBinaryChannel ch : h.channels.values()) {
                if (ch.getStateAddress() != null) {
                    stateChannelCount++;
                }
            }
        }

        var stateItems = new ArrayList<@NonNull SimpleBinaryChannel>(stateChannelCount);

        for (Thing th : getThing().getThings()) {
            var h = ((SimpleBinaryGenericHandler) th.getHandler());
            if (h == null) {
                continue;
            }
            for (SimpleBinaryChannel ch : h.channels.values()) {
                if (ch.getStateAddress() != null) {
                    stateItems.add(ch);
                }
            }
        }

        if (connection != null) {
            var c = connection;
            c.setDataAreas(stateItems);

            if (c.isConnected()) {
                updateState(chAreasCount, new DecimalType(c.getReadAreas().size()));
                updateState(chAreas,
                        new StringType((c.getReadAreas().size() == 0) ? "none" : c.getReadAreas().toString()));
            }
        }

        updateState(chTagCount, new DecimalType(channelCount));

        logger.debug("{} - updating {} channels({} read)", getThing().getLabel(), channelCount, stateChannelCount);
    }
}
