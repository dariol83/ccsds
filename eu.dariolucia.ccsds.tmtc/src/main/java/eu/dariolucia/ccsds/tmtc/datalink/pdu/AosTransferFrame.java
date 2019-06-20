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

package eu.dariolucia.ccsds.tmtc.datalink.pdu;

import eu.dariolucia.ccsds.tmtc.algorithm.Crc16Algorithm;
import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.IDecodingFunction;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class AosTransferFrame extends AbstractTransferFrame {

    public static final int AOS_PRIMARY_HEADER_LENGTH = 6;
    public static final int AOS_PRIMARY_HEADER_FHEC_LENGTH = 2;
    public static final short AOS_M_PDU_FIRST_HEADER_POINTER_IDLE = 2046;
    public static final short AOS_M_PDU_FIRST_HEADER_POINTER_NO_PACKET = 2047;
    public static final short AOS_B_PDU_FIRST_HEADER_POINTER_IDLE = 16382;
    public static final short AOS_B_PDU_FIRST_HEADER_POINTER_ALL_DATA = 16383;

    // AOS Blue Book, 4.1.2.6.5.
//    public static final ReedSolomonAlgorithm AOS_FRAME_HEADER_ERROR_CONTROL_RS_UTIL = new ReedSolomonAlgorithm(
//                    10,
//                    6,
//                    0x13,      // As per AOS Blue Book specs: x^4 + x + 1 = 10011 = 19 = 0x13
//                    new int[]{1, 8, 2, 8},      // As per AOS Blue Book specs: x^4 + (a3 x^3) + (a x^2) + (a3 x) + 1
//                    new int[]{12, 11, 5, 10}    // As per AOS Blue Book specs: g(x) = (x + a6)(x + a7)(x + a8)(x + a9) : only needed for decoding verify
//            );

    public static final ReedSolomonAlgorithm AOS_FRAME_HEADER_ERROR_CONTROL_RS_UTIL = new ReedSolomonAlgorithm(
                    10,
                    6,
                    0x13,      // As per AOS Blue Book specs: x^4 + x + 1 = 10011 = 19 = 0x13
                    2,
                    6,
                    true // TODO, check
    );

    public static IDecodingFunction<AosTransferFrame> decodingFunction(boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, UserDataType userDataType, boolean ocfPresent, boolean fecfPresent) {
        return input -> new AosTransferFrame(input, frameHeaderErrorControlPresent, transferFrameInsertZoneLength, userDataType, ocfPresent, fecfPresent);
    }

    public enum UserDataType {
        M_PDU,
        B_PDU,
        VCA,
        IDLE
    }

    private UserDataType userDataType;

    private boolean frameHeaderErrorControlPresent; // if present, 2 octets
    private boolean validHeader;
    private int transferFrameInsertZoneLength;

    private boolean replayFlag;
    private boolean virtualChannelFrameCountUsageFlag;
    private byte virtualChannelFrameCountCycle;

    private boolean idleFrame;

    // Only valid for M_PDU userDataType
    private short firstHeaderPointer;
    private boolean noStartPacket;
    private short packetZoneStart;

    // Only valid for B_PDU userDataType
    private short bitstreamDataPointer;
    private short bitstreamDataZoneStart;
    private boolean bitstreamAllValid;

    public AosTransferFrame(byte[] frame, boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, UserDataType userDataType, boolean ocfPresent, boolean fecfPresent) {
        super(frame, fecfPresent);
        // Frame header error control field is only assumed as an additional 2 bytes: no R-S decoding on the protected header fields is performed, only error control
        this.ocfPresent = ocfPresent;
        this.frameHeaderErrorControlPresent = frameHeaderErrorControlPresent;
        this.transferFrameInsertZoneLength = transferFrameInsertZoneLength;
        this.userDataType = userDataType;
        decode();
    }

    @Override
    protected void decode() {
        ByteBuffer in = ByteBuffer.wrap(frame);
        short twoOctets = in.getShort();

        short tfvn = (short) ((twoOctets & (short) 0xC000) >> 14);

        // 4.1.2.2.2.2
        if (tfvn != 1) {
            throw new IllegalArgumentException("Transfer Frame Version Number: expected 1, actual " + tfvn);
        }

        transferFrameVersionNumber = tfvn;
        spacecraftId = (short) ((twoOctets & (short) 0x3FC0) >> 6);
        virtualChannelId = (short) ((twoOctets & (short) 0x003F));

        if(virtualChannelId == 63) {
            idleFrame = true;
        }

        virtualChannelFrameCount = (Byte.toUnsignedInt(in.get()) & 0xFF) << 16 |
                (Byte.toUnsignedInt(in.get()) & 0xFF) << 8 | (Byte.toUnsignedInt(in.get()) & 0xFF);

        byte signalingField = in.get();

        replayFlag = (signalingField & (byte) 0x80) != 0;
        virtualChannelFrameCountUsageFlag = (signalingField & (byte) 0x40) != 0;
        virtualChannelFrameCountCycle = (byte) (signalingField & (byte) 0x0F);

        // 4.1.2.5.5.3
        if(!virtualChannelFrameCountUsageFlag && virtualChannelFrameCountCycle != 0) {
            throw new IllegalArgumentException("Virtual Channel Frame Count Cycle: expected 0, actual " + virtualChannelFrameCountCycle);
        }

        dataFieldStart = (short) (AOS_PRIMARY_HEADER_LENGTH + (frameHeaderErrorControlPresent ? AOS_PRIMARY_HEADER_FHEC_LENGTH : 0) + transferFrameInsertZoneLength);

        if(frameHeaderErrorControlPresent) {
            in.getShort();
        }

        if(transferFrameInsertZoneLength > 0) {
            in.get(new byte[transferFrameInsertZoneLength]);
        }

        // Depending on the userDataType
        switch(userDataType) {
            case M_PDU:
                twoOctets = in.getShort();
                firstHeaderPointer = (short) ((twoOctets & (short) 0x07FF));
                packetZoneStart = (short) ((frameHeaderErrorControlPresent ? AOS_PRIMARY_HEADER_LENGTH + AOS_PRIMARY_HEADER_FHEC_LENGTH : AOS_PRIMARY_HEADER_LENGTH) + transferFrameInsertZoneLength + 2);
                noStartPacket = firstHeaderPointer == AOS_M_PDU_FIRST_HEADER_POINTER_NO_PACKET;
                idleFrame = firstHeaderPointer == AOS_M_PDU_FIRST_HEADER_POINTER_IDLE;
                break;
            case B_PDU:
                twoOctets = in.getShort();
                bitstreamDataPointer = (short) ((twoOctets & (short) 0x3FFF));
                bitstreamDataZoneStart = (short) ((frameHeaderErrorControlPresent ? AOS_PRIMARY_HEADER_LENGTH + AOS_PRIMARY_HEADER_FHEC_LENGTH : AOS_PRIMARY_HEADER_LENGTH) + transferFrameInsertZoneLength + 2);
                bitstreamAllValid = bitstreamDataPointer == AOS_B_PDU_FIRST_HEADER_POINTER_ALL_DATA;
                idleFrame = bitstreamDataPointer == AOS_B_PDU_FIRST_HEADER_POINTER_IDLE;
                break;
            default:
                // Do nothing
                break;
        }

        // OCF
        if(ocfPresent) {
            if(fecfPresent) {
                ocfStart = (short) (frame.length - 6);
            } else {
                ocfStart = (short) (frame.length - 4);
            }
        } else {
            ocfStart = -1;
        }

        // FECF
        if(fecfPresent) {
            valid = checkValidity();
        } else {
            // With no FECF it is assumed that the frame is valid
            valid = true;
        }

        // FHEC
        if(frameHeaderErrorControlPresent) {
            this.validHeader = checkAosFrameHeaderErrorControlEncoding(this.frame);
        } else {
            // If not present, assume valid header
            this.validHeader = true;
        }
    }

    private boolean checkValidity() {
        // As this method is called by the check() method, the fecfPresent check is already done
        short crc16 = Crc16Algorithm.getCrc16(this.frame, 0,  this.frame.length - 2);
        short crcFromFrame = getFecf();
        return crc16 == crcFromFrame;
    }

    public short getFhec() {
        if(frameHeaderErrorControlPresent) {
            return ByteBuffer.wrap(frame, AOS_PRIMARY_HEADER_LENGTH, AOS_PRIMARY_HEADER_FHEC_LENGTH).getShort();
        } else {
            throw new IllegalStateException("FHEC not present");
        }
    }

    private boolean checkAosFrameHeaderErrorControlEncoding(byte[] aosFrame) {
        // Convert octets 0, 1 and 5, 6 and 7 into an array of 10 integers, J=4 bits, reversed
        byte[] codeword = new byte[10];
        // TODO: check if reverse is needed
        int[] octetsIdx = new int[] { 7, 6, 5, 1, 0 };
        for(int i = 0; i < octetsIdx.length; ++i) {
            byte b = aosFrame[octetsIdx[i]];
            codeword[i*2] = (byte) (b & 0x0F);
            codeword[i*2 + 1] = (byte) ((b & 0xF0) >>> 4);
        }
        // Check the codeword
        // TODO
        // return AOS_FRAME_HEADER_ERROR_CONTROL_RS_UTIL.checkCodeword(codeword);
        return true;
    }

    public boolean isFrameHeaderErrorControlPresent() {
        return frameHeaderErrorControlPresent;
    }

    public boolean isValidHeader() {
        return validHeader;
    }

    public int getTransferFrameInsertZoneLength() {
        return transferFrameInsertZoneLength;
    }

    public boolean isReplayFlag() {
        return replayFlag;
    }

    public boolean isVirtualChannelFrameCountUsageFlag() {
        return virtualChannelFrameCountUsageFlag;
    }

    public byte getVirtualChannelFrameCountCycle() {
        return virtualChannelFrameCountCycle;
    }

    @Override
    public boolean isIdleFrame() {
        return idleFrame;
    }

    public short getPacketZoneStart() {
        return packetZoneStart;
    }

    public short getPacketZoneLength() {
        return (short) ((frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0)) - getPacketZoneStart());
    }

    public UserDataType getUserDataType() {
        return userDataType;
    }

    public short getFirstHeaderPointer() {
        return firstHeaderPointer;
    }

    public boolean isNoStartPacket() {
        return noStartPacket;
    }

    public short getBitstreamDataPointer() {
        return bitstreamDataPointer;
    }

    public short getBitstreamDataZoneStart() {
        return bitstreamDataZoneStart;
    }

    public short getBitstreamDataZoneLength() {
        return (short) ((frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0)) - getBitstreamDataPointer());
    }

    public boolean isBitstreamAllValid() {
        return bitstreamAllValid;
    }

    public byte[] getBitstreamDataZoneCopy() {
        if(userDataType == UserDataType.B_PDU) {
            return Arrays.copyOfRange(frame, bitstreamDataZoneStart, bitstreamDataZoneStart + getBitstreamDataZoneLength());
        } else {
            throw new IllegalStateException("Cannot return copy of Bitstream Data Zone, Bitstream Data Zone not present, userDataType invalid");
        }
    }

    public byte[] getPacketZoneCopy() {
        if(userDataType == UserDataType.M_PDU) {
            return Arrays.copyOfRange(frame, packetZoneStart, packetZoneStart + getPacketZoneLength());
        } else {
            throw new IllegalStateException("Cannot return copy of Packet Zone, Packet Zone not present, userDataType invalid");
        }
    }

    public byte[] getInsertZoneCopy() {
        if(transferFrameInsertZoneLength > 0) {
            int startIdx = frameHeaderErrorControlPresent ? AOS_PRIMARY_HEADER_LENGTH + AOS_PRIMARY_HEADER_FHEC_LENGTH : AOS_PRIMARY_HEADER_LENGTH;
            return Arrays.copyOfRange(frame, startIdx, startIdx + transferFrameInsertZoneLength);
        } else {
            throw new IllegalStateException("Cannot return copy of Insert Zone, Insert Zone not present");
        }
    }

    @Override
    public String toString() {
        return "AosTransferFrame{" +
                "userDataType=" + userDataType +
                ", frameHeaderErrorControlPresent=" + frameHeaderErrorControlPresent +
                ", transferFrameInsertZoneLength=" + transferFrameInsertZoneLength +
                ", replayFlag=" + replayFlag +
                ", virtualChannelFrameCountUsageFlag=" + virtualChannelFrameCountUsageFlag +
                ", virtualChannelFrameCountCycle=" + virtualChannelFrameCountCycle +
                ", idleFrame=" + idleFrame +
                ", firstHeaderPointer=" + firstHeaderPointer +
                ", noStartPacket=" + noStartPacket +
                ", packetZoneStart=" + packetZoneStart +
                ", bitstreamDataPointer=" + bitstreamDataPointer +
                ", bitstreamDataZoneStart=" + bitstreamDataZoneStart +
                ", bitstreamAllValid=" + bitstreamAllValid +
                ", fecfPresent=" + fecfPresent +
                ", ocfPresent=" + ocfPresent +
                ", transferFrameVersionNumber=" + transferFrameVersionNumber +
                ", spacecraftId=" + spacecraftId +
                ", virtualChannelId=" + virtualChannelId +
                ", virtualChannelFrameCount=" + virtualChannelFrameCount +
                ", valid=" + valid +
                '}';
    }
}
