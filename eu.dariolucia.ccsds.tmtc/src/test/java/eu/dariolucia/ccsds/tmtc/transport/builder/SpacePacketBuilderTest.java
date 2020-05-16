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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpacePacketBuilderTest {

    @Test
    public void testTmPacketEncoding() {
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

    @Test
    public void testIdlePacketEncoding() {
        int userDataLength = 300;
        SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                .setTelemetryPacket()
                .setSequenceFlag(SpacePacket.SequenceFlagType.UNSEGMENTED)
                .setIdle()
                .setPacketSequenceCount(2312)
                .setSecondaryHeaderFlag(true);

        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);
        assertFalse(builder.isFull());

        SpacePacket ttf = builder.build();

        assertEquals(2312, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(SpacePacket.SP_IDLE_APID_VALUE, ttf.getApid());
        assertTrue(ttf.isTelemetryPacket());
        assertTrue(ttf.isIdle());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
        assertEquals(userDataLength, ttf.getPacketDataLength());
        assertEquals(userDataLength + 6, ttf.getLength());
    }

    @Test
    public void testTcPacketEncoding() {
        int userDataLength = 112;
        SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                .setTelecommandPacket()
                .setSequenceFlag(SpacePacket.SequenceFlagType.UNSEGMENTED)
                .setApid(123)
                .setPacketSequenceCount(2312)
                .setSecondaryHeaderFlag(true);

        builder.addData(new byte[] { 0x01, 0x01 });
        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);
        assertEquals(SpacePacket.MAX_SPACE_PACKET_LENGTH - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 2 - userDataLength, builder.getFreeUserDataLength());
        SpacePacket ttf = builder.build();

        assertEquals(2312, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(123, ttf.getApid());
        assertFalse(ttf.isTelemetryPacket());
        assertFalse(ttf.isIdle());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
    }

    @Test
    public void testBuilderReUsing() {
        int userDataLength = 112;
        SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                .setTelecommandPacket()
                .setSequenceFlag(SpacePacket.SequenceFlagType.UNSEGMENTED)
                .setApid(123)
                .setPacketSequenceCount(2312)
                .setSecondaryHeaderFlag(true);

        builder.addData(new byte[] { 0x01, 0x01 });
        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);
        assertEquals(SpacePacket.MAX_SPACE_PACKET_LENGTH - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 2 - userDataLength, builder.getFreeUserDataLength());
        SpacePacket ttf = builder.build();

        builder.clearUserData().incrementPacketSequenceCount();
        residual = builder.addData(new byte[] { 0x05, 0x05 });
        assertEquals(0, residual);
        assertEquals(SpacePacket.MAX_SPACE_PACKET_LENGTH - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 2, builder.getFreeUserDataLength());
        ttf = builder.build();

        assertEquals(2313, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(123, ttf.getApid());
        assertFalse(ttf.isTelemetryPacket());
        assertFalse(ttf.isIdle());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
        assertArrayEquals(new byte[] { 0x05, 0x05 }, ttf.getDataFieldCopy());

        SpacePacket clone = SpacePacketBuilder.create(ttf, true, ttf.isQualityIndicator()).build();
        assertEquals(ttf, clone);
        assertEquals(ttf.hashCode(), clone.hashCode());
    }

    @Test
    public void testPacketInitialisation() {
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
        assertEquals(SpacePacket.MAX_SPACE_PACKET_LENGTH - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 2 - userDataLength, builder.getFreeUserDataLength());
        SpacePacket ttf = builder.build();

        builder = SpacePacketBuilder.create(ttf);
        builder.clearUserData().incrementPacketSequenceCount();
        residual = builder.addData(new byte[] { 0x05, 0x05 });
        assertEquals(0, residual);
        assertEquals(SpacePacket.MAX_SPACE_PACKET_LENGTH - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 2, builder.getFreeUserDataLength());
        ttf = builder.build();

        assertEquals(2313, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(123, ttf.getApid());
        assertTrue(ttf.isTelemetryPacket());
        assertFalse(ttf.isIdle());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
        assertArrayEquals(new byte[] { 0x05, 0x05 }, ttf.getDataFieldCopy());

        builder = SpacePacketBuilder.create(ttf, true, false);
        builder.incrementPacketSequenceCount();
        builder.setQualityIndicator(true);
        residual = builder.addData(new byte[] { 0x05, 0x05 });
        assertEquals(0, residual);
        assertEquals(SpacePacket.MAX_SPACE_PACKET_LENGTH - SpacePacket.SP_PRIMARY_HEADER_LENGTH - 4, builder.getFreeUserDataLength());
        ttf = builder.build();

        assertEquals(2314, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(123, ttf.getApid());
        assertTrue(ttf.isTelemetryPacket());
        assertFalse(ttf.isIdle());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
        assertArrayEquals(new byte[] { 0x05, 0x05, 0x05, 0x05 }, ttf.getDataFieldCopy());
        assertEquals(4, ttf.getPacketDataLength());
    }

    @Test
    public void testErrorCases() {
        // APID
        try {
            SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                    .setApid(-1);
            fail("IllegalArgumentException expected");
        } catch(IllegalArgumentException e) {
            // Good
        }
        try {
            SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                    .setApid(3212);
            fail("IllegalArgumentException expected");
        } catch(IllegalArgumentException e) {
            // Good
        }

        // Packet sequence counter
        try {
            SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                    .setPacketSequenceCount(-1);
            fail("IllegalArgumentException expected");
        } catch(IllegalArgumentException e) {
            // Good
        }
        try {
            SpacePacketBuilder builder = SpacePacketBuilder.create(true)
                    .setPacketSequenceCount(16432);
            fail("IllegalArgumentException expected");
        } catch(IllegalArgumentException e) {
            // Good
        }
    }
}