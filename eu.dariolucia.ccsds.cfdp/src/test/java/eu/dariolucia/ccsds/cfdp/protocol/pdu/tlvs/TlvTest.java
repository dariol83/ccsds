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

package eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs;

import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TlvTest {

    @Test
    public void testEntityId() {
        EntityIdTLV e = new EntityIdTLV(3L, 2);
        assertEquals(EntityIdTLV.TLV_TYPE, e.getType());
        assertEquals(2, e.getLength());
        assertThrows(IllegalArgumentException.class, () -> {
            new EntityIdTLV(null, 3);
        });
    }

    @Test
    public void testFaultHandlerOverride() {
        assertEquals(FaultHandlerOverrideTLV.HandlerCode.ABANDON_TRANSACTION, FaultHandlerOverrideTLV.HandlerCode.map(FaultHandlerStrategy.Action.ABANDON));
        assertEquals(FaultHandlerOverrideTLV.HandlerCode.ISSUE_NOTICE_OF_CANCELLATION, FaultHandlerOverrideTLV.HandlerCode.map(FaultHandlerStrategy.Action.NOTICE_OF_CANCELLATION));
        assertEquals(FaultHandlerOverrideTLV.HandlerCode.ISSUE_NOTICE_OF_SUSPENSION, FaultHandlerOverrideTLV.HandlerCode.map(FaultHandlerStrategy.Action.NOTICE_OF_SUSPENSION));
        assertEquals(FaultHandlerOverrideTLV.HandlerCode.IGNORE_ERROR, FaultHandlerOverrideTLV.HandlerCode.map(FaultHandlerStrategy.Action.NO_ACTION));

        assertEquals(FaultHandlerStrategy.Action.ABANDON, FaultHandlerOverrideTLV.HandlerCode.ABANDON_TRANSACTION.toAction());
        assertEquals(FaultHandlerStrategy.Action.NO_ACTION, FaultHandlerOverrideTLV.HandlerCode.IGNORE_ERROR.toAction());
        assertEquals(FaultHandlerStrategy.Action.NOTICE_OF_SUSPENSION, FaultHandlerOverrideTLV.HandlerCode.ISSUE_NOTICE_OF_SUSPENSION.toAction());
        assertEquals(FaultHandlerStrategy.Action.NOTICE_OF_CANCELLATION, FaultHandlerOverrideTLV.HandlerCode.ISSUE_NOTICE_OF_CANCELLATION.toAction());

        assertThrows(Error.class, FaultHandlerOverrideTLV.HandlerCode.RESERVED::toAction);

        FaultHandlerOverrideTLV tlv = new FaultHandlerOverrideTLV(ConditionCode.CC_CANCEL_REQUEST_RECEIVED, FaultHandlerOverrideTLV.HandlerCode.ISSUE_NOTICE_OF_CANCELLATION);
        assertEquals(FaultHandlerOverrideTLV.TLV_TYPE, tlv.getType());
    }
}
