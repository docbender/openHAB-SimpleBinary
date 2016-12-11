//---------------------------------------------------------------------------
//
// Name:        serial.h
// Author:      Vita Tucek
// Created:     29.11.2016
// License:     MIT
// Description: serial
// 
//
//---------------------------------------------------------------------------

#ifndef SERIAL_H
#define SERIAL_H

#include <stdlib.h>
#include <stm8s.h>

#define BUFFER_SIZE 32

typedef struct buffer buffer;



INTERRUPT void UART1_TX_IRQHandler(void); /* UART1 TX */
INTERRUPT void UART1_RX_IRQHandler(void); /* UART1 RX */

//----------------------- flush serial data ------------------------------------
void buffer_put(buffer *data, uint8_t item);
//----------------------- flush serial data ------------------------------------
uint8_t buffer_pop(buffer *data);
//------------------------- UART tx ready to write -----------------------------
void UART1_TX_IRQHandler(void);
//------------------------- UART received data ---------------------------------
void UART1_RX_IRQHandler(void);
//----------------------- flush serial data ------------------------------------
void serial_begin(int bitrate);
//----------------------- data lenght in buffer --------------------------------
uint8_t serial_available(void);
//----------------------- write serial data ------------------------------------
void serial_write(char *data, int length);
//----------------------- flush serial data ------------------------------------
void serial_flush(void);
//----------------------- write serial data ------------------------------------
uint8_t serial_read(void);


#endif