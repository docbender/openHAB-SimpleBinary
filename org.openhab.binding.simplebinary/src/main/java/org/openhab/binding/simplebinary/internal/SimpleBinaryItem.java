package org.openhab.binding.simplebinary.internal;

import java.awt.Color;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.apache.commons.lang.NotImplementedException;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.core.library.items.ColorItem;
import org.openhab.core.library.items.ContactItem;
import org.openhab.core.library.items.DimmerItem;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.RollershutterItem;
import org.openhab.core.library.items.StringItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class holding item data
 * 
 * @author tucek
 *
 */
public class SimpleBinaryItem extends SimpleBinaryMessage {
	
	public String name;
	private SimpleBinaryBindingConfig itemConfig;
	private byte[] itemData;
	
	private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryProtocol.class);
	

	/**
	 * Constructor for control messages
	 * 
	 * @param messageId ID
	 * @param address   Address
	 * @param itemData  Item data
	 */
	public SimpleBinaryItem(byte messageId, int address, byte[] itemData)
	{
		this("",null,messageId,address,itemData);
	}
	
	/**
	 * Constructor for items
	 * 
	 * @param itemName
	 * @param itemConfig
	 * @param messageId
	 * @param address
	 * @param itemData
	 */
	public SimpleBinaryItem(String itemName, SimpleBinaryBindingConfig itemConfig, byte messageId, int address, byte[] itemData)
	{
		super(messageId, address);
		
		this.name = itemName;
		this.itemConfig = itemConfig;
		this.itemData = itemData;
	}
	

	/* (non-Javadoc)
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryMessage#getMessageId()
	 */
	public byte getMessageId()
	{		
		return this.messageId;
	}
	
	/**
	 * Return item raw data
	 * 
	 * @return
	 */
	public byte[] getData()
	{
		return itemData;
	}
	
	/**
	 * Return item State from itemData
	 * 
	 * @return
	 * @throws Exception
	 */
	public State getState() throws Exception
	{
		if(itemConfig == null)
			return null;
		
		if(itemConfig.itemType.isAssignableFrom(NumberItem.class))
		{
			if(itemConfig.getDataType() == SimpleBinaryTypes.FLOAT)
			{			
				if(itemData.length != 4)
					throw new Exception("getState(): cannot convert to item " + name + " to FLOAT. Wrong data length.");
				else
				{
					logger.debug("converting to FLOAT: " + SimpleBinaryProtocol.arrayToString(itemData, 4)); 
							
					//long bits = itemData[0] | (itemData[1] << 8) | (itemData[2] << 16) | (itemData[3] << 24);
					//logger.debug("bits: {}", bits); 
					//float value = Float.intBitsToFloat((int)bits );
					ByteBuffer bf = ByteBuffer.wrap(itemData);
					bf.order(ByteOrder.LITTLE_ENDIAN);
					float value = bf.getFloat();
					logger.debug("floatbits: {}", Float.floatToIntBits(value));
					
					logger.debug("FLOAT value converted: {}", value ); 
			
					return new DecimalType(value);
				}
			}
			else
			{		
				if(itemData.length == 1)
					return new DecimalType((int)itemData[0]);
				else if(itemData.length == 2)
					return new DecimalType((int)(itemData[0] | (itemData[1] << 8)));
				else if(itemData.length == 4)
					return new DecimalType((int)(itemData[0] | (itemData[1] << 8) | (itemData[2] << 16) | (itemData[3] << 24)));
				else
					throw new Exception("getState(): cannot convert to item " + name + " to " + itemConfig.getDataType() + ". Wrong data length.");				
			}
		}
		else if(itemConfig.itemType.isAssignableFrom(SwitchItem.class))
		{
			if(itemData[0] == 1)
				return OnOffType.ON;
			else
				return OnOffType.OFF;
		}
		else if(itemConfig.itemType.isAssignableFrom(DimmerItem.class))
		{
			if(itemData.length < 3)
			{
				return new PercentType(itemData[0]);
			}
			else
				throw new Exception("getState(): cannot convert to item " + name + " to " + itemConfig.getDataType() + ". Data length > 2");	
		}		
		else if(itemConfig.itemType.isAssignableFrom(ColorItem.class))
		{
			if(itemConfig.getDataType() == SimpleBinaryTypes.HSB)
			{			
				return new HSBType(new DecimalType((int)itemData[0]), new PercentType((int)itemData[1]), new PercentType((int)itemData[2]));
			}
			else if(itemConfig.getDataType() == SimpleBinaryTypes.RGB)
			{				
				return new HSBType(new Color((int)itemData[0], (int)itemData[1], (int)itemData[2]));
			}
			else if(itemConfig.getDataType() == SimpleBinaryTypes.RGBW)
			{
				return new HSBType(new Color((int)itemData[0], (int)itemData[1], (int)itemData[2]));
			}
			else
				throw new Exception("getState(): cannot convert to item " + name + " to " + itemConfig.getDataType() + ".");	
		}
		else if(itemConfig.itemType.isAssignableFrom(StringItem.class))
		{
			String str = new String(itemData);
			return new StringType(str);
		}
		else if(itemConfig.itemType.isAssignableFrom(ContactItem.class))
		{
			if(itemData[0] == 1)
				return OpenClosedType.OPEN;
			else
				return OpenClosedType.CLOSED;
		}
		else if(itemConfig.itemType.isAssignableFrom(RollershutterItem.class))
		{
			if(itemData.length < 3)
			{
				return new PercentType(itemData[0]);
			}
			else
				throw new Exception("getState(): cannot convert to item " + name + " to " + itemConfig.getDataType() + ". Data length > 2");	
		}
		else
			throw new Exception("getState(): cannot convert to item " + name + " to " + itemConfig.getDataType() + ". Unsupported itemtype: " + itemConfig.itemType.toString());		
	}
	
	/**
	 * Return item config
	 * 
	 * @return
	 */
	public SimpleBinaryBindingConfig getConfig()
	{
		return itemConfig;
	}
}
