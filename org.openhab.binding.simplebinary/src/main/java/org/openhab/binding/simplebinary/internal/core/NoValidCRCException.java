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
 * Exception class for no valid crc
 *
 * @author Vita Tucek
 * @since 1.9.0
 */

public class NoValidCRCException extends Exception {
    private static final long serialVersionUID = -4524739106640114564L;

    public NoValidCRCException() {
        super("CRC not valid");
    }

    public NoValidCRCException(String msg) {
        super(msg);
    }

    public NoValidCRCException(byte receivedCrc, byte expectedCrc) {
        super(String.format("CRC not valid. Receive/expected 0x%02X/0x%02X", receivedCrc, expectedCrc));
    }
}
