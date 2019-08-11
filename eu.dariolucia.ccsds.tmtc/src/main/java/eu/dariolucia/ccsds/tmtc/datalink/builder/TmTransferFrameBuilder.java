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

package eu.dariolucia.ccsds.tmtc.datalink.builder;

import eu.dariolucia.ccsds.tmtc.algorithm.Crc16Algorithm;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TmTransferFrameBuilder implements ITransferFrameBuilder<TmTransferFrame> {

    public static int computeUserDataLength(int length, int secHeaderLength, boolean ocfPresent, boolean fecfPresent) {
        if(secHeaderLength > 63) {
            throw new IllegalArgumentException("Transfer Frame Secondary Header Length cannot be greater than 63, actual " + secHeaderLength);
        }

        return length - TmTransferFrame.TM_PRIMARY_HEADER_LENGTH - (secHeaderLength == 0 ? 0 : 1 + secHeaderLength) - (ocfPresent ? 4 : 0) - (fecfPresent ? 2 : 0);
    }

    public static TmTransferFrameBuilder create(int length, int secHeaderLength, boolean ocfPresent, boolean fecfPresent) {
        return new TmTransferFrameBuilder(length, secHeaderLength, ocfPresent, fecfPresent);
    }

    private final int length;
    private final int secondaryHeaderLength;
    private final boolean ocfPresent;
    private final boolean fecfPresent;
    private int freeUserDataLength;

    private int spacecraftId;
    private int virtualChannelId;
    private int virtualChannelFrameCount;
    private int masterChannelFrameCount;
    private boolean synchronisationFlag;
    private boolean packetOrderFlag;
    private int segmentLengthIdentifier;

    private byte[] secondaryHeader;

    private byte[] ocf;

    private boolean idle;

    private byte[] securityHeader;
    private byte[] securityTrailer;

    private List<PayloadUnit> payloadUnits = new LinkedList<>();

    private TmTransferFrameBuilder(int length, int secondaryHeaderLength, boolean ocfPresent, boolean fecfPresent) {
        if(secondaryHeaderLength > 63) {
            throw new IllegalArgumentException("Transfer Frame Secondary Header Length cannot be greater than 63, got " + secondaryHeaderLength);
        }

        this.length = length;
        this.secondaryHeaderLength = secondaryHeaderLength;
        this.ocfPresent = ocfPresent;
        this.fecfPresent = fecfPresent;
        this.freeUserDataLength = computeUserDataLength(length, secondaryHeaderLength, ocfPresent, fecfPresent);
    }

    public TmTransferFrameBuilder setSpacecraftId(int spacecraftId) {
        if(spacecraftId < 0 || spacecraftId > 1023) {
            throw new IllegalArgumentException("Spacecraft ID must be between 0 and 1023 (inclusive), got " + spacecraftId);
        }

        this.spacecraftId = spacecraftId;
        return this;
    }

    public TmTransferFrameBuilder setVirtualChannelId(int virtualChannelId) {
        if(virtualChannelId < 0 || virtualChannelId > 7) {
            throw new IllegalArgumentException("Virtual Channel ID must be between 0 and 7 (inclusive), got " + virtualChannelId);
        }

        this.virtualChannelId = virtualChannelId;
        return this;
    }

    public TmTransferFrameBuilder setVirtualChannelFrameCount(int virtualChannelFrameCount) {
        if(virtualChannelFrameCount < 0 || virtualChannelFrameCount > 255) {
            throw new IllegalArgumentException("Virtual Channel Frame Count must be between 0 and 255 (inclusive), got " + virtualChannelFrameCount);
        }

        this.virtualChannelFrameCount = virtualChannelFrameCount;
        return this;
    }

    public TmTransferFrameBuilder setMasterChannelFrameCount(int masterChannelFrameCount) {
        if(masterChannelFrameCount < 0 || masterChannelFrameCount > 255) {
            throw new IllegalArgumentException("Master Channel Frame Count must be between 0 and 255 (inclusive), got " + masterChannelFrameCount);
        }

        this.masterChannelFrameCount = masterChannelFrameCount;
        return this;
    }

    public TmTransferFrameBuilder setSecondaryHeader(byte[] secondaryHeader) {
        if(secondaryHeaderLength == 0) throw new IllegalStateException("Secondary header not marked as present");
        if(secondaryHeaderLength != secondaryHeader.length) throw new IllegalArgumentException("Secondary header length preallocated to " + secondaryHeaderLength + " octets, got " + secondaryHeader.length + " octets");

        this.secondaryHeader = secondaryHeader;
        return this;
    }

    public TmTransferFrameBuilder setSynchronisationFlag(boolean synchronisationFlag) {
        this.synchronisationFlag = synchronisationFlag;
        return this;
    }

    public TmTransferFrameBuilder setPacketOrderFlag(boolean packetOrderFlag) {
        this.packetOrderFlag = packetOrderFlag;
        return this;
    }

    public TmTransferFrameBuilder setSegmentLengthIdentifier(int segmentLengthIdentifier) {
        if(segmentLengthIdentifier < 0 || segmentLengthIdentifier > 3) {
            throw new IllegalArgumentException("Segment Length ID must be between 0 and 3 (inclusive)");
        }

        this.segmentLengthIdentifier = segmentLengthIdentifier;
        return this;
    }

    public TmTransferFrameBuilder setOcf(byte[] ocf) {
        if(!ocfPresent) throw new IllegalStateException("OCF not marked as present");
        if(ocf.length != 4) throw new IllegalArgumentException("OCF wrong size, expected 4, got " + ocf.length);

        this.ocf = ocf;
        return this;
    }

    public TmTransferFrameBuilder setIdle() {
        this.idle = true;
        return this;
    }

    public TmTransferFrameBuilder setSecurity(byte[] header, byte[] trailer) {
        if(header == null && trailer == null) {
            // Do not do anything
            return this;
        }
        if(isFull()) {
            throw new IllegalArgumentException("TM Frame already full");
        }
        if(getFreeUserDataLength() < header.length + trailer.length) {
            throw new IllegalArgumentException("TM Frame cannot accomodate additional "
                    + (header.length + trailer.length) + " bytes, remaining space is " + getFreeUserDataLength() + " bytes");
        }
        this.securityHeader = header;
        this.securityTrailer = trailer;
        this.freeUserDataLength -= (header.length + trailer.length);

        return this;
    }

    private int addData(byte[] b, int offset, int length, boolean isPacket) {
        // Compute if you can add the requested amount
        int dataToBeWritten = freeUserDataLength >= length ? length : freeUserDataLength;
        int notWrittenData = freeUserDataLength < length ? length - freeUserDataLength : 0;
        if(dataToBeWritten > 0) {
            this.payloadUnits.add(new PayloadUnit(isPacket, Arrays.copyOfRange(b, offset, offset + dataToBeWritten)));
            freeUserDataLength -= dataToBeWritten;
        }
        return notWrittenData;
    }

    public int addSpacePacket(byte[] packet) {
        return addData(packet, 0, packet.length, true);
    }

    public int addData(byte[] data) {
        return addData(data, 0, data.length, false);
    }

    public int addData(byte[] data, int offset, int length) {
        return addData(data, offset, length, false);
    }

    @Override
    public int getFreeUserDataLength() {
        return this.freeUserDataLength;
    }

    public boolean isFull() {
        return this.freeUserDataLength == 0;
    }

    @Override
    public TmTransferFrame build() {
        if(this.freeUserDataLength > 0) {
            // Exception
            throw new IllegalStateException("Transfer Frame Data Field not filled up, still " + this.freeUserDataLength + " bytes missing");
        }

        if(secondaryHeaderLength > 0 && secondaryHeader == null) {
            // Exception
            throw new IllegalStateException("Secondary Header marked as present, but not set");
        }

        if(ocfPresent && ocf == null) {
            // Exception
            throw new IllegalStateException("OCF marked as present, but not set");
        }

        ByteBuffer bb = ByteBuffer.allocate(this.length);

        // Write the primary header (6 bytes)
        short firstTwoOctets = 0;
        firstTwoOctets |= (short) (spacecraftId << 4);
        firstTwoOctets |= (short) (virtualChannelId << 1);
        if(ocfPresent) {
            firstTwoOctets |= (short) (0x0001);
        }
        bb.putShort(firstTwoOctets);

        bb.put((byte)this.masterChannelFrameCount);
        bb.put((byte)this.virtualChannelFrameCount);

        short lastTwoOctets = 0;
        if(secondaryHeaderLength > 0 && secondaryHeader != null) {
            lastTwoOctets |= (short) (0x8000);
        }

        if(synchronisationFlag) {
            lastTwoOctets |= (short) (0x4000);
        }

        if(packetOrderFlag) {
            lastTwoOctets |= (short) (0x2000);
        }

        switch(segmentLengthIdentifier) {
            case 0:
                lastTwoOctets |= (short) (0x0000);
                break;
            case 1:
                lastTwoOctets |= (short) (0x0800);
                break;
            case 2:
                lastTwoOctets |= (short) (0x1000);
                break;
            case 3:
                lastTwoOctets |= (short) (0x1800);
                break;
            default:
                throw new RuntimeException("Segment Length ID cannot be " + segmentLengthIdentifier + ", software bug");
        }

        short firstHeaderPointer = computeFirstHeaderPointer();
        lastTwoOctets |= (short) firstHeaderPointer;

        bb.putShort(lastTwoOctets);

        // Write the secondary header (if present, 1 byte + secondary header length)
        if(secondaryHeaderLength > 0 && secondaryHeader != null) {
            bb.put((byte) this.secondaryHeaderLength);
            bb.put(this.secondaryHeader);
        }

        // Write security header if present
        if(this.securityHeader != null && this.securityHeader.length > 0) {
            bb.put(this.securityHeader);
        }

        // Write the user data
        for(PayloadUnit pu : this.payloadUnits) {
            bb.put(pu.data);
        }

        // Write security trailer if present
        if(this.securityTrailer != null && this.securityTrailer.length > 0) {
            bb.put(this.securityTrailer);
        }

        // Write the OCF (if present, 4 bytes)
        if(this.ocfPresent && this.ocf != null) {
            bb.put(this.ocf);
        }

        byte[] encodedFrame = null;

        // Compute and write the FECF (if present, 2 bytes)
        if(this.fecfPresent) {
            bb.put((byte) 0x00);
            bb.put((byte) 0x00);
            encodedFrame = bb.array();
            short crc = Crc16Algorithm.getCrc16(encodedFrame, 0, encodedFrame.length - 2);
            encodedFrame[encodedFrame.length - 2] = (byte) (crc >> 8);
            encodedFrame[encodedFrame.length - 1] = (byte) (crc);
        } else {
            encodedFrame = bb.array();
        }

        // Return the frame
        return new TmTransferFrame(encodedFrame, fecfPresent, securityHeader != null ? securityHeader.length : 0, securityTrailer != null ? securityTrailer.length : 0);
    }

    private short computeFirstHeaderPointer() {
        if(this.idle) {
            return TmTransferFrame.TM_FIRST_HEADER_POINTER_IDLE;
        } else if(this.payloadUnits.stream().noneMatch((o) -> o.packet)) {
            return TmTransferFrame.TM_FIRST_HEADER_POINTER_NO_PACKET;
        } else {
            short firstPacket = (short) 0;
            for(PayloadUnit pu : payloadUnits) {
                if(pu.packet) {
                    return firstPacket;
                } else {
                    firstPacket += (short) pu.data.length;
                }
            }
            // If here, no pdu present
            return TmTransferFrame.TM_FIRST_HEADER_POINTER_NO_PACKET;
        }
    }

    private class PayloadUnit {
        public final boolean packet;
        public final byte[] data;

        public PayloadUnit(boolean packet, byte[] data) {
            this.packet = packet;
            this.data = data;
        }
    }
}
