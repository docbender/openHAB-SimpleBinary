//---------------------------------------------------------------------------
//
// Name:        simpleBinary.h
// Author:      Vita Tucek
// Created:     28.11.2016
// License:     MIT
// Description: Implementation of SimpleBinary protocol for use with OpenHAB (C language)
//
//---------------------------------------------------------------------------

#ifndef SIMPLEBINARY_H
#define SIMPLEBINARY_H

#include "itemData.h"

typedef struct simpleBinary simpleBinary;

struct simpleBinary
{       
   //device address on line (RS485 address)
   int uartAddress; 
   //items count
   int size;
   //data
   itemData *data;
   //buffer for incoming serial data   
   char serbuf[64];
   //length of buffer
   int serbuflen;
   //flag that RTS handling is enabled
   bool RTSenabled;
   //RTS pin port
   GPIO_TypeDef *RTSport;
   //RTS pin number 
   GPIO_Pin_TypeDef RTSpin;
   //delay time between ask and answer 
   unsigned int sendDelay;
   //milisecond counter with receive data time
   unsigned long receiveTime;
   //index where search for item with new data start
   int newDataStartIndex;
   //index where last time new data was founded
   int newDataLastIndex; 
   //information about search loop in progress
   bool newDataCheckInProgress; 
    // pointer to execution function on force all data as new
   void (*pForceFunction)(simpleBinary*); 
};		 


   // Binding initialization
   void simpleBinary_init(simpleBinary *object, int UartAddress, int Size, void (*PForceFunction)(simpleBinary*));
   //Item initialization 
   itemData* simpleBinary_initItem(simpleBinary *object, int indexAndAddress, itemType_t type, void (*pFce)(itemData*));
   // check if address exist in items array
   bool simpleBinary_checkAddress(simpleBinary *object, int address);
   // Read and send data on given address
   void simpleBinary_readData(simpleBinary *object, int address);
   // Save byte to item by address
   bool simpleBinary_saveByte(simpleBinary *object, int address, const char* data);
   // Save word to item by address
   bool simpleBinary_saveWord(simpleBinary *object, int address, const char* data);
   // Save dword to item by address
   bool simpleBinary_saveDword(simpleBinary *object, int address, const char* data);
   // Save array to item by address
   bool simpleBinary_saveArray(simpleBinary *object, int address, const char *pData, int len);
   // Process data received by UART 
   void simpleBinary_processSerial(simpleBinary *object);
   // Set port and pin number to use as RTS signal
   void simpleBinary_enableRTS(simpleBinary *object, GPIO_TypeDef* GPIOx, GPIO_Pin_TypeDef GPIO_Pin);    
   // Set delay between receive and send message (in ms) for stabilization of communication line 
   void simpleBinary_setSendDelay(simpleBinary *object, unsigned int delayms);
   // Has data to send
   bool simpleBinary_available(simpleBinary *object);
   // Sends all data marked as new in one transaction (full-duplex connection only)
   void simpleBinary_sendNewData(simpleBinary *object);
   
   //send data saved in item
   void simpleBinary_sendData(simpleBinary *object, itemData *item);
   //send to master NoData packet
   void simpleBinary_sendNoData(simpleBinary *object);
   //send to master ErrorDuringSave packet
   void simpleBinary_sendSavingError(simpleBinary* object);
   //send to master InvalidAddress packet
   void simpleBinary_sendInvalidAddress(simpleBinary* object);
   //send to master UnknownData packet
   void simpleBinary_sendUnknownData(simpleBinary* object);
   //send to master WrongCRC packet (calculated CRC included)
   void simpleBinary_sendWrongData(simpleBinary* object, byte crc);
   //send to master RecivedDataAreOk packet
   void simpleBinary_sendOK(simpleBinary* object);
   //check if available new data to send
   void simpleBinary_checkNewData(simpleBinary* object);
   //write data into UART
   void simpleBinary_write(simpleBinary* object, const char* data, int length);

#endif


