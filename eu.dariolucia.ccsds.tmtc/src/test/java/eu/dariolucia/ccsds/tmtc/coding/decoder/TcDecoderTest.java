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

package eu.dariolucia.ccsds.tmtc.coding.decoder;

import eu.dariolucia.ccsds.tmtc.coding.encoder.CltuEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.CltuRandomizerEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.builder.TcTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TcDecoderTest {

    public static final String EXPECTED_CLTU = "EB90FF429A5468E804BEF6688C29A63957C20255555555555556C5C5C5C5C5C5C579";

    @Test
    public void testTcDecoding() {
        // Create TC as result
        TcTransferFrameBuilder builder = TcTransferFrameBuilder.create(false)
                .setSpacecraftId(123)
                .setVirtualChannelId(1)
                .setFrameSequenceNumber(0)
                .setControlCommandFlag(false)
                .setBypassFlag(false);
        builder.addData(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });

        TcTransferFrame tctf = builder.build();

        byte[] input = StringUtil.toByteArray(EXPECTED_CLTU);

        // Randomize and CLTU encode
        CltuRandomizerDecoder randomizer = new CltuRandomizerDecoder();
        CltuDecoder cltuDecoder = new CltuDecoder();
        Optional<byte[]> decoded = List.of(input).stream().map(cltuDecoder).map(randomizer).findFirst();
        assertTrue(decoded.isPresent());
        // Use the decoding function to remove also the virtual fill, if any
        TcTransferFrame frame = TcTransferFrame.decodingFunction((vc) -> false, false).apply(decoded.get());

        assertEquals(tctf.getSpacecraftId(), frame.getSpacecraftId());
        assertEquals(tctf.isBypassFlag(), frame.isBypassFlag());
        assertEquals(tctf.isControlCommandFlag(), frame.isControlCommandFlag());
        assertEquals(tctf.getVirtualChannelId(), frame.getVirtualChannelId());
        assertEquals(tctf.getVirtualChannelFrameCount(), frame.getVirtualChannelFrameCount());
        assertEquals(tctf.getTransferFrameVersionNumber(), frame.getTransferFrameVersionNumber());
    }

    @Test
    public void testNullInput() {
        try {
            CltuDecoder enc = new CltuDecoder();
            enc.apply(null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }

        try {
            CltuRandomizerDecoder enc = new CltuRandomizerDecoder();
            enc.apply(null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }
    }
}
