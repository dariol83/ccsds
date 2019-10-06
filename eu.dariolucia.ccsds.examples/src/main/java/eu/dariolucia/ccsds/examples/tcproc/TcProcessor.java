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

package eu.dariolucia.ccsds.examples.tcproc;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.identifier.IPacketIdentifier;
import eu.dariolucia.ccsds.encdec.identifier.PacketAmbiguityException;
import eu.dariolucia.ccsds.encdec.identifier.PacketNotIdentifiedException;
import eu.dariolucia.ccsds.encdec.identifier.impl.FieldGroupBasedPacketIdentifier;
import eu.dariolucia.ccsds.encdec.structure.DecodingException;
import eu.dariolucia.ccsds.encdec.structure.DecodingResult;
import eu.dariolucia.ccsds.encdec.structure.IPacketDecoder;
import eu.dariolucia.ccsds.encdec.structure.impl.DefaultPacketDecoder;
import eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.CltuDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.CltuRandomizerDecoder;
import eu.dariolucia.ccsds.tmtc.coding.reader.IChannelReader;
import eu.dariolucia.ccsds.tmtc.coding.reader.SyncMarkerVariableLengthChannelReader;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TcReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.demux.VirtualChannelReceiverDemux;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

// Precondition: TM packets to be generated must have type 'TC'
public class TcProcessor {

    private final static String TC_PACKET_TYPE = "TC";

    private final static String TYPE_CLTU = "cltu";
    private final static String TYPE_FRAME = "frame";
    private final static String TYPE_PACKET = "packet";

    // If set, the TC is derandomized.
    // If not set, randomization is not used.
    private final static String ARGS_USE_RANDOMIZATION = "--randomize";
    // The path to the packet encoding definition file (special extension used).
    // This is a mandatory argument.
    private final static String ARGS_DEFINITION_PATH = "--definition";
    // The TCP/IP server port that will receive command invocations.
    // This is a mandatory argument.
    private final static String ARGS_SINK_TCP_PORT = "--out_tcp";
    // The type received by the application: can be cltu, frame, packet.
    // If not set, default is CLTU.
    private final static String ARGS_TYPE = "--type";
    // If set, the FECF will be expected.
    // If not set, no FECF will be expected.
    private final static String ARGS_USE_FECF = "--fecf";
    // If set, the TC segmentation is expected.
    // If not set,TC segmentation is not expected.
    public final static String ARGS_USE_SEGMENTATION = "--segment";

    // Instance fields
    private boolean useRandomization = false;
    private String type = TYPE_CLTU;
    private Definition definition = null;
    private ServerSocket serverSocket = null;
    private boolean useFecf = false;
    private boolean useSegmentation = false;

    private volatile boolean running = false;

    public TcProcessor() {
        // Nothing to do here
    }

    public void setUseRandomization(boolean useRandomization) {
        checkState();
        this.useRandomization = useRandomization;
    }

    public void setDefinition(Definition definition) {
        checkState();
        this.definition = definition;
    }

    public void setServerSocket(ServerSocket serverSocket) {
        checkState();
        this.serverSocket = serverSocket;
    }

    public void setUseFecf(boolean useFecf) {
        checkState();
        this.useFecf = useFecf;
    }

    public void setType(String type) {
        checkState();
        this.type = type;
    }

    public void setUseSegmentation(boolean useSegmentation) {
        checkState();
        this.useSegmentation = useSegmentation;
    }

    private void checkState() {
        if(running) {
            throw new IllegalStateException("TM generator running, cannot configure");
        }
    }

