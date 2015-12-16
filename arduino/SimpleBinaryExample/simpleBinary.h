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
    // Set pin number to use as RTS signal
    void enableRTS(int pinNumber);    
    // Set delay between receive and send message (in ms) for stabilization of communication line 
    void setSendDelay(unsigned int delayms);

  private:
     //device address on line (RS485 address)
     int _uartAddress; 
     //items count
     int _size;
     //data
     itemData *_data;
     //buffer for incoming serial data   
     char serbuf[64];
     //length of buffer
     int serbuflen;
     //flag that RTS handling is enabled
     bool RTSenabled = false;
     //RTS pin number 
     int RTSpin;
     //delay time between ask and answer 
     unsigned int sendDelay = 0;
     //milisecond counter with receive data time
     unsigned long receiveTime = 0;


     //send data saved in item
     void sendData(itemData *item);
     //send to master NoData packet
     void sendNoData();
     //send to master ErrorDuringSave packet
     void sendSavingError();
     //send to master InvalidAddress packet
     void sendInvalidAddress();
     //send to master UnknownData packet
     void sendUnknownData();
     //send to master WrongCRC packet (calculated CRC included)
     void sendWrongData(byte crc);
     //send to master RecivedDataAreOk packet
     void sendOK();
     //check if available new data to send
     void checkNewData();
     //write data into UART
     void write(char* data, int length);

}; 

#endif


