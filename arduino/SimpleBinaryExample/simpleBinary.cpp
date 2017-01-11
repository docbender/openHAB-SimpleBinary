//---------------------------------------------------------------------------
//
// Name:        simpleBinary.cpp
// Author:      Vita Tucek
// Created:     20.8.2015
// Modified:    23.10.2016
// License:     MIT
// Description: Implementation of SimpleBinary protocol for OpenHAB
//
//---------------------------------------------------------------------------

#include "crc8.h"
#include "itemData.h"
#include "simpleBinary.h"


//when defined SEND_PACKET_WRONG_CRC in case received packet has wrong CRC answer "wrong data" is sent
//#define SEND_PACKET_WRONG_CRC
//when defined SEND_PACKET_UNKNOWN_DATA in case received packet contains data that has no action assigned answer "unknown data" is sent
#define SEND_PACKET_UNKNOWN_DATA
//timeout between two receives
#define INCOMING_DATA_TIMEOUT 10


/// Item initialization 
///
/// \param idx      Item index in configuration array 
/// \param address  Item address  
/// \param type     Item data type
///
itemData* simpleBinary::initItem(int idx, int address, itemType type)
{
   return _data[idx].init(address, type, NULL);  
}

/// Item initialization 
///
/// \param idx      Item index in configuration array 
/// \param address  Item address  
/// \param type     Item data type
/// \param (*pFce)(itemData*) Pointer to function that is executed on data receive. NULL for no action  
///
itemData* simpleBinary::initItem(int idx, int address, itemType type, void (*pFce)(itemData*))
{
   return _data[idx].init(address, type, pFce);  
}

/// check if address exist in items array
///
/// \param address  Searched item address  
///
bool simpleBinary::checkAddress(int address) const 
{
   for(int i=0;i<_size;i++)
   {
      if(_data[i].getAddress() == address)
         return true;
   }

   return false;
}


/// Save byte to item by address
///
/// \param address  Item address
/// \param data     Saved data  
///
bool simpleBinary::saveByte(int address, const char* data)
{
   for(int i=0;i<_size;i++)
   {
      if(_data[i].getAddress() == address)
      {
         return _data[i].saveByte(data);
      }
   }

   return false;
}

/// Save word to item by address
///
/// \param address  Item address
/// \param data     Saved data  
///
bool simpleBinary::saveWord(int address, const char* data)
{
   for(int i=0;i<_size;i++)
   {
      if(_data[i].getAddress() == address)
      {
         return _data[i].saveWord(data);
      }
   }

   return false;
}

/// Save dword to item by address
///
/// \param address  Item address
/// \param data     Saved data  
///
bool simpleBinary::saveDword(int address, const char* data)
{
   for(int i=0;i<_size;i++)
   {
      if(_data[i].getAddress() == address)
      {
         return _data[i].saveDword(data);
      }
   }

   return false;
}

/// Save byte array to item by address
///
/// \param address  Item address
/// \param data     Saved data
/// \param len      Data length to save     
///
bool simpleBinary::saveArray(int address, const char *pData, int len)
{
   for(int i=0;i<_size;i++)
   {
      if(_data[i].getAddress() == address)
      {
         if(_data[i].getType() == ARRAY)
         {
            return _data[i].saveArray(pData,len);      
         }
         else
            return false;
      }
   }

   return false;
}

