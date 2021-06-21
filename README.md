## Introduction

This binding for openHAB has ability to connect directly DIY devices (based on Arduino or whatever else). Binding uses serial communication or network communication over implemented TCP server. 

[![openHAB](/.github/openHAB30.svg)](https://github.com/openhab)
[![Version](https://img.shields.io/github/v/release/docbender/openHAB-SimpleBinary?include_prereleases)](https://github.com/docbender/openHAB-SimpleBinary/releases)
[![Download](https://img.shields.io/github/downloads/docbender/openHAB-SimpleBinary/total.svg)](https://github.com/docbender/openHAB-SimpleBinary/releases)
[![Issues](https://img.shields.io/github/issues/docbender/openHAB-SimpleBinary)](https://github.com/docbender/openHAB-SimpleBinary/issues)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/docbender/openHAB-SimpleBinary.svg)](http://isitmaintained.com/project/docbender/openHAB-SimpleBinary "Average time to resolve an issue")
[![Percentage of issues still open](http://isitmaintained.com/badge/open/docbender/openHAB-SimpleBinary.svg)](http://isitmaintained.com/project/docbender/openHAB-SimpleBinary "Percentage of issues still open")

Used protocol is easy to implement. Implementation examples for [Arduino](https://github.com/docbender/openHAB-SimpleBinary/tree/master/arduino/SimpleBinary/examples/ControlLED_Serial) over serial, [STM8](https://github.com/docbender/openHAB-SimpleBinary/tree/master/STM8/ExampleLED), [ESP8266](https://github.com/docbender/openHAB-SimpleBinary/tree/master/arduino/SimpleBinary/examples/ControlLED_TCP) over TCP is part of repository. [Arduino IDE library](https://github.com/docbender/openHAB-SimpleBinary/tree/master/arduino) is also available.

Compiled binding is inserted into release branch: https://github.com/docbender/openHAB-SimpleBinary/releases

Binding is working with openHAB 3.0.

## Serial port
It is possible to configure several ports not only one. At one line it is possible to connect several devices (ready for RS422/RS485).

Binding sets the serial port speed according to user configuration. Other parameters are set as follows: 8 data bits, 1 stop bit, without parity and without flow control. 

Flow control (especially for RS-485 converters) can be provided through RTS pin. This can be turned on by adding _forceRTS_ parameter in binding port configuration. If it is required inverted function of RTS pin, _forceRTSInv_ parameter should be added in configuration instead _forceRTS_.

Communication can operate in 2 data read modes - OnScan and OnChange. 

## TCP server
At binding startup TCP server start listening on user defined port. In basic 256 clients are supported. Every connected client gets actual items states after device ID verification.

Known limitations: max.256 clients(protocol limitation).

## Operating modes
Communication can operate it 2 modes reading data from connected devices - OnScan and OnChange. In OnScan mode all data are reading cyclically. In OnChange mode only new data are sent to openHAB. Each device is polled whether has new data and then sends new data to openHAB. One of his modes must be choose in serial port connection. TCP server connection not use any of these modes as default. Because TCP connection is full-duplex, spontaneous states send is preferred and expected.

## Installation
Copy binding [release](https://github.com/docbender/openHAB-SimpleBinary/releases) in ${OPENHAB_HOME}/addons folder. 
Binding depends on openhab-transport-serial. It must be installed on target openHAB system. Installation can be done in console by running command:

    feature:install openhab-transport-serial

## Bridge
A SimpleBinary Bridge is needed to connect to device. It provides communication channel to all device connected to concreate UART port or TCP server. 

### UART Bridge
Basic bridge configuration requires to know serial port name and communication speed. Additional parameters can be defined.

<table>
  <tr><td><b>Parameter</b></td><td><b>Required</b></td><td><b>Notes</b></td></tr>
  <tr><td>port</td><td>Yes</td><td>UART port name (COM1 or &#47;dev&#47;ttyUSB1)</td></tr>
  <tr><td>baudRate</td><td>Yes</td><td>Communication speed [bits/s].</td></tr>
  <tr><td>pollControl</td><td>No</td><td>Communication mode. OnChange(default) or OnScan.</td></tr>
  <tr><td>pollRate</td><td>No</td><td>Read period [ms]. Default is 1000ms.</td></tr>
  <tr><td>forceRTS</td><td>No</td><td>Communication port force RTS pin activation. <b>Does not work with OH3 now.</b></td></tr>
  <tr><td>invertedRTS</td><td>No</td><td>Invert RTS pin state. <b>Does not work with OH3 now.</b></td></tr>
  <tr><td>charset</td><td>No</td><td>Define code page for communicated strings (e.g. ISO-8859-1, cp1250). If blank or wrong code page is defined, system code page is used. Used code page is printed into log file as INFO.</td></tr>
</table>

Bridge definition together with things can be defined in text files. See [generic bridge configuration](https://www.openhab.org/docs/configuration/things.html#defining-bridges-using-files) for details. In short text files .things are located in ${OPENHAB_CONF}/things folder. Basic bridge configuration with required parameters looks like this:

    Bridge simplebinary:uart_bridge:<device_id> "Label" @ "Location" [ port="<port ID>", baudRate="<baud rate>" ]

Example with optional parameter:

    Bridge simplebinary:uart_bridge:device1 "My Device"  [ port="/dev/ttyUSB1", baudRate="9600", charset="ISO-8859-1" ]

### TCP Bridge
Basic bridge is very simple. No parameters are required.

<table>
  <tr><td><b>Parameter</b></td><td><b>Required</b></td><td><b>Notes</b></td></tr>
  <tr><td>address</td><td>No</td><td>IP address to listen. Empty or 0.0.0.0 means listen for client from everywhere.</td></tr>
  <tr><td>port</td><td>No</td><td>TCP port number. Default 43243.</td></tr>
  <tr><td>charset</td><td>No</td><td>Define code page for communicated strings (e.g. ISO-8859-1, cp1250). If blank or wrong code page is defined, system code page is used. Used code page is printed into log file as INFO.</td></tr>
</table>

Bridge definition together with things can be defined in text files. See [generic bridge configuration](https://www.openhab.org/docs/configuration/things.html#defining-bridges-using-files) for details. In short text files .things are located in ${OPENHAB_CONF}/things folder. Basic bridge configuration with required parameters looks like this:

    Bridge simplebinary:tcp_bridge:<device_id> "Label" @ "Location" 

Example with optional parameter:

    Bridge simplebinary:tcp_bridge:device1 "My Device"  [ charset="ISO-8859-1" ]
    
## Things
To any individual bridge things could be added. Things are user defined so only _generic_device_ thing is available. 

Things definition can be defined in text files. Easiest way is to put it inside bridge definition:

    Bridge simplebinary:<bridge>:<device_id> "Label" @ "Location" [ port="<port ID>", baudRate="<baud rate>" ]
        Thing generic_device <thing_id> "Thing label" {            
        }
    }

## Channels
For _generic_device_ thing binding supports channels types listed in the table below. Individual channel type are converted to simple data types such as bit, byte, word, double word, float, array.

<table>
  <tr><td><b>Channel type</b></td><td><b>Data type</b></td><td><b>Address example</b></td><td><b>Notes</b></td></tr>
  <tr><td>chNumber</td><td>byte, word, dword, float</td><td>1:1:byte, 1:1:word, 1:1:dword, 1:1:float</td><td>Numbers are represent in signed form. Their maximum value depends on the used type.<br>Floating point number is stored in 4 bytes formatted according to IEEE 754.</td></tr>
  <tr><td>chColor</td><td>hsb, rgb, rgbw</td><td>1:1:rgb</td><td>All color parts are transferred in one double word. Each color component corresponds to one byte. Bytes assignment:<br>RGB - byte 0 - Red, 1 - Green, 2 - Blue, 3 - Not used</td></tr>
  <tr><td>chString</td><td>byte array</td><td>1:1:32</td><td>Array of bytes represent null terminated string. Length of array is specified behind address definition.</td></tr>
  <tr><td>chContact</td><td>byte</td><td>1:1</td><td>0 - off, 1 - on</td></tr>
  <tr><td>chSwitch</td><td>byte</td><td>1:1</td><td>0 - off, 1 - on</td></tr>
  <tr><td>chDimmer</td><td>byte</td><td>1:1</td><td>Value range is 0-100</td></tr>
  <tr><td>chRollershutter</td><td>word</td><td>1:1</td><td>State specifies position (0-100%). Command sends Move/Stop/Up/Down (1-Move, 2-Stop, 4-Up, 8-Down) in second byte or eventually target position (0-100%) in first byte.</td></tr>
</table>

Every channel has two parameters _stateAddress_ and _commandAddress_. At least one must have a defined value.

Channel text file definition as part of thing:

    Bridge simplebinary:<bridge>:<device_id> "Label" @ "Location" [ port="<port ID>", baudRate="<baud rate>" ] {
        Thing generic_device <thing_id> "Thing label" {
            Channels:
                Type <channel_type> : <channel_id> [ stateAddress="<address>", commandAddress="<address>" ]
        }
    }

### Configuration example
    Bridge simplebinary:uart_bridge:Device1 "My Device"  [ port="/dev/ttyUSB1", baudRate="9600", charset="ISO-8859-1" ] {
        Thing generic_device devState "Device state" {
            Channels:
                Type chNumber: watchdog1 [ stateAddress="1:0:byte" ]
                Type chNumber: watchdog2 [ stateAddress="2:0:byte" ]
                Type chNumber: watchdog3 [ stateAddress="3:0:byte" ]
        }
        Thing generic_device weather "Weather station" {
            Channels:
                Type chNumber: temperature [ stateAddress="1:1:float" ]                
                Type chNumber: pressure [ stateAddress="1:2:word" ]
                Type chNumber: humidity [ stateAddress="1:3:word" ]
        }
        Thing generic_device hall "Hall" {
            Channels:
                Type chContact: door_contact [ stateAddress="2:4" ]                
                Type chSwitch: light_switch [ stateAddress="2:5", commandAddress="2:5" ]
                Type chNumber: humidity [ stateAddress="2:6:word" ]
        }
        Thing generic_device device "Special device" {
            Channels:
                Type chSwitch: run  [ stateAddress="3:1", commandAddress="3:2" ]            
                Type chDimmer: rate [ stateAddress="3:3", commandAddress="3:4" ]
                Type chString: text [ stateAddress="3:5:20", commandAddress="3:5:20" ]
        }
    }
    Bridge simplebinary:tcp_bridge:Device2 "My second Device" {
        Thing generic_device rollers "Rollershutters" {
            Channels:
                Type chRollershutter: rs1 [ stateAddress="1:0", commandAddress="1:1" ]            
                Type chRollershutter: rs2 [ stateAddress="1:2", commandAddress="1:3" ]
        }
    }

## Protocol
Binding implements master/slave communication model for serial communication. OpenHAB binding is master and connected devices are slaves. Master sends command and waits for response from slave. Answer should arrived in order tens of milliseconds. However bindings has 2000ms timeout to receive answer. Communication protocol itself depends on requested operating mode of the device (OnScan / OnChange). Of course device can support both operating modes.

Network TCP connection use same protocol as serial communication but in producer/consumer communication model. When client is connected, "Hi" message must be send. Then server can verify and register device ID.

Every packet start with device address and is followed by message type. Minimum packet length is 4 bytes. Packets are secured with CRC8 which should be enough for short packets.

### Master query for new data - OnChange mode
On this packet master expecting as answer "data" packet or "no data" packet (message type 0xE2).
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xD0</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Control byte. Supported values:<br>0 - standard (data check only)<br>1 - force all data request</td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>
Control byte of this packet can says type of new data retrieve:
* _Standard_ (value 0) - only really new data are requested. 
* _Force all data_ (value 1) - that device should mark all his data as new and send it to master. This message master send when there was connection lost between devices. That because meantime data could be changed, openHAB or device restarted, ....

### Master query for data with specified address - OnScan mode
On this packet master expecting as answer "data" packet or "invalid address" packet (message type 0xE4).
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xD1</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Data address - low byte</td></tr>
  <tr><td>3</td><td>0xXX</td><td>Data address - high byte</td></tr>
  <tr><td>4</td><td>CRC8</td><td></td></tr>
</table>

### Data packet for data type byte
This "data" packet could be send by master to write data into slave or by slave as answer for data request.
Answer from slave should "done" when everything was right. 
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xDA</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Data address - low byte</td></tr>
  <tr><td>3</td><td>0xXX</td><td>Data address - high byte</td></tr>
  <tr><td>4</td><td>0xXX</td><td>Data</td></tr>
  <tr><td>5</td><td>CRC8</td><td></td></tr>
</table>

### Data packet for data type word
This "data" packet could be send by master to write data into slave or by slave as answer for data request.
Answer from slave should "done" when everything was right.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xDB</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Data address - low byte</td></tr>
  <tr><td>3</td><td>0xXX</td><td>Data address - high byte</td></tr>
  <tr><td>4</td><td>0xXX</td><td>Data - low byte</td></tr>
  <tr><td>5</td><td>0xXX</td><td>Data - high byte</td></tr>
  <tr><td>6</td><td>CRC8</td><td></td></tr>
</table>

### Data packet for data type dword
This "data" packet could be send by master to write data into slave or by slave as answer for data request.
Answer from slave should "done" when everything was right.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xDC</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Data address - low byte</td></tr>
  <tr><td>3</td><td>0xXX</td><td>Data address - high byte</td></tr>
  <tr><td>4</td><td>0xXX</td><td>Data - 1. byte (lowest)</td></tr>
  <tr><td>5</td><td>0xXX</td><td>Data - 2. byte</td></tr>
  <tr><td>6</td><td>0xXX</td><td>Data - 3. byte</td></tr>
  <tr><td>7</td><td>0xXX</td><td>Data - 4. byte (highest)</td></tr>
  <tr><td>8</td><td>CRC8</td><td></td></tr>
</table>

### Data packet for type HSB, RGB, RGBW
This "data" packet could be send by master to write data into slave or by slave as answer for data request.
Answer from slave should "done" when everything was right.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xDD</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Data address - low byte</td></tr>
  <tr><td>3</td><td>0xXX</td><td>Data address - high byte</td></tr>
  <tr><td>4</td><td>0xXX</td><td>Data - 1. byte (lowest)</td></tr>
  <tr><td>5</td><td>0xXX</td><td>Data - 2. byte</td></tr>
  <tr><td>6</td><td>0xXX</td><td>Data - 3. byte</td></tr>
  <tr><td>7</td><td>0xXX</td><td>Data - 4. byte (highest)</td></tr>
  <tr><td>8</td><td>CRC8</td><td></td></tr>
</table>

### Data packet for type array
This "data" packet could be send by master to write data into slave or by slave as answer for data request.
Answer from slave should "done" when everything was right.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xDE</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Data address - low byte</td></tr>
  <tr><td>3</td><td>0xXX</td><td>Data address - high byte</td></tr>
  <tr><td>4</td><td>0xXX</td><td>Data length - low byte</td></tr>
  <tr><td>5</td><td>0xXX</td><td>Data length - high byte</td></tr>
  <tr><td>6</td><td>0xXX</td><td>Data</td></tr>
  <tr><td>...</td><td>0xXX</td><td>Data</td></tr>
  <tr><td>n-1</td><td>CRC8</td><td></td></tr>
</table>

### Acknowledge packet - Done
Answer from slave that data write from master was accepted.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE0</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Error packet - Repeat
Answer that received packet had wrong CRC. On this packet master react by sending last packet again (max. 3 time).
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE1</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Here is expected CRC8 calculated by slave (master logged it for comparison). </td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Acknowledge packet - No new data
Slave react with this packet on <b>query for new data in OnChange mode</b> in case that there's no data to send.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE2</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Error packet - Unknown data
Slave react with this packet if received message had unknown/unsupported type.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE3</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Error packet - Unknown address
Slave react with this packet if received message had unknown data item address.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE4</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Error packet - Error while saving data
Slave send this packet when he could not save received data from master.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE5</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Hi packet
This packet is for connection model producer/consumer (TCP client/server connection). Client sends this packet after he is connected to server.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device address</td></tr>
  <tr><td>1</td><td>0xE6</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Welcome packet
This packet is for connection model producer/consumer (TCP client/server connection). Server sends this packet as Hi packet response.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device address</td></tr>
  <tr><td>1</td><td>0xD2</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Assigned address</td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Deny packet
This packet is for connection model producer/consumer (TCP client/server connection). Server sends this packet as Hi packet response.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device address</td></tr>
  <tr><td>1</td><td>0xD3</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

### Want all data
This packet is sent by device to tell that he want's all his data. In master/slave configuration it could be send as response for new data request (0xD0). 
Note: all data are normally sent when device is connected.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device address</td></tr>
  <tr><td>1</td><td>0xE7</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

## Implementation
The extent of implementation depends on the required features and data types (see chapter Protocol).

At first device should check if message is correctly received (CRC check). Secondly device should check if message is for him by compare message address and his own assigned address. **If the address is different device must not respond!** Otherwise device must response in corresponding way (see chapter Protocol).

## Implementation example
Implementation example for Arduino can be found in [Arduino library repo folder](https://github.com/docbender/openHAB-SimpleBinary/tree/master/arduino).

It consists of two main classes. Class _simpleBinary_ which contains protocol implementation itself and class _itemData_ which provides item data storage and handling with item.

<br>
<b>Class simpleBinary</b> - public methods
<table>
  <tr><td> </td><td><b>simpleBinary</b>(int uartAddress, int size)<br>Constructor. <br>Parameters: <b>uartAddress</b> - device address at communication line, <b>size</b> - number of exchanged items</td></tr>
  <tr><td>void</td><td><b>initItem</b>(int indexAndAddress, itemType type, void (*pFce)(itemData*))<br>Initilize item. <br>Parameters: <b>indexAndAddress</b> - item index in configuration array and also item address used during communication, <b>type</b> - item data type, last parameter is pointer to function that is executed on data receive(NULL for no action).</td></tr>
  <tr><td>void</td><td><b>processSerial</b>()<br>Process data received by UART.</td></tr>
  <tr><td>bool</td><td><b>checkAddress</b>(int address)<br>Check if address exist in items array.<br>Parameters: <b>address</b> - item address used during communication
</td></tr>
  <tr><td>void</td><td><b>readData</b>(int address)<br>Read data on given address and send it to master.<br>Parameters: <b>address</b> - item address used during communication</td></tr>
  <tr><td>bool</td><td><b>saveByte</b>(int address, char* data)<br>Save byte from given data pointer to addresed item. Return false if target is not expected data type.<br>Parameters: <b>address</b> - item address used during communication, <b>data</b> - pointer to data to save.</td></tr>
  <tr><td>bool</td><td><b>saveWord</b>(int address, char* data)<br>Save word from given data pointer to addresed item. Return false if target is not expected data type.<br>Parameters: <b>address</b> - item address used during communication, <b>data</b> - pointer to data to save.</td></tr>
  <tr><td>bool</td><td><b>saveDword</b>(int address, char* data)<br>Save double word from given data pointer to addresed item. Return false if target is not expected data type.<br>Parameters: <b>address</b> - item address used during communication, <b>data</b> - pointer to data to save.</td></tr>
  <tr><td>bool</td><td><b>saveArray</b>(int address, char *pData, int len)<br>Save byte array from given data pointer to addresed item. Return false if target is not expected data type.<br>Parameters: <b>address</b> - item address used during communication, <b>pData</b> - pointer to data to save, <b>len</b> - array length (byte count)</td></tr>
  <tr><td>int</td><td><b>size</b>()<br>Return items count.</td></tr>
  <tr><td>void</td><td><b>enableRTS</b>(int pinNumber)<br>Enable RTS handling. RTS pin is passed as parameter.</td></tr>
  <tr><td>void</td><td><b>setSendDelay</b>(unsigned int delayms)<br>Sets delay between receive and transmit message (in ms). Good for communication line stabilization.</td></tr>
</table>

<br>
<b>Class itemData</b> - public methods
<table>
  <tr><td></td><td><b>itemData</b>()<br>Constructor.</td></tr>
  <tr><td></td><td><b>itemData</b>(int address, itemType type, void (*pFce)(itemData*) )<br>Constructor with item initialization .<br>Parameters: <b>address</b> - item address used during communication, <b>type</b> - item data type, last parameter is pointer to function that is executed on data receive(NULL for no action). </td></tr>
  <tr><td> </td><td><b>itemData</b>(int address, itemType type, int size, void (*pFce)(itemData*) )<br>Constructor with item initialization for arrays.<br>Parameters: <b>address</b> - item address used during communication, <b>type</b> - item data type, <b>size</b> - data size in bytes, last parameter is pointer to function that is executed on data receive(NULL for no action). </td></tr>
  <tr><td>void</td><td><b>init</b>(int address, itemType type, void (*pFce)(itemData*))<br>Item initialization .<br>Parameters: <b>address</b> - item address used during communication, <b>type</b> - item data type, last parameter is pointer to function that is executed on data receive(NULL for no action). </td></tr>
  <tr><td>bool</td><td><b>saveByte</b>(char* data)<br>Save byte from given data pointer. Return false if target is not expected data type.<br>Parameters: <b>data</b> - pointer to data to save.</td></tr>
  <tr><td>bool</td><td><b>saveWord</b>(char* data)<br>Save word from given data pointer. Return false if target is not expected data type.<br>Parameters: <b>data</b> - pointer to data to save.</td></tr>
  <tr><td>bool</td><td><b>saveDword</b>(char* data)<br>Save double word from given data pointer. Return false if target is not expected data type.<br>Parameters: <b>data</b> - pointer to data to save/td></tr>
  <tr><td>bool</td><td><b>saveArray</b>(char *pData, int len)<br>Save array from given data pointer. Return false if target is not expected data type.<br>Parameters: <b>data</b> - pointer to data to save, <b>len</b> - array length (byte count)</td></tr>
  <tr><td>void</td><td><b>save</b>(int value)<br>Save int value.<br>Parameters: <b>value</b> - value to save</td></tr>
  <tr><td>void</td><td><b>save</b>(float value)<br>Save float value.<br>Parameters: <b>value</b> - value to save</td></tr>
  <tr><td>int</td><td><b>getAddress</b>()<br>Return item address.</td></tr>
  <tr><td>void</td><td><b>setNewData</b>()<br>Set item "new data" flag.</td></tr>
  <tr><td>bool</td><td><b>hasNewData</b>()<br>Check if item has "new data" flag set.</td></tr>
  <tr><td>char*</td><td><b>getData</b>()<br>Return item data.</td></tr>
  <tr><td>char*</td><td><b>readNewData</b>()<br>Return item data and reset "new data" flag.</td></tr>
  <tr><td>itemType</td><td><b>getType</b>()<br>Return item data type.</td></tr>
  <tr><td>int</td><td><b>getDataLength</b>()<br>Return length of data.</td></tr>
  <tr><td>void</td><td><b>executeAction</b>()<br>Execute action connected at item initialization.</td></tr>
</table>

### CRC8 calculation implementation

    char CRC8::evalCRC(char *data, int length)
    {
       int crc = 0;
       int i, j;
	
       for(j=0; j < length; j++)
       {
          crc ^= (data[j] << 8);
		
          for(i=0;i<8;i++)
          {
             if((crc & 0x8000) != 0)
                crc ^= (0x1070 << 3);
		
             crc <<= 1;
          }
       }	
       return (char)(crc >> 8);
    }
