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

public class TmTransferFrame extends AbstractTransferFrame {

    public static final int TM_PRIMARY_HEADER_LENGTH = 6;
    public static final short TM_FIRST_HEADER_POINTER_NO_PACKET = 2047;
    public static final short TM_FIRST_HEADER_POINTER_IDLE = 2046;

    public static IDecodingFunction<TmTransferFrame> decodingFunction(boolean fecfPresent) {
        return decodingFunction(fecfPresent, 0, 0);
    }

    public static IDecodingFunction<TmTransferFrame> decodingFunction(boolean fecfPresent, int securityHeaderLength, int securityTrailerLength) {
        return input -> new TmTransferFrame(input, fecfPresent, securityHeaderLength, securityTrailerLength);
    }

    private final int masterChannelFrameCount;

    private final boolean secondaryHeaderPresent;
    private final boolean synchronisationFlag;
    private final boolean packetOrderFlag;
    private final byte segmentLengthIdentifier;
    private final short firstHeaderPointer;
    private final boolean noStartPacket;
    private final boolean idleFrame;

    // Secondary header fields: valid values only if secondaryHeaderPresent == true
    private final byte secondaryHeaderVersionNumber;
    private final byte secondaryHeaderLength;

    // Security header/trailer as per CCSDS 355.0-B-1
    private final int securityHeaderLength;
    private final int securityTrailerLength;

    public TmTransferFrame(byte[] frame, boolean fecfPresent) {
        this(frame, fecfPresent, 0, 0);
    }

    public TmTransferFrame(byte[] frame, boolean fecfPresent, int securityHeaderLength, int securityTrailerLength) {
        super(frame, fecfPresent);

        this.securityHeaderLength = securityHeaderLength;
        this.securityTrailerLength = securityTrailerLength;

        ByteBuffer in = ByteBuffer.wrap(frame);
        short twoOctets = in.getShort();

        short tfvn = (short) ((twoOctets & (short) 0xC000) >> 14);

        // 4.1.2.2.2.2
        if (tfvn != 0) {
            throw new IllegalArgumentException("Transfer Frame Version Number: expected 0, actual " + tfvn);
        }

        transferFrameVersionNumber = tfvn;
        spacecraftId = (short) ((twoOctets & (short) 0x3FF0) >> 4);
        virtualChannelId = (short) ((twoOctets & (short) 0x000E) >> 1);
        ocfPresent = (twoOctets & (short) 0x0001) != 0;

        masterChannelFrameCount = (short) Byte.toUnsignedInt(in.get());
        virtualChannelFrameCount = (short) Byte.toUnsignedInt(in.get());

        twoOctets = in.getShort();

        secondaryHeaderPresent = twoOctets < 0; // if first bit is 1, then the short is negative
        synchronisationFlag = (short) ((twoOctets & (short) 0x4000)) != 0;
        packetOrderFlag = (short) ((twoOctets & (short) 0x2000)) != 0;

        if (!synchronisationFlag && packetOrderFlag) {
            throw new IllegalArgumentException("Synchronisation Flag not set, Packet Order Flag: expected false, actual " + packetOrderFlag);
        }

        segmentLengthIdentifier = (byte) ((twoOctets & (short) 0x1800) >> 11);

        // 4.1.2.7.5.2
        if (!synchronisationFlag && segmentLengthIdentifier != 3) {
            throw new IllegalArgumentException("Synchronisation Flag not set, Segment Length Identifier: expected 3, actual " + segmentLengthIdentifier);
        }

        firstHeaderPointer = (short) ((twoOctets & (short) 0x07FF));

        noStartPacket = firstHeaderPointer == TM_FIRST_HEADER_POINTER_NO_PACKET;
        idleFrame = firstHeaderPointer == TM_FIRST_HEADER_POINTER_IDLE;

        // Secondary header processing
        dataFieldStart = TM_PRIMARY_HEADER_LENGTH;
        if(secondaryHeaderPresent) {
            byte tfshid = in.get();
            secondaryHeaderVersionNumber = (byte) ((tfshid & (byte) 0xC0) >> 6);
            secondaryHeaderLength = (byte) (tfshid & (byte) 0x3F);

            dataFieldStart += 1 + secondaryHeaderLength;
        } else {
            secondaryHeaderVersionNumber = 0;
            secondaryHeaderLength = 0;
        }

        // Use of security (if present)
        dataFieldStart += securityHeaderLength;
        // Compute the length of the data field
        dataFieldLength = frame.length - dataFieldStart - securityTrailerLength - (ocfPresent ? 4 : 0) - (fecfPresent ? 2 : 0);

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
    }

