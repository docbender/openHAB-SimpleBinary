#ifndef SIMPLEBINARY_H
#define SIMPLEBINARY_H

#include "itemData.h"


class simpleBinary
{     
  public:
    simpleBinary() {}; 
    simpleBinary(int uartAddress, int size) {  serbuflen = 0; _uartAddress = uartAddress; _size = size; _data = new itemData[size]; };    

    ~simpleBinary() { delete[] _data; };
    
    const itemData& operator[](const int idx) const { return _data[idx]; }; 
    itemData& operator[](int idx) { return _data[idx]; };

    //Item initialization 
    void initItem(int idx, int address, itemType type, void (*pFce)(itemData*));
    // check if address exist in items array
    bool checkAddress(int address);
    // Read and send data on given address
    void readData(int address);
    // Save byte to item by address
    bool saveByte(int address, char* data);
    // Save word to item by address
    bool saveWord(int address, char* data);
    // Save dword to item by address
    bool saveDword(int address, char* data);
    // Save array to item by address
    bool saveArray(int address, char *pData, int len);
    // Return item count
    int size() { return _size; };
    // Process data received by UART 
    void processSerial();

  private:
     //device address on line (RS485 address)
     int _uartAddress; 
     //items count
     int _size;
     //data
     itemData *_data;
     //buffer for incoming serial data   
     char serbuf[64];
     int serbuflen;


     void sendData(itemData *item);
     void sendNoData();
     void sendSavingError();
     void sendInvalidAddress();
     void sendUnknownData();
     void sendWrongData(byte crc);
     void sendOK();
     void checkNewData();

}; 

#endif


