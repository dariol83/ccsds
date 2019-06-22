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

import java.nio.ByteBuffer;

/**
 * A Reed-Solomon encoder that is used to implement the interleaving mechanism defined by CCSDS 131.0-B-3 and supports
 * dual basis transformation as indicated in CCSDS 131.0-B-3, Annex F. Internally it uses a conventional Reed-Solomon
 * encoder: if needed, this encoder converts the input symbols in dual basis representation and finalises the conversion
 * at the output of the conventional encoder.
 *
 * The way to use instances of this class is the following:
 * <ul>
 *     <li>The instance is created by providing a conventional encoder, the selection whether dual basis representation
 *     must be used and the final sink (the output of the encoder). The sink must have the capacity of a full RS codeword.
 *     </li>
 *     <li>By invoking the method push(), data is pushed into the encoder, accumulated internally and immediately sent to the sink</li>
 *     <li>As soon as the internal accumulator reaches the size of the required RS message, the encoder computes the
 *     RS symbols</li>
 *     <li>Once the construction of the RS symbols is performed, by invoking the method pull() a single byte is written
 *     to the sink</li>
 * </ul>
 *
 * The implementation of this encoder has been done in this way in order to support exactly the processing specified by
 * CCSDS 131.0-B-3, 4.4.1.
 */
public class RsEncoder {

    private final ReedSolomon conventionalEncoder;

    private final boolean dualBasis;

    private final ByteBuffer accumulator;

    private final ByteBuffer sink;

    private ByteBuffer rsBlock;

    /**
     * Contructor of the encoder.
     *
     * @param conventionalEncoder the conventional encoder
     * @param dualBasis true if dual basis representation is needed, false otherwise
     * @param sink the output sink
     */
    public RsEncoder(ReedSolomon conventionalEncoder, boolean dualBasis, ByteBuffer sink) {
        this.sink = sink;
        this.conventionalEncoder = conventionalEncoder;
        this.accumulator = ByteBuffer.allocate(conventionalEncoder.messageLen);
        this.dualBasis = dualBasis;
    }

    /**
     * Push a byte array into the encoder.
     *
     * @param b the data to be pushed
     */
    public void pushMessage(byte[] b) {
        if(accumulator.position() == accumulator.capacity()) {
            throw new IllegalStateException("RS message buffer full");
        }
        accumulator.put(b);
        sink.put(b);
        checkAndCompute();
    }

    /**
     * Push a byte into the encoder.
     *
     * @param b the byte to be pushed
     */
    public void pushMessage(byte b) {
        if(accumulator.position() == accumulator.capacity()) {
            throw new IllegalStateException("RS message buffer full");
        }
        accumulator.put(b);
        sink.put(b);
        checkAndCompute();
    }

    private void checkAndCompute() {
        if (accumulator.position() == accumulator.capacity()) {
            // Full: compute RS block
            computeRsBlock();
        }
    }

    /**
     * This method returns the size of the RS symbol block, if computed. If not, it throws an exception.
     *
     * @return the size in bytes of the RS block.
     */
    public int blockSize() {
        if(rsBlock == null) {
            throw new IllegalStateException("RS block buffer not computed");
        }
        return rsBlock.capacity();
    }

    /**
     * This method allows to check if the RS symbol block is available, so that other methods (pull, pullAll, blockSize)
     * can be called without raising an exception.
     *
     * @return true if the RS symbol block is available, false otherwise
     */
    public boolean ready() {
        return rsBlock != null;
    }

    /**
     * This method reads a byte from the RS symbol block and puts it into the sink buffer.
     */
    public void pull() {
        if(rsBlock == null) {
            throw new IllegalStateException("RS block buffer not computed");
        }
        if(rsBlock.position() == rsBlock.capacity()) {
            throw new IllegalStateException("RS block buffer over");
        }
        sink.put(rsBlock.get());
    }

    /**
     * This method reads all bytes from the RS symbol block and puts them into the sink buffer.
     */
    public void pullAll() {
        if(rsBlock == null) {
            throw new IllegalStateException("RS block buffer not computed");
        }
        sink.put(rsBlock.array());
    }

    // Compute the RS symbol block
    private void computeRsBlock() {
        byte[] message = accumulator.array();
        // If Berlekamp representation must be used, the message must be converted to dual basis
        // by applying the matrix defined in CCSDS 131.0-B-3, Annex F2.
        if(dualBasis) {
            for (int i = 0; i < message.length; i++) {
                int valueI = Byte.toUnsignedInt(message[i]);
                byte valueIfirst = (byte) RsCcsdsUtil.multiplyInverted(valueI);
                message[i] = valueIfirst;
            }
        }
        // Now encode using the conventional encoder
        byte[] codeword = conventionalEncoder.encode(message);
        // Nayuki's encoder outputs the bytes at the beginning of the codeword

        // If we are in Berlekamp representation, then we need to apply another transformation
        rsBlock = ByteBuffer.allocate(conventionalEncoder.eccLen);
        if(dualBasis) {
            for (int i = 0; i < conventionalEncoder.eccLen; i++) {
                rsBlock.put((byte) RsCcsdsUtil.multiplyStraight(Byte.toUnsignedInt(codeword[i])));
            }
        } else {
            rsBlock.put(codeword, 0, conventionalEncoder.eccLen);
        }
        rsBlock.flip();
        // Ready to output
    }
}