    private boolean checkValidity() {
        // As this method is called by the check() method, the fecfPresent check is already done
        short crc16 = Crc16Algorithm.getCrc16(this.frame, 0,  this.frame.length - 2);
        short crcFromFrame = getFecf();
        return crc16 == crcFromFrame;
    }

    public int getMasterChannelFrameCount() {
        return masterChannelFrameCount;
    }

    public boolean isSecondaryHeaderPresent() {
        return secondaryHeaderPresent;
    }

    public boolean isSynchronisationFlag() {
        return synchronisationFlag;
    }

    public boolean isPacketOrderFlag() {
        return packetOrderFlag;
    }

    public byte getSegmentLengthIdentifier() {
        return segmentLengthIdentifier;
    }

    public short getFirstHeaderPointer() {
        return firstHeaderPointer;
    }

    public boolean isNoStartPacket() {
        return noStartPacket;
    }

    @Override
    public boolean isIdleFrame() {
        return idleFrame;
    }

    public byte getSecondaryHeaderVersionNumber() {
        return secondaryHeaderVersionNumber;
    }

    public byte getSecondaryHeaderLength() {
        return secondaryHeaderLength;
    }

    public byte[] getSecondaryHeaderCopy() {
        if(secondaryHeaderPresent) {
            return Arrays.copyOfRange(frame, TM_PRIMARY_HEADER_LENGTH + 1, TM_PRIMARY_HEADER_LENGTH + 1 + secondaryHeaderLength);
        } else {
            throw new IllegalStateException("Cannot return copy of Secondary Header, Secondary Header not present");
        }
    }

    /**
     * This method returns whether security information (header, trailer or both) have been used.
     *
     * @return true if security blocks are part of the TC frame
     */
    public boolean isSecurityUsed() {
        return this.securityHeaderLength != 0 || this.securityTrailerLength != 0;
    }

    /**
     * This method returns the length of the security header field in bytes.
     *
     * @return the length of the security header field in bytes
     */
    public int getSecurityHeaderLength() {
        return securityHeaderLength;
    }

    /**
     * This method returns the length of the security trailer field in bytes.
     *
     * @return the length of the security trailer field in bytes
     */
    public int getSecurityTrailerLength() {
        return securityTrailerLength;
    }

    /**
     * This method returns a copy of the security header field.
     *
     * @return a copy of the security header field
     */
    public byte[] getSecurityHeaderCopy() {
        return Arrays.copyOfRange(frame, TM_PRIMARY_HEADER_LENGTH + secondaryHeaderLength, TM_PRIMARY_HEADER_LENGTH + secondaryHeaderLength + securityHeaderLength);
    }

    /**
     * This method returns a copy of the security trailer field.
     *
     * @return a copy of the security trailer field
     */
    public byte[] getSecurityTrailerCopy() {
        return Arrays.copyOfRange(frame, frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0) - securityTrailerLength, frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0));
    }

    @Override
    public String toString() {
        return "TmTransferFrame{" +
                "masterChannelFrameCount=" + masterChannelFrameCount +
                ", secondaryHeaderPresent=" + secondaryHeaderPresent +
                ", synchronisationFlag=" + synchronisationFlag +
                ", packetOrderFlag=" + packetOrderFlag +
                ", segmentLengthIdentifier=" + segmentLengthIdentifier +
                ", firstHeaderPointer=" + firstHeaderPointer +
                ", noStartPacket=" + noStartPacket +
                ", idleFrame=" + idleFrame +
                ", secondaryHeaderVersionNumber=" + secondaryHeaderVersionNumber +
                ", secondaryHeaderLength=" + secondaryHeaderLength +
                ", securityHeaderLength=" + securityHeaderLength +
                ", securityTrailerLength=" + securityTrailerLength +
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
