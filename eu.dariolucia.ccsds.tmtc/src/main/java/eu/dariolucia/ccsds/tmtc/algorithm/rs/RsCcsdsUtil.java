/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.algorithm.rs;

public class RsCcsdsUtil {

    // Stored per column, see CCSDS 131.0-B-3, Annex F2
    private static final int[] STRAIGHT_T = new int[] {
            0b11111110,
            0b01101001,
            0b01101011,
            0b00001101,
            0b11101111,
            0b11110010,
            0b01011011,
            0b11000111
    };

    // Stored per column, see CCSDS 131.0-B-3, Annex F2
    private static final int[] INVERTED_T = new int[] {
            0b10011011,
            0b11011101,
            0b00111110,
            0b00011100,
            0b00110111,
            0b10110011,
            0b01100000,
            0b10010100
    };

    // TODO: the multiplications could be precomputed and the results preloaded into an array
    // in order to speed up the computation.
    public static int multiply(int integer, int[] matrix) {
        // Even if an integer, we use only 8 bits (the least significant ones)
        // bit 0 corresponds to the bit whose value is 128
        // bit 7 corresponds to the least significant bit
        int result = 0;
        for(int i = 0; i < matrix.length; ++i) {
            // Bitwise row-column multiplication is a bitwise AND
            int multResult = (integer & matrix[i]) & 0xFF;
            // Now compute Hamming's weigth (number of bits) mod 2
            multResult = Integer.bitCount(multResult) % 2;
            // If multResult is 1, it means that the i-th bit in result must be set to 1
            if(multResult == 1) {
                // Build the mask
                int mask = 1 << (7 - i);
                // Apply the mask
                result |= mask;
            }
        }
        return result & 0xFF;
    }

    // Stored per column, see CCSDS 131.0-B-3, Annex F2
    public static int[] getStraightT() {
        return STRAIGHT_T;
    }

    // Stored per column, see CCSDS 131.0-B-3, Annex F2
    public static int[] getInvertedT() {
        return INVERTED_T;
    }
}
