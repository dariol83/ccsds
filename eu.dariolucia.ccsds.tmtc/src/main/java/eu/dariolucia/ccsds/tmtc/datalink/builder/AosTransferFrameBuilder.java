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
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AosTransferFrame;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class AosTransferFrameBuilder implements ITransferFrameBuilder<AosTransferFrame> {

    public static int computeUserDataLength(int length, boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, AosTransferFrame.UserDataType userDataType, boolean ocfPresent, boolean fecfPresent) {
        int typeBasedLength = 0;
        if(userDataType == AosTransferFrame.UserDataType.M_PDU || userDataType == AosTransferFrame.UserDataType.B_PDU) {
            typeBasedLength = 2;
        }
        return length - AosTransferFrame.AOS_PRIMARY_HEADER_LENGTH - (frameHeaderErrorControlPresent ? AosTransferFrame.AOS_PRIMARY_HEADER_FHEC_LENGTH : 0) - transferFrameInsertZoneLength - typeBasedLength - (ocfPresent ? 4 : 0) - (fecfPresent ? 2 : 0);
    }

    public static AosTransferFrameBuilder create(int length, boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, AosTransferFrame.UserDataType userDataType, boolean ocfPresent, boolean fecfPresent) {
        return new AosTransferFrameBuilder(length, frameHeaderErrorControlPresent, transferFrameInsertZoneLength, userDataType, ocfPresent, fecfPresent);
    }

    private final int length;
    private final int insertZoneLength;
    private final boolean frameHeaderErrorControlPresent;
    private final boolean ocfPresent;
    private final boolean fecfPresent;
    private final AosTransferFrame.UserDataType userDataType;

    private int freeUserDataLength;

    private int spacecraftId;
    private int virtualChannelId;
    private int virtualChannelFrameCount;
    private int virtualChannelFrameCountCycle;
    private boolean replayFlag;
    private boolean virtualChannelFrameCountUsageFlag;

    private boolean idle;

    private byte[] insertZone;

    private byte[] ocf;

    private byte[] securityHeader;
    private byte[] securityTrailer;

    private List<AosTransferFrameBuilder.PayloadUnit> payloadUnits = new LinkedList<>();

    public AosTransferFrameBuilder(int length, boolean frameHeaderErrorControlPresent, int insertZoneLength, AosTransferFrame.UserDataType userDataType, boolean ocfPresent, boolean fecfPresent) {
        this.length = length;
        this.insertZoneLength = insertZoneLength;
        this.frameHeaderErrorControlPresent = frameHeaderErrorControlPresent;
        this.ocfPresent = ocfPresent;
        this.fecfPresent = fecfPresent;
        this.userDataType = userDataType;

        this.freeUserDataLength = computeUserDataLength(length, frameHeaderErrorControlPresent, insertZoneLength, userDataType, ocfPresent, fecfPresent);
    }

    public AosTransferFrameBuilder setSpacecraftId(int spacecraftId) {
        if(spacecraftId < 0 || spacecraftId > 255) {
            throw new IllegalArgumentException("Spacecraft ID must be between 0 and 255 (inclusive), got " + spacecraftId);
        }

        this.spacecraftId = spacecraftId;
        return this;
    }

    public AosTransferFrameBuilder setVirtualChannelId(int virtualChannelId) {
        if(virtualChannelId < 0 || virtualChannelId > 63) {
            throw new IllegalArgumentException("Virtual Channel ID must be between 0 and 63 (inclusive), got " + virtualChannelId);
        }

        this.virtualChannelId = virtualChannelId;
        return this;
    }

    public AosTransferFrameBuilder setVirtualChannelFrameCount(int virtualChannelFrameCount) {
        if(virtualChannelFrameCount < 0 || virtualChannelFrameCount > 16777215) {
            throw new IllegalArgumentException("Virtual Channel Frame Count must be between 0 and 16777215 (inclusive), got " + virtualChannelFrameCount);
        }

        this.virtualChannelFrameCount = virtualChannelFrameCount;
        return this;
    }

    public AosTransferFrameBuilder setVirtualChannelFrameCountCycle(int virtualChannelFrameCountCycle) {
        if(virtualChannelFrameCountCycle < 0 || virtualChannelFrameCountCycle > 15) {
            throw new IllegalArgumentException("Virtual Channel Frame Count Cycle must be between 0 and 15 (inclusive), got " + virtualChannelFrameCountCycle);
        }

        this.virtualChannelFrameCountCycle = virtualChannelFrameCountCycle;
        return this;
    }

    public AosTransferFrameBuilder setInsertZone(byte[] insertZone) {
        if(insertZoneLength == 0) {
            throw new IllegalStateException("Insert Zone not marked as present");
        }
        if(insertZoneLength != insertZone.length) {
            throw new IllegalArgumentException("Insert Zone length preallocated to " + insertZoneLength + " octets, got " + insertZone.length + " octets");
        }

        this.insertZone = insertZone;
        return this;
    }

    public AosTransferFrameBuilder setVirtualChannelFrameCountUsageFlag(boolean virtualChannelFrameCountUsageFlag) {
        this.virtualChannelFrameCountUsageFlag = virtualChannelFrameCountUsageFlag;
        return this;
    }

    public AosTransferFrameBuilder setReplayFlag(boolean replayFlag) {
        this.replayFlag = replayFlag;
        return this;
    }

    public AosTransferFrameBuilder setIdle() {
        this.idle = true;
        return this;
    }

    public AosTransferFrameBuilder setSecurity(byte[] header, byte[] trailer) {
        if(header == null && trailer == null) {
            // Do not do anything
            return this;
        }
        if(isFull()) {
            throw new IllegalArgumentException("AOS Frame already full");
        }
        if(getFreeUserDataLength() < header.length + trailer.length) {
            throw new IllegalArgumentException("AOS Frame cannot accomodate additional "
                    + (header.length + trailer.length) + " bytes, remaining space is " + getFreeUserDataLength() + " bytes");
        }
        this.securityHeader = header;
        this.securityTrailer = trailer;
        this.freeUserDataLength -= (header.length + trailer.length);

        return this;
    }

    public AosTransferFrameBuilder setOcf(byte[] ocf) {
        if(!ocfPresent) {
            throw new IllegalStateException("OCF not marked as present");
        }
        if(ocf.length != 4) {
            throw new IllegalArgumentException("OCF wrong size, expected 4, got " + ocf.length);
        }

        this.ocf = ocf;
        return this;
    }

    private int addData(byte[] b, int offset, int length, int type) {
        return addData(b, offset, length, type, -1);
    }

    private int addData(byte[] b, int offset, int length, int type, int validDataBits) {
        // Compute if you can add the requested amount
        int dataToBeWritten = freeUserDataLength >= length ? length : freeUserDataLength;
        int notWrittenData = freeUserDataLength < length ? length - freeUserDataLength : 0;
        if(dataToBeWritten > 0) {
            this.payloadUnits.add(new AosTransferFrameBuilder.PayloadUnit(type, Arrays.copyOfRange(b, offset, offset + dataToBeWritten), validDataBits));
            freeUserDataLength -= dataToBeWritten;
        }
        return notWrittenData;
    }

    public int addBitstreamData(byte[] data, int validDataBits) {
        if(userDataType != AosTransferFrame.UserDataType.B_PDU) {
            throw new IllegalArgumentException("Only B_PDU AOS frames can contain bitstream data");
        }
        return addData(data, 0 , data.length, PayloadUnit.TYPE_BITSTREAM, validDataBits);
    }

    public int addSpacePacket(byte[] packet) {
        if(userDataType != AosTransferFrame.UserDataType.M_PDU) {
            throw new IllegalArgumentException("Only M_PDU AOS frames can contain space packets");
        }
        return addData(packet, 0, packet.length, PayloadUnit.TYPE_PACKET);
    }

    public int addData(byte[] data) {
        return addData(data, 0, data.length, PayloadUnit.TYPE_DATA);
    }

    public int addData(byte[] data, int offset, int length) {
        return addData(data, offset, length, PayloadUnit.TYPE_DATA);
    }

    @Override
    public int getFreeUserDataLength() {
        return this.freeUserDataLength;
    }

    public boolean isFull() {
        return this.freeUserDataLength == 0;
    }

    @Override
    public AosTransferFrame build() {
        if(this.freeUserDataLength > 0) {
            // Exception
            throw new IllegalStateException("AOS Transfer Frame Data Field not filled up, still " + this.freeUserDataLength + " bytes missing");
        }

        if(insertZoneLength > 0 && insertZone == null) {
            // Exception
            throw new IllegalStateException("Insert Zone marked as present, but not set");
        }

        if(ocfPresent && ocf == null) {
            // Exception
            throw new IllegalStateException("OCF marked as present, but not set");
        }

        ByteBuffer bb = ByteBuffer.allocate(this.length);

        // Write the primary header (6 bytes)
        short firstTwoOctets = 0;
        firstTwoOctets |= (short) (0x4000);
        firstTwoOctets |= (short) (spacecraftId << 6);
        firstTwoOctets |= (short) (virtualChannelId);

        bb.putShort(firstTwoOctets);

        int next4octets = 0;
        next4octets |= (this.virtualChannelFrameCount << 8);

        if(this.replayFlag) {
            next4octets |= 0x00000080;
        }

        if(this.virtualChannelFrameCountUsageFlag) {
            next4octets |= 0x00000040;
        }

        next4octets |= this.virtualChannelFrameCountCycle;

        bb.putInt(next4octets);

        if(this.frameHeaderErrorControlPresent) {
            // Add 2 bytes to be filled after the encoding
            bb.put(new byte[2]);
        }

        if(insertZoneLength > 0) {
            bb.put(this.insertZone);
        }

        // Write security header if present
        if(this.securityHeader != null && this.securityHeader.length > 0) {
            bb.put(this.securityHeader);
        }

        if(userDataType == AosTransferFrame.UserDataType.M_PDU) {
            short firstHeaderPointer = computeMPDUFirstHeaderPointer();
            bb.putShort(firstHeaderPointer);
        } else if(userDataType == AosTransferFrame.UserDataType.B_PDU) {
            short firstHeaderPointer = computeBPDUBitstreamDataPointer();
            bb.putShort(firstHeaderPointer);
        }

        // Write the user data
        for(AosTransferFrameBuilder.PayloadUnit pu : this.payloadUnits) {
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

        byte[] encodedFrame;

        // Compute and write the FECF (if present, 2 bytes)
        if(this.fecfPresent) {
            bb.put((byte) 0x00);
            bb.put((byte) 0x00);
            encodedFrame = bb.array();
            if(frameHeaderErrorControlPresent) {
                computeFHEC(encodedFrame);
            }
            short crc = Crc16Algorithm.getCrc16(encodedFrame, 0, encodedFrame.length - 2);
            encodedFrame[encodedFrame.length - 2] = (byte) (crc >> 8);
            encodedFrame[encodedFrame.length - 1] = (byte) (crc);
        } else {
            encodedFrame = bb.array();
            if(frameHeaderErrorControlPresent) {
                computeFHEC(encodedFrame);
            }
        }

        // Return the frame
        return new AosTransferFrame(encodedFrame, frameHeaderErrorControlPresent, insertZoneLength, userDataType, ocfPresent, fecfPresent,
                securityHeader != null ? securityHeader.length : 0, securityTrailer != null ? securityTrailer.length : 0);
    }

    private void computeFHEC(byte[] encodedFrame) {
        // The R-S encoding will change the value to the bytes number 0, 1 and 5 (master channel ID, virtual channel ID, signalling field)
        encodeAosFrameHeaderErrorControl(encodedFrame);
    }

    /**
     * This method encodes the frame header error control field in-place inside the provided AOS frame (octets 6-7).
     * @param aosFrame the frame
     */
    private void encodeAosFrameHeaderErrorControl(byte[] aosFrame) {
        // Convert octets 0, 1 and 5 into an array of 6 integers, J=4 bits
        byte[] message = new byte[6];
        int[] octetsIdx = new int[] { 0, 1, 5 };
        for(int i = 0; i < octetsIdx.length; ++i) {
            byte b = aosFrame[octetsIdx[i]];
            message[i*2] = (byte) ((b & 0xF0) >>> 4);
            message[i*2 + 1] = (byte) (b & 0x0F);
        }
        // Encode the message
        byte[] encoded = AosTransferFrame.AOS_FRAME_HEADER_ERROR_CONTROL_RS_UTIL.encodeCodeword(message);

        // The encoder always returns the RS symbols at the end
        // Put the values in place
        byte oct6 = 0;
        oct6 |= encoded[6];
        oct6 <<= 4;
        oct6 |= encoded[7];
        aosFrame[6] = oct6;

        byte oct7 = 0;
        oct7 |= encoded[8];
        oct7 <<= 4;
        oct7 |= encoded[9];
        aosFrame[7] = oct7;
    }

    private short computeMPDUFirstHeaderPointer() {
        if(this.idle) {
            return AosTransferFrame.AOS_M_PDU_FIRST_HEADER_POINTER_IDLE;
        } else if(this.payloadUnits.stream().noneMatch((o) -> o.type == PayloadUnit.TYPE_PACKET)) {
            return AosTransferFrame.AOS_M_PDU_FIRST_HEADER_POINTER_NO_PACKET;
        } else {
            short firstPacket = (short) 0;
            for(AosTransferFrameBuilder.PayloadUnit pu : payloadUnits) {
                if(pu.type == PayloadUnit.TYPE_PACKET) {
                    return firstPacket;
                } else {
                    firstPacket += (short) pu.data.length;
                }
            }
            // If here, no pdu present
            return AosTransferFrame.AOS_M_PDU_FIRST_HEADER_POINTER_NO_PACKET;
        }
    }

    private short computeBPDUBitstreamDataPointer() {
        if(this.idle) {
            return AosTransferFrame.AOS_B_PDU_FIRST_HEADER_POINTER_IDLE;
        } else if(this.payloadUnits.stream().noneMatch(PayloadUnit::hasSpuriousData)) {
            return AosTransferFrame.AOS_B_PDU_FIRST_HEADER_POINTER_ALL_DATA;
        } else {
            short lastValidBit = (short) 0;
            for(AosTransferFrameBuilder.PayloadUnit pu : payloadUnits) {
                if(pu.type == PayloadUnit.TYPE_BITSTREAM) {
                    lastValidBit += pu.validDataBits;
                    if(pu.hasSpuriousData()) {
                        return lastValidBit;
                    }
                } else {
                    return lastValidBit;
                }
            }
            // If here, no pdu present
            return AosTransferFrame.AOS_B_PDU_FIRST_HEADER_POINTER_ALL_DATA;
        }
    }

    private class PayloadUnit {
        private static final int TYPE_BITSTREAM = 0;
        private static final int TYPE_PACKET = 1;
        private static final int TYPE_DATA = 2;

        public final int type;
        public final byte[] data;
        public final int validDataBits;

        public PayloadUnit(int type, byte[] data, int validDataBits) {
            this.type = type;
            this.data = data;
            if(type == TYPE_PACKET) {
                this.validDataBits = data.length * 8;
            } else if(type == TYPE_DATA) {
                this.validDataBits = data.length * 8;
            } else if(type == TYPE_BITSTREAM) {
                if(data.length * 8 >= validDataBits) {
                    this.validDataBits = validDataBits;
                } else {
                    this.validDataBits = data.length * 8;
                }
            } else {
                throw new IllegalArgumentException("Type " + type + " not recognized");
            }
        }

        public boolean hasSpuriousData() {
            return data.length * 8 != validDataBits;
        }
    }
}
