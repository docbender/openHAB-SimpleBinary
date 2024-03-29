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
 * Timeout interface
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public interface SimpleBinaryIRequestTimeouted {
    void timeoutEvent(SimpleBinaryDevice device);
}
