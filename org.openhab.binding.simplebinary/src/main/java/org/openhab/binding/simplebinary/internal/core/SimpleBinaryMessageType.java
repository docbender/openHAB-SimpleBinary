/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

/**
 * Message type enumerator
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public enum SimpleBinaryMessageType {
    /**
     * Message contains data
     */
    DATA,
    /**
     * Command accepted by device
     */
    OK,
    /**
     * Device received data with wrong CRC
     */
    RESEND,
    /**
     * No data to send from device
     */
    NODATA,
    /**
     * Ask device to send new data
     */
    CHECKNEWDATA,
    /**
     * Query to device
     */
    QUERY,
    /**
     * Device received data with unknown type
     */
    UNKNOWN_DATA,
    /**
     * Unknown message
     */
    UNKNOWN,
    /**
     * Device received data with unknown address
     */
    UNKNOWN_ADDRESS,
    /**
     * Device can not save data
     */
    SAVING_ERROR,
    /**
     * Device say "Hi" when connection established
     */
    HI,
    /**
     * Give me all data
     */
    WANT_EVERYTHING
}
