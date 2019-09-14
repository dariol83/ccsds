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
import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.definition.IdentFieldMatcher;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.IPacketEncoder;
import eu.dariolucia.ccsds.encdec.structure.PathLocation;
import eu.dariolucia.ccsds.encdec.structure.impl.DefaultPacketEncoder;
import eu.dariolucia.ccsds.encdec.structure.resolvers.IdentificationFieldBasedResolver;
import eu.dariolucia.ccsds.encdec.value.BitString;
import eu.dariolucia.ccsds.tmtc.coding.ChannelEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.CltuEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.CltuRandomizerEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.IVirtualChannelSenderOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TcSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

// Precondition: each packet definition contains a field match pointing to an identification field called APID, used to set the APID. Mandatory.
// Precondition: if a packet definition contains a field named as an identification field, the expected identification field value is used (check implementation of the PacketBasedEncodeResolver
// Precondition: TC packets have type 'TC'
public class TcGenerator {

    private final static String TC_PACKET_TYPE = "TC";

    private final static String FIELD_APID_NAME = "APID";

    private final static String TYPE_CLTU = "cltu";
    private final static String TYPE_FRAME = "frame";
    private final static String TYPE_PACKET = "packet";

    private final static String CMD_AD = "send_ad";
    private final static String CMD_BD = "send_bd";
    private final static String CMD_SETVR = "send_setvr";
    private final static String CMD_UNLOCK = "send_unlock";
    private final static String CMD_EXIT = "exit";
    private final static String CMD_LIST = "list";

    // If set, the TC frame is randomized before CLTU encoding.
    // If not set, randomization is not used.
    public final static String ARGS_USE_RANDOMIZATION = "--randomize";
    // If set, the TC segmentation is used.
    // If not set,TC segmentation is not used.
    public final static String ARGS_USE_SEGMENTATION = "--segment";
    // The path to the packet encoding definition file (special extension used).
    // This is a mandatory argument.
    public final static String ARGS_DEFINITION_PATH = "--definition";
    // If provided, the generated frames will be stored to the specified file in binary format, one after the other
    // If not provided, no file will be written.
    public final static String ARGS_SINK_FILE = "--out_file";
    // If provided, the generated frames will be sent to the specified host.
    // If not provided, it is set to localhost.
    private final static String ARGS_SINK_TCP_HOST = "--out_host";
    // If provided, the generated frames will be sent to the specified TCP port.
    // If not provided, no TCP/IP server will be open.
    private final static String ARGS_SINK_TCP_PORT = "--out_port";
    // If set, the FECF will be computed and added to all generated frames.
    // If not set, no FECF will be generated.
    public final static String ARGS_USE_FECF = "--fecf";
    // The spacecraft ID.
    // This is a mandatory argument.
    public final static String ARGS_SCID = "--scid";
    // The TC VC ID.
    // If not provided, VC 0 will be used.
    public final static String ARGS_VCID = "--vcid";
    // The type emitted by the application: can be cltu, frame, packet.
    // If not set, default is CLTU.
    private final static String ARGS_TYPE = "--type";

    // The System.in reader
    public final static BufferedReader CONSOLE = new BufferedReader(new InputStreamReader(System.in));

    // Instance fields
    private boolean useRandomization = false;
    private boolean useSegmentation = false;
    private Definition definition = null;
    private OutputStream outFile = null;
    private String host = "localhost";
    private int port = 0;
    private volatile Socket outSocket = null;
    private boolean useFecf = false;
    private int scId = 0;
    private int vcId = 0;
    private String type = TYPE_CLTU;
    // A console-based interactive resolver, which asks the values using the command line interface
    private final IEncodeResolver defaultResolver = new IdentificationFieldBasedResolver(new ConsoleInteractiveResolver());
    // A programmatic Consumer that can be added
    private Consumer<byte[]> optionalConsumer;

    private volatile boolean running = false;

    public TcGenerator() {
        // Nothing to do here
    }

    public void setUseRandomization(boolean useRandomization) {
        checkState();
        this.useRandomization = useRandomization;
    }

    public void setUseSegmentation(boolean useSegmentation) {
        checkState();
        this.useSegmentation = useSegmentation;
    }

    public void setDefinition(Definition definition) {
        checkState();
        this.definition = definition;
    }

    public void setOutFile(OutputStream outFile) {
        checkState();
        this.outFile = outFile;
    }

    public void setHost(String host) {
        checkState();
        this.host = host;
    }

    public void setPort(int port) {
        checkState();
        this.port = port;
    }

    public void setUseFecf(boolean useFecf) {
        checkState();
        this.useFecf = useFecf;
    }

    public void setType(String type) {
        checkState();
        if(!type.equals(TYPE_CLTU) && !type.equals(TYPE_FRAME) && !type.equals(TYPE_PACKET)) {
            throw new IllegalArgumentException(ARGS_TYPE + " not recognized: " + type);
        }
        this.type = type;
    }

