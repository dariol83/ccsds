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

package eu.dariolucia.ccsds.encdec.structure.impl;

import eu.dariolucia.ccsds.encdec.bit.BitEncoderDecoder;
import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.resolvers.DefaultNullBasedResolver;
import eu.dariolucia.ccsds.encdec.structure.resolvers.DefinitionValueBasedResolver;
import eu.dariolucia.ccsds.encdec.structure.resolvers.IdentificationFieldBasedResolver;
import eu.dariolucia.ccsds.encdec.structure.resolvers.PathLocationBasedResolver;
import eu.dariolucia.ccsds.encdec.value.BitString;
import eu.dariolucia.ccsds.encdec.value.MilUtil;
import eu.dariolucia.ccsds.encdec.value.TimeUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPacketEncoderTest {

    @Test
    public void testDefinition1() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF1.PARAM1", 2);
        map.put("DEF1.PARAM2", 124.25f);
        map.put("DEF1.PARAM3", 61);
        map.put("DEF1.PARAM4", true);
        map.put("DEF1.PARAM5", false);
        map.put("DEF1.PARAM6", new BitString(new byte[] {0x05, 0x51}, 13));
        map.put("DEF1.PARAM7", new byte[] {0x23, 0x12, (byte) 0x92});
        map.put("DEF1.PARAM8", "Hello01");
        map.put("DEF1.PARAM9", true);
        map.put("DEF1.PARAM10", Instant.ofEpochSecond(123456789, 123456789));
        map.put("DEF1.PARAM11", Duration.ofSeconds(1234, 91156789));
        map.put("DEF1.PARAM12", 7);
        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new PathLocationBasedResolver(map));
        assertEquals(26, encoded.length);

        // The final byte array should be the following
        byte[] expected = new byte[] {
                0b01001000, // 3 bits int + float
                0b01011111, // float
                0b00010000, // float
                0b00000000, // float
                0b00011110, // float + 6 bits (5) uint
                (byte) 0b11000000, // 6 bits (1) unit + bool + bool + bitstring
                (byte) 0b10101010, // bitstring
                0b00100011, // byte string
                0b00010010, // byte string
                (byte) 0b10010010, // byte string
                0x48, // char string
                0x65, // char string
                0x6c, // char string
                0x6c, // char string
                0x6f, // char string
                0x30, // char string
                0x31, // char string
                (byte) 0b10001011, // absolute time
                0b01011001, // absolute time
                (byte) 0b10000010, // absolute time
                0b01001111, // absolute time
                (byte) 0b11110101, // absolute time
                0b01000001, // absolute time
                (byte) 0b11101001, // boolean bit + bit at idx 1 and the next 15 bits are the duration
                0b00001011, // duration
                (byte) 0b11110000 // duration + uint 3 bits + padding
        };

        assertArrayEquals(expected, encoded);
    }

    @Test
    public void testDefinition2() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF2.PARAM1", 1);
        map.put("DEF2.PARAM2", 3);
        map.put("DEF2.PARAM3", 7);
        map.put("DEF2.PARAM4", 432.345633);
        map.put("DEF2.PARAM5", 1.234);
        map.put("DEF2.PARAM6", 432.345633);
        map.put("DEF2.PARAM7", Instant.ofEpochSecond(123456789, 123456000));
        map.put("DEF2.PARAM8", Instant.ofEpochSecond(123456789, 123000000));

        // Now we can encode
        byte[] encoded = encoder.encode("DEF2", new PathLocationBasedResolver(map));
        assertEquals(34, encoded.length);

        // Check the encoded
        ByteBuffer bb = ByteBuffer.wrap(encoded);
        byte first3params = bb.get();
        assertEquals(0b01011111, first3params);
        double dd = bb.getDouble();
        assertEquals(432.345633, dd);
        long data = Integer.toUnsignedLong(bb.getInt());
        double val = MilUtil.fromMil32Real(data);
        assertEquals(1.234, val, 0.00001);
        long mil48val = Integer.toUnsignedLong(bb.getInt());
        mil48val <<= 16;
        mil48val |= Short.toUnsignedLong(bb.getShort());
        assertEquals(432.345633, MilUtil.fromMil48Real(mil48val), 0.00001);
        byte[] firstTime = new byte[8];
        bb.get(firstTime);
        Instant t1 = TimeUtil.fromCDS(firstTime, null, true, 1);
        assertEquals(Instant.ofEpochSecond(123456789, 123456000), t1);
        byte[] secondTime = new byte[7];
        bb.get(secondTime);
        Instant t2 = TimeUtil.fromCUC(secondTime, null, 4,3);
        assertEquals(Instant.ofEpochSecond(123456789, 123000000).getEpochSecond(), t2.getEpochSecond());
        assertTrue(Math.abs(Instant.ofEpochSecond(123456789, 123000000).getNano() - t2.getNano()) < 100);
    }

    @Test
    public void testDefinition3() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF3.PARAM1", 3);
        map.put("DEF3.ARRAY1#0.PARAM_A1", 1);
        map.put("DEF3.ARRAY1#0.PARAM_A2", 2);
        map.put("DEF3.ARRAY1#0.PARAM_A3", true);
        map.put("DEF3.ARRAY1#1.PARAM_A1", 3);
        map.put("DEF3.ARRAY1#1.PARAM_A2", 4);
        map.put("DEF3.ARRAY1#1.PARAM_A3", false);
        map.put("DEF3.ARRAY1#2.PARAM_A1", 5);
        map.put("DEF3.ARRAY1#2.PARAM_A2", -3);
        map.put("DEF3.ARRAY1#2.PARAM_A3", true);
        map.put("DEF3.PARAM2", 7);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF3", new PathLocationBasedResolver(map));
        assertEquals(5, encoded.length);

        // Check the encoded
        // The final byte array should be the following
        byte[] expected = new byte[] {
                0b01100001,
                0b00010100,
                0b01100100,
                0b00010111,
                (byte) 0b10110111
        };

        assertArrayEquals(expected, encoded);
    }

    @Test
    public void testDefinition4() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF4.PARAM1", 3);
        map.put("DEF4.STRUCT1.PARAM_A1", 1);
        map.put("DEF4.STRUCT1.PARAM_A2", 2);
        map.put("DEF4.STRUCT1.PARAM_A3", true);
        map.put("DEF4.PARAM2", 1);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF4", new PathLocationBasedResolver(map));
        assertEquals(2, encoded.length);

        // Check the encoded
        // The final byte array should be the following
        byte[] expected = new byte[] {
                0b01100001,
                0b00010101
        };

        assertArrayEquals(expected, encoded);
    }

    @Test
    public void testDefinition5() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF5.PARAM1", 3);
        map.put("DEF5.ARRAY1#0.PARAM_A1", 2);
        map.put("DEF5.ARRAY1#0.ARRAY2#0.PARAM_AA1", 2);
        map.put("DEF5.ARRAY1#0.ARRAY2#0.PARAM_AA2", 3);
        map.put("DEF5.ARRAY1#0.ARRAY2#1.PARAM_AA1", 5);
        map.put("DEF5.ARRAY1#0.ARRAY2#1.PARAM_AA2", 6);
        map.put("DEF5.ARRAY1#1.PARAM_A1", 3);
        map.put("DEF5.ARRAY1#1.ARRAY2#0.PARAM_AA1", 2);
        map.put("DEF5.ARRAY1#1.ARRAY2#0.PARAM_AA2", 3);
        map.put("DEF5.ARRAY1#1.ARRAY2#1.PARAM_AA1", 5);
        map.put("DEF5.ARRAY1#1.ARRAY2#1.PARAM_AA2", 6);
        map.put("DEF5.ARRAY1#1.ARRAY2#2.PARAM_AA1", 5);
        map.put("DEF5.ARRAY1#1.ARRAY2#2.PARAM_AA2", 6);
        map.put("DEF5.PARAM2", 7);
        map.put("DEF5.PARAM3", true);
        map.put("DEF5.PARAM4", false);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF5", new PathLocationBasedResolver(map));
        assertEquals(9, encoded.length);

        // Check the encoded
        // The final byte array should be the following
        byte[] expected = new byte[] {
                0b00011000,
                (byte) 0b10000100,
                0b00110010,
                (byte) 0b10011000,
                0b01100010,
                0b00011001,
                0b01001100,
                0b01010011,
                0b00011110
        };

        assertArrayEquals(expected, encoded);
    }


    @Test
    public void testDefinition6() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF6.ARRAY1#0.STRUCT1.ARRAY2#0.PARAM_AA1", 2);
        map.put("DEF6.ARRAY1#0.STRUCT1.ARRAY2#1.PARAM_AA1", 2);
        map.put("DEF6.ARRAY1#0.STRUCT1.STRUCT2.PARAM_S2", 4);
        map.put("DEF6.ARRAY1#1.STRUCT1.ARRAY2#0.PARAM_AA1", 3);
        map.put("DEF6.ARRAY1#1.STRUCT1.ARRAY2#1.PARAM_AA1", 3);
        map.put("DEF6.ARRAY1#1.STRUCT1.STRUCT2.PARAM_S2", 6);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF6", new PathLocationBasedResolver(map));
        assertEquals(4, encoded.length);

        // Check the encoded
        // The final byte array should be the following
        byte[] expected = new byte[] {
                0b00010000,
                (byte) 0b10000100,
                0b00011000,
                (byte) 0b11000110
        };

        assertArrayEquals(expected, encoded);
    }

    @Test
    public void testDefinition7() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        char[] tmpArr = new char[59];
        Arrays.fill(tmpArr, ' ');
        String strVal = new String(tmpArr);

        Map<String, Object> map = new TreeMap<>();
        map.put("DEF7.PARAM1", 8);
        map.put("DEF7.PARAM2", 59);
        map.put("DEF7.PARAM3", strVal);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF7", new PathLocationBasedResolver(map));
        assertEquals(61, encoded.length);

        // Check the encoded
        assertEquals(0b01000111, encoded[0]);
        assertEquals(0b01100100, encoded[1]);
    }


    @Test
    public void testDefinition8() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF8.PARAM1", 3);
        map.put("DEF8.PARAM2", 1);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF8", new PathLocationBasedResolver(map));
        assertEquals(1, encoded.length);

        // Check the encoded
        assertEquals(0b00011001, encoded[0]);
    }

    @Test
    public void testDefinition9() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF9.PARAM1", 3);
        map.put("DEF9.PARAM2", 1);
        map.put("DEF9.PARAM3", 4);
        map.put("DEF9.PARAM4", 7);
        map.put("DEF9.PARAM5", 5);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF9", new PathLocationBasedResolver(map));
        assertEquals(5, encoded.length);

        // Check the encoded
        assertEquals(0b00011001, encoded[0]);
        assertEquals(0b01000000, encoded[1]);
        assertEquals(0b00000010, encoded[2]);
        assertEquals(0b00010000, encoded[3]);
        assertEquals(0b00000111, encoded[4]);
    }

    @Test
    public void testDefinition10() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF10.PARAM1", 3);
        map.put("DEF10.ARRAY1#0.PARAM_A1", 1234);
        map.put("DEF10.ARRAY1#0.PARAM_A2", 73);
        map.put("DEF10.ARRAY1#0.PARAM_A3", 74);
        map.put("DEF10.ARRAY1#1.PARAM_A1", 1235);
        map.put("DEF10.ARRAY1#1.PARAM_A2", 1);
        map.put("DEF10.ARRAY1#1.PARAM_A3", 2);
        map.put("DEF10.ARRAY1#2.PARAM_A1", 1236);
        map.put("DEF10.ARRAY1#2.PARAM_A2", 78.4f);
        map.put("DEF10.ARRAY1#2.PARAM_A3", 123.232);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF10", new PathLocationBasedResolver(map));
        assertEquals(16, encoded.length);

        // Check the encoded
        BitEncoderDecoder bed = new BitEncoderDecoder(encoded);
        assertEquals(3, bed.getNextIntegerSigned(5));
        assertEquals(1234, bed.getNextIntegerUnsigned(12));
        assertEquals(73, bed.getNextIntegerUnsigned(7));
        assertEquals(74, bed.getNextIntegerUnsigned(7));
        assertEquals(1235, bed.getNextIntegerUnsigned(12));
        assertEquals(1, bed.getNextIntegerSigned(3));
        assertEquals(2, bed.getNextIntegerSigned(3));
        assertEquals(1236, bed.getNextIntegerUnsigned(12));
        assertEquals(78.4, bed.getNextFloat(), 0.01);
        assertEquals(123.232, bed.getNextFloat(), 0.0001);
    }

    @Test
    public void testDefinition11() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF11.PARAM1", Instant.ofEpochSecond(123456789, 12332789));
        map.put("DEF11.PARAM2",Duration.ofSeconds(1234, 91156789));
        // Now we can encode
        byte[] encoded = encoder.encode("DEF11", new PathLocationBasedResolver(map));
        assertEquals(16, encoded.length);

        Instant t1 = TimeUtil.fromCUC(Arrays.copyOfRange(encoded, 0, 8));
        Duration d1 = TimeUtil.fromCUCduration(Arrays.copyOfRange(encoded, 8, 16));
        assertEquals(123456789, t1.getEpochSecond());
        assertTrue(Math.abs(12332789 - t1.getNano()) < 100);

        assertEquals(1234, d1.getSeconds());
        assertTrue(Math.abs(91156789 - d1.getNano()) < 100);
    }

    @Test
    public void testDefinition12() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF12.PARAM1", 2);
        map.put("DEF12.PARAM2", 124.25f);
        map.put("DEF12.PARAM3", true);
        map.put("DEF12.PARAM4", -4);
        // Now we can encode
        byte[] encoded = encoder.encode("DEF12", new PathLocationBasedResolver(map));
        assertEquals(7, encoded.length);

        // The final byte array should be the following
        byte[] expected = new byte[] {
                0b01000000, // int
                0b01000010, // float
                (byte) 0b11111000, // float
                (byte) 0b10000000, // float
                0b00000000, // float
                0b00000000,
                (byte) 0b10111100
        };

        assertArrayEquals(expected, encoded);
    }

    @Test
    public void testDefinition13() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions6.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new DefinitionValueBasedResolver(new DefaultNullBasedResolver(), false));
        assertEquals(26, encoded.length);

        // The final byte array should be the following
        byte[] expected = new byte[] {
                0b01001000, // 3 bits int + float
                0b01011111, // float
                0b00010000, // float
                0b00000000, // float
                0b00011110, // float + 6 bits (5) uint
                (byte) 0b11000000, // 6 bits (1) unit + bool + bool + bitstring
                (byte) 0b10101010, // bitstring
                0b00100011, // byte string
                0b00010010, // byte string
                (byte) 0b10010010, // byte string
                0x48, // char string
                0x65, // char string
                0x6c, // char string
                0x6c, // char string
                0x6f, // char string
                0x30, // char string
                0x31, // char string
                (byte) 0b10001011, // absolute time
                0b01011001, // absolute time
                (byte) 0b10000010, // absolute time
                0b01001111, // absolute time
                (byte) 0b11110101, // absolute time
                0b01000001, // absolute time
                (byte) 0b11101001, // boolean bit + bit at idx 1 and the next 15 bits are the duration
                0b00001011, // duration
                (byte) 0b11110000 // duration + uint 3 bits + padding
        };

        // Exclude the fractionary part of the absolute time due to inhability of the parse method to read
        // the full resolution of the time
        assertArrayEquals(Arrays.copyOfRange(expected, 0, 20), Arrays.copyOfRange(encoded, 0, 20));
    }

    @Test
    public void testDefinitionWithIdentifierResolver() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions7.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF1.ACK_FIELD", 0);
        map.put("DEF1.PARAM1", 2);
        map.put("DEF1.PARAM2", 124.25f);
        map.put("DEF1.PARAM3", 61);
        map.put("DEF1.PARAM4", true);
        map.put("DEF1.PARAM5", false);
        map.put("DEF1.PARAM6", new BitString(new byte[] {0x05, 0x51}, 13));
        map.put("DEF1.PARAM7", new byte[] {0x23, 0x12, (byte) 0x92});
        map.put("DEF1.PARAM8", "Hello01");
        map.put("DEF1.PARAM9", true);
        map.put("DEF1.PARAM10", Instant.ofEpochSecond(123456789, 123456789));
        map.put("DEF1.PARAM11", Duration.ofSeconds(1234, 91156789));
        map.put("DEF1.PARAM12", 61);
        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new IdentificationFieldBasedResolver(new PathLocationBasedResolver(map)));

        // The final byte array (start) should be the following
        assertEquals(0, encoded[0]);
        assertEquals(3, encoded[1]);
        assertEquals(25, encoded[2]);
    }

    @Test
    public void testDefinitionWithNullResolver() throws IOException, EncodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions7.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new DefaultNullBasedResolver());

        // First 12 bytes all zeroes
        assertArrayEquals(new byte[12], Arrays.copyOfRange(encoded, 0, 12));
    }
}