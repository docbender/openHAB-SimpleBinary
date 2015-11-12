/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.simplebinary.internal;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;

import javafx.collections.transformation.SortedList;

import org.apache.commons.io.IOUtils;
import org.openhab.binding.simplebinary.internal.SimpleBinaryDeviceState.DeviceStates;
import org.openhab.binding.simplebinary.internal.SimpleBinaryPortState.PortStates;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

/**
 * Serial device class
 * 
 * @author Vita Tucek
 * 
 */
public class SimpleBinaryUART implements SimpleBinaryIDevice, SerialPortEventListener {

	private static final Logger logger = LoggerFactory.getLogger(SimpleBinaryUART.class);

	private String deviceName;
	private String port;
	private int baud = 9600;
	private int MAX_RESEND_COUNT = 2;

	private EventPublisher eventPublisher;
	// item config
	private Map<String, SimpleBinaryBindingConfig> itemsConfig;
	// port id
	private CommPortIdentifier portId;
	// port instance
	private SerialPort serialPort;
	// input data stream
	private InputStream inputStream;
	// output data stream
	private OutputStream outputStream;
	// buffer for incoming data
	private ByteBuffer inBuffer = ByteBuffer.allocate(256);
	// flag that device is connected
	private boolean connected = false;
	// queue for commands
	private Queue<SimpleBinaryItemData> commandQueue = new LinkedList<SimpleBinaryItemData>();
	// flag waiting
	private boolean waitingForAnswer = false;
	// store last sended data
	private SimpleBinaryItemData lastSendedData = null;
	// counting resend data
	private int resendCounter = 0;
	// timer measuring answer timeout
	private Timer timer = new Timer();
	private TimerTask timeoutTask = null;
	private SimpleBinaryPoolControl poolControl;
	private boolean forceRTS;
	private boolean invertedRTS;
	private long sendedTimeTicks = 0;
	// states
	public SimpleBinaryPortState portState = new SimpleBinaryPortState();
	public SimpleBinaryDeviceStateCollection devicesStates;

	public SimpleBinaryUART(String deviceName, String port, int baud, SimpleBinaryPoolControl simpleBinaryPoolControl, boolean forceRTS, boolean invertedRTS) {
		this.deviceName = deviceName;
		this.port = port;
		this.baud = baud;
		this.poolControl = simpleBinaryPoolControl;
		this.forceRTS = forceRTS;
		this.invertedRTS = invertedRTS;

		inBuffer.order(ByteOrder.LITTLE_ENDIAN);		
	}

	public void setBindingData(EventPublisher eventPublisher, Map<String, SimpleBinaryBindingConfig> itemsConfig, Map<String, SimpleBinaryInfoBindingConfig> itemsInfoConfig) {
		this.eventPublisher = eventPublisher;
		this.itemsConfig = itemsConfig;
		
		this.devicesStates = new SimpleBinaryDeviceStateCollection(deviceName, itemsInfoConfig);
	}

	public void unsetBindingData() {
		this.eventPublisher = null;
		this.itemsConfig = null;
		this.devicesStates = null;
	}

	public String getPort() {
		return port;
	}

	public boolean isConnected() {
		return connected;
	}

