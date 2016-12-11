//---------------------------------------------------------------------------
//
// Name:        simpleBinary.c
// Author:      Vita Tucek
// Created:     29.11.2016
// License:     MIT
// Description: Implementation of SimpleBinary protocol for OpenHAB (C language)
//
//---------------------------------------------------------------------------

#include <stm8s_gpio.h>
#include "basic_time_functions.h"
#include "serial.h"
#include "crc8.h"
#include "itemData.h"
#include "simpleBinary.h"


//when defined SEND_PACKET_WRONG_CRC in case received packet has wrong CRC answer "wrong data" is sent
//#define SEND_PACKET_WRONG_CRC
//when defined SEND_PACKET_UNKNOWN_DATA in case received packet contains data that has no action assigned answer "unknown data" is sent
#define SEND_PACKET_UNKNOWN_DATA
//timeout between two receives
#define INCOMING_DATA_TIMEOUT 10


void simpleBinary_init(simpleBinary *object, int UartAddress, int Size, void (*PForceFunction)(simpleBinary*))
{
	object->RTSenabled = false;
	
	object->sendDelay = 0;
	object->receiveTime = 0;
	object->serbuflen = 0;
	object->uartAddress = UartAddress;
	
	object->newDataStartIndex =0;
	object->newDataCheckInProgress = false;
	
	object->size = Size;
   object->data = (itemData*)malloc(sizeof(itemData)*Size); 
	 
	object->pForceFunction = PForceFunction;
}

/// Item initialization 
///
/// \param indexAndAddress      Item index in configuration array. 
///                              Simultaneously item address  
/// \param type     Item data type
/// \param (*pFce)(itemData*) Pointer to function that is executed on data receive. NULL for no action  
///
itemData* simpleBinary_initItem(simpleBinary *object, int indexAndAddress, itemType_t type, void (*pFce)(itemData*))
{
   itemData_init(&object->data[indexAndAddress], indexAndAddress, type, pFce);
   return &object->data[indexAndAddress];
}

/// check if address exist in items array
///
/// \param address  Searched item address  
///
bool simpleBinary_checkAddress(simpleBinary *object, int address) 
{
   int i;
   for(i=0;i<object->size;i++)
   {
      if(object->data[i].address == address)
         return true;
   }

   return false;
}


/// Save byte to item by address
///
/// \param address  Item address
/// \param data     Saved data  
///
bool simpleBinary_saveByte(simpleBinary *object, int address, const char* data)
{
   int i;
   for(i=0;i<object->size;i++)
   {
      if(object->data[i].address == address)
      {
         return itemData_saveRecvByte(&object->data[i],data);
      }
   }

   return false;
}

/// Save word to item by address
///
/// \param address  Item address
/// \param data     Saved data  
///
bool simpleBinary_saveWord(simpleBinary *object, int address, const char* data)
{
	int i;
   for(i=0;i<object->size;i++)
   {
      if(object->data[i].address == address)
      {
			return itemData_saveRecvWord(&object->data[i],data);
      }
   }

   return false;
}

