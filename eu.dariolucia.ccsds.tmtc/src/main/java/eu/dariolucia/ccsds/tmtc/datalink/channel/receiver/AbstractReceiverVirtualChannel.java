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

package eu.dariolucia.ccsds.tmtc.datalink.channel.receiver;

import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * This class represents an abstraction of a virtual channel using for receiving purposes. Typical use cases for this class
 * (and derivatives) are in the implementation of a telemetry stream processed by a ground segment system, or for the
 * reception and processing of telecommands. This class expects that an external entity pushes one or more transfer frames.
 * Space packets, bitstream data or user data are emitted as extracted from the frame. If frame gaps are detected, these
 * are reported.
 *
 * In order to receive the extracted space packets, gap notifications, and other data, registration of a {@link IVirtualChannelReceiverOutput}
 * implementation shall be performed via the register method.
 *
 * This class is not thread safe.
 *
 * @param <T> the type of transfer frame
 */
public abstract class AbstractReceiverVirtualChannel<T extends AbstractTransferFrame> implements Consumer<T> {

    private final List<IVirtualChannelReceiverOutput> listeners = new CopyOnWriteArrayList<>();

    private final int virtualChannelId;

    private final VirtualChannelAccessMode mode;

    private final boolean exceptionIfVcViolated;

    private volatile int currentVcSequenceCounter = -1;

    // If not null, it indicates that a space pdu of the already allocated length is under reconstruction (segmented)
    private final byte[] currentPacket = new byte[65536]; // Max pdu length
    // It points to the next byte to the written in currentPacket
    private int currentOffset = -1;
    //
    private int currentPacketLength = -1;
    //
    private T currentFirstFrame = null;

    protected AbstractReceiverVirtualChannel(int virtualChannelId, VirtualChannelAccessMode mode, boolean exceptionIfVcViolated) {
        this.virtualChannelId = virtualChannelId;
        this.mode = mode;
        this.exceptionIfVcViolated = exceptionIfVcViolated;
    }

    protected abstract int getVcFrameCounterModulo();

    public final VirtualChannelAccessMode getReceiverMode() {
        return this.mode;
    }

    public final int getVirtualChannelId() {
        return virtualChannelId;
    }

    public int getCurrentVcSequenceCounter() {
        return currentVcSequenceCounter;
    }

    public final void register(IVirtualChannelReceiverOutput listener) {
        this.listeners.add(listener);
    }

    public final void deregister(IVirtualChannelReceiverOutput listener) {
        this.listeners.remove(listener);
    }

    protected final void notifyTransferFrameReceived(T frame) {
        this.listeners.forEach(o -> o.transferFrameReceived(this, frame));
    }

    protected final void notifyBitstreamExtracted(T frame, byte[] data, int numBits) {
        this.listeners.forEach(o -> o.bitstreamExtracted(this, frame, data, numBits));
    }

    protected final void notifyDataExtracted(T frame, byte[] data) {
        this.listeners.forEach(o -> o.dataExtracted(this, frame, data));
    }

    protected final void notifySpacePacketExtracted(T frame, byte[] packet, boolean qualityIndicator) {
        this.listeners.forEach(o -> o.spacePacketExtracted(this, frame, packet, qualityIndicator));
    }

    protected final void notifyGapDetected(int expectedVcCount, int receivedVcCount, int missingFrames) {
        this.listeners.forEach(o -> o.gapDetected(this, expectedVcCount, receivedVcCount, missingFrames));
    }

    @Override
    public void accept(T frame) {
        processFrame(frame);
    }

    public void processFrame(T frame) {
        if (frame.getVirtualChannelId() != getVirtualChannelId()) {
            if (this.exceptionIfVcViolated) {
                // Exception
                throw new IllegalArgumentException("Transfer frame with VC ID " + frame.getVirtualChannelId() + " received on receiver for virtual channel " + getVirtualChannelId());
            } else {
                // Silently drop the frame
                return;
            }
        }
        // forwardItem frame reception
        notifyTransferFrameReceived(frame);

        // check for gaps
        // If gap, notify gap and update counter
        boolean gapDetected = false;
        if (isGapDetectionApplicable(frame) && this.currentVcSequenceCounter > -1) {
            int expectedNext = (this.currentVcSequenceCounter + 1) % getVcFrameCounterModulo();
            int current = frame.getVirtualChannelFrameCount();
            if (current != expectedNext) {
                // Gap found
                gapDetected = true;
                int missingFrames = (current > expectedNext) ? (current - expectedNext) : (getVcFrameCounterModulo() - expectedNext + current);
                notifyGapDetected(expectedNext, current, missingFrames);
            }
        }
        this.currentVcSequenceCounter = frame.getVirtualChannelFrameCount();

        switch (this.mode) {
            case Packet:
                extractPacket(frame, gapDetected);
                return;
            case Data:
                extractData(frame, gapDetected);
                return;
            case Bitstream:
                extractBitstream(frame, gapDetected);
                return;
        }
    }

