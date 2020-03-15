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

package eu.dariolucia.ccsds.encdec.pus;

import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class PusHeaderTest {

    @Test
    public void testTmPusHeader() {
        {
            Instant genTime = Instant.now();
            TmPusHeader h1 = new TmPusHeader((byte) 1, (short) 3, (short) 25, null, 4, genTime);
            byte[] encodedHeader = new byte[20];
            AbsoluteTimeDescriptor desc = AbsoluteTimeDescriptor.newCucDescriptor(4, 3);
            h1.encodeTo(encodedHeader, 6, 8, false, Instant.parse("2000-01-01T00:00:00.000Z"), desc, 0);
            // Check at byte level
            assertEquals((byte) 0x10, encodedHeader[6]);
            assertEquals(3, Byte.toUnsignedInt(encodedHeader[7]));
            assertEquals((byte) 25, Byte.toUnsignedInt(encodedHeader[8]));
            assertEquals(4, Byte.toUnsignedInt(encodedHeader[9]));
            // Read back
            TmPusHeader readBack = TmPusHeader.decodeFrom(encodedHeader, 6, false, 8, false, Instant.parse("2000-01-01T00:00:00.000Z"), desc);
            // Equality
            assertNotNull(h1.toString());
            assertNotNull(readBack.toString());
            assertFalse(readBack.isPacketSubCounterSet());
            assertTrue(readBack.isDestinationIdSet());
            assertTrue(readBack.isAbsoluteTimeSet());
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getPacketSubCounter(), readBack.getPacketSubCounter());
            assertEquals(h1.getDestinationId(), readBack.getDestinationId());
            assertEquals(h1.getAbsoluteTime().getEpochSecond(), readBack.getAbsoluteTime().getEpochSecond());
            assertTrue(Math.abs(h1.getAbsoluteTime().getNano() - readBack.getAbsoluteTime().getNano()) <= 60); // Delta is less than the quantisation error: 1/(2^24) of a second -> 59.6 nsec
        }
        {
            TmPusHeader h1 = new TmPusHeader((byte) 1, (short) 200, (short) 129, (short) 50, null, null);
            byte[] encodedHeader = new byte[20];
            h1.encodeTo(encodedHeader, 3, 0, false, null, null, 0);
            // Check at byte level
            assertEquals((byte) 0x10, encodedHeader[3]);
            assertEquals(200, Byte.toUnsignedInt(encodedHeader[4]));
            assertEquals(129, Byte.toUnsignedInt(encodedHeader[5]));
            assertEquals(50, Byte.toUnsignedInt(encodedHeader[6]));

            // Read back
            TmPusHeader readBack = TmPusHeader.decodeFrom(encodedHeader, 3, true, 0, false, null, null);
            // Equality
            assertNotNull(h1.toString());
            assertNotNull(readBack.toString());
            assertTrue(readBack.isPacketSubCounterSet());
            assertFalse(readBack.isDestinationIdSet());
            assertFalse(readBack.isAbsoluteTimeSet());
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getPacketSubCounter(), readBack.getPacketSubCounter());
            assertEquals(h1.getDestinationId(), readBack.getDestinationId());
            assertEquals(h1.getAbsoluteTime(), readBack.getAbsoluteTime());
        }
        {
            Instant genTime = Instant.now();
            TmPusHeader h1 = new TmPusHeader((byte) 1, (short) 3, (short) 25, null, 4, genTime);
            byte[] encodedHeader = new byte[20];
            AbsoluteTimeDescriptor desc = AbsoluteTimeDescriptor.newCucDescriptor(4, 3);
            h1.encodeTo(encodedHeader, 6, 8, true, Instant.parse("2000-01-01T00:00:00.000Z"), desc, 0);
            // Check at byte level
            assertEquals((byte) 0x10, encodedHeader[6]);
            assertEquals(3, Byte.toUnsignedInt(encodedHeader[7]));
            assertEquals((byte) 25, Byte.toUnsignedInt(encodedHeader[8]));
            assertEquals(4, Byte.toUnsignedInt(encodedHeader[9]));
            // Read back
            TmPusHeader readBack = TmPusHeader.decodeFrom(encodedHeader, 6, false, 8, true, Instant.parse("2000-01-01T00:00:00.000Z"), desc);
            // Equality
            assertNotNull(h1.toString());
            assertNotNull(readBack.toString());
            assertFalse(readBack.isPacketSubCounterSet());
            assertTrue(readBack.isDestinationIdSet());
            assertTrue(readBack.isAbsoluteTimeSet());
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getPacketSubCounter(), readBack.getPacketSubCounter());
            assertEquals(h1.getDestinationId(), readBack.getDestinationId());
            assertEquals(h1.getAbsoluteTime().getEpochSecond(), readBack.getAbsoluteTime().getEpochSecond());
            assertTrue(Math.abs(h1.getAbsoluteTime().getNano() - readBack.getAbsoluteTime().getNano()) <= 60); // Delta is less than the quantisation error: 1/(2^24) of a second -> 59.6 nsec
        }
        {
            Instant genTime = Instant.now();
            TmPusHeader h1 = new TmPusHeader((byte) 1, (short) 3, (short) 25, null, 4, genTime);
            byte[] encodedHeader = new byte[20];
            AbsoluteTimeDescriptor desc = AbsoluteTimeDescriptor.newCdsDescriptor(true, 1);
            h1.encodeTo(encodedHeader, 6, 8, true, Instant.parse("2000-01-01T00:00:00.000Z"), desc, 0);
            // Check at byte level
            assertEquals((byte) 0x10, encodedHeader[6]);
            assertEquals(3, Byte.toUnsignedInt(encodedHeader[7]));
            assertEquals((byte) 25, Byte.toUnsignedInt(encodedHeader[8]));
            assertEquals(4, Byte.toUnsignedInt(encodedHeader[9]));
            // Read back
            TmPusHeader readBack = TmPusHeader.decodeFrom(encodedHeader, 6, false, 8, true, Instant.parse("2000-01-01T00:00:00.000Z"), desc);
            // Equality
            assertNotNull(h1.toString());
            assertNotNull(readBack.toString());
            assertFalse(readBack.isPacketSubCounterSet());
            assertTrue(readBack.isDestinationIdSet());
            assertTrue(readBack.isAbsoluteTimeSet());
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getPacketSubCounter(), readBack.getPacketSubCounter());
            assertEquals(h1.getDestinationId(), readBack.getDestinationId());
            assertEquals(h1.getAbsoluteTime().getEpochSecond(), readBack.getAbsoluteTime().getEpochSecond());
            assertTrue(Math.abs(h1.getAbsoluteTime().getNano() - readBack.getAbsoluteTime().getNano()) <= 1000); // microsecond resolution
        }
        {
            Instant genTime = Instant.now();
            TmPusHeader h1 = new TmPusHeader((byte) 1, (short) 3, (short) 25, null, 4, genTime);
            byte[] encodedHeader = new byte[20];
            AbsoluteTimeDescriptor desc = AbsoluteTimeDescriptor.newCdsDescriptor(true, 1);
            h1.encodeTo(encodedHeader, 6, 8, true, Instant.parse("2000-01-01T00:00:00.000Z"), desc, 0);
            // Check at byte level
            assertEquals((byte) 0x10, encodedHeader[6]);
            assertEquals(3, Byte.toUnsignedInt(encodedHeader[7]));
            assertEquals((byte) 25, Byte.toUnsignedInt(encodedHeader[8]));
            assertEquals(4, Byte.toUnsignedInt(encodedHeader[9]));
            // Read back
            TmPusHeader readBack = TmPusHeader.decodeFrom(encodedHeader, 6, false, 8, true, Instant.parse("2000-01-01T00:00:00.000Z"), desc);
            // Equality
            assertNotNull(h1.toString());
            assertNotNull(readBack.toString());
            assertFalse(readBack.isPacketSubCounterSet());
            assertTrue(readBack.isDestinationIdSet());
            assertTrue(readBack.isAbsoluteTimeSet());
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getPacketSubCounter(), readBack.getPacketSubCounter());
            assertEquals(h1.getDestinationId(), readBack.getDestinationId());
            assertEquals(h1.getAbsoluteTime().getEpochSecond(), readBack.getAbsoluteTime().getEpochSecond());
            assertTrue(Math.abs(h1.getAbsoluteTime().getNano() - readBack.getAbsoluteTime().getNano()) <= 1000); // Microsecond resolution
        }
        {
            Instant genTime = Instant.now();
            TmPusHeader h1 = new TmPusHeader((byte) 1, (short) 3, (short) 25, null, 4, genTime);
            byte[] encodedHeader = new byte[20];
            AbsoluteTimeDescriptor desc = AbsoluteTimeDescriptor.newCdsDescriptor(true, 1);
            h1.encodeTo(encodedHeader, 6, 8, false, Instant.parse("2000-01-01T00:00:00.000Z"), desc, 0);
            // Check at byte level
            assertEquals((byte) 0x10, encodedHeader[6]);
            assertEquals(3, Byte.toUnsignedInt(encodedHeader[7]));
            assertEquals((byte) 25, Byte.toUnsignedInt(encodedHeader[8]));
            assertEquals(4, Byte.toUnsignedInt(encodedHeader[9]));
            // Read back
            TmPusHeader readBack = TmPusHeader.decodeFrom(encodedHeader, 6, false, 8, false, Instant.parse("2000-01-01T00:00:00.000Z"), desc);
            // Equality
            assertNotNull(h1.toString());
            assertNotNull(readBack.toString());
            assertFalse(readBack.isPacketSubCounterSet());
            assertTrue(readBack.isDestinationIdSet());
            assertTrue(readBack.isAbsoluteTimeSet());
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getPacketSubCounter(), readBack.getPacketSubCounter());
            assertEquals(h1.getDestinationId(), readBack.getDestinationId());
            assertEquals(h1.getAbsoluteTime().getEpochSecond(), readBack.getAbsoluteTime().getEpochSecond());
            assertTrue(Math.abs(h1.getAbsoluteTime().getNano() - readBack.getAbsoluteTime().getNano()) <= 1000); // Microsecond resolution
        }
        {
            TmPusHeader h1 = new TmPusHeader((short) 30, (short) 40, null);
            assertEquals(1, h1.getVersion());
            assertEquals(30, h1.getServiceType());
            assertEquals(40, h1.getServiceSubType());
            assertNull(h1.getPacketSubCounter());
            assertNull(h1.getDestinationId());
        }
    }

    @Test
    public void testTcPusHeader() {
        {
            TcPusHeader h1 = new TcPusHeader((byte) 1, new AckField(true, false, false, true), (short) 3, (short) 25, 13);
            byte[] encodedHeader = new byte[20];
            h1.encodeTo(encodedHeader, 6, 8, 0);
            // Check at byte level
            assertEquals((byte) 0x19, encodedHeader[6]);
            assertEquals(3, Byte.toUnsignedInt(encodedHeader[7]));
            assertEquals((byte) 25, Byte.toUnsignedInt(encodedHeader[8]));
            assertEquals(13, Byte.toUnsignedInt(encodedHeader[9]));
            // Read back
            TcPusHeader readBack = TcPusHeader.decodeFrom(encodedHeader, 6, 8);
            // Equality
            assertNotNull(h1.toString());
            assertNotNull(readBack.toString());
            assertNotNull(readBack.getAckField());
            assertNotNull(readBack.getAckField().toString());
            assertTrue(readBack.isSourceIdSet());
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getSourceId(), readBack.getSourceId());
            assertEquals(h1.getAckField(), readBack.getAckField());
            assertEquals(h1.getAckField().hashCode(), readBack.getAckField().hashCode());
        }
        {
            TcPusHeader h1 = new TcPusHeader((short) 200,(short) 200);
            byte[] encodedHeader = new byte[20];
            h1.encodeTo(encodedHeader, 0, 0, 0);
            TcPusHeader readBack = TcPusHeader.decodeFrom(encodedHeader, 0, 0);
            assertEquals(h1.getVersion(), readBack.getVersion());
            assertEquals(h1.getServiceType(), readBack.getServiceType());
            assertEquals(h1.getServiceSubType(), readBack.getServiceSubType());
            assertEquals(h1.getSourceId(), readBack.getSourceId());
            assertEquals(h1.getAckField(), readBack.getAckField());
        }
    }
}
