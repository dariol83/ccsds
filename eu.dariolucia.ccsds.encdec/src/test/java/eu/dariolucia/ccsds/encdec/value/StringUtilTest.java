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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StringUtilTest {

    private static final String DUMP1 = "001122334455AABBCCDDFFEE";
    private static final byte[] BDUMP1 = new byte[] { 00, 0x11, 0x22, 0x33, 0x44, 0x55, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xFF, (byte) 0xEE };

    @Test
    public void toByteArray() {
        assertArrayEquals(BDUMP1, StringUtil.toByteArray(DUMP1));
    }

    @Test
    public void toHexDump() {
        assertEquals(DUMP1, StringUtil.toHexDump(BDUMP1));
    }
}