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
package org.openhab.binding.simplebinary.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link SimpleBinaryBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
public class SimpleBinaryBindingConstants {
    public static final String VERSION = "3.3.0";

    private static final String BINDING_ID = "simplebinary";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_UART_BRIDGE = new ThingTypeUID(BINDING_ID, "uart_bridge");
    public static final ThingTypeUID THING_TYPE_TCP_BRIDGE = new ThingTypeUID(BINDING_ID, "tcp_bridge");
    public static final ThingTypeUID THING_TYPE_GENERIC = new ThingTypeUID(BINDING_ID, "generic_device");

    // List of all Bridge Channel ids
    public static final String CHANNEL_VERSION = "chVersionTypeID";
    public static final String CHANNEL_TAG_COUNT = "chTagCountTypeID";
    public static final String CHANNEL_REQUESTS = "chRequestsTypeID";
    public static final String CHANNEL_BYTES = "chBytesTypeID";
    public static final String CHANNEL_CYCLE_TIME = "chCycleTimeTypeID";
    public static final String CHANNEL_STATE_CURRENT = "devState";
    public static final String CHANNEL_STATE_PREVIOUS = "devPreviousState";
    public static final String CHANNEL_STATE_CHANGED = "devStateChanged";
    public static final String CHANNEL_PACKET_LOST = "devPacketLost";
    public static final String CHANNEL_LAST_COMMUNICATION = "devLastCommunication";

    // List of all Channel Type UIDs
    public static final ChannelTypeUID CHANNEL_TYPE_VERSION = new ChannelTypeUID(BINDING_ID, CHANNEL_VERSION);
    public static final ChannelTypeUID CHANNEL_TYPE_TAG_COUNT = new ChannelTypeUID(BINDING_ID, CHANNEL_TAG_COUNT);
    public static final ChannelTypeUID CHANNEL_TYPE_REQUESTS = new ChannelTypeUID(BINDING_ID, CHANNEL_REQUESTS);
    public static final ChannelTypeUID CHANNEL_TYPE_BYTES = new ChannelTypeUID(BINDING_ID, CHANNEL_BYTES);
    public static final ChannelTypeUID CHANNEL_TYPE_CYCLE_TIME = new ChannelTypeUID(BINDING_ID, CHANNEL_CYCLE_TIME);

    // List of all Thing Channel ids
    public static final String CHANNEL_NUMBER = "chNumber";
    public static final String CHANNEL_COLOR = "chColor";
    public static final String CHANNEL_STRING = "chString";
    public static final String CHANNEL_CONTACT = "chContact";
    public static final String CHANNEL_SWITCH = "chSwitch";
    public static final String CHANNEL_DIMMER = "chDimmer";
    public static final String CHANNEL_ROLLERSHUTTER = "chRollershutter";
}
