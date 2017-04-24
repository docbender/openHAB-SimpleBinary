//---------------------------------------------------------------------------
//
// Name:        simpleBinaryWebEsp8266.h
// Author:      Vita Tucek
// Created:     10.4.2017
// Modified:    
// License:     MIT
// Description: Web server for SimpleBinary
//
//---------------------------------------------------------------------------

#ifndef _SIMPLEBINARY_WEB_ESP8266_H_
#define _SIMPLEBINARY_WEB_ESP8266_H_

#include <ESP8266WebServer.h>

class simpleBinaryWebEsp8266
{
   public:
      typedef std::function<void(int)> UpdateEventHandler;
      typedef std::function<void()> EventHandler;
      
      //constructor
      simpleBinaryWebEsp8266(); 
      //web server initialization 
      void begin(int currentAddress, UpdateEventHandler onAddressUpdated);
      //web server initialization
      void begin(int currentAddress, UpdateEventHandler onAddressUpdated, EventHandler onPageLoading);
      //web server handle procedure (should be called periodically)
      void handleClient(void);
      //set actual device address to be available to display
      void setAddress(int currentAddress); 
      //create json file from key/value pairs
      static String makeJson(std::pair<const char*, const char*> values[], int length);
      //json with table values
      String json;
      
   private:
      //web server
      ESP8266WebServer server;
      //stored device address
      int address;
      //event 'address was updated from web'
      UpdateEventHandler addressUpdate;
      //event 'page loading'
      EventHandler pageLoad;
      //construct html page 
      String page(void); 
      //save address result - 0: unknown, 1: ok, 2: out-of-range
      int saveResult;
};

#endif