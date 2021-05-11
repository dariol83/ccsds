package eu.dariolucia.ccsds.cfdp.protocol.checksum.impl;

import eu.dariolucia.ccsds.cfdp.protocol.checksum.CfdpChecksumRegistry;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksum;
import eu.dariolucia.ccsds.cfdp.protocol.checksum.ICfdpChecksumFactory;

import java.nio.ByteBuffer;

/**
 * The modular checksum (identified by checksum type zero) shall be calculated by the
 * following method (see annex F for an example):
 * a) the checksum shall initially be set to all 'zeroes';
 * b) it shall be calculated by modulo 2^32 addition of all 4-octet words, aligned from the
 * start of the file;
 * c) each 4-octet word shall be constructed by copying some octet of file data, whose
 * offset within the file is an integral multiple of 4 (such as 0, 4, 8, 12, etc.), into the first
 * (high-order) octet of the word and copying the next three octets of file data into the
 * next three octets of the word;
 * d) the results of the addition shall be carried into each available octet of the checksum
 * unless the addition overflows the checksum length, in which case, carry shall be
 * discarded.
 *
 * In order to include in a checksum the content of a file-data PDU whose offset
 * is not an integral multiple of 4, it is necessary to align the data properly before
 * adding 4-octet blocks of it to the checksum. Data at offset Q may be aligned
 * by inserting N octets of value 'zero' before the first octet of the data, where
 * N = Q mod 4 (the remainder obtained upon dividing Q by 4).
 *
 * In order to include in a checksum a sequence of M octets (the first of which is
 * at a file offset that is an integral multiple of 4) where M is less than 4, it is
 * necessary to pad the data to length 4 before adding it to the checksum. The
 * data may be padded by inserting (4 – M) octets of value 'zero' after the last
 * octet of the data. This condition can apply only at the end of the file.
 *
 * Ref: CCSDS 727.0-B-5, 4.2.5
 */
public class ModularChecksum implements ICfdpChecksumFactory {
    @Override
    public int type() {
        return CfdpChecksumRegistry.MODULAR_CHECKSUM_TYPE;
    }

    @Override
    public ICfdpChecksum build() {
        return new ModularChecksumComputer();
    }

    public static class ModularChecksumComputer implements ICfdpChecksum {

        private long currentChecksum = 0;

        @Override
        public int checksum(byte[] data, int offset, int len) {
            // The checksum shall initially be set to all 'zeroes'
            long accumulator = 0;
            int i = 0;
            for(; i < len; i += 4) {
                if(len - i >= 4) {
                    // The checksum shall be calculated by modulo 232 addition of all 4-octet words, aligned from the
                    // start of the file

                    // Read 4 bytes in a row as integer (unsigned and increment the accumulator)
                    long read = Integer.toUnsignedLong(ByteBuffer.wrap(data, i + offset, 4).getInt());
                    accumulator += read;
                } else {
                    // I need to pad at the end
                    byte[] tmp = new byte[] {0,0,0,0};
                    System.arraycopy(data, i, tmp, 0, len - i);
                    long read = Integer.toUnsignedLong(ByteBuffer.wrap(tmp, 0, 4).getInt());
                    accumulator += read;
                }
            }
            // the results of the addition shall be carried into each available octet of the checksum unless the
            // addition overflows the checksum length, in which case, carry shall be discarded
            accumulator &= 0xFFFFFFFF;
            return (int) accumulator;
        }

        @Override
        public int checksum(byte[] data, int fileOffset) {
            // Check the file offset: if not 4-bytes aligned, do so and just the first 4-bytes word
            int leadingZeroes = fileOffset % 4;
            if(leadingZeroes > 0) {
                // I need to pad at the beginning
                byte[] tmp = new byte[] {0,0,0,0};
                System.arraycopy(data, 0, tmp, leadingZeroes, 4 - leadingZeroes);
                long read = Integer.toUnsignedLong(ByteBuffer.wrap(tmp, 0, 4).getInt());
                currentChecksum += read;
            }
            // Then compute the checksum using the standard approach
            currentChecksum += Integer.toUnsignedLong(checksum(data, 4 - leadingZeroes, data.length - (4 - leadingZeroes)));
            currentChecksum &= 0xFFFFFFFF;
            return (int) currentChecksum;
        }

        @Override
        public int type() {
            return CfdpChecksumRegistry.NULL_CHECKSUM_TYPE;
        }
    }
}
