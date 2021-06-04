/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.protocol.checksum.impl;

import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksumFactory;

/**
 * The null checksum algorithm (identified by checksum type 15) shall be
 * implemented. The null checksum algorithm shall be simply to set the value of the checksum
 * to zero.
 *
 * Ref: CCSDS 727.0-B-5, 4.2.2.4
 */
public class NullChecksum implements ICfdpChecksumFactory {
    @Override
    public int type() {
        return CfdpChecksumRegistry.NULL_CHECKSUM_TYPE;
    }

    @Override
    public ICfdpChecksum build() {
        return new NullChecksumComputer();
    }

    public static class NullChecksumComputer implements ICfdpChecksum {

        @Override
        public int checksum(byte[] data, int offset, int len) {
            return 0;
        }

        @Override
        public int checksum(byte[] data, long fileOffset) {
            return 0;
        }

        @Override
        public int getCurrentChecksum() {
            return 0;
        }

        @Override
        public int type() {
            return CfdpChecksumRegistry.NULL_CHECKSUM_TYPE;
        }
    }
}
