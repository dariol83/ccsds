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

package eu.dariolucia.ccsds.examples.tcgen;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuTransferDataInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuAsyncNotifyInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuTransferDataReturn;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.IServiceInstanceListener;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuServiceInstance;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;

// It uses the class TcGenerator.
public class SleTcGenerator {

    // The SLE configuration file.
    // This is a mandatory argument.
    private final static String ARGS_SLE_PATH = "--sle";

    // The first CLTU ID.
    // If not set, it is set to 0.
    private final static String ARGS_CLTU_ID = "--cltu_id";

    // Instance fields
    private String sleConfiguration = null;
    private int firstCltuId = 0;

    private volatile boolean running = false;
    private CltuServiceInstance cltuServiceInstance;

    public SleTcGenerator() {
        // Nothing to do here
    }

    public void setSleConfiguration(String sleConfiguration) {
        checkState();
        this.sleConfiguration = sleConfiguration;
    }

    public void setFirstCltuId(int firstCltuId) {
        checkState();
        this.firstCltuId = firstCltuId;
    }

    private void checkState() {
        if(running) {
            throw new IllegalStateException("TM processor running, cannot configure");
        }
    }

    public void startReception() throws Exception {
        // Check if socket is available
        if(this.sleConfiguration == null) {
            throw new IllegalStateException("A valid SLE configuration is required");
        }
        UtlConfigurationFile sleConfigurationFile = UtlConfigurationFile.load(new FileInputStream(this.sleConfiguration));
        // Look for the first CLTU service instance
        Optional<ServiceInstanceConfiguration> selectedSleConfiguration = sleConfigurationFile.getServiceInstances()
                .stream()
                .filter(o -> o instanceof CltuServiceInstanceConfiguration)
                .findFirst();
        if(selectedSleConfiguration.isEmpty()) {
            throw new IOException("A CLTU service instance must be included in the SLE configuration file");
        }
        CltuServiceInstanceConfiguration cltuConfiguration = (CltuServiceInstanceConfiguration) selectedSleConfiguration.get();
        this.running = true;

        // Create the CLTU SLE service instance
        this.cltuServiceInstance = new CltuServiceInstance(sleConfigurationFile.getPeerConfiguration(), cltuConfiguration);
        this.cltuServiceInstance.configure();
        // Register the SLE callback to process the incoming data
        this.cltuServiceInstance.register(new IServiceInstanceListener() {
            @Override
            public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
                System.out.println("SLE state updated to " + state);
            }

            @Override
            public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
                // Log only CLTU-TRANSFER-DATA return and ASYNC-NOTIFY
                if(operation instanceof CltuTransferDataReturn) {
                    System.out.println("\tCLTU-TRANSFER-DATA " + ((CltuTransferDataReturn) operation).getCltuIdentification().intValue() + " "
                            + (((CltuTransferDataReturn) operation).getResult().getPositiveResult() != null ? " accepted" : " rejected ") );
                } else if(operation instanceof CltuAsyncNotifyInvocation) {
                    CltuAsyncNotifyInvocation as = (CltuAsyncNotifyInvocation) operation;
                    if(as.getCltuLastOk() != null) {
                        if(as.getCltuLastOk().getCltuOk() != null) {
                            System.out.println("\tCLTU-ASYNC-NOTIFY Last OK CLTU " + as.getCltuLastOk().getCltuOk().getCltuIdentification().intValue());
                        } else if(as.getCltuLastOk().getNoCltuOk() != null) {
                            System.out.println("\tCLTU-ASYNC-NOTIFY No last OK CLTU");
                        }
                    }
                    if(as.getCltuLastProcessed() != null) {
                        if(as.getCltuLastProcessed().getCltuProcessed() != null) {
                            System.out.println("\tCLTU-ASYNC-NOTIFY Last processed CLTU " + as.getCltuLastProcessed().getCltuProcessed().getCltuIdentification().intValue());
                        } else if(as.getCltuLastProcessed().getNoCltuProcessed() != null) {
                            System.out.println("\tCLTU-ASYNC-NOTIFY No last processes CLTU");
                        }
                    }
                    if(as.getCltuNotification() != null) {
                        if(as.getCltuNotification().getCltuRadiated() != null) {
                            System.out.println("\tCLTU-ASYNC-NOTIFY CLTU radiated");
                        }
                    }
                }
            }

            @Override
            public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
                if(operation instanceof CltuTransferDataInvocation) {
                    System.out.println("\tCLTU-TRANSFER-DATA " + ((CltuTransferDataInvocation) operation).getCltuIdentification().intValue() + " sent");
                }
            }

