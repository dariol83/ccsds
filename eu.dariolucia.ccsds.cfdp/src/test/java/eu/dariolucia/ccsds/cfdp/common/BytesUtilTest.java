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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BytesUtilTest {

    @Test
    public void testDecoding() {
        // Test 0, 1 byte
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0 }, 0, 1));
        // Test 0, 2 bytes
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0, 0 }, 0, 2));
        // Test 0, 3 bytes
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0, 0 , 0}, 0, 3));
        // Test 0, 4 bytes
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0}, 0, 4));
        // Test 0, 5 bytes
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0}, 0, 5));
        // Test 0, 6 bytes
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0}, 0, 6));
        // Test 0, 7 bytes
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0}, 0, 7));
        // Test 0, 8 bytes
        assertEquals(0L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0, 0}, 0, 8));

        // Test 1, 1 byte
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 1 }, 0, 1));
        // Test 1, 2 bytes
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 0, 1 }, 0, 2));
        // Test 1, 3 bytes
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 0, 0 , 1}, 0, 3));
        // Test 1, 4 bytes
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 1}, 0, 4));
        // Test 1, 5 bytes
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 1}, 0, 5));
        // Test 1, 6 bytes
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 1}, 0, 6));
        // Test 1, 7 bytes
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 1}, 0, 7));
        // Test 1, 8 bytes
        assertEquals(1L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0, 1}, 0, 8));

        // Test 21, 1 byte
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 21 }, 0, 1));
        // Test 21, 2 bytes
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 0, 21 }, 0, 2));
        // Test 21, 3 bytes
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 0, 0 , 21}, 0, 3));
        // Test 21, 4 bytes
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 21}, 0, 4));
        // Test 21, 5 bytes
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 21}, 0, 5));
        // Test 21, 6 bytes
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 21}, 0, 6));
        // Test 21, 7 bytes
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 21}, 0, 7));
        // Test 21, 8 bytes
        assertEquals(21L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0, 21}, 0, 8));

        // Test 221, 1 byte
        assertEquals(221L, BytesUtil.readInteger(new byte[] { (byte) 221 }, 0, 1));
        // Test 221, 2 bytes
        assertEquals(221L, BytesUtil.readInteger(new byte[] { 0, (byte) 221 }, 0, 2));
        // Test 221, 3 bytes
        assertEquals(221L, BytesUtil.readInteger(new byte[] { 0, 0 , (byte) 221}, 0, 3));
        // Test 221, 4 bytes
        assertEquals(221L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, (byte) 221}, 0, 4));
        // Test 221, 5 bytes
        assertEquals(221L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, (byte) 221}, 0, 5));
        // Test 221, 6 bytes
        assertEquals(221L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, (byte) 221}, 0, 6));
        // Test 221, 7 bytes
        assertEquals(221L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, (byte) 221}, 0, 7));
        // Test 221, 8 bytes
        assertEquals(221L, BytesUtil.readInteger(new byte[] { 0, 0 , 0, 0, 0, 0, 0,(byte) 221}, 0, 8));

        // Test 0x984325, 3 bytes
        assertEquals(0x00984325L, BytesUtil.readInteger(new byte[] { (byte) 0x98, (byte) 0x43, (byte) 0x25}, 0, 3));
        // Test 0x984325, 4 bytes
        assertEquals(0x00984325L, BytesUtil.readInteger(new byte[] { 0, (byte) 0x98, (byte) 0x43, (byte) 0x25}, 0, 4));
        // Test 0x984325, 5 bytes
        assertEquals(0x00984325L, BytesUtil.readInteger(new byte[] { 0, 0, (byte) 0x98, (byte) 0x43, (byte) 0x25}, 0, 5));
    }

    @Test
    public void testEncoding() {
        assertArrayEquals(new byte[] { 0 }, BytesUtil.encodeInteger(0L, 1));
        assertArrayEquals(new byte[] { 0, 0 }, BytesUtil.encodeInteger(0L, 2));
        assertArrayEquals(new byte[] { 0, 0, 0 }, BytesUtil.encodeInteger(0L, 3));
        assertArrayEquals(new byte[] { 0, 0, 0, 0 }, BytesUtil.encodeInteger(0L, 4));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0 }, BytesUtil.encodeInteger(0L, 5));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0 }, BytesUtil.encodeInteger(0L, 6));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0 }, BytesUtil.encodeInteger(0L, 7));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 }, BytesUtil.encodeInteger(0L, 8));

        assertArrayEquals(new byte[] { 1 }, BytesUtil.encodeInteger(1L, 1));
        assertArrayEquals(new byte[] { 0, 1 }, BytesUtil.encodeInteger(1L, 2));
        assertArrayEquals(new byte[] { 0, 0, 1 }, BytesUtil.encodeInteger(1L, 3));
        assertArrayEquals(new byte[] { 0, 0, 0, 1 }, BytesUtil.encodeInteger(1L, 4));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 1 }, BytesUtil.encodeInteger(1L, 5));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 1 }, BytesUtil.encodeInteger(1L, 6));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 1 }, BytesUtil.encodeInteger(1L, 7));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 1 }, BytesUtil.encodeInteger(1L, 8));

        assertArrayEquals(new byte[] { 21 }, BytesUtil.encodeInteger(21L, 1));
        assertArrayEquals(new byte[] { 0, 21 }, BytesUtil.encodeInteger(21L, 2));
        assertArrayEquals(new byte[] { 0, 0, 21 }, BytesUtil.encodeInteger(21L, 3));
        assertArrayEquals(new byte[] { 0, 0, 0, 21 }, BytesUtil.encodeInteger(21L, 4));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 21 }, BytesUtil.encodeInteger(21L, 5));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 21 }, BytesUtil.encodeInteger(21L, 6));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 21 }, BytesUtil.encodeInteger(21L, 7));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 21 }, BytesUtil.encodeInteger(21L, 8));

        assertArrayEquals(new byte[] { (byte) 0x98, (byte) 0x43, (byte) 0x25 }, BytesUtil.encodeInteger(0x00984325L, 3));
        assertArrayEquals(new byte[] { 0, (byte) 0x98, (byte) 0x43, (byte) 0x25 }, BytesUtil.encodeInteger(0x00984325L, 4));
        assertArrayEquals(new byte[] { 0, 0, (byte) 0x98, (byte) 0x43, (byte) 0x25 }, BytesUtil.encodeInteger(0x00984325L, 5));
    }

    @Test
    public void testNbBytes() {
        assertEquals(1, BytesUtil.getEncodingOctetsNb(1));
        assertEquals(1, BytesUtil.getEncodingOctetsNb(0));
        assertEquals(1, BytesUtil.getEncodingOctetsNb(2));
        assertEquals(1, BytesUtil.getEncodingOctetsNb(127));
        assertEquals(1, BytesUtil.getEncodingOctetsNb(128));
        assertEquals(1, BytesUtil.getEncodingOctetsNb(254));
        assertEquals(1, BytesUtil.getEncodingOctetsNb(255));
        assertEquals(2, BytesUtil.getEncodingOctetsNb(256));
        assertEquals(2, BytesUtil.getEncodingOctetsNb(1024));
        assertEquals(2, BytesUtil.getEncodingOctetsNb(65534));
        assertEquals(2, BytesUtil.getEncodingOctetsNb(65535));
        assertEquals(3, BytesUtil.getEncodingOctetsNb(65536));
        assertEquals(4, BytesUtil.getEncodingOctetsNb(Integer.MAX_VALUE));
        assertEquals(4, BytesUtil.getEncodingOctetsNb(Integer.MAX_VALUE * 2L - 1));
        assertEquals(4, BytesUtil.getEncodingOctetsNb(Integer.MAX_VALUE * 2L));
        assertEquals(4, BytesUtil.getEncodingOctetsNb(Integer.MAX_VALUE * 2L + 1));
        assertEquals(5, BytesUtil.getEncodingOctetsNb(Integer.MAX_VALUE * 2L + 2));
        assertEquals(6, BytesUtil.getEncodingOctetsNb(((Integer.MAX_VALUE * 2L + 2) * 256)));
        assertEquals(7, BytesUtil.getEncodingOctetsNb(((Integer.MAX_VALUE * 2L + 2) * 256 * 256)));
        assertEquals(8, BytesUtil.getEncodingOctetsNb(Long.MAX_VALUE - 1));
        assertEquals(8, BytesUtil.getEncodingOctetsNb(Long.MAX_VALUE));
    }
}