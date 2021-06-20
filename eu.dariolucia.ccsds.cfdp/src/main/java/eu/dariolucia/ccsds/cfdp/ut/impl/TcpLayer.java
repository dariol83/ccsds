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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TcpLayer extends AbstractUtLayer {

    private static final Logger LOG = Logger.getLogger(TcpLayer.class.getName());

    private volatile Thread readerThread; // NOSONAR: this rule is non-sense, as the language allows to do so
    private volatile ServerSocket serverSocket; // NOSONAR: this rule is non-sense, as the language allows to do so
    private final int localTcpPort;

    private final Map<Long, Socket> id2sendingSocket = new ConcurrentHashMap<>();

    public TcpLayer(Mib mib, int localTcpPort) {
        super(mib);
        if(localTcpPort < 1 || localTcpPort > 65535) {
            throw new IllegalArgumentException("TCP port must be in the range 1 - 65535");
        }
        this.localTcpPort = localTcpPort;
    }

    @Override
    public String getName() {
        return "TCP";
    }

    @Override
    public void request(CfdpPdu pdu, long destinationEntityId) throws UtLayerException {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("UT Layer %s: requesting transmission of PDU %s to entity %d", getName(), pdu, destinationEntityId));
        }
        Socket ds;
        synchronized (this) {
            // If the destination is not available for TX, exception
            if (!isActivated() || !getTxAvailability(destinationEntityId)) {      // NOSONAR: concurrent hash maps do not accept null values
                throw new UtLayerException(String.format("TX not available for destination entity %d", destinationEntityId));
            }
            // For each destination ID, we have a specific socket
            ds = this.id2sendingSocket.get(destinationEntityId);
            if (ds == null) {
                RemoteEntityConfigurationInformation conf = getMib().getRemoteEntityById(destinationEntityId);
                if (conf == null) {
                    throw new UtLayerException("Cannot retrieve connection information for remote entity " + destinationEntityId);
                }
                if(LOG.isLoggable(Level.FINER)) {
                    LOG.log(Level.FINER, String.format("UT Layer %s: connection to entity %d using address string %s", getName(), destinationEntityId, conf.getUtAddress()));
                }
                // utAddress in the form of tcp:<hostname>:<port>
                String utAddress = conf.getUtAddress();
                String[] fields = utAddress.split(":", -1);
                if (fields.length != 3) {
                    throw new UtLayerException(String.format("Cannot retrieve proper UT address for remote entity %d, expected format is tcp:<hostname>:<port> but got %s", destinationEntityId, utAddress));
                }
                if (!fields[0].equals("tcp")) {
                    throw new UtLayerException(String.format("Cannot retrieve proper UT address for remote entity %d, expected format is tcp:<hostname>:<port> but got %s", destinationEntityId, utAddress));
                }
                InetAddress address;
                try {
                    address = InetAddress.getByName(fields[1]);
                } catch (UnknownHostException e) {
                    throw new UtLayerException(e);
                }
                int port = Integer.parseInt(fields[2]);
                if (port < 1 || port > 65535) {
                    throw new UtLayerException(String.format("Cannot retrieve proper UT address for remote entity %d, TCP port should be between 1 and 65535 but got %s", destinationEntityId, utAddress));
                }
                try {
                    ds = new Socket(address, port);
                    if(LOG.isLoggable(Level.INFO)) {
                        LOG.log(Level.INFO, String.format("UT Layer %s: connection to entity %d at %s:%d string %s established", getName(), destinationEntityId, address, port, conf.getUtAddress()));
                    }
                } catch (IOException e) {
                    throw new UtLayerException(e);
                }
                this.id2sendingSocket.put(destinationEntityId, ds);
            }
        }

        // Send the PDU to the socket
        synchronized (ds) {
            try {
                OutputStream ostr = ds.getOutputStream();
                if(ostr == null) {
                    throw new IOException("No output stream available: null");
                }
                ds.getOutputStream().write(pdu.getPdu());
            } catch (IOException e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("UT Layer %s: exception when sending PDU to entity %d: %s", getName(), destinationEntityId, e.getMessage()), e);
                }
                this.id2sendingSocket.remove(destinationEntityId);
                try {
                    ds.close();
                } catch (IOException ioException) {
                    // Ignore here
                }
                throw new UtLayerException(e);
            }
        }
    }

    @Override
    public synchronized void activate() throws UtLayerException {
        super.activate();
        if(this.readerThread == null) {
            // open server socket
            if(LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("UT Layer %s: opening server socket at port %d", getName(), this.localTcpPort));
            }
            try {
                this.serverSocket = new ServerSocket(this.localTcpPort);
            } catch (IOException e) {
                throw new UtLayerException(e);
            }
            // start processing thread
            this.readerThread = new Thread(this::accept, String.format("TCP UT Layer - %s receiver", getName()));
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }
    }

    private void accept() {
        while(isActivated()) {
            Socket sock;
            try {
                sock = this.serverSocket.accept();
            } catch (IOException e) {
                // If it was activated and nobody triggered the deactivation, then we have to do something about it
                if(isActivated()) {
                    if (LOG.isLoggable(Level.SEVERE)) {
                        LOG.log(Level.SEVERE, String.format("Error during UT layer %s reception (accept): %s", getName(), e.getMessage()), e);
                    }
                    // Something is wrong
                    try {
                        deactivate();
                    } catch (UtLayerException utLayerException) {
                        if (LOG.isLoggable(Level.SEVERE)) {
                            LOG.log(Level.SEVERE, String.format("Error while deactivating UT layer %s after failure in reception: %s", getName(), e.getMessage()), utLayerException);
                        }
                    }
                }
                return;
            }
            if(LOG.isLoggable(Level.INFO)) {
                LOG.log(Level.INFO, String.format("UT Layer %s: new connection received from %s", getName(), sock.getRemoteSocketAddress()));
            }
            final Socket fsock = sock;
            Thread t = new Thread(() -> handle(fsock), "TCP UT Connection Handler - " + sock.getRemoteSocketAddress());
            t.setDaemon(true);
            t.start();
        }
    }

    private void handle(Socket sock) {
        // Get the inputstream
        InputStream is;
        try {
            is = sock.getInputStream();
        } catch (IOException e) {
            if(isActivated() && LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("Error during UT layer %s reception: %s", getName(), e.getMessage()), e);
            }
            // Something is wrong: close the connection
            try {
                sock.close();
            } catch (IOException ioException) {
                // Ignore
            }
            return;
        }
        // Start reading it
        while(isActivated()) {
            CfdpPdu data;
            try {
                data = CfdpPduDecoder.decode(is);
                if(LOG.isLoggable(Level.FINEST)) {
                    LOG.log(Level.FINEST, String.format("UT Layer %s: new PDU received from %s - %s", getName(), sock.getRemoteSocketAddress(), data));
                }
            } catch (Exception e) {
                if(isActivated() && LOG.isLoggable(Level.SEVERE)) {
                    LOG.log(Level.SEVERE, String.format("Error during UT layer %s reception: %s", getName(), e.getMessage()), e);
                }
                // Something is wrong: close the connection
                try {
                    sock.close();
                } catch (IOException ioException) {
                    // Ignore
                }
                return;
            }
            notifyPduReceived(data);
        }
    }

    @Override
    public void deactivate() throws UtLayerException {
        super.deactivate();
        synchronized (this) {
            // close server socket
            if (this.serverSocket != null) {
                if(LOG.isLoggable(Level.INFO)) {
                    LOG.log(Level.INFO, String.format("UT Layer %s: closing server socket at port %d", getName(), this.localTcpPort));
                }
                try {
                    this.serverSocket.close();
                } catch (IOException e) {
                    // Ignore
                }
                this.serverSocket = null;
            }
            // stop processing thread
            this.readerThread = null;
        }
        // close and cleanup all client sockets
        for(Map.Entry<Long, Socket> entry : this.id2sendingSocket.entrySet()) {
            synchronized (entry.getValue()) {
                try {
                    entry.getValue().close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        synchronized (this) {
            this.id2sendingSocket.clear();
        }
    }

    @Override
    public String toString() {
        return "TcpLayer{" +
                "localTcpPort=" + localTcpPort +
                '}';
    }
}
