## Example openHAB Arduino serial binding implementation

This example shows Arduino Nano blinking LED controled through SimpleBinary serial port communication. LED state is reported back into openHAB.

In source basic defines need to be set correctly:

    //LED pin
    #define LED_PIN 13
    // UART
    #define UART_SPEED  9600
    #define UART_ADDRESS 1
    
Uart speed depends on binding port configuration *simplebinary:port=COM15:9600;onchange*.

Part of the example are openHAB configuration files for items and sitemap.
