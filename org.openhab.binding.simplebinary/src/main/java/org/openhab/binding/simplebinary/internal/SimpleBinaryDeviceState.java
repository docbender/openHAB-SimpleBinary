package org.openhab.binding.simplebinary.internal;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;


/**
 * Status of communicated device
 * 
 * @author vita
 * @since 1.8.0
 */

public class SimpleBinaryDeviceState {
	
	double packetLost = 0.0;
	
	Queue<Calendar> communicationOK = new LinkedList<Calendar>();
	Queue<Calendar> communicationError = new LinkedList<Calendar>();
	
	public enum DeviceStates {
		UNKNOWN, CONNECTED, NOT_RESPONDING, RESPONSE_ERROR, DATA_ERROR
	}
	
	private DeviceStates state = DeviceStates.UNKNOWN;
	private DeviceStates prevState = DeviceStates.UNKNOWN;
	private Calendar changedSince = Calendar.getInstance(); 
	
	
	/**
	 * Return last device state
	 * 
	 * @return
	 */
	public DeviceStates getState() {
		return state;
	}
	
	
	/**
	 * Return previous state of device
	 * 
	 * @return
	 */
	public DeviceStates getPreviousState() {
		return prevState;
	}
	
	
	/**
	 * Return date of last device state change
	 * 
	 * @return
	 */
	public Calendar getChangeDate() {
		return changedSince;
	}
	
	
	/**
	 * Set actual device state
	 * 
	 * @param state
	 */
	public void setState(DeviceStates state) {
		// set state only if previous is different
		if(this.state != state) {
			this.prevState = this.state;
			this.state = state;
			this.changedSince = Calendar.getInstance();			
		}
		
		if(this.state == DeviceStates.CONNECTED)
			communicationOK.add(Calendar.getInstance());
		else
			communicationError.add(Calendar.getInstance());
		
		calcPacketLost();
	}	
	
	
	/**
	 * Return packet lost in percentage
	 * 
	 * @return
	 */
	public double getPacketLost()
	{		
		calcPacketLost();
		
		return packetLost;
	}

	/**
	 * Calculate packet lost for specific time in past
	 */
	private void calcPacketLost() {
		Calendar limitTime = Calendar.getInstance();
		limitTime.add(Calendar.MINUTE, -5);
		
		while(communicationOK.size() > 0 && communicationOK.element().after(limitTime))
			communicationOK.remove();
		while(communicationError.size() > 0 && communicationError.element().after(limitTime))
			communicationError.remove();
			
		packetLost =  100 * communicationError.size() / (communicationOK.size() + communicationError.size());
	}	
}


