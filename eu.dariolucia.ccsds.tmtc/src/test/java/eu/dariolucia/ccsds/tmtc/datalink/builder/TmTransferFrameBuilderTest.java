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

package eu.dariolucia.ccsds.tmtc.datalink.builder;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TmTransferFrameBuilderTest {

    @Test
    public void testIdleFrameEncoding() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setOcf(new byte[] { 0x00, 0x00, 0x00, 0x00 })
                .setIdle();

        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);

        TmTransferFrame ttf = builder.build();

        assertEquals(789, ttf.getSpacecraftId());
        assertEquals(2, ttf.getVirtualChannelId());
        assertEquals(34, ttf.getMasterChannelFrameCount());
        assertEquals(123, ttf.getVirtualChannelFrameCount());
        assertFalse(ttf.isPacketOrderFlag());
        assertFalse(ttf.isSecondaryHeaderPresent());
        assertFalse(ttf.isSynchronisationFlag());
        assertEquals(3, ttf.getSegmentLengthIdentifier());
        assertEquals(TmTransferFrame.TM_FIRST_HEADER_POINTER_IDLE, ttf.getFirstHeaderPointer());
        assertTrue(ttf.isIdleFrame());
        assertFalse(ttf.isNoStartPacket());
    }

    @Test
    public void testTwoPacketsFrameEncoding() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setOcf(new byte[] { 0x00, 0x00, 0x00, 0x00 });

        int residual = builder.addSpacePacket(new byte[userDataLength/2]);
        assertEquals(0, residual);
        residual = builder.addSpacePacket(new byte[userDataLength/2 + 1]);
        assertEquals(0, residual);

        TmTransferFrame ttf = builder.build();

        assertEquals(789, ttf.getSpacecraftId());
        assertEquals(2, ttf.getVirtualChannelId());
        assertEquals(34, ttf.getMasterChannelFrameCount());
        assertEquals(123, ttf.getVirtualChannelFrameCount());
        assertFalse(ttf.isPacketOrderFlag());
        assertFalse(ttf.isSecondaryHeaderPresent());
        assertFalse(ttf.isSynchronisationFlag());
        assertEquals(3, ttf.getSegmentLengthIdentifier());
        assertEquals(0, ttf.getFirstHeaderPointer());
        assertFalse(ttf.isIdleFrame());
        assertFalse(ttf.isNoStartPacket());
    }

    @Test
    public void testNoPacketsFrameEncoding() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setOcf(new byte[] { 0x00, 0x00, 0x00, 0x00 });

        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);

        TmTransferFrame ttf = builder.build();

        assertEquals(789, ttf.getSpacecraftId());
        assertEquals(2, ttf.getVirtualChannelId());
        assertEquals(34, ttf.getMasterChannelFrameCount());
        assertEquals(123, ttf.getVirtualChannelFrameCount());
        assertFalse(ttf.isPacketOrderFlag());
        assertFalse(ttf.isSecondaryHeaderPresent());
        assertFalse(ttf.isSynchronisationFlag());
        assertEquals(3, ttf.getSegmentLengthIdentifier());
        assertEquals(TmTransferFrame.TM_FIRST_HEADER_POINTER_NO_PACKET, ttf.getFirstHeaderPointer());
        assertFalse(ttf.isIdleFrame());
        assertTrue(ttf.isNoStartPacket());
    }

    @Test
    public void testSegmentedPacketsFrameEncoding() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setOcf(new byte[] { 0x00, 0x00, 0x00, 0x00 });

        int residual = builder.addData(new byte[userDataLength/2]);
        assertEquals(0, residual);
        residual = builder.addSpacePacket(new byte[userDataLength/4]);
        assertEquals(0, residual);
        residual = builder.addSpacePacket(new byte[userDataLength/2]);
        assertEquals(userDataLength/4 - 1, residual);

        TmTransferFrame ttf = builder.build();

        assertEquals(789, ttf.getSpacecraftId());
        assertEquals(2, ttf.getVirtualChannelId());
        assertEquals(34, ttf.getMasterChannelFrameCount());
        assertEquals(123, ttf.getVirtualChannelFrameCount());
        assertFalse(ttf.isPacketOrderFlag());
        assertFalse(ttf.isSecondaryHeaderPresent());
        assertFalse(ttf.isSynchronisationFlag());
        assertEquals(3, ttf.getSegmentLengthIdentifier());
        assertEquals(userDataLength/2, ttf.getFirstHeaderPointer());
        assertFalse(ttf.isIdleFrame());
        assertFalse(ttf.isNoStartPacket());
    }
}