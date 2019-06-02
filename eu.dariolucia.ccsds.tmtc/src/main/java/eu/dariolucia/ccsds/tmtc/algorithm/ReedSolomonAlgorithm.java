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

import java.util.Arrays;

/**
 * Class implementing the Reed-Solomon encoding/checking utility functions, as specified in CCSDS 131.0-B-3, 4.3.
 */
// TODO verify
public class ReedSolomonAlgorithm {

    public static final eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm TM_255_223 = new eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm(
            255,
            223,
            0x187,     // As per Blue Book specs: F(x) = x^8 + x^7 + x^2 + x + 1 = 110000111 = 391 = 0x187
            makeGeneratorPolynomial(0x187, 11, 112, 32),
            generatePolynomialRoots(0x187, 11, 112, 143)
    );

    public static final eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm TM_255_239 = new eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm(
            255,
            239,
            0x187,     // As per Blue Book specs: F(x) = x^8 + x^7 + x^2 + x + 1 = 110000111 = 391 = 0x187
            makeGeneratorPolynomial(0x187, 11, 120, 16),
            generatePolynomialRoots(0x187, 11, 120, 135)
    );

    public static int[] makeGeneratorPolynomial(int gfMod, int basis, int startingPower, int eccLen) {
        return new int[0];
    }

    public static int[] generatePolynomialRoots(int gfMod, int basis, int from, int to) {
        return new int[0];
    }

    private final RsDescriptor descriptor;

    /**
     * Create a Reed-Solomon algorithm executor based on the provided characteristics.
     *
     * @param codewordLength length of the codeword in bytes (the amount of data present in the final codeblock)
     * @param messageLength length of the encoded message in bytes
     * @param galoisFieldModulus the modulous of the Galois field
     * @param generatorPolynom the coefficients of the generator polynom
     * @param generatorPolynomRoots the roots of the generator polynom
     */
    public ReedSolomonAlgorithm(int codewordLength, int messageLength, int galoisFieldModulus, int[] generatorPolynom, int[] generatorPolynomRoots) {
        this(new RsDescriptor(codewordLength, messageLength, galoisFieldModulus, generatorPolynom, generatorPolynomRoots));
    }

    private ReedSolomonAlgorithm(RsDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * This method encodes the provided frame using the provided RS structure. It is required that the frame length (frame.length)
     * to be a multiple of the message length defined by the structure, otherwise an {@link IllegalArgumentException} exception will be thrown.
     *
     * @param frame the frame to be encoded
     * @return the frame followed by the Reed Solomon blocks
     * @throws IllegalArgumentException if frame has an unexpected length
     */
    public byte[] encodeFrame(byte[] frame) {
        // TODO

        // Current implementation: copy the frame and add, at the end, add a number of bytes equals to the number of
        // blocks * (messageLength - codewordLength)
        byte[] toReturn = new byte[frame.length + (frame.length / this.descriptor.messageLength) * (this.descriptor.codewordLength - this.descriptor.messageLength)];
        System.arraycopy(frame, 0, toReturn, 0, frame.length);
        return toReturn;
    }

    /**
     * This method simply removes the codeblocks and returns a copy of the frame contents. It does not attempt to perform
     * any error correction (...yet).
     *
     * @param rsCodeblock the RS encoded frame
     * @return the frame
     */
    public byte[] decodeFrame(byte[] rsCodeblock) {
        // TODO

        // Sanity check
        if(rsCodeblock.length % this.descriptor.codewordLength != 0) {
            throw new IllegalStateException("Expected frame length to be a multiple of " + descriptor.codewordLength + ", got " + rsCodeblock.length);
        }
        // Depending on the descriptor, compute the number of bytes to discard from the end of the provided codeblock
        int numRsBlocks = rsCodeblock.length / this.descriptor.codewordLength;
        int numBytesToDiscard = numRsBlocks * this.descriptor.rsBlockLength;

        return Arrays.copyOfRange(rsCodeblock, 0, rsCodeblock.length - numBytesToDiscard);
    }

    public boolean checkCodeword(int[] codeword) {
        // TODO
        return true;
    }

    public int[] encodeCodeword(int[] message) {
        // TODO
        return new int[this.descriptor.codewordLength];
    }

    /**
     * Utility class to collect all the parameters of a given {@link ReedSolomonAlgorithm} instance.
     */
    private static class RsDescriptor {

        public int messageLength;

        public int rsBlockLength;

        public int codewordLength;

        public int galoisFieldModulus;

        public int[] generatorPolynom;

        public int[] generatorPolynomRoots;

        public RsDescriptor(int codewordLength, int messageLength, int galoisFieldModulus, int[] generatorPolynom, int[] generatorPolynomRoots) {
            this.messageLength = messageLength;
            this.codewordLength = codewordLength;
            this.rsBlockLength = codewordLength - messageLength;
            this.galoisFieldModulus = galoisFieldModulus;
            this.generatorPolynom = generatorPolynom;
            this.generatorPolynomRoots = generatorPolynomRoots;
        }
    }
}
