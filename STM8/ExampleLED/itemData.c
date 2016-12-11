//---------------------------------------------------------------------------
//
// Name:        itemData.c
// Author:      Vita Tucek
// Created:     28.11.2016
// License:     MIT
// Description: Class for holding data
//
//---------------------------------------------------------------------------

#include "itemData.h"
#include "crc8.h"

///
void itemData_init(itemData *item, int address, itemType_t type, void (*pFce)(itemData*))
{
  item->address = address;
  item->type = type;
  item->nda = false;
  item->pExecFunction = pFce;

  if(item->type == BYTE)
  {
    item->data = (char*)malloc(1); 
    item->datalen = 1;
  }
  else if(item->type == WORD)
  {
    item->data = (char*)malloc(2);  
    item->datalen = 2;
  }    
  else if(item->type == DWORD || item->type == FLOAT || item->type == RGB || item->type == RGBW || item->type == HSB)
  {
    item->data = (char*)malloc(4);      
    item->datalen = 4;
  }    
  else
    item->data = NULL;
}

///
bool itemData_saveRecvByte(itemData *item, const char* data)
{
   if(item->type != BYTE)
     return false;

   item->data[0] = data[0];
  
   if(item->pExecFunction != NULL) 		
	    (*(item->pExecFunction))(item);

   return true;
}

//
bool itemData_saveRecvWord(itemData *item, const char* data)
{
  if(item->type != WORD)
    return false;
      
  item->data[0] = data[0];
  item->data[1] = data[1];

   if(item->pExecFunction != NULL) 		
	    (*(item->pExecFunction))(item);
  
  return true;
}

//
bool itemData_saveRecvDword(itemData *item, const char* data)
{
  if(item->type == DWORD || item->type == FLOAT || item->type == RGB || item->type == RGBW || item->type == HSB)
  {      
      item->data[0] = data[0];
      item->data[1] = data[1];
      item->data[2] = data[2];
      item->data[3] = data[3];

      if(item->pExecFunction != NULL) 		
	       (*(item->pExecFunction))(item);
      
      return true;
  }
  else
    return false;
}

//
bool itemData_saveRecvArray(itemData *item, const char *pData, const int len)
{
	int i;
	
  if(item->type == ARRAY)
  {
    if(item->datalen < len)
      return false;

    for(i=0;i<len;i++)
    {
      item->data[i] = pData[i];
    }

    if(item->pExecFunction != NULL) 		
	    (*(item->pExecFunction))(item);

    return true;
  }
  else
    return false;
}

//    
void itemData_saveI8(itemData *item, const char value) 
{ 
   itemData_saveU8(item, (uint8_t)(value & 0xFF));
}

//    
void itemData_saveU8(itemData *item, const uint8_t value) 
{ 
   if(item->type == BYTE) item->data[0] = value; 
   else if(item->type == WORD) { item->data[0] = value; item->data[1] = 0; } 
   else if(item->type == DWORD) { item->data[0] = value; item->data[1] = 0; item->data[2] = 0; item->data[3] = 0; } 
}    

//   
void itemData_saveI16(itemData *item, const int16_t value) 
{ 
   itemData_saveU16(item, (uint16_t)(value & 0xFFFF));
}

//   
void itemData_saveU16(itemData *item, const uint16_t value) 
{ 
   if(item->type == BYTE) item->data[0] = (uint8_t)value; 
   else if(item->type == WORD) { char *pData = (char*)&value; item->data[0] = pData[1]; item->data[1] = pData[0]; } 
   else if(item->type == DWORD) { char *pData = (char*)&value; item->data[0] = pData[1]; item->data[1] = pData[0]; item->data[2] = 0; item->data[3] = 0;} 
}

//   
void itemData_saveI32(itemData *item, const int32_t value) 
{ 
   itemData_saveU32(item, (uint32_t)(value & 0xFFFFFFFF));
}

//
void itemData_saveU32(itemData *item, const uint32_t value) 
{ 
   if(item->type == BYTE) 
      item->data[0] = (uint8_t)value; 
   else if(item->type == WORD) 
   { 
      uint16_t tmp = (uint16_t)value; 
      char *pData = (char*)&tmp; 
      item->data[0] = pData[1]; 
      item->data[1] = pData[0]; 
   } 
   else if(item->type == DWORD) 
   { 
      char *pData = (char*)&value; 
      item->data[0] = pData[3]; 
      item->data[1] = pData[2]; 
      item->data[2] = pData[1]; 
      item->data[3] = pData[0]; 
   } 
}

//
void itemData_saveF(itemData *item, const float value) 
{ 
   if(item->type == FLOAT) 
   {
      char *pData = (char*)&value; 
      item->data[0] = pData[3]; 
      item->data[1] = pData[2]; 
      item->data[2] = pData[1]; 
      item->data[3] = pData[0];       
   }
}

//save data only when different and set "new data" flag - for transceive data
void itemData_saveSetI8(itemData *item, const char value) 
{ 
   if(!itemData_equalsI8(item, value))
   {
      itemData_saveI8(item, value);       
      item->nda = true;
   }
}

