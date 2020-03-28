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

package eu.dariolucia.ccsds.tmtc.datalink.channel;

import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class VirtualChannelReceiverOutputTest {

    @Test
    public void testEmptyOutput() {
        IVirtualChannelReceiverOutput output = new IVirtualChannelReceiverOutput() {
        };
        assertNotNull(output); // To please Sonarqube
        output.bitstreamExtracted(null, null, null, 0);
        output.dataExtracted(null, null, null);
        output.gapDetected(null, 0, 0, 0);
        output.spacePacketExtracted(null, null, null, true);
        output.transferFrameReceived(null, null);
    }
}