	// open device
	public Boolean open() {
		logger.debug("Opening port {}", this.deviceName);
		
		portState.setState(PortStates.CLOSED);
		//clear device states
		devicesStates.clear();
		//reset connected state
		connected = false;
		setWaitingForAnswer(false);

		try {
			// get port ID
			portId = CommPortIdentifier.getPortIdentifier(this.port);
		} catch (NoSuchPortException ex) {
			logger.warn("Port {} not found", this.port);
			logger.warn("Available ports: " + getCommPortListString());

			return false;
		}

		if (portId != null) {
			// initialize serial port
			try {
				serialPort = (SerialPort) portId.open("openHAB", 2000);
			} catch (PortInUseException e) {
				logger.error("Port is in use");

				this.close();
				return false;
			}

			try {
				inputStream = serialPort.getInputStream();
			} catch (IOException e) {
				logger.error(e.toString());

				this.close();
				return false;
			}

			try {
				// get the output stream
				outputStream = serialPort.getOutputStream();
			} catch (IOException e) {
				logger.error(e.toString());

				this.close();
				return false;
			}

			try {
				serialPort.addEventListener(this);
			} catch (TooManyListenersException e) {
				logger.error(e.toString());

				this.close();
				return false;
			}

			// activate the DATA_AVAILABLE notifier
			serialPort.notifyOnDataAvailable(true);
			if (this.forceRTS) {
				// OUTPUT_BUFFER_EMPTY
				serialPort.notifyOnOutputEmpty(true);
			}

			try {
				// set port parameters
				serialPort.setSerialPortParams(baud, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
				// serialPort.setRTS(false);				
			} catch (UnsupportedCommOperationException e) {
				logger.error(e.toString());

				this.close();
				return false;
			}
		}

		logger.debug("Port {} opened", this.deviceName);
		portState.setState(PortStates.LISTENING);
		connected = true;
		return true;
	}

	@SuppressWarnings("rawtypes")
	private String getCommPortListString() {
		StringBuilder sb = new StringBuilder();
		Enumeration portList = CommPortIdentifier.getPortIdentifiers();
		while (portList.hasMoreElements()) {
			CommPortIdentifier id = (CommPortIdentifier) portList.nextElement();
			if (id.getPortType() == CommPortIdentifier.PORT_SERIAL) {
				sb.append(id.getName() + "\n");
			}
		}

		return sb.toString();
	}

	// close device
	public void close() {
		serialPort.removeEventListener();
		IOUtils.closeQuietly(inputStream);
		IOUtils.closeQuietly(outputStream);
		serialPort.close();

		portState.setState(PortStates.CLOSED);
		connected = false;
	}

	// reconnect device
	private void reconnect() {
		logger.info("Trying to reconnect to serial port {}", this.port);

		close();
		open();
	}

	// send data into device channel
	public void sendData(String itemName, Command command, SimpleBinaryBindingConfig config) {
		// compile data
		// byte[] data = SimpleBinaryProtocol.compileDataFrame(itemName, command, config);
		SimpleBinaryItem data = SimpleBinaryProtocol.compileDataFrame(itemName, command, config);

		sendData(data);
	}

	public void sendData(SimpleBinaryItem data) {
		if (data != null) {
			logger.debug("Adding command into queue " + this.port);
			commandQueue.add(data);

			processCommandQueue();
		} else
			logger.warn("Nothing to send. Empty data");
	}

	/**
	 * Prepare request to check if device with specific address has new data
	 * 
	 * @param deviceAddress
	 */
	private void sendNewDataCheck(int deviceAddress) {
		SimpleBinaryItemData data = SimpleBinaryProtocol.compileNewDataFrame(deviceAddress);

		commandQueue.add(data);

		processCommandQueue();
	}

	/**
	 * Prepare request for read data of specific item
	 * 
	 * @param itemConfig
	 */
	private void sendReadData(SimpleBinaryBindingConfig itemConfig) {
		SimpleBinaryItemData data = SimpleBinaryProtocol.compileReadDataFrame(itemConfig);

		// check if packet already exist
		if (!dataInQueue(data))
			commandQueue.add(data);

		processCommandQueue();
	}

	/**
	 * Check if data packet is not already in queue
	 * 
	 * @param item
	 * @return
	 */
	private boolean dataInQueue(SimpleBinaryItemData item) {
		if (commandQueue.isEmpty())
			return false;

		for (SimpleBinaryItemData qitem : commandQueue) {
			if (qitem.getData().length != item.getData().length)
				break;

			for (int i = 0; i < qitem.getData().length; i++) {
				if (qitem.getData()[i] != item.getData()[i])
					break;

				if (i == qitem.getData().length - 1)
					return true;
			}
		}

		return false;
	}

	/**
	 * Check if queue is not empty and send data to device
	 * 
	 */
	private void processCommandQueue() {
		logger.debug("Processing commandQueue - length {}", commandQueue.size());

		if (commandQueue.isEmpty())
			return;

		if (!waitingForAnswer)
			sendDataOut(commandQueue.remove());
		else
			logger.debug("Processing commandQueue - waiting");
	}

	/**
	 * Write data into device stream
	 * 
	 * @param data
	 *            Item data with compiled packet
	 */
	private void sendDataOut(SimpleBinaryItemData data) {
		logger.debug("Sending data to " + this.port + " with length {} bytes", data.getData().length);
		logger.debug("data: {}", SimpleBinaryProtocol.arrayToString(data.getData(), data.getData().length));

		try {
			// set RTS
			if (this.forceRTS) {
				//calc minumum time for send + currentTime
				sendedTimeTicks = System.currentTimeMillis() + (((data.getData().length * 8) + 2)*1000 / this.baud);			
				
				serialPort.setRTS(invertedRTS ? false : true);

				//logger.debug("RTS set");
				logger.info("RTS set");
			}

			// write string to serial port
			outputStream.write(data.getData());
			outputStream.flush();

			setWaitingForAnswer(true);

			setLastSendedData(data);
		} catch (IOException e) {
			logger.error("Error writing to serial port {}", this.port);

			reconnect();
		}
	}

	private void setWaitingForAnswer(boolean state) {
		waitingForAnswer = state;

		if (state) {
			if (timeoutTask != null)
				timeoutTask.cancel();

			timeoutTask = new TimerTask() {
				@Override
				public void run() {
					timeoutTask = null;
					logger.warn("Receiving data timeouted");
					setWaitingForAnswer(false);
				}
			};

			timer.schedule(timeoutTask, 2000);
		} else {
			if (timeoutTask != null) {
				timeoutTask.cancel();
				timeoutTask = null;
			}
		}
	}

	// serial events
	public void serialEvent(SerialPortEvent event) {
		switch (event.getEventType()) {
		case SerialPortEvent.BI:
		case SerialPortEvent.OE:
		case SerialPortEvent.FE:
		case SerialPortEvent.PE:
		case SerialPortEvent.CD:
		case SerialPortEvent.CTS:
		case SerialPortEvent.DSR:
		case SerialPortEvent.RI:
			break;
		case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
			// reset RTS
			if (this.forceRTS && (serialPort.isRTS() && !invertedRTS || !serialPort.isRTS() && invertedRTS)) {
				
				//comply minimum sending time. Added because on ubuntu14.04 event OUTPUT_BUFFER_EMPTY is called periodically even before some data are send.   
				if(System.currentTimeMillis() > sendedTimeTicks){
					serialPort.setRTS(invertedRTS ? true : false);

					//logger.debug("RTS reset");
					logger.info("RTS reset");		
				}
			}
			break;
		case SerialPortEvent.DATA_AVAILABLE:

			byte[] readBuffer = new byte[32];

			try {
				while (inputStream.available() > 0) {
					int bytes = inputStream.read(readBuffer);
					logger.debug("Received data on serial port {} - length {} bytes", port, bytes);

					if (bytes + inBuffer.position() >= inBuffer.capacity()) {
						logger.error("Buffer overrun");
						break;
					} else {
						inBuffer.put(readBuffer, 0, bytes);
						// Thread.sleep(50);
					}
				}

				logger.debug("Received data in serial port {} buffer - length {} bytes", port, inBuffer.position());

				// check data
				if (itemsConfig != null) {
					// check minimum length
					while (inBuffer.position() > 3) {
						// flip buffer
						inBuffer.flip();

						try {
							SimpleBinaryMessage itemData = SimpleBinaryProtocol.decompileData(inBuffer, itemsConfig, deviceName);

							if (itemData != null) {
								setWaitingForAnswer(false);

								if (itemData instanceof SimpleBinaryItem) {
									logger.debug("Incoming data");

									resendCounter = 0;
									
									//set state
									devicesStates.setDeviceState(((SimpleBinaryItem) itemData).getAddress(), DeviceStates.CONNECTED);

									State state = ((SimpleBinaryItem) itemData).getState();

									if (state == null) {
										logger.warn("Incoming data - Unknown item state");
									} else {
										logger.debug("Incoming data - item:{}/state:{}", ((SimpleBinaryItem) itemData).name, state);

										if (eventPublisher != null)
											eventPublisher.postUpdate(((SimpleBinaryItem) itemData).name, state);
									}

									// if data income on request "check new data" send it again for new check
									if (getLastSendedData().getMessageType() == SimpleBinaryMessageType.CHECKNEWDATA)
										this.resendData();
									else
										processCommandQueue();
								} else if (itemData instanceof SimpleBinaryMessage) {
									logger.debug("Incoming control message");								
									
									//set state
									devicesStates.setDeviceState(((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.CONNECTED);

									if (itemData.getMessageType() == SimpleBinaryMessageType.OK) {
										logger.debug("Device {} on port {} report data OK", itemData.getAddress(), port);

										resendCounter = 0;

										processCommandQueue();
									} else if (itemData.getMessageType() == SimpleBinaryMessageType.RESEND) {
										logger.debug("Device {} on port {} request resend data", itemData.getAddress(), port);

										if (resendCounter < MAX_RESEND_COUNT) {
											this.resendData();
											resendCounter++;
										} else {
											logger.warn("Max resend attempts reached.");
											//set state
											devicesStates.setDeviceState(((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.RESPONSE_ERROR);
											
											resendCounter = 0;
											processCommandQueue();
										}
									} else if (itemData.getMessageType() == SimpleBinaryMessageType.NODATA) {
										logger.debug("Device {} on port {} answer no new data", itemData.getAddress(), port);

										resendCounter = 0;

										processCommandQueue();
									} else if (itemData.getMessageType() == SimpleBinaryMessageType.UNKNOWN_DATA) {
										logger.warn("Device {} on port {} report unknown data", itemData.getAddress(), port);
										logger.debug("Sended data:");
										logger.debug(lastSendedData.getData().toString());
										
										//set state
										devicesStates.setDeviceState(((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);

										resendCounter = 0;

										processCommandQueue();
									} else if (itemData.getMessageType() == SimpleBinaryMessageType.UNKNOWN_ADDRESS) {
										logger.warn("Device {} on port {} report unknown address", itemData.getAddress(), port);
										logger.debug("Sended data:");
										logger.debug(lastSendedData.getData().toString());
										
										//set state
										devicesStates.setDeviceState(((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);										

										resendCounter = 0;

										processCommandQueue();
									} else if (itemData.getMessageType() == SimpleBinaryMessageType.SAVING_ERROR) {
										logger.warn("Device {} on port {} report saving data error", itemData.getAddress(), port);
										logger.debug("Sended data:");
										logger.debug(lastSendedData.getData().toString());
										
										//set state
										devicesStates.setDeviceState(((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);										

										resendCounter = 0;

										processCommandQueue();
									} else {
										resendCounter = 0;
										logger.warn("Device {} on port {} - Unsupported message type received: " + itemData.getMessageType().toString(), itemData.getAddress(),
												port);
										
										//set state
										devicesStates.setDeviceState(((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);											
									}
								}
							}

							// compact buffer
							inBuffer.compact();
						} catch (BufferUnderflowException ex) {
							logger.warn("Buffer underflow while reading " + ex.toString());
							// rewind buffer
							inBuffer.rewind();
							// compact buffer
							inBuffer.compact();
						} catch (NoValidCRCException ex) {
							logger.error("Invalid CRC while reading " + ex.toString());
							// compact buffer
							inBuffer.compact();

							if (resendCounter < MAX_RESEND_COUNT)
								this.resendData();
							else
								setWaitingForAnswer(false);
						} catch (NoValidItemInConfig ex) {
							logger.error("Item not found in items config " + ex.toString());
							// compact buffer
							inBuffer.compact();

							setWaitingForAnswer(false);
						} catch (UnknownMessageException ex) {
							logger.error("Income unknown message " + ex.toString());
							// clear buffer
							inBuffer.clear();

						} catch (NotImplementedException ex) {
							logger.warn("Message not implemented " + ex.toString());
							inBuffer.clear();

							setWaitingForAnswer(false);
						} catch (Exception ex) {
							logger.error("Reading incoming data error: {}", ex.toString());
							inBuffer.clear();
						}
					}
				}
			} catch (IOException e) {
				logger.error("Error receiving data on serial port {}: {}", port, e.getMessage());
			}
			break;
		}
	}

	public void resendData() {

		if (getLastSendedData() != null) {
			logger.debug("Resend data on serial port {}", port);

			// resendCounter++;
			sendDataOut(getLastSendedData());

			logger.debug("resendCounter {}", resendCounter);
		}
	}

	public SimpleBinaryItemData getLastSendedData() {
		return lastSendedData;
	}

	public void setLastSendedData(SimpleBinaryItemData data) {
		lastSendedData = data;
	}

	public void checkNewData() {

		if (isConnected()) {
			logger.debug("checkNewData() in mode {} is called", poolControl);

			if (poolControl == SimpleBinaryPoolControl.ONSCAN) {
				for (Map.Entry<String, SimpleBinaryBindingConfig> item : itemsConfig.entrySet()) {
					if (item.getValue().device.equals(this.deviceName)) {
						logger.debug("checkNewData() item={} direction={} ", item.getValue().item.getName(), item.getValue().direction);
						// input direction only
						if (item.getValue().direction < 2)
							this.sendReadData(item.getValue());
					}
				}
			} else {
				List<Integer> deviceItems = new ArrayList<Integer>();

				for (Map.Entry<String, SimpleBinaryBindingConfig> item : itemsConfig.entrySet()) {
					if (item.getValue().device.equals(this.deviceName)) {
						if (!deviceItems.contains(item.getValue().busAddress))
							deviceItems.add(item.getValue().busAddress);
					}
				}

				for (Integer integer : deviceItems) {
					this.sendNewDataCheck(integer);
				}

				deviceItems = null;
			}
		}
	}
}
