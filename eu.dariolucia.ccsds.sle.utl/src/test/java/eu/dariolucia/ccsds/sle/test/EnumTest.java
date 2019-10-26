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

package eu.dariolucia.ccsds.sle.test;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.UnbindReason;
import eu.dariolucia.ccsds.sle.utl.si.BindDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.UnbindReasonEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
    }
}
