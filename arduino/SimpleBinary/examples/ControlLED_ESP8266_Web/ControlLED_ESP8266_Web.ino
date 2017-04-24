//---------------------------------------------------------------------------
//
// Name:        ControlLED_ESP8266_Web.ino
// Author:      Vita Tucek
// Created:     24.4.2017
// Modified:    
// License:     MIT
// Description: Blinking LED on ESP8266 example using SimpleBinary TCP communication 
//              with OpenHAB binding. LED state is reported back into openHAB.
//              Web interface is for user-friendly setting device address 
//              and for display custom information
//
//---------------------------------------------------------------------------

#include <ESP8266WiFi.h>
#include <simpleBinary.h>
#include <simpleBinaryWebEsp8266.h>
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

//binding definition - device address, items count, device stream
simpleBinary items(CLIENT_ID, 2, client);
//configured items
itemData *pLEDCommandItem,*pLEDStatusItem;
//web interface instance
simpleBinaryWebEsp8266 web;

//---------------------------------------------------------------------------------------------------------
void setup() {   
   pinMode(LED_PIN, OUTPUT);
 
   //initialize first item with address 0, data type BYTE (represent openHAB switch) 
   //and pointer to function that is provide on state change
   pLEDCommandItem = items.initItem(0,BYTE, executeData);

   //initialize second item with address 1, data type BYTE (represent openHAB contact) 
   //no action executed because no incoming data expected
   pLEDStatusItem = items.initItem(1,BYTE);

   pLEDStatusItem->saveSet(1);     
   
   // delete old config
   WiFi.disconnect(true);

   delay(1000);

   WiFi.onEvent(WiFiEvent);

   WiFi.begin(WIFI_SSID, WIFI_PWD);
   
   //web interface initialization
   web.begin(CLIENT_ID,[](int address){ 
      //event react on successful address save from web interface
      //saved address is passed by 'address' parameter
      
      //set device for operation with new address
      items.setDeviceAddress(address);
      },[](){
         //event is call before web request is provided
         //custom key/value pair can be passed as JSON formatted string into 
         //'web.json' variable. These pairs are displayed in html output
         String ram = String(ESP.getFreeHeap());
         String chid = String(ESP.getChipId());
         String fchid = String(ESP.getFlashChipId());
         String fchs = String(ESP.getFlashChipSize());
         String fchsp = String(ESP.getFlashChipSpeed());  
         String rr = ESP.getResetReason();
         
         std::pair<const char*, const char*> values[] = {            
            std::make_pair("Free RAM",ram.c_str()),
            std::make_pair("Chip ID",chid.c_str()),
            std::make_pair("Flash chip ID",fchid.c_str()),
            std::make_pair("Flash chip size",fchs.c_str()),
            std::make_pair("Flash chip speed",fchsp.c_str()),
            std::make_pair("Last reset reason",rr.c_str())
         };
         //create JSON from passed values
         web.json = simpleBinaryWebEsp8266::makeJson(values,6);
      });   
}

//---------------------------------------------------------------------------------------------------------
void loop() {
   //handle web requests periodically
   web.handleClient();
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
         items.sendHi();
         //on connect mark data to send
         pLEDStatusItem->setNewData();         
      }
   }

   //data on wifi available
   if(client.available())
   {
      //process them
      items.processSerial();
      yield();
   }
   
   //connected and new data ready to send
   if(items.available() && client.connected())
   {
      items.sendNewData();
   }
   
   delay(1);
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
   if(item->getAddress() == 0)
   {
      //read item data 
      bool state = (item->getData()[0] == 0);
      //turn on/off LED depend on receive data
      digitalWrite(LED_PIN,state);
      
      //report current status back
      pLEDStatusItem->saveSet(state ? 0 : 1);      
   } 
}
