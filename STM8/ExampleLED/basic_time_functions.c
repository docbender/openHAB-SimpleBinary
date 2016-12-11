//---------------------------------------------------------------------------
//
// Name:        basic_time_functions.c
// Author:      Vita Tucek
// Created:     29.11.2016
// License:     MIT
// Description: Time
// 
//
//---------------------------------------------------------------------------

#include "basic_time_functions.h"

volatile uint32_t millisCounter; // milliseconds counter

void systime_init(void)
{
   millisCounter = 0;
   // Timer enable
   CLK_PeripheralClockConfig(CLK_PERIPHERAL_TIMER4 , ENABLE); 
   // Timer initialiyation
   TIM4_DeInit(); 
   // Time base configuration - F_CPU/128
   TIM4_TimeBaseInit(TIM4_PRESCALER_128, TIM4_PERIOD);
   // Clear TIM4 update flag 
   TIM4_ClearFlag(TIM4_FLAG_UPDATE);
   // Enable update interrupt 
   TIM4_ITConfig(TIM4_IT_UPDATE, ENABLE);   
   // Enable TIM4
   TIM4_Cmd(ENABLE);    
}

//------------------------- TIM4 overflow --------------------------------------
void TIM4_OVF_IRQHandler(void) 
{
   millisCounter++;
   TIM4_ClearITPendingBit(TIM4_IT_UPDATE); 
}

//------------------------- soft delay -----------------------------------------
void delay(uint32_t delayms)
{
	uint32_t clk2ms = CPU_CLK / 1000;
	uint32_t count = delayms * clk2ms / 3; //3 instructions
	
	while(count-- > 0)
	{
	}	
}

//------------------------- systick counter ------------------------------------
uint32_t millis(void)
{
   return millisCounter;
}
