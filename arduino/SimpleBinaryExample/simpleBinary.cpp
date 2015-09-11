//---------------------------------------------------------------------------
//
// Name:        simpleBinary.cpp
// Author:      Vita Tucek
// Created:     20.8.2015
// Description: Implementation of SimpleBinary protocol for use with OpenHAB
//
//---------------------------------------------------------------------------

#include "crc8.h"
#include "itemData.h"
#include "simpleBinary.h"


/// Item initialization 
///
/// \param idx      Item index in configuration array 
/// \param address  Item address  
/// \param type     Item data type
/// \param (*pFce)(itemData*) Pointer to function that is executed on data receive. NULL for no action  
///
void simpleBinary::initItem(int idx, int address, itemType type, void (*pFce)(itemData*))
{
  _data[idx].init(address, type, pFce);  
}

/// check if address exist in items array
///
/// \param address  Searched item address  
///
bool simpleBinary::checkAddress(int address)
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
bool simpleBinary::saveByte(int address, char* data)
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
bool simpleBinary::saveWord(int address, char* data)
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
bool simpleBinary::saveDword(int address, char* data)
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
bool simpleBinary::saveArray(int address, char *pData, int len)
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
    while (Serial.available() > 0) 
    {
      int data = Serial.read();

      serbuf[serbuflen++] = data;
    }

    if(serbuflen > 3)
    {
      if(serbuf[0] == _uartAddress)    
      {
        int address;
        char crc;
        
        switch(serbuf[1])
        {
          //new data
          case (char)0xD0:
            if(serbuf[2] == 0x00)
            {
               crc = CRC8::evalCRC(serbuf,3);

               if(crc != serbuf[3])
                  sendWrongData(serbuf[3]);
                else
                  checkNewData();
            }   
            else
              sendUnknownData();              

            serbuflen = 0;                       
            break;
          //read data  
          case (char)0xD1: 
            if(serbuflen < 5)
              break;
              
            address =  serbuf[2] | (serbuf[3] << 8);
            
            crc = CRC8::evalCRC(serbuf,4);

            if(crc != serbuf[4])
               sendWrongData(crc);
            else
               readData(address);            

            serbuflen = 0;           
            break;
          //write byte
          case (char)0xDA:
            if(serbuflen < 6)
              break;
            //address
            address = serbuf[2] | (serbuf[3] << 8);
            //crc check
            crc = CRC8::evalCRC(serbuf,5);
            if(serbuf[5] != crc)
              sendWrongData(crc);
            else         
            {
              //check address
              if(!checkAddress(address))
                sendInvalidAddress();               
              //write data into memory
              if(saveByte(address,serbuf+4))
                sendOK();
              else
                sendSavingError();
            }
            //clear buffer
            serbuflen = 0;                                                           
            break;
          //write word
          case (char)0xDB:
            if(serbuflen < 7)
              break;

            //address 
            address = serbuf[2] | (serbuf[3] << 8);
            //crc check
            crc = CRC8::evalCRC(serbuf,6);
            if(serbuf[6] != crc)
              sendWrongData(crc);
            else       
            {
              //check address
              if(!checkAddress(address))
                sendInvalidAddress();                 
              //write data into memory
              if(saveWord(address,serbuf+4))
                sendOK();
              else
                sendSavingError();              
            }
            //clear buffer
            serbuflen = 0;            
            break;
          //write dword
          case (char)0xDC:
          case (char)0xDD:
            if(serbuflen < 9)
              break;

            //address
            address = serbuf[2] | (serbuf[3] << 8);
            //crc check
            crc = CRC8::evalCRC(serbuf,8);
            if(serbuf[8] != crc)
              sendWrongData(crc);
            else
            {
              //check address
              if(!checkAddress(address))
                sendInvalidAddress();
              //write data into memory
              if(saveDword(address,serbuf+4))
                sendOK();
              else
                sendSavingError();  
            }
            //clear buffer
            serbuflen = 0;             
            break;
          //write array
          case (char)0xDE:
            if(serbuflen < 6)
              break;
            
            int datalen;
            datalen = (serbuf[4] | (serbuf[5] << 8));
            //correct packet length check
            if(serbuflen < 7 + datalen)
              break;

            //address
            address = serbuf[2] | (serbuf[3] << 8);
            //crc check
            crc = CRC8::evalCRC(serbuf,6+datalen);
            if(serbuf[7+datalen] != crc)
              sendWrongData(crc);
            else            
            {
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
            //clear buffer
            serbuflen = 0;               
            break;                        
          default:
            serbuflen = 0;
            sendUnknownData();
            break;            
         }
      }
      else
      {
        serbuflen = 0;
        return;
      }
    }
}


