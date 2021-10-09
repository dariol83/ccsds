/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.mib;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

class MibCoverageTest {

//    @Test
//    public void testXsdSchema() throws IOException, JAXBException {
//        JAXBContext jaxbContext = JAXBContext.newInstance(Mib.class);
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
    public void testMibCoverage() throws IOException {
        Mib m = new Mib();
        m.setLocalEntity(new LocalEntityConfigurationInformation());
        m.setRemoteEntities(new LinkedList<>());
        m.getRemoteEntities().add(new RemoteEntityConfigurationInformation());

        m.getLocalEntity().setLocalEntityId(3);
        m.getLocalEntity().setEofRecvIndicationRequired(true);
        m.getLocalEntity().setEofSentIndicationRequired(true);
        m.getLocalEntity().setFileSegmentRecvIndicationRequired(true);
        m.getLocalEntity().setResumedIndicationRequired(true);
        m.getLocalEntity().setSuspendedIndicationRequired(true);
        m.getLocalEntity().setTransactionFinishedIndicationRequired(true);
        m.getLocalEntity().setFaultHandlerStrategyList(new LinkedList<>());
        m.getLocalEntity().getFaultHandlerStrategyList().add(new FaultHandlerStrategy(ConditionCode.fromCode((byte)3), FaultHandlerStrategy.Action.NO_ACTION));
        FaultHandlerStrategy fhs2 = new FaultHandlerStrategy();
        fhs2.setCondition(ConditionCode.fromCode((byte)3));
        fhs2.setStrategy(FaultHandlerStrategy.Action.NO_ACTION);
        m.getLocalEntity().getFaultHandlerStrategyList().add(fhs2);
        m.getLocalEntity().setEofRecvIndicationRequired(true);

        m.getRemoteEntities().get(0).setCheckInterval(20);
        m.getRemoteEntities().get(0).setCheckIntervalExpirationLimit(3);
        m.getRemoteEntities().get(0).setCrcRequiredOnTransmission(true);
        m.getRemoteEntities().get(0).setDefaultChecksumType(0);
        m.getRemoteEntities().get(0).setDefaultTransmissionModeAcknowledged(true);
        m.getRemoteEntities().get(0).setImmediateNakModeEnabled(true);
        m.getRemoteEntities().get(0).setKeepAliveDiscrepancyLimit(2);
        m.getRemoteEntities().get(0).setKeepAliveInterval(333);
        m.getRemoteEntities().get(0).setMaximumFileSegmentLength(2222);
        m.getRemoteEntities().get(0).setNakRecomputationInterval(222);
        m.getRemoteEntities().get(0).setNakTimerExpirationLimit(21);
        m.getRemoteEntities().get(0).setPositiveAckTimerExpirationLimit(2);
        m.getRemoteEntities().get(0).setPositiveAckTimerInterval(2000);
        m.getRemoteEntities().get(0).setProtocolVersion(1);
        m.getRemoteEntities().get(0).setRemoteEntityId(2);
        m.getRemoteEntities().get(0).setRetainIncompleteReceivedFilesOnCancellation(true);
        m.getRemoteEntities().get(0).setNakTimerInterval(2222);
        m.getRemoteEntities().get(0).setTransactionClosureRequested(true);
        m.getRemoteEntities().get(0).setTransactionInactivityLimit(2000);
        m.getRemoteEntities().get(0).setUtLayer("Test");
        m.getRemoteEntities().get(0).setUtAddress("Whatever");

        assertEquals(m.getRemoteEntities().get(0), m.getRemoteEntityById(2));
        assertNotNull(m.toString());

        assertNotNull(m.getRemoteEntityById(2));
        assertNull(m.getRemoteEntityById(1));
        assertNull(m.getRemoteEntityById(-1));
        assertEquals(1, m.getRemoteEntityById(2).getProtocolVersion());

        Path tempFile = Files.createTempFile("cfdp_mib", ".xml");
        assertDoesNotThrow(() -> Mib.save(m, new FileOutputStream(tempFile.toFile())));
        Mib m2 = Mib.load(new FileInputStream(tempFile.toFile()));
        assertEquals(m.getLocalEntity().getLocalEntityId(), m2.getLocalEntity().getLocalEntityId());

        assertThrows(IOException.class, () -> {
            Mib.load(new ByteArrayInputStream("<xml></xml>".getBytes(StandardCharsets.UTF_8)));
        });
        m2.setLocalEntity(null);
        m2.setRemoteEntities(null);
        // assertThrows(IOException.class, () -> {
        //     Mib.save(m2, new ByteArrayOutputStream());
        // });
    }

}