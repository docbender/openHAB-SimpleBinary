## Introduction

This binding for openHAB has ability to connect DIY devices (based on Arduino or other board with small MCU). C

Communication is done through serial port, so you can use RS232, RS485 (multiple devices on one bus is supported) or USB with virtual serial port driver. And also it is possible to configure several port not only one. 

Used protocol is preatty simple. Communication can operate it 2 modes reading data from connected devices - OnScan and OnChange. 

More about this can be found in wiki page: https://github.com/docbender/openHAB-SimpleBinary/wiki

Compiled binding is inserted into release branch.
