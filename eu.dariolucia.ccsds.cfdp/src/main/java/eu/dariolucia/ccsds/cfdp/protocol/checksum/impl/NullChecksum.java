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
