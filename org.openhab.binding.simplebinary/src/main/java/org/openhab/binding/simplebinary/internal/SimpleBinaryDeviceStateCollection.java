package org.openhab.binding.simplebinary.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.InfoType;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig;

/**
 * @author tucek
 * 
 */
public class SimpleBinaryDeviceStateCollection extends HashMap<Integer, SimpleBinaryDeviceState> {
	private static final long serialVersionUID = -6637691081696263746L;

	protected Map<String, SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig> deviceItemsConfigs;

	/**
	 * @param deviceItemsConfigs
	 */
	public SimpleBinaryDeviceStateCollection(String deviceName, Map<String, SimpleBinaryInfoBindingConfig> deviceItemsConfigs) {
		super();

		this.deviceItemsConfigs = new HashMap<String, SimpleBinaryInfoBindingConfig>();
		
		// add only appropriate item configuration
		for (Map.Entry<String, SimpleBinaryInfoBindingConfig> item : deviceItemsConfigs.entrySet()) {

			if(item.getValue().device == deviceName){
				this.deviceItemsConfigs.put(item.getKey(), item.getValue());
			}
		}
	}

	// public void addDevice(Integer deviceAddress, DeviceState.DeviceStates state){
	//
	// }

	public void setDeviceState(Integer deviceAddress, SimpleBinaryDeviceState.DeviceStates state) {

		if (!this.containsKey(deviceAddress)) {
			this.put(deviceAddress, new SimpleBinaryDeviceState());

		}

		// set internal state
		this.get(deviceAddress).setState(state);

		// send data to event bus
		for (Map.Entry<String, SimpleBinaryInfoBindingConfig> item : deviceItemsConfigs.entrySet()) {

			//check correct address
			if(item.getValue().busAddress == deviceAddress){
				// find right info type
				if(item.getValue().infoType == InfoType.CONNECTED) {
					//TODO send
				}				
			}
		}

	}

	// public void setDeviceState(Integer deviceAddress, DeviceState.DeviceStates state) {
	//
	// if(!this.containsKey(deviceAddress)){
	// this.put(deviceAddress, new DeviceState());
	//
	// }
	//
	// //set internal state
	// this.get(deviceAddress).setState(state);
	//
	// //send data to event bus
	//
	// }
}
