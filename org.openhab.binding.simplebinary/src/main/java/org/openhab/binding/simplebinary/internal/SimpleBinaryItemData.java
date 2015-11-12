package org.openhab.binding.simplebinary.internal;

/**
 * 
 * Class holding item data
 * 
 * @author vita
 * @since 1.8.0
 */
public class SimpleBinaryItemData extends SimpleBinaryMessage {

	protected byte[] itemData;

	public SimpleBinaryItemData(byte messageId, int address, byte[] itemData) {
		super(messageId, address);

		this.itemData = itemData;
	}

	/**
	 * Return item raw data
	 * 
	 * @return
	 */
	public byte[] getData() {
		return itemData;
	}

}
