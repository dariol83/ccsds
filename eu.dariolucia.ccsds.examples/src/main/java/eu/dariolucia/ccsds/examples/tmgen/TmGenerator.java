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

package eu.dariolucia.ccsds.examples.tmgen;

import eu.dariolucia.ccsds.encdec.definition.Definition;
import eu.dariolucia.ccsds.encdec.definition.IdentFieldMatcher;
import eu.dariolucia.ccsds.encdec.definition.PacketDefinition;
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.structure.IPacketEncoder;
import eu.dariolucia.ccsds.encdec.structure.impl.DefaultPacketEncoder;
import eu.dariolucia.ccsds.encdec.structure.resolvers.DefaultNullBasedResolver;
import eu.dariolucia.ccsds.encdec.structure.resolvers.IdentificationFieldBasedResolver;
import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.ChannelEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.ReedSolomonEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmRandomizerEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.TmSenderVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.sender.mux.TmMasterChannelMuxer;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.builder.ClcwBuilder;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.builder.SpacePacketBuilder;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// Precondition: each packet definition contains a field match pointing to an identification field called APID, used to set the APID. Mandatory.
// Precondition: if a packet definition contains a field named as an identification field, the expected identification field value is used (check implementation of the PacketBasedEncodeResolver
// Precondition: in the extension of each packet definition, the list of VC IDs, comma-separated, shall be present. If not present, the packet is assumed generated on all VCIDs, with the exclusion of VC7
public class TmGenerator {

    private final static String FIELD_APID_NAME = "APID";

    // If set, the CCSDS ASM will be added to each frame
    // If not set, no ASM block will be generated.
    private final static String ARGS_USE_ASM = "--asm";
    // If set, the Reed-Solomon codeblock with the derived interleaving depth will be generated and put at the end of each frame
    // If not set, no RS block will be generated.
    private final static String ARGS_USE_RS = "--rs";
    // If set, the frame+RS block, if present, is randomized.
    // If not set, randomization is not used.
    private final static String ARGS_USE_RANDOMIZATION = "--randomize";
    // The path to the packet encoding definition file (special extension used).
    // This is a mandatory argument.
    private final static String ARGS_DEFINITION_PATH = "--definition";
    // If provided, the generated frames will be stored to the specified file in binary format, one after the other
    // If not provided, no file will be written.
    private final static String ARGS_SINK_FILE = "--out_file";
    // If provided, the generated frames will be sent to any TCP/IP connection established on the specified TCP port
    // If not provided, no TCP/IP server will be open.
    private final static String ARGS_SINK_TCP_PORT = "--out_tcp";
    // The frame generation bitrate
    // If not set, by default 8192 bps will be used
    private final static String ARGS_BITRATE = "--bitrate";
    // If set, the FECF will be computed and added to all generated frames.
    // If not set, no FECF will be generated.
    private final static String ARGS_USE_FECF = "--fecf";
    // If set, a dummy CLCW will be added to all generated frames.
    // If not set, no CLCW will be generated.
    private final static String ARGS_USE_CLCW = "--clcw";
    // The frame length in bytes.
    // If not provided, 1115 will be used.
    private final static String ARGS_FRAME_LENGTH = "--frame_length";
    // If provided, the frames will be generated only on the specified VCs (comma separated list). On VC7 (if specified) only idle frames will be transmitted.
    // If not provided, all VC IDs will be generated.
    private final static String ARGS_VCID_LIST = "--vcid_list";
    // If provided, the frames will be generated using the specified weights (normalized).
    // If not provided, the specified VC IDs will be evenly generated (randomly).
    private final static String ARGS_VCID_PROBABILITY = "--vcid_probability";
    // The spacecraft ID.
    // This is a mandatory argument.
    private final static String ARGS_SCID = "--scid";

