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

package eu.dariolucia.ccsds.sle.utl.config;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.DeliveryMode;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.network.PortMapping;
import eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rcf.RcfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rocf.RocfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuNotificationModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuPlopInEffectEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuProtocolAbortModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfControlWordTypeEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfUpdateModeEnum;
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
import java.util.Arrays;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

class UtlConfigurationFileTest {

    /**
     * Verify the correct generation of the XSD schema.
     *
     * @throws IOException
     * @throws JAXBException
     */
    @Test
    public void testXsdSchema() throws IOException, JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(UtlConfigurationFile.class);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(bos);
        SchemaOutputResolver sor = new SchemaOutputResolver() {
            @Override
            public Result createOutput(String s, String s1) {
                StreamResult sr = new StreamResult(ps);
                sr.setSystemId(s);
                return sr;
            }
        };
        jaxbContext.generateSchema(sor);
        ps.close();
        String theSchema = new String(bos.toByteArray());
        assertTrue(theSchema.length() > 500);
    }

    /**
     * Verify the correct serialisation and deserialisation of the configuration.
     *
     * @throws IOException
     */
    @Test
    public void testSerializeDeserialize() throws IOException {
        UtlConfigurationFile file = new UtlConfigurationFile();

        file.setPeerConfiguration(new PeerConfiguration());
        file.getPeerConfiguration().setAuthenticationDelay(30);
        file.getPeerConfiguration().setLocalId("LOCAL-ID");
        file.getPeerConfiguration().setLocalPassword("00112233445566");
        file.getPeerConfiguration().setPortMappings(new LinkedList<>());
        file.getPeerConfiguration().getPortMappings().add(new PortMapping("PORT1", 4, 30, "127.0.0.1:23233", 0, 0));
        file.getPeerConfiguration().getPortMappings().add(new PortMapping("PORT2", 2, 40, "127.0.0.1:23234", 65536, 65536));
        PortMapping pm = new PortMapping();
        pm.setPortName("ThePort");
        pm.setDeadFactor(4);
        pm.setHeartbeatInterval(3);
        pm.setAddress("127.0.0.1:20");
        pm.setTcpRxBufferSize(32000);
        pm.setTcpTxBufferSize(64000);
        file.getPeerConfiguration().getPortMappings().add(pm);
        file.getPeerConfiguration().setRemotePeers(new LinkedList<>());
        file.getPeerConfiguration().getRemotePeers().add(new RemotePeer("PEER1", AuthenticationModeEnum.NONE, HashFunctionEnum.SHA_1, "AABBCCDDEEFF"));
        RemotePeer peer2 = new RemotePeer();
        peer2.setId("the_id");
        peer2.setAuthenticationMode(AuthenticationModeEnum.ALL);
        peer2.setAuthenticationHash(HashFunctionEnum.SHA_1);
        peer2.setPassword("AABBCC");
        file.getPeerConfiguration().getRemotePeers().add(peer2);

        file.setServiceInstances(new LinkedList<>());
        // RAF SI
        RafServiceInstanceConfiguration rafSi = new RafServiceInstanceConfiguration();
        rafSi.setInitiator(InitiatorRoleEnum.USER);
        rafSi.setInitiatorIdentifier("LOCAL-ID");
        rafSi.setResponderIdentifier("PEER1");
        rafSi.setResponderPortIdentifier("PORT1");
        rafSi.setReturnTimeoutPeriod(60);
        rafSi.setServiceVersionNumber(2);
        rafSi.setDeliveryMode(DeliveryModeEnum.TIMELY_ONLINE);
        rafSi.setLatencyLimit(3);
        rafSi.setTransferBufferSize(10);
        rafSi.setReportingCycle(30);
        rafSi.setMinReportingCycle(20);
        rafSi.setStartTime(null);
        rafSi.setEndTime(null);
        rafSi.setPermittedFrameQuality(Arrays.asList(RafRequestedFrameQualityEnum.ALL_FRAMES, RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY));
        rafSi.setRequestedFrameQuality(RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY);
        file.getServiceInstances().add(rafSi);

        assertEquals(ApplicationIdentifierEnum.RAF, rafSi.getType());

        // RCF SI
        RcfServiceInstanceConfiguration rcfSi = new RcfServiceInstanceConfiguration();
        rcfSi.setInitiator(InitiatorRoleEnum.USER);
        rcfSi.setInitiatorIdentifier("LOCAL-ID");
        rcfSi.setResponderIdentifier("PEER1");
        rcfSi.setResponderPortIdentifier("PORT1");
        rcfSi.setReturnTimeoutPeriod(60);
        rcfSi.setServiceVersionNumber(2);
        rcfSi.setDeliveryMode(DeliveryModeEnum.TIMELY_ONLINE);
        rcfSi.setLatencyLimit(3);
        rcfSi.setTransferBufferSize(10);
        rcfSi.setReportingCycle(30);
        rcfSi.setMinReportingCycle(20);
        rcfSi.setStartTime(null);
        rcfSi.setEndTime(null);
        rcfSi.setPermittedGvcid(Arrays.asList(new GVCID(123, 1, null), new GVCID(123, 1, 0)));
        rcfSi.setRequestedGvcid(new GVCID(123, 1, 0));
        file.getServiceInstances().add(rcfSi);

        assertEquals(ApplicationIdentifierEnum.RCF, rcfSi.getType());
        assertNotNull(rcfSi.getRequestedGvcid());

        // ROCF SI
        RocfServiceInstanceConfiguration rocf = new RocfServiceInstanceConfiguration();
        rocf.setInitiator(InitiatorRoleEnum.USER);
        rocf.setInitiatorIdentifier("LOCAL-ID");
        rocf.setResponderIdentifier("PEER1");
        rocf.setResponderPortIdentifier("PORT1");
        rocf.setReturnTimeoutPeriod(60);
        rocf.setServiceVersionNumber(2);
        rocf.setDeliveryMode(DeliveryModeEnum.TIMELY_ONLINE);
        assertEquals(DeliveryModeEnum.TIMELY_ONLINE, rocf.getDeliveryMode());
        rocf.setLatencyLimit(3);
        assertEquals(3, rocf.getLatencyLimit());
        rocf.setTransferBufferSize(10);
        assertEquals(10, rocf.getTransferBufferSize());
        rocf.setReportingCycle(30);
        assertEquals(30, rocf.getReportingCycle());
        rocf.setMinReportingCycle(20);
        assertEquals(20, rocf.getMinReportingCycle());
        rocf.setStartTime(null);
        assertNull(rocf.getStartTime());
        rocf.setEndTime(null);
        assertNull(rocf.getEndTime());
        rocf.setPermittedGvcid(Arrays.asList(new GVCID(123, 1, null), new GVCID(123, 1, 0)));
        assertNotNull(rocf.getPermittedGvcid());
        rocf.setPermittedTcVcids(Arrays.asList(0, 1));
        assertNotNull(rocf.getPermittedTcVcids());
        rocf.setPermittedControlWordTypes(Arrays.asList(RocfControlWordTypeEnum.ALL, RocfControlWordTypeEnum.CLCW, RocfControlWordTypeEnum.NO_CLCW));
        assertNotNull(rocf.getPermittedControlWordTypes());
        rocf.setPermittedUpdateModes(Arrays.asList(RocfUpdateModeEnum.CHANGE_BASED, RocfUpdateModeEnum.CONTINUOUS));
        assertNotNull(rocf.getPermittedUpdateModes());
        rocf.setRequestedGvcid(new GVCID(123, 1, 0));
        assertNotNull(rocf.getRequestedGvcid());
        rocf.setRequestedTcVcid(1);
        assertEquals(1, rocf.getRequestedTcVcid());
        rocf.setRequestedControlWordType(RocfControlWordTypeEnum.CLCW);
        assertEquals(RocfControlWordTypeEnum.CLCW, rocf.getRequestedControlWordType());
        rocf.setRequestedUpdateMode(RocfUpdateModeEnum.CONTINUOUS);
        assertEquals(RocfUpdateModeEnum.CONTINUOUS, rocf.getRequestedUpdateMode());
        file.getServiceInstances().add(rocf);

        assertEquals(ApplicationIdentifierEnum.ROCF, rocf.getType());

        // CLTU SI
        CltuServiceInstanceConfiguration cltuSi = new CltuServiceInstanceConfiguration();
        cltuSi.setServiceInstanceIdentifier("test");
        cltuSi.setInitiator(InitiatorRoleEnum.USER);
        cltuSi.setInitiatorIdentifier("LOCAL-ID");
        cltuSi.setResponderIdentifier("PEER1");
        cltuSi.setResponderPortIdentifier("PORT2");
        cltuSi.setReturnTimeoutPeriod(60);
        cltuSi.setServiceVersionNumber(2);
        assertEquals(2, cltuSi.getServiceVersionNumber());
        cltuSi.setBitlockRequired(false);
        assertFalse(cltuSi.isBitlockRequired());
        cltuSi.setRfAvailableRequired(true);
        assertTrue(cltuSi.isRfAvailableRequired());
        cltuSi.setProtocolAbortMode(CltuProtocolAbortModeEnum.ABORT_MODE);
        assertEquals(CltuProtocolAbortModeEnum.ABORT_MODE, cltuSi.getProtocolAbortMode());
        cltuSi.setReportingCycle(30);
        assertEquals(30, cltuSi.getReportingCycle());
        cltuSi.setMinReportingCycle(20);
        assertEquals(20, cltuSi.getMinReportingCycle());
        cltuSi.setStartTime(null);
        assertNull(cltuSi.getStartTime());
        cltuSi.setEndTime(null);
        assertNull(cltuSi.getEndTime());
        cltuSi.setMaxCltuLength(2000);
        assertEquals(2000, cltuSi.getMaxCltuLength());
        cltuSi.setMinCltuDelay(2000);
        assertEquals(2000, cltuSi.getMinCltuDelay());
        cltuSi.setAcquisitionSequenceLength(300);
        cltuSi.setClcwGlobalVcid(new GVCID(100,0,1));
        cltuSi.setClcwPhysicalChannel("CLCW_PH_CH");
        cltuSi.setModulationFrequency(70000000L);
        cltuSi.setModulationIndex(700);
        cltuSi.setNotificationMode(CltuNotificationModeEnum.IMMEDIATE);
        cltuSi.setPlop1idleSequenceLength(40);
        cltuSi.setPlopInEffect(CltuPlopInEffectEnum.PLOP2);
        cltuSi.setSubcarrierToBitrateRatio(1);
        file.getServiceInstances().add(cltuSi);

        assertEquals(ApplicationIdentifierEnum.CLTU, cltuSi.getType());
        assertDoesNotThrow(cltuSi::toString);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        UtlConfigurationFile.save(file, bos);
        bos.flush();
        ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
        UtlConfigurationFile d1 = UtlConfigurationFile.load(bin);
        assertEquals(file, d1);
        assertDoesNotThrow(d1::hashCode);
    }

    @Test
    void testWrongFiles() {
        assertThrows(IOException.class, () -> UtlConfigurationFile.load(new ByteArrayInputStream("<test></test>".getBytes())));
    }
}