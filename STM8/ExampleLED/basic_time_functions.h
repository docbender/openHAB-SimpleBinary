//---------------------------------------------------------------------------
//
// Name:        basic_time_functions.h
// Author:      Vita Tucek
// Created:     29.11.2016
// License:     MIT
// Description: Time
// 
//
//---------------------------------------------------------------------------

#ifndef BASIC_TIME_FUNCTIONS_H
#define BASIC_TIME_FUNCTIONS_H

#include <stm8s.h>

#define CPU_CLK 16000000l
#define TIM4_PERIOD 124 //period for 1ms

INTERRUPT void TIM4_OVF_IRQHandler(void); /* Timer 4 overflow */

void systime_init(void);
//------------------------- TIM4 overflow --------------------------------------
void TIM4_OVF_IRQHandler(void);
//------------------------- soft delay -----------------------------------------
void delay(uint32_t delayms);
//------------------------- systick counter ------------------------------------
uint32_t millis(void);

#endif