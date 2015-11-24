package org.openhab.binding.simplebinary.internal;

/**
 * Exception class for item not found in configuration (device or address)
 * 
 * @author vita
 * @since 1.8.0
 */

public class NoValidItemInConfig extends Exception {

	public NoValidItemInConfig() {
		super("Item not found in configuration");
	}

	public NoValidItemInConfig(String msg) {
		super(msg);
	}
	
	public NoValidItemInConfig(String deviceName, int devId, int address) {
		super("Item was not found in configuration (device address=" + devId + "; item address=" + address );
	}
}
