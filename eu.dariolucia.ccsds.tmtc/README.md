# TM/TC module
This module provides support to parse and build CCSDS TM frames, TC frames, AOS frames, Space Packets and full FARM/FOP implementation for COP-1. It allows encoding/decoding of space packets into TM frames, TC frames or AOS frames. It also provides some basic encodings (e.g. CLTU encoding/decoding, randomization, FECF encoding/checking, Reed-Solomon encoding/checking) but no error correction capabilities. 

## Getting started
### Data link frames
The eu.dariolucia.ccsds.tmtc.datalink package contains:
- Java objects to construct TM frames, TC frames and AOS frames using a Builder pattern;
- Java objects to access the header fields and contents of TM frames, TC frames and AOS frames;
- Java object to send TM frames, TC frames and AOS frames, constructed from user data bytes or space packets as per allocated virtual channel;
- Java object to receive TM frames, TC frames and AOS frames, extracting user data bytes or reconstructing space packets as per allocated virtual channel;
- A set of utility classes to exploit the Java Stream approach and the Java 9 Reactive Stream paradigm introduced in Java 9.

### OCF
The eu.dariolucia.ccsds.tmtc.ocf package contains:
- Java objects to construct CLCW objects using a Builder pattern;
- Java objects to access the fields and contents of CLCW objects.

### Space packets
The eu.dariolucia.ccsds.tmtc.transport package contains:
- Java objects to construct Space Packet objects using a Builder pattern;
- Java objects to access the fields and contents of Space Packet objects.

### Coding
The eu.dariolucia.ccsds.tmtc.coding package implements the mechanisms to encode transfer frames to, and to read and decode transfer frames from, an underlying channel implementation.

It also provides a set of utility classes to exploit the Java Stream approach and the Java 9 Reactive Stream paradigm introduced in Java 9.

### Algorithms
The eu.dariolucia.ccsds.tmtc.algorithm package contains implementations of the CCSDS channel coding algorithms:
- Reed Solomon encoder and checker;
- CLTU encoder/decoder;
- CLTU and TM randomization;
- Frame Error Control Field computation;
- AOS Frame Header Error Control computation.

### COP-1
The eu.dariolucia.ccsds.tmtc.cop1 package contains the implementation of a FARM-1 and FOP-1(B) entities, as per CCSDS specification.

## Examples

In this section there are some examples on how to use the module objects in different situations. It is recommended to read the Javadoc, inspect the JUnit tests 
and check the examples provided in the eu.dariolucia.ccsds.examples module. 

### Read Space Packets from CADUs dumped in a file

Context: a file contains the dump of randomized CADUs (TM frames), with attached sync-marker, Reed-Solomon coding 223,255.
Objective: extract the space packets from the transfer frames.

    // First the channel reader: since we know the length of the CADU, we use a fixed length reader
    IChannelReader channelReader = new FixedLengthChannelReader(new FileInputStream(...),
            this.frameLength
            + TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER.length // CCSDS ASM (4 bytes) for RS encoding
            + this.frameLength/223 * 32); // RS 223/255 encoding

    // Then the channel decoder: functions are applied in order - first the sync word removal, then derandomization, then RS check
    ChannelDecoder<TmTransferFrame> channelDecoder = ChannelDecoder.create(TmTransferFrame.decodingFunction(this.useFecf));
    channelDecoder.addDecodingFunction(new TmAsmDecoder());
    channelDecoder.addDecodingFunction(new TmRandomizerDecoder());
    channelDecoder.addDecodingFunction(new ReedSolomonDecoder(ReedSolomonAlgorithm.TM_255_223, this.frameLength/223, true));
    channelDecoder.configure();
    
    // Then the virtual channel objects (from 0 to 7)
    TmReceiverVirtualChannel[] virtualChannels = new TmReceiverVirtualChannel[8];
    for(int i = 0; i < virtualChannels.length; ++i) {
        // Packet mode - i.e. packet extraction - requested
        virtualChannels[i] = new TmReceiverVirtualChannel(i, VirtualChannelAccessMode.PACKET, true);
    }
    
    // Then the object that routes the TM frame to the correct VC object
    VirtualChannelReceiverDemux masterChannelDemuxer = new VirtualChannelReceiverDemux(virtualChannels);
    
    // Now create a virtual channel receiver, which is called back when a new TM frame or space packet is received/decoded by a VC
    IVirtualChannelReceiverOutput vcOutput = ... ; // Your implementation of here, check Javadoc
    
    // Register the output to all VCs
    Arrays.stream(virtualChannels).forEach(vc -> vc.register(vcOutput));
    
    // Start reading and processing
    while(running) {
        // Read a frame
        byte[] data = channelReader.readNext();
        // Decode the frame
        TmTransferFrame frame = channelDecoder.apply(data);
        // Process the frame (vcOutput will be called accordingly)
        masterChannelDemuxer.processFrame(frame);
    }
    // That's it


