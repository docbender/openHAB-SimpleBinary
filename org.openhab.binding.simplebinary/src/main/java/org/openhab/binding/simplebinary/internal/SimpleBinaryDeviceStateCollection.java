package org.openhab.binding.simplebinary.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.InfoType;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author tucek
 * 
 */
public class SimpleBinaryDeviceStateCollection extends HashMap<Integer, SimpleBinaryDeviceState> {
	private static final long serialVersionUID = -6637691081696263746L;

	private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryPortState.class);

	protected Map<String, SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig> deviceItemsConfigs;
	protected EventPublisher eventPublisher;

	/**
	 * @param deviceItemsConfigs
	 */
	public SimpleBinaryDeviceStateCollection(String deviceName, Map<String, SimpleBinaryInfoBindingConfig> deviceItemsConfigs, EventPublisher eventPublisher) {
		super();

		this.deviceItemsConfigs = deviceItemsConfigs;
		this.eventPublisher = eventPublisher;

		// add only appropriate item configuration
		for (Map.Entry<String, SimpleBinaryInfoBindingConfig> item : deviceItemsConfigs.entrySet()) {

			if (item.getValue().device == deviceName) {
				this.deviceItemsConfigs.put(item.getKey(), item.getValue());
			}
		}
	}

	// public void addDevice(Integer deviceAddress, DeviceState.DeviceStates state){
	//
	// }

	public void setDeviceState(String deviceName, Integer deviceAddress, SimpleBinaryDeviceState.DeviceStates state) {

		if (!this.containsKey(deviceAddress)) {
			this.put(deviceAddress, new SimpleBinaryDeviceState());
		}

		// retrieve device
		SimpleBinaryDeviceState deviceState = this.get(deviceAddress);
		// set internal state
		deviceState.setState(state);

		if (eventPublisher != null) {

			// send data to event bus
			for (Map.Entry<String, SimpleBinaryInfoBindingConfig> item : deviceItemsConfigs.entrySet()) {

				// check correct device and target address
				if (item.getValue().device.equals(deviceName) && item.getValue().busAddress == deviceAddress) {
					// find right info type
					if (item.getValue().infoType == InfoType.STATE) {
						// update event bus
						eventPublisher.postUpdate(item.getValue().item.getName(), new DecimalType(deviceState.getState().ordinal()));
					} else if (item.getValue().infoType == InfoType.PREVIOUS_STATE) {
						// update event bus
						eventPublisher.postUpdate(item.getValue().item.getName(), new DecimalType(deviceState.getPreviousState().ordinal()));
					} else if (item.getValue().infoType == InfoType.STATE_CHANGE_TIME) {
						// update event bus
						eventPublisher.postUpdate(item.getValue().item.getName(), new DateTimeType(deviceState.getChangeDate()));
					} else if (item.getValue().infoType == InfoType.PACKET_LOST) {
						// update event bus
						eventPublisher.postUpdate(item.getValue().item.getName(), new DecimalType(deviceState.getPacketLost()));
					}
				}
			}
		}
	}

	public void setStateToAllConfiguredDevices(String deviceName, SimpleBinaryDeviceState.DeviceStates state) {
		if (eventPublisher != null) {

			logger.debug("setStateToAllConfiguredDevices");

			// send data to event bus
			for (Map.Entry<String, SimpleBinaryInfoBindingConfig> item : deviceItemsConfigs.entrySet()) {
				logger.debug("{}", item.toString());

				// check correct device and target address
				if (item.getValue().device.equals(deviceName) && item.getValue().busAddress >= 0 && item.getValue().infoType == InfoType.STATE) {
					logger.debug("{}={}", item.getKey(), state.toString());
					setDeviceState(deviceName, item.getValue().busAddress, state);
				}
			}
		}
	}
}
