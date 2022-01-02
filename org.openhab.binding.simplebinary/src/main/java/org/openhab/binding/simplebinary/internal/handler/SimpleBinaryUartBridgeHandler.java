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
import org.openhab.binding.simplebinary.internal.config.SimpleBinaryUartConfiguration;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryPollControl;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryUART;
import org.openhab.core.io.transport.serial.SerialPortManager;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SimpleBinaryUartBridgeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author VitaTucek - Initial contribution
 */
@NonNullByDefault
public class SimpleBinaryUartBridgeHandler extends SimpleBinaryBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(SimpleBinaryUartBridgeHandler.class);

    private @Nullable SimpleBinaryUartConfiguration config;

    private @NonNullByDefault({}) SerialPortManager serialPortManager;

    public SimpleBinaryUartBridgeHandler(Bridge bridge, SerialPortManager serialPortManager) {
        super(bridge);

        this.serialPortManager = serialPortManager;
    }

    @Override
    public void initialize() {
        config = getConfigAs(SimpleBinaryUartConfiguration.class);

        logger.debug(
                "{} - Bridge configuration: Port={},BaudRate={},PollControl={},ForceRTS={},InvertedRTS={},Charset={},Timeout={},PollRate={},RetryCount={},DegradeTime={},DiscardCommand={}",
                getThing().getLabel(), config.port, config.baudRate, config.pollControl, config.forceRTS,
                config.invertedRTS, config.charset, config.timeout, config.pollRate, config.retryCount,
                config.degradeTime, config.discardCommands);

        // configuration validation
        boolean valid = true;

        if (config.port == null || config.port.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Port not specified");
            valid = false;
            return;
        }

        if (config.baudRate < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid Baud Rate");
            valid = false;
            return;
        }

        if (config.pollControl == null
                || !(config.pollControl.equals("ONCHANGE") || config.pollControl.equals("ONSCAN"))) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Invalid Pool Control.");
            valid = false;
            return;
        }

        if (config.pollRate <= 0) {
            logger.warn(
                    "{} - poll rate is set to 0. That means new data will be read immediately after previous one will be finished.",
                    getThing().getLabel());
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

        if (config.retryCount < 0) {
            config.retryCount = 0;
        }

        if (config.degradeTime < 0) {
            config.degradeTime = 0;
        }

        if (!valid) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
            logger.error(
                    "{} - Bridge configuration is invalid. Port={},BaudRate={},PollControl={},ForceRTS={},InvertedRTS={},Charset={},PollRate={}",
                    getThing().getLabel(), config.port, config.baudRate, config.pollControl, config.forceRTS,
                    config.invertedRTS, config.charset, config.pollRate);
        }

        connection = new SimpleBinaryUART(serialPortManager, config.port, config.baudRate,
                SimpleBinaryPollControl.valueOf(config.pollControl), config.forceRTS, config.invertedRTS,
                config.pollRate, charset, config.timeout, config.retryCount, config.degradeTime,
                config.discardCommands);

        super.initialize();
    }
}