    protected final void doExtractPacket(T frame, boolean gapDetected) {
        // If the frame is idle frame, return
        if (frame.isIdleFrame()) {
            return;
        }
        // If gapDetected and pdu reconstruction is pending, close the pdu with dummy data and forwardItem pdu (quality = false)
        // Clearly, the packet is closed only if you can close it (length fully known)
        if (gapDetected && isReconstructionPending()) {
            closeCurrentPacket(frame);
        }
        // Keep reconstructing/extracting the packets and notify accordingly
        extractPackets(frame);
    }

    protected final void extractPackets(T frame) {
        // Guard conditions
        if (frame.isIdleFrame()) {
            throw new IllegalArgumentException("Space packets cannot be extracted from idle frames, this is a bug");
        }
        byte[] fullFrame = frame.getFrame();
        int firstFrameDataOffset = retrievePacketDataFieldStart(frame);
        int frameDataLength = retrievePacketDataFieldLength(frame);

        int alreadyRead = 0;
        // Reconstruction on going (segmented pdu)
        if (isReconstructionPending()) {
            if (this.currentPacketLength == -1) {
                // If we are here it means that we started reading a pdu, but we still do not know how long this
                // pdu is.
                // We need to read 6 - currentOffset bytes to derive a length. If this space is not even available
                // before the start of the next pdu, or the end of the pdu, then we silently discard the initial piece
                // of pdu we read. In such a case, there is a big problem with the data handling ... maybe we should
                // just fail.
                int headerToRead = 6 - this.currentOffset;
                if (!frameContainsNoStartOfPacket(frame) && headerToRead > retrieveFirstHeaderPointer(frame)) {
                    // Problem: abort, what was read is really to short to make any guess about the pdu
                    alreadyRead = -1;
                    this.currentOffset = -1;
                } else {
                    // Read what is remaining and derive the length
                    System.arraycopy(fullFrame, firstFrameDataOffset, this.currentPacket, this.currentOffset, headerToRead);
                    this.currentOffset += headerToRead;
                    ByteBuffer bb = ByteBuffer.wrap(this.currentPacket, 0, 6);
                    bb.getInt(); // discard
                    this.currentPacketLength = Short.toUnsignedInt(bb.getShort()) + 1 + 6;
                    alreadyRead = headerToRead;
                }
            }
            // If now currentOffset is -1, the pdu reconstruction was aborted. If not, we can go on
            if (this.currentOffset != -1) {
                // Check that there is enough space between the beginning of the data field and the first header pointer
                // location
                int yetToRead = this.currentPacketLength - this.currentOffset;
                // If the frame contains no start pdu, copy what is remaining (as much as you can)
                if (frameContainsNoStartOfPacket(frame)) {
                    int toCopy = Math.min(yetToRead, frameDataLength);
                    System.arraycopy(fullFrame, firstFrameDataOffset + alreadyRead, this.currentPacket, this.currentOffset, toCopy);
                    this.currentOffset += toCopy;
                } else {
                    // If the frame has a start pdu, check if the bytes between the start of the data field and the first
                    // header pointer
                    int firstHeaderPointer = retrieveFirstHeaderPointer(frame);
                    if (yetToRead > firstHeaderPointer - alreadyRead) {
                        // Packet overlap: close the reconstruction of the current pdu and notify it with bad quality
                        System.arraycopy(fullFrame, firstFrameDataOffset + alreadyRead, this.currentPacket, this.currentOffset, firstHeaderPointer - alreadyRead);
                        notifySpacePacketExtracted(this.currentFirstFrame, toPacket(), false);
                        this.currentOffset = -1;
                        this.currentPacketLength = -1;
                        this.currentFirstFrame = null;
                    } else {
                        // No pdu overlap: close the reconstruction with success
                        System.arraycopy(fullFrame, firstFrameDataOffset + alreadyRead, this.currentPacket, this.currentOffset, yetToRead);
                        this.currentOffset += yetToRead; // this.currentOffset should be equal to this.currentPacket.length
                    }
                }
                // If the pdu is closed, notify it with success
                if (this.currentPacketLength != -1 && (this.currentPacketLength == this.currentOffset)) {
                    notifySpacePacketExtracted(this.currentFirstFrame, toPacket(), true);
                    this.currentOffset = -1;
                    this.currentPacketLength = -1;
                    this.currentFirstFrame = null;
                }
            }
        }
        // Extract packets
        if (!frameContainsNoStartOfPacket(frame)) {
            int currentHeaderPointer = retrieveFirstHeaderPointer(frame);
            while (currentHeaderPointer < frameDataLength) {
                currentHeaderPointer = nextPacket(frame, fullFrame, firstFrameDataOffset, frameDataLength, currentHeaderPointer);
            }
        }
    }

