package org.openhab.binding.simplebinary.internal;

/**
 * Exception class for received unknown message (unknown message ID)
 * 
 * @author vita
 * @since 1.8.0
 */

public class WrongAddressException extends Exception {
	private static final long serialVersionUID = -66093531080490622L;

	public WrongAddressException() {
		super();
	}

	public WrongAddressException(String msg) {
		super(msg);
	}
	
	public WrongAddressException(int expectedAddress, int receivedAddress) {
		super(String.format("Expected address=%d received address=%d", expectedAddress, receivedAddress));
	}
}