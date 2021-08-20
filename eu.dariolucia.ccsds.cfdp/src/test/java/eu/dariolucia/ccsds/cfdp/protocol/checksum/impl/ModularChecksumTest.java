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
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ModularChecksumTest {

    @Test
    public void testModularChecksum() {
        byte[] input1 = new byte[] {0,1,2,3,4,5,6,7,8,9, (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};
        ModularChecksum checksum = new ModularChecksum();
        assertEquals(CfdpChecksumRegistry.MODULAR_CHECKSUM_TYPE, checksum.type());
        ICfdpChecksum ck1 = checksum.build();
        assertEquals(CfdpChecksumRegistry.MODULAR_CHECKSUM_TYPE, ck1.type());
        ck1.checksum(input1, 0);
        int result = ck1.getCurrentChecksum();
        assertEquals(0xD8EDA1C4, result);

        ICfdpChecksum ck2 = checksum.build();
        assertEquals(CfdpChecksumRegistry.MODULAR_CHECKSUM_TYPE, ck2.type());
        ck2.checksum(Arrays.copyOfRange(input1, 2, 7), 2);
        ck2.checksum(Arrays.copyOfRange(input1, 7, 9), 7);
        ck2.checksum(Arrays.copyOfRange(input1, 0, 2), 0);
        ck2.checksum(Arrays.copyOfRange(input1, 9, 16), 9);

        result = ck2.getCurrentChecksum();
        assertEquals(0xD8EDA1C4, result);

        ICfdpChecksum ck3 = checksum.build();
        ck3.checksum(new byte[] {1, 2, 3}, 1);
        result = ck3.getCurrentChecksum();
        assertEquals(0x00010203, result);
        assertNotNull(CfdpChecksumRegistry.getModularChecksum());
    }

    @Test
    public void testFileChecksum() throws IOException {
        InputStream in = TestUtils.class.getClassLoader().getResourceAsStream("test1.txt");
        byte[] input1 = in.readAllBytes();
        ModularChecksum checksum = new ModularChecksum();
        ICfdpChecksum ck1 = checksum.build();
        ck1.checksum(input1, 0);
        int result = ck1.getCurrentChecksum();

        assertEquals(0x7E152D02, result);
    }
}