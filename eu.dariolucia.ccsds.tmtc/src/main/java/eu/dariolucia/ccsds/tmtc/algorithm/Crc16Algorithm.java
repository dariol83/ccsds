/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.algorithm;

/**
 * This class contains the algorithm to compute CRCs using different algorithms.
 */
public class Crc16Algorithm {

	/**
	 * This method computs the CRC16 of the provided byte array, using the algorithm described in
	 * CCSDS 132.0-B-2, 4.1.6.2.
	 *
	 * @param frame the frame
	 * @param offset the offset
	 * @param length the length
	 * @return the 2 bytes CRC of the provided byte array, from offset (incl.) to offset + length (excl.)
	 */
	public static short getCrc16(byte[] frame, int offset, int length) {
		int shiftRegister = 0x0000FFFF;
		for(int i = 0; i < length; ++i) {
			shiftRegister = ingestValue(shiftRegister, (short) Byte.toUnsignedInt(frame[offset + i]));
		}
		return (short) shiftRegister;
	}

	/**
	 * This method is used to compute the state of shift register upon ingestion of a new value: for the state of the
	 * shift register and the ingested value, the int and short data type are respectively used to avoid playing with
	 * negative byte values.
	 *
	 * The approach is very simple and follows the block diagram defined in CCSDS 132.0-B-2, 4.1.6.2:
	 * - the shift register has 16 bits, whose current state is provided by the shiftRegister parameter;
	 * - the 8 bits of the value are processed starting from the most significant one: a mask is used to extract the
	 *   value;
	 * - the shift register is pushed by one block at every bit ingestion;
	 * - we check the bit value that we have to push in the shift register: if it is 1, then we add it to positions 0,
	 *   5, 12 as per block diagram; if it is 0, we don't do anything;
	 * - we check the output of the shift register: if it is 1, then we add it to positions 0, 5, 12, as per block
	 *   diagram; if it is 0, we don't do anything;
	 * - we update the mask to extract the next bit from the value and we keep going until we process all the 8 bits of
	 *   the value.
	 *
	 * @param shiftRegister             the current state of the shift register: the 16 LSB are significant
	 * @param value                     the value to be ingested
	 * @return the state of the shift register at the end of the ingestion of the provided value
	 */
	private static int ingestValue(int shiftRegister, short value) {
		for (short valueMask = 0b10000000; valueMask != 0; valueMask >>= 1) {
			// push the shift register by one (to the left in this case and read the bit that is emitted (16th bit)
			shiftRegister <<= 1;
			// add the value bit to the prescribed positions (if it is 1)
			if ((value & valueMask) > 0) {
				// add 1 to the positions indicated by the generator polynomial (0,5,12)
				shiftRegister ^= 0b00010000_00100001;
			}
			// add the shift register output (if it is 1)
			if ((shiftRegister & 0b1_00000000_00000000) > 0) {
				// add 1 to the positions indicated by the generator polynomial (0,5,12)
				shiftRegister ^= 0b00010000_00100001;
			}
		}
		// return the shift register, reset the 16 MSB
		return shiftRegister & 0x0000FFFF;
	}

}
