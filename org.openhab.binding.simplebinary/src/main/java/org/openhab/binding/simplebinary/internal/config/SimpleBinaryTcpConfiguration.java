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
 * The {@link SimpleBinaryTcpConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author VitaTucek - Initial contribution
 */
public class SimpleBinaryTcpConfiguration {

    /**
     * Communication address
     */
    public String address = "";

    /**
     * Communication port baud rate
     */
    public int port = 43243;

    /**
     * String data code page
     */
    public String charset = "";
}
