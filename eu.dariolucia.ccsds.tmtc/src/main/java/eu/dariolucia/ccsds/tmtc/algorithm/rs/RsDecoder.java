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

import java.util.Arrays;

/**
 * A Reed-Solomon decoder that is used to check codewords and supports dual basis transformation as indicated in
 * CCSDS 131.0-B-3, Annex F. Internally it uses a conventional Reed-Solomon decoder: if needed, this decoder converts
 * the input symbols in dual basis representation and finalises the conversion at the output of the conventional decoder.
 */
public class RsDecoder {

    private final ReedSolomon conventionalDecoder;

    private final boolean dualBasis;

    public RsDecoder(ReedSolomon conventionalDecoder, boolean dualBasis) {
        this.conventionalDecoder = conventionalDecoder;
        this.dualBasis = dualBasis;
    }

    /**
     * This method extracts the message from the codeword and optionally checks if the codeword has errors. If checking
     * is enabled, then this method returns null in case of errors.
     *
     * @param codeword the codeword to decode
     * @return the decoded message, or null if error checking was enabled and there are errors in the codeword
     */
    public byte[] decode(byte[] codeword, boolean errorChecking) {
        // Nayuki's decoder wants to have the RS symbols before the actual message

        if (!errorChecking) {
            // If no error checking, let's optimize
            return Arrays.copyOfRange(codeword, 0, codeword.length - conventionalDecoder.eccLen);
        } else {
            // Move data in front
            byte[] copied = new byte[codeword.length];
            System.arraycopy(codeword, codeword.length - conventionalDecoder.eccLen, copied, 0, conventionalDecoder.eccLen);
            System.arraycopy(codeword, 0, copied, conventionalDecoder.eccLen, codeword.length - conventionalDecoder.eccLen);

            if (dualBasis) {
                // Apply invertedT to the codeword: this action restores the RS symbols as generated by the conventional encoder
                // and reconstruct the original input to the conventional encoder as well.
                for (int i = 0; i < copied.length; ++i) {
                    copied[i] = (byte) RsCcsdsUtil.multiplyInverted(Byte.toUnsignedInt(copied[i]));
                }
            }
            byte[] decoded = conventionalDecoder.check(copied, true);
            if (decoded != null && dualBasis) {
                // Apply straightT to the decoded message
                for (int i = 0; i < decoded.length; ++i) {
                    decoded[i] = (byte) RsCcsdsUtil.multiplyStraight(Byte.toUnsignedInt(decoded[i]));
                }
            }
            return decoded;
        }
    }
}