    public void setScId(int scId) {
        checkState();
        this.scId = scId;
    }

    public void setVcId(int vcId) {
        checkState();
        this.vcId = vcId;
    }

    public void setOptionalConsumer(Consumer<byte[]> optionalConsumer) {
        checkState();
        this.optionalConsumer = optionalConsumer;
    }

    private void checkState() {
        if(running) {
            throw new IllegalStateException("TM generator running, cannot configure");
        }
    }

    public void startGeneration() throws IOException {
        // Check if the definition is available
        if(this.definition == null) {
            throw new IllegalStateException("A valid Definition database is required");
        }
        this.running = true;

        // Depending on the selected output type, we need to have a channel encoder that either encodes to CLTU (with or without previous randomisation)
        // or is a pass-through. VC allocated only in case of type equal to cltu or frame.
        ChannelEncoder<TcTransferFrame> channelEncoder = null;
        TcSenderVirtualChannel vc = null;
        if(type.equals(TYPE_CLTU) || type.equals(TYPE_FRAME)) {
            channelEncoder = ChannelEncoder.create();
            if (this.useRandomization) {
                // Add a randomizer
                channelEncoder.addEncodingFunction(new CltuRandomizerEncoder<>());
            }
            if (this.type.equals(TYPE_CLTU)) {
                // Add a CLTU encoder
                channelEncoder.addEncodingFunction(new CltuEncoder<>());
            }
            // Freeze the encoder configuration
            channelEncoder.configure();
            // Create the VC object to generate frames
            vc = new TcSenderVirtualChannel(this.scId, this.vcId, VirtualChannelAccessMode.Packet, this.useFecf, this.useSegmentation);
            // Link
            final ChannelEncoder<TcTransferFrame> finalChannelEncoder = channelEncoder;
            IVirtualChannelSenderOutput<TcTransferFrame> vcOut = (vc1, generatedFrame, bufferedBytes) -> {
                byte[] encoded = finalChannelEncoder.apply(generatedFrame);
                send(encoded);
            };
            vc.register(vcOut);
        }

        // Create the packet encoder
        IPacketEncoder encoder = new DefaultPacketEncoder(this.definition);

        // Now we are ready to start the generation
        // Create a map to keep track of the APID counter
        Map<Integer, AtomicInteger> apid2counter = new HashMap<>();
        System.out.println("Supported commands:\n" +
                "         - send_ad <TC Definition ID> [map]\n" +
                "         - send_bd <TC Definition ID> [map]\n" +
                "         - send_setvr <VR> (only frame/cltu mode)\n" +
                "         - send_unlock (only frame/cltu mode)\n" +
                "         - list\n" +
                "         - exit");
        String commandRead;
        do {
            System.out.print("> ");
            System.out.flush();
            commandRead = CONSOLE.readLine();
            if(commandRead.isBlank()) {
                continue;
            }
            String[] cmd = commandRead.split(" ", -1);
            switch(cmd[0]) {
                case CMD_AD:
                case CMD_BD:
                    sendCommand(encoder, vc, cmd[1], cmd[0].equals(CMD_AD), cmd.length >= 3 ? cmd[2] : null, apid2counter);
                    break;
                case CMD_SETVR:
                    sendSetVr(vc, cmd[1]);
                    break;
                case CMD_UNLOCK:
                    sendUnlock(vc);
                    break;
                case CMD_LIST:
                    printDefinitions();
                    break;
                case CMD_EXIT:
                    System.out.printf("\tExit...\n");
                    System.exit(0);
                    break;
                default:
                    System.out.printf("Command not recognized: %s\n", commandRead);
                    break;
            }
        } while(true);

        // That's it
    }