            @Override
            public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error, Exception exception) {
                System.out.println("\tSLE PDU error while sending: " + error);
            }

            @Override
            public void onPduDecodingError(ServiceInstance serviceInstance, byte[] encodedOperation) {
                System.out.println("\tSLE PDU error while decoding");
            }

            @Override
            public void onPduHandlingError(ServiceInstance serviceInstance, Object operation, byte[] encodedOperation) {
                System.out.println("\tSLE PDU error while handling");
            }
        });

        // Bind the service instance
        this.cltuServiceInstance.bind(cltuConfiguration.getServiceVersionNumber());
        Thread.sleep(2000);
        // Start the service instance
        this.cltuServiceInstance.start((long) this.firstCltuId);
        // The call is asynchronous so the thread continues and can be used to deliver commands
    }

    private void forwardCltu(byte[] bytes) {
        try {
            this.cltuServiceInstance.transferData(this.firstCltuId, null, null, 1000000000, true, bytes);
        } catch (Exception e) {
            System.out.println("\tException when sending CLTU: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        SleTcGenerator sleGen = new SleTcGenerator();
        TcGenerator tcGen = new TcGenerator();
        if(args.length == 0) {
            System.out.println("Usage: TcGenerator [argument]+");
            System.out.println();
            System.out.println("Supported arguments:");
            System.out.println("--definition <path>         absolute path to the encoding definition file. Mandatory");
            System.out.println("--scid <value>              spacecraft ID in decimal format. Default: 0");
            System.out.println("--vcid <value>              TC VC ID in decimal format. Default: 0");
            System.out.println("--out_file                  absolute path to the file where TC frames will be written. Default: disabled");
            System.out.println("--sle <path>                absolute path to the SLE configuration file. Mandatory");
            System.out.println("--cltu_id <value>           first CLTU id. Default: 0");
            System.out.println("--segment                   use TC packet segmentation. Default: not used");
            System.out.println("--randomize                 randomize the frame. Default: not set");
            System.out.println("--fecf                      generate the Frame Error Control Field. Default: not generated");
            System.exit(-1);
        }

        for(int i = 0; i < args.length;) {
            switch(args[i]) {
                case ARGS_SLE_PATH:
                    sleGen.setSleConfiguration(args[i+1]);
                    i += 2;
                    break;
                case ARGS_CLTU_ID:
                    sleGen.setFirstCltuId(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case TcGenerator.ARGS_DEFINITION_PATH:
                    tcGen.setDefinition(Definition.load(new FileInputStream(args[i+1])));
                    i += 2;
                    break;
                case TcGenerator.ARGS_SCID:
                    tcGen.setScId(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case TcGenerator.ARGS_VCID:
                    tcGen.setVcId(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case TcGenerator.ARGS_SINK_FILE:
                    File f = new File(args[i+1]);
                    tcGen.setOutFile(new FileOutputStream(f, f.exists()));
                    i += 2;
                    break;
                case TcGenerator.ARGS_USE_FECF:
                    tcGen.setUseFecf(true);
                    i += 1;
                    break;
                case TcGenerator.ARGS_USE_SEGMENTATION:
                    tcGen.setUseSegmentation(true);
                    i += 1;
                    break;
                case TcGenerator.ARGS_USE_RANDOMIZATION:
                    tcGen.setUseRandomization(true);
                    i += 1;
                    break;
                default:
                    throw new IllegalArgumentException("Argument " + args[i] + " not recognized");
            }
        }

        // Register the SLE interface to the TC Generator
        tcGen.setOptionalConsumer(sleGen::forwardCltu);

        // Start the SLE interface
        sleGen.startReception();

        // Start the TC generator
        tcGen.startGeneration();
    }
}
