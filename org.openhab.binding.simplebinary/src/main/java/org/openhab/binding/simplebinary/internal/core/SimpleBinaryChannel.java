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

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simplebinary.internal.SimpleBinaryBindingConstants;
import org.openhab.binding.simplebinary.internal.handler.SimpleBinaryGenericHandler;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author VitaTucek - Initial contribution
 *
 */
public class SimpleBinaryChannel {
    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryChannel.class);

    public ChannelUID channelId;
    public ChannelTypeUID channelType;
    public String stateAddress;
    public String commandAddress;
    private State value;
    private Command lastCommand;
    private String error;
    private SimpleBinaryAddress stateAddressEx;
    private SimpleBinaryAddress commandAddressEx;
    private SimpleBinaryGenericHandler thing;
    private long valueUpdateTime = 0;
    private boolean missingCommandReported = false;

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

        return true;
    }

    public void clear() {
        thing = null;
        value = null;
        lastCommand = null;
    }

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

    public @Nullable String getError() {
        return error;
    }

    public @Nullable SimpleBinaryAddress getStateAddress() {
        return stateAddressEx;
    }

    public @Nullable SimpleBinaryAddress getCommandAddress() {
        return commandAddressEx;
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