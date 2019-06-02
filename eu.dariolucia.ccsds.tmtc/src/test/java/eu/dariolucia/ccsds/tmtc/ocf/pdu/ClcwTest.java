/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.ocf.pdu;

import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClcwTest {

    private static byte[] FIRST_CLCW = StringUtil.toByteArray( "0002A689");
    private static byte[] SECOND_CLCW = StringUtil.toByteArray("0002D0D4");

    @Test
    public void testClcwDecoding() {
        Clcw clcw = new Clcw(FIRST_CLCW);
        assertEquals(Clcw.CopEffectType.NONE, clcw.getCopInEffect());
        assertEquals(0, clcw.getStatusField());
        assertEquals(137, clcw.getReportValue());
        assertEquals(3, clcw.getFarmBCounter());

        clcw = new Clcw(SECOND_CLCW);
        assertEquals(Clcw.CopEffectType.NONE, clcw.getCopInEffect());
        assertEquals(0, clcw.getStatusField());
        assertEquals(212, clcw.getReportValue());
        assertEquals(0, clcw.getFarmBCounter());
    }
}