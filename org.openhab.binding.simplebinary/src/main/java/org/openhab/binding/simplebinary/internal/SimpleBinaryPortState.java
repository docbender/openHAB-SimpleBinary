package org.openhab.binding.simplebinary.internal;

import java.util.Date;

/**
 * Status of communication port
 * 
 * @author vita
 * @since 1.8.0
 */

public class SimpleBinaryPortState {

	public enum PortStates {
		UNKNOWN, LISTENING, CLOSED 
	}
	
	private PortStates state = PortStates.UNKNOWN;
	private PortStates prevState = PortStates.UNKNOWN;
	private Date changedSince; 
	
	public PortStates getState() {
		return state;
	}
	
	public PortStates getPreviusState() {
		return prevState;
	}
	
	public Date getChangeDate() {
		return changedSince;
	}
	
	public void setState(PortStates state) {
		// set state only if previous is different
		if(this.state != state) {
			this.prevState = this.state;
			this.state = state;
			this.changedSince = new Date();
		}
	}
}
