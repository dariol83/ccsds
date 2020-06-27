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

import eu.dariolucia.ccsds.sle.utl.si.DiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.*;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafStartDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafStartResult;
import eu.dariolucia.ccsds.sle.utl.si.rcf.RcfStartDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.rcf.RcfStartResult;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfStartDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfStartResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ResultTest {

    @Test
    public void testRcfStartResult() {
        assertFalse(RcfStartResult.noError().isError());
        assertTrue(RcfStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).isError());
        assertTrue(RcfStartResult.errorSpecific(RcfStartDiagnosticsEnum.INVALID_GVCID).isError());
        assertEquals(DiagnosticsEnum.OTHER_REASON, RcfStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).getCommon());
        assertEquals(RcfStartDiagnosticsEnum.INVALID_STOP_TIME, RcfStartResult.errorSpecific(RcfStartDiagnosticsEnum.INVALID_STOP_TIME).getSpecific());
    }

    @Test
    public void testRafStartResult() {
        assertFalse(RafStartResult.noError().isError());
        assertTrue(RafStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).isError());
        assertTrue(RafStartResult.errorSpecific(RafStartDiagnosticsEnum.INVALID_STOP_TIME).isError());
        assertEquals(DiagnosticsEnum.OTHER_REASON, RafStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).getCommon());
        assertEquals(RafStartDiagnosticsEnum.INVALID_STOP_TIME, RafStartResult.errorSpecific(RafStartDiagnosticsEnum.INVALID_STOP_TIME).getSpecific());
    }

    @Test
    public void testRocfStartResult() {
        assertFalse(RocfStartResult.noError().isError());
        assertTrue(RocfStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).isError());
        assertTrue(RocfStartResult.errorSpecific(RocfStartDiagnosticsEnum.INVALID_STOP_TIME).isError());
        assertEquals(DiagnosticsEnum.OTHER_REASON, RocfStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).getCommon());
        assertEquals(RocfStartDiagnosticsEnum.INVALID_START_TIME, RocfStartResult.errorSpecific(RocfStartDiagnosticsEnum.INVALID_START_TIME).getSpecific());
    }

    @Test
    public void testCltuStartResult() {
        assertFalse(CltuStartResult.noError().isError());
        assertTrue(CltuStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).isError());
        assertTrue(CltuStartResult.errorSpecific(CltuStartDiagnosticsEnum.PRODUCTION_TIME_EXPIRED).isError());
        assertEquals(DiagnosticsEnum.OTHER_REASON, CltuStartResult.errorCommon(DiagnosticsEnum.OTHER_REASON).getCommon());
        assertEquals(CltuStartDiagnosticsEnum.UNABLE_TO_COMPLY, CltuStartResult.errorSpecific(CltuStartDiagnosticsEnum.UNABLE_TO_COMPLY).getSpecific());
    }

    @Test
    public void testCltuThrowEventResult() {
        assertFalse(CltuThrowEventResult.noError().isError());
        assertTrue(CltuThrowEventResult.errorCommon(DiagnosticsEnum.OTHER_REASON).isError());
        assertTrue(CltuThrowEventResult.errorSpecific(CltuThrowEventDiagnosticsEnum.EVENT_INVOCATION_ID_OUT_OF_SEQUENCE).isError());
        assertEquals(DiagnosticsEnum.OTHER_REASON, CltuThrowEventResult.errorCommon(DiagnosticsEnum.OTHER_REASON).getCommon());
        assertEquals(CltuThrowEventDiagnosticsEnum.EVENT_INVOCATION_ID_OUT_OF_SEQUENCE, CltuThrowEventResult.errorSpecific(CltuThrowEventDiagnosticsEnum.EVENT_INVOCATION_ID_OUT_OF_SEQUENCE).getSpecific());
    }

    @Test
    public void testCltuTransferDataResult() {
        assertFalse(CltuTransferDataResult.noError(2000).isError());
        assertTrue(CltuTransferDataResult.errorCommon(DiagnosticsEnum.OTHER_REASON).isError());
        assertTrue(CltuTransferDataResult.errorSpecific(CltuTransferDataDiagnosticsEnum.OUT_OF_SEQUENCE).isError());
        assertEquals(DiagnosticsEnum.OTHER_REASON, CltuTransferDataResult.errorCommon(DiagnosticsEnum.OTHER_REASON).getCommon());
        assertEquals(CltuTransferDataDiagnosticsEnum.INCONSISTENT_TIME_RANGE, CltuTransferDataResult.errorSpecific(CltuTransferDataDiagnosticsEnum.INCONSISTENT_TIME_RANGE).getSpecific());
    }

}
