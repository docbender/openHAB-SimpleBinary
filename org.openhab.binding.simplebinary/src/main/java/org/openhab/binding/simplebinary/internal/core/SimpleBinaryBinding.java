/**
 * Copyright (c) 2010-2016, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.simplebinary.SimpleBinaryBindingProvider;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericBindingProvider.DataDirectionFlow;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericBindingProvider.DeviceConfig;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.binding.simplebinary.internal.core.SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implement this class if you are going create an actively polling service like querying a Website/Device.
 *
 * @author Vita Tucek
 * @since 1.9.0
 */
public class SimpleBinaryBinding extends AbstractActiveBinding<SimpleBinaryBindingProvider> {

    private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryBinding.class);

    public static final Double JavaVersion = Double.parseDouble(System.getProperty("java.specification.version"));;

    /**
     * The BundleContext. This is only valid when the bundle is ACTIVE. It is set in the activate() method and must not
     * be accessed anymore once the deactivate() method was called or before activate() was called.
     */
    @SuppressWarnings("unused")
    private BundleContext bundleContext;

    /**
     * the refresh interval which is used to poll values from the SimpleBinary server (optional, defaults to 10000ms)
     */
    private long refreshInterval = 10000;

    // devices
    private Map<String, SimpleBinaryGenericDevice> devices = new HashMap<String, SimpleBinaryGenericDevice>();
    // data item configs
    private Map<String, SimpleBinaryBindingConfig> items = new HashMap<String, SimpleBinaryBindingConfig>();
    // info item configs
    private Map<String, SimpleBinaryInfoBindingConfig> infoItems = new HashMap<String, SimpleBinaryInfoBindingConfig>();

    /**
     * used to store events that we have sent ourselves; we need to remember them for not reacting to them
     */
    public static class Update {
        private String itemName;
        private State state;

        Update(final String itemName, final State state) {
            this.itemName = itemName;
            this.state = state;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof Update)) {
                return false;
            }
            return (this.itemName == null ? ((Update) o).itemName == null : this.itemName.equals(((Update) o).itemName))
                    && (this.state == null ? ((Update) o).state == null : this.state.equals(((Update) o).state));
        }

        @Override
        public int hashCode() {
            return (this.itemName == null ? 0 : this.itemName.hashCode())
                    ^ (this.state == null ? 0 : this.state.hashCode());
        }
    }

    public static List<Update> ignoreEventList = Collections.synchronizedList(new ArrayList<Update>());

    public SimpleBinaryBinding() {
    }

    /**
     * Called by the SCR to activate the component with its configuration read from CAS
     *
     * @param bundleContext
     *            BundleContext of the Bundle that defines this component
     * @param configuration
     *            Configuration properties for this component obtained from the ConfigAdmin service
     */
    public void activate(final BundleContext bundleContext, final Map<String, Object> configuration) {
        this.bundleContext = bundleContext;

        logger.debug("activate() method is called!");
        logger.info(bundleContext.getBundle().toString());

        startBinding(configuration);
    }

    /**
     * Parse configuration and start communications
     *
     * @param configuration
     *            Configuration properties for this component obtained from the ConfigAdmin service
     */
    public void startBinding(final Map<String, Object> configuration) {
        // the configuration is guaranteed not to be null, because the component definition has the
        // configuration-policy set to require. If set to 'optional' then the configuration may be null

        // to override the default refresh interval one has to add a
        // parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
        String refreshIntervalString = (String) configuration.get("refresh");
        if (StringUtils.isNotBlank(refreshIntervalString)) {
            refreshInterval = Long.parseLong(refreshIntervalString);
        }

        logger.debug("RefreshInterval={}", refreshInterval);

        // if devices collection isn't empty
        if (devices.size() > 0) {
            logger.debug("Device count {}", devices.size());

            // close all connections
            for (Map.Entry<String, SimpleBinaryGenericDevice> item : devices.entrySet()) {
                item.getValue().close();
                item.getValue().unsetBindingData();
            }
        }

        devices.clear();

        Pattern rgxUARTKey = Pattern.compile("^port\\d*$");
        Pattern rgxUARTValue = Pattern.compile("^(\\S+:\\d+)(;((onscan)|(onchange)))?(;((forceRTS)|(forceRTSInv)))?$");
        Pattern rgxTCPServerKey = Pattern.compile("^tcpserver$");
        Pattern rgxTCPServerValue = Pattern.compile("^((\\S+[:])?(\\d+))(;((onscan)|(onchange)|(none)))?$");
        Pattern rgxTCPServerClientKey = Pattern.compile("^tcpserverclientlist$");
        Pattern rgxTCPServerClientValue = Pattern.compile(
                "^((\\d+)[:](\\d{1,3}[.]\\d{1,3}[.]\\d{1,3}[.]\\d{1,3})([:]iplock)?)$", Pattern.CASE_INSENSITIVE);

        logger.debug("Looking for device configuration...");

        for (Map.Entry<String, Object> item : configuration.entrySet()) {
            logger.trace("key:{}/value:{}", item.getKey(), item.getValue());

            // port
            if (rgxUARTKey.matcher(item.getKey()).matches()) {
                String portString = (String) item.getValue();
                if (StringUtils.isNotBlank(portString)) {

                    Matcher matcher = rgxUARTValue.matcher(portString);

                    if (!matcher.matches()) {
                        logger.error("{}: Wrong port configuration: {}", item.getKey(), portString);
                        logger.info(
                                "Port configuration example: port=COM1:9600 or port=/dev/ttyS1:9600;onchange;forceRTS");
                        setProperlyConfigured(false);
                        return;
                    }

                    SimpleBinaryPoolControl simpleBinaryPoolControl = SimpleBinaryPoolControl.ONCHANGE;
                    int speed = 9600;
                    boolean forceRTS = false;
                    boolean RTSInversion = false;

                    // check group 3
                    if (matcher.group(3) != null) {

                        if (matcher.group(3).equals("onscan")) {
                            simpleBinaryPoolControl = SimpleBinaryPoolControl.ONSCAN;
                        }
                    }
                    // check group 7
                    if (matcher.group(7) != null) {
                        forceRTS = true;

                        if (matcher.group(9) != null) {
                            RTSInversion = true;
                        }
                    }

                    if (matcher.group(1).contains(":")) {
                        String[] s = matcher.group(1).split(":");

                        portString = s[0];
                        speed = Integer.valueOf(s[1]);
                    } else {
                        portString = matcher.group(1);
                    }

                    logger.info("SimpleBinary port={} speed={} mode={} forcerts={} rtsInversion={}", portString, speed,
                            simpleBinaryPoolControl, forceRTS, RTSInversion);

                    SimpleBinaryUART uartDevice = new SimpleBinaryUART(item.getKey(), portString, speed,
                            simpleBinaryPoolControl, forceRTS, RTSInversion);
                    uartDevice.setBindingData(eventPublisher, items, infoItems, devices);

                    devices.put(item.getKey(), uartDevice);
                } else {
                    logger.error("Blank port configuration");
                    setProperlyConfigured(false);
                    return;
                }
            } else if (rgxTCPServerKey.matcher(item.getKey()).matches()) {
                String portString = (String) item.getValue();
                if (StringUtils.isNotBlank(portString)) {
                    Matcher matcher = rgxTCPServerValue.matcher(portString);

                    if (!matcher.matches()) {
                        logger.error("{}: Wrong port configuration: {}", item.getKey(), portString);
                        logger.info(
                                "TCP server configuration example: tcpserver=27540 or tcpserver=192.168.0.1:27540;onchange");
                        setProperlyConfigured(false);
                        return;
                    }

                    SimpleBinaryPoolControl simpleBinaryPoolControl = SimpleBinaryPoolControl.NONE;
                    int port = SimpleBinaryIP.DEFAULT_PORT;

                    // check group 5
                    if (matcher.group(5) != null) {

                        if (matcher.group(5).equals("onscan")) {
                            simpleBinaryPoolControl = SimpleBinaryPoolControl.ONSCAN;
                        } else if (matcher.group(5).equals("onchange")) {
                            simpleBinaryPoolControl = SimpleBinaryPoolControl.ONCHANGE;
                        }

                    }

                    // port
                    port = Integer.valueOf(matcher.group(1));

                    // bind address
                    if (matcher.group(2) != null) {
                        portString = matcher.group(2);
                    } else {
                        portString = "";
                    }

                    logger.info("SimpleBinary bind_address={} port={} mode={} ", portString, port,
                            simpleBinaryPoolControl);

                    SimpleBinaryIP ipDevice = new SimpleBinaryIP(item.getKey(), portString, port,
                            simpleBinaryPoolControl);
                    ipDevice.setBindingData(eventPublisher, items, infoItems, devices);
                    devices.put(item.getKey(), ipDevice);
                } else {
                    logger.error("Blank TCP server configuration");
                    setProperlyConfigured(false);
                    return;
                }
            }
        }

        if (devices.size() == 0) {
            logger.warn("No configurations exist.");
            setProperlyConfigured(false);
            return;
        }

        if (devices.containsKey("tcpserver")) {
            SimpleBinaryIP server = ((SimpleBinaryIP) devices.get("tcpserver"));

            for (Map.Entry<String, Object> item : configuration.entrySet()) {
                if (rgxTCPServerClientKey.matcher(item.getKey()).matches()) {
                    String clientsString = (String) item.getValue();

                    if (StringUtils.isNotBlank(clientsString)) {

                        String[] clients = clientsString.split(";");

                        for (String s : clients) {
                            Matcher matcher = rgxTCPServerClientValue.matcher(s);

                            if (!matcher.matches()) {
                                logger.error("{}: Wrong TCP client configuration: {}", item.getKey(), s);
                                logger.info(
                                        "Configuration example: tcpserverclientlist=1:192.168.0.1;2:192.168.0.5:iplock");
                                setProperlyConfigured(false);
                                return;
                            }

                            // add device to collection - device ID, device IP address
                            server.addDevice(matcher.group(2), matcher.group(3), matcher.group(4) != null);
                        }

                    } else {
                        logger.error("Blank TCP client list configuration");
                    }
                }
            }
        }

        logger.debug("setProperlyConfigured ");

        setProperlyConfigured(true);

        for (Map.Entry<String, SimpleBinaryGenericDevice> item : devices.entrySet()) {
            item.getValue().open();
        }
    }

    /**
     * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin service.
     *
     * @param configuration
     *            Updated configuration properties
     */
    public void modified(final Map<String, Object> configuration) {
        // update the internal configuration accordingly

        logger.info("Binding configuration changed. Binding is going to restart...");

        startBinding(configuration);
    }

    /**
     * Called by the SCR to deactivate the component when either the configuration is removed or mandatory references
     * are no longer satisfied or the component has simply been stopped.
     *
     * @param reason
     *            Reason code for the deactivation:<br>
     *            <ul>
     *            <li>0 – Unspecified
     *            <li>1 – The component was disabled
     *            <li>2 – A reference became unsatisfied
     *            <li>3 – A configuration was changed
     *            <li>4 – A configuration was deleted
     *            <li>5 – The component was disposed
     *            <li>6 – The bundle was stopped
     *            </ul>
     */
    public void deactivate(final int reason) {
        this.bundleContext = null;
        // deallocate resources here that are no longer needed and
        // should be reset when activating this binding again

        logger.info("Binding configuration was removed. Binding wil be deactivated...");

        for (Map.Entry<String, SimpleBinaryGenericDevice> item : devices.entrySet()) {
            item.getValue().unsetBindingData();
            item.getValue().close();
        }

        devices.clear();
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected String getName() {
        return "SimpleBinary communication service";
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected void execute() {
        // the frequently executed code (polling) goes here ...
        if (logger.isDebugEnabled()) {
            logger.debug("execute() method is called!");
        }

        if (devices != null && devices.size() > 0) {
            // go through all devices
            for (Map.Entry<String, SimpleBinaryGenericDevice> item : devices.entrySet()) {
                // check device for new data
                item.getValue().checkNewData();
                // check device for timeout connection (if implemented)
                item.getValue().checkConnectionTimeout();
            }
        }
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected void internalReceiveCommand(String itemName, Command command) {
        // the code being executed when a command was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        if (logger.isDebugEnabled()) {
            logger.debug("internalReceiveCommand({},{}) is called!", itemName, command);
        }

        // retrieve item config
        SimpleBinaryBindingConfig config = items.get(itemName);

        if (config == null) {
            logger.warn("No config for item: {}", itemName);
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Config: {}", config);
        }

        updateState(config, command);
    }

    /**
     * @{inheritDoc
     */
    @Override
    protected void internalReceiveUpdate(String itemName, State newState) {
        // the code being executed when a state was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        if (logger.isTraceEnabled()) {
            logger.trace("internalReceiveUpdate({},{}) is called!", itemName, newState);
        }

        if (!isEcho(itemName, newState)) {
            SimpleBinaryBindingConfig config = items.get(itemName);
            if (config == null) {
                return;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("internalReceiveUpdate({},{}) - updating binding item!", itemName, newState);
            }
            updateState(config, newState);
        }
    }

    /**
     * Check if item update is not from this binding
     *
     * @param itemName
     *            Item name
     * @param state
     *            Item state
     * @return
     *         True if update came from this binding
     */
    private boolean isEcho(String itemName, State state) {
        if (ignoreEventList.remove(new Update(itemName, state))) {
            logger.trace("This received event (item='{}', state='{}') was post by this binding -> ignore!", itemName,
                    state.toString());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Update binding item value
     *
     * @param itemConfig
     *            Item specific configuration
     * @param state
     *            Item state
     */
    private void updateState(SimpleBinaryBindingConfig itemConfig, Type state) {
        itemConfig.setState(state);

        int updates = 0;

        // through all devices
        for (DeviceConfig d : itemConfig.devices) {
            // send to output devices only
            if (d.dataDirection == DataDirectionFlow.INPUT) {
                continue;
            }

            SimpleBinaryGenericDevice device = devices.get(d.deviceName);

            if (device != null) {
                try {
                    updates++;
                    device.sendData(itemConfig.item.getName(), state, itemConfig, d);
                } catch (Exception ex) {
                    logger.error("updateState(): file:{}|line:{}|method:{}|message:{}",
                            ex.getStackTrace()[0].getFileName(), ex.getStackTrace()[0].getLineNumber(),
                            ex.getStackTrace()[0].getMethodName(), ex.getMessage());
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    ex.printStackTrace(pw);
                    logger.error(sw.toString());
                }
            } else {
                logger.warn("No device for item: {}", itemConfig.item.getName());
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Item {}: {} devices will be updated", itemConfig.item.getName(), updates);
        }
    }

    @Override
    public void bindingChanged(BindingProvider provider, String itemName) {
        if (logger.isDebugEnabled()) {
            logger.debug("bindingChanged({},{}) is called!", provider, itemName);
        }

        BindingConfig config = ((SimpleBinaryGenericBindingProvider) provider).getItemConfig(itemName);

        if (config instanceof SimpleBinaryBindingConfig) {
            if (items.get(itemName) != null) {
                items.remove(itemName);
            }

            if (config != null) {
                items.put(itemName, (SimpleBinaryBindingConfig) config);
            }

        } else if (config instanceof SimpleBinaryInfoBindingConfig) {
            if (infoItems.get(itemName) != null) {
                infoItems.remove(itemName);
            }

            if (config != null) {
                infoItems.put(itemName, (SimpleBinaryInfoBindingConfig) config);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("ItemsConfig: {}:{}", items, items.entrySet().size());
        }

        super.bindingChanged(provider, itemName);
    }

    @Override
    public void allBindingsChanged(BindingProvider provider) {
        if (logger.isDebugEnabled()) {
            logger.debug("allBindingsChanged() is called!");
        }

        items.clear();
        infoItems.clear();

        for (Entry<String, BindingConfig> item : ((SimpleBinaryGenericBindingProvider) provider).configs().entrySet()) {
            if (item.getValue() instanceof SimpleBinaryBindingConfig) {
                items.put(item.getKey(), (SimpleBinaryBindingConfig) item.getValue());
            } else if (item.getValue() instanceof SimpleBinaryInfoBindingConfig) {
                infoItems.put(item.getKey(), (SimpleBinaryInfoBindingConfig) item.getValue());
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Record processed: {}", ((SimpleBinaryGenericBindingProvider) provider).configs().size());
            logger.debug("Configured data/info items: {}/{}", items.size(), infoItems.size());
        }

        super.allBindingsChanged(provider);
    }
}
