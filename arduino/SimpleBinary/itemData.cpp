//---------------------------------------------------------------------------
//
// Name:        itemData.cpp
// Author:      Vita Tucek
// Created:     20.8.2015
// License:     MIT
// Description: Class for holding data
//
//---------------------------------------------------------------------------

#include "Arduino.h"
#include "itemData.h"
#include "crc8.h"

/// constructor
itemData::itemData()
{
  _nda = false;
  pExecFunction = NULL;
}

/// constructor
itemData::itemData(int address, itemType type)
{
  init(address, type, NULL);
}

/// constructor
itemData::itemData(int address, itemType type, void (*pFce)(itemData*))
{
  init(address, type, pFce);
}

///array constructor
itemData::itemData(int address, itemType type, int size, void (*pFce)(itemData*))
{
  _address = address;
  _type = type;
  _nda = false;
  _data = (char*)new byte[size];     
  _datalen = size; 
  pExecFunction = pFce;
}

itemData::~itemData()
{
  delete[] _data;
}

///
itemData* itemData::init(int address, itemType type, void (*pFce)(itemData*))
{
  _address = address;
  _type = type;
  _nda = false;
  pExecFunction = pFce;

  if(_type == BYTE)
  {
    _data = (char*)new byte[1]; 
    _datalen = 1;
  }
  else if(_type == WORD)
  {
    _data = (char*)new byte[2]; 
    _datalen = 2;
  }    
  else if(_type == DWORD || _type == FLOAT || _type == RGB || _type == RGBW || _type == HSB)
  {
    _data = (char*)new byte[4];     
    _datalen = 4;
  }    
  else
    _data = NULL;

  return this;
}

///
void itemData::executeAction()
{
  if(pExecFunction == NULL) 
    return; 
    
  (*pExecFunction)(this);  
}

///
bool itemData::saveByte(const char* data)
{
   if(_type != BYTE)
     return false;

   _data[0] = data[0];

   //setNewData();
   executeAction();

   return true;
}

//
bool itemData::saveWord(const char* data)
{
  if(_type != WORD)
    return false;
      
  _data[0] = data[0];
  _data[1] = data[1];

  //setNewData();
  executeAction();
  
  return true;
}

//
bool itemData::saveDword(const char* data)
{
  if(_type == DWORD || _type == FLOAT || _type == RGB || _type == RGBW || _type == HSB)
  {      
      _data[0] = data[0];
      _data[1] = data[1];
      _data[2] = data[2];
      _data[3] = data[3];

      //setNewData(); 
      executeAction();
      
      return true;
  }
  else
    return false;
}

//
bool itemData::saveArray(const char *pData, const int len)
{
  if(_type == ARRAY)
  {
    if(_datalen < len)
      return false;

    for(int i=0;i<len;i++)
    {
      _data[i] = pData[i];
    }

    //setNewData();
    executeAction();

    return true;
  }
  else
    return false;
}

//    
void itemData::save(const char value) 
{ 
   save((uint8_t)(value & 0xFF));
}

//    
void itemData::save(const uint8_t value) 
{ 
   if(_type == BYTE) _data[0] = value; 
   else if(_type == WORD) { int16_t tmp = (int16_t)value; memcpy(_data,&tmp,2); } 
   else if(_type == DWORD) { int32_t tmp = (int32_t)value; memcpy(_data,&tmp,4); } 
}    

//   
void itemData::save(const int16_t value) 
{ 
   save((uint16_t)(value & 0xFFFF));
}

//   
void itemData::save(const uint16_t value) 
{ 
   if(_type == BYTE) _data[0] = (char)value; 
   else if(_type == WORD) { int16_t tmp = (int16_t)value; memcpy(_data,&tmp,2); } 
   else if(_type == DWORD) { int32_t tmp = (int32_t)value; memcpy(_data,&tmp,4); } 
};

//   
void itemData::save(const int32_t value) 
{ 
   save((uint32_t)(value & 0xFFFFFFFF));
}

//
void itemData::save(const uint32_t value) 
{ 
   if(_type == BYTE) _data[0] = (uint8_t)value; 
   else if(_type == WORD) { uint16_t tmp = (uint16_t)value; memcpy(_data,&tmp,2); } 
   else if(_type == DWORD) { uint32_t tmp = (uint32_t)value; memcpy(_data,&tmp,4); } 
};

//
void itemData::save(const float value) 
{ 
   if(_type == FLOAT) memcpy(_data,&value,4); 
}

//save data only when different and set "new data" flag - for transceive data
void itemData::saveSet(const char value) 
{ 
   if(!equals(value))
   {
      save(value); 
      setNewData(); 
   }
}

//
void itemData::saveSet(const uint8_t value) 
{ 
   if(!equals(value))
   {
      save(value); 
      setNewData(); 
   }
}

//
void itemData::saveSet(const int16_t value) 
{ 
   if(!equals(value))
   {
      save(value); 
      setNewData(); 
   }
}

//
void itemData::saveSet(const uint16_t value) 
{ 
   if(!equals(value))
   {
      save(value); 
      setNewData(); 
   }
}

//
void itemData::saveSet(const int32_t value) 
{ 
   if(!equals(value))
   {
      save(value); 
      setNewData(); 
   }
}

//
void itemData::saveSet(const uint32_t value) 
{ 
   if(!equals(value))
   {
      save(value); 
      setNewData(); 
   }
}

//
void itemData::saveSet(const float value) 
{ 
   if(!equals(value))
   {
      save(value); 
      setNewData(); 
   }
}

//
bool itemData::equals(const char value)
{
   if(_type == BYTE)
      return (_data[0] & 0xFF) == value;
   return false;
}

//
bool itemData::equals(const uint8_t value)
{
   if(_type == BYTE)
      return _data[0] == value;
   return false;
}

//
bool itemData::equals(const int16_t value)
{
   if(_type == WORD)
      return memcmp(_data, &value, 2) == 0;
   return false;   
}

//
bool itemData::equals(const uint16_t value)
{
   if(_type == WORD)
      return memcmp(_data, &value, 2) == 0;
   return false;   
}

//
bool itemData::equals(const int32_t value)
{
   if(_type == DWORD)
      return memcmp(_data, &value, 4) == 0;
   return false;   
}

//
bool itemData::equals(const uint32_t value)
{
   if(_type == DWORD)
      return memcmp(_data, &value, 4) == 0;
   return false;   
}

//
bool itemData::equals(const float value)
{
   if(_type == FLOAT)
      return memcmp(_data, &value, 4) == 0;
   else
      return false;
}

