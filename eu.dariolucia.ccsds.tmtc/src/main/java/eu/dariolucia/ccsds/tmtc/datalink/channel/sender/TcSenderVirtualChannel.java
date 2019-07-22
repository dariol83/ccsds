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

import eu.dariolucia.ccsds.tmtc.datalink.builder.TcTransferFrameBuilder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.transport.pdu.BitstreamData;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.util.*;
import java.util.function.Supplier;

/**
 * This class allows to generate TC transfer frames for a specific virtual channel in three different modes (TC packet,
 * TC segment, VCA), and with or without security information. In case of COP-1 utilisation, it can generate and emit
 * the Unlock and SetVR BC-frames.
 */
public class TcSenderVirtualChannel extends AbstractSenderVirtualChannel<TcTransferFrame> {

    private final boolean segmented;
    private final Supplier<byte[]> secHeaderSupplier;
    private final Supplier<byte[]> secTrailerSupplier;
    private final int secHeaderLength;
    private final int secTrailerLength;

    private volatile int mapId;

    private volatile boolean adMode;

    /**
     * Constructor used to create a TC virtual channel. The type of service provided by the virtual channel is driven by
     * the specified mode (Packet or Data) and segmentation, according to the following scheme:
     * - mode Packet, segmentation on: MAP based packet service (one or more packets, segmentation allowed)
     * - mode Packet, segmentation off: packet service (one or more packets, segmentation not allowed)
     * - mode Data, segmentation on: MAP SDU service (one MAP SDU with segmentation if needed)
     * - mode Data, segmentation off: VCA service
     *
     * If mode is set to Bitstream, a runtime exception is thrown.
     *
     * @param spacecraftId the spacecraft id
     * @param virtualChannelId the virtual channel id
     * @param mode the virtual channel access service mode (only Data or Packet)
     * @param fecfPresent true FECF is present, otherwise false
     * @param segmented true if segment headers are generated, false if no segment headers are generated
     * @param secHeaderLength the expected length of the security header
     * @param secTrailerLength the expected length of the security trailer
     * @param secHeaderSupplier if provided, a security header will be retrieved from the supplier and put in the frame
     * @param secTrailerSupplier if provided, a security trailer will be retrieved from the supplier and put in the frame
     */
    public TcSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, boolean segmented,
                                  int secHeaderLength, int secTrailerLength, Supplier<byte[]> secHeaderSupplier, Supplier<byte[]> secTrailerSupplier) {
        super(spacecraftId, virtualChannelId, mode, fecfPresent);
        this.segmented = segmented;
        if(mode == VirtualChannelAccessMode.Bitstream) {
           throw new IllegalArgumentException("Virtual channel " + virtualChannelId + " does not support access mode " + mode);
        }
        this.secHeaderSupplier = secHeaderSupplier;
        this.secTrailerSupplier = secTrailerSupplier;
        this.secHeaderLength = secHeaderLength >= 0 ? secHeaderLength : 0;
        this.secTrailerLength = secTrailerLength >= 0 ? secTrailerLength : 0;

        if(secHeaderLength > 0 && secHeaderSupplier == null) {
            throw new IllegalArgumentException("Security header length specified, but no security header supplier provided");
        }

        if(secTrailerLength > 0 && secTrailerSupplier == null) {
            throw new IllegalArgumentException("Security trailer length specified, but no security trailer supplier provided");
        }
    }

    /**
     * Constructor to create a TC virtual channel without security information.
     *
     * @param spacecraftId the spacecraft id
     * @param virtualChannelId the virtual channel id
     * @param mode the virtual channel access service mode (only Data or Packet)
     * @param fecfPresent true FECF is present, otherwise false
     * @param segmented true if segment headers are generated, false if no segment headers are generated
     */
    public TcSenderVirtualChannel(int spacecraftId, int virtualChannelId, VirtualChannelAccessMode mode, boolean fecfPresent, boolean segmented) {
        this(spacecraftId, virtualChannelId, mode, fecfPresent, segmented, 0, 0, null, null);
    }

    /**
     * This method returns whether security information will be injected in the generated TC frame or not.
     *
     * @return true if the virtual channel is secured, false otherwise
     */
    public boolean isSecured() {
        return secHeaderLength > 0 || secTrailerLength > 0;
    }

    /**
     * This method returns whether the virtual channel generates frames with TC segments rather than plain packets.
     *
     * @return if the TC segment header is generated, false otherwise
     */
    public boolean isSegmented() {
        return segmented;
    }

    /**
     * This method returns the default MAP ID, generated in case segmentation is active.
     *
     * @return the value of the default MAP ID
     */
    public int getMapId() {
        return mapId;
    }

    /**
     * This method sets the default MAP IP.
     *
     * @param mapId the MAP ID to be used as default
     */
    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    /**
     * This method returns whether frames by default are generated without bypass flag (AD frames) or not (BD frames).
     *
     * @return true if bypass-flag is false (i.e. AD-frames), false otherwise (i.e. BD frames)
     */
    public boolean isAdMode() {
        return adMode;
    }

    /**
     * This mode sets the default service (true: AD-frames, false: BD-frames)
     *
     * @param adMode true if AD-frames, false if BD-frames
     */
    public void setAdMode(boolean adMode) {
        this.adMode = adMode;
    }

    /**
     * This method generates and dispatches an Unlock BC-frame, to remove the Lockout state from the on-board FARM.
     */
    public void dispatchUnlock() {
        byte[] secH = secHeaderSupplier != null ? secHeaderSupplier.get() : new byte[0];
        byte[] secT = secTrailerSupplier != null ? secTrailerSupplier.get() : new byte[0];

        TcTransferFrame tc = TcTransferFrameBuilder.create(isFecfPresent())
                .setSpacecraftId(getSpacecraftId())
                .setVirtualChannelId(getVirtualChannelId())
                .setFrameSequenceNumber(incrementVirtualChannelFrameCounter(256))
                .setBypassFlag(true)
                .setControlCommandFlag(true)
                .setSecurity(secH, secT)
                .setUnlockControlCommand()
                .build();

        notifyTransferFrameGenerated(tc, 0);
    }

    /**
     * This method generates and dispatches a Set_V(R) BC-frame, to initialise the on-board FARM with the provided frame number.
     *
     * @param frameNumber the frame number for the Set_V(R)
     */
    public void dispatchSetVr(int frameNumber) {
        byte[] secH = secHeaderSupplier != null ? secHeaderSupplier.get() : new byte[0];
        byte[] secT = secTrailerSupplier != null ? secTrailerSupplier.get() : new byte[0];

        TcTransferFrame tc = TcTransferFrameBuilder.create(isFecfPresent())
                .setSpacecraftId(getSpacecraftId())
                .setVirtualChannelId(getVirtualChannelId())
                .setFrameSequenceNumber(incrementVirtualChannelFrameCounter(256))
                .setBypassFlag(true)
                .setControlCommandFlag(true)
                .setSecurity(secH, secT)
                .setSetVrControlCommand(frameNumber)
                .build();
        notifyTransferFrameGenerated(tc, 0);
    }

    @Override
    public int dispatch(Collection<SpacePacket> pkts) {
        return dispatch(isAdMode(), getMapId(), pkts);
    }

    /**
     * This method encapsulates the provided packets inside one or more TC frames. The behaviour of this method is to
     * try to encapsulate as many packets as possible into a single TC frame, without segmenting the packets in multiple
     * frames. Segmentation is done only if unavoidable (i.e. packet larger than the TC frame max allowed user data).
     * In this case, a sequence of frames is generated and dispatched only to cover the segmented space packets, i.e.
     * there is no mix between segmented and unsegmented packets. In other words, generated TC frames contain:
     * - one or more complete TC packets (either with or without segmentation); or
     * - one part of a single packet (first, parts in the middle, last) (only with segmentation).
     *
     * In order to allow TC packet segmentation over multiple TC frames, segmentation shall be enabled on the virtual
     * channel. If segmentation is not enabled and a packet larger than the allowed size is requested for transmission,
     * a runtime exception is thrown.
     *
     * @param adMode if this is an AD transmission, i.e. the bypass flag is not set
     * @param mapId the map ID to be used (ignored if segmentation is disabled for the virtual channel)
     * @param pkts the collection of packets to be sent
     * @return the number of remaining free bytes in the not-yet-sent TC frame (0 in this implementation).
     */
    public int dispatch(boolean adMode, int mapId, Collection<SpacePacket> pkts) {
        if (getMode() != VirtualChannelAccessMode.Packet) {
            throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " access mode set to mode " + getMode() + ", but requested Packet access");
        }
        List<SpacePacket> packets = new ArrayList<>(pkts);
        int maxDataPerFrame = getMaxUserDataLength();
        // Strategy: fill in a transfer frame as much as you can, always using an UNSEGMENTED approach.
        // If the next space is going to spill out, then close the frame and send the closed frame immediately.
        for (int i = 0; i < packets.size(); ++i) {
            SpacePacket isp = packets.get(i);
            if (this.currentFrame == null) {
                // If the packet fits it, then create the frame and add it to the frame
                if (maxDataPerFrame >= isp.getLength()) {
                    byte[] secH = secHeaderSupplier != null ? secHeaderSupplier.get() : new byte[0];
                    byte[] secT = secTrailerSupplier != null ? secTrailerSupplier.get() : new byte[0];

                    this.currentFrame = TcTransferFrameBuilder.create(isFecfPresent())
                            .setSpacecraftId(getSpacecraftId())
                            .setVirtualChannelId(getVirtualChannelId())
                            .setFrameSequenceNumber(incrementVirtualChannelFrameCounter(256))
                            .setBypassFlag(!adMode)
                            .setControlCommandFlag(false)
                            .setSecurity(secH, secT);
                    if (segmented) {
                        ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.NO_SEGMENT, mapId);
                    }
                    // Add the packet
                    ((TcTransferFrameBuilder) this.currentFrame).addData(isp.getPacket());
                } else {
                    // If the packet does not fit in, remember that you created this frame now, so you have to segment it
                    // across several frames, but only if segmentation is active (see TC standard). If no segmentation
                    // is active, then the standard is violated and an exception is thrown.
                    if(!segmented) {
                        throw new IllegalArgumentException("Cannot encode packet of size " + isp.getLength()
                                + " inside TC frame: packet too large and segmentation not active on virtual channel " + getVirtualChannelId());
                    }
                    byte[] packetToSend = isp.getPacket();

                    int chunks = packetToSend.length / maxDataPerFrame + (packetToSend.length % maxDataPerFrame == 0 ? 0 : 1);
                    // Send the chunks
                    for (int cki = 0; cki < chunks; ++cki) {
                        byte[] secH = secHeaderSupplier != null ? secHeaderSupplier.get() : new byte[0];
                        byte[] secT = secTrailerSupplier != null ? secTrailerSupplier.get() : new byte[0];

                        this.currentFrame = TcTransferFrameBuilder.create(isFecfPresent())
                                .setSpacecraftId(getSpacecraftId())
                                .setVirtualChannelId(getVirtualChannelId())
                                .setFrameSequenceNumber(incrementVirtualChannelFrameCounter(256))
                                .setBypassFlag(!adMode)
                                .setControlCommandFlag(false)
                                .setSecurity(secH, secT);

                        // First chunk is FIRST
                        if (cki == 0) {
                            ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.FIRST, mapId);
                        } else if (cki == chunks - 1) {
                            // Last chunk is LAST
                            ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.LAST, mapId);
                        } else {
                            // Middle chunks are CONTINUE
                            ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.CONTINUE, mapId);
                        }

                        int currOffset = cki * maxDataPerFrame;
                        ((TcTransferFrameBuilder) this.currentFrame).addData(packetToSend, currOffset, cki == chunks - 1 ? packetToSend.length - currOffset : maxDataPerFrame);
                        TcTransferFrame toSend = this.currentFrame.build();
                        this.currentFrame = null;
                        notifyTransferFrameGenerated(toSend, calculateRemainingData(packets, i) - currOffset);
                    }
                }
            } else {
                // If you are here, it means that at least a packet was added to the existing frame and the segment
                // already set.
                // If the packet fits it, then add it to the frame
                if (this.currentFrame.getFreeUserDataLength() > isp.getLength()) {
                    // Add the packet
                    ((TcTransferFrameBuilder) this.currentFrame).addData(isp.getPacket());
                } else {
                    // If the packet does not fit in, then finalize the current frame (in line with documented behaviour)
                    TcTransferFrame toSend = this.currentFrame.build();
                    this.currentFrame = null;
                    // Decrement i of 1, to reprocess this packet
                    --i;
                    // Notify
                    notifyTransferFrameGenerated(toSend, calculateRemainingData(packets, i));
                }
            }
        }
        if (this.currentFrame != null) {
            // Close the last frame and send it
            TcTransferFrame toSend = this.currentFrame.build();
            this.currentFrame = null;
            // Notify
            notifyTransferFrameGenerated(toSend, 0);
        }
        return 0;
    }

    @Override
    public int dispatch(SpacePacket isp) {
        return dispatch(isAdMode(), getMapId(), isp);
    }

    public int dispatch(boolean adMode, int mapId, SpacePacket isp) {
        return dispatch(adMode, mapId, Collections.singletonList(isp));
    }

    @Override
    public int dispatch(SpacePacket... isp) {
        return dispatch(isAdMode(), getMapId(), isp);
    }

    public int dispatch(boolean adMode, int mapId, SpacePacket... isp) {
        return dispatch(adMode, mapId, Arrays.asList(isp));
    }

    public int dispatch(byte[] userData) {
        return dispatch(isAdMode(), getMapId(), userData);
    }

    public int dispatch(boolean adMode, int mapId, byte[] userData) {
        if (getMode() != VirtualChannelAccessMode.Data) {
            throw new IllegalStateException("Virtual channel " + getVirtualChannelId() + " access mode set to mode " + getMode() + ", but requested User Data access");
        }
        int maxDataPerFrame = getMaxUserDataLength();
        // If the data fits it, then create the frame and add it to the frame
        if (maxDataPerFrame >= userData.length) {
            byte[] secH = secHeaderSupplier != null ? secHeaderSupplier.get() : new byte[0];
            byte[] secT = secTrailerSupplier != null ? secTrailerSupplier.get() : new byte[0];

            this.currentFrame = TcTransferFrameBuilder.create(isFecfPresent())
                    .setSpacecraftId(getSpacecraftId())
                    .setVirtualChannelId(getVirtualChannelId())
                    .setFrameSequenceNumber(incrementVirtualChannelFrameCounter(256))
                    .setBypassFlag(!adMode)
                    .setControlCommandFlag(false)
                    .setSecurity(secH, secT);
            if (segmented) {
                ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.NO_SEGMENT, mapId);
            }
            // Add the data
            ((TcTransferFrameBuilder) this.currentFrame).addData(userData);
            // Close the frame and send it
            TcTransferFrame toSend = this.currentFrame.build();
            this.currentFrame = null;
            // Notify
            notifyTransferFrameGenerated(toSend, 0);
        } else {
            // If the data does not fit in, remember that you created this frame now, so you have to segment it
            // across several frames. As it is either VCA access or MAP SDU access, we do not set any constraint.
            byte[] packetToSend = userData;

            int chunks = packetToSend.length / maxDataPerFrame + (packetToSend.length % maxDataPerFrame == 0 ? 0 : 1);
            // Send the chunks
            for (int cki = 0; cki < chunks; ++cki) {
                byte[] secH = secHeaderSupplier != null ? secHeaderSupplier.get() : new byte[0];
                byte[] secT = secTrailerSupplier != null ? secTrailerSupplier.get() : new byte[0];

                this.currentFrame = TcTransferFrameBuilder.create(isFecfPresent())
                        .setSpacecraftId(getSpacecraftId())
                        .setVirtualChannelId(getVirtualChannelId())
                        .setFrameSequenceNumber(incrementVirtualChannelFrameCounter(256))
                        .setBypassFlag(!adMode)
                        .setControlCommandFlag(false)
                        .setSecurity(secH, secT);
                if (segmented) {
                    // First chunk is FIRST
                    if (cki == 0) {
                        ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.FIRST, mapId);
                    } else if (cki == chunks - 1) {
                        // Last chunk is LAST
                        ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.LAST, mapId);
                    } else {
                        // Middle chunks are CONTINUE
                        ((TcTransferFrameBuilder) this.currentFrame).setSegment(TcTransferFrame.SequenceFlagType.CONTINUE, mapId);
                    }
                }
                int currOffset = cki * maxDataPerFrame;
                ((TcTransferFrameBuilder) this.currentFrame).addData(packetToSend, currOffset, cki == chunks - 1 ? packetToSend.length - currOffset : maxDataPerFrame);
                TcTransferFrame toSend = this.currentFrame.build();
                this.currentFrame = null;
                notifyTransferFrameGenerated(toSend, packetToSend.length - currOffset);
            }
        }
        return 0;
    }

    /**
     * This method is not supported for this class and throws {@link UnsupportedOperationException} if invoked.
     *
     * @param bitstreamData not applicable
     * @return a runtime exception
     */
    @Override
    public int dispatch(BitstreamData bitstreamData) {
        throw new UnsupportedOperationException("Virtual channel " + getVirtualChannelId() + " cannot dispatch frames with Bitstream data, data not supported");
    }

    /**
     * This method is not supported for this class and throws {@link UnsupportedOperationException} if invoked.
     *
     * @param idlePattern not applicable
     */
    @Override
    public void dispatchIdle(byte[] idlePattern) {
        throw new UnsupportedOperationException("Virtual channel " + getVirtualChannelId() + " cannot dispatch idle frames, type of frame not supported");
    }

    /**
     * This method returns the maximum amount of bytes that this virtual channel can pack into the user data field of a
     * transfer frame. It depends on the segmentation, presence of the FECF and presence of the security information.
     *
     * @return user data field maximum amount (bytes)
     */
    @Override
    public int getMaxUserDataLength() {
        return TcTransferFrameBuilder.computeMaxUserDataLength(isFecfPresent()) - (segmented ? 1 : 0) - secHeaderLength - secTrailerLength;
    }

}