/// Process data received by UART 
///
void simpleBinary::processSerial()
{
#ifdef INCOMING_DATA_TIMEOUT
   unsigned long now = millis();
   
   //clear old data
   if(serbuflen > 0)
   {
      unsigned long diff = now - receiveTime;
      
      if(diff > INCOMING_DATA_TIMEOUT)
      {
        serbuflen = 0;
      }
   }
#endif   
   //set last receive time
   receiveTime = now;
   
   //read all incoming data
   while (serial->available() > 0) 
   {
      int data = serial->read();

      serbuf[serbuflen++] = data;
   }

   while(serbuflen > 3)
   {     
      //search address
      while(serbuf[0] != _uartAddress)
      {
         if(--serbuflen <=0)
            return;
         
         //move data by one byte
         for(int i=0;i<serbuflen;i++)
         {
            serbuf[i] = serbuf[i+1];
         }
      }
    
      if(serbuflen < 4)
         return;

      int address;
      char crc;
              
      switch(serbuf[1])
      {
         //new data
         case (char)0xD0:  
               crc = CRC8::evalCRC(serbuf,3);

            if(crc == serbuf[3])
            {
               if(serbuf[2] == 0x00 || serbuf[2] == 0x01)
               {
                  if(serbuf[2] == 0x01)
                  {
                     //force all output data as new through user function
                     forceAllNewData();
                  }
                  
                  //check data to send
                  checkNewData();
               }
#ifdef SEND_PACKET_UNKNOWN_DATA              
               else
                  sendUnknownData();   
#endif                 
            }
#ifdef SEND_PACKET_WRONG_CRC
            else
               sendWrongData(crc);
#endif               
            serbuflen = 0;
            return;
            //read data  
         case (char)0xD1: 
            if(serbuflen < 5)
               return;

            crc = CRC8::evalCRC(serbuf,4);

            if(crc == serbuf[4])
            {
               address =  serbuf[2] | (serbuf[3] << 8); 
               readData(address); 
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            serbuflen = 0;
            return;
            //write byte
         case (char)0xDA:
            if(serbuflen < 6)
               return;
            //crc check
            crc = CRC8::evalCRC(serbuf,5);
            
            if(serbuf[5] == crc)   
            {                       
               //address
               address = serbuf[2] | (serbuf[3] << 8);
               //check address
               if(!checkAddress(address))
                  sendInvalidAddress();               
               //write data into memory
               if(saveByte(address,serbuf+4))
                  sendOK();
               else
                  sendSavingError();
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            //clear buffer
            serbuflen = 0;
            return;
            //write word
         case (char)0xDB:
            if(serbuflen < 7)
               return;

            //crc check
            crc = CRC8::evalCRC(serbuf,6);

            if(serbuf[6] == crc)
            {
               //address 
               address = serbuf[2] | (serbuf[3] << 8);
               //check address
               if(!checkAddress(address))
                  sendInvalidAddress();                 
               //write data into memory
               if(saveWord(address,serbuf+4))
                  sendOK();
               else
                  sendSavingError();               
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else            
               sendWrongData(crc);
#endif
            //clear buffer
            serbuflen = 0;
            return;
            //write dword
         case (char)0xDC:
         case (char)0xDD:
            if(serbuflen < 9)
               return;

            //crc check
            crc = CRC8::evalCRC(serbuf,8);
            
            if(serbuf[8] == crc)            
            {
               //address
               address = serbuf[2] | (serbuf[3] << 8);

               //check address
               if(!checkAddress(address))
                  sendInvalidAddress();
               //write data into memory
               if(saveDword(address,serbuf+4))
                  sendOK();
               else
                  sendSavingError();
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            //clear buffer
            serbuflen = 0;
            return;
            //write array
         case (char)0xDE:
            if(serbuflen < 6)
               return;
            
            int datalen;
            datalen = (serbuf[4] | (serbuf[5] << 8));
            //correct packet length check
            if(serbuflen < 7 + datalen)
               return;

            //crc check
            crc = CRC8::evalCRC(serbuf,6+datalen);              
            if(serbuf[7+datalen] == crc)
            {
               //address
               address = serbuf[2] | (serbuf[3] << 8);
               //check address
               if(!checkAddress(address))
                  sendInvalidAddress();
               char *pData = serbuf + 6;
               //write data into memory              
               if(saveArray(address,pData, datalen))
                  sendOK();
               else
                  sendSavingError();            
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            //clear buffer
            serbuflen = 0;
            return;                        
         default:
            //serbuflen = 0;
            serbuflen--;

            //move data by one byte - suggested by ModuloFS
            for(int i=0;i<serbuflen;i++)
            {
               serbuf[i] = serbuf[i+1];
            }
            break;            
      }
   }
}


/// Check if there are new data available to send
///
void simpleBinary::checkNewData()
{
   int i;

   if(_newDataCheckInProgress)
      i = _newDataLastIndex;
   else
   {
      i = _newDataStartIndex;
      _newDataCheckInProgress = true;
   }

   //check all items - start one after last stop
   do
   {   
      if(_data[i].hasNewData())
      {
         sendData(&(_data[i]));

         if(_size > 1)
         {
            int next = (++i < _size) ? i : 0;

            if(next == _newDataStartIndex)
            {
               _newDataStartIndex = (++_newDataStartIndex < _size) ? _newDataStartIndex : 0;
               _newDataCheckInProgress = false;      
            }
            else
            {
               _newDataLastIndex = next;
            }
         }
         
         return;
      } 

      i++;  

      if(i >= _size)
      i = 0;
   }
   while(i != _newDataStartIndex);

   _newDataStartIndex = (++i < _size) ? i : 0;
   _newDataCheckInProgress = false;  

   //no new data available
   sendNoData();
}

/// Read and send data on given address
///
/// \param address  Item address    
///
void simpleBinary::readData(int address)
{
   for(int i=0;i<_size;i++)
   {    
      //send data first finded item
      //itemData it = (*items)[i];
      if(_data[i].getAddress() == address)
      {
         sendData(&(_data[i]));
         
         return;
      }    
   }

   sendInvalidAddress();
}

/// Send answer that data are OK
void simpleBinary::sendOK()
{
   char data[4];

   data[0] = _uartAddress;
   data[1] = 0xE0;
   data[2] = 0x0;
   data[3] = CRC8::evalCRC(data,3);
   
   write(data,4);
}

/// Send answer that data are bad (crc)
void simpleBinary::sendWrongData(byte crc)
{
   char data[4];

   data[0] = _uartAddress;
   data[1] = 0xE1;
   data[2] = crc;
   data[3] = CRC8::evalCRC(data,3);
   
   write(data,4);
}

/// Send answer that data are unknown
void simpleBinary::sendUnknownData()
{
   char data[4];

   data[0] = _uartAddress;
   data[1] = 0xE3;
   data[2] = 0x0;
   data[3] = CRC8::evalCRC(data,3);
   
   write(data,4);
}

/// Send answer that has unknown address
void simpleBinary::sendInvalidAddress()
{
   char data[4];

   data[0] = _uartAddress;
   data[1] = 0xE4;
   data[2] = 0x0;
   data[3] = CRC8::evalCRC(data,3);
   
   write(data,4);
}

/// Send error while saving data
void simpleBinary::sendSavingError()
{
   char data[4];

   data[0] = _uartAddress;
   data[1] = 0xE5;
   data[2] = 0x0;
   data[3] = CRC8::evalCRC(data,3);
   
   write(data,4);
}

/// Send answer that there are no new data
void simpleBinary::sendNoData()
{
   char data[4];

   data[0] = _uartAddress;
   data[1] = 0xE2;
   data[2] = 0x0;
   data[3] = CRC8::evalCRC(data,3);
   
   write(data,4);
}

/// Send data to master device
///
/// \param item  Item to send    
///
void simpleBinary::sendData(itemData *item)
{
   char* data = NULL;
   int address;

   switch(item->getType())
   {
   case BYTE:  
      data = new char[6];

      data[0] = _uartAddress;
      data[1] = 0xDA;
      item->addressToMemory(data+2);
      data[4] = *(*item).readNewData();
      data[5] = CRC8::evalCRC(data,5);

      write(data,6);
      break;
   case WORD:
      data = new char[7];

      data[0] = _uartAddress;
      data[1] = 0xDB;
      item->addressToMemory(data+2);
      item->readNewDataToMemory(data+4);
      data[6] = CRC8::evalCRC(data,6);

      write(data,7);
      break;
   case DWORD:
   case FLOAT:   
      data = new char[9];

      data[0] = _uartAddress;
      data[1] = 0xDC;
      item->addressToMemory(data+2);
      item->readNewDataToMemory(data+4);
      data[8] = CRC8::evalCRC(data,8);

      write(data,9);
      break;
   case HSB:
   case RGB:
   case RGBW:    
      data = new char[9];

      data[0] = _uartAddress;
      data[1] = 0xDD;
      item->addressToMemory(data+2);
      item->readNewDataToMemory(data+4);
      data[8] = CRC8::evalCRC(data,8);

      write(data,9);
      break;    
   case ARRAY:
      int len = 7+item->getDataLength();
      data = new char[len];

      data[0] = _uartAddress;
      data[1] = 0xDE;
      item->addressToMemory(data+2);
      item->dataLengthToMemory(data+4);
      item->readNewDataToMemory(data+6);
      data[len-1] = CRC8::evalCRC(data,len-1);

      write(data,len);
      break;       
   }

   if(data != NULL)  
      delete[] data; 
}

/// Write data to serial port
///
/// \param data    Data to send    
/// \param length  Data length
///
void simpleBinary::write(const char* data, int length)
{   
   if(sendDelay > 0)
   {    
      unsigned long diff;
      unsigned long now = millis();
      
      if(now >= receiveTime)
         diff = now - receiveTime;
      else
         diff = 4294967295 - receiveTime + now;

      if(diff < sendDelay)
         delay(sendDelay-diff);    
   }

   if(RTSenabled)
   {
      digitalWrite(RTSpin, HIGH);
      serial->write(data,length);
      serial->flush();
      digitalWrite(RTSpin, LOW);
   }
   else
      serial->write(data,length);
}

/// Set pin number to use as RTS signal
///
/// \param pinNumber  Pin number for RTS signal    
///
void simpleBinary::enableRTS(int pinNumber)
{
   RTSenabled = true;
   RTSpin = pinNumber;

   pinMode(RTSpin, OUTPUT);
   digitalWrite(RTSpin, LOW);
}

/// Set delay between receive and send message (in ms) for stabilization of communication line
///
/// \param delayms  Delay in ms    
///
void simpleBinary::setSendDelay(unsigned int delayms)
{
   sendDelay = delayms;
}

/// Run assigned function to force all data as new one
///
void simpleBinary::forceAllNewData()
{
   if(pForceFunction == NULL) 
      return; 
   
   (*pForceFunction)(this);  
}

/// Has data to send
///
bool simpleBinary::available()
{
   //through all items
   for(int i=0;i<_size;i++)
   {   
      if(_data[i].hasNewData())
         return true;
   }

   return false;
}

/// Sends all data marked as new in one transaction (full-duplex connection only)
///
void simpleBinary::sendNewData()
{
   bool newData = false;
   
   //through all items
   for(int i=0;i<_size;i++)
   {   
      if(_data[i].hasNewData())
      {
         sendData(&(_data[i]));   

         newData = true;
      } 
   }

   //no new data available
   if(!newData)  
      sendNoData();  
}

/// Sends "Hi" message (full-duplex connection only)
///
void simpleBinary::sendHi()
{
   char* data = new char[4];
  
   data[0] = _uartAddress;
   data[1] = 0xE6;   
   data[2] = 0x00;
   data[3] = CRC8::evalCRC(data,3);
  
   write(data,4);
}
