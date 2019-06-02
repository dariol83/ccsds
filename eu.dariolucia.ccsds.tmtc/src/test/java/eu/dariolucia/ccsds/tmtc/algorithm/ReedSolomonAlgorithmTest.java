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

package eu.dariolucia.ccsds.tmtc.algorithm;

import eu.dariolucia.ccsds.tmtc.datalink.builder.TmTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FIXME: there is something not convincing about the implementation...
 */
class ReedSolomonAlgorithmTest {

    // TODO
//    @Test
//    public void testSmallEncoding() {
//        int[] message = new int[] { 0x02, 0x04, 0x05, 0x06, 0x04, 0x04};
//
//        ReedSolomonAlgorithm rs = new ReedSolomonAlgorithm(
//                10,
//                6,
//                0x13,      // As per AOS Blue Book specs: x^4 + x + 1 = 10011 = 19 = 0x13
//                new int[]{1, 8, 2, 8},      // As per AOS Blue Book specs: x^4 + (a3 x^3) + (a x^2) + (a3 x) + 1
//                new int[]{12, 11, 5, 10}    // As per AOS Blue Book specs: g(x) = (x + a6)(x + a7)(x + a8)(x + a9) : only needed for decoding verify
//        );
//
//        int[] output = rs.encodeCodeword(message);
//
//        assertEquals(0x0E, output[0]);
//        assertEquals(0x0D, output[1]);
//        assertEquals(0x0C, output[2]);
//        assertEquals(0x02, output[3]);
//    }

    @Test
    public void testEncodeFrame() {
        int userDataLength = TmTransferFrameBuilder.computeUserDataLength(1115, 0, true, false);
        TmTransferFrameBuilder builder = TmTransferFrameBuilder.create(1115, 0, true, false)
                .setSpacecraftId(789)
                .setVirtualChannelId(2)
                .setMasterChannelFrameCount(34)
                .setVirtualChannelFrameCount(123)
                .setPacketOrderFlag(false)
                .setSynchronisationFlag(false)
                .setSegmentLengthIdentifier(3)
                .setOcf(new byte[]{0x00, 0x00, 0x00, 0x00})
                .setIdle();

        builder.addData(new byte[userDataLength]);
        TmTransferFrame frame = builder.build();

        byte[] encoded = ReedSolomonAlgorithm.TM_255_223.encodeFrame(frame.getFrameCopy());
        assertNotNull(encoded);
        assertEquals(1115 + 32 * 5, encoded.length);
    }
}