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

package eu.dariolucia.ccsds.tmtc.datalink.builder;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AosTransferFrameBuilderTest {

    @Test
    public void testAosFrameEncoding() {
        int userData = AosTransferFrameBuilder.computeUserDataLength(892, true, 0, AosTransferFrame.UserDataType.IDLE, true, true);
        AosTransferFrameBuilder b = AosTransferFrameBuilder.create(892, true, 0, AosTransferFrame.UserDataType.IDLE, true, true)
                .setSpacecraftId(123)
                .setVirtualChannelId(42)
                .setVirtualChannelFrameCount(0xFED123)
                .setReplayFlag(false)
                .setVirtualChannelFrameCountUsageFlag(true)
                .setVirtualChannelFrameCountCycle(10)
                .setIdle();

        int remaining = b.addData(new byte[userData]);
        assertEquals(0, remaining);

        b.setOcf(new byte[] { (byte) 0xAA, (byte) 0xAA, (byte) 0xAA, (byte) 0xAA });

        AosTransferFrame ttf = b.build();
        assertEquals(123, ttf.getSpacecraftId());
        assertEquals(42, ttf.getVirtualChannelId());
        assertEquals(0xFED123, ttf.getVirtualChannelFrameCount());
    }
}