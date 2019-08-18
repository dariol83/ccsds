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

package eu.dariolucia.ccsds.examples.tmproc;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.identifier.IPacketIdentifier;
import eu.dariolucia.ccsds.encdec.identifier.PacketAmbiguityException;
import eu.dariolucia.ccsds.encdec.identifier.PacketNotIdentifiedException;
import eu.dariolucia.ccsds.encdec.identifier.impl.FieldGroupBasedPacketIdentifier;
import eu.dariolucia.ccsds.encdec.structure.DecodingResult;
import eu.dariolucia.ccsds.encdec.structure.IPacketDecoder;
import eu.dariolucia.ccsds.encdec.structure.impl.DefaultPacketDecoder;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafTransferDataInvocation;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.IServiceInstanceListener;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstance;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TmReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.demux.VirtualChannelReceiverDemux;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

// Precondition: TM packet definitions to be decoded must have type 'TM'
public class SleTmProcessor {

    private final static String TM_PACKET_TYPE = "TM";

    // The path to the packet encoding definition file (special extension used).
    // This is a mandatory argument.
    private final static String ARGS_DEFINITION_PATH = "--definition";
    // The SLE configuration file.
    // This is a mandatory argument.
    private final static String ARGS_SLE_PATH = "--sle";
    // If set, the FECF will be expected as part of each frame.
    // If not set, no FECF is expected.
    private final static String ARGS_USE_FECF = "--fecf";

    // Instance fields
    private Definition definition = null;
    private String sleConfiguration = null;
    private boolean useFecf = false;

    private volatile boolean running = false;

    public SleTmProcessor() {
        // Nothing to do here
    }

    public void setDefinition(Definition definition) {
        checkState();
        this.definition = definition;
    }

    public void setUseFecf(boolean useFecf) {
        checkState();
        this.useFecf = useFecf;
    }

    public void setSleConfiguration(String sleConfiguration) {
        checkState();
        this.sleConfiguration = sleConfiguration;
    }

    private void checkState() {
        if(running) {
            throw new IllegalStateException("TM processor running, cannot configure");
        }
    }

    public void startReception() throws Exception {
        // Check if the definition is available
        if(this.definition == null) {
            throw new IllegalStateException("A valid Definition database is required");
        }
        // Check if socket is available
        if(this.sleConfiguration == null) {
            throw new IllegalStateException("A valid SLE configuration is required");
        }
        UtlConfigurationFile sleConfigurationFile = UtlConfigurationFile.load(new FileInputStream(this.sleConfiguration));
        // Look for the first RAF service instance
        Optional<ServiceInstanceConfiguration> selectedSleConfiguration = sleConfigurationFile.getServiceInstances()
                .stream()
                .filter(o -> o instanceof RafServiceInstanceConfiguration)
                .findFirst();
        if(selectedSleConfiguration.isEmpty()) {
            throw new IOException("A RAF service instance must be included in the SLE configuration file");
        }
        RafServiceInstanceConfiguration rafConfiguration = (RafServiceInstanceConfiguration) selectedSleConfiguration.get();
        this.running = true;

        // Prepare the TM processing chain

        // Create the RAF SLE service instance
        RafServiceInstance rafSi = new RafServiceInstance(sleConfigurationFile.getPeerConfiguration(), rafConfiguration);

        // Prepare the virtual channel objects (from 0 to 7)
        TmReceiverVirtualChannel[] virtualChannels = new TmReceiverVirtualChannel[8];
        for(int i = 0; i < virtualChannels.length; ++i) {
            virtualChannels[i] = new TmReceiverVirtualChannel(i, VirtualChannelAccessMode.Packet, true);
        }
        VirtualChannelReceiverDemux masterChannelDemuxer = new VirtualChannelReceiverDemux(virtualChannels);
        // Then the packet identifier and decoder
        IPacketIdentifier packetIdentifier = new FieldGroupBasedPacketIdentifier(this.definition, false, Collections.singletonList(TM_PACKET_TYPE));
        IPacketDecoder packetDecoder = new DefaultPacketDecoder(this.definition);
        // Now create a virtual channel receiver, which is called back when a new TM frame or space packet is received/decoded by a VC
        IVirtualChannelReceiverOutput vcOutput = buildVirtualChannelReceiverOutput(packetIdentifier, packetDecoder);
        // Register the output to all VCs
        Arrays.stream(virtualChannels).forEach(vc -> vc.register(vcOutput));

        // Register the SLE callback to process the incoming data
        rafSi.register(new IServiceInstanceListener() {
            @Override
            public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
                System.out.println("SLE state updated to " + state);
            }

            @Override
            public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
                if(operation instanceof RafTransferDataInvocation) {
                    // Get the frame
                    byte[] data = ((RafTransferDataInvocation) operation).getData().value;
                    // Create the frame object
                    TmTransferFrame frame = new TmTransferFrame(data, useFecf);
                    // Process the frame (packet extraction, packet identification, parameter extraction, parameter printing)
                    masterChannelDemuxer.processFrame(frame);
                }
                // Ignore the rest
            }

            @Override
            public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
                // No operation
            }

