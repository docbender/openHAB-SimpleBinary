//---------------------------------------------------------------------------
//
// Name:        itemData.cpp
// Author:      Vita Tucek
// Created:     20.8.2015
// Description: Class for holding data
//
//---------------------------------------------------------------------------

#include "Arduino.h"
#include "itemData.h"
#include "crc8.h"

/// constructor
itemData::itemData()
{
  pExecFunction = NULL;
}

/// constructor
itemData::itemData(int address, itemType type, void (*pFce)(itemData*))
{
  init(address, type,pFce);
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
void itemData::init(int address, itemType type, void (*pFce)(itemData*))
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
}

///
void itemData::executeAction()
{
  if(pExecFunction == NULL) 
    return; 
    
  (*pExecFunction)(this);  
}

///
bool itemData::saveByte(char* data)
{
   if(_type != BYTE)
     return false;

   _data[0] = data[0];

   //setNewData();
   executeAction();

   return true;
}

//
bool itemData::saveWord(char* data)
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
bool itemData::saveDword(char* data)
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
bool itemData::saveArray(char *pData, int len)
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



