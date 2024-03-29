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

package eu.dariolucia.ccsds.cfdp.common;

import eu.dariolucia.ccsds.cfdp.entity.FaultDeclaredException;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExceptionTest {

    @Test
    public void testExceptions() {
        assertThrows(CfdpException.class, () -> {
            throw new CfdpException("Test");
        });
        assertThrows(CfdpException.class, () -> {
            throw new CfdpException(new RuntimeException());
        });
        assertThrows(CfdpException.class, () -> {
            throw new CfdpException("Test", new RuntimeException());
        });
        assertThrows(CfdpRuntimeException.class, () -> {
            throw new CfdpRuntimeException("Test");
        });
        assertThrows(CfdpRuntimeException.class, () -> {
            throw new CfdpRuntimeException(new RuntimeException());
        });
        assertThrows(CfdpRuntimeException.class, () -> {
            throw new CfdpRuntimeException("Test", new RuntimeException());
        });
        assertThrows(CfdpStandardComplianceError.class, () -> {
            throw new CfdpStandardComplianceError("Test");
        });
        assertThrows(FilestoreException.class, () -> {
            throw new FilestoreException("Test");
        });
        assertThrows(FilestoreException.class, () -> {
            throw new FilestoreException(new RuntimeException());
        });
        assertThrows(FilestoreException.class, () -> {
            throw new FilestoreException("Test", new RuntimeException());
        });

        try {
            FaultDeclaredException exception = new FaultDeclaredException(123, FaultHandlerStrategy.Action.NO_ACTION, ConditionCode.CC_CANCEL_REQUEST_RECEIVED, 3);
            throw exception;
        } catch (FaultDeclaredException e) {
            assertEquals(123, e.getTransactionId());
            assertEquals(FaultHandlerStrategy.Action.NO_ACTION, e.getAction());
            assertEquals(ConditionCode.CC_CANCEL_REQUEST_RECEIVED, e.getConditionCode());
            assertEquals(3, e.getGeneratingEntityId());
        }

        assertThrows(UtLayerException.class, () -> {
            throw new UtLayerException("Test");
        });
        assertThrows(UtLayerException.class, () -> {
            throw new UtLayerException(new RuntimeException());
        });
        assertThrows(UtLayerException.class, () -> {
            throw new UtLayerException("Test", new RuntimeException());
        });
    }
}