            @Override
            public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error, Exception exception) {
                System.out.println("SLE PDU error while sending: " + error);
            }

            @Override
            public void onPduDecodingError(ServiceInstance serviceInstance, byte[] encodedOperation) {
                System.out.println("SLE PDU error while decoding");
            }

            @Override
            public void onPduHandlingError(ServiceInstance serviceInstance, Object operation, byte[] encodedOperation) {
                System.out.println("SLE PDU error while handling");
            }
        });

        // Start reading and processing (assume positive results)

        // Bind the service instance
        rafSi.bind(rafConfiguration.getServiceVersionNumber());
        Thread.sleep(2000);
        // Start the service instance
        rafSi.start(null, null, RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY);

        // Wait forever
        while(running) {
            Thread.sleep(1000);
        }
        // That's it
    }

    private IVirtualChannelReceiverOutput buildVirtualChannelReceiverOutput(IPacketIdentifier packetIdentifier, IPacketDecoder packetDecoder) {
        return new IVirtualChannelReceiverOutput() {
                @Override
                public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                    System.out.printf("%s, %d, %d, %s, %d, %d, %s, %s, %s",
                            receivedFrame.getClass().getSimpleName(),
                            receivedFrame.getSpacecraftId(),
                            vc.getVirtualChannelId(),
                            "N/A",
                            receivedFrame.getLength(),
                            0,
                            "N/A",
                            "N/A",
                            receivedFrame.isValid() ? "good" : "bad");
                }

                @Override
                public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame lastFrame, byte[] packet, boolean qualityIndicator) {
                    SpacePacket sp = new SpacePacket(packet, qualityIndicator);
                    try {
                        // Identify the packet
                        String packetName = packetIdentifier.identify(packet);
                        // Log the packet
                        System.out.printf("%s, %d, %d, %s, %d, %d, %s, %s, %s",
                                sp.getClass().getSimpleName(),
                                lastFrame.getSpacecraftId(),
                                vc.getVirtualChannelId(),
                                packetName,
                                sp.getLength(),
                                sp.getApid(),
                                "N/A",
                                "N/A",
                                sp.isQualityIndicator() ? "good" : "bad");
                        // Decode the packet user data
                        DecodingResult result = packetDecoder.decode(packetName, sp.getPacket(), SpacePacket.SP_PRIMARY_HEADER_LENGTH, sp.getPacketDataLength());
                        // Print the encoded parameter values (flatten encoded composite items such as arrays or structures)
                        Map<String, Object> parameterMap = result.getDecodedItemsAsMap();
                        for(Map.Entry<String, Object> param : parameterMap.entrySet()) {
                            System.out.printf("%s, %d, %d, %s, %d, %d, %s, %s, %s",
                                    "Parameter",
                                    lastFrame.getSpacecraftId(),
                                    vc.getVirtualChannelId(),
                                    param.getKey(),
                                    0,
                                    0,
                                    Objects.toString(param.getValue(), "<null>"),
                                    param.getKey().getClass().getSimpleName(), // TODO: link to the definition somehow? Or way to request a definition by location to the Definition object?
                                    "N/A");
                        }
                    } catch (PacketNotIdentifiedException e) {
                        System.out.println("Packet not identified: " + e.getMessage() + "\n" + StringUtil.toHexDump(packet));
                    } catch (PacketAmbiguityException e) {
                        System.out.println("Packet ambiguity: " + e.getMessage() + "\n" + StringUtil.toHexDump(packet));
                    }
                }

                @Override
                public void dataExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data) {
                    // Not used
                }

                @Override
                public void bitstreamExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame frame, byte[] data, int numBits) {
                    // Not used
                }

                @Override
                public void gapDetected(AbstractReceiverVirtualChannel vc, int expectedVc, int receivedVc, int missingFrames) {
                    // Not used
                }
            };
    }

    public static void main(String[] args) throws Exception {
        SleTmProcessor tmGen = new SleTmProcessor();
        if(args.length == 0) {
            System.out.println("Usage: SleTmProcessor [argument]+");
            System.out.println();
            System.out.println("Supported arguments:");
            System.out.println("--definition <path>         absolute path to the encoding definition file. Mandatory");
            System.out.println("--sle <path>                absolute path to the SLE configuration file. Mandatory");
            System.out.println("--fecf                      expect the Frame Error Control Field. Default: not set");
            System.exit(-1);
        }
        for(int i = 0; i < args.length;) {
            switch(args[i]) {
                case ARGS_DEFINITION_PATH:
                    tmGen.setDefinition(Definition.load(new FileInputStream(args[i+1])));
                    i += 2;
                    break;
                case ARGS_SLE_PATH:
                    tmGen.setSleConfiguration(args[i+1]);
                    i += 2;
                    break;
                case ARGS_USE_FECF:
                    tmGen.setUseFecf(true);
                    i += 1;
                    break;
                default:
                    throw new IllegalArgumentException("Argument " + args[i] + " not recognized");
            }
        }

        // Start the reception
        tmGen.startReception();
    }
}
