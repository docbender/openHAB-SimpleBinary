//---------------------------------------------------------------------------
//
// Name:        simpleBinaryWebEsp8266.cpp
// Author:      Vita Tucek
// Created:     10.4.2017
// Modified:    4.5.2017
// License:     MIT
// Description: Web server for SimpleBinary
//
//---------------------------------------------------------------------------
#ifdef ESP8266
#include "simpleBinaryWebEsp8266.h"

//construct html page 
String simpleBinaryWebEsp8266::page()
{   
   String content = F("<html>");
   if(json!=NULL && json.length() > 0){
      content += F("<head><script type = 'text/javascript'>"
                     "function refresh() {"
                       "var req = new XMLHttpRequest();"
                       "req.onreadystatechange = function() { "
                           "if(req.readyState == 4 && req.status == 200){"
                              "console.log(req.responseText);"
                              "var resp = JSON.parse(req.responseText);"
                              "fillTableBody(resp);"
                           "}"
                       "}; "
                       "req.open('GET', 'data.json', true);"
                       "req.send(null);"                       
                     "} "
                     "function fillTableBody(data) {"
                       "if(data.length == 0)"
                           "return;"
                       "var table = document.getElementById('tb');"
                       "var i;"
                       "var tr;"
                       "var tc;"
                       "for (var key in data){"
                           "tr = table.insertRow(-1);"
                           "tc = tr.insertCell(-1);"
                           "tc.innerText = key + ':';"
                           "tc.style.textAlign = 'right';"
                           "tc.style.paddingRight = '20px';"
                           "tc.style.width = '20%';"
                           "tc = tr.insertCell(-1);"
                           "tc.innerText = data[key];"
                       "}"                       
                     "} "          
                     "function init(){"
                        "refresh();"
                     "}"         
		            "</script>"
	            "</head><body onload = 'init()'");
   }else{
      content += F("<body");
   }
   content += F(" bgcolor='#393939' text='white'><div style='text-align: center'><h2>SimpleBinary web control</h2><hr><br>"
                "<div style='display: inline-block; text-align: left'>"
                "<form action='/address'><table id='tb'><tbody>"
                "<tr><td style='text-align: right; width: 20%; padding-right: 20;'>Device address:<td><input type='number' name='ADDRESS' value='");
   content += address;
   content += F("'><td style='width: 10%;'><input type='submit' name='SAVE' value='Save'><td style='width: 30%;'/>");      
      
   if(saveResult==1){
      content += F("Address saved");
   }else if (saveResult==2){
      content += F("Address range is 0-255");
   }
   content += F("</tbody></table></form></div></div></body></html>");
   
   return content;
}
 
//constructor
simpleBinaryWebEsp8266::simpleBinaryWebEsp8266(){
   saveResult = 0;  
}

//web server initialization
void simpleBinaryWebEsp8266::begin(int currentAddress, UpdateEventHandler onAddressUpdated, EventHandler onPageLoading){
   //home page
   server.on("/", HTTP_GET,[&]() {    
      if(pageLoad!=NULL)
         pageLoad();     
            
      server.send(200, F("text/html"), page());      
      saveResult = 0;
   }); 
   //save new device address
   server.on("/address", HTTP_GET,[&]() { 
      int newAddress = server.arg("ADDRESS").toInt();  

      //address out-of-range
      if(newAddress < 0 || newAddress > 255){
         saveResult = 2;
      }else{   
         address = newAddress;
         saveResult = 1;
         //call address update event
         if(addressUpdate!=NULL)
            addressUpdate(address);
      }
      //redirection
      server.sendHeader("Location", String("/"), true);
      server.send(302, "text/plain", "");
   });
   //return json file
   server.on("/data.json", HTTP_GET,[&]() {       
      server.send(200, F("application/json"), json);
   });   
   //file not found
   server.onNotFound([&](){
      server.send(404, F("text/html"), F("<html><body><center><h2>404 FileNotFound</h2></center></body></html>"));
   });
   
   //set address
   address = currentAddress;
   //set event handlers
   addressUpdate = onAddressUpdated;
   pageLoad = onPageLoading;
   //start webserver
   server.begin();
}

//web server initialization
void simpleBinaryWebEsp8266::begin(int currentAddress, UpdateEventHandler onAddressUpdated){
   begin(currentAddress,onAddressUpdated,NULL);
}

//web server handle procedure (should be called periodically)
void simpleBinaryWebEsp8266::handleClient(void){
   server.handleClient();
}

//set actual device address to be available to display
void simpleBinaryWebEsp8266::setAddress(int currentAddress){
   address = currentAddress;
}

//create json file from key/value pairs
String simpleBinaryWebEsp8266::makeJson(std::pair<const char*, const char*> values[], int length){
   String text = "{";
   if(length==0){
      text += " }";
      return text;
   }
   
   for(int i=0;i<length;i++){
      text += "\"";
      text += values[i].first;
      text += "\":\"";
      text += values[i].second;
      text += "\"";
      if(i<length-1)
         text += ",";
   }   
   text += " }";
   return text;
}
#endif