//---------------------------------------------------------------------------
//
// Name:        simpleBinary.h
// Author:      Vita Tucek
// Created:     20.8.2015
// License:     MIT
// Description: Implementation of SimpleBinary protocol for use with OpenHAB
//
//---------------------------------------------------------------------------

#ifndef SIMPLEBINARY_H
#define SIMPLEBINARY_H

#include <Stream.h>
#include "itemData.h"


class simpleBinary
{     
  public:    
    simpleBinary(int UartAddress, int Size, Stream& SerialStream, void (*PForceFunction)(simpleBinary*)=NULL ):sendDelay(0),receiveTime(0),serbuflen(0),_uartAddress(UartAddress),_size(Size),_newDataStartIndex(0), _newDataCheckInProgress(false)  
            {  _data = new itemData[Size]; serial = &SerialStream; pForceFunction = PForceFunction; };    

    ~simpleBinary() { delete[] _data; };
    
    const itemData& operator[](const int idx) const { return _data[idx]; }; 
    itemData& operator[](int idx) { return _data[idx]; };

    //Item initialization 
    itemData* initItem(int idx, int address, itemType type);
    //Item initialization 
    itemData* initItem(int idx, int address, itemType type, void (*pFce)(itemData*));
    // check if address exist in items array
    bool checkAddress(int address) const;
    // Read and send data on given address
    void readData(int address);
    // Save byte to item by address
    bool saveByte(int address, const char* data);
    // Save word to item by address
    bool saveWord(int address, const char* data);
    // Save dword to item by address
    bool saveDword(int address, const char* data);
    // Save array to item by address
    bool saveArray(int address, const char *pData, int len);
    // Return item count
    int size() const { return _size; };
    // Process data received by UART 
    void processSerial();
    // Set pin number to use as RTS signal
    void enableRTS(int pinNumber);    
    // Set delay between receive and send message (in ms) for stabilization of communication line 
    void setSendDelay(unsigned int delayms);
    
    // pointer to execution function on force all data as new
    void (*pForceFunction)(simpleBinary*); 

  private:
     //device address on line (RS485 address)
     int _uartAddress; 
     //items count
     int _size;
     //data
     itemData *_data;
     //buffer for incoming serial data   
     char serbuf[64];
     //length of buffer
     int serbuflen;
     //flag that RTS handling is enabled
     bool RTSenabled = false;
     //RTS pin number 
     int RTSpin;
     //delay time between ask and answer 
     unsigned int sendDelay;
     //milisecond counter with receive data time
     unsigned long receiveTime;
     //index where search for item with new data start
     int _newDataStartIndex;
     //index where last time new data was founded
     int _newDataLastIndex; 
     //information about search loop in progress
     bool _newDataCheckInProgress;     

     Stream *serial;

     //send data saved in item
     void sendData(itemData *item);
     //send to master NoData packet
     void sendNoData();
     //send to master ErrorDuringSave packet
     void sendSavingError();
     //send to master InvalidAddress packet
     void sendInvalidAddress();
     //send to master UnknownData packet
     void sendUnknownData();
     //send to master WrongCRC packet (calculated CRC included)
     void sendWrongData(byte crc);
     //send to master RecivedDataAreOk packet
     void sendOK();
     //check if available new data to send
     void checkNewData();
     //write data into UART
     void write(const char* data, int length);
     //run assigned function when force called
     void forceAllNewData();

}; 

#endif


