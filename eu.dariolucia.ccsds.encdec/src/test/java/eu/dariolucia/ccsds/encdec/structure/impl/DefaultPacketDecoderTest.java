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

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.structure.DecodingException;
import eu.dariolucia.ccsds.encdec.structure.DecodingResult;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.ParameterValue;
import eu.dariolucia.ccsds.encdec.structure.resolvers.PathLocationBasedResolver;
import eu.dariolucia.ccsds.encdec.time.impl.DefaultGenerationTimeProcessor;
import eu.dariolucia.ccsds.encdec.value.BitString;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPacketDecoderTest {

    @Test
    void testDefinitionPerformance() throws IOException, EncodingException, DecodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        List<byte[]> encodedPackets = new LinkedList<>();
        List<String> packetIds = new LinkedList<>();

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);

        // Define the resolver map for the first
        {
            Map<String, Object> map = new TreeMap<>();
            map.put("DEF1.PARAM1", 2);
            map.put("DEF1.PARAM2", 124.25f);
            map.put("DEF1.PARAM3", 61);
            map.put("DEF1.PARAM4", true);
            map.put("DEF1.PARAM5", false);
            map.put("DEF1.PARAM6", new BitString(new byte[]{0x05, 0x50}, 13)); // Remember: the bits after the 13th one are not relevant and won't be encoded/decoded
            map.put("DEF1.PARAM7", new byte[]{0x23, 0x12, (byte) 0x92});
            map.put("DEF1.PARAM8", "Hello01");
            map.put("DEF1.PARAM9", true);
            map.put("DEF1.PARAM10", Instant.ofEpochSecond(123456789, 0));
            map.put("DEF1.PARAM11", Duration.ofSeconds(127, 0));
            map.put("DEF1.PARAM12", 7);
            // Now we can encode
            byte[] encoded = encoder.encode("DEF1", new PathLocationBasedResolver(map));
            encodedPackets.add(encoded);
            packetIds.add("DEF1");
        }

        {
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
            encodedPackets.add(encoded);
            packetIds.add("DEF2");
        }

        // Decode 20000 times the lists
        long time1 = System.currentTimeMillis();
        int decoded = 0;
        int paramsDecoded = 0;
        DefaultPacketDecoder decoder = new DefaultPacketDecoder(d);
        for (int i = 0; i < 160000; ++i) {
            DecodingResult dr = decoder.decode(packetIds.get(i % 2), encodedPackets.get(i % 2));
            if(dr.getDecodedItems().size() > 0) {
                ++decoded;
                paramsDecoded += dr.getDecodedItems().size();
            }
        }
        long time2 = System.currentTimeMillis();
        System.out.println("Decoded 160000 packets in " + (time2 - time1) + " ms");
        System.out.println("Packet decoding rate: " + ((decoded * 1000.0)/((double) (time2 - time1))) + " pkts/sec");
        System.out.println("Parameter decoding rate: " + ((paramsDecoded * 1000.0)/((double) (time2 - time1))) + " parameters/sec");

    }

    @Test
    void testDefinition1() throws IOException, EncodingException, DecodingException {
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
        map.put("DEF1.PARAM6", new BitString(new byte[]{0x05, 0x50}, 13)); // Remember: the bits after the 13th one are not relevant and won't be encoded/decoded
        map.put("DEF1.PARAM7", new byte[]{0x23, 0x12, (byte) 0x92});
        map.put("DEF1.PARAM8", "Hello01");
        map.put("DEF1.PARAM9", true);
        map.put("DEF1.PARAM10", Instant.ofEpochSecond(123456789, 0));
        map.put("DEF1.PARAM11", Duration.ofSeconds(127, 0));
        map.put("DEF1.PARAM12", 7);
        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new PathLocationBasedResolver(map));
        assertEquals(26, encoded.length);

        decodeAndCompare(d, "DEF1", map, encoded);
    }

    @Test
    void testDefinitionTime() throws IOException, EncodingException, DecodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions4.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF1.PARAM1", Instant.ofEpochSecond(123456789, 0));
        map.put("DEF1.PARAM2", 2);
        map.put("DEF1.PARAM3", 124.25f);
        map.put("DEF1.PARAM4", 61);
        map.put("DEF1.PARAM5", 30);
        map.put("DEF1.PARAM6", Duration.ofSeconds(127, 700000000));
        map.put("DEF1.PARAM7", true);
        map.put("DEF1.PARAM8", false);
        map.put("DEF1.PARAM9", false);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new PathLocationBasedResolver(map));

        DefaultPacketDecoder decoder = new DefaultPacketDecoder(d);
        DecodingResult dr = decoder.decode("DEF1", encoded, new DefaultGenerationTimeProcessor(Instant.ofEpochSecond(0)));

        for(DecodingResult.Item i : dr.getDecodedItems()) {
            DecodingResult.Parameter ei = (DecodingResult.Parameter) i;
            switch(ei.name) {
                case "PARAM1":
                case "PARAM6": {
                    assertEquals(Instant.ofEpochSecond(0), ei.generationTime);
                }
                break;
                case "PARAM2":
                case "PARAM3": {
                    assertEquals(Instant.ofEpochSecond(123456789, 0), ei.generationTime);
                }
                break;
                case "PARAM4": {
                    assertEquals(Instant.ofEpochSecond(123456789, 10000000), ei.generationTime);
                }
                break;
                case "PARAM5": {
                    assertEquals(Instant.ofEpochSecond(123456788, 950000000), ei.generationTime);
                }
                break;
                case "PARAM7": {
                    assertEquals(Instant.ofEpochSecond(123456916, 710000000).getEpochSecond(), ei.generationTime.getEpochSecond());
                    assertEquals(Instant.ofEpochSecond(123456916, 710000000).getNano(), ei.generationTime.getNano(), 1000000);
                }
                break;
                case "PARAM8": {
                    assertEquals(Instant.ofEpochSecond(123456916, 700000000).getEpochSecond(), ei.generationTime.getEpochSecond());
                    assertEquals(Instant.ofEpochSecond(123456916, 700000000).getNano(), ei.generationTime.getNano(), 1000000);
                }
                break;
                case "PARAM9": {
                    assertEquals(Instant.ofEpochSecond(127, 700000000).getEpochSecond(), ei.generationTime.getEpochSecond());
                    assertEquals(Instant.ofEpochSecond(127, 700000000).getNano(), ei.generationTime.getNano(), 1000000);
                }
                break;
            }
        }
    }

    @Test
    void testDefinition2() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF2", map, encoded);
    }

    @Test
    void testDefinition3() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF3", map, encoded);
    }

    @Test
    void testDefinition4() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF4", map, encoded);
    }

    @Test
    void testDefinition5() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF5", map, encoded);
    }

    @Test
    void testDefinition6() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF6", map, encoded);
    }

    @Test
    void testDefinition7() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF7", map, encoded);
    }


    @Test
    void testDefinition8() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF8", map, encoded);
    }

    @Test
    void testDefinition9() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF9", map, encoded);
    }

    @Test
    void testDefinition10() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF10", map, encoded);
    }

    @Test
    void testDefinition11() throws IOException, EncodingException, DecodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions2.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF11.PARAM1", Instant.ofEpochSecond(123456789, 12332789));
        map.put("DEF11.PARAM2", Duration.ofSeconds(1234, 9115695));
        // Now we can encode
        byte[] encoded = encoder.encode("DEF11", new PathLocationBasedResolver(map));
        assertEquals(16, encoded.length);

        decodeAndCompare(d, "DEF11", map, encoded);
    }

    @Test
    void testDefinition12() throws IOException, EncodingException, DecodingException {
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

        decodeAndCompare(d, "DEF12", map, encoded);
    }

    @Test
    void testDefinition13() throws IOException, EncodingException, DecodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions8.xml");
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
        map.put("DEF1.PARAM6", new BitString(new byte[]{0x05, 0x50}, 13)); // Remember: the bits after the 13th one are not relevant and won't be encoded/decoded
        map.put("DEF1.PARAM7", new byte[]{0x23, 0x12, (byte) 0x92});
        map.put("DEF1.PARAM8", "Hello01");
        map.put("DEF1.PARAM9", true);
        map.put("DEF1.PARAM10", Instant.ofEpochSecond(123456789, 0));
        map.put("DEF1.PARAM11", Duration.ofSeconds(127, 0));
        map.put("DEF1.PARAM12", 2);
        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new PathLocationBasedResolver(map));
        assertEquals(26, encoded.length);

        DecodingResult result = decodeAndCompare(d, "DEF1", map, encoded);

        List<ParameterValue> values = result.getDecodedParameters();
        assertNotNull(values);
        assertEquals(11, values.size());
        for(ParameterValue pv : values) {
            String paramId = pv.getId();
            String encodedParamId = paramId.replace("PP", "DEF1.PARAM");
            Object expectedValue = map.get(encodedParamId);
            compareEqual(expectedValue, pv.getValue());
            assertNotNull(pv.toString());
            assertNull(pv.getGenerationTime());
        }
    }

    @Test
    void testDefinition14() throws IOException, EncodingException, DecodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions9.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF1.PARAM1", 4); // 4 entries
        map.put("DEF1.ARRAY1#0.PARAM_A1", 4); // PP4
        map.put("DEF1.ARRAY1#0.PARAM_A2", true); // Boolean
        map.put("DEF1.ARRAY1#1.PARAM_A1", 2); // PP2
        map.put("DEF1.ARRAY1#1.PARAM_A2", 27.65); // Real
        map.put("DEF1.ARRAY1#2.PARAM_A1", 8); // PP8
        map.put("DEF1.ARRAY1#2.PARAM_A2", "0123456"); // String (7)
        map.put("DEF1.ARRAY1#3.PARAM_A1", 7); // PP7
        map.put("DEF1.ARRAY1#3.PARAM_A2", new byte[] {0x11, 0x22, 0x33}); // 3 bytes
        map.put("DEF1.PARAM3", 12); // PP12, signed int len 6
        map.put("DEF1.PARAM4", 10); // PP12

        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new PathLocationBasedResolver(map));

        DecodingResult result = decodeAndCompare(d, "DEF1", map, encoded);

        List<ParameterValue> values = result.getDecodedParameters();
        assertNotNull(values);
        assertEquals(4, values.size());
        for(ParameterValue pv : values) {
            String paramId = pv.getId();
            switch(paramId) {
                case "PP4": assertEquals(true, pv.getValue());
                    break;
                case "PP2": assertEquals(27.65, (double) pv.getValue(), 0.001);
                    break;
                case "PP8": assertEquals("0123456", pv.getValue());
                    break;
                case "PP7": assertArrayEquals(new byte[] {0x11, 0x22, 0x33}, (byte[]) pv.getValue());
                    break;
                default:
                    fail("Parameter " + paramId + " not expected");
            }
        }

        Map<String, Object> mapRes = result.getDecodedItemsAsMap();
        Object val = mapRes.get("DEF1.PARAM4");
        assertNotNull(val);
        assertTrue(val instanceof Number);
        assertEquals(10, ((Number) val).intValue());
    }

    @Test
    void testDefinitionUnknown() throws IOException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions8.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);
        DefaultPacketDecoder decoder = new DefaultPacketDecoder(d);
        try {
            DecodingResult dr = decoder.decode("Not there", new byte[0]);
            fail("DecodingException expected");
        } catch (DecodingException e) {
            // Good
        }

    }

    DecodingResult decodeAndCompare(Definition d, String packetDefinition, Map<String, Object> originalMap, byte[] encoded) throws DecodingException {
        DefaultPacketDecoder decoder = new DefaultPacketDecoder(d);
        DecodingResult dr = decoder.decode(packetDefinition, encoded);
        assertEquals(packetDefinition, dr.getDefinition().getId());
        Map<String, Object> decodedMap = dr.getDecodedItemsAsMap();
        assertEquals(originalMap.size(), decodedMap.size());
        for (String key : originalMap.keySet()) {
            Object decodedVal = decodedMap.get(key);
            Object originalVal = originalMap.get(key);
            compareEqual(decodedVal, originalVal);
        }
        return dr;
    }

    private void compareEqual(Object decodedVal, Object originalVal) {
        // Now I should compare ... but in Java (with autoboxing) this can be really unreliable, so I have to invent
        // something else: long comparison also for doubles and floats. Bad but good enough now.
        if (decodedVal instanceof Number && originalVal instanceof Number) {
            assertEquals(((Number) originalVal).longValue(), ((Number) decodedVal).longValue());
        } else if (decodedVal instanceof byte[] && originalVal instanceof byte[]) {
            assertArrayEquals((byte[]) originalVal, (byte[]) decodedVal);
        } else if (decodedVal instanceof Instant && originalVal instanceof Instant) {
            // Hard to write a generic comparison function, if the time encoding resolution is not known. Expect
            // that the input is coherent at least up to the millisecond.
            assertEquals(((Instant) originalVal).getEpochSecond(), ((Instant) decodedVal).getEpochSecond());
            assertTrue(Math.abs(((Instant) originalVal).getNano() - ((Instant) decodedVal).getNano()) < 1000000);
        } else {
            assertEquals(originalVal, decodedVal);
        }
    }

}