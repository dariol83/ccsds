/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.datalink.pdu;

import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TcTransferFrameTest {

    private static byte[] FIRST_FRAME = StringUtil.toByteArray("207B001717C1000102030405060708090A0B0C0D0E0F0DEF");

    @Test
    public void testTcTransferFrameDecoding() {
        TcTransferFrame tctf = new TcTransferFrame(FIRST_FRAME, (vc) -> true, true);

        assertEquals(123, tctf.getSpacecraftId());
        assertEquals(0, tctf.getVirtualChannelId());
        assertEquals(23, tctf.getVirtualChannelFrameCount());
        assertTrue(tctf.isSegmented());
        assertEquals(TcTransferFrame.SequenceFlagType.NO_SEGMENT, tctf.getSequenceFlag());
        assertTrue(tctf.isValid());
        // For coverage only
        assertNotNull(tctf.toString());
    }
}