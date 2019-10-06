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

import eu.dariolucia.ccsds.tmtc.algorithm.rs.ReedSolomon;
import eu.dariolucia.ccsds.tmtc.algorithm.rs.RsDecoder;
import eu.dariolucia.ccsds.tmtc.algorithm.rs.RsEncoder;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Class implementing the Reed-Solomon encoding/checking utility functions, as specified in CCSDS 131.0-B-3, 4.3.
 */
public class ReedSolomonAlgorithm {

    /**
     * Instantiation of the Reed Solomon algorithm according to the specifications of CCSDS 131.0-B-3, 4.3, defining
     * a 255,223 encoding:
     * <ul>
     *     <li>field generator: F(x) = x^8 + x^7 + x^2 + x + 1 = 110000111 = 391 = 0x187</li>
     *     <li>primitive element: alpha^11 = 173 in GF(2^8)</li>
     *     <li>initial root: 112</li>
     *     <li>dual basis representation</li>
     * </ul>
     */
    public static final eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm TM_255_223 = new eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm(
            223,
            255,
            0x187,     // As per Blue Book specs: F(x) = x^8 + x^7 + x^2 + x + 1 = 110000111 = 391 = 0x187
            173,        // alpha ^ 11
            112,        //
            true
    );

    /**
     * Instantiation of the Reed Solomon algorithm according to the specifications of CCSDS 131.0-B-3, 4.3, defining
     * a 255,239 encoding:
     * <ul>
     *      <li>field generator: F(x) = x^8 + x^7 + x^2 + x + 1 = 110000111 = 391 = 0x187</li>
     *      <li>primitive element: alpha^11 = 173 in GF(2^8)</li>
     *      <li>initial root: 120</li>
     *      <li>dual basis representation</li>
     * </ul>
     */
    public static final eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm TM_255_239 = new eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm(
            239,
            255,
            0x187,     // As per Blue Book specs: F(x) = x^8 + x^7 + x^2 + x + 1 = 110000111 = 391 = 0x187
            173,
            120,
            true
    );

    private final int messageLength;
    private final int codewordLength;
    private final boolean dualbasis;
    private final int eccLength;
    private final ReedSolomon reedSolomon;

    /**
     * Create a Reed-Solomon algorithm executor based on the provided characteristics.
     *
     * @param messageLength length of the encoded message in bytes
     * @param codewordLength length of the codeword in bytes (the amount of data present in the final codeblock)
     * @param galoisFieldModulus the modulous of the Galois field
     * @param generator the coefficients of the generator polynom
     * @param initialRoot the roots of the generator polynom
     * @param dualbasis true if dual basis representation is used, false otherwise
     */
    public ReedSolomonAlgorithm(int messageLength, int codewordLength, int galoisFieldModulus, int generator, int initialRoot, boolean dualbasis) {
        this.messageLength = messageLength;
        this.codewordLength = codewordLength;
        this.eccLength = this.codewordLength - this.messageLength;
        this.dualbasis = dualbasis;
        this.reedSolomon = new ReedSolomon(galoisFieldModulus, generator, messageLength, eccLength, initialRoot);
    }

    /**
     * This method encodes the provided frame using the RS properties specified at construction time. It is required that the frame length (frame.length)
     * is a multiple of the message length defined by the structure, otherwise an {@link IllegalArgumentException} exception will be thrown. Possible
     * padding (virtual fill) to the frame shall be added by the caller.
     *
     * The interleaving depth specifies how to construct the codewords from the frame, according to CCSDS 131.0-B-3, 4.3.5.
     * The approach implemented here is exactly the one described in CCSDS 131.0-B-3, 4.4.1.
     *
     * @param frame the frame to be encoded
     * @param interleavingDepth the interleaving depth, allowed values are I=1, 2, 3, 4, 5, and 8
     * @return the frame followed by the Reed Solomon blocks
     * @throws IllegalArgumentException if frame has an unexpected length, or if the interleaving is not supported
     */
    public byte[] encodeFrame(byte[] frame, int interleavingDepth) {
        if(frame.length % messageLength != 0) {
            throw new IllegalArgumentException("Frame length (" + frame.length + " bytes) is not a multiple of " + messageLength);
        }
        if(interleavingDepth != 1 && interleavingDepth != 2 && interleavingDepth != 3 && interleavingDepth != 4 && interleavingDepth != 5 && interleavingDepth != 8) {
            throw new IllegalArgumentException("Unsupported interleaving depth");
        }

        // Instantiate the sink
        ByteBuffer sink = ByteBuffer.allocate(computeFinalMessageLength(frame.length));
        // Instantiate interleavingDepth encoders
        RsEncoder[] encs = new RsEncoder[interleavingDepth];
        for(int i = 0; i < encs.length; ++i) {
            encs[i] = new RsEncoder(reedSolomon, dualbasis, sink);
        }
        // Encode
        for(int i = 0; i < frame.length; ++i) {
            encs[i % interleavingDepth].pushMessage(frame[i]);
        }
        // Verify completion
        for(RsEncoder e : encs) {
            if(!e.ready()) {
                throw new IllegalStateException("One encoder is not ready to output the RS block");
            }
        }
        // Output RS block
        int blockSize = encs[0].blockSize();
        for(int i = 0; i < blockSize; ++i) {
            for(int j = 0;j < encs.length; ++j) {
                encs[j].pull();
            }
        }
        // Return
        return sink.array();
    }

