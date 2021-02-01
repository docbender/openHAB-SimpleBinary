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
package org.openhab.binding.simplebinary.internal.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simplebinary.internal.SimpleBinaryBindingConstants;
import org.openhab.binding.simplebinary.internal.handler.SimpleBinaryGenericHandler;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.util.UnitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author VitaTucek - Initial contribution
 *
 */
public class SimpleBinaryChannel {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryChannel.class);
    /** Channel ID */
    public ChannelUID channelId;
    /** ChannelType ID */
    public ChannelTypeUID channelType;
    /** State string address */
    public String stateAddress;
    /** Command string address */
    public String commandAddress;
    /** Number value unit */
    public String unit;
    /** Stored state value */
    private State value;
    /** Last command */
    private Command lastCommand;
    /** Channel configuration error */
    private String error;
    /** State address */
    private SimpleBinaryAddress stateAddressEx;
    /** Command address */
    private SimpleBinaryAddress commandAddressEx;
    /** Associated thing */
    private SimpleBinaryGenericHandler thing;
    /** Last value update */
    private long valueUpdateTime = 0;
    private boolean missingCommandReported = false;
    /** Defined unit */
    private Unit<?> unitInstance = null;
    /** Unit exists flag */
    private boolean unitExists = false;

    final private static Pattern numberAddressPattern = Pattern.compile("^((\\d+):(\\d+):(byte|word|dword|float))$");
    final private static Pattern stringAddressPattern = Pattern.compile("^((\\d+):(\\d+):(\\d+))$");
    final private static Pattern switchAddressPattern = Pattern.compile("^((\\d+):(\\d+))$");
    final private static Pattern contactAddressPattern = Pattern.compile("^((\\d+):(\\d+))$");
    final private static Pattern dimmerAddressPattern = Pattern.compile("^((\\d+):(\\d+))$");
    final private static Pattern colorAddressPattern = Pattern.compile("^((\\d+):(\\d+):(rgb|rgbw|hsb))$");
    final private static Pattern rollershutterAddressPattern = Pattern.compile("^((\\d+):(\\d+))$");

    @Override
    public String toString() {
        return String.format("ChID=%s,StateAddress=%s,CmdAddress=%s", channelId.getId(), stateAddress, commandAddress);
    }

    /**
     * Initialize channel from thing configuration
     *
     * @param handler Thing handler
     * @return True if initialization is OK. When initialization not succeed, reason can be obtain by getError()
     */
    public boolean init(SimpleBinaryGenericHandler handler) {
        missingCommandReported = false;
        if (handler == null) {
            error = "ThingHandler is null";
            return false;
        }

        thing = handler;

        if (channelId == null) {
            error = "ChannelID is null";
            return false;
        }
        if (channelType == null) {
            error = "ChannelType is null";
            return false;
        }
        if (stateAddress == null && commandAddress == null) {
            error = "No state or command address specified";
            return false;
        }

        if (stateAddress != null && (stateAddressEx = checkAddress(stateAddress)) == null) {
            return false;
        }

        if (commandAddress != null && (commandAddressEx = checkAddress(commandAddress)) == null) {
            return false;
        }

        if (unit != null) {
            unitInstance = UnitUtils.parseUnit(unit);
            if (unitInstance != null) {
                unitExists = true;
            } else {
                logger.warn("Channel {} - cannot parse defined unit({})", this.toString(), unit);
            }
        }
        return true;
    }

    /**
     * Clear instance
     */
    public void clear() {
        thing = null;
        value = null;
        lastCommand = null;
    }

    /**
     * Check string address obtained from configuration
     *
     * @param address Item address
     * @return
     */
    public @Nullable SimpleBinaryAddress checkAddress(String address) {
        final Matcher matcher;
        switch (channelType.getId()) {
            case SimpleBinaryBindingConstants.CHANNEL_NUMBER:
                matcher = numberAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Address format is <deviceID>:<address>:<type>. Address example 1:1:byte",
                            address, channelType.getId());
                    return null;
                }
                return new SimpleBinaryAddress(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                        matcher.group(4));
            case SimpleBinaryBindingConstants.CHANNEL_STRING:
                matcher = stringAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Address format is <deviceID>:<address>:<length>. Address example 1:1:32",
                            address, channelType.getId());
                    return null;
                }
                return new SimpleBinaryAddress(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                        Integer.parseInt(matcher.group(4)));
            case SimpleBinaryBindingConstants.CHANNEL_SWITCH:
                matcher = switchAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Address format is <deviceID>:<address>. Address example 1:1",
                            address, channelType.getId());
                    return null;
                }
                return new SimpleBinaryAddress(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            case SimpleBinaryBindingConstants.CHANNEL_CONTACT:
                matcher = contactAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Address format is <deviceID>:<address>. Address example 1:1",
                            address, channelType.getId());
                    return null;
                }
                return new SimpleBinaryAddress(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            case SimpleBinaryBindingConstants.CHANNEL_DIMMER:
                matcher = dimmerAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Address format is <deviceID>:<address>. Address example 1:1",
                            address, channelType.getId());
                    return null;
                }
                return new SimpleBinaryAddress(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            case SimpleBinaryBindingConstants.CHANNEL_COLOR:
                matcher = colorAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Address format is <deviceID>:<address>:<color type>. Address example 1:1:rgb",
                            address, channelType.getId());
                    return null;
                }
                return new SimpleBinaryAddress(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)),
                        matcher.group(4));
            case SimpleBinaryBindingConstants.CHANNEL_ROLLERSHUTTER:
                matcher = rollershutterAddressPattern.matcher(address);
                if (!matcher.matches()) {
                    error = String.format(
                            "Unsupported address '%s' for typeID=%s. Address format is <deviceID>:<address>. Address example 1:1",
                            address, channelType.getId());
                    return null;
                }
                return new SimpleBinaryAddress(Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
            default:
                return null;
        }
    }

    /**
     * Get error if init() failed
     *
     * @return
     */
    public @Nullable String getError() {
        return error;
    }

    /**
     * Get address for channel state
     *
     * @return
     */
    public @Nullable SimpleBinaryAddress getStateAddress() {
        return stateAddressEx;
    }

    /**
     * Get address for command
     *
     * @return
     */
    public @Nullable SimpleBinaryAddress getCommandAddress() {
        return commandAddressEx;
    }

    /**
     * Get number value unit
     *
     * @return Unit
     */
    public Unit<?> getUnit() {
        return unitInstance;
    }

    /**
     * Check if unit presented
     *
     * @return Unit exists flag
     */
    public boolean hasUnit() {
        return unitExists;
    }

    public void setState(State state) {
        value = state;
        if (thing == null) {
            return;
        }
        thing.updateState(channelId, state);
        valueUpdateTime = System.currentTimeMillis();
        clearError();
    }

    public @Nullable State getState() {
        return value;
    }

    public void setCommand(Command command) {
        lastCommand = command;
    }

    public @Nullable Command getCommand() {
        return lastCommand;
    }

    public @Nullable SimpleBinaryGenericHandler getThing() {
        return thing;
    }

    public void setError(String message) {
        if (thing == null) {
            return;
        }
        thing.setError(message);
    }

    private void clearError() {
        if (thing == null) {
            return;
        }

        thing.clearError();
    }

    public boolean isMissingCommandReported() {
        if (!missingCommandReported) {
            missingCommandReported = true;
            return false;
        }
        return true;
    }
}