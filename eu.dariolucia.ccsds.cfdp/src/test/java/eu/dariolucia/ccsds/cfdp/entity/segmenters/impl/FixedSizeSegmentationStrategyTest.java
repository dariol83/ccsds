/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.entity.segmenters.impl;

import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class FixedSizeSegmentationStrategyTest {

    @Test
    public void testFixedSegmentationStrategy() throws IOException {
        FixedSizeSegmentationStrategy s = new FixedSizeSegmentationStrategy();
        assertTrue(s.support(null, null, "whatever"));

        InputStream in = TestUtils.class.getClassLoader().getResourceAsStream("configuration_entity_1.xml");
        Mib conf1File = Mib.load(in);
        assertNotNull(s.newSegmenter(conf1File, null, "whatever", 2));
    }
}