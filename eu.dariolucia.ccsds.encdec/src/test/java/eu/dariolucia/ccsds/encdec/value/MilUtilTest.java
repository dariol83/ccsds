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

package eu.dariolucia.ccsds.encdec.value;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MilUtilTest {

    @Test
    public void test() {
        double[] testData = { 0.0023, 0.1f, 12312.1232, -0.2321, -99231.290123, 0.0, 1.0, -1.0 };
        for(double d : testData) {
            {
                long rawMil = MilUtil.toMil32Real(d);
                double back = MilUtil.fromMil32Real(rawMil);
                assertEquals(d, back, 0.1);
            }
            {
                long rawMil = MilUtil.toMil48Real(d);
                double back = MilUtil.fromMil48Real(rawMil);
                assertEquals(d, back, 0.00001);
            }
        }
    }
}