    private void sendCommand(IPacketEncoder encoder, TcSenderVirtualChannel vc, String packetName, boolean isAd, String mapId, Map<Integer, AtomicInteger> apid2counter) {
        PacketDefinition packetDefinition = null;
        for(PacketDefinition pd : this.definition.getPacketDefinitions()) {
            if(pd.getId().equals(packetName)) {
                packetDefinition = pd;
                break;
            }
        }
        if(packetDefinition != null) {
            try {
                SpacePacket tcPacket = generatePacket(encoder, packetDefinition, apid2counter);
                if(vc != null) {
                    vc.setAdMode(isAd);
                    if(mapId != null && !mapId.isEmpty()) {
                        vc.setMapId(Integer.parseInt(mapId));
                    }
                    vc.dispatch(tcPacket);
                } else {
                    send(tcPacket.getPacket());
                }
            } catch(RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().equals("Encoding aborted by user")) {
                    return;
                } else {
                    throw e;
                }
            }
        } else {
            System.out.printf("\tPacket definition %s not found\n", packetName);
        }
    }

    private void sendUnlock(TcSenderVirtualChannel vc) {
        if(vc == null) {
            System.out.printf("\tFrame/CLTU transfer mode not set, cannot send Unlock\n");
        } else {
            vc.dispatchUnlock();
        }
    }

    private void sendSetVr(TcSenderVirtualChannel vc, String s) {
        if(vc == null) {
            System.out.printf("\tFrame/CLTU transfer mode not set, cannot send Set_V(R)\n");
        } else {
            vc.dispatchSetVr(Integer.parseInt(s));
        }
    }

    private void printDefinitions() {
        for(PacketDefinition pd : this.definition.getPacketDefinitions()) {
            if(pd.getType() == null || !pd.getType().equals(TC_PACKET_TYPE)) {
                continue;
            }
            System.out.printf("\tPacket %s\n", pd.getId());
            if(pd.getDescription() != null && !pd.getDescription().isBlank()) {
                System.out.printf("\t\t%s\n", pd.getDescription());
            }
        }
    }

    private SpacePacket generatePacket(IPacketEncoder encoder, PacketDefinition packetDefinition, Map<Integer, AtomicInteger> apid2counter) {
        // First generate the user data according to the packet definition (including PUS type and PUS subtype)
        byte[] encoded = encoder.encode(packetDefinition.getId(), defaultResolver);

        // Compute the APID
        int apid = 0;
        for(IdentFieldMatcher ifm : packetDefinition.getMatchers()) {
            if(ifm.getField().getId().equals(FIELD_APID_NAME)) {
                apid = ifm.getValue();
                break;
            }
        }
        // Get the counter
        AtomicInteger counter = apid2counter.getOrDefault(apid, new AtomicInteger(-1));
        apid2counter.put(apid, counter);
        // Then generate the packet, use the derived APID and related APID counter
        SpacePacketBuilder spb = SpacePacketBuilder.create()
                .setApid(apid % 2048) // 11 bits
                .setPacketSequenceCount(counter.incrementAndGet() % 16384) // 14 bits
                .setSecondaryHeaderFlag(true)
                .setSequenceFlag(SpacePacket.SequenceFlagType.UNSEGMENTED)
                .setTelecommandPacket()
                .setQualityIndicator(true);
        // Then add the encoded data (the user data field including the secondary header)
        spb.addData(encoded);
        // Then build the packet
        return spb.build();
    }

    private void send(byte[] data) {
        System.out.printf("\tSent: %s\n", StringUtil.toHexDump(data));
        // If there is a consumer, forward
        if(this.optionalConsumer != null) {
            this.optionalConsumer.accept(data);
        }
        // If the file is defined, go for it
        if(this.outFile != null) {
            try {
                this.outFile.write(data);
                this.outFile.flush();
                System.out.printf("\tFile: %s\n", StringUtil.toHexDump(data));
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        // If the socket is there, go for it
        if(this.outSocket == null) {
            // If there is a socket, open the connection
            if(this.port > 0) {
                try {
                    this.outSocket = new Socket(this.host, this.port);
                } catch (IOException e) {
                    System.out.printf("\tCannot open socket to " + this.host + ":" + this.port + "\n");
                    this.outSocket = null;
                }
            }
        }
        if(this.outSocket != null) {
            try {
                this.outSocket.getOutputStream().write(data);
                this.outSocket.getOutputStream().flush();
                System.out.printf("\tSock: %s\n", StringUtil.toHexDump(data));
            } catch(Exception e) {
                e.printStackTrace();
                try {
                    this.outSocket.close();
                } catch (IOException ex) {
                    //
                }
                this.outSocket = null;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        TcGenerator tcGen = new TcGenerator();
        if(args.length == 0) {
            System.out.println("Usage: TcGenerator [argument]+");
            System.out.println();
            System.out.println("Supported arguments:");
            System.out.println("--definition <path>         absolute path to the encoding definition file. Mandatory");
            System.out.println("--scid <value>              spacecraft ID in decimal format. Default: 0");
            System.out.println("--vcid <value>              TC VC ID in decimal format. Default: 0");
            System.out.println("--out_file                  absolute path to the file where TC frames will be written. Default: disabled");
            System.out.println("--out_port                  port number where TC frames will be sent to. Default: 0 (disabled)");
            System.out.println("--out_host                  host name where TC frames will be sent to. Default: localhost");
            System.out.println("--segment                   use TC packet segmentation. Default: not used");
            System.out.println("--randomize                 randomize the frame. Default: not set");
            System.out.println("--fecf                      generate the Frame Error Control Field. Default: not generated");
            System.out.println("--type                      set the output type, can be cltu, frame, packet. Default: cltu");
            System.exit(-1);
        }
        for(int i = 0; i < args.length;) {
            switch(args[i]) {
                case ARGS_DEFINITION_PATH:
                    tcGen.setDefinition(Definition.load(new FileInputStream(args[i+1])));
                    i += 2;
                    break;
                case ARGS_SCID:
                    tcGen.setScId(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_VCID:
                    tcGen.setVcId(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_SINK_FILE:
                    File f = new File(args[i+1]);
                    tcGen.setOutFile(new FileOutputStream(f, f.exists()));
                    i += 2;
                    break;
                case ARGS_SINK_TCP_PORT:
                    tcGen.setPort(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_SINK_TCP_HOST:
                    tcGen.setHost(args[i+1]);
                    i += 2;
                    break;
                case ARGS_TYPE:
                    tcGen.setType(args[i+1]);
                    i += 2;
                    break;
                case ARGS_USE_FECF:
                    tcGen.setUseFecf(true);
                    i += 1;
                    break;
                case ARGS_USE_SEGMENTATION:
                    tcGen.setUseSegmentation(true);
                    i += 1;
                    break;
                case ARGS_USE_RANDOMIZATION:
                    tcGen.setUseRandomization(true);
                    i += 1;
                    break;
                default:
                    throw new IllegalArgumentException("Argument " + args[i] + " not recognized");
            }
        }

        // Start the generation
        tcGen.startGeneration();
    }

    public static class ConsoleInteractiveResolver implements IEncodeResolver {

        @Override
        public boolean getBooleanValue(EncodedParameter parameter, PathLocation location) {
            return request(location.toString(), Boolean.class, Boolean::parseBoolean);
        }

        @Override
        public int getEnumerationValue(EncodedParameter parameter, PathLocation location) {
            return request(location.toString(), Integer.class, Integer::parseInt);
        }

        @Override
        public long getSignedIntegerValue(EncodedParameter parameter, PathLocation location) {
            return request(location.toString(), Long.class, Long::parseLong);
        }

        @Override
        public long getUnsignedIntegerValue(EncodedParameter parameter, PathLocation location) {
            return request(location.toString(), Long.class, Long::parseLong);
        }

        @Override
        public double getRealValue(EncodedParameter parameter, PathLocation location) {
            return request(location.toString(), Double.class, Double::parseDouble);
        }

        @Override
        public Instant getAbsoluteTimeValue(EncodedParameter parameter, PathLocation location) {
            return request(location.toString(), Instant.class, Instant::parse);
        }

        @Override
        public Duration getRelativeTimeValue(EncodedParameter parameter, PathLocation location) {
            return request(location.toString(), Duration.class, Duration::parse);
        }

        @Override
        public BitString getBitStringValue(EncodedParameter parameter, PathLocation location, int maxBitlength) {
            return request(location.toString(), BitString.class, BitString::parseBitString, maxBitlength);
        }

        @Override
        public byte[] getOctetStringValue(EncodedParameter parameter, PathLocation location, int maxByteLength) {
            return request(location.toString(), byte[].class, StringUtil::toByteArray, maxByteLength);
        }

        @Override
        public String getCharacterStringValue(EncodedParameter parameter, PathLocation location, int maxStringLength) {
            return request(location.toString(), String.class, Function.identity(), maxStringLength);
        }

        @Override
        public Object getExtensionValue(EncodedParameter parameter, PathLocation location) {
            // Not supported
            throw new UnsupportedOperationException("Type not supported");
        }

        @Override
        public AbsoluteTimeDescriptor getAbsoluteTimeDescriptor(EncodedParameter parameter, PathLocation location, Instant value) {
            // Not supported
            throw new UnsupportedOperationException("Descriptor not supported");
        }

        @Override
        public RelativeTimeDescriptor getRelativeTimeDescriptor(EncodedParameter parameter, PathLocation location, Duration value) {
            // Not supported
            throw new UnsupportedOperationException("Descriptor not supported");
        }

        private <T> T request(String parameter, Class<T> clazz, Function<String, T> parseFunction) {
            return request(parameter, clazz, parseFunction, -1);
        }

        private <T> T request(String parameter, Class<T> clazz, Function<String, T> parseFunction, int length) {
            try {
                String read = null;
                while (read == null) {
                    String promptString = parameter + " value (" + clazz.getSimpleName();
                    if(length > -1) {
                        promptString += ", length " + length;
                    }
                    promptString += "): ";
                    System.out.print(promptString);
                    System.out.flush();
                    read = CONSOLE.readLine();
                    if (!read.equals("abort")) {
                        try {
                            return parseFunction.apply(read.trim());
                        } catch (Exception e) {
                            System.out.printf("Value cannot be parsed as %s: %s", clazz.getSimpleName(), read);
                            read = null;
                        }
                    }
                    // If at this stage read is not null, then it was 'abort'
                }
                throw new RuntimeException("Encoding aborted by user");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
