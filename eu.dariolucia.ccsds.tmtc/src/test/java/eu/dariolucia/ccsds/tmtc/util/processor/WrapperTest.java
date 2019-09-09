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

package eu.dariolucia.ccsds.tmtc.util.processor;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class WrapperTest {

    @Test
    public void testConsumerWrapper() {
        try {
            ConsumerWrapper<Object> consumer = new ConsumerWrapper<>(null);
            fail("Exception expected");
        } catch(NullPointerException e) {
            // Good
        }

        List<Object> list = new LinkedList<>();
        Consumer<Object> consumer = list::add;
        ConsumerWrapper<Object> c1 = new ConsumerWrapper<>(consumer);
        ConsumerWrapper<Object> c2 = new ConsumerWrapper<>(consumer);
        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    public void testSupplierWrapper() {
        try {
            SupplierWrapper<Object> consumer = new SupplierWrapper<>(null);
            fail("Exception expected");
        } catch(NullPointerException e) {
            // Good
        }

        Supplier<Object> supplier = Object::new;
        SupplierWrapper<Object> s1 = new SupplierWrapper<>(supplier);
        SupplierWrapper<Object> s2 = new SupplierWrapper<>(supplier);
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
    }
}
