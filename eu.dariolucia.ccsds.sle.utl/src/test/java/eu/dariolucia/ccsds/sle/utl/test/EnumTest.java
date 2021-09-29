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

import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuStartDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuThrowEventDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuTransferDataDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafStartDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.rcf.RcfStartDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfStartDiagnosticsEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EnumTest {
    @Test
    void testEnums() {
        try {
            BindDiagnosticsEnum en = BindDiagnosticsEnum.getBindDiagnostics(111);
            fail("Exception expected");
        } catch (IllegalArgumentException e) {
            // Good
        }

        PeerAbortReasonEnum en = PeerAbortReasonEnum.fromCode((byte) 99);
        assertEquals(PeerAbortReasonEnum.UNKNOWN, en);

        assertEquals(UnbindReasonEnum.SUSPEND, UnbindReasonEnum.fromCode((byte)1));
        try {
            UnbindReasonEnum en2 = UnbindReasonEnum.fromCode((byte)111);
            fail("Exception expected");
        } catch (IllegalArgumentException e) {
            // Good
        }

        assertEquals(ProductionStatusEnum.UNKNOWN, ProductionStatusEnum.fromCode(-1));
        assertEquals(ProductionStatusEnum.UNKNOWN, ProductionStatusEnum.fromCode(20));
        assertEquals(LockStatusEnum.UNKNOWN, LockStatusEnum.fromCode(-2));
        assertEquals(LockStatusEnum.UNKNOWN, LockStatusEnum.fromCode(20));
        assertEquals(DiagnosticsEnum.OTHER_REASON, DiagnosticsEnum.getDiagnostics(127));
        assertThrows(IllegalArgumentException.class, () -> DiagnosticsEnum.getDiagnostics(22));
        assertEquals(CltuThrowEventDiagnosticsEnum.EVENT_INVOCATION_ID_OUT_OF_SEQUENCE, CltuThrowEventDiagnosticsEnum.getDiagnostics(1));
        assertThrows(IllegalArgumentException.class, () -> CltuThrowEventDiagnosticsEnum.getDiagnostics(22));
        assertEquals(4, RafStartDiagnosticsEnum.MISSING_TIME_VALUE.getCode());
        assertEquals(RafStartDiagnosticsEnum.INVALID_START_TIME, RafStartDiagnosticsEnum.getDiagnostics(2));
        assertThrows(IllegalArgumentException.class, () -> RafStartDiagnosticsEnum.getDiagnostics(22));
        assertEquals(CltuStartDiagnosticsEnum.PRODUCTION_TIME_EXPIRED, CltuStartDiagnosticsEnum.getDiagnostics(2));
        assertThrows(IllegalArgumentException.class, () -> CltuStartDiagnosticsEnum.getDiagnostics(22));
        assertEquals(RcfStartDiagnosticsEnum.INVALID_START_TIME, RcfStartDiagnosticsEnum.getDiagnostics(2));
        assertThrows(IllegalArgumentException.class, () -> RcfStartDiagnosticsEnum.getDiagnostics(22));
        assertEquals(4, RocfStartDiagnosticsEnum.MISSING_TIME_VALUE.getCode());
        assertEquals(RocfStartDiagnosticsEnum.INVALID_START_TIME, RocfStartDiagnosticsEnum.getDiagnostics(2));
        assertThrows(IllegalArgumentException.class, () -> RocfStartDiagnosticsEnum.getDiagnostics(22));
        assertEquals(CltuTransferDataDiagnosticsEnum.OUT_OF_SEQUENCE, CltuTransferDataDiagnosticsEnum.getDiagnostics(2));
        assertThrows(IllegalArgumentException.class, () -> CltuTransferDataDiagnosticsEnum.getDiagnostics(22));


    }
}
