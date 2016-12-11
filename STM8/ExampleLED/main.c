//---------------------------------------------------------------------------
//
// Name:        main.c
// Author:      Vita Tucek
// Created:     11.12.2016
// License:     MIT
// Description: SimpleBinary LED example
//
//---------------------------------------------------------------------------

#include <stm8s.h>

#include "basic_time_functions.h"
#include "serial.h"
#include "simplebinary.h"

#define LED_PIN GPIO_PIN_3

//variables definitions
simpleBinary sb;
itemData *i0, *i1;

//-------------------------------- force items values for read -----------------
void forceSbItems(simpleBinary* object)
{
   //mark output data as new
   i1->nda = true;
}

//-------------------------------- precess received items ----------------------
void execRead(itemData* item)
{
   if(item==i0)
   {
      itemData_saveSetU8(i1, item->data[0]);
      
      if(item->data[0] == 0)
         GPIO_WriteLow(GPIOD,LED_PIN);
      else
         GPIO_WriteHigh(GPIOD,LED_PIN);
   }     
}

//-------------------------------- main ----------------------------------------
int main()
{
   CLK_DeInit(); 
   CLK_HSECmd(ENABLE);    
	CLK_SYSCLKConfig(CLK_PRESCALER_HSIDIV1);
   //time initialization (delay + millis)
   systime_init();
   //UART initialization
   serial_begin(9600);
	//GPIO initialization
	GPIO_DeInit(GPIOD);
	
   //init LED pin output
	GPIO_Init(GPIOD, LED_PIN, GPIO_MODE_OUT_PP_LOW_SLOW);
   //init RTS pin output
   GPIO_Init(GPIOD, GPIO_PIN_4, GPIO_MODE_OUT_PP_LOW_FAST);
   
   //binding initialization (2 items, device with address 1)
   simpleBinary_init(&sb, 1, 2, forceSbItems);
   //enable RTS control (RS485 flow control)
   simpleBinary_enableRTS(&sb,GPIOD,GPIO_PIN_4);
   //items initialization
   i0 = simpleBinary_initItem(&sb,0,BYTE,execRead);
   i1 = simpleBinary_initItem(&sb,1,BYTE,NULL);
   itemData_saveSetU8(i1, 0);
	
   /* Enable general interrupts */
   enableInterrupts();
	
	while (1)
	{
      //handle UART data control
      simpleBinary_processSerial(&sb);   
	}
}