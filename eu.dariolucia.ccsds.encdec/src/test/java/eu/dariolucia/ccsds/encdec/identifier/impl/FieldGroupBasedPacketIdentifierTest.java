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

package eu.dariolucia.ccsds.encdec.identifier.impl;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.definition.IdentField;
import eu.dariolucia.ccsds.encdec.definition.IdentFieldMatcher;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.identifier.IPacketIdentifier;
import eu.dariolucia.ccsds.encdec.identifier.PacketAmbiguityException;
import eu.dariolucia.ccsds.encdec.identifier.PacketNotIdentifiedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FieldGroupBasedPacketIdentifierTest {

    @Test
    public void testPerformanceBehaviour() throws PacketNotIdentifiedException, PacketAmbiguityException, InterruptedException {
        // Create a lot of definitions
        Definition d = new Definition();

        final IdentField apid = new IdentField("APID", 0, 2, 0b0000011111111111, 0, 0, 0);

        final IdentField t = new IdentField("PUS Type", 7, 1);
        final IdentField s = new IdentField("PUS Subtype", 8, 1);
        final IdentField p1 = new IdentField("P1", 10, 1);
        final IdentField p2 = new IdentField("P2", 11, 1);
        final IdentField p3 = new IdentField("P3", 14, 3);

        d.getIdentificationFields().add(apid);
        d.getIdentificationFields().add(t);
        d.getIdentificationFields().add(s);
        d.getIdentificationFields().add(p1);
        d.getIdentificationFields().add(p2);
        d.getIdentificationFields().add(p3);

        // Generate 1000 APIDs (from 100 to 1100): for each, generated packets <t,s> (3-4, 1-20)
        for (int i = 100; i <= 1100; ++i) {
            for (int j = 3; j <= 4; ++j) {
                for (int k = 1; k <= 20; ++k) {
                    d.getPacketDefinitions().add(new PacketDefinition(
                            "A" + i + "T" + j + "S" + k,
                            new IdentFieldMatcher(apid, i),
                            new IdentFieldMatcher(t, j),
                            new IdentFieldMatcher(s, k)
                    ));
                }
            }
        }

        // Generated packets <t,s> (5-8, 1-5)
        for (int j = 5; j <= 8; ++j) {
            for (int k = 1; k <= 5; ++k) {
                d.getPacketDefinitions().add(new PacketDefinition(
                        "T" + j + "S" + k,
                        new IdentFieldMatcher(t, j),
                        new IdentFieldMatcher(s, k)
                ));
            }
        }

        // Generate 300 APIDs (from 100 to 400): for each, generated packets <t,s, p1> (10, 1-20, 1-5)
        for (int i = 100; i <= 400; ++i) {
            for (int j = 10; j <= 10; ++j) {
                for (int k = 1; k <= 20; ++k) {
                    for (int z = 1; z <= 5; ++z) {
                        d.getPacketDefinitions().add(new PacketDefinition(
                                "A" + i + "T" + j + "S" + k + "P1" + z,
                                new IdentFieldMatcher(apid, i),
                                new IdentFieldMatcher(t, j),
                                new IdentFieldMatcher(s, k),
                                new IdentFieldMatcher(p1, z)
                        ));
                    }
                }
            }
        }

        // Generated packets P3
        for (int k = 15000; k <= 17000; ++k) {
            d.getPacketDefinitions().add(new PacketDefinition(
                    "P3" + k,
                    new IdentFieldMatcher(p3, k)
            ));
        }

        System.out.println("Total definitions: " + d.getPacketDefinitions().size());

        long snap = System.currentTimeMillis();
        Runtime.getRuntime().gc();
        long freeMem = Runtime.getRuntime().freeMemory();
        // Lets start: create the identifier
        IPacketIdentifier identifier = new FieldGroupBasedPacketIdentifier(d);
        Runtime.getRuntime().gc();
        Thread.sleep(100);
        Runtime.getRuntime().gc();
        System.out.println("Identifier memory size: " + (freeMem - Runtime.getRuntime().freeMemory()) + " bytes");
        System.out.println("Identifier loading time: " + (System.currentTimeMillis() - snap) + " ms");

        // Create a few packets and put them in a list
        List<byte[]> packets = new LinkedList<>();
        packets.add(createPacket(303, 3, 19));
        packets.add(createPacket(304, 3, 19));
        packets.add(createPacket(305, 3, 19));
        packets.add(createPacket(306, 3, 19));
        packets.add(createPacket(307, 3, 19));
        packets.add(createPacket(303, 10, 4, 2));
        packets.add(createPacket(303, 10, 4, 4));
        packets.add(createPacket(303, 10, 1, 2));
        packets.add(createPacket(203, 10, 4, 2));
        packets.add(createPacket(203, 10, 4, 4));
        packets.add(createPacket(203, 10, 1, 2));
        packets.add(createPacket(5, 1));
        packets.add(createPacket(5, 3));
        packets.add(createPacket(6, 2));
        packets.add(createPacket(16621));
        packets.add(createPacket(16535));
        packets.add(createPacket(16536));

        snap = System.currentTimeMillis();
        Runtime.getRuntime().gc();
        Thread.sleep(100);
        Runtime.getRuntime().gc();
        freeMem = Runtime.getRuntime().freeMemory();

        boolean goOn = true;

        int recognized = 0;

        while(goOn) {
            for(byte[] pkt : packets) {
                String identified = identifier.identify(pkt);
                assertNotNull(identified);
                ++recognized;
            }
            long elapsed = System.currentTimeMillis() - snap;
            if(elapsed > 10000) {
                goOn = false;
            }
        }

        long runningTime = System.currentTimeMillis() - snap;
        Runtime.getRuntime().gc();
        Thread.sleep(100);
        Runtime.getRuntime().gc();
        long occupiedMem = freeMem - Runtime.getRuntime().freeMemory();
        System.out.println("Identifier running time for " + recognized + " packet identifications: " + runningTime + " ms");
        System.out.println("Identifier average identification rate: " + ((double) recognized/ ((double) runningTime / 1000.0)) + " packets per second");
        System.out.println("Identifier memory size: " + occupiedMem + " bytes");
    }

    @Test
    public void testRecognitionBehaviour() throws PacketNotIdentifiedException, PacketAmbiguityException, IOException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions1.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        // Lets start: create the identifier
        IPacketIdentifier identifier = new FieldGroupBasedPacketIdentifier(d);

        // Create packets and check with the corresponding definitions
        {
            byte[] p = createPacket(303, 3, 25);
            assertEquals("DEF4", identifier.identify(p));
        }

        {
            byte[] p = createPacket(301, 3, 25);
            assertEquals("DEF2", identifier.identify(p));
        }

        try {
            byte[] p = createPacket(304, 3, 25);
            String id = identifier.identify(p);
            fail("PacketNotIdentifiedException expected");
        } catch (PacketNotIdentifiedException e) {
            assertNotNull(e.getPacket());
            assertArrayEquals(createPacket(304, 3, 25), e.getPacket());
            // Good
        }

        {
            byte[] p = createPacket(304, 3, 25, 2);
            assertEquals("DEF6", identifier.identify(p));
        }

        {
            byte[] p = createPacket(304, 3, 25, 1);
            assertEquals("DEF5", identifier.identify(p));
        }

        {
            byte[] p = createPacket(1, 3);
            assertEquals("DEF9", identifier.identify(p));
        }

        try {
            byte[] p = createPacket(1, 7);
            String id = identifier.identify(p);
            fail("PacketNotIdentifiedException expected");
        } catch (PacketNotIdentifiedException e) {
            // Good
        }

        {
            byte[] p = createPacket(45923);
            assertEquals("DEF11", identifier.identify(p));
        }

        try {
            byte[] p = createPacket(45922);
            String id = identifier.identify(p);
            fail("PacketNotIdentifiedException expected");
        } catch (PacketNotIdentifiedException e) {
            // Good
        }
    }

    @Test
    public void testRecognitionAmbiguityBehaviour() throws PacketNotIdentifiedException, PacketAmbiguityException, IOException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions3.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        // Lets start: create the identifier
        IPacketIdentifier identifier = new FieldGroupBasedPacketIdentifier(d);

        // Create packets and check with the corresponding definitions
        {
            byte[] p = createPacket(301, 3, 25);
            assertEquals("DEF2", identifier.identify(p));
        }

        {
            byte[] p = createPacket(300, 3, 25, 2);
            assertEquals("DEF3", identifier.identify(p));
        }

        {
            byte[] p = createPacket(300, 3, 25);
            assertEquals("DEF1", identifier.identify(p));
        }

        // Create another decoder with ambiguity detection
        identifier = new FieldGroupBasedPacketIdentifier(d, true);

        // Create packets and check with the corresponding definitions
        {
            byte[] p = createPacket(301, 3, 25);
            assertEquals("DEF2", identifier.identify(p));
        }

        {
            try {
                byte[] p = createPacket(300, 3, 25, 2);
                identifier.identify(p);
                fail("Expected ambiguity detection triggered");
            } catch (PacketAmbiguityException e) {
                // Good
            }
        }

        {
            byte[] p = createPacket(300, 3, 25);
            assertEquals("DEF1", identifier.identify(p));
        }
    }

    private byte[] createPacket(int a, int t, int s) {
        short apid = 0;
        apid |= (a & 0x07FF);
        ByteBuffer bb = ByteBuffer.allocate(20);
        bb.putShort(apid); // 0-1
        bb.putInt(0x0); // 2-3-4-5
        bb.put((byte) 0x01); // 6 OBT like
        bb.put((byte) t); // 7
        bb.put((byte) s); // 8
        bb.put((byte) 0x00); // 9 Random field
        bb.put((byte) 0x00); // 10 P1
        bb.put((byte) 0x00); // 11 P2
        bb.put((byte) 0x00); // 12 Random field
        bb.put((byte) 0x00); // 13 Random field
        bb.put((byte) 0x00); // 14 P3 MSB (8)
        bb.putShort((short) 0x00); // 15-16 P3 LSB (16)
        bb.put((byte) 0x00); // 17 Random field
        bb.put((byte) 0x00); // 18 Random field
        bb.put((byte) 0x00); // 19 Random field

        return bb.array();
    }

    private byte[] createPacket(int a, int t, int s, int p1) {
        short apid = 0;
        apid |= (a & 0x07FF);
        ByteBuffer bb = ByteBuffer.allocate(20);
        bb.putShort(apid); // 0-1
        bb.putInt(0x0); // 2-3-4-5
        bb.put((byte) 0x01); // 6 OBT like
        bb.put((byte) t); // 7
        bb.put((byte) s); // 8
        bb.put((byte) 0x00); // 9 Random field
        bb.put((byte) p1); // 10 P1
        bb.put((byte) 0x00); // 11 P2
        bb.put((byte) 0x00); // 12 Random field
        bb.put((byte) 0x00); // 13 Random field
        bb.put((byte) 0x00); // 14 P3 MSB (8)
        bb.putShort((short) 0x00); // 15-16 P3 LSB (16)
        bb.put((byte) 0x00); // 17 Random field
        bb.put((byte) 0x00); // 18 Random field
        bb.put((byte) 0x00); // 19 Random field

        return bb.array();
    }

    private byte[] createPacket(int t, int s) {
        ByteBuffer bb = ByteBuffer.allocate(20);
        bb.putShort((short) 500); // 0-1
        bb.putInt(0x0); // 2-3-4-5
        bb.put((byte) 0x01); // 6 OBT like
        bb.put((byte) t); // 7
        bb.put((byte) s); // 8
        bb.put((byte) 0x00); // 9 Random field
        bb.put((byte) 0x01); // 10 P1
        bb.put((byte) 0x00); // 11 P2
        bb.put((byte) 0x00); // 12 Random field
        bb.put((byte) 0x00); // 13 Random field
        bb.put((byte) 0x00); // 14 P3 MSB (8)
        bb.putShort((short) 0x00); // 15-16 P3 LSB (16)
        bb.put((byte) 0x00); // 17 Random field
        bb.put((byte) 0x00); // 18 Random field
        bb.put((byte) 0x00); // 19 Random field

        return bb.array();
    }

    private byte[] createPacket(int p3) {
        ByteBuffer bb = ByteBuffer.allocate(20);
        bb.putShort((short) 500); // 0-1
        bb.putInt(0x0); // 2-3-4-5
        bb.put((byte) 0x01); // 6 OBT like
        bb.put((byte) 4); // 7
        bb.put((byte) 9); // 8
        bb.put((byte) 0x00); // 9 Random field
        bb.put((byte) 0x01); // 10 P1
        bb.put((byte) 0x00); // 11 P2
        bb.put((byte) 0x00); // 12 Random field
        bb.put((byte) 0x00); // 13 Random field
        byte msb = (byte) (p3 >> 16);
        bb.put((byte) 0x00); // 14 P3 MSB (8)
        bb.putShort((short) (p3 & 0x0000FFFF)); // 15-16 P3 LSB (16)
        bb.put((byte) 0x00); // 17 Random field
        bb.put((byte) 0x00); // 18 Random field
        bb.put((byte) 0x00); // 19 Random field

        return bb.array();
    }
}