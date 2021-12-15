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
import java.nio.ByteOrder;

import org.openhab.binding.simplebinary.internal.SimpleBinaryBindingConstants;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class holding item data and config
 *
 * @author Vita Tucek
 * @since 1.9.0
 *
 */
public class SimpleBinaryItem extends SimpleBinaryItemData {

    private SimpleBinaryChannel item;

    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryProtocol.class);

    /**
     * Constructor for items
     *
     * @param item
     * @param messageId
     * @param deviceId
     * @param itemData
     */
    public SimpleBinaryItem(SimpleBinaryChannel item, byte messageId, int deviceId, int itemAddress, byte[] itemData) {
        super(messageId, deviceId, itemAddress, itemData);

        this.item = item;
    }

    /**
     * Return item State from itemData
     *
     * @return
     * @throws Exception
     */
    public State getState() throws Exception {
        if (item == null) {
            return null;
        }

        logger.trace("Item {}, address={}, deviceID={}, messageID=0x{}, datalenght={}", item.channelId,
                this.itemAddress, this.deviceId, Integer.toHexString(this.messageId & 0xFF), this.itemData.length);

        if (item.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_NUMBER)) {
            if (item.getStateAddress().getType() == SimpleBinaryTypes.FLOAT) {
                if (itemData.length != 4) {
                    throw new Exception(
                            "getState(): cannot convert to item " + item.channelId + " to FLOAT. Wrong data length.");
                } else {
                    logger.trace("converting to FLOAT: {}", SimpleBinaryProtocol.arrayToString(itemData, 4));

                    ByteBuffer bf = ByteBuffer.wrap(itemData);
                    bf.order(ByteOrder.LITTLE_ENDIAN);
                    float value = bf.getFloat();
                    logger.trace("floatbits: {}", Float.floatToIntBits(value));

                    logger.trace("FLOAT value converted: {}", value);

                    return item.hasUnit() ? new QuantityType<>(value, item.getUnit()) : new DecimalType(value);
                }
            } else {
                if (itemData.length == 1) {
                    return item.hasUnit() ? new QuantityType<>(itemData[0], item.getUnit())
                            : new DecimalType(itemData[0]);
                } else if (itemData.length == 2) {
                    return item.hasUnit()
                            ? new QuantityType<>(((itemData[0] & 0xFF | ((itemData[1] & 0xFF) << 8))), item.getUnit())
                            : new DecimalType(((itemData[0] & 0xFF | ((itemData[1] & 0xFF) << 8))));
                } else if (itemData.length == 4) {
                    return item.hasUnit()
                            ? new QuantityType<>(((itemData[0] & 0xFF | ((itemData[1] & 0xFF) << 8)
                                    | ((itemData[2] & 0xFF) << 16) | ((itemData[3] & 0xFF) << 24))), item.getUnit())
                            : new DecimalType(((itemData[0] & 0xFF | ((itemData[1] & 0xFF) << 8)
                                    | ((itemData[2] & 0xFF) << 16) | ((itemData[3] & 0xFF) << 24))));
                } else {
                    throw new Exception("getState(): cannot convert to item " + item.channelId + " to "
                            + item.getStateAddress().getType() + ". Wrong data length.");
                }
            }
        } else if (item.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_SWITCH)) {
            if (itemData[0] == 1) {
                return OnOffType.ON;
            } else {
                return OnOffType.OFF;
            }
        } else if (item.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_DIMMER)) {
            if (itemData.length < 3) {
                return new PercentType(itemData[0]);
            } else {
                throw new Exception("getState(): cannot convert to item " + item.channelId + " to "
                        + item.getStateAddress().getType() + ". Data length > 2");
            }
        } else if (item.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_COLOR)) {
            // logger.info("Color data = {},{},{},{}", itemData[0] & 0xFF, itemData[1] & 0xFF, itemData[2] & 0xFF,
            // itemData[3] & 0xFF);
            if (item.getStateAddress().getType() == SimpleBinaryTypes.HSB) {
                return new HSBType(new DecimalType((itemData[0] & 0xFF) + ((itemData[1] & 0xFF) << 8)),
                        new PercentType(itemData[2] & 0xFF), new PercentType(itemData[3] & 0xFF));
            } else if (item.getStateAddress().getType() == SimpleBinaryTypes.RGB) {
                return HSBType.fromRGB(itemData[0] & 0xFF, itemData[1] & 0xFF, itemData[2] & 0xFF);
            } else if (item.getStateAddress().getType() == SimpleBinaryTypes.RGBW) {
                return HSBType.fromRGB(itemData[0] & 0xFF, itemData[1] & 0xFF, itemData[2] & 0xFF);
            } else {
                throw new Exception("getState(): cannot convert to item " + item.channelId + " to "
                        + item.getStateAddress().getType() + ".");
            }
        } else if (item.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_STRING)) {
            String str = new String(itemData);
            return new StringType(str);
        } else if (item.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_CONTACT)) {
            if (itemData[0] == 1) {
                return OpenClosedType.OPEN;
            } else {
                return OpenClosedType.CLOSED;
            }
        } else if (item.channelType.getId().equals(SimpleBinaryBindingConstants.CHANNEL_ROLLERSHUTTER)) {
            if (itemData.length < 3) {
                return new PercentType(itemData[0]);
            } else {
                throw new Exception("getState(): cannot convert to item " + item.channelId + " to "
                        + item.getStateAddress().getType() + ". Data length > 2");
            }
        } else {
            throw new Exception("getState(): cannot convert channel " + item.channelId + " to "
                    + item.getStateAddress().getType() + ". Unsupported channel type: " + item.channelType.getId());
        }
    }

    /**
     * Return item config
     *
     * @return
     */
    public SimpleBinaryChannel getConfig() {
        return item;
    }
}
