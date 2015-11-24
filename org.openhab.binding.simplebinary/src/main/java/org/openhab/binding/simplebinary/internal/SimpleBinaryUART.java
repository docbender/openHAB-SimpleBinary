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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TooManyListenersException;

import org.apache.commons.io.IOUtils;
import org.openhab.binding.simplebinary.internal.SimpleBinaryDeviceState.DeviceStates;
import org.openhab.binding.simplebinary.internal.SimpleBinaryPortState.PortStates;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig;
import org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryInfoBindingConfig;
import org.openhab.core.events.EventPublisher;
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

	/** port name ex.: port, port1, ... */
	private String deviceName;
	/** port address ex.: COM1, /dev/ttyS1, ... */
	private String port;
	/** port baud rate */
	private int baud = 9600;
	/** defines maximum resend count */
	private int MAX_RESEND_COUNT = 2;

	private EventPublisher eventPublisher;
	/** item config */
	private Map<String, SimpleBinaryBindingConfig> itemsConfig;
	/** port id */
	private CommPortIdentifier portId;
	/** port instance */
	private SerialPort serialPort;
	/** input data stream */
	private InputStream inputStream;
	/** output data stream */
	private OutputStream outputStream;
	/** buffer for incoming data */
	private SimpleBinaryByteBuffer inBuffer = new SimpleBinaryByteBuffer(256);
	//private ByteBuffer inBuffer = ByteBuffer.allocate(256);
	/** flag that device is connected */
	private boolean connected = false;
	/** queue for commands */
	private Queue<SimpleBinaryItemData> commandQueue = new LinkedList<SimpleBinaryItemData>();
	/** flag waiting */
	private boolean waitingForAnswer = false;
	/** store last sent data */
	private SimpleBinaryItemData lastSentData = null;
	/** counting resend data */
	private int resendCounter = 0;
	/** timer measuring answer timeout */
	private Timer timer = new Timer();
	private TimerTask timeoutTask = null;
	/** Used pool control ex.: OnChange, OnScan */
	private SimpleBinaryPoolControl poolControl;
	/** Flag indicating RTS signal will be handled */
	private boolean forceRTS;
	/** Flag indicating RTS signal will be handled on inverted logic */
	private boolean invertedRTS;
	/** Variable for count minimal time before reset RTS signal */
	private long sentTimeTicks = 0;
	/** State of serial port */
	public SimpleBinaryPortState portState = new SimpleBinaryPortState();
	/** State of connected slave devices */
	public SimpleBinaryDeviceStateCollection devicesStates;

	/**
	 * Constructor
	 * 
	 * @param deviceName
	 * @param port
	 * @param baud
	 * @param simpleBinaryPoolControl
	 * @param forceRTS
	 * @param invertedRTS
	 */
	public SimpleBinaryUART(String deviceName, String port, int baud, SimpleBinaryPoolControl simpleBinaryPoolControl, boolean forceRTS, boolean invertedRTS) {
		this.deviceName = deviceName;
		this.port = port;
		this.baud = baud;
		this.poolControl = simpleBinaryPoolControl;
		this.forceRTS = forceRTS;
		this.invertedRTS = invertedRTS;

		inBuffer.order(ByteOrder.LITTLE_ENDIAN);
	}

	/**
	 * Method to set binding configuration
	 * 
	 * @param eventPublisher
	 * @param itemsConfig
	 * @param itemsInfoConfig
	 */
	public void setBindingData(EventPublisher eventPublisher, Map<String, SimpleBinaryBindingConfig> itemsConfig, Map<String, SimpleBinaryInfoBindingConfig> itemsInfoConfig) {
		this.eventPublisher = eventPublisher;
		this.itemsConfig = itemsConfig;

		this.portState.setBindingData(eventPublisher, itemsInfoConfig, this.deviceName);
		this.devicesStates = new SimpleBinaryDeviceStateCollection(deviceName, itemsInfoConfig, eventPublisher);
	}

	/**
	 * Method to clear inner binding configuration
	 */
	public void unsetBindingData() {
		this.eventPublisher = null;
		this.itemsConfig = null;
		this.devicesStates = null;
	}

	/**
	 * Return port hardware name 
	 * 
	 * @return
	 */
	public String getPort() {
		return port;
	}

	
	/**
	 * Check if port is opened
	 * 
	 * @return
	 */
	public boolean isConnected() {
		return connected;
	}

	/** 
	 * Open serial port
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#open()
	 */
	public Boolean open() {
		logger.debug("Opening port {}", this.port);

		portState.setState(PortStates.CLOSED);
		// clear device states
		devicesStates.clear();
		// set initial state for configured devices
		devicesStates.setStateToAllConfiguredDevices(this.deviceName, DeviceStates.UNKNOWN);
		// reset connected state
		connected = false;
		setWaitingForAnswer(false);

		try {
			// get port ID
			portId = CommPortIdentifier.getPortIdentifier(this.port);
		} catch (NoSuchPortException ex) {
			portState.setState(PortStates.NOT_EXIST);

			logger.warn("Port {} not found", this.port);
			logger.warn("Available ports: " + getCommPortListString());

			return false;
		}

		if (portId != null) {
			// initialize serial port
			try {
				serialPort = (SerialPort) portId.open("openHAB", 2000);
			} catch (PortInUseException e) {
				portState.setState(PortStates.NOT_AVAILABLE);

				logger.error("Port {} is in use", this.port);

				this.close();
				return false;
			}

			try {
				inputStream = serialPort.getInputStream();
			} catch (IOException e) {
				logger.error("Port {}:{}", this.port, e.toString());

				this.close();
				return false;
			}

			try {
				// get the output stream
				outputStream = serialPort.getOutputStream();
			} catch (IOException e) {
				logger.error("Port {}:{}", this.port, e.toString());

				this.close();
				return false;
			}

			try {
				serialPort.addEventListener(this);
			} catch (TooManyListenersException e) {
				logger.error("Port {}:{}", this.port, e.toString());

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
				logger.error("Port {}:{}", this.port, e.toString());

				this.close();
				return false;
			}
		}

		logger.debug("Port {} opened", this.port);
		// TODO zajistit update do eventbusu - pres stateCollection ????
		portState.setState(PortStates.LISTENING);
		connected = true;
		return true;
	}

	/**
	 * Return list of available port
	 * @return
	 */
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


	/**
	 * Close serial port
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#close()
	 */
	public void close() {
		serialPort.removeEventListener();
		IOUtils.closeQuietly(inputStream);
		IOUtils.closeQuietly(outputStream);
		serialPort.close();

		portState.setState(PortStates.CLOSED);
		connected = false;
	}

	/**
	 * Reconnect device
	 */
	private void reconnect() {
		logger.info("Port {}: Trying to reconnect", this.port);

		close();
		open();
	}

	
	/**
	 * Send command into device channel
	 * 
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#sendData(java.lang.String, org.openhab.core.types.Command, org.openhab.binding.simplebinary.internal.SimpleBinaryGenericBindingProvider.SimpleBinaryBindingConfig)
	 */
	public void sendData(String itemName, Command command, SimpleBinaryBindingConfig config) {
		// compile data
		// byte[] data = SimpleBinaryProtocol.compileDataFrame(itemName, command, config);
		SimpleBinaryItem data = SimpleBinaryProtocol.compileDataFrame(itemName, command, config);

		sendData(data);
	}

	/**
	 * Add compiled data item to sending queue 
	 *  
	 * @param data
	 */
	public void sendData(SimpleBinaryItem data) {
		if (data != null) {
			logger.debug("Port {}: Adding command into queue", this.port);
			commandQueue.add(data);

			processCommandQueue();
		} else
			logger.warn("Port {}: Nothing to send. Empty data", this.port);
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
				// calc minumum time for send + currentTime + 0ms
				sentTimeTicks = System.currentTimeMillis() + (((data.getData().length * 8) + 4) * 1000 / this.baud);

				serialPort.setRTS(invertedRTS ? false : true);

				logger.debug("Port {} - RTS set", this.port);
				// logger.info("RTS set");
			}

			// write string to serial port
			outputStream.write(data.getData());
			outputStream.flush();

			setWaitingForAnswer(true);

			setLastSentData(data);
		} catch (IOException e) {
			logger.error("Error writing to serial port {}", this.port);

			reconnect();
		}
	}

	/**
	 * Set / reset waiting task for answer for slave device
	 * 
	 * @param state
	 */
	private void setWaitingForAnswer(boolean state) {
		waitingForAnswer = state;

		if (state) {
			if (timeoutTask != null)
				timeoutTask.cancel();

			timeoutTask = new TimerTask() {
				@Override
				public void run() {
					timeoutTask = null;
					dataTimeouted();
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

	/**
	 * Method processed after waiting for answer is timeouted
	 */
	private void dataTimeouted() {
		logger.warn("Port {} - Receiving data timeouted", this.port);

		int address = this.getLastSentData().getAddress();

		devicesStates.setDeviceState(this.deviceName, address, DeviceStates.NOT_RESPONDING);
	}


	/**
	 * Serial events
	 * 
	 * @see gnu.io.SerialPortEventListener#serialEvent(gnu.io.SerialPortEvent)
	 */
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

				// comply minimum sending time. Added because on ubuntu14.04 event OUTPUT_BUFFER_EMPTY is called
				// periodically even before some data are send.
				if (System.currentTimeMillis() > sentTimeTicks) {
					serialPort.setRTS(invertedRTS ? true : false);

					logger.debug("Port {} - RTS reset", this.port);
					// logger.info("RTS reset");
				}
			}
			break;
		case SerialPortEvent.DATA_AVAILABLE:
			try {
				while (inputStream.available() > 0) {
					if (!readIncomingData())
						break;
				}

				logger.debug("Received data in serial port {} buffer - length {} bytes", port, inBuffer.position());

				// check data
				if (itemsConfig != null) {
					// check minimum length
					while (inBuffer.position() > 3) {
						// flip buffer
						inBuffer.flip();

						logger.debug("Reading input buffer - serial port {}, buffer remaining={} bytes", port, inBuffer.remaining());

						try {
							SimpleBinaryMessage itemData = SimpleBinaryProtocol.decompileData(inBuffer, itemsConfig, deviceName);

							if (itemData != null) {
								setWaitingForAnswer(false);

								if (itemData instanceof SimpleBinaryItem) {
									logger.debug("Incoming data");

									resendCounter = 0;

									// set state
									devicesStates.setDeviceState(this.deviceName, ((SimpleBinaryItem) itemData).getAddress(), DeviceStates.CONNECTED);

									State state = ((SimpleBinaryItem) itemData).getState();

									if (state == null) {
										logger.warn("Incoming data - Unknown item state");
									} else {
										logger.debug("Incoming data - item:{}/state:{}", ((SimpleBinaryItem) itemData).name, state);

										if (eventPublisher != null)
											eventPublisher.postUpdate(((SimpleBinaryItem) itemData).name, state);
									}

									// if data income on request "check new data" send it again for new check
									if (getLastSentData().getMessageType() == SimpleBinaryMessageType.CHECKNEWDATA) {
										inBuffer.compact();
										this.resendData();
										break;
									}
								} else if (itemData instanceof SimpleBinaryMessage) {
									logger.debug("Incoming control message");

									// set state
									devicesStates.setDeviceState(this.deviceName, ((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.CONNECTED);

									if (itemData.getMessageType() == SimpleBinaryMessageType.OK) {
										logger.debug("Device {} on port {} report data OK", itemData.getAddress(), port);

										resendCounter = 0;

									} else if (itemData.getMessageType() == SimpleBinaryMessageType.RESEND) {
										logger.info("Device {} on port {} request resend data", itemData.getAddress(), port);

										if (resendCounter < MAX_RESEND_COUNT) {
											inBuffer.compact();
											this.resendData();
											resendCounter++;
											break;
										} else {
											logger.warn("Max resend attempts reached.");
											// set state
											devicesStates.setDeviceState(this.deviceName, ((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.RESPONSE_ERROR);

											resendCounter = 0;
										}
									} else if (itemData.getMessageType() == SimpleBinaryMessageType.NODATA) {
										logger.debug("Device {} on port {} answer no new data", itemData.getAddress(), port);

										resendCounter = 0;

									} else if (itemData.getMessageType() == SimpleBinaryMessageType.UNKNOWN_DATA) {
										logger.warn("Device {} on port {} report unknown data", itemData.getAddress(), port);
										logger.debug("Sent data:");
										logger.debug(lastSentData.getData().toString());

										// set state
										devicesStates.setDeviceState(this.deviceName, ((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);

										resendCounter = 0;

									} else if (itemData.getMessageType() == SimpleBinaryMessageType.UNKNOWN_ADDRESS) {
										logger.warn("Device {} on port {} report unknown address", itemData.getAddress(), port);
										logger.debug("Sent data:");
										logger.debug(lastSentData.getData().toString());

										// set state
										devicesStates.setDeviceState(this.deviceName, ((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);

										resendCounter = 0;

									} else if (itemData.getMessageType() == SimpleBinaryMessageType.SAVING_ERROR) {
										logger.warn("Device {} on port {} report saving data error", itemData.getAddress(), port);
										logger.debug("Sent data:");
										logger.debug(lastSentData.getData().toString());

										// set state
										devicesStates.setDeviceState(this.deviceName, ((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);

										resendCounter = 0;

									} else {
										resendCounter = 0;
										logger.warn("Device {} on port {} - Unsupported message type received: " + itemData.getMessageType().toString(), itemData.getAddress(),
												port);

										// set state
										devicesStates.setDeviceState(this.deviceName, ((SimpleBinaryMessage) itemData).getAddress(), DeviceStates.DATA_ERROR);
									}
								}
							} else {

							}

							// compact buffer
							inBuffer.compact();

							processCommandQueue();
						} catch (BufferUnderflowException ex) {
							logger.warn("Port {} - Buffer underflow while reading: {}", this.port, ex.getMessage());
							// print details
							printCommunicationInfo();

							// rewind buffer
							inBuffer.rewind();
							// compact buffer
							inBuffer.compact();

							break;

						} catch (NoValidCRCException ex) {
							logger.error("Port {} - Invalid CRC while reading: ", this.port, ex.getMessage());
							// print details
							printCommunicationInfo();
							// compact buffer
							inBuffer.compact();

							if (resendCounter < MAX_RESEND_COUNT) {
								this.resendData();
								break;
							} else {
								setWaitingForAnswer(false);
								processCommandQueue();
							}
						} catch (NoValidItemInConfig ex) {
							logger.error("Port {} - Item not found in items config: ", this.port, ex.getMessage());
							// print details
							printCommunicationInfo();
							// compact buffer
							inBuffer.compact();

							setWaitingForAnswer(false);
							processCommandQueue();
						} catch (UnknownMessageException ex) {
							logger.error("Port {} - Income unknown message: ", this.port, ex.getMessage());
							// print details
							printCommunicationInfo();

							setWaitingForAnswer(false);

							inBuffer.rewind();

							if (inBuffer.remaining() < 5) {
								// clear buffer
								inBuffer.clear();

								logger.warn("Port {} - input buffer cleared", this.port);
								processCommandQueue();
							} else {
								logger.warn("Port {} - {} bytes remain. Triyng to find correct message. ", this.port, inBuffer.remaining());
								// delete first byte
								inBuffer.rewind();
								inBuffer.get();
								inBuffer.compact();
							}

						} catch (NotImplementedException ex) {
							logger.warn("Port {} - Message not implemented: ", this.port, ex.getMessage());
							// print details
							printCommunicationInfo();
							inBuffer.clear();

							setWaitingForAnswer(false);
							processCommandQueue();
						} catch (ModeChangeException ex) {
							logger.error("Port {} - Bad operation: {}", this.port, ex.getMessage());
							
							inBuffer.initialize(); 
							
							// print details
							printCommunicationInfo();
						
						} catch (Exception ex) {
							logger.error("Port {} - Reading incoming data error: {}", this.port, ex.getMessage());
							// print details
							printCommunicationInfo();
							inBuffer.clear();
							processCommandQueue();
						}

						// // check for new data
						// while (inputStream.available() > 0) {
						// if (!readIncomingData())
						// break;
						// }
					}
				}
			} catch (IOException e) {
				logger.error("Error receiving data on serial port {}: {}", port, e.getMessage());
			} catch (Exception ex) {
				logger.error("Exception receiving data on serial port {}: {}", port, ex.toString());
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				ex.printStackTrace(pw);
				logger.error(sw.toString());
			}
			break;
		}
	}

	/**
	 * Print communication information
	 * @throws ModeChangeException 
	 */
	private void printCommunicationInfo() throws ModeChangeException {
		// content of input buffer
		int position = inBuffer.position();
		inBuffer.rewind();
		byte[] data = new byte[inBuffer.limit()];
		inBuffer.get(data);
		inBuffer.position(position);
		logger.info("Data in input buffer: {}", SimpleBinaryProtocol.arrayToString(data, data.length));

		// last data out
		logger.info("Last sent data: {}", SimpleBinaryProtocol.arrayToString(lastSentData.getData(), lastSentData.getData().length));
	}

	/**
	 * Read data from serial port buffer
	 * 
	 * @throws IOException
	 * @throws ModeChangeException 
	 */
	private boolean readIncomingData() throws IOException, ModeChangeException {

		// logger.info("readIncomingData()");
		byte[] readBuffer = new byte[32];
		int bytes = inputStream.read(readBuffer);

		logger.debug("Received data on serial port {} - length {} bytes", port, bytes);

		if (bytes + inBuffer.position() >= inBuffer.capacity()) {
			logger.error("Buffer overrun");
			return false;
		} else {
			inBuffer.put(readBuffer, 0, bytes);
		}

		return true;
	}

	/**
	 * Provide resending last sent message 
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#resendData()
	 */
	public void resendData() {

		if (getLastSentData() != null) {
			logger.debug("Resend data on serial port {}", port);

			// resendCounter++;
			sendDataOut(getLastSentData());

			logger.debug("resendCounter {}", resendCounter);
		}
	}

	/**
	 * Return last sent message 
	 * 
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#getLastSentData()
	 */
	public SimpleBinaryItemData getLastSentData() {
		return lastSentData;
	}

	
	/**
	 * Set last sent data
	 * 
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#setLastSentData(org.openhab.binding.simplebinary.internal.SimpleBinaryItemData)
	 */
	public void setLastSentData(SimpleBinaryItemData data) {
		lastSentData = data;
	}

	/**  
	 * @see org.openhab.binding.simplebinary.internal.SimpleBinaryIDevice#checkNewData()
	 */
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
