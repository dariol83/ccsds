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

import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GvcidTest {

    @Test
    void testGvcid() {
        GVCID v1 = new GVCID(12,1,0);
        GVCID v2 = new GVCID(12,1,0);
        GVCID v3 = new GVCID(11,1,0);
        GVCID v4 = new GVCID(12,0,0);
        GVCID v5 = new GVCID(12,0,5);
        GVCID v6 = new GVCID(12,0,null);

        assertEquals(12, v1.getSpacecraftId());
        assertEquals(1, v1.getTransferFrameVersionNumber());
        assertEquals(0, v1.getVirtualChannelId());

        assertEquals(v1, v1);
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
        assertNotEquals(v1, v3);
        assertNotEquals(v1, v4);
        assertNotEquals(v1, v5);
        assertNotEquals(v1, v6);
        assertNotEquals(v5, v6);
        assertNotEquals(v6, v5);
        assertFalse(v5.equals(null));
        assertNotEquals(v5, new Object());

        assertNotNull(v1.toString());
        assertNotNull(v6.toString());
    }
}
