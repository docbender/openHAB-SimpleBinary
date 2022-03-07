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
package org.openhab.binding.simplebinary.internal.config;

/**
 * The {@link SimpleBinaryUartConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author VitaTucek - Initial contribution
 */
public class SimpleBinaryUartConfiguration {

    /**
     * Communication port
     */
    public String port = "";

    /**
     * Communication port baud rate
     */
    public int baudRate = 9600;

    /**
     * Communication poll control
     */
    public String pollControl = "ONCHANGE";

    /**
     * Communication port force RTS pin activation
     */
    public boolean forceRTS = false;

    /**
     * Invert RTS pin state
     */
    public boolean invertedRTS = false;

    /**
     * String data code page
     */
    public String charset = "";

    /**
     * Device poll rate
     */
    public int pollRate = 1000;

    /**
     * Response timeout
     */
    public int timeout = 1000;

    /**
     * Number of retries before device is set into degrade mode
     */
    public int degradeMaxFailuresCount = 0;

    /**
     * Device in degrade mode spend time (ms)
     */
    public int degradeTime = 5000;

    /**
     * Commands for offline device will be discarded
     */
    public boolean discardCommand = false;

    /**
     * Sync commands for device that get online
     */
    public boolean syncCommand = false;
}
