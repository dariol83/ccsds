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

public class RsEncoder {

    private final ReedSolomon conventionalEncoder;

    private final boolean dualBasis;

    private final ByteBuffer accumulator;

    private final ByteBuffer sink;

    private ByteBuffer rsBlock;

    public RsEncoder(ReedSolomon conventionalEncoder, boolean dualBasis, ByteBuffer sink) {
        this.sink = sink;
        this.conventionalEncoder = conventionalEncoder;
        this.accumulator = ByteBuffer.allocate(conventionalEncoder.messageLen);
        this.dualBasis = dualBasis;
    }

    public void pushMessage(byte[] b) {
        if(accumulator.position() == accumulator.capacity()) {
            throw new IllegalStateException("RS message buffer full");
        }
        accumulator.put(b);
        sink.put(b);
        checkAndCompute();
    }

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

    public int blockSize() {
        if(rsBlock == null) {
            throw new IllegalStateException("RS block buffer not computed");
        }
        return rsBlock.capacity();
    }

    public boolean ready() {
        return rsBlock != null;
    }

    public void pull() {
        if(rsBlock == null) {
            throw new IllegalStateException("RS block buffer not computed");
        }
        if(rsBlock.position() == rsBlock.capacity()) {
            throw new IllegalStateException("RS block buffer over");
        }
        sink.put(rsBlock.get());
    }

    public void pullAll() {
        if(rsBlock == null) {
            throw new IllegalStateException("RS block buffer not computed");
        }
        sink.put(rsBlock.array());
    }

    public byte[] get() {
        if(rsBlock == null) {
            throw new IllegalStateException("RS block buffer not computed");
        }
        return rsBlock.array();
    }

    private void computeRsBlock() {
        byte[] message = accumulator.array();
        // If Berlekamp representation must be used, the message must be converted to dual basis
        // by applying the matrix defined in CCSDS 131.0-B-3, Annex F2.
        if(dualBasis) {
            for (int i = 0; i < message.length; i++) {
                int valueI = Byte.toUnsignedInt(message[i]);
                byte valueIfirst = (byte) RsCcsdsUtil.multiply(valueI, RsCcsdsUtil.getInvertedT());
                message[i] = valueIfirst;
            }
        }
        // Now encode using the conventional encoder
        byte[] codeword = conventionalEncoder.encode(message);
        // Nayuki's encoder outputs the bytes at the beginning of the codeword

        // If we are in Berlekamp representation, then we need to apply another transformation
        if(dualBasis) {
            rsBlock = ByteBuffer.allocate(conventionalEncoder.eccLen);
            for (int i = 0; i < conventionalEncoder.eccLen; i++) {
                rsBlock.put((byte) RsCcsdsUtil.multiply(Byte.toUnsignedInt(codeword[i]), RsCcsdsUtil.getStraightT()));
            }
            rsBlock.flip();
        } else {
            rsBlock = ByteBuffer.wrap(codeword, 0, conventionalEncoder.eccLen);
        }
        // Ready to output
    }
}
