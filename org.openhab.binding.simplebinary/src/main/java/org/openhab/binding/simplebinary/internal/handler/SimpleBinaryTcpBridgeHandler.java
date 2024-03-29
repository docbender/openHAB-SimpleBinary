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
package org.openhab.binding.simplebinary.internal.handler;

import java.nio.charset.Charset;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.simplebinary.internal.config.SimpleBinaryTcpConfiguration;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryIP;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SimpleBinaryTcpBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
public class SimpleBinaryTcpBridgeHandler extends SimpleBinaryBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(SimpleBinaryTcpBridgeHandler.class);

    private @Nullable SimpleBinaryTcpConfiguration config;

    public SimpleBinaryTcpBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        config = getConfigAs(SimpleBinaryTcpConfiguration.class);

        logger.debug(
                "{} - Bridge configuration: Host/IP={},Port={},Charset={},Timeout={},DegradeMaxFailuresCount={},DegradeTime={},DiscardCommand={},SyncCommand={}",
                getThing().getLabel(), config.address, config.port, config.charset, config.timeout,
                config.degradeMaxFailuresCount, config.degradeTime, config.discardCommand, config.syncCommand);

        // configuration validation
        boolean valid = true;

        if (config.port < 0 || config.port > 65535) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid port number");
            valid = false;
            return;
        }

        Charset charset;
        if (config.charset == null || config.charset.isBlank()) {
            charset = Charset.defaultCharset();
        } else if (!Charset.isSupported(config.charset)) {
            charset = Charset.defaultCharset();
            logger.warn("{} - charset '{}' is not recognized. Default one is used.", getThing().getLabel(),
                    config.charset);
        } else {
            charset = Charset.forName(config.charset);
        }

        logger.info("{} - Current charset {}", getThing().getLabel(), charset.name());

        if (!valid) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            logger.error("{} - Bridge configuration is invalid. Host/IP={},Port={},Charset={}", getThing().getLabel(),
                    config.address, config.port, config.charset);
        }

        connection = new SimpleBinaryIP(config.address, config.port, charset, config.timeout,
                config.degradeMaxFailuresCount, config.degradeTime, config.discardCommand, config.syncCommand);

        super.initialize();
    }
}
