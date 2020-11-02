package org.openhab.binding.simplebinary.internal.core;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simplebinary.internal.handler.SimpleBinaryBridgeHandler;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleBinaryChannelStatus {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryChannelStatus.class);

    public ChannelUID channelId;
    public ChannelTypeUID channelType;
    public int deviceId;
    public String commandAddress;
    private State value;
    private String error;
    private SimpleBinaryBridgeHandler bridge;
    private long valueUpdateTime = 0;

    @Override
    public String toString() {
        return String.format("ChID=%s,DeviceId=%s,TypeId=%s", channelId.getId(), deviceId, channelType.getId());
    }

    public boolean init(SimpleBinaryBridgeHandler handler) {
        if (handler == null) {
            error = "ThingHandler is null";
            return false;
        }

        bridge = handler;

        if (channelId == null) {
            error = "ChannelID is null";
            return false;
        }
        if (channelType == null) {
            error = "ChannelType is null";
            return false;
        }
        if (deviceId < 0 || deviceId > 255) {
            error = "DeviceId is out of range";
            return false;
        }

        return true;
    }

    public void clear() {
        bridge = null;
        value = null;
    }

    public @Nullable String getError() {
        return error;
    }

    public void setState(State state) {
        value = state;
        if (bridge == null) {
            return;
        }
        bridge.updateState(channelId, state);
        valueUpdateTime = System.currentTimeMillis();
    }

    public @Nullable State getState() {
        return value;
    }

    public @Nullable SimpleBinaryBridgeHandler getThing() {
        return bridge;
    }

    @Override
    public int hashCode() {
        return channelId.hashCode();
    }

}