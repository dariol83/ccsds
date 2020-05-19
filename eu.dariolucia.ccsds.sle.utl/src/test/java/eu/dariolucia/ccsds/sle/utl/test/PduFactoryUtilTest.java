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

package eu.dariolucia.ccsds.sle.utl.test;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.ConditionalTime;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Credentials;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Time;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.TimeCCSDSpico;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.service.instance.id.ServiceInstanceIdentifier;
import eu.dariolucia.ccsds.sle.utl.config.network.RemotePeer;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.sle.utl.si.AuthenticationModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.HashFunctionEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PduFactoryUtilTest {

    @Test
    void testTimeFunctions() {
        ConditionalTime ct = new ConditionalTime();
        ct.setKnown(new Time());
        // No time
        assertThrows(IllegalArgumentException.class, () -> PduFactoryUtil.toDate(ct));
        ct.getKnown().setCcsdsPicoFormat(new TimeCCSDSpico());
        // Wrong time
        ct.getKnown().getCcsdsPicoFormat().value = new byte[10];
        assertThrows(IllegalArgumentException.class, () -> PduFactoryUtil.toDate(ct));
        // Negative value
        assertThrows(IllegalArgumentException.class, () -> PduFactoryUtil.buildCDSTimePico(-10, 10));
        assertThrows(IllegalArgumentException.class, () -> PduFactoryUtil.buildCDSTime(-10, 10));
        assertArrayEquals(new long[] {3000, 3001}, PduFactoryUtil.buildTimeMillisPico(PduFactoryUtil.buildCDSTimePico(3000, 3001)));
        assertNull(PduFactoryUtil.toDate(null));
    }

    @Test
    void testAuthenticationFunctions() throws InterruptedException {
        // Wrong credentials decoding
        RemotePeer peer = new RemotePeer("PEER", AuthenticationModeEnum.ALL, HashFunctionEnum.SHA_1, "0001");
        assertFalse(PduFactoryUtil.performAuthentication(peer, new byte[12], 0));

        Credentials creds = PduFactoryUtil.buildCredentials(true, "PEER", new byte[] { 0, 1 }, HashFunctionEnum.SHA_1);
        AwaitUtil.await(2000);
        assertFalse(PduFactoryUtil.performAuthentication(peer, creds.getUsed().value, 0));
    }

    @Test
    void testBuildSIID() {
        String siid = "sagr=232.spack=123.what=111.ever=222";
        ServiceInstanceIdentifier s = PduFactoryUtil.buildServiceInstanceIdentifier(siid, ApplicationIdentifierEnum.FSP);
        assertNotNull(s);
    }
}
