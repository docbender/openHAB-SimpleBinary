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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simplebinary.internal.SimpleBinaryBindingConstants;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryChannel;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryChannelStatus;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryDevice;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryDeviceCollection;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericDevice;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.type.ChannelTypeUID;
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
    protected volatile boolean disposed = false;
    /** device status channels */
    public final Map<ChannelUID, SimpleBinaryChannelStatus> statusChannels = new LinkedHashMap<ChannelUID, SimpleBinaryChannelStatus>();
    /** bridge channels */
    protected @Nullable ChannelUID chVersion, chTagCount, chRequests, chBytes, chCycleTime;
    /** channels count */
    private int channelCount = 0;
    /** Initial scheduler delay */
    private static final long INIT_SECONDS = 5;

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
            } else if (channel.getChannelTypeUID().equals(SimpleBinaryBindingConstants.CHANNEL_TYPE_CYCLE_TIME)) {
                chCycleTime = channel.getUID();
            }
        });
    }

    @SuppressWarnings("null")
    @Override
    public void initialize() {
        updateState(chVersion, new StringType(SimpleBinaryBindingConstants.VERSION));

        if (connection == null) {
            return;
        }

        // check configuration
        for (Channel channel : thing.getChannels()) {
            final ChannelUID channelUID = channel.getUID();
            final ChannelTypeUID channelTypeUID = channel.getChannelTypeUID();
            if (channelTypeUID == null) {
                logger.warn("{} - Channel {} has no type", thing.getLabel(), channel.getLabel());
                continue;
            } else if (!channelTypeUID.getId().startsWith("dev")) {
                continue;
            }

            final SimpleBinaryChannelStatus chConfig = channel.getConfiguration().as(SimpleBinaryChannelStatus.class);
            chConfig.channelId = channelUID;
            chConfig.channelType = channelTypeUID;

            if (!chConfig.init(this)) {
                logger.warn("{} - channel configuration error {}, Error={}", thing.getLabel(), chConfig,
                        chConfig.getError());
                continue;
            }

            logger.debug("{} - channel added {}", thing.getLabel(), chConfig);

            statusChannels.put(channelUID, chConfig);
        }

        // react on connection changes
        connection.onConnectionChanged((connected, reason) -> {
            if (connected) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                if (reason == null) {
                    updateStatus(ThingStatus.OFFLINE);
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, reason);

                    // unexpected offline state -> background reconnect
                    scheduler.execute(() -> {
                        try {
                            logger.info("{} - reconnecting...", getThing().getLabel());
                            Thread.sleep(10000);
                            if (!disposed && connection != null) {
                                connection.open();
                            }
                        } catch (InterruptedException e) {
                            logger.error("{}.", getThing().getLabel(), e);
                        }
                    });
                }
            }
        });

        connection.onMetricsUpdated((requests, bytes) -> {
            if (disposed) {
                return;
            }
            updateState(chRequests, new DecimalType(requests));
            updateState(chBytes, new DecimalType(bytes));
        });

        connection.onCycleTimeUpdated((duration) -> {
            if (disposed) {
                return;
            }
            updateState(chCycleTime, new DecimalType(duration));
        });

        connection.onDeviceStateUpdated((deviceId, state) -> {
            Set<SimpleBinaryChannelStatus> channels = statusChannels.values().stream()
                    .filter(p -> p.deviceId == deviceId).collect(Collectors.toSet());

            if (channels.isEmpty()) {
                return;
            }

            for (var ch : channels) {
                logger.debug("State update ch={},type={},id={}", ch.channelId, ch.channelType.getId(), ch.deviceId);
                if (ch.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_STATE_CURRENT)) {
                    ch.setState(new StringType(state.getState().toString()));
                } else if (ch.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_STATE_PREVIOUS)) {
                    ch.setState(new StringType(state.getPreviousState().toString()));
                } else if (ch.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_STATE_CHANGED)) {
                    ch.setState(new DateTimeType(state.getChangeDate()));
                } else if (ch.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_PACKET_LOST)) {
                    ch.setState(new DecimalType(state.getPacketLost()));
                } else if (ch.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_LAST_COMMUNICATION)) {
                    ch.setState(new DateTimeType(state.getLastCommunication()));
                }
            }
        });

        // temporarily status
        updateStatus(ThingStatus.UNKNOWN);

        // background initialization
        scheduler.schedule(() -> {
            try {
                Thread.sleep(1000);
                if (!disposed && connection != null) {
                    connection.open();
                }
            } catch (InterruptedException e) {
                logger.error("{}.", getThing().getLabel(), e);
            }
        }, INIT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void updateState(@Nullable ChannelUID channel, State state) {
        if (channel == null) {
            logger.debug("{} - updateState(...) channelID is null for state={}", getThing().getLabel(), state);
            return;
        }
        // logger.debug("{} - update channelID={}, state={}", getThing().getLabel(), channel, state);

        super.updateState(channel, state);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
        logger.debug("{} - bridge has been stopped", getThing().getLabel());
    }

    @SuppressWarnings({ "null", "unused" })
    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("{} - Command {} for channel {}", thing.getLabel(), command, channelUID);

        // get cached values
        if (command instanceof RefreshType) {
            if (channelUID.equals(chVersion)) {
                updateState(channelUID, new StringType(SimpleBinaryBindingConstants.VERSION));
            } else if (channelUID.equals(chTagCount)) {
                updateState(channelUID, new DecimalType(channelCount));
            } else {
                SimpleBinaryChannelStatus channel = statusChannels.get(channelUID);
                if (channel == null) {
                    logger.warn("{} - cannot get value to refresh. Channel {} not found.", thing.getLabel(),
                            channelUID);
                } else {
                    State s = channel.getState();
                    if (s != null) {
                        updateState(channelUID, s);
                    }
                }
                return;
            }
        }
    }

    /**
     * Update bridge configuration by all things channels
     */
    @SuppressWarnings("null")
    public void updateConfig() {
        int channelCount = 0;
        int stateChannelCount = 0, commandChannelCount = 0;

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
                if (ch.getCommandAddress() != null) {
                    commandChannelCount++;
                }
            }
        }

        var stateItems = new ArrayList<@NonNull SimpleBinaryChannel>(stateChannelCount);
        var commandItems = new ArrayList<@NonNull SimpleBinaryChannel>(commandChannelCount);
        var devices = new SimpleBinaryDeviceCollection();

        for (Thing th : getThing().getThings()) {
            var h = ((SimpleBinaryGenericHandler) th.getHandler());
            if (h == null) {
                continue;
            }
            for (SimpleBinaryChannel ch : h.channels.values()) {
                if (ch.getStateAddress() != null) {
                    stateItems.add(ch);
                    if (!devices.containsKey(ch.getStateAddress().getDeviceId())) {
                        devices.put(ch.getStateAddress().getDeviceId(),
                                new SimpleBinaryDevice(ch.getStateAddress().getDeviceId()).addThingHandler(h));
                    } else {
                        devices.get(ch.getStateAddress().getDeviceId()).addThingHandler(h);
                    }
                }
                if (ch.getCommandAddress() != null) {
                    commandItems.add(ch);
                    if (!devices.containsKey(ch.getCommandAddress().getDeviceId())) {
                        devices.put(ch.getCommandAddress().getDeviceId(),
                                new SimpleBinaryDevice(ch.getStateAddress().getDeviceId()).addThingHandler(h));
                    } else {
                        devices.get(ch.getCommandAddress().getDeviceId()).addThingHandler(h);
                    }
                }
            }
        }

        for (var s : statusChannels.values()) {
            if (!devices.containsKey(s.deviceId)) {
                devices.put(s.deviceId, new SimpleBinaryDevice(s.deviceId));
            }
        }

        if (connection != null) {
            var c = connection;
            c.setDataAreas(devices, stateItems, commandItems);
        }

        updateState(chTagCount, new DecimalType(channelCount));

        logger.debug("{} - updating {} channels({} read)", getThing().getLabel(), channelCount, stateChannelCount);
    }
}
