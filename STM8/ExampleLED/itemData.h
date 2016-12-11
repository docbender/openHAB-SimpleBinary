//---------------------------------------------------------------------------
//
// Name:        itemData.h
// Author:      Vita Tucek
// Created:     28.11.2016
// License:     MIT
// Description: Class for holding data header (C language)
// 
//
//---------------------------------------------------------------------------

#ifndef ITEMDATA_H
#define ITEMDATA_H

#include <stdlib.h>
#include <string.h>
#include <stm8s.h>

#define false 0
#define true 1

typedef unsigned char byte;
/*
typedef char bool;
typedef char int8_t;
typedef unsigned char uint8_t;
typedef short int16_t;
typedef unsigned short uint16_t;
typedef long int32_t;
typedef unsigned long uint32_t;
*/

// data type enum
typedef enum 
{
   BYTE,
   WORD,
   DWORD,
   FLOAT,
   HSB,
   RGB,
   RGBW,
   ARRAY
} itemType_t;  

typedef struct itemData itemData;

struct itemData
{
   //address
   int address;
   //new data available
   bool nda;  
   //type
   itemType_t type;
   //item data pointer
   char *data;  
   //data length
   int datalen; 
   //pointer to execution function on new data
	 void (*pExecFunction)(itemData*);  	 
};
   
 
// Item initialization 
void itemData_init(itemData *item, int address, itemType_t type, void (*pFce)(itemData*));

// save data and execute action if defined - for save received data
bool itemData_saveRecvByte(itemData *item, const char* data);
bool itemData_saveRecvWord(itemData *item, const char* data);
bool itemData_saveRecvDword(itemData *item, const char* data);
bool itemData_saveRecvArray(itemData *item, const char *pData, int len); 

// save data
void itemData_saveI8(itemData *item, const char value);
void itemData_saveU8(itemData *item, const uint8_t value);    
void itemData_saveI16(itemData *item, const int16_t value);
void itemData_saveU16(itemData *item, const uint16_t value);    
void itemData_saveI32(itemData *item, const int32_t value);
void itemData_saveU32(itemData *item, const uint32_t value);
void itemData_saveF(itemData *item, const float value);
//save data and set "new data" flag (only if data not equal) - for transceive data
void itemData_saveSetI8(itemData *item, const char value);
void itemData_saveSetU8(itemData *item, const uint8_t value);    
void itemData_saveSetI16(itemData *item, const int16_t value);   
void itemData_saveSetU16(itemData *item, const uint16_t value);   
void itemData_saveSetI32(itemData *item, const int32_t value);   
void itemData_saveSetU32(itemData *item, const uint32_t value);       
void itemData_saveSetF(itemData *item, const float value);

// copy item address into data buffer
void itemData_addressToMemory(itemData *item, char *data);
// read item data and reset "new data" flag
char* itemData_readNewData(itemData *item) ;
// copy item data into buffer and reset "new data" flag 
void itemData_readNewDataToMemory(itemData *item, char *data);
// copy data length into data buffer
void itemData_dataLengthToMemory(itemData *item, char *data);

// compare memory areas in reverse (begin of first area with end of second, ...)
int revmemcmp(void *d1, void *d2, int length);

// compare new and old item value (type depended)
bool itemData_equalsI8(itemData *item, const char value);
bool itemData_equalsU8(itemData *item, const uint8_t value);    
bool itemData_equalsI16(itemData *item, const int16_t value);   
bool itemData_equalsU16(itemData *item, const uint16_t value);   
bool itemData_equalsI32(itemData *item, const int32_t value);   
bool itemData_equalsU32(itemData *item, const uint32_t value);       
bool itemData_equalsF(itemData *item, const float value);     

// get variable from item data buffer(type depended)
// Function aren't type safe. Only right function (depend at item type)can be used
int8_t itemData_getDataAsI8(itemData *item);
uint8_t itemData_getDataAsU8(itemData *item);
int16_t itemData_getDataAsI16(itemData *item);
uint16_t itemData_getDataAsU16(itemData *item);
int32_t itemData_getDataAsI32(itemData *item);
uint32_t itemData_getDataAsU32(itemData *item);
float itemData_getDataAsF(itemData *item);

#endif


