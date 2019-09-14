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
import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.ReedSolomonDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmAsmDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.TmRandomizerDecoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.FixedLengthChannelReader;
import eu.dariolucia.ccsds.tmtc.coding.reader.IChannelReader;
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
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

// Precondition: TM packet definitions to be decoded must have type 'TM'
public class TmProcessor {

    private final static String TM_PACKET_TYPE = "TM";

    // If set, the CCSDS ASM will be expected before each frame
    // If not set, no ASM block is expected.
    private final static String ARGS_USE_ASM = "--asm";
    // If set, the Reed-Solomon codeblock will be expected after each frame
    // If not set, no RS block is expected.
    private final static String ARGS_USE_RS = "--rs";
    // The path to the packet encoding definition file (special extension used).
    // This is a mandatory argument.
    private final static String ARGS_DEFINITION_PATH = "--definition";
    // The host name to connect to.
    // If not set, the default is 'localhost'.
    private final static String ARGS_SOURCE_TCP_HOST = "--in_host";
    // The TCP port to use
    // This is a mandatory argument.
    private final static String ARGS_SOURCE_TCP_PORT = "--in_port";
    // If set, the FECF will be expected as part of each frame.
    // If not set, no FECF is expected.
    private final static String ARGS_USE_FECF = "--fecf";
    // The frame length in bytes.
    // If not provided, 1115 will be used.
    private final static String ARGS_FRAME_LENGTH = "--frame_length";
    // If provided, the frames are expected to be randomized, so derandomization will be performed.
    // If not provided, no derandomization will be applied.
    private final static String ARGS_USE_RANDOMIZATION = "--derandomize";

    // Instance fields
    private boolean useAsm = false;
    private boolean useRs = false;
    private Definition definition = null;
    private String host = "localhost";
    private int port = 0;
    private boolean useFecf = false;
    private boolean useDerandomization = false;
    private int frameLength = 1115;

    private volatile Socket socket = null;

    private volatile boolean running = false;

    public TmProcessor() {
        // Nothing to do here
    }

    public void setUseAsm(boolean useAsm) {
        checkState();
        this.useAsm = useAsm;
    }

    public void setUseRs(boolean useRs) {
        checkState();
        this.useRs = useRs;
    }

    public void setDefinition(Definition definition) {
        checkState();
        this.definition = definition;
    }

    public void setUseFecf(boolean useFecf) {
        checkState();
        this.useFecf = useFecf;
    }

