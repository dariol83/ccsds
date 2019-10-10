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

package eu.dariolucia.ccsds.sle.test;

import eu.dariolucia.ccsds.sle.utl.network.tml.ITmlChannelObserver;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannel;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannelException;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlDisconnectionReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TmlTest {

    @BeforeAll
    static void setLogLevel() {
        Logger.getLogger("eu.dariolucia").setLevel(Level.ALL);
        Arrays.stream(Logger.getLogger("eu.dariolucia").getHandlers()).forEach(o -> o.setLevel(Level.ALL));
    }

    @Test
    void testTmlHeartbeat() throws TmlChannelException, InterruptedException {
        ITmlChannelObserver clientObs = new ITmlChannelObserver() {
            @Override
            public void onChannelConnected(TmlChannel channel) {

            }

            @Override
            public void onChannelDisconnected(TmlChannel channel, TmlDisconnectionReasonEnum reason, PeerAbortReasonEnum peerAbortReason) {

            }

            @Override
            public void onPduReceived(TmlChannel channel, byte[] pdu) {

            }
        };
        TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 10100, 1, 3, clientObs, 0, 0);

        AtomicReference<TmlDisconnectionReasonEnum> disconnection = new AtomicReference<>();
        AtomicReference<PeerAbortReasonEnum> abortReason = new AtomicReference<>();
        ITmlChannelObserver serverObs = new ITmlChannelObserver() {
            @Override
            public void onChannelConnected(TmlChannel channel) {

            }

            @Override
            public void onChannelDisconnected(TmlChannel channel, TmlDisconnectionReasonEnum reason, PeerAbortReasonEnum peerAbortReason) {
                disconnection.set(reason);
                abortReason.set(peerAbortReason);
            }

            @Override
            public void onPduReceived(TmlChannel channel, byte[] pdu) {

            }
        };
        TmlChannel server = TmlChannel.createServerTmlChannel(10100, serverObs, 0, 0);

        server.connect();
        client.connect();

        // No messages exchanged for 4 seconds: verify readers are alive
        AwaitUtil.await(5000);

        assertTrue(server.isRunning());
        assertTrue(client.isRunning());

        // Abort the client
        client.abort((byte) 3);
        AwaitUtil.awaitCondition(1000, () -> disconnection.get() != null);
        assertEquals(TmlDisconnectionReasonEnum.REMOTE_PEER_ABORT, disconnection.get());
        assertEquals(PeerAbortReasonEnum.PROTOCOL_ERROR, abortReason.get());

        AwaitUtil.await(1000);

        server = TmlChannel.createServerTmlChannel(10100, serverObs, 0, 0);
        server.connect();
        client.connect();

        // No messages exchanged for 4 seconds: verify readers are alive
        AwaitUtil.await(5000);

        assertTrue(server.isRunning());
        assertTrue(client.isRunning());

        // Disconnect the server
        client.aboutToDisconnect();
        server.disconnect();
        client.disconnect();
    }
}
