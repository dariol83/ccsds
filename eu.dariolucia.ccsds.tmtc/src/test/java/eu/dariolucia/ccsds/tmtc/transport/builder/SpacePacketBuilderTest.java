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

package eu.dariolucia.ccsds.tmtc.transport.builder;

import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpacePacketBuilderTest {

    @Test
    public void testPacketEncoding() {
        int userDataLength = 112;
        SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                .setTelemetryPacket()
                .setSequenceFlag(SpacePacket.SequenceFlagType.UNSEGMENTED)
                .setApid(123)
                .setPacketSequenceCount(2312)
                .setSecondaryHeaderFlag(true);

        builder.addData(new byte[] { 0x01, 0x01 });
        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);

        SpacePacket ttf = builder.build();

        assertEquals(2312, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(123, ttf.getApid());
        assertTrue(ttf.isTelemetryPacket());
        assertFalse(ttf.isIdle());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
        assertEquals(114, ttf.getPacketDataLength());
        assertEquals(120, ttf.getLength());
        assertArrayEquals(ttf.getPacket(), ttf.getPacketCopy());
        assertEquals(1, ttf.getDataFieldCopy()[0]);
        assertEquals(1, ttf.getDataFieldCopy()[1]);
        assertNotNull(ttf.toString());
    }
}