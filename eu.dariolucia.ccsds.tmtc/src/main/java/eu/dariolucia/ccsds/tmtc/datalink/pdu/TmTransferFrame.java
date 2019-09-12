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

/**
 * This class is used to decode and manipulate a TM transfer frame, compliant to CCSDS 132.0-B-2. It includes also support
 * for the security protocol, as defined by the same standard.
 */
public class TmTransferFrame extends AbstractTransferFrame {

    /**
     * Length of the primary header
     */
    public static final int TM_PRIMARY_HEADER_LENGTH = 6;
    /**
     * Value of the first header pointer when no start of packet is present inside the frame
     */
    public static final short TM_FIRST_HEADER_POINTER_NO_PACKET = 2047;
    /**
     * Value of the first header pointer in case of idle frame
     */
    public static final short TM_FIRST_HEADER_POINTER_IDLE = 2046;

    /**
     * Decoding function for TM frames, which can be used when building {@link eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder} objects.
     * Security protocol information is considered missing.
     *
     * @param fecfPresent true if the FECF is present, false otherwise
     * @return the TM frame decoding function
     */
    public static IDecodingFunction<TmTransferFrame> decodingFunction(boolean fecfPresent) {
        return decodingFunction(fecfPresent, 0, 0);
    }

    /**
     * Decoding function for TM frames, which can be used when building {@link eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder} objects.
     *
     * @param fecfPresent true if the FECF is present, false otherwise
     * @param securityHeaderLength size of the security header length in bytes, 0 if not present
     * @param securityTrailerLength size of the security trailer length in bytes, 0 if not present
     * @return the TM frame decoding function
     */
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

    /**
     * Constructor of a TM transfer frame, assuming no security protocol used.
     *
     * @param frame the frame data
     * @param fecfPresent true if the FECF is present, false otherwise
     */
    public TmTransferFrame(byte[] frame, boolean fecfPresent) {
        this(frame, fecfPresent, 0, 0);
    }

    /**
     * Constructor of a TM transfer frame.
     *
     * @param frame the frame data
     * @param fecfPresent true if the FECF is present, false otherwise
     * @param securityHeaderLength size of the security header length in bytes, 0 if not present
     * @param securityTrailerLength size of the security trailer length in bytes, 0 if not present
     */
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

    /**
     * This method returns the value of the master channel frame count.
     *
     * @return the value of the master channel frame count field
     */
    public int getMasterChannelFrameCount() {
        return masterChannelFrameCount;
    }

    /**
     * This method returns whether the secondary header flag is set.
     *
     * @return true if the flag is set, false otherwise
     */
    public boolean isSecondaryHeaderPresent() {
        return secondaryHeaderPresent;
    }

    /**
     * This method returns whether the sync flag is set.
     *
     * @return true if the flag is set, false otherwise
     */
    public boolean isSynchronisationFlag() {
        return synchronisationFlag;
    }

    /**
     * This method returns whether the packet order flag is set.
     *
     * @return true if the flag is set, false otherwise
     */
    public boolean isPacketOrderFlag() {
        return packetOrderFlag;
    }

    /**
     * This method returns the value of the segment length identifier.
     *
     * @return the value of the segment length identifier field
     */
    public byte getSegmentLengthIdentifier() {
        return segmentLengthIdentifier;
    }

    /**
     * This method returns the value of the first header pointer.
     *
     * @return the value of the first header pointer field
     */
    public short getFirstHeaderPointer() {
        return firstHeaderPointer;
    }

    /**
     * This method returns whether the frame contains no start of a space packet.
     *
     * @return true if the frame does not contain the start of a packet, false otherwise
     */
    public boolean isNoStartPacket() {
        return noStartPacket;
    }

    @Override
    public boolean isIdleFrame() {
        return idleFrame;
    }

    /**
     * This method returns the value of the secondary header version number. The value is meaningful only if the
     * secondary header is marked as present.
     *
     * @return the value of the secondary header version number field
     */
    public byte getSecondaryHeaderVersionNumber() {
        return secondaryHeaderVersionNumber;
    }

    /**
     * This method returns the value of the secondary header length. The value is meaningful only if the
     * secondary header is marked as present.
     *
     * @return the value of the secondary header length
     */
    public byte getSecondaryHeaderLength() {
        return secondaryHeaderLength;
    }

    /**
     * This method returns a copy of the secondary header. If the secondary header is not present, an {@link IllegalStateException}
     * is thrown.
     *
     * @return the copy of the secondary header
     * @throws IllegalStateException if the secondary header is not present
     */
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
