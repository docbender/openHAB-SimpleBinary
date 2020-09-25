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
    DATA,
    OK,
    RESEND,
    NODATA,
    CHECKNEWDATA,
    QUERY,
    UNKNOWN_DATA,
    UNKNOWN,
    UNKNOWN_ADDRESS,
    SAVING_ERROR,
    HI,
    WANT_EVERYTHING
}