    // Instance fields
    private boolean useAsm = false;
    private boolean useRs = false;
    private boolean useRandomization = false;
    private Definition definition = null;
    private OutputStream outFile = null;
    private ServerSocket serverSocket = null;
    private volatile Socket outSocket = null;
    private int bitrate = 8192;
    private boolean useFecf = false;
    private boolean useClcw = false;
    private int frameLength = 1115;
    private List<Integer> vcIds = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7);
    private List<Integer> vcIdPriority = Arrays.asList(1, 1, 1, 1, 1, 1, 1, 1);
    private int scId = 0;
    // A fixed encode resolver with hardcoded default values and encoding for identification fields
    private final IEncodeResolver defaultResolver = new IdentificationFieldBasedResolver(new DefaultNullBasedResolver());

    private volatile boolean running = false;

    public TmGenerator() {
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

    public void setUseRandomization(boolean useRandomization) {
        checkState();
        this.useRandomization = useRandomization;
    }

    public void setDefinition(Definition definition) {
        checkState();
        this.definition = definition;
    }

    public void setOutFile(OutputStream outFile) {
        checkState();
        this.outFile = outFile;
    }

    public void setServerSocket(ServerSocket serverSocket) {
        checkState();
        this.serverSocket = serverSocket;
    }

    public void setBitrate(int bitrate) {
        checkState();
        this.bitrate = bitrate;
    }

    public void setUseFecf(boolean useFecf) {
        checkState();
        this.useFecf = useFecf;
    }

    public void setUseClcw(boolean useClcw) {
        checkState();
        this.useClcw = useClcw;
    }

    public void setFrameLength(int frameLength) {
        checkState();
        this.frameLength = frameLength;
    }

    public void setVcIds(List<Integer> vcIds) {
        checkState();
        this.vcIds = vcIds;
    }

    public void setVcIdPriority(List<Integer> vcIdPriority) {
        checkState();
        this.vcIdPriority = vcIdPriority;
    }

    public void setScId(int scId) {
        checkState();
        this.scId = scId;
    }

    private void checkState() {
        if(running) {
            throw new IllegalStateException("TM generator running, cannot configure");
        }
    }

    public void startGeneration() {
        // Check if the definition is available
        if(this.definition == null) {
            throw new IllegalStateException("A valid Definition database is required");
        }
        this.running = true;
        // Partition the packet definitions per VCID
        Map<Integer, List<PacketDefinition>> vc2packets = partitionPacketsPerVc();
        // Compute VC ID priorities (as probability, normalised)
        int[] vcChoiceSet = computeVcChoiceSet();
        // If there is a server socket, start the thread to open the server and handle the connection
        if(this.serverSocket != null) {
            startSocketHandler();
        }
        // Create the channel encoder with the specified options
        ChannelEncoder<TmTransferFrame> channelEncoder = ChannelEncoder.create();
        if(this.useRs) {
            // Add a ReedSolomon encoder, try to derive the interleaving depth: the frame length must be a multiple of 223 bytes
            channelEncoder.addEncodingFunction(new ReedSolomonEncoder<>(ReedSolomonAlgorithm.TM_255_223, this.frameLength / 223));
        }
        if(this.useRandomization) {
            // Add a randomizer
            channelEncoder.addEncodingFunction(new TmRandomizerEncoder<>());
        }
        if(this.useAsm) {
            // Add the TM attached synch marker
            channelEncoder.addEncodingFunction(new TmAsmEncoder<>());
        }
        // Freeze the encoder configuration
        channelEncoder.configure();
        // Create a standard TM muxer with a consumer using the method that forwards the frame after encoding
        TmMasterChannelMuxer mux = new TmMasterChannelMuxer(o -> forwardFrame(channelEncoder.apply(o)));
        // Create the specified VC IDs
        Map<Integer, TmSenderVirtualChannel> vc2sender = new HashMap<>();
        for(int i : vcIds) {
            // Create the VC, use the muxer as master channel frame count supplier
            TmSenderVirtualChannel vc = new TmSenderVirtualChannel(this.scId, i, VirtualChannelAccessMode.Packet, this.useFecf, this.frameLength, mux::getNextCounter, this.useClcw ? this::ocfSupplier : null);
            // Register the muxer
            vc.register(mux);
            vc2sender.put(i, vc);
        }
        // Create the packet encoder
        IPacketEncoder encoder = new DefaultPacketEncoder(this.definition);

        // Now we are ready to start the generation

        // Compute the sleep time per frame, considering also the encoding information. Compute the frame total length:
        // - frame length
        // - if ASM is present, add 4 bytes (0x1A, 0xCF, 0xFC, 0x1D)
        // - if RS is present, add 32 bytes for each 223 bytes, so 32 * interleaving depth
        int totalFrameLength = this.frameLength;
        totalFrameLength += this.useAsm ? TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER.length : 0;
        totalFrameLength += this.useRs ? 32 * (this.frameLength / 223) : 0;
        int waitTimeMs = (int) Math.round(1000.0 / (this.bitrate / (double) (totalFrameLength * 8)));
        // Create a map to keep track of the APID counter
        Map<Integer, AtomicInteger> apid2counter = new HashMap<>();
        // In this example, this loop will go forever and no separate thread is used to start the generation
        while(this.running) {
            // Pick a VC
            int vcToGenerate = selectRandomVc(vcChoiceSet);
            // If it is VC 7, then generate an idle frame
            if(vcToGenerate == 7) {
                vc2sender.get(vcToGenerate).dispatchIdle(new byte[] { 0x55 });
            } else {
                int writtenData = 0;
                // Until the TM frame is generated
                while (writtenData < vc2sender.get(vcToGenerate).getMaxUserDataLength()) {
                    // Pick a packet for that VC
                    PacketDefinition selectedPacketDefinition = selectRandomPacket(vc2packets.get(vcToGenerate));
                    // Generate the packet
                    SpacePacket packet = generatePacket(encoder, selectedPacketDefinition, apid2counter);
                    // Send the packet to the VC
                    vc2sender.get(vcToGenerate).dispatch(packet);
                    // Increase the amount of written data
                    writtenData += packet.getLength();
                }
            }
            // Sleep for a computed amount of milliseconds, depending on the bitrate
            try {
                Thread.sleep(waitTimeMs);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // That's it
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
                .setTelemetryPacket()
                .setQualityIndicator(true);
        // Then add the encoded data (the user data field including the secondary header)
        spb.addData(encoded);
        // Then build the packet
        return spb.build();
    }

    private PacketDefinition selectRandomPacket(List<PacketDefinition> packetDefinitions) {
        return packetDefinitions.get((int) Math.floor(Math.random() * packetDefinitions.size()));
    }

    private int selectRandomVc(int[] vcChoiceSet) {
        return vcChoiceSet[(int) Math.floor(Math.random() * vcChoiceSet.length)];
    }

    private int[] computeVcChoiceSet() {
        int sum = this.vcIdPriority.stream().reduce(0, Integer::sum);
        int[] toReturn = new int[sum];
        int idx = 0;
        for(int i = 0; i < this.vcIdPriority.size(); ++i) {
            for(int j = 0; j < this.vcIdPriority.get(i); ++j) {
                toReturn[idx + j] = this.vcIds.get(i);
            }
            idx += this.vcIdPriority.get(i);
        }
        return toReturn;
    }

    /**
     * Create a dummy fixed CLCW.
     *
     * @param vcId the VC ID to be put in the CLCW (ignored)
     * @return the Clcw object
     */
    private AbstractOcf ocfSupplier(int vcId) {
        return ClcwBuilder.create()
                .setVirtualChannelId(0)
                .setCopInEffect(false)
                .setLockoutFlag(false)
                .setNoBitlockFlag(true)
                .setNoRfAvailableFlag(true)
                .setFarmBCounter(0)
                .setReportValue(0)
                .setWaitFlag(false)
                .setRetransmitFlag(false)
                .build();
    }

    private void forwardFrame(byte[] data) {
        // If the file is defined, go for it
        if(this.outFile != null) {
            try {
                this.outFile.write(data);
                this.outFile.flush();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        // If the socket is there, go for it
        Socket sock = this.outSocket;
        if(sock != null) {
            try {
                sock.getOutputStream().write(data);
                sock.getOutputStream().flush();
            } catch(Exception e) {
                try {
                    sock.close();
                } catch (IOException ex) {
                    //
                }
            }
        }
    }

    private void startSocketHandler() {
        Thread t = new Thread(() -> {
            while(running) {
                try {
                    this.outSocket = this.serverSocket.accept();
                    try {
                        this.outSocket.setKeepAlive(true);
                        // Monitor the connection
                        while (outSocket.isConnected()) {
                            int data = this.outSocket.getInputStream().read();
                            if(data < 0) {
                                throw new IOException("End of stream");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        try {
                            this.outSocket.close();
                        } catch (IOException ex) {
                            //
                        }
                        this.outSocket = null;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        t.setName("Server Socket Handler");
        t.setDaemon(true);
        t.start();
    }

    private Map<Integer, List<PacketDefinition>> partitionPacketsPerVc() {
        // Navigate the definitions, read the VC info in the packet definition extension (comma separated) and build the partition
        Map<Integer, List<PacketDefinition>> toReturn = new HashMap<>();
        for(PacketDefinition pd : this.definition.getPacketDefinitions()) {
            List<Integer> vcsToAdd = this.vcIds;
            String extension = pd.getExtension();
            if(extension != null && !extension.isBlank()) {
                String[] spl = extension.split(",", -1);
                vcsToAdd = Arrays.stream(spl).map(Integer::parseInt).collect(Collectors.toList());
            }
            for(int vc : vcsToAdd) {
                List<PacketDefinition> p = toReturn.computeIfAbsent(vc, k -> new ArrayList<>());
                p.add(pd);
            }
        }
        return toReturn;
    }

    public static void main(String[] args) throws IOException {
        TmGenerator tmGen = new TmGenerator();
        if(args.length == 0) {
            System.out.println("Usage: TmGenerator [argument]+");
            System.out.println();
            System.out.println("Supported arguments:");
            System.out.println("--definition <path>         absolute path to the encoding definition file. Mandatory");
            System.out.println("--bitrate <value>           desired generation bitrate in bits per second. Default: 8192");
            System.out.println("--frame_length <value>      length of the TM frame in bytes. Default: 1115");
            System.out.println("--scid <value>              spacecraft ID in decimal format. Default: 0");
            System.out.println("--out_file                  absolute path to the file where TM frames will be written. Default: disabled");
            System.out.println("--out_tcp                   port number where TM frames will be written if a TCP client connects. Default: disabled");
            System.out.println("--asm                       generate ASM block. Default: not generated");
            System.out.println("--rs                        generate RS codeblock. Default: not generated");
            System.out.println("--randomize                 randomize the frame+RS. Default: not set");
            System.out.println("--fecf                      generate the Frame Error Control Field. Default: not generated");
            System.out.println("--clcw                      generate a dummy CLCW. Default: not generated");
            System.exit(-1);
        }
        for(int i = 0; i < args.length;) {
            switch(args[i]) {
                case ARGS_BITRATE:
                    tmGen.setBitrate(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_DEFINITION_PATH:
                    tmGen.setDefinition(Definition.load(new FileInputStream(args[i+1])));
                    i += 2;
                    break;
                case ARGS_FRAME_LENGTH:
                    tmGen.setFrameLength(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_SCID:
                    tmGen.setScId(Integer.parseInt(args[i+1]));
                    i += 2;
                    break;
                case ARGS_SINK_FILE:
                    File f = new File(args[i+1]);
                    tmGen.setOutFile(new FileOutputStream(f, f.exists()));
                    i += 2;
                    break;
                case ARGS_SINK_TCP_PORT:
                    ServerSocket ss = new ServerSocket(Integer.parseInt(args[i+1]));
                    tmGen.setServerSocket(ss);
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
                case ARGS_USE_CLCW:
                    tmGen.setUseClcw(true);
                    i += 1;
                    break;
                case ARGS_USE_RANDOMIZATION:
                    tmGen.setUseRandomization(true);
                    i += 1;
                    break;
                case ARGS_VCID_LIST:
                    String[] spl1 = args[i+1].split(",", -1);
                    List<Integer> t1 = Arrays.stream(spl1).map(Integer::parseInt).collect(Collectors.toList());
                    tmGen.setVcIds(t1);
                    i += 2;
                    break;
                case ARGS_VCID_PROBABILITY:
                    String[] spl2 = args[i+1].split(",", -1);
                    List<Integer> t2 = Arrays.stream(spl2).map(Integer::parseInt).collect(Collectors.toList());
                    tmGen.setVcIdPriority(t2);
                    i += 2;
                    break;
                default:
                    throw new IllegalArgumentException("Argument " + args[i] + " not recognized");
            }
        }

        // Start the generation
        tmGen.startGeneration();
    }
}
