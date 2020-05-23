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

package eu.dariolucia.ccsds.encdec.pus;

/**
 * This class implements the two checksum algorithms defined by the ECSS-E-70-41A for the Packet Error Control Field.
 */
public class PusChecksumUtil {

    private static int[] crcTbl;

    static {
        crcTbl = new int[256];
        int tmp;
        for (int i = 0; i < 256; i++) {
            tmp = 0;
            if ((i & 1) != 0) tmp = tmp ^ 0x1021;
            if ((i & 2) != 0) tmp = tmp ^ 0x2042;
            if ((i & 4) != 0) tmp = tmp ^ 0x4084;
            if ((i & 8) != 0) tmp = tmp ^ 0x8108;
            if ((i & 16) != 0) tmp = tmp ^ 0x1231;
            if ((i & 32) != 0) tmp = tmp ^ 0x2462;
            if ((i & 64) != 0) tmp = tmp ^ 0x48C4;
            if ((i & 128) != 0) tmp = tmp ^ 0x9188;
            crcTbl[i] = tmp;
        }
    }

    private PusChecksumUtil() {
        // Private constructor
    }

    /**
     * Compute a 16 bits CRC checksum according to ECSS-E-70-41A, section A.1.6.
     * If this method is applied on data set having the last two bytes as checksum bytes, then the data set is
     * error-free if the CRC checksum returns 0.
     *
     * @param data   the data to use
     * @param offset the start offset
     * @param length the number of bytes to process
     * @return the 16 bits CRC checksum
     */
    public static short crcChecksum(byte[] data, int offset, int length) {
        int chk = 0x0000FFFF; // syndrome to all ones
        for (int i = 0; i < length; i++) {
            chk = ((((chk << 8) & 0x0000FF00) ^ crcTbl[(((chk >> 8) ^ Byte.toUnsignedInt(data[i + offset])) & 0x000000FF)]) & 0x0000FFFF);
        }
        return (short) chk;
    }

    /**
     * Compute a 16 bits ISO checksum according to ECSS-E-70-41A, section A.2.3 and example on
     * https://en.wikipedia.org/wiki/Fletcher%27s_checksum#Fletcher-16
     *
     * @param data   the data to use
     * @param offset the start offset
     * @param length the number of bytes to process
     * @return the 16 bits ISO checksum
     */
    public static short isoChecksum(byte[] data, int offset, int length) {
        long c0 = 0;
        long c1 = 0;
        for (int i = 0; i < length; ++i) {
            c0 = (c0 + Byte.toUnsignedInt(data[i + offset]));
            c1 = (c1 + c0);
        }

        int ck1 = 255  - (int) ((c0 + c1) % 255);
        int ck2 = 255 - (int) ((c0 + ck1) % 255);
        int toReturn = 0;
        toReturn |= ck1;
        toReturn <<= 8;
        toReturn |= ck2;
        return (short) toReturn;
    }

    /**
     * Verify the 16 bits ISO checksum according to ECSS-E-70-41A, section A.2.3 and example on
     * https://en.wikipedia.org/wiki/Fletcher%27s_checksum#Fletcher-16
     *
     * @param data   the data to use
     * @param offset the start offset
     * @param length the number of bytes to process
     * @return true if the checksum is verified, otherwise false is the data is corrupted
     */
    public static boolean verifyIsoChecksum(byte[] data, int offset, int length) {
        long c0 = 0;
        long c1 = 0;
        for (int i = 0; i < length; ++i) {
            c0 = (c0 + Byte.toUnsignedInt(data[i + offset]));
            c1 = (c1 + c0);
        }
        return c0 % 255 == 0 && c1 % 255 == 0;
    }
}
