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

	public SimpleBinaryItemData(byte messageId, int deviceId, byte[] itemData) {
		super(messageId, deviceId, -1);

		this.itemData = itemData;
	}
	
	public SimpleBinaryItemData(byte messageId, int deviceId, int itemAddress, byte[] itemData) {
		super(messageId, deviceId, itemAddress);

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
