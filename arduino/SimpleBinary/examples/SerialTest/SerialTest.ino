//---------------------------------------------------------------------------
//
// Name:        SerialTest.ino
// Author:      Vita Tucek
// Created:     14.6.2021
// Modified:    
// License:     MIT
// Description: test simplebinary
//
//---------------------------------------------------------------------------

#include <simpleBinary.h>

// LED output
#define LED_PIN LED_BUILTIN
// ReadyToSend pin - flow control
//#define RTS_PIN 5
// SimpleBinary device address
#define CLIENT_ID 2

// time
uint32_t secondsOfDay = 0, uptime = 0;
uint32_t last_hours = 0, last_minutes = 0, lastTimer = 0;
uint32_t hours = 0, minutes = 0;
uint32_t displayTimeout = 30;

// items definition
simpleBinary *items;
itemData *pFreeRamItem,*pUptimeItem,
   *pTest01Item,*pTest02Item,
   *pTest03Item,*pTest04Item,
   *pTest05Item,*pTest06Item,
   *pTest07Item,*pTest08Item,
   *pTestRgb01Item,*pTestRgb02Item,
   *pTestRgbw01Item,*pTestRgbw02Item,
   *pTestHsb01Item,*pTestHsb02Item,
   *pTestRr01CmdItem,*pTestRr01StaItem;


uint16_t rrCmd = 0;
uint8_t rrPosition = 0;

//-------------------------------------------------------------------------------------------------
static int FreeRam(){
  extern int __heap_start, *__brkval; 
  int v; 
  return (int) &v - (__brkval == 0 ? (int) &__heap_start : (int) __brkval); 
}

//-------------------------------------------------------------------------------------------------
void setup() {
   // initialize serial interface
   Serial.begin(9600); 
   // initialize binding
   items = new simpleBinary(CLIENT_ID, 18, Serial, forceAllItemsAsNew);
   // line stabilisation delay
   items->setSendDelay(100);
   // RAM - state
   pFreeRamItem = items->initItem(0,DWORD); 
   pFreeRamItem->saveSet(FreeRam());
   // uptime - state
   pUptimeItem = items->initItem(1,DWORD);
   pUptimeItem->saveSet(0);
   // test 01
   pTest01Item = items->initItem(2,BYTE,receiveByte);
   pTest01Item->saveSet(0);   
   // test 02
   pTest02Item = items->initItem(3,BYTE);
   pTest02Item->saveSet(0); 
   // test 03
   pTest03Item = items->initItem(4,WORD,receiveWord);
   pTest03Item->saveSet(0);   
   // test 04
   pTest04Item = items->initItem(5,WORD);
   pTest04Item->saveSet(0);   
   // test 05
   pTest05Item = items->initItem(6,DWORD,receiveDword);
   pTest05Item->saveSet(0);   
   // test 06
   pTest06Item = items->initItem(7,DWORD);
   pTest06Item->saveSet(0);  
   // test 07
   pTest07Item = items->initItem(8,FLOAT,receiveFloat);
   pTest07Item->saveSet(0);   
   // test 08
   pTest08Item = items->initItem(9,FLOAT);
   pTest08Item->saveSet(0);  
   // test RGB 1
   pTestRgb01Item = items->initItem(10,RGB,receiveColor);
   pTestRgb01Item->saveSet(0);   
   // test RGB 2
   pTestRgb02Item = items->initItem(11,RGB);
   pTestRgb02Item->saveSet(0); 
   // test RGBW 1
   pTestRgbw01Item = items->initItem(12,RGBW,receiveColor);
   pTestRgbw01Item->saveSet(0);   
   // test RGBW 2
   pTestRgbw02Item = items->initItem(13,RGBW);
   pTestRgbw02Item->saveSet(0); 
   // test HSB 1
   pTestHsb01Item = items->initItem(14,HSB,receiveColor);
   pTestHsb01Item->saveSet(0);   
   // test HSB 2
   pTestHsb02Item = items->initItem(15,HSB);
   pTestHsb02Item->saveSet(0);    
   // test RR CMD
   pTestRr01CmdItem = items->initItem(16,WORD,receiveRoller);
   pTestRr01CmdItem->saveSet(0);   
   // test RR STA
   pTestRr01StaItem = items->initItem(17,WORD);
   pTestRr01StaItem->saveSet(0);   
   
#ifdef LED_PIN
   // LED output
   pinMode(LED_PIN, OUTPUT);
#endif
#ifdef RTS_PIN
   // set RTS pin
   items->enableRTS(RTS_PIN);
#endif   
   // mark all items
   forceAllItemsAsNew(items);
}

