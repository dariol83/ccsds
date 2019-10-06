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
 * This class contains the algorithm to compute randomization using different algorithms.
 */
public class RandomizerAlgorithm {

    private RandomizerAlgorithm() {
        // Private constructor
    }

    /**
     * Definition of the CLTU pseudo random pattern, up to 65536 bytes, which is good enough for TC frames (max 1024),
     * Proximity-1 (max 2048), USLP (max 65536) and for AOS frames with reasonable size.
     */
    private static final byte[] CLTU_PSEUDO_RANDOM_PATTERN = generateCltuPseudoRandomPattern(65536);

    /**
     * Definition of the TM pseudo random pattern, up to 65536 bytes, which is good enough for TM frames (max 1024),
     * Proximity-1 (max 2048), USLP (max 65536) and for AOS frames with reasonable size.
     */
    private static final byte[] TM_PSEUDO_RANDOM_PATTERN = generateTmPseudoRandomPattern(65536);

    /**
     * This method generates the CLTU pseudo random pattern of the given length. The generator polynomial is the one
     * defined in CCSDS 231.0-B-3, 6.2: h(x) = x^8 + x^6 + x^4 + x^3 + x^2 + x + 1
     *
     * Generated sequence: 1111 1111 0011 1001 1001 1110 0101 1010 0110 1000 ...
     *
     * @param length the length of the CLTU pseudo random pattern to generate
     * @return the CLTU pseudo random pattern
     */
    private static byte[] generateCltuPseudoRandomPattern(int length) {
        byte[] pattern = new byte[length];
        int patternIdx = 0;
        byte patternMask = (byte) 0b1000_0000;
        // we need to do length * 8 clock steps
        short shiftRegister = 0x00FF;

        for(int i = 0; i < length * 8; ++i) {
            // clock
            shiftRegister <<= 1;
            // extract the output value
            boolean outputBit = (shiftRegister & 0b00000001_00000000) > 0;
            // read the bits in positions 7, 6, 5, 4, 2
            boolean b7 = (shiftRegister & 0b00000000_10000000) > 0;
            boolean b6 = (shiftRegister & 0b00000000_01000000) > 0;
            boolean b5 = (shiftRegister & 0b00000000_00100000) > 0;
            boolean b4 = (shiftRegister & 0b00000000_00010000) > 0;
            boolean b2 = (shiftRegister & 0b00000000_00000100) > 0;
            // set the entry bit
            if(outputBit ^ b7 ^ b6 ^ b5 ^ b4 ^ b2) {
                shiftRegister |= 0b00000000_00000001;
            }
            // clear the output bit
            shiftRegister &= 0x00FF;
            // Store the output bit in the pattern array
            if(outputBit) {
                pattern[patternIdx] |= patternMask;
            }
            // move the pattern mask
            patternMask = (byte) ((Byte.toUnsignedInt(patternMask) >> 1) & 0xFF);
            if(patternMask == 0) {
                // reinitialise the mask
                patternMask = (byte) 0b1000_0000;
                // increment the byte
                ++patternIdx;
            }
        }

        return pattern;
    }

    /**
     * This method randomizes the provided frame using the pseudo-random polynomial as defined in CCSDS 231.0-B-3, 6.2.
     * Randomization is performed in-place for efficiency reasons.
     *
     * @param frame the frame to randomize
     */
    public static void randomizeFrameCltu(byte[] frame) {
        for (int i = 0; i < frame.length; ++i) {
            frame[i] = (byte) (frame[i] ^ CLTU_PSEUDO_RANDOM_PATTERN[i]);
        }
    }

    /**
     * This method generates the TM pseudo random pattern of the given length. The generator polynomial is the one
     * defined in CCSDS 131.0-B-3 , 10.4.1: h(x) = x^8 + x^7 + x^5 + x^3 + 1
     *
     * Generated sequence: 1111 1111 0100 1000 0000 1110 1100 0000 1001 1010 ...
     *
     * @param length the length of the TM pseudo random pattern to generate
     * @return the TM pseudo random pattern
     */
    private static byte[] generateTmPseudoRandomPattern(int length) {
        byte[] pattern = new byte[length];
        int patternIdx = 0;
        byte patternMask = (byte) 0b1000_0000;
        // we need to do length * 8 clock steps
        short shiftRegister = 0x00FF;

        for(int i = 0; i < length * 8; ++i) {
            // clock
            shiftRegister <<= 1;
            // extract the output value
            boolean outputBit = (shiftRegister & 0b00000001_00000000) > 0;
            // read the bits in positions 5, 3, 1
            boolean b5 = (shiftRegister & 0b00000000_00100000) > 0;
            boolean b3 = (shiftRegister & 0b00000000_00001000) > 0;
            boolean b1 = (shiftRegister & 0b00000000_00000010) > 0;
            // set the entry bit
            if(outputBit ^ b5 ^ b3 ^ b1) {
                shiftRegister |= 0b00000000_00000001;
            }
            // clear the output bit
            shiftRegister &= 0x00FF;
            // Store the output bit in the pattern array
            if(outputBit) {
                pattern[patternIdx] |= patternMask;
            }
            // move the pattern mask
            patternMask = (byte) ((Byte.toUnsignedInt(patternMask) >> 1) & 0xFF);
            if(patternMask == 0) {
                // reinitialise the mask
                patternMask = (byte) 0b1000_0000;
                // increment the byte
                ++patternIdx;
            }
        }

        return pattern;
    }

    /**
     * This method randomizes the provided frame using the pseudo-random polynom as defined in CCSDS 131.0-B-3, 10.4.
     * Randomization is performed in-place for efficiency reasons.
     *
     * @param frame the frame to randomize
     */
    public static void randomizeFrameTm(byte[] frame) {
        for (int i = 0; i < frame.length; ++i) {
            frame[i] = (byte) (frame[i] ^ TM_PSEUDO_RANDOM_PATTERN[i]);
        }
    }
}
