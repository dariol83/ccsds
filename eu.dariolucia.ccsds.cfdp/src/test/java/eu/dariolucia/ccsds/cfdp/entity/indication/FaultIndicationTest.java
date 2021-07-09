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

package eu.dariolucia.ccsds.cfdp.entity.indication;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.FileDirectivePdu;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FaultIndicationTest {

    @Test
    public void testFaultIndicationConstruction() {
        FaultIndication f = new FaultIndication(3, FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED, 2321);
        assertEquals(3, f.getTransactionId());
        assertEquals(FileDirectivePdu.CC_CANCEL_REQUEST_RECEIVED, f.getConditionCode());
        assertEquals(2321, f.getProgress());
        assertNotNull(f.toString());
    }
}