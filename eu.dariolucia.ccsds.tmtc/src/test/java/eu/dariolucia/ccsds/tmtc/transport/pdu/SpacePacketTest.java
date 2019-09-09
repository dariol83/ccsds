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

package eu.dariolucia.ccsds.tmtc.transport.pdu;

import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpacePacketTest {

    private static final String SP1_DUMP = "087BC9080071010100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";

    private static final String SP_WRONG_LEN_DUMP = "087BC9080070010100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";

    @Test
    public void testPacketDecoding() {
        SpacePacket ttf = SpacePacket.decodingBiFunction().apply(StringUtil.toByteArray(SP1_DUMP), true);

        assertEquals(2312, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(123, ttf.getApid());
        assertTrue(ttf.isTelemetryPacket());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
        assertFalse(ttf.isIdle());
        assertEquals(114, ttf.getPacketDataLength());
        assertEquals(120, ttf.getLength());
    }

    @Test
    public void testPacketDecoding2() {
        SpacePacket ttf = SpacePacket.decodingFunction().apply(StringUtil.toByteArray(SP1_DUMP));

        assertEquals(2312, ttf.getPacketSequenceCount());
        assertEquals(SpacePacket.SequenceFlagType.UNSEGMENTED, ttf.getSequenceFlag());
        assertEquals(123, ttf.getApid());
        assertTrue(ttf.isTelemetryPacket());
        assertTrue(ttf.isQualityIndicator());
        assertTrue(ttf.isSecondaryHeaderFlag());
        assertFalse(ttf.isIdle());
        assertEquals(114, ttf.getPacketDataLength());
        assertEquals(120, ttf.getLength());
    }

    @Test
    public void testPacketDecodingWrongLength() {
        try {
            SpacePacket ttf = SpacePacket.decodingFunction().apply(StringUtil.toByteArray(SP_WRONG_LEN_DUMP));
            fail("IllegalArgumentException expected");
        } catch(IllegalArgumentException e) {
            // Good
        }
    }

    @Test
    public void testAnnotatedPacket() {
        SpacePacket ttf = SpacePacket.decodingFunction().apply(StringUtil.toByteArray(SP1_DUMP));

        assertTrue(ttf.getAnnotationKeys().isEmpty());
        ttf.setAnnotationValue("x", 123);
        assertEquals(123, ttf.getAnnotationValue("x"));
        assertNull(ttf.getAnnotationValue("y"));
        ttf.setAnnotationValueIfAbsent("x", 333);
        assertEquals(123, ttf.getAnnotationValue("x"));
        assertEquals(1, ttf.getAnnotationKeys().size());
        assertTrue(ttf.isAnnotationPresent("x"));
        assertFalse(ttf.isAnnotationPresent("y"));
        ttf.clearAnnotationValue("y");
        assertTrue(ttf.isAnnotationPresent("x"));
        ttf.clearAnnotationValue("x");
        assertFalse(ttf.isAnnotationPresent("x"));
        ttf.setAnnotationValue("x", 123);
        ttf.setAnnotationValue("y", "abc");
        assertEquals(2, ttf.getAnnotationKeys().size());
        ttf.clearAnnotations();
        assertTrue(ttf.getAnnotationKeys().isEmpty());
    }
}