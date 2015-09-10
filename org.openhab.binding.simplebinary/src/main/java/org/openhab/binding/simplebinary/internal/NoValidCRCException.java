package org.openhab.binding.simplebinary.internal;

/**
 * Exception class for no valid crc
 * 
 * @author vita
 * @since 1.8.0
 */

public class NoValidCRCException extends Exception {

	public NoValidCRCException() {
		super();
	}

	public NoValidCRCException(String msg) {
		super(msg);
	}

}
