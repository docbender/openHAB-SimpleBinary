package org.openhab.binding.simplebinary.internal;

/**
 * Exception class for received unknown message (unknown message ID)
 * 
 * @author vita
 * @since 1.8.0
 */

public class UnknownMessageException extends Exception {
	private static final long serialVersionUID = 1367099920816957870L;

	public UnknownMessageException() {
		super();
	}

	public UnknownMessageException(String msg) {
		super(msg);
	}
}