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

class DataTypeEnumTest {

    @Test
    public void testEnum() {
        for(int i = 1; i <= 11; ++i) {
            DataTypeEnum result = DataTypeEnum.fromCode(i);
            assertNotNull(result);
            assertEquals(i, result.getCode());
        }

        try {
            DataTypeEnum.fromCode(0);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // Good
        }

        try {
            DataTypeEnum.fromCode(12);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // Good
        }
    }
}