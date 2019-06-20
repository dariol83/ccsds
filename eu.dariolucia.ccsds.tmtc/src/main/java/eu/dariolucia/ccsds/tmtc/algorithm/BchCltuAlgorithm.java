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

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * This class contains the algorithm to encode and check CLTUs using BCH CCSDS algorithm for TC frames, according
 * to the specification defined in CCSDS 231.0-B-3. The code is not optimized and it is fairly naive, but follows
 * strictly the standard specification, so that is can be exactly correlated to the block diagram provided in
 * CCSDS 231.0-B-3, Figure 3-2.
 */
public class BchCltuAlgorithm {

    /**
     * The prefix of the BCH CLTU (ref. CCSDS 231.0-B-3, 5.2.2.2)
     */
    private static final byte[] DEFAULT_CLTU_PREFIX = new byte[]{(byte) 0xEB, (byte) 0x90};
    /**
     * The suffix of the BCH CLTU (ref. CCSDS 231.0-B-3, 5.2.4.1)
     */
    private static final byte[] DEFAULT_CLTU_SUFFIX = new byte[]{(byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, 0x79};
    /**
     * CLTU pad/fill bytes for last code block (ref. CCSDS 231.0-B-3, 3.4.2)
     * Use 1010 1010 - 0x55 to ensure enough bit transition density.
     */
    private static final byte DEFAULT_CLTU_FILL_BYTE = 0x55;
    /**
     * The default CLTU algorithm to be used (CCSDS compliant).
     */
    private static final BchCltuAlgorithm DEFAULT_CLTU_ALGORITHM = new BchCltuAlgorithm();

    /**
     * This method encodes a TC frame (randomized or not) into a CLTU using the default BCH algorithm (as per CCSDS
     * standard).
     *
     * @param frame the TC frame to encode, could be randomized depending on the channel configuration
     * @return the encoded CLTU
     */
    public static byte[] encode(byte[] frame) {
        return DEFAULT_CLTU_ALGORITHM.encodeCltu(frame);
    }

    /**
     * This method decodes a CLTU into a TC frame (randomized or not) using the default BCH algorithm (as per CCSDS
     * standard).
     *
     * @param cltu the CLTU to check
     * @return the decoded TC frame, could be randomized depending on the channel configuration
     */
    public static byte[] decode(byte[] cltu) {
        return DEFAULT_CLTU_ALGORITHM.decodeCltu(cltu);
    }

    private final byte[] effectivePrefix;

    private final byte effectiveFillByte;

    private final byte[] effectiveSuffix;

    /**
     * Create a default BCH CLTU algorithm with the default prefix, fill byte and suffix as specified
     * by CCSDS 231.0-B-3.
     */
    public BchCltuAlgorithm() {
        this(null, null);
    }

    /**
     * Create a BCH CLTU algorithm with the specified prefix and suffix. The fill byte is set to 0x55.
     *
     * @param effectivePrefix the prefix to be used
     * @param effectiveSuffix  the suffix to be used
     */
    public BchCltuAlgorithm(byte[] effectivePrefix, byte[] effectiveSuffix) {
        this(effectivePrefix, DEFAULT_CLTU_FILL_BYTE, effectiveSuffix);
    }

    /**
     * Create a BCH CLTU algorithm with the specified prefix, fill byte and suffix.
     *
     * @param effectivePrefix the prefix to be used
     * @param effectiveFillByte      the value of the fill byte to be used
     * @param effectiveSuffix  the suffix to be used
     */
    public BchCltuAlgorithm(byte[] effectivePrefix, byte effectiveFillByte, byte[] effectiveSuffix) {
        this.effectiveFillByte = effectiveFillByte;
        if (effectivePrefix != null) {
            this.effectivePrefix = effectivePrefix;
        } else {
            this.effectivePrefix = DEFAULT_CLTU_PREFIX;
        }
        if (effectiveSuffix != null) {
            this.effectiveSuffix = effectiveSuffix;
        } else {
            this.effectiveSuffix = DEFAULT_CLTU_SUFFIX;
        }
    }

    /**
     * This method is used to compute the state of shift register upon ingestion of a new value: for the state of the
     * shift register and the ingested value, the short data type is used to avoid playing with negative byte values.
     *
     * The approach is very simple and follows the block diagram defined in CCSDS 231.0-B-3, 3.3:
     * - the shift register has 7 bits, whose current state is provided by the currentShiftRegisterState parameter;
     * - the 8 bits of the value are processed starting from the most significant one: a mask is used to extract the
     *   value;
     * - the shift register is pushed by one block at every bit ingestion;
     * - we check the bit value that we have to push in the shift register: if it is 1, then we add it to positions 0,
     *   2, 6 as per block diagram; if it is 0, we don't do anything;
     * - we check the output of the shift register: if it is 1, then we add it to positions 0, 2, 6, as per block
     *   diagram; if it is 0, we don't do anything;
     * - we update the mask to extract the next bit from the value and we keep going until we process all the 8 bits of
     *   the value.
     *
     * @param currentShiftRegisterState the current state of the shift register: the first 7 LSB are significant
     * @param value                     the value to be ingested
     * @return the state of the shift register at the end of the ingestion of the provided value
     */
    private static short ingestValue(short currentShiftRegisterState, byte value) {
        // get the unsigned version of the value
        short uintValue = (short) Byte.toUnsignedInt(value);
        // read and process one bit of the value after the other (8 bits in total)
        // start with the most significant bit (as per standard) and keep shifting to the right
        for (short valueMask = 0b1000_0000; valueMask != 0; valueMask >>= 1) {
            // clock the shift register to the left and read the bit that is emitted (8th bit)
            currentShiftRegisterState <<= 1;
            // add the value bit to the prescribed positions (if it is 1)
            if ((uintValue & valueMask) > 0) {
                // add 1 to the positions indicated by the generator polynomial (0,2,6)
                currentShiftRegisterState ^= 0b0100_0101;
            }
            // add the shift register output (if it is 1)
            if ((currentShiftRegisterState & 0b1000_0000) > 0) {
                // add 1 to the positions indicated by the generator polynomial (0,2,6)
                currentShiftRegisterState ^= 0b0100_0101;
            }
        }
        // as value of the shift register, we are interested only to the first 7 bits LSB, reset the others
        return (short) (currentShiftRegisterState & 0b0111_1111);
    }

    /**
     * This method performs a CLTU BCH encoding of the provided frame using the pre-computer lookup table as per
     * CCSDS 231.0-B-3, 3.3.
     * <p>
     * Randomization of the input frame is not performed by this method.
     *
     * @param frame the frame to be encoded
     * @return the BCH-encoded CLTU
     */
    public byte[] encodeCltu(byte[] frame) {
        // payload length of the CLTU: if there are missing bytes to reach this end, fill bytes will be used
        int cltuPayloadLength = (frame.length / 7 + (frame.length % 7 > 0 ? 1 : 0)) * 8;
        // total length of the CLTU
        int cltuTotalLength = effectivePrefix.length + cltuPayloadLength + effectiveSuffix.length;

        // allocate the CLTU output buffer...
        ByteBuffer cltuOutputBuffer = ByteBuffer.allocate(cltuTotalLength);
        // ...and keep track of the number of payload bytes currently written in the CLTU: required to add fill bytes if necessary
        int cltuWrittenBytes = 0;

        // add the prefix
        cltuOutputBuffer.put(effectivePrefix);

        // index used to read the frame bytes, one byte at a time
        int currentFrameIndex = 0;
        // index in the current code block: when 7 is reached (56 bits processed), the parity byte must be added and
        // we need to start with the next code block
        int currentBlockIndex = 0;
        // the state of the shift register
        short currentShiftRegisterState = 0;
        // encode the CLTU body
        while (cltuWrittenBytes < cltuPayloadLength) {
            // take the next byte either from the frame (if you can) or from the fill byte
            byte valueToProcess;
            if (currentFrameIndex < frame.length) {
                valueToProcess = frame[currentFrameIndex];
                ++currentFrameIndex;
            } else {
                valueToProcess = effectiveFillByte;
            }
            // the value goes straight to the output buffer...
            cltuOutputBuffer.put(valueToProcess);
            ++cltuWrittenBytes;
            // ...and the state of the shift register by processing the bits one by one is computed
            currentShiftRegisterState = ingestValue(currentShiftRegisterState, valueToProcess);
            // let's increment the code block index
            currentBlockIndex++;
            // if we are at the end of the code block, then we need to compute and add the parity byte: complement
            // the 7 bits of the registry and set the filler bit to 0 (ref. CCSDS 231.0-B-3, 3.2.3)
            if (currentBlockIndex >= 7) {
                // add to the output buffer
                cltuOutputBuffer.put((byte) (~currentShiftRegisterState << 1));
                ++cltuWrittenBytes;
                // reset the code block index, to start the next code block...
                currentBlockIndex = 0;
                // ...and reset the shift register as well
                currentShiftRegisterState = 0;
            }
        }
        // add the suffix to the output buffer and return
        return cltuOutputBuffer.put(effectiveSuffix).array();
    }

    /**
     * This method decodes a CLTU into a frame. The calculated frame contains additional fill octets at
     * the end. These octets shall be removed by taking into account the managed parameters established for
     * the transfer layer. Because of this, removal of fill octets is not performed by this method.
     * <p>
     * In addition, error decoding, error checking and de-randomization of the resulting frame are not performed by this
     * method. However, basic checks (prefix match, suffix match) are performed and an exception is
     * raised if they are found incoherent.
     *
     * @param cltu the CLTU raw data
     * @return the frame raw data
     * @throws IllegalArgumentException if the CLTU is malformed in its basic properties and cannot be decoded
     */
    public byte[] decodeCltu(byte[] cltu) {
        // check if the prefix matches
        for (int i = 0; i < effectivePrefix.length; i++) {
            if (cltu[i] != effectivePrefix[i]) {
                throw new IllegalArgumentException("The CLTU does not start with the expected prefix, expected prefix is " + Arrays.toString(effectivePrefix));
            }
        }
        // calculate the data size from the CLTU size
        int dataSize = cltu.length - effectivePrefix.length - effectiveSuffix.length;
        // calculate the output frame size and allocate a temporary buffer
        int blocksToRead = dataSize / 8;
        ByteBuffer frameBuffer = ByteBuffer.allocate(blocksToRead * 7);
        // keep memory of where you are reading, in order to verify the suffix later
        int readBytePosition = 0;
        // block copy
        for (int i = 0; i < blocksToRead; ++i) {
            frameBuffer.put(cltu, effectivePrefix.length + i * 8, 7);
            readBytePosition = effectivePrefix.length + i * 8 + 7;
        }
        // add the last parity byte
        ++readBytePosition;
        // check CLTU suffix
        if (cltu.length - readBytePosition != effectiveSuffix.length) {
            throw new IllegalArgumentException("The CLTU suffix length is wrong (" + (cltu.length - readBytePosition) + ") at initial offset " + readBytePosition + ", expected length of " + effectiveSuffix.length);
        }
        for (int i = 0; i < effectiveSuffix.length; ++i) {
            if (cltu[readBytePosition + i] != effectiveSuffix[i]) {
                throw new IllegalArgumentException("The CLTU suffix at initial offset " + readBytePosition + " does not match, expected " + Arrays.toString(effectiveSuffix));
            }
        }
        // return the output buffer
        return frameBuffer.array();
    }
}
