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

import eu.dariolucia.ccsds.tmtc.datalink.builder.TcTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TcTransferFrameBuilderTest {

    @Test
    public void testTcFrameEncoding() {
        int userDataLength = 112;
        TcTransferFrameBuilder builder = TcTransferFrameBuilder.create(false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setFrameSequenceNumber(123)
                .setBypassFlag(true)
                .setControlCommandFlag(false);

        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);

        TcTransferFrame ttf = builder.build();

        assertEquals(789, ttf.getSpacecraftId());
        assertEquals(2, ttf.getVirtualChannelId());
        assertEquals(123, ttf.getVirtualChannelFrameCount());
        assertFalse(ttf.isControlCommandFlag());
        assertTrue(ttf.isBypassFlag());
        assertFalse(ttf.isSegmented());
        assertNotNull(ttf.toString());
    }

    @Test
    public void testTcSecurityEncoding() {
        int userDataLength = 120;
        TcTransferFrameBuilder builder = TcTransferFrameBuilder.create(false)
                .setSecurity(new byte[] {1, 2, 4}, new byte[] { 9, 8, 7, 6})
                .setSpacecraftId(123)
                .setVirtualChannelId(2)
                .setFrameSequenceNumber(11)
                .setBypassFlag(true)
                .setControlCommandFlag(false);

        int residual = builder.addData(new byte[userDataLength]);
        assertEquals(0, residual);

        TcTransferFrame ttf = builder.build();

        assertEquals(123, ttf.getSpacecraftId());
        assertEquals(2, ttf.getVirtualChannelId());
        assertEquals(11, ttf.getVirtualChannelFrameCount());
        assertFalse(ttf.isControlCommandFlag());
        assertTrue(ttf.isBypassFlag());
        assertFalse(ttf.isSegmented());
        assertArrayEquals(new byte[] {1, 2, 4}, ttf.getSecurityHeaderCopy());
        assertArrayEquals(new byte[] {9, 8, 7, 6}, ttf.getSecurityTrailerCopy());
        assertNotNull(ttf.toString());
    }
}