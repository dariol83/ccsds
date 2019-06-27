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

package eu.dariolucia.ccsds.tmtc.coding.encoder;

import eu.dariolucia.ccsds.tmtc.datalink.builder.TcTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TcEncoderTest {

    public static final String EXPECTED_CLTU = "EB90FF429A5468E804BEF6688C29A63957C20255555555555556C5C5C5C5C5C5C579";

    @Test
    public void testTcEncoding() {
        // Create TC
        TcTransferFrameBuilder builder = TcTransferFrameBuilder.create(false)
                .setSpacecraftId(123)
                .setVirtualChannelId(1)
                .setFrameSequenceNumber(0)
                .setControlCommandFlag(false)
                .setBypassFlag(false);
        builder.addData(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 });

        TcTransferFrame tctf = builder.build();

        // Randomize and CLTU encode
        CltuRandomizerEncoder<TcTransferFrame> randomizer = new CltuRandomizerEncoder<>();
        CltuEncoder<TcTransferFrame> cltuEncoder = new CltuEncoder<>();
        Optional<byte[]> encoded = List.of(tctf).stream().map(TcTransferFrame::getFrame).map(randomizer.asFunction()).map(cltuEncoder.asFunction()).findFirst();
        assertTrue(encoded.isPresent());
        assertArrayEquals(StringUtil.toByteArray(EXPECTED_CLTU), encoded.get());
    }

    @Test
    public void testNullInput() {
        try {
            CltuEncoder<TcTransferFrame> enc = new CltuEncoder<>();
            enc.apply(null, null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }

        try {
            CltuRandomizerEncoder<TcTransferFrame> enc = new CltuRandomizerEncoder<>();
            enc.apply(null, null);
            fail("NullPointerException expected");
        } catch(NullPointerException e) {
            // Good
        }
    }
}