/// Save dword to item by address
///
/// \param address  Item address
/// \param data     Saved data  
///
bool simpleBinary_saveDword(simpleBinary *object, int address, const char* data)
{
	int i;
   for(i=0;i<object->size;i++)
   {
      if(object->data[i].address == address)
      {
			return itemData_saveRecvDword(&object->data[i],data);
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
bool simpleBinary_saveArray(simpleBinary *object, int address, const char *pData, int len)
{
   int i;
   for(i=0;i<object->size;i++)
   {
      if(object->data[i].address == address)
      {
         if(object->data[i].type == ARRAY)
         {
            return itemData_saveRecvArray(&object->data[i],pData,len);
         }
         else
            return false;
      }
   }

   return false;
}

/// Process data received by UART 
///
void simpleBinary_processSerial(simpleBinary *object)
{
#ifdef INCOMING_DATA_TIMEOUT
   int address;
   char crc;
   int i;
   int datalen;
   
   if(serial_available() == 0)
      return;
      
   //clear old data
   if(object->serbuflen > 0)
   {    
      if(millis() - object->receiveTime > INCOMING_DATA_TIMEOUT)
      {
        object->serbuflen = 0;
      }
   }
#endif   
   //set last receive time
   object->receiveTime = millis();
   
   //read all incoming data
   while (serial_available() > 0) 
   {
      object->serbuf[object->serbuflen++] = serial_read();
   }

   while(object->serbuflen > 3)
   {     
      //search address
      while(object->serbuf[0] != object->uartAddress)
      {
         if(--object->serbuflen <=0)
            return;
         
         //move data by one byte
         for(i=0;i<object->serbuflen;i++)
         {
            object->serbuf[i] = object->serbuf[i+1];
         }
      }
    
      if(object->serbuflen < 4)
         return;
             
      switch(object->serbuf[1])
      {
         //new data
         case (char)0xD0:  
               crc = evalCRC(object->serbuf,3);

            if(crc == object->serbuf[3])
            {
               if(object->serbuf[2] == 0x00 || object->serbuf[2] == 0x01)
               {
                  if(object->serbuf[2] == 0x01)
                  {
                     //force all output data as new through user function
                     if(object->pForceFunction != NULL)   
							   (*object->pForceFunction)(object);  
                  }
                  
                  //check data to send
                  simpleBinary_checkNewData(object);
               }
#ifdef SEND_PACKET_UNKNOWN_DATA              
               else
                  simpleBinary_sendUnknownData(object);   
#endif                 
            }
#ifdef SEND_PACKET_WRONG_CRC
            else
               sendWrongData(crc);
#endif               
            object->serbuflen = 0;
            return;
            //read data  
         case (char)0xD1: 
            if(object->serbuflen < 5)
               return;

            crc = evalCRC(object->serbuf,4);

            if(crc == object->serbuf[4])
            {
               address =  object->serbuf[2] | (object->serbuf[3] << 8); 
               simpleBinary_readData(object,address); 
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            object->serbuflen = 0;
            return;
            //write byte
         case (char)0xDA:
            if(object->serbuflen < 6)
               return;
            //crc check
            crc = evalCRC(object->serbuf,5);
            
            if(object->serbuf[5] == crc)   
            {                       
               //address
               address = object->serbuf[2] | (object->serbuf[3] << 8);
               //check address
               if(!simpleBinary_checkAddress(object,address))
                  simpleBinary_sendInvalidAddress(object);               
               //write data into memory
               if(simpleBinary_saveByte(object,address,object->serbuf+4))
                  simpleBinary_sendOK(object);
               else
                  simpleBinary_sendSavingError(object);
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            //clear buffer
            object->serbuflen = 0;
            return;
            //write word
         case (char)0xDB:
            if(object->serbuflen < 7)
               return;

            //crc check
            crc = evalCRC(object->serbuf,6);

            if(object->serbuf[6] == crc)
            {
               //address 
               address = object->serbuf[2] | (object->serbuf[3] << 8);
               //check address
               if(!simpleBinary_checkAddress(object,address))
                  simpleBinary_sendInvalidAddress(object);                 
               //write data into memory
               if(simpleBinary_saveWord(object,address,object->serbuf+4))
                  simpleBinary_sendOK(object);
               else
                  simpleBinary_sendSavingError(object);               
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else            
               sendWrongData(crc);
#endif
            //clear buffer
            object->serbuflen = 0;
            return;
            //write dword
         case (char)0xDC:
         case (char)0xDD:
            if(object->serbuflen < 9)
               return;

            //crc check
            crc = evalCRC(object->serbuf,8);
            
            if(object->serbuf[8] == crc)            
            {
               //address
               address = object->serbuf[2] | (object->serbuf[3] << 8);

               //check address
               if(!simpleBinary_checkAddress(object, address))
                  simpleBinary_sendInvalidAddress(object);
               //write data into memory
               if(simpleBinary_saveDword(object,address,object->serbuf+4))
                  simpleBinary_sendOK(object);
               else
                  simpleBinary_sendSavingError(object);
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            //clear buffer
            object->serbuflen = 0;
            return;
            //write array
         case (char)0xDE:
            if(object->serbuflen < 6)
               return;
            
            
            datalen = (object->serbuf[4] | (object->serbuf[5] << 8));
            //correct packet length check
            if(object->serbuflen < 7 + datalen)
               return;

            //crc check
            crc = evalCRC(object->serbuf,6+datalen);              
            if(object->serbuf[7+datalen] == crc)
            {
               //address
               address = object->serbuf[2] | (object->serbuf[3] << 8);
               //check address
               if(!simpleBinary_checkAddress(object, address))
                  simpleBinary_sendInvalidAddress(object);
               //write data into memory              
               if(simpleBinary_saveArray(object, address, object->serbuf + 6, datalen))
                  simpleBinary_sendOK(object);
               else
                  simpleBinary_sendSavingError(object);            
            }
#ifdef SEND_PACKET_WRONG_CRC            
            else
               sendWrongData(crc);
#endif
            //clear buffer
            object->serbuflen = 0;
            return;                        
         default:
            //object->serbuflen = 0;
            object->serbuflen--;

            //move data by one byte - suggested by ModuloFS
            for(i=0;i<object->serbuflen;i++)
            {
               object->serbuf[i] = object->serbuf[i+1];
            }
            break;            
      }
   }
}


/// Check if there are new data available to send
///
void simpleBinary_checkNewData(simpleBinary* object)
{
   int i;

   if(object->newDataCheckInProgress)
      i = object->newDataLastIndex;
   else
   {
      i = object->newDataStartIndex;
      object->newDataCheckInProgress = true;
   }

   //check all items - start one after last stop
   do
   {   
      if(object->data[i].nda)
      {
         simpleBinary_sendData(object, &(object->data[i]));

         if(object->size > 1)
         {
            int next = (++i < object->size) ? i : 0;

            if(next == object->newDataStartIndex)
            {
               object->newDataStartIndex = (++object->newDataStartIndex < object->size) ? object->newDataStartIndex : 0;
               object->newDataCheckInProgress = false;      
            }
            else
            {
               object->newDataLastIndex = next;
            }
         }
         
         return;
      } 

      i++;  

      if(i >= object->size)
      i = 0;
   }
   while(i != object->newDataStartIndex);

   object->newDataStartIndex = (++i < object->size) ? i : 0;
   object->newDataCheckInProgress = false;  

   //no new data available
   simpleBinary_sendNoData(object);
}

/// Read and send data on given address
///
/// \param address  Item address    
///
void simpleBinary_readData(simpleBinary* object, int address)
{
   int i;
   for(i=0;i<object->size;i++)
   {    
      //send data first finded item
      //itemData it = (*items)[i];
      if(object->data[i].address == address)
      {
         simpleBinary_sendData(object, &(object->data[i]));
         
         return;
      }    
   }

   simpleBinary_sendInvalidAddress(object);
}

/// Send answer that data are OK
void simpleBinary_sendOK(simpleBinary* object)
{
   char data[4];

   data[0] = object->uartAddress;
   data[1] = 0xE0;
   data[2] = 0x0;
   data[3] = evalCRC(data,3);
   
   simpleBinary_write(object, data,4);
}

/// Send answer that data are bad (crc)
void simpleBinary_sendWrongData(simpleBinary* object, byte crc)
{
   char data[4];

   data[0] = object->uartAddress;
   data[1] = 0xE1;
   data[2] = crc;
   data[3] = evalCRC(data,3);
   
   simpleBinary_write(object,data,4);
}

/// Send answer that data are unknown
void simpleBinary_sendUnknownData(simpleBinary* object)
{
   char data[4];

   data[0] = object->uartAddress;
   data[1] = 0xE3;
   data[2] = 0x0;
   data[3] = evalCRC(data,3);
   
   simpleBinary_write(object,data,4);
}

/// Send answer that has unknown address
void simpleBinary_sendInvalidAddress(simpleBinary* object)
{
   char data[4];

   data[0] = object->uartAddress;
   data[1] = 0xE4;
   data[2] = 0x0;
   data[3] = evalCRC(data,3);
   
   simpleBinary_write(object,data,4);
}

/// Send error while saving data
void simpleBinary_sendSavingError(simpleBinary* object)
{
   char data[4];

   data[0] = object->uartAddress;
   data[1] = 0xE5;
   data[2] = 0x0;
   data[3] = evalCRC(data,3);
   
   simpleBinary_write(object,data,4);
}

/// Send answer that there are no new data
void simpleBinary_sendNoData(simpleBinary* object)
{
   char data[4];

   data[0] = object->uartAddress;
   data[1] = 0xE2;
   data[2] = 0x0;
   data[3] = evalCRC(data,3);
   
   simpleBinary_write(object,data,4);
}

/// Send data to master device
///
/// \param item  Item to send    
///
void simpleBinary_sendData(simpleBinary* object, itemData *item)
{
   int address;
   int len;
   char *data;

   switch(item->type)
   {
   case BYTE:  
      data = (char*)malloc(6);

      data[0] = object->uartAddress;
      data[1] = 0xDA;
      itemData_addressToMemory(item, data+2);
      data[4] = *itemData_readNewData(item);
      data[5] = evalCRC(data,5);

      simpleBinary_write(object,data,6);
      
      free(data);
      break;
   case WORD:
      data = (char*)malloc(7);

      data[0] = object->uartAddress;
      data[1] = 0xDB;
      itemData_addressToMemory(item, data+2);
      itemData_readNewDataToMemory(item, data+4);
      data[6] = evalCRC(data,6);

      simpleBinary_write(object,data,7);
      
      free(data);
      break;
   case DWORD:
   case FLOAT:   
      data = (char*)malloc(9);

      data[0] = object->uartAddress;
      data[1] = 0xDC;
      itemData_addressToMemory(item, data+2);
      itemData_readNewDataToMemory(item, data+4);
      data[8] = evalCRC(data,8);

      simpleBinary_write(object,data,9);
      
      free(data);
      break;
   case HSB:
   case RGB:
   case RGBW:    
      data = (char*)malloc(9);

      data[0] = object->uartAddress;
      data[1] = 0xDD;
      itemData_addressToMemory(item, data+2);
      itemData_readNewDataToMemory(item, data+4);
      data[8] = evalCRC(data,8);

      simpleBinary_write(object,data,9);
      
      free(data);
      break;    
   case ARRAY:
      len = 7+item->datalen;
      data = (char*)malloc(len);

      data[0] = object->uartAddress;
      data[1] = 0xDE;
      itemData_addressToMemory(item, data+2);
      itemData_dataLengthToMemory(item, data+4);
      itemData_readNewDataToMemory(item, data+6);
      data[len-1] = evalCRC(data,len-1);

      simpleBinary_write(object,data,len);
      
      free(data);
      break;       
   }
}

/// Write data to serial port
///
/// \param data    Data to send    
/// \param length  Data length
///
void simpleBinary_write(simpleBinary* object, const char* data, int length)
{   
   if(object->sendDelay > 0)
   {    
      uint32_t diff;
      uint32_t now = millis();
      
      diff = now - object->receiveTime;

      if(diff < object->sendDelay)
         delay(object->sendDelay-diff);    
   }

   if(object->RTSenabled)
   {
      GPIO_WriteHigh(object->RTSport,object->RTSpin);
      serial_write(data,length);
      serial_flush();
      GPIO_WriteLow(object->RTSport,object->RTSpin);
   }
   else
      serial_write(data,length);
}

/// Set pin number to use as RTS signal
///
/// \param GPIOx  Pin port for RTS signal    
/// \param GPIO_Pin  Pin number for RTS signal 
///
void simpleBinary_enableRTS(simpleBinary *object, GPIO_TypeDef* GPIOx, GPIO_Pin_TypeDef GPIO_Pin)
{
   object->RTSenabled = true;
   object->RTSport = GPIOx;   
   object->RTSpin = GPIO_Pin;
   
   GPIO_Init(GPIOx, GPIO_Pin, GPIO_MODE_OUT_PP_LOW_SLOW);
}

/// Set delay between receive and send message (in ms) for stabilization of communication line
///
/// \param delayms  Delay in ms    
///
void simpleBinary_setSendDelay(simpleBinary *object, unsigned int delayms)
{
   object->sendDelay = delayms;
}

/// Has data to send
///
bool simpleBinary_available(simpleBinary *object)
{
	 int i;
   //through all items
   for(i=0;i<object->size;i++)
   {   
      if(object->data[i].nda)
         return true;
   }

   return false;
}

/// Sends all data marked as new in one transaction (full-duplex connection only)
///
void simpleBinary_sendNewData(simpleBinary *object)
{
	 int i;
   bool newData = false;
   
   //through all items
   for(i=0;i<object->size;i++)
   {   
      if(object->data[i].nda)
      {
         simpleBinary_sendData(object, &(object->data[i]));   

         newData = true;
      } 
   }

   //no new data available
   if(!newData)  
      simpleBinary_sendNoData(object);  
}

/// Sends "Hi" message (full-duplex connection only)
///
void simpleBinary_sendHi(simpleBinary *object)
{
   char data[4];
  
   data[0] = object->uartAddress;
   data[1] = 0xE6;   
   data[2] = 0x00;
   data[3] = evalCRC(data,3);
  
   simpleBinary_write(object, data,4);
}