    public void startProcessing() {
        // Check if the definition is available
        if(this.definition == null) {
            throw new IllegalStateException("A valid Definition database is required");
        }
        this.running = true;

        // Start the thread to open the server and handle the connection
        while(running) {
            try {
                handleConnection(this.serverSocket.accept());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // That's it
    }

    private void handleConnection(Socket outSocket) throws IOException {
        // Create the channel reader (assume CLTU for now)
        IChannelReader reader = null;
        if(this.type.equals(TYPE_CLTU)) {
            reader = new SyncMarkerVariableLengthChannelReader(outSocket.getInputStream(), new byte[]{(byte) 0xEB, (byte) 0x90}, new byte[]{(byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, (byte) 0xC5, 0x79});
        } else {
            throw new UnsupportedOperationException("Type " + this.type + " not supported by this example application");
        }

        // Create the channel decoder with the specified options
        ChannelDecoder<TcTransferFrame> channelDecoder = ChannelDecoder.create(TcTransferFrame.decodingFunction(this.useSegmentation, this.useFecf));
        if(this.type.equals(TYPE_CLTU)) {
            // Add CLTU decoding
            channelDecoder.addDecodingFunction(new CltuDecoder());
        }
        if(this.useRandomization) {
            // Add a derandomizer
            channelDecoder.addDecodingFunction(new CltuRandomizerDecoder());
        }
        // Freeze the decoder configuration
        channelDecoder.configure();

        // Create the 8 VCs (0..7) for TC reception (assuming CLTU or frame transmission)
        TcReceiverVirtualChannel[] tcVcs = new TcReceiverVirtualChannel[8];
        for(int i = 0; i < 8; ++i) {
            tcVcs[i] = new TcReceiverVirtualChannel(i, VirtualChannelAccessMode.PACKET, true);
        }

        // Create the demuxer
        VirtualChannelReceiverDemux demux = new VirtualChannelReceiverDemux(tcVcs);

        // Create the processor to get space packets from virtual channels
        // This means create the packet identifier and decoder: for identification use only the type 'TC'
        IPacketIdentifier packetIdentifier = new FieldGroupBasedPacketIdentifier(this.definition, false, Collections.singletonList(TC_PACKET_TYPE));
        IPacketDecoder packetDecoder = new DefaultPacketDecoder(this.definition);
        // Now build the processor
        IVirtualChannelReceiverOutput outputProcessor = buildVirtualChannelReceiverOutput(packetIdentifier, packetDecoder);
        // Register the processor
        Arrays.stream(tcVcs).forEach(o -> o.register(outputProcessor));

        // Finally, process the input stream
        try {
            outSocket.setKeepAlive(true);
            // Monitor the connection
            while (outSocket.isConnected()) {
                byte[] data = reader.readNext();
                TcTransferFrame tcFrame = channelDecoder.apply(data);
                demux.processFrame(tcFrame);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                outSocket.close();
            } catch (IOException ex) {
                //
            }
        }
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
                } catch (DecodingException e) {
                    System.out.println("Packet decoding problem: " + e.getMessage() + "\n" + StringUtil.toHexDump(packet));
                }
            }
        };
    }

    public static void main(String[] args) throws IOException {
        TcProcessor tcProcessor = new TcProcessor();
        if(args.length == 0) {
            System.out.println("Usage: TcProcessor [argument]+");
            System.out.println();
            System.out.println("Supported arguments:");
            System.out.println("--definition <path>         absolute path to the encoding definition file. Mandatory");
            System.out.println("--out_tcp <port>            port number where TM frames will be written if a TCP client connects. Mandatory");
            System.out.println("--segment                   segmentation is active. Default: not set");
            System.out.println("--type <cltu|frame|packet>  received command type, can be cltu, frame (to be supported), packet (to be supported). Default: cltu");
            System.out.println("--randomize                 derandomize the TC frame. Default: not set");
            System.out.println("--fecf                      Frame Error Control Field present. Default: not present");
            System.exit(-1);
        }
        for(int i = 0; i < args.length;) {
            switch(args[i]) {
                case ARGS_USE_RANDOMIZATION:
                    tcProcessor.setUseRandomization(true);
                    i += 1;
                    break;
                case ARGS_DEFINITION_PATH:
                    tcProcessor.setDefinition(Definition.load(new FileInputStream(args[i+1])));
                    i += 2;
                    break;
                case ARGS_SINK_TCP_PORT:
                    ServerSocket ss = new ServerSocket(Integer.parseInt(args[i+1]));
                    tcProcessor.setServerSocket(ss);
                    i += 2;
                    break;
                case ARGS_USE_SEGMENTATION:
                    tcProcessor.setUseSegmentation(true);
                    i += 1;
                    break;
                case ARGS_USE_FECF:
                    tcProcessor.setUseFecf(true);
                    i += 1;
                    break;
                case ARGS_TYPE:
                    tcProcessor.setType(args[i+1]);
                    i += 2;
                    break;
                default:
                    throw new IllegalArgumentException("Argument " + args[i] + " not recognized");
            }
        }

        // Start the processing
        tcProcessor.startProcessing();
    }
}
