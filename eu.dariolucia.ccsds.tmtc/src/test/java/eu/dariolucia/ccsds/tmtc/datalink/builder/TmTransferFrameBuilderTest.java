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
import static org.junit.jupiter.api.Assertions.assertFalse;

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

        try {
            ttf.getSecondaryHeaderCopy();
            fail("getSecondaryHeaderCopy exception expected");
        } catch(Exception e) {
            // Good
        }
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
                .setOcf(new byte[] { 0x01, 0x02, 0x03, 0x04 });

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
        assertArrayEquals(new byte[] { 0x01, 0x02, 0x03, 0x04 }, ttf.getOcfCopy());
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
    public void testNoPacketsNoOcfFrameEncoding() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, false, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, false, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3);

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
        assertFalse(ttf.isOcfPresent());
        assertFalse(ttf.isFecfPresent());
        assertEquals(3, ttf.getSegmentLengthIdentifier());
        assertEquals(TmTransferFrame.TM_FIRST_HEADER_POINTER_NO_PACKET, ttf.getFirstHeaderPointer());
        assertFalse(ttf.isIdleFrame());
        assertTrue(ttf.isNoStartPacket());

        try {
            ttf.getOcfCopy();
            fail("IllegalStateException expected");
        } catch(IllegalStateException e) {
            // Good
        }

        try {
            ttf.getFecf();
            fail("IllegalStateException expected");
        } catch(IllegalStateException e) {
            // Good
        }
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

    @Test
    public void testFecfFrameEncoding() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, true);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, true)
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
        assertTrue(ttf.isValid());
        assertTrue(ttf.isFecfPresent());
    }

    @Test
    public void testSecurityFrameEncoding() {
        byte[] secHeader = new byte[] { 0x01, 0x01, 0x01, 0x01 };
        byte[] secTrailer = new byte[] { 0x0F, 0x0F };
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setSecurity(secHeader, secTrailer)
                .setOcf(new byte[] { 0x00, 0x00, 0x00, 0x00 });

        // Security headers reduce the available space
        userDataLength -= secHeader.length + secTrailer.length;
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
        assertTrue(ttf.isValid());
        assertFalse(ttf.isFecfPresent());
        assertEquals(TmTransferFrame.TM_PRIMARY_HEADER_LENGTH + secHeader.length, ttf.getDataFieldStart());
        // Length of the frame, minus header length, minus OCF length, minus security header and trailer
        assertEquals(1115 - 6 - 4 - 4 - 2, ttf.getDataFieldLength());
        assertArrayEquals(secHeader, ttf.getSecurityHeaderCopy());
        assertArrayEquals(secTrailer, ttf.getSecurityTrailerCopy());
    }

    @Test
    public void testSecurityFecfFrameEncoding() {
        byte[] secHeader = new byte[] { 0x01, 0x01, 0x01, 0x01 };
        byte[] secTrailer = new byte[] { 0x0F, 0x0F };
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, true);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, true)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setSecurity(secHeader, secTrailer)
                .setOcf(new byte[] { 0x00, 0x00, 0x00, 0x00 });

        // Security headers reduce the available space
        userDataLength -= secHeader.length + secTrailer.length;
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
        assertTrue(ttf.isValid());
        assertTrue(ttf.isFecfPresent());
        assertEquals(TmTransferFrame.TM_PRIMARY_HEADER_LENGTH + secHeader.length, ttf.getDataFieldStart());
        // Length of the frame, minus header length, minus OCF length, minus FECF, minus security header and trailer
        assertEquals(1115 - 6 - 4 - 2 - 4 - 2, ttf.getDataFieldLength());
        assertArrayEquals(secHeader, ttf.getSecurityHeaderCopy());
        assertArrayEquals(secTrailer, ttf.getSecurityTrailerCopy());
        assertNotNull(ttf.toString());
        assertEquals(4, ttf.getSecurityHeaderLength());
        assertEquals(2, ttf.getSecurityTrailerLength());
        assertTrue(ttf.isSecurityUsed());
    }

    @Test
    public void testSecondaryHeaderFrameEncoding() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 5, true, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 5, true, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setOcf(new byte[] { 0x01, 0x02, 0x03, 0x04 })
                .setSecondaryHeader(new byte[] { (byte) 0xFF, (byte) 0xFA, 0x11, 0x14, 0x76});

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
        assertTrue(ttf.isSecondaryHeaderPresent());
        assertFalse(ttf.isSynchronisationFlag());
        assertEquals(3, ttf.getSegmentLengthIdentifier());
        assertEquals(0, ttf.getFirstHeaderPointer());
        assertFalse(ttf.isIdleFrame());
        assertFalse(ttf.isNoStartPacket());
        assertArrayEquals(new byte[] { 0x01, 0x02, 0x03, 0x04 }, ttf.getOcfCopy());
        assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xFA, 0x11, 0x14, 0x76}, ttf.getSecondaryHeaderCopy());
    }
}