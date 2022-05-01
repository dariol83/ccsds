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

package eu.dariolucia.ccsds.tmtc.transport.builder;

import eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncapsulationPacketBuilderTest {

    @Test
    public void testPacketEncoding() {
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setIdle()
                    .build();

            byte[] expected = new byte[]{(byte) 0b11100000};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacketBuilder b = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setData(new byte[]{0, 1, 2, 3});

            EncapsulationPacket packet = b.build();

            byte[] expected = new byte[]{(byte) 0b11100001, 0x06, 0, 1, 2, 3};
            assertArrayEquals(expected, packet.getPacket());

            b.setData(new byte[] {5,6,7,8,9});

            packet = b.build();

            expected = new byte[]{(byte) 0b11100001, 0x07, 5,6,7,8,9};
            assertArrayEquals(expected, packet.getPacket());

            b.clearData();

            packet = b.build();

            expected = new byte[]{(byte) 0b11100000 };
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setCcsdsDefinedField(null)
                    .setUserDefinedField(-1)
                    .setEncapsulationProtocolIdExtension(-1)
                    .setData(new byte[]{0, 1, 2, 3, 4})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11100001, 0x07, 0, 1, 2, 3, 4};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE)
                    .setLengthOfLength(2) // fixed, 4 bytes
                    .setQualityIndicator(true)
                    .setData(new byte[]{0, 1, 2, 3})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11100010, 0x00, 0x00, 0x08, 0, 1, 2, 3};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setData(new byte[]{0, 1, 2, 3})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11111101, 0x06, 0, 1, 2, 3};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC)
                    .setLengthOfLength(1) // fixed, 1 byte length
                    .setQualityIndicator(true)
                    .setData(new byte[]{0, 1, 2, 3})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11111101, 0x06, 0, 1, 2, 3};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC)
                    .setLengthOfLength(3) // fixed, 4 bytes length
                    .setQualityIndicator(true)
                    .setData(new byte[]{0, 1, 2, 3})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11111111, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0, 1, 2, 3};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setUserDefinedField(0x09)
                    .setEncapsulationProtocolIdExtension(0x00)
                    .setData(new byte[]{0, 1, 2, 3, 4})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11100010, (byte) 0x90, 0x00, 0x09, 0, 1, 2, 3, 4};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_EXTENSION)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setUserDefinedField(0x0A)
                    .setEncapsulationProtocolIdExtension(0x0E)
                    .setData(new byte[]{0, 1, 2, 3, 4})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11111010, (byte) 0xAE, 0x00, 0x09, 0, 1, 2, 3, 4};
            assertArrayEquals(expected, packet.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_EXTENSION)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setUserDefinedField(0x0A)
                    .setEncapsulationProtocolIdExtension(0x0E)
                    .setCcsdsDefinedField(new byte[] { 10, 11})
                    .setData(new byte[]{0, 1, 2, 3, 4})
                    .build();

            byte[] expected = new byte[]{(byte) 0b11111011, (byte) 0xAE, 0x0A, 0x0B, 0x00, 0x00, 0x00, 0x0D, 0, 1, 2, 3, 4};
            assertArrayEquals(expected, packet.getPacket());

            EncapsulationPacket packet2 = EncapsulationPacketBuilder.create(packet, true, true).build();
            assertArrayEquals(packet.getPacket(), packet2.getPacket());

            EncapsulationPacket packet3 = EncapsulationPacketBuilder.create(packet).build(); // no data field is copied
            expected = new byte[]{(byte) 0b11111011, (byte) 0xAE, 0x0A, 0x0B, 0x00, 0x00, 0x00, 0x08};
            assertArrayEquals(expected, packet3.getPacket());
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_EXTENSION)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setUserDefinedField(0x0A)
                    .setEncapsulationProtocolIdExtension(0x0E)
                    .setCcsdsDefinedField(null)
                    .setData(new byte[65535])
                    .build();

            assertEquals(65535 + 8, packet.getPacket().length);
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_EXTENSION)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setUserDefinedField(-1)
                    .setEncapsulationProtocolIdExtension(-1)
                    .setCcsdsDefinedField(null)
                    .setData(new byte[255])
                    .build();

            assertEquals(255 + 4, packet.getPacket().length);
        }
        {
            EncapsulationPacket packet = EncapsulationPacketBuilder.create()
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_EXTENSION)
                    .setLengthOfLength(-1) // dynamic
                    .setQualityIndicator(true)
                    .setUserDefinedField(-1)
                    .setEncapsulationProtocolIdExtension(-1)
                    .setCcsdsDefinedField(null)
                    .setData(new byte[65535])
                    .build();

            assertEquals(65535 + 8, packet.getPacket().length);
        }
    }

    @Test
    public void testErrorCases() {
        {
            assertThrows(IllegalArgumentException.class, () -> EncapsulationPacketBuilder.create().setCcsdsDefinedField(new byte[1]));
            assertThrows(IllegalArgumentException.class, () -> EncapsulationPacketBuilder.create().setCcsdsDefinedField(new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> EncapsulationPacketBuilder.create().setCcsdsDefinedField(new byte[3]));

            assertThrows(IllegalArgumentException.class, () -> EncapsulationPacketBuilder.create().setLengthOfLength(-2));
            assertThrows(IllegalArgumentException.class, () -> EncapsulationPacketBuilder.create().setLengthOfLength(4));
            assertThrows(IllegalArgumentException.class, () -> EncapsulationPacketBuilder.create().setLengthOfLength(5));

            assertThrows(IllegalStateException.class, () -> EncapsulationPacketBuilder.create().setLengthOfLength(0)
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC).build());

            assertThrows(IllegalStateException.class, () -> EncapsulationPacketBuilder.create().setLengthOfLength(1)
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC).setData(new byte[254]).build());
            assertThrows(IllegalStateException.class, () -> EncapsulationPacketBuilder.create().setLengthOfLength(2)
                    .setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_MISSION_SPECIFIC).setData(new byte[65532]).build());
        }
    }
}