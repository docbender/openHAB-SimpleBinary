//---------------------------------------------------------------------------
//
// Name:        ControlLED_Serial.cpp
// Author:      Vita Tucek
// Created:     6.2.2016
// Modified:    30.1.2017
// License:     MIT
// Description: Blinking LED on Arduino Nano example using SimpleBinary serial 
//              communication with OpenHAB binding. 
//              LED state is reported back into openHAB
//
//---------------------------------------------------------------------------

#include "Arduino.h"

#include "simpleBinary.h"

//LED pin
#define LED_PIN 13
// UART
#define UART_SPEED  9600
#define UART_ADDRESS 1

//library definition - set device(UART) address, items count and device(Serial) stream
simpleBinary items(UART_ADDRESS, 2, Serial);

void setup() {
   //UART inicialization
   Serial.begin(UART_SPEED);
   //UART RX Complete Interrupt Enable
   UCSR0B |= 0x80;

   //initialize first item with address 1, data type BYTE (represent openHAB switch) 
   //and pointer to function that is provide on state change
   items.initItem(0,BYTE, executeData);

   //initialize second item with address 2, data type BYTE (represent openHAB contact) 
   //no action executed because no incoming data expected
   items.initItem(1,BYTE);
}

void loop() 
{  
   // check if data has been sent from the master computer
   if (Serial.available()) 
   {
      //process them
      items.processSerial();
   }
   else
   {    
      delay(100);
   }
}

//action function
void executeData(itemData *item)
{
   //check correct address - not necessary if only one item is paired with function
   if(item->getAddress() == 1)
   {
      //read item data 
      bool state = (item->getData()[0] == 1);
      //turn on/off LED depend on receive data
      digitalWrite(LED_PIN,state);
      
      //report current status back
      items[1].save(state ? 1 : 0);
      //mark item has new data that have to be sent
      items[1].setNewData();
      //both above functions ("save" and "setNewData") can be replaced by one "saveSet" function
   }
}