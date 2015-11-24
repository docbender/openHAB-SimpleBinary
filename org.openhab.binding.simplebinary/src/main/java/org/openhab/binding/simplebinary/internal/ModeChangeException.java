/**
 * 
 */
package org.openhab.binding.simplebinary.internal;

import org.openhab.binding.simplebinary.internal.SimpleBinaryByteBuffer.BufferMode;

/**
 * Exception class for bytebuffer. Raised when performed wrong operation in mode read or write
 * 
 * @author vita
 * @since 1.8.0
 */

public class ModeChangeException  extends Exception {

	public ModeChangeException() {
		super("Operation not supported in given mode");
	}

	public ModeChangeException(String msg) {
		super(msg);
	}
	
	public ModeChangeException(String operation, BufferMode mode) {
		super("Operation " + operation + " is not supported in mode " + mode.toString());
	}

}
