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

package eu.dariolucia.ccsds.encdec.structure;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class PathLocationTest {

    @Test
    void testFirst() {
        PathLocation p1 = PathLocation.of("a", "b", "c");
        assertEquals("a", p1.first());
    }

    @Test
    void testLast() {
        PathLocation p1 = PathLocation.of("a", "b", "c");
        assertEquals("c", p1.last());
    }

    @Test
    void testAppendIndex() {
        PathLocation p1 = PathLocation.of("a", "b", "c");
        PathLocation p2 = p1.appendIndex(3);
        assertEquals("a.b.c#3", p2.toString());

        p1 = PathLocation.of();
        try {
            p1.appendIndex(2);
            fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
            // Ok
        }

        try {
            p2.appendIndex(2);
            fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
            // Ok
        }

    }

    @Test
    void testIsParentOf() {
        PathLocation p1 = PathLocation.of("a", "b", "c");
        PathLocation p2 = p1.appendIndex(3);
        assertTrue(p1.isParentOf(p2));
    }

    @Test
    void testIsChildOf() {
        PathLocation p1 = PathLocation.of("a", "b", "c");
        PathLocation p2 = p1.appendIndex(3);
        assertTrue(p2.isChildOf(p1));
    }

    @Test
    void testConstructor() {
        PathLocation pl = PathLocation.of("the", "path", "with#1", "array");
        assertEquals("the.path.with#1.array", pl.toString());

        PathLocation pl2 = PathLocation.of(Arrays.asList("the", "path", "with#1", "array"));
        assertEquals("the.path.with#1.array", pl.toString());
        assertEquals(pl, pl2);

        try {
            PathLocation pl3 = PathLocation.of((String[]) null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }
    }
}