    protected final int nextPacket(T frame, byte[] fullFrame, int firstFrameDataOffset, int frameDataLength, int currentHeaderPointer) {
        this.currentOffset = 0;
        this.currentFirstFrame = frame;
        // If at least 6 bytes are available, then the pdu length can be derived, do it
        if (currentHeaderPointer + 6 < frameDataLength) {
            ByteBuffer bb = ByteBuffer.wrap(fullFrame, firstFrameDataOffset + currentHeaderPointer, 6);
            bb.getInt(); // discard
            this.currentPacketLength = Short.toUnsignedInt(bb.getShort()) + 1 + 6; // the field returns the length of the packet data field - 1, we have to add also the header length (6)
            // Now read as much as you can, which is the minimum between the packet length and the remaining data in the frame.
            int toRead = Math.min(this.currentPacketLength, (frameDataLength + firstFrameDataOffset) - (firstFrameDataOffset + currentHeaderPointer));
            System.arraycopy(fullFrame, firstFrameDataOffset + currentHeaderPointer, this.currentPacket, this.currentOffset, toRead);
            this.currentOffset += toRead;
            // If the pdu is complete, notify
            if (toRead == this.currentPacketLength) {
                // Packet complete, notify
                notifySpacePacketExtracted(this.currentFirstFrame, toPacket(), true);
                this.currentOffset = -1;
                this.currentPacketLength = -1;
                this.currentFirstFrame = null;
            }
            return currentHeaderPointer + toRead;
        } else {
            // Read what you can
            int toRead = frameDataLength - currentHeaderPointer;
            System.arraycopy(fullFrame, firstFrameDataOffset + currentHeaderPointer, this.currentPacket, this.currentOffset, toRead);
            this.currentOffset += toRead;
            return currentHeaderPointer + toRead;
        }
    }

    protected final void closeCurrentPacket(T frame) {
        if (this.currentOffset == -1) {
            throw new IllegalStateException("Closing a void space pdu, this is a bug");
        }
        // Close only if you can
        if(this.currentPacketLength > -1) {
            notifySpacePacketExtracted(this.currentFirstFrame, toPacket(), false);
        }
        // If you could not close it, then it is impossible to understand the length, there must have been a gap, ignored
        currentOffset = -1;
        currentPacketLength = -1;
        currentFirstFrame = null;
    }

    protected byte[] toPacket() {
        return Arrays.copyOfRange(this.currentPacket, 0, this.currentPacketLength);
    }

    protected boolean isReconstructionPending() {
        return this.currentOffset != -1;
    }

    protected abstract boolean frameContainsNoStartOfPacket(T frame);

    protected abstract int retrieveFirstHeaderPointer(T frame);

    protected abstract int retrievePacketDataFieldStart(T frame);

    protected abstract int retrievePacketDataFieldLength(T frame);

    protected abstract boolean isGapDetectionApplicable(T frame);

    /**
     * Subclasses can override, even though it should not be necessary.
     *
     * @param frame       the frame from which the data field must be retrieved
     * @param gapDetected if a gap in the frame counter was detected
     */
    protected void extractData(T frame, boolean gapDetected) {
        // This overwrites the setting in the frame
        byte[] dataField = frame.getDataFieldCopy();
        notifyDataExtracted(frame, dataField);
    }

    /**
     * Subclasses can override.
     *
     * @param frame       the frame from which the bit stream must be retrieved
     * @param gapDetected if a gap in the frame counter was detected
     */
    protected void extractBitstream(T frame, boolean gapDetected) {
        extractData(frame, gapDetected);
    }

    /**
     * Subclasses can override, e.g. to add additional checks.
     *
     * @param frame       the frame from which the packets must be retrieved
     * @param gapDetected if a gap in the frame counter was detected
     */
    protected void extractPacket(T frame, boolean gapDetected) {
        doExtractPacket(frame, gapDetected);
    }
}
