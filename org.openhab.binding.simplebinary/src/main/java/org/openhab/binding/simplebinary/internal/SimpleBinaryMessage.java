/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal;

/**
 * Class holding message data
 * 
 * @author vita
 * @since 1.8.0
 */
public class SimpleBinaryMessage {
	protected byte messageId;
	protected int busAddress;

	/**
	 * Constructor
	 * 
	 * @param messageId
	 * @param address
	 */
	public SimpleBinaryMessage(byte messageId, int address) {
		this.messageId = messageId;
		this.busAddress = address;
	}

	/**
	 * Return message ID
	 * 
	 * @return
	 */
	public byte getMessageId() {
		return this.messageId;
	}

	/**
	 * Return device address
	 * 
	 * @return
	 */
	public int getAddress() {
		return this.busAddress;
	}

	/**
	 * Return message type depending on message ID
	 * 
	 * @return
	 */
	public SimpleBinaryMessageType getMessageType() {
		switch (messageId) {
		case (byte) 0xD0:
			return SimpleBinaryMessageType.CHECKNEWDATA;
		case (byte) 0xD1:
		case (byte) 0xD2:
		case (byte) 0xD3:
		case (byte) 0xD4:
			return SimpleBinaryMessageType.QUERY;
		case (byte) 0xDA:
		case (byte) 0xDB:
		case (byte) 0xDC:
		case (byte) 0xDD:
		case (byte) 0xDE:
			return SimpleBinaryMessageType.DATA;
		case (byte) 0xE0:
			return SimpleBinaryMessageType.OK;
		case (byte) 0xE1:
			return SimpleBinaryMessageType.RESEND;
		case (byte) 0xE2:
			return SimpleBinaryMessageType.NODATA;
		case (byte) 0xE4:
			return SimpleBinaryMessageType.UNKNOWN_ADDRESS;
		case (byte) 0xE5:
			return SimpleBinaryMessageType.SAVING_ERROR;
		default:
			return SimpleBinaryMessageType.UNKNOWN;
		}
	}
}
