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

package eu.dariolucia.ccsds.tmtc.coding;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.LineHexDumpChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StreamUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TmChannelDecoderTest {

    private static String FILE_TM1 = "dumpFile_tm_1.hex";

    @Test
    public void testTmDecodingWithIteration() throws IOException {
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Build the decoder: TM Frame decoding function, no FECF
        ChannelDecoder<TmTransferFrame> cd = ChannelDecoder.create(TmTransferFrame.decodingFunction(false))
                .addDecodingFunction(new TmAsmDecoder()) // Add ASM removal with default ASM
                .addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block removal 255/223
                .configure();
        // Use iteration approach
        AbstractTransferFrame tf;
        byte[] tfRaw;
        int counter = 0;
        while ((tfRaw = reader.readNext()) != null) {
            assertEquals(1279, tfRaw.length);
            tf = cd.apply(tfRaw);
            ++counter;
            assertEquals(1115, tf.getLength());
        }
        assertEquals(152, counter);
    }

    @Test
    public void testTmDecodingWithStream() {
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Use stream approach: no need for decoder
        List<TmTransferFrame> frames = StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(new TmAsmDecoder()) // Remove ASM
                .map(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Remove R-S codeblock
                .map(TmTransferFrame.decodingFunction(false)) // Convert to TM frame
                .collect(Collectors.toList());

        assertEquals(152, frames.size());
    }

    @Test
    public void testTmDecodingWithStreamAndDecoder() {
        // Build the reader
        LineHexDumpChannelReader reader = new LineHexDumpChannelReader(this.getClass().getClassLoader().getResourceAsStream(FILE_TM1));
        // Build the decoder: TM Frame decoding function, no FECF, ASM removal, R-S removal
        ChannelDecoder<TmTransferFrame> decoder = ChannelDecoder.create(TmTransferFrame.decodingFunction(false))
                .addDecodingFunction(new TmAsmDecoder()) // Add ASM removal with default ASM
                .addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223)) // Add R-S symbol block removal 255/223
                .configure();
        // Use stream approach with decoder
        List<TmTransferFrame> frames = StreamUtil.from(reader) // Reads the frames, correctly segmented
                .map(decoder) // Use the preconfigured decoder
                .collect(Collectors.toList());

        assertEquals(152, frames.size());
    }
}