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
import eu.dariolucia.ccsds.tmtc.coding.IDecodingFunction;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class TcTransferFrame extends AbstractTransferFrame {

    public static final int TC_PRIMARY_HEADER_LENGTH = 5;
    public static final int MAX_TC_FRAME_LENGTH = 1024;

    public static IDecodingFunction<TcTransferFrame> decodingFunction(boolean segmented, boolean fecfPresent) {
        return input -> new TcTransferFrame(input, segmented, fecfPresent);
    }

    public static IDecodingFunction<TcTransferFrame> decodingFunction(boolean segmented, boolean fecfPresent, int secHeaderLength, int secTrailerLength) {
        return input -> new TcTransferFrame(input, segmented, fecfPresent, secHeaderLength, secTrailerLength);
    }

    public enum FrameType {
        AD,
        RESERVED,
        BD,
        BC
    }

    public enum ControlCommandType {
        UNLOCK,
        SET_VR,
        RESERVED
    }

    public enum SequenceFlagType {
        FIRST,
        CONTINUE,
        LAST,
        NO_SEGMENT
    }

    private boolean bypassFlag;
    private boolean controlCommandFlag;
    private short frameLength;

    private boolean segmented;
    private byte mapId;
    private SequenceFlagType sequenceFlag;

    // The next attribute is valid only if controlCommandFlag == true && bypassFlag == true
    private ControlCommandType controlCommandType;
    // The next attribute is valid only if frameType == BC and Data Unit is 3 bytes that
    // conform to 4.1.3.3.3
    private short setVrValue;

    // Security header/trailer as per CCSDS 355.0-B-1
    private int securityHeaderLength;
    private int securityTrailerLength;

    public TcTransferFrame(byte[] frame, boolean segmented, boolean fecfPresent, int securityHeaderLength, int securityTrailerLength) {
        super(frame, fecfPresent);
        this.segmented = segmented;
        this.securityHeaderLength = securityHeaderLength;
        this.securityTrailerLength = securityTrailerLength;
        decode();
    }

    public TcTransferFrame(byte[] frame, boolean segmented, boolean fecfPresent) {
        this(frame, segmented, fecfPresent, 0, 0);
    }

    @Override
    protected void decode() {
        ByteBuffer in = ByteBuffer.wrap(frame);
        // First 2 octets
        short twoOctets = in.getShort();

        short tfvn = (short) ((twoOctets & (short) 0xC000) >> 14);
        // 4.1.2.2.2
        if (tfvn != 0) {
            throw new IllegalArgumentException("Transfer Frame Version Number: expected 0, actual " + tfvn);
        }

        transferFrameVersionNumber = tfvn;
        bypassFlag = (twoOctets & (short) 0x2000) != 0;
        controlCommandFlag = (twoOctets & (short) 0x1000) != 0;
        spacecraftId = (short) (twoOctets & (short) 0x03FF);

        // Next 2 octets
        twoOctets = in.getShort();

        virtualChannelId = (short) ((twoOctets & (short) 0xFC00) >> 10);
        // 4.1.2.7.2
        frameLength = (short) ((twoOctets & (short) 0x03FF) + 1);

        // 4.1.2.7.3
        if(frameLength != frame.length) {
            throw new IllegalArgumentException("Wrong Frame Length: expected " + frame.length + ", actual " + frameLength);
        }

        // Last octet
        virtualChannelFrameCount = (short) Byte.toUnsignedInt(in.get());

        dataFieldStart = TC_PRIMARY_HEADER_LENGTH;

        // 4.1.3.3
        if(getFrameType() == FrameType.BC) {
            int dataFieldLength = frame.length - dataFieldStart - (fecfPresent ? 2 : 0) - securityHeaderLength - securityTrailerLength;
            if(dataFieldLength == 1) {
                controlCommandType = frame[dataFieldStart + securityHeaderLength] == 0x00 ? ControlCommandType.UNLOCK : ControlCommandType.RESERVED;
            } else if (dataFieldLength == 3) {
                if(frame[dataFieldStart] == (byte) 0x82 && frame[dataFieldStart + securityHeaderLength + 1] == 0x00) {
                    controlCommandType = ControlCommandType.SET_VR;
                    setVrValue = (short) Byte.toUnsignedInt(frame[dataFieldStart + securityHeaderLength + 2]);
                } else {
                    controlCommandType = ControlCommandType.RESERVED;
                }
            } else {
                controlCommandType = ControlCommandType.RESERVED;
            }
        }

        if(this.segmented && getFrameType() != FrameType.BC) {
            byte segHeader = frame[dataFieldStart];
            this.mapId = (byte) (segHeader & 0x3F);
            this.sequenceFlag = SequenceFlagType.values()[((segHeader & 0xC0) >> 6)];
        }

        // FECF
        if(fecfPresent) {
            valid = checkValidity();
        } else {
            // With no FECF it is assumed that the frame is valid
            valid = true;
        }
    }

    private boolean checkValidity() {
        // As this method is called by the decode() method, the fecfPresent check is already done
        short crc16 = Crc16Algorithm.getCrc16(this.frame, 0,  this.frame.length - 2);
        short crcFromFrame = getFecf();
        return crc16 == crcFromFrame;
    }

    public FrameType getFrameType() {
        if(bypassFlag) {
            return controlCommandFlag ? FrameType.BC : FrameType.BD;
        } else {
            return controlCommandFlag ? FrameType.RESERVED : FrameType.AD;
        }
    }

    @Override
    public boolean isIdleFrame() {
        return false;
    }

    public boolean isBypassFlag() {
        return bypassFlag;
    }

    public boolean isControlCommandFlag() {
        return controlCommandFlag;
    }

    public short getFrameLength() {
        return frameLength;
    }

    public ControlCommandType getControlCommandType() {
        return controlCommandType;
    }

    public short getSetVrValue() {
        return setVrValue;
    }

    public byte getMapId() {
        return mapId;
    }

    public SequenceFlagType getSequenceFlag() {
        return sequenceFlag;
    }

    public boolean isSegmented() {
        return segmented;
    }

    public boolean isSecurityUsed() {
        return this.securityHeaderLength != 0 || this.securityTrailerLength != 0;
    }

    public int getSecurityHeaderLength() {
        return securityHeaderLength;
    }

    public int getSecurityTrailerLength() {
        return securityTrailerLength;
    }

    public byte[] getSecurityHeaderCopy() {
        return Arrays.copyOfRange(frame, TC_PRIMARY_HEADER_LENGTH + (segmented ? 1 : 0), TC_PRIMARY_HEADER_LENGTH + (segmented ? 1 : 0) + securityHeaderLength);
    }

    public byte[] getSecurityTrailerCopy() {
        return Arrays.copyOfRange(frame, frame.length - (fecfPresent ? 2 : 0) - securityTrailerLength, frame.length - (fecfPresent ? 2 : 0));
    }

    @Override
    public String toString() {
        return "TcTransferFrame{" +
                "bypassFlag=" + bypassFlag +
                ", controlCommandFlag=" + controlCommandFlag +
                ", frameLength=" + frameLength +
                ", controlCommandType=" + controlCommandType +
                ", setVrValue=" + setVrValue +
                ", fecfPresent=" + fecfPresent +
                ", ocfPresent=" + ocfPresent +
                ", transferFrameVersionNumber=" + transferFrameVersionNumber +
                ", spacecraftId=" + spacecraftId +
                ", virtualChannelId=" + virtualChannelId +
                ", virtualChannelFrameCount=" + virtualChannelFrameCount +
                ", securityHeaderLength=" + securityHeaderLength +
                ", securityTrailerLength=" + securityTrailerLength +
                ", valid=" + valid +
                '}';
    }
}
