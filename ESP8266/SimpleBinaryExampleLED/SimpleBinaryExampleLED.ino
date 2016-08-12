//---------------------------------------------------------------------------
//
// Name:        SimpleBinaryExampleLED.ino
// Author:      Vita Tucek
// Created:     9.8.2016
// License:     MIT
// Description: Blinking LED on ESP8266 example using SimpleBinary communication 
//              with OpenHAB binding. LED state is reported back into openHAB
//
//---------------------------------------------------------------------------

#include <ESP8266WiFi.h>
#include "simpleBinary.h"
#include "crc8.h"

//---- Need to be setup correctly ----
///Wifi AP SSID
#define WIFI_SSID "SSID"
///Wifi AP password
#define WIFI_PWD  "password"
///openHAB server IP address
#define SERVER_IP "192.168.0.101"
///openHAB binding configured port (default is 43243)
#define SERVER_PORT 43243

#define CLIENT_ID 1

#define LED_PIN LED_BUILTIN

volatile bool wifiConnected = false;
bool wifiConnectedMem = false;
WiFiClient client;
uint32_t lastTry = 0;
bool connectWait = false;                    

//definice promennych vymeny dat
simpleBinary *items;
itemData *pLEDCommandItem,*pLEDStatusItem;


//---------------------------------------------------------------------------------------------------------
void setup() {   
   pinMode(LED_PIN, OUTPUT);

   items = new simpleBinary(CLIENT_ID, 2, client);   
   //initialize first item with address 1, data type BYTE (represent openHAB switch) 
   //and pointer to function that is provide on state change
   pLEDCommandItem = items->initItem(0,1,BYTE, executeData);

   //initialize second item with address 2, data type BYTE (represent openHAB contact) 
   //no action executed because no incoming data expected
   pLEDStatusItem = items->initItem(1,2,BYTE);

   pLEDStatusItem->saveSet(1);  
   
   
   // delete old config
   WiFi.disconnect(true);

   delay(1000);

   WiFi.onEvent(WiFiEvent);

   WiFi.begin(WIFI_SSID, WIFI_PWD);
}

//---------------------------------------------------------------------------------------------------------
void loop() {
   //wifi
   //connect
   if(wifiConnected && !client.connected() && (!connectWait || (millis() - lastTry > 5000)))
   {
      if (!client.connect(SERVER_IP, SERVER_PORT)) 
      {
         lastTry = millis();
         connectWait = true;
         
         //connection failed"
         //now wait 5 sec...
      }
      else
      {
         connectWait = false;
         //send "Hi" message to server
         items->sendHi();
         //on connect mark data to send
         pLEDStatusItem->setNewData();         
      }
   }

   //data on wifi available
   if(client.available())
   {
      //process them
      items->processSerial();
   }
   
   //connected and new data ready to send
   if(items->available() && client.connected())
   {
      items->sendNewData();
   }
}

//-------------------------------------------------------------------------------------------------
void WiFiEvent(WiFiEvent_t event) {

   switch(event) {
      case WIFI_EVENT_STAMODE_GOT_IP:
         wifiConnected = true;
         
         break;
      case WIFI_EVENT_STAMODE_DISCONNECTED:
         wifiConnected = false;
         
         //WiFi lost connection
         break;
   }
}

//---------------------------------------------------------------------------------------------------------
//function resend data to another item
void executeData(itemData *item)
{
   //check correct address - not necessary if only one item is paired with function
   if(item->getAddress() == 1)
   {
      //read item data 
      bool state = (item->getData()[0] == 0);
      //turn on/off LED depend on receive data
      digitalWrite(LED_PIN,state);
      
      //report current status back
      pLEDStatusItem->saveSet(state ? 0 : 1);      
   } 
}
