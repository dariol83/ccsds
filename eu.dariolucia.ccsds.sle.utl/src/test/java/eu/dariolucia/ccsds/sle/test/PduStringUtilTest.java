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

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Time;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.TimeCCSDS;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.TimeCCSDSpico;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PduStringUtilTest {

    @Test
    void testOIDStringConversion() {
        String oid1 = "1.112.33.22.1.232.1.232";
        String oid2 = "";
        assertEquals(oid1, PduStringUtil.instance().toOIDString(PduStringUtil.instance().fromOIDString(oid1)));
        assertEquals(oid2, PduStringUtil.instance().toOIDString(PduStringUtil.instance().fromOIDString(oid2)));
    }

    @Test
    void testTimeConversion() {
        Time t = new Time();
        assertEquals("<time format unknown>", PduStringUtil.instance().toString(t));
        t.setCcsdsPicoFormat(new TimeCCSDSpico());
        t.getCcsdsPicoFormat().value = PduFactoryUtil.buildCDSTimePico(3000, 3001);
        assertTrue(PduStringUtil.instance().toString(t).endsWith("(pico)"));
        t.setCcsdsPicoFormat(null);
        t.setCcsdsFormat(new TimeCCSDS());
        t.getCcsdsFormat().value = PduFactoryUtil.buildCDSTime(3000, 3001);
        assertFalse(PduStringUtil.instance().toString(t).endsWith("(pico)"));
        assertNotEquals("<time format unknown>", PduStringUtil.instance().toString(t));
    }
}
