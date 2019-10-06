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
import eu.dariolucia.ccsds.encdec.extension.ExtensionId;
import eu.dariolucia.ccsds.encdec.extension.IDecoderExtension;
import eu.dariolucia.ccsds.encdec.structure.DecodingException;
import eu.dariolucia.ccsds.encdec.structure.DecodingResult;
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
import eu.dariolucia.ccsds.encdec.structure.resolvers.PathLocationBasedResolver;
import eu.dariolucia.ccsds.encdec.time.impl.DefaultGenerationTimeProcessor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// READ THIS!
//
// This test fails if run from IntelliJ, OK if runs with Maven. The module detection from IntelliJ when running
// JUnit tests fails to use the module path, and falls back to the classpath. This means that provided services
// will fail to load, unless you specify them with the old META-INF/services approach, which I do not want to do for
// Java11-based code.
//
// If you want to raise a remark with the IntelliJ team, here you go: https://youtrack.jetbrains.com/issue/IDEA-171419
class EncoderDecoderExtensionTest {

    @Test
    public void testEncoderDecoder() throws IOException, EncodingException, DecodingException {
        InputStream defStr = this.getClass().getClassLoader().getResourceAsStream("definitions5.xml");
        assertNotNull(defStr);
        Definition d = Definition.load(defStr);

        DefaultPacketEncoder encoder = new DefaultPacketEncoder(d);
        // Define the resolver map
        final UUID theUUID = UUID.randomUUID();
        Map<String, Object> map = new TreeMap<>();
        map.put("DEF1.PARAM1", Instant.ofEpochSecond(123456789, 0));
        map.put("DEF1.PARAM2", theUUID);
        map.put("DEF1.PARAM3", 26283);

        // Now we can encode
        byte[] encoded = encoder.encode("DEF1", new PathLocationBasedResolver(map));

        DefaultPacketDecoder decoder = new DefaultPacketDecoder(d);
        DecodingResult dr = decoder.decode("DEF1", encoded, new DefaultGenerationTimeProcessor(Instant.ofEpochSecond(0)));

        for(DecodingResult.Item i : dr.getDecodedItems()) {
            DecodingResult.Parameter ei = (DecodingResult.Parameter) i;
            switch(ei.name) {
                case "PARAM1": {
                    assertEquals(Instant.ofEpochSecond(0), ei.generationTime);
                }
                break;
                case "PARAM2": {
                    assertEquals(theUUID, ei.value);
                }
                break;
                case "PARAM3": {
                    assertEquals(26283L, ei.value);
                }
                break;
            }
        }
    }
}