//
void itemData_saveSetU8(itemData *item, const uint8_t value) 
{ 
   if(!itemData_equalsU8(item, value))
   {
      itemData_saveU8(item, value); 
      item->nda = true;
   }
}

//
void itemData_saveSetI16(itemData *item, const int16_t value) 
{ 
   if(!itemData_equalsI16(item, value))
   {
      itemData_saveI16(item, value); 
      item->nda = true;
   }
}

//
void itemData_saveSetU16(itemData *item, const uint16_t value) 
{ 
   if(!itemData_equalsU16(item, value))
   {
      itemData_saveU16(item, value); 
      item->nda = true; 
   }
}

//
void itemData_saveSetI32(itemData *item, const int32_t value) 
{ 
   if(!itemData_equalsI32(item, value))
   {
      itemData_saveI32(item, value); 
      item->nda = true; 
   }
}

//
void itemData_saveSetU32(itemData *item, const uint32_t value) 
{ 
   if(!itemData_equalsU32(item, value))
   {
      itemData_saveU32(item, value); 
      item->nda = true;
   }
}

//
void itemData_saveSetF(itemData *item, const float value) 
{ 
   if(!itemData_equalsF(item, value))
   {
      itemData_saveF(item, value); 
      item->nda = true;
   }
}

//
bool itemData_equalsI8(itemData *item, const char value)
{
   if(item->type == BYTE)
      return ((item->data[0] == value) ? true : false);
   return false;
}

//
bool itemData_equalsU8(itemData *item, const uint8_t value)
{
   if(item->type == BYTE)
      return item->data[0] == value;
   return false;
}

// compare memory areas in reverse (begin of first area with end of second, ...)
int revmemcmp(void *d1, void *d2, int length)
{
   int i = 0;
   
   for(i=0;i<length;i++)
   {
      if(((char*)d1)[i]!=((char*)d2)[length-1-i])
         return 1;
   }   
   return 0;
}

//
bool itemData_equalsI16(itemData *item, const int16_t value)
{
   if(item->type == WORD)
      return revmemcmp(item->data, &value, 2) == 0;
   return false;   
}

//
bool itemData_equalsU16(itemData *item, const uint16_t value)
{
   if(item->type == WORD)
      return revmemcmp(item->data, &value, 2) == 0;
   return false;   
}

//
bool itemData_equalsI32(itemData *item, const int32_t value)
{
   if(item->type == DWORD)
      return revmemcmp(item->data, &value, 4) == 0;
   return false;   
}

//
bool itemData_equalsU32(itemData *item, const uint32_t value)
{
   if(item->type == DWORD)
      return revmemcmp(item->data, &value, 4) == 0;
   return false;   
}

//
bool itemData_equalsF(itemData *item, const float value)
{
   if(item->type == FLOAT)
      return revmemcmp(item->data, &value, 4) == 0;
   else
      return false;
}

// copy item address into data buffer
void itemData_addressToMemory(itemData *item, char *data) 
{ 
   char *pSrc = (char*)&(item->address);
   data[0] = pSrc[1];
   data[1] = pSrc[0];   
}

// read item data and reset "new data" flag
char* itemData_readNewData(itemData *item) 
{ 
   item->nda = false; 
   return item->data; 
} 

// copy item data into buffer and reset "new data" flag 
void itemData_readNewDataToMemory(itemData *item, char *data) 
{ 
   item->nda = false; 
   memcpy(data,item->data,item->datalen); 
}

// copy data length into data buffer
void itemData_dataLengthToMemory(itemData *item, char *data) 
{ 
   char *pSrc = (char*)&(item->datalen);
   data[0] = pSrc[1];
   data[1] = pSrc[0];     
}

int8_t itemData_getDataAsI8(itemData *item)
{
   return item->data[0];
}

uint8_t itemData_getDataAsU8(itemData *item)
{
   return (uint8_t)item->data[0];
}

int16_t itemData_getDataAsI16(itemData *item)
{
   return (int16_t)(item->data[0] | item->data[1] << 8);
}

uint16_t itemData_getDataAsU16(itemData *item)
{
   return (uint16_t)(item->data[0] | item->data[1] << 8);
}

int32_t itemData_getDataAsI32(itemData *item)
{
   int32_t x;
   
   char *p = (char *)&x;
   p[0] = item->data[3];
   p[1] = item->data[2];
   p[2] = item->data[1];
   p[3] = item->data[0];
     
   return x; 
}

uint32_t itemData_getDataAsU32(itemData *item)
{
   uint32_t x;
   
   char *p = (char *)&x;
   p[0] = item->data[3];
   p[1] = item->data[2];
   p[2] = item->data[1];
   p[3] = item->data[0];
     
   return x;  
}

float itemData_getDataAsF(itemData *item)
{
   float f;
   
   char *p = (char *)&f;
   p[0] = item->data[3];
   p[1] = item->data[2];
   p[2] = item->data[1];
   p[3] = item->data[0];
     
   return f;
}