/*
 *   Copyright (c) 2024 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.examples.sle;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafStartInvocation;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.si.LockStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.ProductionStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.ReturnServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceBindingStateEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafStartResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * This class implements an example on how to implement a simple SLE RAF (Return All Frames) service instance, which
 * delivers frames received by connecting to a specified TCP/IP port.
 * The characteristics of the TCP/IP connection, of the TM frames and of the SLE service instance are specified via command
 * line and in the SLE configuration file. The application runs until killed.
 * The application can be started with the following arguments: path to SLE file, source host, source port, frame length in bytes.
 */
public class SleDataProvision {

    private final String sourceHost;
    private final int sourcePort;
    private final int frameLength;
    private volatile boolean running = false;
    private RafServiceInstanceProvider provider;
    private Thread connectionThread;
    private final RafServiceInstanceConfiguration siConfiguration;
    private final PeerConfiguration peerConfiguration;

    public SleDataProvision(String configurationFilePath, String sourceHost, int sourcePort, int frameLength) throws IOException {
        // Use the file format and classes as defined by the sle.utl module
        UtlConfigurationFile sleConfigurationFile = UtlConfigurationFile.load(new FileInputStream(configurationFilePath));
        // Look for the first RAF service instance
        Optional<ServiceInstanceConfiguration> selectedSleConfiguration = sleConfigurationFile.getServiceInstances()
                .stream()
                .filter(o -> o instanceof RafServiceInstanceConfiguration)
                .findFirst();
        if(selectedSleConfiguration.isEmpty()) {
            throw new IOException("A RAF service instance must be included in the SLE configuration file");
        }
        this.siConfiguration = (RafServiceInstanceConfiguration) selectedSleConfiguration.get();
        this.peerConfiguration = sleConfigurationFile.getPeerConfiguration();
        this.sourceHost = sourceHost;
        this.sourcePort = sourcePort;
        this.frameLength = frameLength;
    }

    public void startProvision() {
        if(running) {
            return;
        }
        running = true;
        // Create the SLE RAF service instance
        provider = new RafServiceInstanceProvider(peerConfiguration, siConfiguration);
        provider.setStartOperationHandler(this::startOperationReceived);
        // Now configure the provider: with this operation, the provider configuration is fixed
        provider.configure();
        provider.setUnbindReturnBehaviour(true);
        // Now activate the bind reception: with this operation, the provider waits for the bind
        provider.waitForBind(true, null);
        // We are now ready to go: let's start a thread that tries to connect to the specified host and port and retrieves the TM frames
        connectionThread = new Thread(this::handleTmConnection);
        connectionThread.setDaemon(true);
        connectionThread.start();
        // Done
    }

    private void handleTmConnection() {
        boolean inLock = false;
        while(this.running) {
            Socket socket = null;
            // Connect to the server
            try {
                socket = new Socket(this.sourceHost, this.sourcePort);
                // If we can connect, we simulate a carrier lock
                this.provider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK,
                        LockStatusEnum.IN_LOCK, LockStatusEnum.OUT_OF_LOCK, ProductionStatusEnum.INTERRUPTED);
                // We set a socket timeout to indicate that we want to have an exception if we don't read anything within
                // the configured amount of milliseconds
                socket.setSoTimeout(5000);
                // We read frameLength data, and we try to push it to the provider
                while(this.running) {
                    try {
                        byte[] frame = socket.getInputStream().readNBytes(this.frameLength);
                        if (frame != null && frame.length == this.frameLength) {
                            // We have a frame, let's update the production status...
                            if (!inLock) {
                                this.provider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK,
                                        LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, ProductionStatusEnum.RUNNING);
                                inLock = true;
                            }
                            // ... and let's forward the frame
                            if(this.provider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.ACTIVE) {
                                // We transfer data only if the provider is active, otherwise we ignore
                                this.provider.transferData(frame, ReturnServiceInstanceProvider.FRAME_QUALITY_GOOD,
                                        0, Instant.now(), false, PduStringUtil.toHexDump("ANT1".getBytes(StandardCharsets.ISO_8859_1)),
                                        false, new byte[0]);
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        // Can happen, try to read again but report out of lock for frame sync
                        this.provider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK,
                                LockStatusEnum.IN_LOCK, LockStatusEnum.OUT_OF_LOCK, ProductionStatusEnum.RUNNING);
                        inLock = false;
                    }
                }
            } catch (IOException e) {
                if(socket != null) {
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        // Nothing to be done here
                    }
                    socket = null;
                }
                // If connection fail, report production status interrupted and retry with hardcoded period (1 second)
                if(this.running && this.provider != null) {
                    inLock = false;
                    provider.updateProductionStatus(Instant.now(), LockStatusEnum.NOT_IN_USE, LockStatusEnum.NOT_IN_USE,
                            LockStatusEnum.NOT_IN_USE, LockStatusEnum.OUT_OF_LOCK, ProductionStatusEnum.INTERRUPTED);
                }
                // Wait before trying again
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    //
                }
            }
        }
    }

    private RafStartResult startOperationReceived(RafStartInvocation rafStartInvocation) {
        // Always accept start operations
        return RafStartResult.noError();
    }

    /**
     * Application entry point.
     *
     * @param args the application arguments:
     */
    public static void main(String[] args) throws IOException {
        // No check on command line argument: this is an example
        SleDataProvision sleService = new SleDataProvision(args[0], args[1], Integer.parseInt(args[2]), Integer.parseInt(args[3]));
        // Start the reception and forwarding
        sleService.startProvision();
    }
}
