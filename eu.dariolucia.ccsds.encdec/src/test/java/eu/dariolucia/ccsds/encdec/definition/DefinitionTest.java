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

package eu.dariolucia.ccsds.encdec.definition;

import org.junit.jupiter.api.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.transform.Result;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionTest {

//    @Test
//    public void testXsdSchema() throws IOException, JAXBException {
//        JAXBContext jaxbContext = JAXBContext.newInstance(Definition.class);
//        ByteArrayOutputStream bos = new ByteArrayOutputStream();
//        final PrintStream ps = new PrintStream(bos);
//        SchemaOutputResolver sor = new SchemaOutputResolver() {
//            @Override
//            public Result createOutput(String s, String s1) {
//                StreamResult sr = new StreamResult(ps);
//                sr.setSystemId(s);
//                return sr;
//            }
//        };
//        jaxbContext.generateSchema(sor);
//        ps.close();
//        String theSchema = new String(bos.toByteArray());
//        System.out.println(theSchema);
//        assertTrue(theSchema.length() > 500);
//    }

    @Test
    public void testSerializeDeserialize() throws JAXBException, IOException {
        Definition d = new Definition();

        d.getIdentificationFields().add(new IdentField("APID", 0, 2, 0b0000011111111111, 0, 0, 0));
        d.getIdentificationFields().add(new IdentField("PUS Type", 7, 1));
        d.getIdentificationFields().add(new IdentField("PUS Subtype", 8, 1));
        d.getIdentificationFields().add(new IdentField("P1", 10, 1));
        d.getIdentificationFields().add(new IdentField("P2", 11, 1));
        d.getIdentificationFields().add(new IdentField("P3", 14, 3));

        d.getPacketDefinitions().add(new PacketDefinition(
                "DEF1",
                new IdentFieldMatcher(d.getIdentificationFields().get(0), 300),
                new IdentFieldMatcher(d.getIdentificationFields().get(1), 3),
                new IdentFieldMatcher(d.getIdentificationFields().get(2), 25)
        ));
        d.getPacketDefinitions().add(new PacketDefinition(
                "DEF2",
                new IdentFieldMatcher(d.getIdentificationFields().get(0), 301),
                new IdentFieldMatcher(d.getIdentificationFields().get(1), 3),
                new IdentFieldMatcher(d.getIdentificationFields().get(2), 25)
        ));
        d.getPacketDefinitions().add(new PacketDefinition(
                "DEF3",
                new IdentFieldMatcher(d.getIdentificationFields().get(0), 302),
                new IdentFieldMatcher(d.getIdentificationFields().get(1), 3),
                new IdentFieldMatcher(d.getIdentificationFields().get(2), 25)
        ));
        d.getPacketDefinitions().add(new PacketDefinition(
                "DEF4",
                new IdentFieldMatcher(d.getIdentificationFields().get(0), 303),
                new IdentFieldMatcher(d.getIdentificationFields().get(1), 3),
                new IdentFieldMatcher(d.getIdentificationFields().get(2), 25)
        ));
        d.getPacketDefinitions().add(new PacketDefinition(
                "DEF5",
                new IdentFieldMatcher(d.getIdentificationFields().get(0), 304),
                new IdentFieldMatcher(d.getIdentificationFields().get(1), 3),
                new IdentFieldMatcher(d.getIdentificationFields().get(2), 25),
                new IdentFieldMatcher(d.getIdentificationFields().get(3), 1)
        ));
        d.getPacketDefinitions().add(new PacketDefinition(
                "DEF6",
                new IdentFieldMatcher(d.getIdentificationFields().get(0), 305),
                new IdentFieldMatcher(d.getIdentificationFields().get(1), 3),
                new IdentFieldMatcher(d.getIdentificationFields().get(2), 25),
                new IdentFieldMatcher(d.getIdentificationFields().get(3), 2)
        ));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Definition.save(d, bos);
        bos.flush();
        ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
        Definition d1 = Definition.load(bin);
        assertEquals(d, d1);
    }
}