    public void setFrameLength(int frameLength) {
        checkState();
        this.frameLength = frameLength;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setUseDerandomization(boolean useDerandomization) {
        checkState();
        this.useDerandomization = useDerandomization;
    }

    private void checkState() {
        if(running) {
            throw new IllegalStateException("TM processor running, cannot configure");
        }
    }

    public void startReception() throws IOException {
        // Check if the definition is available
        if(this.definition == null) {
            throw new IllegalStateException("A valid Definition database is required");
        }
        // Check if socket is available
        this.socket = new Socket(this.host, this.port);

        this.running = true;

        // Prepare the TM processing chain

        // First the channel reader: depending on the setup, use a fixed length reader
        IChannelReader channelReader = new FixedLengthChannelReader(this.socket.getInputStream(),
                this.frameLength
                + (this.useAsm ? TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER.length : 0) // Assume CCSDS ASM (4 bytes)
                + (this.useRs ? (this.frameLength/223) * 32 : 0)); // Assume a RS 223/255 encoding

        // Then the channel decoder
        ChannelDecoder<TmTransferFrame> channelDecoder = ChannelDecoder.create(TmTransferFrame.decodingFunction(this.useFecf));
        if(this.useAsm) {
            channelDecoder.addDecodingFunction(new TmAsmDecoder());
        }
        if(this.useDerandomization) {
            channelDecoder.addDecodingFunction(new TmRandomizerDecoder());
        }
        if(this.useRs) { // Full checking capabilities
            channelDecoder.addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, this.frameLength/223, true));
        }
        channelDecoder.configure();
        // Then the virtual channel objects (from 0 to 7)
        TmReceiverVirtualChannel[] virtualChannels = new TmReceiverVirtualChannel[8];
        for(int i = 0; i < virtualChannels.length; ++i) {
            virtualChannels[i] = new TmReceiverVirtualChannel(i, VirtualChannelAccessMode.Packet, true);
        }
        VirtualChannelReceiverDemux masterChannelDemuxer = new VirtualChannelReceiverDemux(virtualChannels);
        // Then the packet identifier and decoder: for identification use only the type 'TM'
        IPacketIdentifier packetIdentifier = new FieldGroupBasedPacketIdentifier(this.definition, false, Collections.singletonList(TM_PACKET_TYPE));
        IPacketDecoder packetDecoder = new DefaultPacketDecoder(this.definition);
        // Now create a virtual channel receiver, which is called back when a new TM frame or space packet is received/decoded by a VC
        IVirtualChannelReceiverOutput vcOutput = buildVirtualChannelReceiverOutput(packetIdentifier, packetDecoder);
        // Register the output to all VCs
        Arrays.stream(virtualChannels).forEach(vc -> vc.register(vcOutput));
        // Start reading and processing
        while(running) {
            // Read a frame
            byte[] data = channelReader.readNext();
            // Decode the frame
            TmTransferFrame frame = channelDecoder.apply(data);
            // Process the frame (packet extraction, packet identification, parameter extraction, parameter printing)
            masterChannelDemuxer.processFrame(frame);
        }
        // That's it
    }

    private IVirtualChannelReceiverOutput buildVirtualChannelReceiverOutput(IPacketIdentifier packetIdentifier, IPacketDecoder packetDecoder) {
        return new IVirtualChannelReceiverOutput() {
                @Override
                public void transferFrameReceived(AbstractReceiverVirtualChannel vc, AbstractTransferFrame receivedFrame) {
                    System.out.printf("%s, %d, %d, %s, %d, %d, %s, %s, %s\n",
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
                public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                    SpacePacket sp = new SpacePacket(packet, qualityIndicator);
                    try {
                        // Identify the packet
                        String packetName = packetIdentifier.identify(packet);
                        // Log the packet
                        System.out.printf("%s, %d, %d, %s, %d, %d, %s, %s, %s\n",
                                sp.getClass().getSimpleName(),
                                firstFrame.getSpacecraftId(),
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
                            System.out.printf("%s, %d, %d, %s, %d, %d, %s, %s, %s\n",
                                    "Parameter",
                                    firstFrame.getSpacecraftId(),
                                    vc.getVirtualChannelId(),
                                    param.getKey(),
                                    0,
                                    0,
                                    Objects.toString(param.getValue(), "<null>"),
                                    param.getValue().getClass().getSimpleName(), // TODO: link to the definition somehow?
                                    "N/A");
                        }
                    } catch (PacketNotIdentifiedException e) {
                        System.out.println("Packet not identified: " + e.getMessage() + "\n" + StringUtil.toHexDump(packet));
                    } catch (PacketAmbiguityException e) {
                        System.out.println("Packet ambiguity: " + e.getMessage() + "\n" + StringUtil.toHexDump(packet));
                    }
                }
            };
    }

    public static void main(String[] args) throws IOException {
        TmProcessor tmGen = new TmProcessor();
        if(args.length == 0) {
            System.out.println("Usage: TmProcessor [argument]+");
            System.out.println();
            System.out.println("Supported arguments:");
            System.out.println("--definition <path>         absolute path to the encoding definition file. Mandatory");
            System.out.println("--frame_length <value>      length of the TM frame in bytes. Default: 1115");
            System.out.println("--in_host <value>           host name to connect to. Default: localhost");
            System.out.println("--in_port <value>           port number where TM frames will be read from. Mandatory.");
            System.out.println("--asm                       expect ASM block. Default: not set");
            System.out.println("--rs                        expect the RS codeblock. Default: not set");
            System.out.println("--fecf                      expect the Frame Error Control Field. Default: not set");
            System.out.println("--derandomize               derandomize the frame. Default: not set");
            System.exit(-1);
        }
        for(int i = 0; i < args.length;) {
            switch(args[i]) {
                case ARGS_DEFINITION_PATH:
                    tmGen.setDefinition(Definition.load(new FileInputStream(args[i+1])));
                    i += 2;
                    break;
                case ARGS_FRAME_LENGTH:
                    tmGen.setFrameLength(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_SOURCE_TCP_HOST:
                    tmGen.setHost(args[i+1]);
                    i += 2;
                    break;
                case ARGS_SOURCE_TCP_PORT:
                    tmGen.setPort(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_USE_ASM:
                    tmGen.setUseAsm(true);
                    i += 1;
                    break;
                case ARGS_USE_RS:
                    tmGen.setUseRs(true);
                    i += 1;
                    break;
                case ARGS_USE_FECF:
                    tmGen.setUseFecf(true);
                    i += 1;
                    break;
                case ARGS_USE_RANDOMIZATION:
                    tmGen.setUseDerandomization(true);
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
