package org.openhab.binding.simplebinary.internal;

/**
 * Exception class for item not found in configuration (device or address)
 * 
 * @author vita
 * @since 1.8.0
 */

public class NoValidItemInConfig extends Exception {

	public NoValidItemInConfig() {
		super();
	}

	public NoValidItemInConfig(String msg) {
		super(msg);
	}

}
