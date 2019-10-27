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

import eu.dariolucia.ccsds.sle.utl.network.tml.*;
import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class TmlTest {

    @BeforeAll
    static void setLogLevel() {
        Logger.getLogger("eu.dariolucia").setLevel(Level.ALL);
        Arrays.stream(Logger.getLogger("eu.dariolucia").getHandlers()).forEach(o -> o.setLevel(Level.ALL));
    }

    @Test
    void testErrorCases() throws IOException, InterruptedException {
        ITmlChannelObserver obs = new ITmlChannelObserver() {
            @Override
            public void onChannelConnected(TmlChannel channel) {
                throw new RuntimeException("To prove resistance in case of failures from the observer");
            }

            @Override
            public void onChannelDisconnected(TmlChannel channel, TmlDisconnectionReasonEnum reason, PeerAbortReasonEnum peerAbortReason) {
                throw new RuntimeException("To prove resistance in case of failures from the observer");
            }

            @Override
            public void onPduReceived(TmlChannel channel, byte[] pdu) {
                throw new RuntimeException("To prove resistance in case of failures from the observer");
            }
        };
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32000, 5, 5, null, 0, 0);
            fail("Exception expected");
        } catch (NullPointerException e) {
            // OK
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel(null, 32000, 5, 5, obs, 0, 0);
            fail("Exception expected");
        } catch (NullPointerException e) {
            // OK
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("this_host_does_not_exist_I_hope", 32000, 5, 5, obs, 0, 0);
            client.connect();
            fail("Exception expected");
        } catch (TmlChannelException e) {
            assertTrue(e.getCause() instanceof UnknownHostException);
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32000, 5, 5, obs, 0, 0);
            client.connect();
            fail("Exception expected");
        } catch (TmlChannelException e) {
            assertTrue(e.getCause() instanceof IOException);
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32003, 2, 2, obs, 0, 0);
            client.sendPdu(new byte[4]);
            fail("Exception expected");
        } catch (TmlChannelException e) {
            // Good
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32000, 5, 5, obs, 0, 0);
            ServerSocket ss = new ServerSocket(32000);
            new Thread(() -> {
                try {
                    Socket s = ss.accept();
                    // Read the context message
                    byte[] ctxMessage = s.getInputStream().readNBytes(20);
                    assertEquals(20, ctxMessage.length);
                    // Write back wrong data
                    s.getOutputStream().write(new byte[] { 12, 1, 2, 3, 4, 5, 6, 7, 8});
                    // Close
                    s.close();
                    ss.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            client.connect();
            AwaitUtil.await(1000);
            client.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32001, 2, 2, obs, 0, 0);
            ServerSocket ss = new ServerSocket(32001);
            new Thread(() -> {
                try {
                    Socket s = ss.accept();
                    // Read the context message
                    byte[] ctxMessage = s.getInputStream().readNBytes(20);
                    assertEquals(20, ctxMessage.length);
                    // Do not do anything for 6 seconds
                    AwaitUtil.await(6000);
                    // Close
                    s.close();
                    ss.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            client.connect();
            AwaitUtil.await(1000);
            client.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        boolean correctStage = false;
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32002, 5, 2, obs, 0, 0);
            ServerSocket ss = new ServerSocket(32002);
            new Thread(() -> {
                try {
                    Socket s = ss.accept();
                    // Read the context message
                    byte[] ctxMessage = s.getInputStream().readNBytes(20);
                    assertEquals(20, ctxMessage.length);
                    // Do not do anything for 3 seconds
                    AwaitUtil.await(3000);
                    // Close
                    s.close();
                    ss.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            client.connect();
            correctStage = true;
            AwaitUtil.await(1000);
            client.connect();
            fail("Exception not expected");
        } catch (TmlChannelException e) {
            assertTrue(correctStage);
            // Good
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32003, 2, 2, obs, 0, 0);
            ServerSocket ss = new ServerSocket(32003);
            new Thread(() -> {
                try {
                    Socket s = ss.accept();
                    // Read the context message
                    byte[] ctxMessage = s.getInputStream().readNBytes(20);
                    assertEquals(20, ctxMessage.length);
                    AwaitUtil.await(500);
                    // Close
                    s.close();
                    ss.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            client.connect();
            AwaitUtil.await(1000);
            client.sendPdu(new byte[4]);
            fail("Exception expected");
        } catch (TmlChannelException e) {
            // Good
        }
        try {
            TmlChannel client = TmlChannel.createClientTmlChannel("localhost", 32004, 2, 2, obs, 0, 0);
            ServerSocket ss = new ServerSocket(32004);
            new Thread(() -> {
                try {
                    Socket s = ss.accept();
                    // Read the context message
                    byte[] ctxMessage = s.getInputStream().readNBytes(20);
                    assertEquals(20, ctxMessage.length);
                    AwaitUtil.await(500);
                    // Close
                    s.close();
                    ss.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            client.connect();
            AwaitUtil.await(1000);
            client.abort((byte) 3);
            assertFalse(client.isRunning());
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32005, obs, 0, 0);
            server.connect();
            Socket s = new Socket("localhost", 32005);
            new Thread(() -> {
                try {
                    // Send a wrong context message
                    s.getOutputStream().write(new byte[20]);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            AwaitUtil.await(1000);
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32006, obs, 0, 0);
            server.connect();
            Socket s = new Socket("localhost", 32006);
            new Thread(() -> {
                try {
                    // Send a correct context message
                    byte[] ctxMessage = new byte[] {0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x53, 0x50, 0x31, 0x00, 0x00, 0x00, 0x01, 0x00, 0x02, 0x00, 0x02}; // Dead factor 2, heartbeat 2
                    s.getOutputStream().write(ctxMessage);
                    s.getOutputStream().flush();
                    // Silence for 5 seconds
                    AwaitUtil.await(5000);
                    s.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            AwaitUtil.await(6000);
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32007, obs, 0, 0);
            server.connect();
            Socket s = new Socket("localhost", 32007);
            new Thread(() -> {
                try {
                    // Disconnect right away
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            AwaitUtil.await(1000);
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32008, obs, 0, 0);
            server.connect();
            Socket s = new Socket("localhost", 32008);
            new Thread(() -> {
                try {
                    AwaitUtil.await(2000);
                    // Disconnect right away
                    s.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            AwaitUtil.await(500);
            server.aboutToDisconnect();
            AwaitUtil.await(2000);
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32009, obs, 0, 0);
            server.connect();
            Socket s = new Socket("localhost", 32009);
            new Thread(() -> {
                try {
                    // Send a correct context message
                    byte[] ctxMessage = new byte[] {0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x53, 0x50, 0x31, 0x00, 0x00, 0x00, 0x01, 0x00, 0x0F, 0x00, 0x0F}; // Dead factor 15, heartbeat 15
                    s.getOutputStream().write(ctxMessage);
                    s.getOutputStream().flush();
                    // Send a good PDU
                    s.getOutputStream().write(new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x08}); // 8 bytes of PDU
                    s.getOutputStream().write(new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}); // we send 8 bytes
                    s.getOutputStream().flush();
                    // Send a corrupted PDU
                    s.getOutputStream().write(new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x14}); // 20 bytes of PDU
                    s.getOutputStream().write(new byte[]{0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}); // we send only 8 bytes
                    s.getOutputStream().flush();
                    // Close the stream
                    s.getOutputStream().close();
                    AwaitUtil.await(1000);
                    s.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            AwaitUtil.await(3000);
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32010, obs, 0, 0);
            server.connect();
            Socket s = new Socket("localhost", 32010);
            new Thread(() -> {
                try {
                    // Send a corrupted context message
                    byte[] ctxMessage = new byte[] {0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x53, 0x50, 0x31, 0x00, 0x00, 0x00, 0x01, 0x00}; // 17 bytes only instead of 20
                    s.getOutputStream().write(ctxMessage);
                    s.getOutputStream().flush();
                    // Close the stream
                    s.getOutputStream().close();
                    AwaitUtil.await(1000);
                    s.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            AwaitUtil.await(3000);
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32011, obs, 0, 0);
            server.connect();
            try {
                server.connect();
                fail("Exception expected");
            } catch (TmlChannelException e) {
                // Good
            }
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32012, obs, 0, 0);
            server.connect();
            Socket s = new Socket("localhost", 32012);
            new Thread(() -> {
                try {
                    AwaitUtil.await(4000);
                    s.close();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            AwaitUtil.await(2000);
            try {
                server.connect();
                fail("Exception expected");
            } catch (TmlChannelException e) {
                // Good
            }
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
        try {
            TmlChannel server = TmlChannel.createServerTmlChannel(32013, obs, 0, 0);
            server.connect();
            try {
                TmlChannel server2 = TmlChannel.createServerTmlChannel(32013, obs, 0, 0);
                server2.connect();
                fail("Exception expected");
            } catch (TmlChannelException e) {
                // Good
            }
            server.disconnect();
        } catch (TmlChannelException e) {
            e.printStackTrace();
            fail("Exception not expected");
        }
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
