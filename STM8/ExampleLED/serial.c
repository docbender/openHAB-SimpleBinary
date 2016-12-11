//---------------------------------------------------------------------------
//
// Name:        serial.c
// Author:      Vita Tucek
// Created:     29.11.2016
// License:     MIT
// Description: serial
// 
//
//---------------------------------------------------------------------------

#include "serial.h"
#include "basic_time_functions.h"

struct buffer
{
    uint8_t data[BUFFER_SIZE];  // data buffer
    uint8_t count;     // number of items in the buffer
    uint8_t *head;     // pointer to head
    uint8_t *tail;     // pointer to tail
    uint8_t *end;      // pointer to end
    
} rxBuffer, txBuffer;


//----------------------- flush serial data ------------------------------------
void serial_begin(int bitrate)
{
   //init rx buffer
   rxBuffer.head = rxBuffer.data;
   rxBuffer.end = rxBuffer.data + BUFFER_SIZE;
   rxBuffer.tail = rxBuffer.data;
   rxBuffer.count = 0;
   //init tx buffer
   txBuffer.head = txBuffer.data;
   txBuffer.end = txBuffer.data + BUFFER_SIZE;
   txBuffer.tail = txBuffer.data;
   txBuffer.count = 0;
	// UART Init
	UART1_DeInit();
	UART1_Init(bitrate,UART1_WORDLENGTH_8D,UART1_STOPBITS_1,UART1_PARITY_NO,
		UART1_SYNCMODE_CLOCK_DISABLE,UART1_MODE_TXRX_ENABLE);
   
   /* Enable the UART Receive interrupt: this interrupt is generated when the UART
    receive data register is not empty */
   UART1_ITConfig(UART1_IT_RXNE_OR, ENABLE);
  
   /* Enable UART */
   UART1_Cmd(ENABLE);	
}

//----------------------- put byte into buffer ---------------------------------
void buffer_put(buffer *data, uint8_t item)
{
   if(data->count == BUFFER_SIZE)
      return;
      
   *(data->head) = item;

   if(++data->head==data->end)
      data->head = data->data;
   
   data->count++;
}

//----------------------- retrieve datafrom buffer -----------------------------
uint8_t buffer_pop(buffer *data)
{
   uint8_t item;
   if(data->count == 0)
      return 0;

   item = *(data->tail);

   if(++data->tail==data->end)
      data->tail = data->data;
      
   data->count--;
   
   return item;
}
 
//------------------------- UART tx ready to write -----------------------------
void UART1_TX_IRQHandler(void)
{
   uint8_t data;
    
   if(txBuffer.count == 0)
   {
      UART1_ITConfig(UART1_IT_TXE, DISABLE);  
      return;
   }
      
   data = buffer_pop(&txBuffer);
	
   /* Write one byte to the transmit data register */
   UART1_SendData8(data);
}

//------------------------- UART received data ---------------------------------
void UART1_RX_IRQHandler(void) 
{
   //read data(during read interrupt bit is cleared)
   buffer_put(&rxBuffer, UART1_ReceiveData8()); 
}  

//----------------------- data lenght in buffer --------------------------------
uint8_t serial_available(void)
{
	return rxBuffer.count;
}

//----------------------- write serial data ------------------------------------
void serial_write(char *data, int length)
{
   int i;
   uint8_t formerlen;
   
   UART1_ITConfig(UART1_IT_TXE, DISABLE);
   
   formerlen = txBuffer.count;
   
   for(i=0;i<length;i++)
   {
      buffer_put(&txBuffer,data[i]);
   }
   
   UART1_ITConfig(UART1_IT_TXE, ENABLE);  
}

//----------------------- flush serial data ------------------------------------
void serial_flush(void)
{
   while(txBuffer.count>0) // || (UART1_GetFlagStatus(UART1_FLAG_TC) != 0))
   {      
   }
   delay(1);
}

//----------------------- write serial data ------------------------------------
uint8_t serial_read(void)
{
   return buffer_pop(&rxBuffer);
}
