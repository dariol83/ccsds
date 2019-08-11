/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.datalink.channel.sender;

import eu.dariolucia.ccsds.tmtc.datalink.builder.TmTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.AbstractOcf;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This class allows to send TM frames built from space packets or using the VCA mode. It can work in pull and push mode.
 *
 * For additional details, refers to the parent {@link AbstractSenderVirtualChannel} documentation.
 */
public class TmSenderVirtualChannel extends AbstractSenderVirtualChannel<TmTransferFrame> {

    private final Function<Integer, AbstractOcf> ocfSupplier;

    private final Function<Integer, byte[]> secondaryHeaderSupplier;

    private final Supplier<Integer> masterChannelFrameCounterSupplier;

    private final int secondaryHeaderLength;

    private final int frameLength;

    // Security info
    private final Supplier<byte[]> secHeaderSupplier;
    private final Supplier<byte[]> secTrailerSupplier;
    private final int secHeaderLength;
    private final int secTrailerLength;

    public TmSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Supplier<Integer> masterChannelFrameCounterSupplier, Function<Integer, AbstractOcf> ocfSupplier) {
        this(spacecraftId, virtualChannelId, mode, fecfPresent, frameLength, masterChannelFrameCounterSupplier, ocfSupplier, 0, null);
    }

    public TmSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Supplier<Integer> masterChannelFrameCounterSupplier, Function<Integer, AbstractOcf> ocfSupplier, int secondaryHeaderLength, Function<Integer, byte[]> secondaryHeaderSupplier) {
        this(spacecraftId, virtualChannelId, mode, fecfPresent, frameLength, masterChannelFrameCounterSupplier, ocfSupplier, secondaryHeaderLength, secondaryHeaderSupplier, null, 0, 0, null, null);
    }

    public TmSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Supplier<Integer> masterChannelFrameCounterSupplier, Function<Integer, AbstractOcf> ocfSupplier, IVirtualChannelDataProvider dataProvider) {
        this(spacecraftId, virtualChannelId, mode, fecfPresent, frameLength, masterChannelFrameCounterSupplier, ocfSupplier, 0, null, dataProvider, 0, 0, null, null);
    }

    public TmSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, int frameLength, Supplier<Integer> masterChannelFrameCounterSupplier, Function<Integer, AbstractOcf> ocfSupplier, int secondaryHeaderLength, Function<Integer, byte[]> secondaryHeaderSupplier, IVirtualChannelDataProvider dataProvider,
                                  int secHeaderLength, int secTrailerLength, Supplier<byte[]> secHeaderSupplier, Supplier<byte[]> secTrailerSupplier) {
        super(spacecraftId, virtualChannelId, mode, fecfPresent, dataProvider);
        this.frameLength = frameLength;
        this.ocfSupplier = ocfSupplier;
        this.secondaryHeaderSupplier = secondaryHeaderSupplier;
        this.secondaryHeaderLength = secondaryHeaderLength;
        this.masterChannelFrameCounterSupplier = masterChannelFrameCounterSupplier;
        if(mode == VirtualChannelAccessMode.Bitstream) {
            throw new IllegalArgumentException("Virtual channel " + virtualChannelId + " does not support access mode " + mode);
        }

        this.secHeaderSupplier = secHeaderSupplier;
        this.secTrailerSupplier = secTrailerSupplier;
        this.secHeaderLength = Math.max(secHeaderLength, 0);
        this.secTrailerLength = Math.max(secTrailerLength, 0);

        if(secHeaderLength > 0 && secHeaderSupplier == null) {
            throw new IllegalArgumentException("Security header length specified, but no security header supplier provided");
        }

        if(secTrailerLength > 0 && secTrailerSupplier == null) {
            throw new IllegalArgumentException("Security trailer length specified, but no security trailer supplier provided");
        }
    }

    public Function<Integer, AbstractOcf> getOcfSupplier() {
        return ocfSupplier;
    }

    public boolean isOcfPresent() {
        return ocfSupplier != null;
    }

    public Function<Integer, byte[]> getSecondaryHeaderSupplier() {
        return secondaryHeaderSupplier;
    }

    public Supplier<Integer> getMasterChannelFrameCounterSupplier() {
        return masterChannelFrameCounterSupplier;
    }

    public int getSecondaryHeaderLength() {
        return secondaryHeaderLength;
    }

    public int getFrameLength() {
        return frameLength;
    }

    @Override
    public void dispatchIdle(byte[] idlePattern) {
        if (this.currentFrame != null) {
            throw new IllegalStateException("Pending frame prevents generation of idle frame for virtual channel " + getVirtualChannelId());
        }
        this.currentFrame = createFrameBuilder();
        // Add idle pattern: fill the frame
        int remaining = this.currentFrame.getFreeUserDataLength();
        while(remaining > 0) {
            ((TmTransferFrameBuilder) this.currentFrame).addData(idlePattern);
            remaining = this.currentFrame.getFreeUserDataLength();
        }
        // Set idle value in the first header pointer
        ((TmTransferFrameBuilder) this.currentFrame).setIdle();
        // Build
        TmTransferFrame toSend = finalizeFullFrame();
        // Dispatch
        this.currentFrame = null;
        notifyTransferFrameGenerated(toSend, 0);
    }

    @Override
    public int dispatch(byte[] userData) {
        if (getMode() != VirtualChannelAccessMode.Data) {
            throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " access mode set to mode " + getMode() + ", but requested User Data access");
        }
        int notWrittenData = userData.length;
        while(notWrittenData > 0) {
            // If there is no pending frame, create the frame builder
            if (this.currentFrame == null) {
                this.currentFrame = createFrameBuilder();
            }
            // Add the data
            notWrittenData = ((TmTransferFrameBuilder) this.currentFrame).addData(userData, userData.length - notWrittenData, notWrittenData);
            // Check full frame
            if(((TmTransferFrameBuilder) this.currentFrame).isFull()) {
                // Build
                TmTransferFrame toSend = finalizeFullFrame();
                // Dispatch
                this.currentFrame = null;
                notifyTransferFrameGenerated(toSend, notWrittenData);
            }
        }
        return getRemainingFreeSpace();
    }

    @Override
    public int dispatch(Collection<SpacePacket> pkts) {
        if (getMode() != VirtualChannelAccessMode.Packet) {
            throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " access mode set to mode " + getMode() + ", but requested Packet access");
        }
        List<SpacePacket> packets = new ArrayList<>(pkts);
        int maxDataPerFrame = getMaxUserDataLength();
        // Strategy: fill in a transfer frame as much as you can, till the end. Do segmentation if needed.
        for (int i = 0; i < packets.size(); ++i) {
            SpacePacket isp = packets.get(i);
            int notWrittenData = isp.getLength();
            while(notWrittenData > 0) {
                // If there is no pending frame, create the frame builder
                if (this.currentFrame == null) {
                    this.currentFrame = createFrameBuilder();
                }
                // Add the packet: if the packet is not written yet, then write it and get the remaining data.
                if(notWrittenData == isp.getLength()) {
                    notWrittenData = ((TmTransferFrameBuilder) this.currentFrame).addSpacePacket(isp.getPacket());
                } else {
                    // Otherwise it means that this is a segmented packet that spilled over: write what you can
                    notWrittenData = ((TmTransferFrameBuilder) this.currentFrame).addData(isp.getPacket(), isp.getLength() - notWrittenData, notWrittenData);
                }
                if(((TmTransferFrameBuilder) this.currentFrame).isFull()) {
                    // Build
                    TmTransferFrame toSend = finalizeFullFrame();
                    // Dispatch
                    this.currentFrame = null;
                    notifyTransferFrameGenerated(toSend, notWrittenData + calculateRemainingData(packets, i + 1));
                }
            }
        }
        // Return free space
        return getRemainingFreeSpace();
    }

    @Override
    public int dispatch(BitstreamData bitstreamData) {
        throw new UnsupportedOperationException("Virtual channel " + getVirtualChannelId() + " cannot dispatch frames with Bitstream data, data not supported");
    }

    @Override
    public int getMaxUserDataLength() {
        return TmTransferFrameBuilder.computeUserDataLength(getFrameLength(), getSecondaryHeaderLength(), isOcfPresent(), isFecfPresent()) - secHeaderLength - secTrailerLength;
    }

    protected TmTransferFrame finalizeFullFrame() {
        ((TmTransferFrameBuilder) this.currentFrame)
                .setVirtualChannelFrameCount(incrementVirtualChannelFrameCounter(256))
                .setMasterChannelFrameCount(getMasterChannelFrameCounterSupplier().get());
        // Add secondary header
        if(this.secondaryHeaderLength > 0) {
            ((TmTransferFrameBuilder) this.currentFrame).setSecondaryHeader(this.secondaryHeaderSupplier.apply(getVirtualChannelId()));
        }
        // Add OCF
        if(this.ocfSupplier != null) {
            ((TmTransferFrameBuilder) this.currentFrame).setOcf(this.ocfSupplier.apply(getVirtualChannelId()).getOcf());
        }

        return this.currentFrame.build();
    }

    protected TmTransferFrameBuilder createFrameBuilder() {
        // Add security if present
        byte[] secH = secHeaderSupplier != null ? secHeaderSupplier.get() : new byte[0];
        byte[] secT = secTrailerSupplier != null ? secTrailerSupplier.get() : new byte[0];

        return TmTransferFrameBuilder.create(getFrameLength(), getSecondaryHeaderLength(), isOcfPresent(), isFecfPresent())
                .setSecurity(secH, secT)
                .setSpacecraftId(getSpacecraftId())
                .setVirtualChannelId(getVirtualChannelId())
                .setSynchronisationFlag(false)
                .setPacketOrderFlag(false)
                .setSegmentLengthIdentifier(3);
    }
}