/// Check if there are new data available to send
///
void simpleBinary::checkNewData()
{
  //check all items
  for(int i=0;i<_size;i++)
  {    
    //send data first finded item
    //itemData it = (*items)[i];
    if(_data[i].hasNewData())
    {
      sendData(&(_data[i]));
      
      return;
    }    
  }

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
    
    Serial.write(data,4);
}

/// Send answer that data are bad (crc)
void simpleBinary::sendWrongData(byte crc)
{
    char data[4];

    data[0] = _uartAddress;
    data[1] = 0xE1;
    data[2] = crc;
    data[3] = CRC8::evalCRC(data,3);
    
    Serial.write(data,4);
}

/// Send answer that data are unknown
void simpleBinary::sendUnknownData()
{
    char data[4];

    data[0] = _uartAddress;
    data[1] = 0xE3;
    data[2] = 0x0;
    data[3] = CRC8::evalCRC(data,3);
    
    Serial.write(data,4);
}

/// Send answer that has unknown address
void simpleBinary::sendInvalidAddress()
{
    char data[4];

    data[0] = _uartAddress;
    data[1] = 0xE4;
    data[2] = 0x0;
    data[3] = CRC8::evalCRC(data,3);
    
    Serial.write(data,4);
}

/// Send error while saving data
void simpleBinary::sendSavingError()
{
    char data[4];

    data[0] = _uartAddress;
    data[1] = 0xE5;
    data[2] = 0x0;
    data[3] = CRC8::evalCRC(data,3);
    
    Serial.write(data,4);
}

/// Send answer that there are no new data
void simpleBinary::sendNoData()
{
    char data[4];

    data[0] = _uartAddress;
    data[1] = 0xE2;
    data[2] = 0x0;
    data[3] = CRC8::evalCRC(data,3);
    
    Serial.write(data,4);
}

/// Send data to master device
///
/// \param item  Item to send    
///
void simpleBinary::sendData(itemData *item)
{
  char* data = NULL;
  int address;
  
  switch((*item).getType())
  {
    case BYTE:  
      data = new char[6];
  
      data[0] = _uartAddress;
      data[1] = 0xDA;
      item->addressToMemory(data+2);
      data[4] = *(*item).readNewData();
      data[5] = CRC8::evalCRC(data,5);
  
      Serial.write(data,6);
    break;
    case WORD:
      data = new char[7];
  
      data[0] = _uartAddress;
      data[1] = 0xDB;
      item->addressToMemory(data+2);
      item->readNewDataToMemory(data+4);
      data[6] = CRC8::evalCRC(data,6);
  
      Serial.write(data,7);
    break;
    case DWORD:
    case FLOAT:   
      data = new char[9];
  
      data[0] = _uartAddress;
      data[1] = 0xDC;
      item->addressToMemory(data+2);
      item->readNewDataToMemory(data+4);
      data[8] = CRC8::evalCRC(data,8);
  
      Serial.write(data,9);
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
  
      Serial.write(data,9);
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
  
      Serial.write(data,len);
    break;       
  }

  if(data != NULL)  
    delete[] data;  
}

