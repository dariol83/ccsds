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

package eu.dariolucia.ccsds.cfdp.ut.impl;

import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.decoder.CfdpPduDecoder;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UdpLayer extends AbstractUtLayer {

    private static final Logger LOG = Logger.getLogger(UdpLayer.class.getName());

    private volatile Thread readerThread; // NOSONAR: this rule is non-sense, as the language allows to do so
    private volatile DatagramSocket serverSocket; // NOSONAR: this rule is non-sense, as the language allows to do so
    private final int localUdpPort;

    private final Map<Long, DatagramSocket> id2sendingSocket = new ConcurrentHashMap<>();

    public UdpLayer(Mib mib, int localUdpPort) {
        super(mib);
        if(localUdpPort < 1 || localUdpPort > 65535) {
            throw new IllegalArgumentException("UDP port must be in the range 1 - 65535");
        }
        this.localUdpPort = localUdpPort;
    }

    @Override
    public String getName() {
        return "UDP";
    }

    @Override
    protected void handleRequest(CfdpPdu pdu, RemoteEntityConfigurationInformation destinationInfo) throws UtLayerException {
        long destinationEntityId = destinationInfo.getRemoteEntityId();
        DatagramSocket destinationSocket;
        // For each destination ID, we have a specific socket

        // The only purpose of this synchronized block is to avoid potential double insertion
        synchronized (id2sendingSocket) {
            destinationSocket = this.id2sendingSocket.get(destinationEntityId);
            if (destinationSocket == null) {
                // utAddress in the form of udp:<hostname>:<port>
                String utAddress = destinationInfo.getUtAddress();
                String[] fields = utAddress.split(":", -1);
                if (fields.length != 3) {
                    throw new UtLayerException(String.format("Cannot retrieve proper UT address for remote entity %d, expected format is udp:<hostname>:<port> but got %s", destinationEntityId, utAddress));
                }
                if (!fields[0].equals("udp")) {
                    throw new UtLayerException(String.format("Cannot retrieve proper UT address for remote entity %d, expected format is udp:<hostname>:<port> but got %s", destinationEntityId, utAddress));
                }
                InetAddress address;
                try {
                    address = InetAddress.getByName(fields[1]);
                } catch (UnknownHostException e) {
                    throw new UtLayerException(e);
                }
                int port = Integer.parseInt(fields[2]);
                if (port < 1 || port > 65535) {
                    throw new UtLayerException(String.format("Cannot retrieve proper UT address for remote entity %d, UDP port should be between 1 and 65535 but got %s", destinationEntityId, utAddress));
                }
                try {
                    destinationSocket = new DatagramSocket();
                } catch (SocketException e) {
                    throw new UtLayerException(e);
                }
                destinationSocket.connect(address, port);
                this.id2sendingSocket.put(destinationEntityId, destinationSocket);
            }
        }

        // Build the datagram and send it to the socket
        DatagramPacket dp = new DatagramPacket(pdu.getPdu(), pdu.getPdu().length);

        try {
            synchronized (destinationSocket) {
                destinationSocket.send(dp);
            }
        } catch (IOException e) {
            if (LOG.isLoggable(Level.WARNING)) {
                LOG.log(Level.WARNING, String.format("UT Layer %s: exception when sending PDU to entity %d: %s", getName(), destinationEntityId, e.getMessage()), e);
            }
            this.id2sendingSocket.remove(destinationEntityId);
            destinationSocket.close();
            throw new UtLayerException(e);
        }
    }

    @Override
    protected void handleActivate() throws UtLayerException {
        if(this.readerThread == null) {
            if(LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("UT Layer %s: opening server socket at port %d", getName(), this.localUdpPort));
            }
            // open server socket
            try {
                this.serverSocket = new DatagramSocket(this.localUdpPort);
            } catch (SocketException e) {
                throw new UtLayerException(e);
            }
            // start processing thread
            this.readerThread = new Thread(this::read, String.format("UDP UT Layer - %s receiver", getName()));
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }
    }

    private void read() {
        DatagramSocket sock = this.serverSocket;
        byte[] b = new byte[65536]; // 64K buffer
        while(isActivated()) {
            DatagramPacket dp = new DatagramPacket(b, 65536);
            try {
                sock.receive(dp);
            } catch (IOException e) {
                if(isActivated()) {
                    if (LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("Error during UT layer %s reception: %s", getName(), e.getMessage()), e);
                    }
                    // Something is wrong
                    deactivate();
                }
                return;
            }
            byte[] pdu = Arrays.copyOfRange(dp.getData(), 0, dp.getLength());
            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("UT Layer %s: datagram received from %s: %s", getName(), dp.getAddress(), StringUtil.toHexDump(pdu)));
            }
            try {
                CfdpPdu decoded = CfdpPduDecoder.decode(pdu);
                notifyPduReceived(decoded);
            } catch (Exception e) {
                // Error while decoding PDU
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("Cannot decode PDU received from UT layer %s, sender %s: %s", getName(), dp.getAddress(), e.getMessage()), e);
                }
                if(LOG.isLoggable(Level.FINE)) {
                    LOG.log(Level.FINE, String.format("Problematic PDU dump: %s", StringUtil.toHexDump(pdu)));
                }
            }
        }
    }

    @Override
    protected void handleDeactivate() {
        // close server socket
        if (this.serverSocket != null) {
            this.serverSocket.close();
            this.serverSocket = null;
        }
        // stop processing thread
        this.readerThread = null;

        // close and cleanup all client sockets
        for(Map.Entry<Long, DatagramSocket> entry : this.id2sendingSocket.entrySet()) {
            entry.getValue().close();
        }
        this.id2sendingSocket.clear();
    }

    @Override
    public String toString() {
        return "UdpLayer{" +
                "localUdpPort=" + localUdpPort +
                '}';
    }
}
