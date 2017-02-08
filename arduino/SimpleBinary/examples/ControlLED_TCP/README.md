## Example openHAB ESP8266 TCP binding implementation

This example shows blinking LED on ESP8266 using SimpleBinary TCP communication with OpenHAB binding. LED state is reported back into openHAB.

In source basic defines need to be set correctly:

    ///Wifi AP SSID
    #define WIFI_SSID "SSID"
    ///Wifi AP password
    #define WIFI_PWD  "password"
    ///openHAB server IP address
    #define SERVER_IP "192.168.0.101"
    ///openHAB binding configured port (default is 43243)
    #define SERVER_PORT 43243
    //client ID
    #define CLIENT_ID 1
    //controlled LED pin number (default is bultin LED pin)
    #define LED_PIN LED_BUILTIN
    
Server port must equal to binding configuration parameter *simplebinary:tcpserver=43243*.

Part of the example are openHAB configuration files for items and sitemap.
