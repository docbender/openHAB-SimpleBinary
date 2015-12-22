package org.openhab.binding.simplebinary.internal;

/**
 * Exception class for no valid crc
 * 
 * @author vita
 * @since 1.8.0
 */

public class NoValidCRCException extends Exception {
	private static final long serialVersionUID = -4524739106640114564L;

	public NoValidCRCException() {
		super("CRC not valid");
	}

	public NoValidCRCException(String msg) {
		super(msg);
	}
	
	public NoValidCRCException(byte receivedCrc, byte expectedCrc) {
		super(String.format("CRC not valid. Receive/expected 0x%02X/0x%02X,", receivedCrc, expectedCrc));
	}

}
