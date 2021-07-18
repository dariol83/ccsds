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

import eu.dariolucia.ccsds.cfdp.entity.segmenters.FileSegment;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.impl.FilesystemBasedFilestore;
import eu.dariolucia.ccsds.cfdp.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class FixedSizeSegmenterTest {

    @Test
    public void testFixedSizeSegmenter() throws IOException, FilestoreException {
        File fs1Folder = Files.createTempDirectory("cfdp").toFile();
        FilesystemBasedFilestore fs1 = new FilesystemBasedFilestore(fs1Folder);

        String path = TestUtils.createRandomFileIn(fs1, "testfile_ack.bin", 10); // 10 KB
        FixedSizeSegmenter segmenter = new FixedSizeSegmenter(fs1, path, 512);

        int segmentCounter = 0;
        boolean eof = false;
        while(!eof) {
            FileSegment seg = segmenter.nextSegment();
            if(seg.isEof()) {
                eof = true;
            } else {
                ++segmentCounter;
            }
        }

        assertEquals(20, segmentCounter);
        // Check twice that the end of file is reached
        assertTrue(segmenter.nextSegment().isEof());
        assertTrue(segmenter.nextSegment().isEof());
    }

}