//---------------------------------------------------------------------------
//
// Name:        crc8.c
// Author:      Vita Tucek
// Created:     28.11.2016
// License:     MIT
// Description: CRC8 check header (C language)
//
//---------------------------------------------------------------------------

char evalCRC(char *data, int length)
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
