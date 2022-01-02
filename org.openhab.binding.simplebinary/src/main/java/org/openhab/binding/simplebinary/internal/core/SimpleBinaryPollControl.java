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
 * Enumeration of communication type used for configured device
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public enum SimpleBinaryPollControl {
    ONCHANGE(1),
    ONSCAN(2),
    NONE(0);

    private final int value;

    private SimpleBinaryPollControl(int value) {
        this.value = value;
    }

    public static SimpleBinaryPollControl valueOf(int value) {
        if (value == 1) {
            return SimpleBinaryPollControl.ONCHANGE;
        }
        if (value == 2) {
            return SimpleBinaryPollControl.ONSCAN;
        }

        return SimpleBinaryPollControl.NONE;
    }

    public int getValue() {
        return value;
    }
}
