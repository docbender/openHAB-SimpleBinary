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
public enum SimpleBinaryPoolControl {
    ONCHANGE(1),
    ONSCAN(2),
    NONE(0);

    private final int value;

    private SimpleBinaryPoolControl(int value) {
        this.value = value;
    }

    public static SimpleBinaryPoolControl valueOf(int value) {
        if (value == 1) {
            return SimpleBinaryPoolControl.ONCHANGE;
        }
        if (value == 2) {
            return SimpleBinaryPoolControl.ONSCAN;
        }

        return SimpleBinaryPoolControl.NONE;
    }

    public int getValue() {
        return value;
    }
}
