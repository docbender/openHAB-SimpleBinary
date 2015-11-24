package org.openhab.binding.simplebinary.internal;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.InfoType;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;

/**
 * Status of communication port
 * 
 * @author vita
 * @since 1.8.0
 */

public class SimpleBinaryPortState {

	public enum PortStates {
		UNKNOWN, LISTENING, CLOSED, NOT_EXIST, NOT_AVAILABLE
	}



	private PortStates state = PortStates.UNKNOWN;
	private PortStates prevState = PortStates.UNKNOWN;
	private Calendar changedSince;
	private EventPublisher eventPublisher;
	private String itemState = null;
	private String itemPreviousState = null;
	private String itemStateChangeTime = null;

	public PortStates getState() {
		return state;
	}

	public PortStates getPreviusState() {
		return prevState;
	}

	public Calendar getChangeDate() {
		return changedSince;
	}

	public void setState(PortStates state) {

		// set state only if previous is different
		if (this.state != state) {
			this.prevState = this.state;
			this.state = state;
			this.changedSince = Calendar.getInstance();

			// update event bus
			if (itemState != null)
				eventPublisher.postUpdate(itemState, new DecimalType(this.state.ordinal()));
			if (itemPreviousState != null)
				eventPublisher.postUpdate(itemPreviousState, new DecimalType(this.prevState.ordinal()));
			if (itemStateChangeTime != null)
				eventPublisher.postUpdate(itemStateChangeTime, new DateTimeType(this.changedSince));

		}
	}

	public void setBindingData(EventPublisher eventPublisher, Map<String, SimpleBinaryInfoBindingConfig> itemsInfoConfig, String deviceName) {
		this.eventPublisher = eventPublisher;

		for (Map.Entry<String, SimpleBinaryInfoBindingConfig> item : itemsInfoConfig.entrySet()) {

			if (item.getValue().device.equals(deviceName)) {

				// check correct address
				if (item.getValue().busAddress == -1) {
					// find right info type
					if (item.getValue().infoType == InfoType.STATE)
						itemState = item.getValue().item.getName();
					else if (item.getValue().infoType == InfoType.PREVIOUS_STATE)
						itemPreviousState = item.getValue().item.getName();
					else if (item.getValue().infoType == InfoType.STATE_CHANGE_TIME)
						itemStateChangeTime = item.getValue().item.getName();
				}
			}
		}
	}
}
