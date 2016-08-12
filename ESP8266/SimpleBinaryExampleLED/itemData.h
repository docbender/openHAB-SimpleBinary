//---------------------------------------------------------------------------
//
// Name:        itemData.h
// Author:      Vita Tucek
// Created:     20.8.2015
// License:     MIT
// Description: Class for holding data header
// 
//
//---------------------------------------------------------------------------

#ifndef ITEMDATA_H
#define ITEMDATA_H

#include "Arduino.h"

// data type enum
enum itemType
{
  BYTE,
  WORD,
  DWORD,
  FLOAT,
  HSB,
  RGB,
  RGBW,
  ARRAY
};


class itemData
{

     
  public:
    itemData();
    itemData(int address, itemType type);
    itemData(int address, itemType type, void (*pFce)(itemData*) );
    itemData(int address, itemType type, int size, void (*pFce)(itemData*) );

    ~itemData();

    // Item initialization 
    itemData* init(int address, itemType type, void (*pFce)(itemData*));

    // save data and execute action if defined - for save received data
    bool saveByte(const char* data);
    bool saveWord(const char* data);
    bool saveDword(const char* data);
    bool saveArray(const char *pData, int len); 
    // save data
    void save(const char value);
    void save(const uint8_t value);    
    void save(const int16_t value);
    void save(const uint16_t value);    
    void save(const int32_t value);
    void save(const uint32_t value);
    void save(const float value);
    //save data and set "new data" flag (only if data not equal) - for transceive data
    void saveSet(const char value);
    void saveSet(const uint8_t value);    
    void saveSet(const int16_t value);   
    void saveSet(const uint16_t value);   
    void saveSet(const int32_t value);   
    void saveSet(const uint32_t value);       
    void saveSet(const float value);

    // return item address
    int getAddress() const { return _address; };
    // copy item address into data buffer
    void addressToMemory(char *data) const { memcpy(data,&_address,2); };
    // check if item has "new data" flag set 
    bool hasNewData() const { return _nda; };
    // set item "new data" flag 
    void setNewData() { _nda = true; };
    // read item data and reset "new data" flag
    char* readNewData() { _nda = false; return _data; };
    // copy item data into buffer and reset "new data" flag 
    void readNewDataToMemory(char *data) {  _nda = false; memcpy(data,_data,_datalen); };
    // function execute connected action
    void executeAction();
    // return item data type
    const itemType getType() const { return _type; };
    // return length of data
    const int getDataLength() const { return _datalen; };
    // return item data only
    char* getData() const { return _data; };
    // copy data length into data buffer
    void dataLengthToMemory(char *data) const { memcpy(data,&_datalen,2); };

    // pointer to execution function on new data
    void (*pExecFunction)(itemData*);   

  private:
     //address
     int _address;
     //new data available
     bool _nda;  
     //type
     itemType _type;
     //item data pointer
     char *_data;  
     //data length
     int _datalen;   

    bool equals(const char value);
    bool equals(const uint8_t value);    
    bool equals(const int16_t value);   
    bool equals(const uint16_t value);   
    bool equals(const int32_t value);   
    bool equals(const uint32_t value);       
    bool equals(const float value);     

}; 

#endif


