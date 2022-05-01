/*
 *   Copyright (c) 2022 Dario Lucia (https://www.dariolucia.eu)
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

import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class EncapsulationPacketTest {

    @Test
    public void testEncapsulationPacketDecoding() {
        {
            byte[] data = new byte[]{(byte) 0b11111111, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0, 1, 2, 3};
            EncapsulationPacket ep = new EncapsulationPacket(data, true);

            assertArrayEquals(new byte[2], ep.getCcsdsDefinedField());
            assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
            assertEquals(0, ep.getEncapsulationProtocolIdExtension());
            assertEquals(8, ep.getPrimaryHeaderLength());
            assertEquals(4, ep.getEncapsulatedDataFieldLength());
            assertEquals(0, ep.getUserDefinedField());
            assertEquals(0, ep.getEncapsulationProtocolIdExtension());
        }

        {
            byte[] data = new byte[]{(byte) 0b11111101, 0x06, 0, 1, 2, 3};
            EncapsulationPacket ep = new EncapsulationPacket(data, true);

            assertNull(ep.getCcsdsDefinedField());
            assertFalse(ep.isCcsdsDefinedFieldPresent());
            assertEquals(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC, ep.getEncapsulationProtocolId());
            assertEquals(-1, ep.getEncapsulationProtocolIdExtension());
            assertFalse(ep.isEncapsulationProtocolIdExtensionPresent());
            assertEquals(2, ep.getPrimaryHeaderLength());
            assertEquals(4, ep.getEncapsulatedDataFieldLength());
            assertEquals(-1, ep.getUserDefinedField());
            assertFalse(ep.isUserDefinedFieldPresent());
            assertFalse(ep.isIdle());
            assertTrue(ep.isQualityIndicator());
            assertArrayEquals(new byte[] {0, 1, 2, 3}, ep.getDataFieldCopy());
            assertArrayEquals(data, ep.getPacketCopy());
            assertArrayEquals(data, ep.getPacket());
            assertEquals(6, ep.getLength());

            assertNotNull(ep.toString());

            EncapsulationPacket ep2 = new EncapsulationPacket(data, true);

            assertEquals(ep2, ep);
            assertEquals(ep2.hashCode(), ep.hashCode());

            Function<byte[], EncapsulationPacket> fun = EncapsulationPacket.decodingFunction();
            EncapsulationPacket ep3 = fun.apply(data);

            assertEquals(ep3, ep);
            assertEquals(ep3.hashCode(), ep.hashCode());

            BiFunction<byte[], Boolean, EncapsulationPacket> fun2 = EncapsulationPacket.decodingBiFunction();
            EncapsulationPacket ep4 = fun2.apply(data, true);

            assertEquals(ep4, ep);
            assertEquals(ep4.hashCode(), ep.hashCode());
        }
    }

    @Test
    public void testEncapsulationPacketErrorCase() {
        assertThrows(IllegalArgumentException.class, () -> {
            byte[] data = new byte[]{(byte) 0b11111101, 0x06, 0, 1, 2, 3, 5, 6, 7};
            EncapsulationPacket ep = new EncapsulationPacket(data, true);
        });
    }

    @Test
    public void testPacketLengthParsing() {
        assertEquals(12, EncapsulationPacket.getEncapsulationPacketLength(new byte[]{(byte) 0b11111111, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0, 1, 2, 3}));
        assertEquals(8, EncapsulationPacket.getEncapsulationPacketLength(new byte[]{(byte) 0b11111110, 0x00, 0x00, 0x08, 0, 1, 2, 3}));
        assertEquals(6, EncapsulationPacket.getEncapsulationPacketLength(new byte[]{(byte) 0b11111101, 0x06, 0, 1, 2, 3}));
        assertEquals(1, EncapsulationPacket.getEncapsulationPacketLength(new byte[]{(byte) 0b11100000}));
    }
}