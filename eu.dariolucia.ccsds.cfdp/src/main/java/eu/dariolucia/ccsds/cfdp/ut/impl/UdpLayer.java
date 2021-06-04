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
    public void request(CfdpPdu pdu, long destinationEntityId) throws UtLayerException {
        DatagramSocket ds;
        synchronized (this) {
            // If the destination is not available for TX, exception
            if (!isActivated() || id2txAvailable.computeIfAbsent(destinationEntityId, k -> true)) {      // NOSONAR: concurrent hash maps do not accept null values
                throw new UtLayerException(String.format("TX not available for destination entity %d", destinationEntityId));
            }
            // For each destination ID, we have a specific socket
            ds = this.id2sendingSocket.get(destinationEntityId);
            if (ds == null) {
                RemoteEntityConfigurationInformation conf = getMib().getRemoteEntityById(destinationEntityId);
                if (conf == null) {
                    throw new UtLayerException("Cannot retrieve connection information for remote entity " + destinationEntityId);
                }
                // utAddress in the form of udp:<hostname>:<port>
                String utAddress = conf.getUtAddress();
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
                    ds = new DatagramSocket();
                } catch (SocketException e) {
                    throw new UtLayerException(e);
                }
                ds.connect(address, port);
                this.id2sendingSocket.put(destinationEntityId, ds);
            }
        }

        // Build the datagram and send it to the socket
        DatagramPacket dp = new DatagramPacket(pdu.getPdu(), pdu.getPdu().length);

        synchronized (ds) {
            try {
                ds.send(dp);
            } catch (IOException e) {
                this.id2sendingSocket.remove(destinationEntityId);
                ds.close();
                throw new UtLayerException(e);
            }
        }
    }

    @Override
    public synchronized void activate() throws UtLayerException {
        super.activate();
        if(this.readerThread == null) {
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
                if(LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("Error during UT layer %s reception: %s", getName(), e.getMessage()), e);
                }
                // Something is wrong
                try {
                    deactivate();
                } catch (UtLayerException utLayerException) {
                    if(LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("Error while deactivating UT layer %s after failure in reception: %s", getName(), e.getMessage()), utLayerException);
                    }
                }
                return;
            }
            byte[] pdu = Arrays.copyOfRange(dp.getData(), 0, dp.getLength());
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
    public void deactivate() throws UtLayerException {
        super.deactivate();
        synchronized (this) {
            // close server socket
            if (this.serverSocket != null) {
                this.serverSocket.close();
                this.serverSocket = null;
            }
            // stop processing thread
            this.readerThread = null;
        }
        // close and cleanup all client sockets
        for(Map.Entry<Long, DatagramSocket> entry : this.id2sendingSocket.entrySet()) {
            synchronized (entry.getValue()) {
                entry.getValue().close();
            }
        }
        synchronized (this) {
            this.id2sendingSocket.clear();
        }
    }
}
