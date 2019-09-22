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

package eu.dariolucia.ccsds.encdec.definition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentFieldTest {

    @Test
    public void testGettersSetters() {
        IdentField if1 = new IdentField("ID1", 0, 2, 1, 2, 3, 4);
        assertEquals("ID1", if1.getId());
        assertEquals(0, if1.getByteOffset());
        assertEquals(2, if1.getByteLength());
        assertEquals(1, if1.getAndMask());
        assertEquals(2, if1.getOrMask());
        assertEquals(3, if1.getLShift());
        assertEquals(4, if1.getRShift());

        if1.setId("ID2");
        if1.setByteOffset(3);
        if1.setByteLength(3);
        if1.setAndMask(-1);
        if1.setOrMask(0);
        if1.setLShift(1);
        if1.setRShift(2);

        assertEquals("ID2", if1.getId());
        assertEquals(3, if1.getByteOffset());
        assertEquals(3, if1.getByteLength());
        assertEquals(-1, if1.getAndMask());
        assertEquals(0, if1.getOrMask());
        assertEquals(1, if1.getLShift());
        assertEquals(2, if1.getRShift());
    }
}