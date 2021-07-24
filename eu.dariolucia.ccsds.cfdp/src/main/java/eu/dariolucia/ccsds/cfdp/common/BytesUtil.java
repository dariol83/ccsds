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

package eu.dariolucia.ccsds.cfdp.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Class with utility methods for byte manipulation.
 */
public class BytesUtil {

    private BytesUtil() {
        // Nothing here
    }

    /**
     * This method reads an (unsigned) natural number from an array, starting from the given offset, MSB.
     * If the size is 0, this method returns a null Long.
     * If the size is above 8 or negative, this method throws an exception.
     *
     * If you set the size equal to 8, be aware that this method will provide you with a (potentially) signed long:
     * even if this is a mistake, I doubt that someone will ever use an entity ID or transaction ID with a value larger than 2^63.
     * It that happens, this method will still work, but please don't complain if you are not considering this when your
     * transaction ID will wrap around/becomes negative, ok? :)
     *
     * To be clear before you complain about my lack of precision and professionalism:
     * if you started 1 million transactions per second (yes, 1 million), it would take ca. 292.471 YEARS before you wrap around.
     *
     * @param data the array to read from
     * @param offset the starting offset
     * @param size the length of the encoded integer in bytes
     * @return the integer (as long)
     * @throws CfdpRuntimeException if size is greater than 8 or negative
     */
    public static Long readInteger(byte[] data, int offset, int size) {
        if(size > 8) {
            throw new CfdpRuntimeException("Cannot read an unsigned integer larger than 8, actual is " + size);
        }
        if(size <= -1) {
            throw new CfdpRuntimeException("Cannot read an unsigned integer with a negative size, actual is " + size);
        }
        if(size == 0) {
            return null;
        }
        long tempAccumulator = 0;
        for(int i = 0; i < size; ++i) {
            tempAccumulator <<= 8;
            tempAccumulator |= Byte.toUnsignedLong(data[offset + i]);
        }
        return tempAccumulator;
    }

    /**
     * This method encodes an (unsigned) natural number to an array, MSB, using the provided number of octets.
     * If the size is 0, this method returns a 0-length array.
     * If the size is above 8 or negative, this method throws an exception.
     *
     * If you set the size equal to 8, be aware that this method will provide you with a (potentially) signed long:
     * even if this is a mistake, I doubt that someone will ever use an entity ID or transaction ID with a value larger than 2^63.
     * It that happens, this method will still work, but please don't complain if you are not considering this when your
     * transaction ID will wrap around/becomes negative, ok? :)
     *
     * To be clear before you complain about my lack of precision and professionalism:
     * if you started 1 million transactions per second (yes, 1 million), it would take ca. 292.471 YEARS before you wrap around.
     *
     * @param value the value to encode
     * @param size the length of the encoded integer in bytes
     * @return the array containing the encoded integer
     * @throws CfdpRuntimeException if size is greater than 8 or negative
     */
    public static byte[] encodeInteger(long value, int size) {
        if(size > 8) {
            throw new CfdpRuntimeException("Cannot encode an unsigned integer larger than 8, actual is " + size);
        }
        if(size <= -1) {
            throw new CfdpRuntimeException("Cannot encode an unsigned integer with a negative size, actual is " + size);
        }
        byte[] toReturn = new byte[size];
        long maskSelector = 0x00000000000000FF;
        for(int i = 0; i < size; ++i) {
            toReturn[size - 1 - i] = (byte) ((value & maskSelector) >>> (i * 8));
            maskSelector <<= 8;
        }
        return toReturn;
    }

    /**
     * Write a LV string to a byte buffer: it is assumed that the byte buffer can accomodate
     * the length of the string (ISO_8859_1 encoded) plus the length byte.
     *
     * @param bb the byte buffer output
     * @param toWrite the string to write
     */
    public static void writeLVString(ByteBuffer bb, String toWrite) {
        if(toWrite != null && toWrite.length() > 255) {
            throw new IllegalArgumentException("String length is greater than 255, cannot write LV string: " + toWrite);
        }
        if(toWrite != null && toWrite.length() > 0) {
            bb.put((byte) (toWrite.length() & 0xFF));
            bb.put(toWrite.getBytes(StandardCharsets.US_ASCII));
        } else {
            bb.put((byte) 0);
        }
    }

    /**
     * Read a LV string from the provided byte array starting at the specified offset.
     * It returns an empty string, in case the string length (L) is 0.
     *
     * @param data the byte array to read from
     * @param offset the offset
     * @return the decoded string (empty, 0-length string if the L value is 0x00)
     */
    public static String readLVString(byte[] data, int offset) {
        int len = Byte.toUnsignedInt(data[offset]);
        offset += 1;
        if(len > 0) {
            return new String(data, offset, len, StandardCharsets.US_ASCII);
        } else {
            return "";
        }
    }

    private static final long BYTE_1_LIMIT = (long) (Math.pow(256, 1)) - 1;
    private static final long BYTE_2_LIMIT = (long) (Math.pow(256, 2)) - 1;
    private static final long BYTE_3_LIMIT = (long) (Math.pow(256, 3)) - 1;
    private static final long BYTE_4_LIMIT = (long) (Math.pow(256, 4)) - 1;
    private static final long BYTE_5_LIMIT = (long) (Math.pow(256, 5)) - 1;
    private static final long BYTE_6_LIMIT = (long) (Math.pow(256, 6)) - 1;
    private static final long BYTE_7_LIMIT = (long) (Math.pow(256, 7)) - 1;

    /**
     * Return the minimum number of bytes that it is required to encode the provided long value.
     * Negative values always require 8 bytes.
     *
     * @param maxEntityId the number to process
     * @return the minimum number of bytes that it is required to encode the provided long value (between 0 and 8)
     */
    public static int getEncodingOctetsNb(long maxEntityId) {
        if(maxEntityId < 0) {
            return 8;
        }
        if(maxEntityId == 0) {
            return 1;
        }
        if(maxEntityId <= BYTE_1_LIMIT) {
            return 1;
        }
        if(maxEntityId <= BYTE_2_LIMIT) {
            return 2;
        }
        if(maxEntityId <= BYTE_3_LIMIT) {
            return 3;
        }
        if(maxEntityId <= BYTE_4_LIMIT) {
            return 4;
        }
        if(maxEntityId <= BYTE_5_LIMIT) {
            return 5;
        }
        if(maxEntityId <= BYTE_6_LIMIT) {
            return 6;
        }
        if(maxEntityId <= BYTE_7_LIMIT) {
            return 7;
        }
        return 8;
    }
}
