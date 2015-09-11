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
    itemData(int address, itemType type, void (*pFce)(itemData*) );
    itemData(int address, itemType type, int size, void (*pFce)(itemData*) );

    ~itemData();

    // Item initialization 
    void init(int address, itemType type, void (*pFce)(itemData*));

    // save data
    bool saveByte(char* data);
    bool saveWord(char* data);
    bool saveDword(char* data);
    bool saveArray(char *pData, int len); 
    void save(float value) { if(_type == FLOAT) memcpy(_data,&value,4); };

    // return item address
    int getAddress() { return _address; };
    // copy item address into data buffer
    void addressToMemory(char *data) { memcpy(data,&_address,2); };
    // check if item has "new data" flag set 
    bool hasNewData() { return _nda; };
    // set item "new data" flag 
    void setNewData() { _nda = true; };
    // read item data and reset "new data" flag
    char* readNewData() { _nda = false; return _data; };
    // copy item data into buffer and reset "new data" flag 
    void readNewDataToMemory(char *data) {  _nda = false; memcpy(data,_data,_datalen); };
    // function execute connected action
    void executeAction();
    // return item data type
    itemType getType() { return _type; };
    // return length of data
    int getDataLength() { return _datalen; };
    // return item data only
    char* getData() { return _data; };
    // copy data length into data buffer
    void dataLengthToMemory(char *data) { memcpy(data,&_datalen,2); };

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

}; 

#endif


