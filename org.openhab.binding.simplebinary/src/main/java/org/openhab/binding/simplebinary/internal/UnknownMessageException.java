package org.openhab.binding.simplebinary.internal;

/**
 * Exception class for received unknown message (unknown message ID)
 * 
 * @author vita
 * @since 1.8.0
 */

public class UnknownMessageException extends Exception {

	public UnknownMessageException() {
		super();
	}

	public UnknownMessageException(String msg) {
		super(msg);
	}
}