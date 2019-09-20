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

package eu.dariolucia.ccsds.encdec.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BitStringTest {

    @Test
    public void testBitStringParser() {
        {
            String str = "01001";
            BitString bs = BitString.parseBitString(str);
            assertNotNull(bs);
            assertArrayEquals(new byte[]{0x48}, bs.getData());
            assertEquals(5, bs.getLength());
        }
        {
            String str = "01001001";
            BitString bs = BitString.parseBitString(str);
            assertNotNull(bs);
            assertArrayEquals(new byte[]{0x49}, bs.getData());
            assertEquals(8, bs.getLength());
        }
        {
            String str = "101001000";
            BitString bs = BitString.parseBitString(str);
            assertNotNull(bs);
            assertArrayEquals(new byte[]{(byte)0xA4, 0x00}, bs.getData());
            assertEquals(9, bs.getLength());
            String str2 = bs.toString();
            assertEquals(str, str2);
        }
        {
            String str = "1010F1000";
            try {
                BitString bs = BitString.parseBitString(str);
                fail("Exception expected");
            } catch (BitString.BitStringFormatException e) {
                // Good
            }
        }
        {
            try {
                BitString bs = new BitString(new byte[2], 17);
                fail("Exception expected");
            } catch (IllegalArgumentException e) {
                // Good
            }
        }
    }

    @Test
    public void testBitStringHash() {
        String str = "01001";
        BitString bs = BitString.parseBitString(str);
        BitString bs2 = BitString.parseBitString(str);
        assertEquals(bs.hashCode(), bs2.hashCode());
        assertEquals(bs, bs2);
    }
}