    private int computeFinalMessageLength(int length) {
        return length + (length / messageLength) * eccLength;
    }

    /**
     * This method simply removes the Reed Solomon symbols and returns a copy of the frame contents.
     * Optionally, it can perform an error detection on the frame. If the frame has errors, then it returns null.
     * It does not attempt to perform any error correction.
     *
     * @param encodedFrame the RS encoded frame, with the RS block at the end
     * @param interleavingDepth interleaving depth, only required if error checking is enabled, otherwise ignored
     * @param errorChecking if true, error detection is enabled
     * @return the frame or null if error detection is requested and errors are found
     */
    public byte[] decodeFrame(byte[] encodedFrame, int interleavingDepth, boolean errorChecking) {
        // Sanity check
        if(encodedFrame.length % codewordLength != 0) {
            throw new IllegalArgumentException("Expected frame length to be a multiple of " + codewordLength + ", got " + encodedFrame.length);
        }
        // Depending on the algorithm configuration, compute the number of bytes to discard from the end of the provided codeblock
        int numRsBlocks = encodedFrame.length / codewordLength;
        int numBytesToDiscard = numRsBlocks * eccLength;
        byte[] decoded = Arrays.copyOfRange(encodedFrame, 0, encodedFrame.length - numBytesToDiscard);
        // If error detection is requested, we need to take into account the interleaving depth
        if(errorChecking) {
            if(encodedFrame.length / codewordLength != interleavingDepth) {
                throw new IllegalArgumentException("The provided frame length does not correspond with the provided interleaving depth");
            }
            // Instantiate interleavingDepth buffers
            ByteBuffer[] decs = new ByteBuffer[interleavingDepth];
            for(int i = 0; i < decs.length; ++i) {
                decs[i] = ByteBuffer.allocate(codewordLength);
            }
            // Fill the buffers
            for(int i = 0; i < encodedFrame.length; ++i) {
                decs[i % interleavingDepth].put(encodedFrame[i]);
            }
            // Instantiate the decoder
            RsDecoder decoder = new RsDecoder(reedSolomon, dualbasis);

            for(ByteBuffer bb : decs) {
                bb.flip();
                byte[] result = decoder.decode(bb.array(), true);
                if(result == null) {
                    return null;
                }
            }
        }
        return decoded;
    }

    /**
     * This method encodes the provided message according to the provided configuration of the Reed Solomon algorithm.
     * The input must be the message to encode, whose size must be equal to messageLength, otherwise an exception is
     * thrown. In fact, this method does not perform padding or virtual fill. The output is the message plus the RS block at the end.
     *
     * @param message the message to encode
     * @return the encoded codeword (input + RS block)
     */
    public byte[] encodeCodeword(byte[] message) {
        if(message.length != messageLength) {
            throw new IllegalArgumentException("Message length " + message.length + " does not match the configured message length for this encoder: " + messageLength);
        }
        // Instantiate the sink
        ByteBuffer sink = ByteBuffer.allocate(codewordLength);
        // Instantiate the encoder
        RsEncoder encs = new RsEncoder(reedSolomon, dualbasis, sink);
        // Encode
        encs.pushMessage(message);
        // Verify completion
        if(!encs.ready()) {
            throw new IllegalStateException("One encoder is not ready to output the RS block");
        }
        // Output RS block
        encs.pullAll();
        // Return
        return sink.array();
    }

    /**
     * The input must be the message plus the RS block at the end.
     *
     * @param codeword the encoded codeword (input + RS block)
     * @param errorChecking if error checking must be performed
     * @return the decoded message (no error correction) or null if errors are present
     */
    public byte[] decodeCodeword(byte[] codeword, boolean errorChecking) {
        if(codeword.length != codewordLength) {
            throw new IllegalArgumentException("Codeword length " + codeword.length + " does not match the configured codeword length for this encoder: " + codewordLength);
        }
        // Instantiate the decoder
        RsDecoder decs = new RsDecoder(reedSolomon, dualbasis);
        // Decode
        return decs.decode(codeword, errorChecking);
    }
}
