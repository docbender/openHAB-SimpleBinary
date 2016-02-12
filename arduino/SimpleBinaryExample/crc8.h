//---------------------------------------------------------------------------
//
// Name:        crc8.h
// Author:      Vita Tucek
// Created:     20.8.2015
// License:     MIT
// Description: CRC8 check header
//
//---------------------------------------------------------------------------
#ifndef CRC8_H
#define CRC8_H


class CRC8
{
  public:
   static char evalCRC(char *data, int length);
}; 

#endif


