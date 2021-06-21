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

import org.junit.jupiter.api.Test;

import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.*;

class MibCoverageTest {

    @Test
    public void testMibCoverage() {
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
        m.getLocalEntity().getFaultHandlerStrategyList().add(new FaultHandlerStrategy(3, FaultHandlerStrategy.Action.NO_ACTION));
        FaultHandlerStrategy fhs2 = new FaultHandlerStrategy();
        fhs2.setCondition(3);
        fhs2.setStrategy(FaultHandlerStrategy.Action.NO_ACTION);
        m.getLocalEntity().getFaultHandlerStrategyList().add(fhs2);
        m.getLocalEntity().setEofRecvIndicationRequired(true);

        m.getRemoteEntities().get(0).setAcknowledgedModeSupported(true);
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
    }

}