int bytes = 0;

//-------------------------------------------------------------------------------------------------
void loop() {  
   // handle binding
   items->handle();
   
   delay(1);
   timers();   
#ifdef LED_PIN
   blinkSlow();
#endif
}

//-------------------------------------------------------------------------------------------------
void timers()
{
   uint32_t now = millis();
   
   //second elapsed
   if(now - lastTimer >= 1000)
   {           
      lastTimer = now;
      
      uptime++;
      secondsOfDay++;
      if(secondsOfDay>=24*3600)
         secondsOfDay = 0;
      
      hours = secondsOfDay / 3600;
      minutes = (secondsOfDay / 60) % 60;

      // 3s elapsed
      if(!(uptime % 3)){
         pUptimeItem->saveSet(uptime);  
         pFreeRamItem->saveSet(FreeRam());
      }   
      
      // rollershutter handling      
      // Up
      if((rrCmd & 0xFF00)==0x400 && rrPosition < 100){
         pTestRr01StaItem->saveSet(++rrPosition);
      // Down   
      }else if((rrCmd & 0xFF00)==0x800 && rrPosition > 0){
         pTestRr01StaItem->saveSet(--rrPosition);
      // Stop   
      }else if((rrCmd & 0xFF00)==0x200){

      }
   }
}

//-------------------------------------------------------------------------------------------------
void receiveByte(itemData *item){
   int adddress = item->getAddress();
   //save data
   (*items)[adddress+1].saveByte(item->getData()); 
   (*items)[adddress+1].setNewData();  
}

//-------------------------------------------------------------------------------------------------
void receiveWord(itemData *item){
   int adddress = item->getAddress();
   //save data
   (*items)[adddress+1].saveWord(item->getData()); 
   (*items)[adddress+1].setNewData(); 
   
  
}

//-------------------------------------------------------------------------------------------------
void receiveDword(itemData *item){
   int adddress = item->getAddress();
   //save data
   (*items)[adddress+1].saveDword(item->getData()); 
   (*items)[adddress+1].setNewData();   
     
}

//-------------------------------------------------------------------------------------------------
void receiveFloat(itemData *item){
   int adddress = item->getAddress();
   //save data
   (*items)[adddress+1].saveDword(item->getData()); 
   (*items)[adddress+1].setNewData();   
   

   float value = *((float*)(item->getData()));
}

//-------------------------------------------------------------------------------------------------
void receiveColor(itemData *item){
   // function to process RGB data
   if(item->getType() == RGB){
      char *data = item->getData();
    
      uint8_t red = data[0];
      uint8_t green = data[1];
      uint8_t blue = data[2];
      uint8_t white = 0;
   }
   
   int adddress = item->getAddress();
   //save data
   (*items)[adddress+1].saveDword(item->getData()); 
   (*items)[adddress+1].setNewData();     
}

//-------------------------------------------------------------------------------------------------
void receiveRoller(itemData *item){
   int adddress = item->getAddress();
   uint16_t value = item->getData()[0] | item->getData()[1]<<8;
   
   rrCmd = value;   
}

//--------------------------------- Force data as new ---------------------------------------------
void forceAllItemsAsNew(simpleBinary *allItems) {  
   //mark first 3 items
   for(int i=0;i<allItems->size();i++)
   {
      (*allItems)[i].setNewData();
   }
}


//----------------------------------- LED turn on -------------------------------------------------
void turnOnLed(){
   digitalWrite(LED_PIN,LOW);
}

//----------------------------------- LED turn off ------------------------------------------------
void turnOffLed(){
   digitalWrite(LED_PIN,HIGH);
}

//----------------------------------- LED slow blinking -------------------------------------------
void blinkSlow(){
   digitalWrite(LED_PIN,secondsOfDay%4<2);
}

//----------------------------------- LED fast blinking -------------------------------------------
void blinkFast(){
   digitalWrite(LED_PIN,secondsOfDay%2);
}
