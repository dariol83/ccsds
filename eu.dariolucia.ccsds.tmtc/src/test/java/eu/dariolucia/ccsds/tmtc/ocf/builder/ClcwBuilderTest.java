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

package eu.dariolucia.ccsds.tmtc.ocf.builder;

import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ClcwBuilderTest {

    @Test
    public void testClcwEncoding() {
        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(true)
                .setFarmBCounter(3)
                .setReportValue(137)
                .setStatusField(0)
                .setNoRfAvailableFlag(true)
                .setNoBitlockFlag(false)
                .setWaitFlag(false)
                .setLockoutFlag(true)
                .setRetransmitFlag(false)
                .setReservedSpare(0)
                .setVirtualChannelId(0)
                .build();

        assertArrayEquals(new byte[]{0x01, 0x00, (byte) 0xA6, (byte) 0x89}, clcw.getOcf());
        assertEquals(137, clcw.getReportValue());
        assertEquals(3, clcw.getFarmBCounter());
        assertEquals(0, clcw.getStatusField());
        assertTrue(clcw.isNoRfAvailableFlag());
        assertFalse(clcw.isNoBitlockFlag());
        assertFalse(clcw.isWaitFlag());
        assertTrue(clcw.isLockoutFlag());
        assertFalse(clcw.isRetransmitFlag());
        assertEquals(0, clcw.getReservedSpare());
        assertEquals(0, clcw.getVirtualChannelId());
        assertTrue(clcw.isClcw());
        assertEquals(0, clcw.getVersionNumber());
        assertNotNull(clcw.toString());

    }

    @Test
    public void testClcwNonStandardEncoding() {
        Clcw clcw = ClcwBuilder.create()
                .setCopInEffect(false)
                .setFarmBCounter(3)
                .setReservedSpare(2)
                .setReportValue(137)
                .setStatusField(0)
                .setNoRfAvailableFlag(true)
                .setNoBitlockFlag(false)
                .setWaitFlag(false)
                .setLockoutFlag(true)
                .setRetransmitFlag(false)
                .setVirtualChannelId(0)
                .build();

        assertTrue(Arrays.equals(new byte[] { 0x00, 0x02, (byte) 0xA6, (byte) 0x89 }, clcw.getOcf()));
        assertEquals(137, clcw.getReportValue());
        assertEquals(3, clcw.getFarmBCounter());
    }
}