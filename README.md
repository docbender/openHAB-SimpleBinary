## Introduction

This binding for openHAB has ability to connect DIY devices (based on Arduino or whatever else). Binding uses serial communication or implemented TCP server. 

Used protocol which is easy to implement. Implementation examples for [Arduino](https://github.com/docbender/openHAB-SimpleBinary/tree/master/arduino/SimpleBinary/examples/ControlLED_Serial), [STM8](https://github.com/docbender/openHAB-SimpleBinary/tree/master/STM8/ExampleLED), [ESP8266](https://github.com/docbender/openHAB-SimpleBinary/tree/master/arduino/SimpleBinary/examples/ControlLED_TCP) is part of repository. 

[GitHub Pages](https://pages.github.com/)

Compiled binding is inserted into release branch: https://github.com/docbender/openHAB-SimpleBinary/releases

Binding is working with openHAB 1.9 as well as with openHAB2 in 1.x compatibility mode.

## Serial port
It is possible to configure several ports not only one. At one line it is possible to connect several devices (ready for RS422/RS485).

Each port has separated configuration (speed, mode). Communication can operate in 2 data read modes - OnScan and OnChange. 

## TCP server
At binding startup TCP server on user defined port is started, ready to connect clients.

## Operating modes
Communication can operate it 2 modes reading data from connected devices - OnScan and OnChange. In OnScan mode all data are reading cyclically. In OnChange mode only new data are sent to openHAB. Each device is polled whether has new data and then sends new data to openHAB. One of his modes must be choose in serial port connection. TCP server connection not use any of these modes as default. Because TCP connection is full-duplex, spontaneous states send is preferred and expected.

## Supported data types
Binding support openHAB data types listed in the table. Individual item types are converted to simple data types such as byte, word, double word, float, array, rgb. For same types may be converted type defined, others has it strictly defined. Supported data types
 
<table>
  <tr><td><b>Item type</b></td><td><b>Supported data type</b></td><td><b>Notes</b></td></tr>
  <tr><td>Number</td><td>byte, word, dword, float</td><td>Numbers are represented in signed form. Their maximum value depends on the used type.<br>Floating point number is represented by float type. Float is stored in 4 bytes formatted according to IEEE 754.</td></tr>
  <tr><td>Color</td><td>hsb, rgb, rgbw</td><td>All color parts are transferred in one double word. Each color component corresponds to one byte. Assignment of bytes depending on the chosen data type is following:<br><br>HSB - byte 0 - Hue, 1 - Saturation , 2 - Brightness, 3 - Not used<br>RGB - byte 0 - Red, 1 - Green, 2 - Blue, 3 - Not used<br>RGBW - byte 0 - Red, 1 - Green, 2 - Blue, 3 - White</td></tr>
  <tr><td>String</td><td>array</td><td>Array of bytes. Length of array is specified in square brackets behind type definition (e.g. array[32] defines byte array with length 32 bytes)</td></tr>
  <tr><td>Contact</td><td>byte</td><td>0 - off, 1 - on</td></tr>
  <tr><td>Switch</td><td>byte</td><td>0 - off, 1 - on</td></tr>
  <tr><td>Dimmer</td><td>byte</td><td></td></tr>
  <tr><td>Rollershutter</td><td>word</td><td>Lower byte for position, upper byte for StopMove/UpDown command - 1-Move,2-Stop,4-Up,8-Down</td></tr>
</table>


## Binding configuration
In openhab.cfg is binding configured this way:

    ################################### SimpleBinary Binding ######################################
    #
    # Select port (ex.: COM1:115000;onscan;forceRTSInv or /dev/ttyUSB1:9600). It is possible defined 
    # more ports ex. port1, port2,port145,... Behind semicolon you can specify data pool control (optional).    
    # Options for reach slave data are onchange(ask configured devices for new data - default) 
    # or onscan(read all data every scan). Option forceRTS or forceRTSInv are for trigger RTS signal
    # during data send (useful for RS485 converter). 
    # TCP server is defined with tcpserver key. As value server port must be defined.
    # 
    # serial port configuration
    simplebinary:port=COM4:9600;onchange
    # TCP server configuration
    simplebinary:tcpserver=43243
    #refresh - check for new data interval - default 10000ms 
    simplebinary:refresh=2000

## Item configuration

Binding specify item configuration is located as usual in the brackets at the end of the configuration line. It consists of three compulsory and two optional parameters. 

Configuration format:

    simplebinary="port name:device address:item address:[used data type]:[direction]"

<table>
  <tr><td><b>Configuration parameter</b></td><td><b>Description</b></td></tr>
  <tr><td>port name</td><td>Port name that is specified in binding configuration in openhab.cfg. For <i>simplebinary:port=</i> port name is <b>port</b>. For <i>simplebinary:port1=</i> port name is <b>port1</b>. For <i>simplebinary:tcpserver=</i> port name is <b>tcpserver</b>.</td></tr>
  <tr><td>device address</td><td>The device address is number that device is indicated on the bus (for RS485 and ethernet)</td></tr>
  <tr><td>item address</td><td>It is the address under which the data is stored in the target device.</td></tr>
  <tr><td>used data type</td><td>Optional. Allow define data type. See table <i>Supported data types</i></td></tr>
  <tr><td>direction</td><td>Optional. Can be <b>I</b> - input, <b>O</b> - output or <b>IO</b> - both (default). It is used only for request data from connected devices. So if all device items are outputs only, there would be no requesting for data from device. It is also good to define data direction for inter-device communication items.</td></tr>
</table>

Example of binding configuration:

    Dimmer    Item01    "Value[%d]"    { simplebinary="port:1:1" }
    Number    Item02    "Value[%d]"    { simplebinary="port:1:3:dword:IO" }
    Number    Item03    "Value[%f]"    { simplebinary="port:1:4:float:O" }
    Number    Item04    "Value[%f]"    { simplebinary="port:1:14:float:I" }
    String    Item05    "Value[%s]"    { simplebinary="port:2:1:array[32]:O" }
    Color     TestColor01              { simplebinary="tcpserver:2:2:rgb:O" }
    
### Inter-device communication
Binding support inter-device communication. To configure this another device/binding can be specified separated by comma in item configuration:

    Number    Temperature    "Value[%f]"    { simplebinary="port:1:1:float:I", simplebinary="tcpserver:1:1:float:O" }
    Number    TemperatureCPU "Value[%f]"    { exec="<[/bin/cat /sys/class/thermal/thermal_zone0/temp:30000:JS(divideTemp.js)]", simplebinary="tcpserver:1:2:float:O" }    

### Diagnostic item configuration
Binding itself offers some diagnostic states. There are available statuses for communication port itself and also statuses for connected devices. These statuses can be provided into openHAB if they are properly configured (by use configuration parameter _info_ behind port name or device address). 

Port diagnostic info configuration format:

    simplebinary="port name:info:port status"

Port statuses:
<table>
  <tr><td><b>Status</b></td><td><b>Description</b></td><td><b>Returned values</b></td></tr>
  <tr><td>state</td><td>Return current port status.</td><td>0 - Unknown<br>1 - Listening (opened and ready)<br>2 - Closed<br>3 - Not exist<br>4 - Not available (probably used)</td></tr>
  <tr><td>previous_state</td><td>Return previous port status</td><td>Same as state</td></tr>
  <tr><td>state_change_time</td><td>Return time when status was changed</td><td>DateTime value</td></tr>
</table>

Device diagnostic info configuration format:

    simplebinary="port name:device address:info:device status"

Device statuses:
<table>
  <tr><td><b>Status</b></td><td><b>Description</b></td><td><b>Returned values</b></td></tr>
  <tr><td>state</td><td>Return current device status.</td><td>0 - Unknown<br>1 - Connected and answering<br>2 - Not responding<br>3 - Response error (device still wants repeat query - received message had bad CRC)<br>4 - Data error (device report unknown item address, unknown data, unsupported message or error while saving delivered data)</td></tr>
  <tr><td>previous_state</td><td>Return previous device status</td><td>Same as state</td></tr>
  <tr><td>state_change_time</td><td>Return time when status was changed</td><td>DateTime value</td></tr>
  <tr><td>packet_lost</td><td>Return percentage of packet lost (not delivered, bad CRC, ...) within last 5 minutes</td><td>0-100%</td></tr>
</table>

Example of diagnostic item configuration:

    Number    PortState            "Port state [%s]"                 { simplebinary="port:info:state" }
    Number    PortPreviouState     "Port previous state [%s]"        { simplebinary="port:info:previous_state" }
    DateTime  PortStateChangeTime  "Port changed [%1$tA, %1$td.%1$tm.%1$tY %1$tT]"    { simplebinary="port:info:state_change_time" }

    Number    Dev01State           "Device 1 state [%s]"             { simplebinary="port:1:info:state" }
    Number    Dev01PreviouState    "Device 1 previous state [%s]"    { simplebinary="port:1:info:previous_state" }
    DateTime  Dev01StateChangeTime "Device 1 changed [%1$tA, %1$td.%1$tm.%1$tY %1$tT]" { simplebinary="port:1:info:state_change_time" }
    Number    Dev01PacketLost      "Device 1 packet lost rate [%s]"  { simplebinary="port:1:info:packet_lost" }

    Number    Dev02State           "Device 2 state [%s]"             { simplebinary="port:2:info:state" }
    Number    Dev02PreviouState    "Device 2 previous state [%s]"    { simplebinary="port:2:info:previous_state" }
    DateTime  Dev02StateChangeTime "Device 2 changed [%1$tA, %1$td.%1$tm.%1$tY %1$tT]" { simplebinary="port:2:info:state_change_time" }
    Number    Dev02PacketLost      "Device 2 packet lost rate [%s]"  { simplebinary="port:2:info:packet_lost" }


## UART setting
Binding sets the serial port speed according to user configuration. Other parameters are set as follows: 8 data bits, 1 stop bit, without parity and without flow control. 

Flow control (especially for RS-485 converters) can be provided through RTS pin. This can be turned on by adding _forceRTS_ parameter in binding port configuration. If it is required inverted function of RTS pin, _forceRTSInv_ parameter should be added in configuration instead _forceRTS_.

## Protocol
Binding implements master/slave communication model. OpenHAB binding is master and connected devices are slaves. Master sends command and waits for response from slave. Answer should arrived in order tens of milliseconds. However bindings has 2000ms timeout to receive answer.

Communication protocol itself depends on requested operating mode of the device (OnScan / OnChange). Of course device can support both operating modes.

Every packet start with slave address and is followed by message type. Minimum packet length is 4 bytes. Packets are secured with CRC8 which should be enough for short packets.

###Master query for new data - OnChange mode
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

###Master query for data with specified address - OnScan mode
On this packet master expecting as answer "data" packet or "invalid address" packet (message type 0xE4).
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xD1</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Data address - low byte</td></tr>
  <tr><td>3</td><td>0xXX</td><td>Data address - high byte</td></tr>
  <tr><td>4</td><td>CRC8</td><td></td></tr>
</table>

###Data packet for data type byte
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

###Data packet for data type word
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

###Data packet for data type dword
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

###Data packet for type HSB, RGB, RGBW
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

###Data packet for type array
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

###Acknowledge packet - Done
Answer from slave that data write from master was accepted.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE0</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

###Error packet - Repeat
Answer that received packet had wrong CRC. On this packet master react by sending last packet again (max. 3 time).
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE1</td><td>Message type</td></tr>
  <tr><td>2</td><td>0xXX</td><td>Here is expected CRC8 calculated by slave (master logged it for comparison). </td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

###Acknowledge packet - No new data
Slave react with this packet on <b>query for new data in OnChange mode</b> in case that there's no data to send.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE2</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

###Error packet - Unknown data
Slave react with this packet if received message had unknown/unsupported type.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE3</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

###Error packet - Unknown address
Slave react with this packet if received message had unknown data item address.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE4</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

###Error packet - Error while saving data
Slave send this packet when he could not save received data from master.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device slave address</td></tr>
  <tr><td>1</td><td>0xE5</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

###Hi packet
This packet is for connection model producer/consumer (TCP client/server connection). Client sends this packet after he is connected to server.
<table>
  <tr><td><b>Byte</b></td><td><b>Value</b></td><td><b>Description</b></td></tr>
  <tr><td>0</td><td>0xXX</td><td>Device address</td></tr>
  <tr><td>1</td><td>0xE6</td><td>Message type</td></tr>
  <tr><td>2</td><td>0x00</td><td></td></tr>
  <tr><td>3</td><td>CRC8</td><td></td></tr>
</table>

##Implementation
The extent of implementation depends on the required features and data types (see chapter Protocol).

At first device should check if message is correctly received (CRC check). Secondly device should check if message is for him by compare message address and his own assigned address. **If the address is different device must not respond!** Otherwise device must response in corresponding way (see chapter Protocol).

##Implementation example
Implementation example for Arduino can be found in repo folder [arduino/SimpleBinaryExample](https://github.com/docbender/openHAB-SimpleBinary/tree/master/arduino/SimpleBinaryExample).

It consists of two main classes. Class _simpleBinary_ which contains protocol implementation itself and class _itemData_ which provides item data storage and handling with item.

<br>
<b>Class simpleBinary</b> - public methods
<table>
  <tr><td> </td><td><b>simpleBinary</b>(int uartAddress, int size)<br>Constructor. <br>Parameters: <b>uartAddress</b> - device address at communication line, <b>size</b> - number of exchanged items</td></tr>
  <tr><td>void</td><td><b>initItem</b>(int idx, int address, itemType type, void (*pFce)(itemData*))<br>Initilize item. <br>Parameters: <b>idx</b> - item index in array, <b>address</b> - item address used during communication, <b>type</b> - item data type, last parameter is pointer to function that is executed on data receive(NULL for no action).</td></tr>
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

###CRC8 calculation implementation

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
