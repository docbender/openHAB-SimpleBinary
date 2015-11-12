package org.openhab.binding.simplebinary.internal;

import java.util.Date;


/**
 * Status of communicated device
 * 
 * @author vita
 * @since 1.8.0
 */

public class SimpleBinaryDeviceState {
	
	public enum DeviceStates {
		UNKNOWN, CONNECTED, NOT_RESPONDING, RESPONSE_ERROR, DATA_ERROR
	}
	
	private DeviceStates state = DeviceStates.UNKNOWN;
	private DeviceStates prevState = DeviceStates.UNKNOWN;
	private Date changedSince; 
	
	public DeviceStates getState() {
		return state;
	}
	
	public DeviceStates getPreviusState() {
		return prevState;
	}
	
	public Date getChangeDate() {
		return changedSince;
	}
	
	public void setState(DeviceStates state) {
		// set state only if previous is different
		if(this.state != state) {
			this.prevState = this.state;
			this.state = state;
			this.changedSince = new Date();
		}
	}
}


