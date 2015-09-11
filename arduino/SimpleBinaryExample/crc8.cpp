//---------------------------------------------------------------------------
//
// Name:        crc8.cpp
// Author:      Vita Tucek
// Created:     20.8.2015
// Description: CRC8 check
//
//---------------------------------------------------------------------------

#include "crc8.h"

//
char CRC8::evalCRC(char *data, int length)
{
  int crc = 0;
	int i, j;
	
	for(j=0; j < length; j++)
	{
		crc ^= (data[j] << 8);
		
		for(i=0;i<8;i++)
		{
			if((crc & 0x8000) != 0)
				crc ^= (0x1070 << 3);
		
			crc <<= 1;
		}
	}
	
	return (char)(crc >> 8);
} 

