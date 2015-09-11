//---------------------------------------------------------------------------
//
// Name:        simpleBinary.cpp
// Author:      Vita Tucek
// Created:     11.9.2015
// Description: Usage example of SimpleBinary communication with OpenHAB binding
//
//---------------------------------------------------------------------------

#include "Arduino.h"

#include "simpleBinary.h"


// UART
#define UART_SPEED  9600
#define UART_ADDRESS 1

//definice promennych vymeny dat
simpleBinary *items;

void setup() {
  
  //items initialization
  items = new simpleBinary(UART_ADDRESS,11);

  //rgb with connection to executeRGB function
  items->initItem(0,100,RGB, executeRGB);
  //write only
  items->initItem(1,101,FLOAT, NULL);
  //write only
  items->initItem(2,102,FLOAT, NULL);
  //other items with connection to executeData function
  items->initItem(3,1,BYTE, executeData);
  items->initItem(4,2,WORD, executeData);  
  items->initItem(5,11,BYTE, executeData);
  items->initItem(6,12,WORD, executeData);
  items->initItem(7,3,DWORD, executeData);
  items->initItem(8,13,DWORD, executeData);
  items->initItem(9,4,FLOAT, executeData);
  items->initItem(10,14,FLOAT, executeData);  

  //UART inicialization
  Serial.begin(UART_SPEED);
  UCSR0B |= 0x80;
}

void loop() 
{  
  // check if data has been sent from the master computer
  if (Serial.available()) 
  {
    //process them
    items->processSerial();
  }
  else
  {    
    delay(100);
  }
}

//function resend data to another item
void executeData(itemData *item)
{
  if(item->getAddress() == 1)
  {
    //save data
    (*items)[5].saveByte(item->getData());
    //mark item that hold new data
    (*items)[5].setNewData();
  }
  else if(item->getAddress() == 2)
  {
    (*items)[6].saveWord(item->getData());
    (*items)[6].setNewData();
  }
  else if(item->getAddress() == 3)
  {
    (*items)[8].saveDword(item->getData());
    (*items)[8].setNewData();
  }
  else if(item->getAddress() == 4)
  {
    (*items)[10].saveDword(item->getData());
    (*items)[10].setNewData();
  } 
}

//function to process RGB data
void executeRGB(itemData *item)
{
  if(item->getType() == RGB)
  {
    char *data = item->getData();
    
    uint8_t red = data[0];
    uint8_t green = data[1];
    uint8_t blue = data[2];
    uint8_t white = 0;

    //now send data to lights
    //DmxSimple.write(1, red);
    //DmxSimple.write(2, green);
    //DmxSimple.write(3, blue);
    //DmxSimple.write(4, white);
  }
}
