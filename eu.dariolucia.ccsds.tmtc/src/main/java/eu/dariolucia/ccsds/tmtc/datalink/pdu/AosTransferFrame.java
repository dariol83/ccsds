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

/**
 * This class is used to decode and manipulate an AOS transfer frame, compliant to CCSDS 732.0-B-3. It includes also support
 * for the security protocol, as defined by the same standard.
 */
public class AosTransferFrame extends AbstractTransferFrame {

    public static final int AOS_PRIMARY_HEADER_LENGTH = 6;
    public static final int AOS_PRIMARY_HEADER_FHEC_LENGTH = 2;
    public static final short AOS_M_PDU_FIRST_HEADER_POINTER_IDLE = 2046;
    public static final short AOS_M_PDU_FIRST_HEADER_POINTER_NO_PACKET = 2047;
    public static final short AOS_B_PDU_FIRST_HEADER_POINTER_IDLE = 16382;
    public static final short AOS_B_PDU_FIRST_HEADER_POINTER_ALL_DATA = 16383;

    // AOS Blue Book, 4.1.2.6.5.
    public static final ReedSolomonAlgorithm AOS_FRAME_HEADER_ERROR_CONTROL_RS_UTIL = new ReedSolomonAlgorithm(
                    6,
                    10,
                    0x13,      // As per AOS Blue Book specs: x^4 + x + 1 = 10011 = 19 = 0x13
                    2,
                    6,
                    false
    );

    /**
     * Decoding function for AOS frames, which can be used when building {@link eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder} objects.
     * Security protocol information is considered missing.
     *
     * @param frameHeaderErrorControlPresent true if the FHEC is present, false otherwise
     * @param transferFrameInsertZoneLength size of the insert zone field, 0 if not present
     * @param userDataType user data type, depending on the channel access service: M_PDU, B_PDU, VCA or IDLE for VC 63 frames
     * @param ocfPresent true if the OCF is present, false otherwise
     * @param fecfPresent true if the FECF is present, false otherwise
     * @return the AOS frame decoding function
     */
    public static IDecodingFunction<AosTransferFrame> decodingFunction(boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, UserDataType userDataType, boolean ocfPresent, boolean fecfPresent) {
        return decodingFunction(frameHeaderErrorControlPresent, transferFrameInsertZoneLength, userDataType, ocfPresent, fecfPresent, 0, 0);
    }

    /**
     * Decoding function for AOS frames, which can be used when building {@link eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder} objects.
     *
     * @param frameHeaderErrorControlPresent true if the FHEC is present, false otherwise
     * @param transferFrameInsertZoneLength size of the insert zone field in bytes, 0 if not present
     * @param userDataType user data type, depending on the channel access service: M_PDU, B_PDU, VCA or IDLE for VC 63 frames
     * @param ocfPresent true if the OCF is present, false otherwise
     * @param fecfPresent true if the FECF is present, false otherwise
     * @param securityHeaderLength size of the security header length in bytes, 0 if not present
     * @param securityTrailerLength size of the security trailer length in bytes, 0 if not present
     * @return the AOS frame decoding function
     */
    public static IDecodingFunction<AosTransferFrame> decodingFunction(boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, UserDataType userDataType, boolean ocfPresent, boolean fecfPresent, int securityHeaderLength, int securityTrailerLength) {
        return input -> new AosTransferFrame(input, frameHeaderErrorControlPresent, transferFrameInsertZoneLength, userDataType, ocfPresent, fecfPresent, securityHeaderLength, securityTrailerLength);
    }

    /**
     * Type of virtual channel access service
     */
    public enum UserDataType {
        /**
         * The frame contains space packets
         */
        M_PDU,
        /**
         * The frame contains bitstream data
         */
        B_PDU,
        /**
         * The frame contains user data of unknown format/specification
         */
        VCA,
        /**
         * The frame contains idle data
         */
        IDLE
    }

    private final UserDataType userDataType;

    private final boolean frameHeaderErrorControlPresent; // if present, 2 octets
    private final boolean validHeader;
    private final int transferFrameInsertZoneLength;

    private final boolean replayFlag;
    private final boolean virtualChannelFrameCountUsageFlag;
    private final byte virtualChannelFrameCountCycle;

    private boolean idleFrame;

    // Only valid for M_PDU userDataType
    private short firstHeaderPointer;
    private boolean noStartPacket;
    private short packetZoneStart;

    // Only valid for B_PDU userDataType
    private short bitstreamDataPointer;
    private short bitstreamDataZoneStart;
    private boolean bitstreamAllValid;

    // Security header/trailer as per CCSDS 355.0-B-1
    private final int securityHeaderLength;
    private final int securityTrailerLength;

    /**
     * Constructor of a AOS transfer frame, assuming no security protocol used.
     *
     * @param frame the frame data
     * @param frameHeaderErrorControlPresent true if the FHEC is present, false otherwise
     * @param transferFrameInsertZoneLength size of the insert zone field in bytes, 0 if not present
     * @param userDataType user data type, depending on the channel access service: M_PDU, B_PDU, VCA or IDLE for VC 63 frames
     * @param ocfPresent true if the OCF is present, false otherwise
     * @param fecfPresent true if the FECF is present, false otherwise
     */
    public AosTransferFrame(byte[] frame, boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, UserDataType userDataType, boolean ocfPresent, boolean fecfPresent) {
        this(frame, frameHeaderErrorControlPresent, transferFrameInsertZoneLength, userDataType, ocfPresent, fecfPresent, 0, 0);
    }

    /**
     * Constructor of a AOS transfer frame.
     *
     * @param frame the frame data
     * @param frameHeaderErrorControlPresent true if the FHEC is present, false otherwise
     * @param transferFrameInsertZoneLength size of the insert zone field in bytes, 0 if not present
     * @param userDataType user data type, depending on the channel access service: M_PDU, B_PDU, VCA or IDLE for VC 63 frames
     * @param ocfPresent true if the OCF is present, false otherwise
     * @param fecfPresent true if the FECF is present, false otherwise
     * @param securityHeaderLength size of the security header length in bytes, 0 if not present
     * @param securityTrailerLength size of the security trailer length in bytes, 0 if not present
     */
    public AosTransferFrame(byte[] frame, boolean frameHeaderErrorControlPresent, int transferFrameInsertZoneLength, UserDataType userDataType, boolean ocfPresent, boolean fecfPresent, int securityHeaderLength, int securityTrailerLength) {
        super(frame, fecfPresent);

        // Frame header error control field is only assumed as an additional 2 bytes: no R-S error correction on the protected header fields is performed, only error control
        this.ocfPresent = ocfPresent;
        this.frameHeaderErrorControlPresent = frameHeaderErrorControlPresent;
        this.transferFrameInsertZoneLength = transferFrameInsertZoneLength;
        this.userDataType = userDataType;

        this.securityHeaderLength = securityHeaderLength;
        this.securityTrailerLength = securityTrailerLength;

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
        // Use of security (if present)
        dataFieldStart += securityHeaderLength;

        // Compute the length of the data field
        dataFieldLength = frame.length - dataFieldStart - securityTrailerLength - (ocfPresent ? 4 : 0) - (fecfPresent ? 2 : 0);

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
                packetZoneStart = (short) (dataFieldStart + 2);
                noStartPacket = firstHeaderPointer == AOS_M_PDU_FIRST_HEADER_POINTER_NO_PACKET;
                idleFrame = firstHeaderPointer == AOS_M_PDU_FIRST_HEADER_POINTER_IDLE;
                break;
            case B_PDU:
                twoOctets = in.getShort();
                bitstreamDataPointer = (short) ((twoOctets & (short) 0x3FFF));
                bitstreamDataZoneStart = (short) (dataFieldStart + 2);
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

    /**
     * This method returns the value as short of the FHEC field.
     *
     * @return the value of the FHEC as short
     * @throws IllegalStateException if there is no FHEC defined on the AOS frame
     */
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
        int[] octetsIdx = new int[] { 0, 1, 5, 6, 7 };
        for(int i = 0; i < octetsIdx.length; ++i) {
            byte b = aosFrame[octetsIdx[i]];
            codeword[i*2] = (byte) ((b & 0xF0) >>> 4);
            codeword[i*2 + 1] = (byte) (b & 0x0F);
        }
        // Check the codeword
        return AOS_FRAME_HEADER_ERROR_CONTROL_RS_UTIL.decodeCodeword(codeword, true) != null;
    }

    /**
     * This method returns whether the FHEC field is present.
     *
     * @return true if the FHEC is present, false otherwise.
     */
    public boolean isFrameHeaderErrorControlPresent() {
        return frameHeaderErrorControlPresent;
    }

    /**
     * If the FHEC field is present, this method returns whether the header fields protected by the FHEC present no modifications,
     * i.e. the header fields are correct. If the FHEC is not present, this method returns always true.
     *
     * @return the validity status of the header field according to the evaluation of the FHEC is present. If not present, this method returns true.
     */
    public boolean isValidHeader() {
        return validHeader;
    }

    /**
     * This method returns the length in bytes of the Transfer Frame Insert Zone field.
     *
     * @return the length of the Transfer Frame Insert Zone field
     */
    public int getInsertZoneLength() {
        return transferFrameInsertZoneLength;
    }

    /**
     * This metod returns the value of the replay flag.
     *
     * @return true if the replay flag is set (1), false otherwise (0)
     */
    public boolean isReplayFlag() {
        return replayFlag;
    }

    /**
     * This method returns the value of the VC frame count usage flag.
     *
     * @return true if the flag is set (1), false otherwise (0)
     */
    public boolean isVirtualChannelFrameCountUsageFlag() {
        return virtualChannelFrameCountUsageFlag;
    }

    /**
     * This method returns the value of the VC frame count cycle.
     *
     * @return the value of the VC frame count cycle
     */
    public byte getVirtualChannelFrameCountCycle() {
        return virtualChannelFrameCountCycle;
    }

    /**
     * This method returns whether this frame is an idle frame.
     *
     * @return true if the frame is an idle frame, false otherwise
     */
    @Override
    public boolean isIdleFrame() {
        return idleFrame;
    }

    /**
     * This method returns the index of the first byte of the packet zone for M_PDU frame types. The index takes into
     * account possible presence of the FHEC, insert zone and security header. In other words, this method returns the
     * byte index immediately following the first header pointer.
     *
     * @return the index of the first byte of the packet zone
     */
    public short getPacketZoneStart() {
        return packetZoneStart;
    }

    /**
     * This method returns the length of the packet zone for M_PDU frame types, taking into account the possible presence of OCF, FECF and
     * security trailer.
     *
     * @return the length of the packet zone
     */
    public short getPacketZoneLength() {
        return (short) ((frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0)) - securityTrailerLength - getPacketZoneStart());
    }

    /**
     * This method returns the user data type handled by this frame.
     *
     * @return the user data type
     */
    public UserDataType getUserDataType() {
        return userDataType;
    }

    /**
     * This method returns the first header pointer value, in case of M_PDU type.
     *
     * @return the first header pointer value
     */
    public short getFirstHeaderPointer() {
        return firstHeaderPointer;
    }

    /**
     * This method returns whether the frame contains no start of a packet.
     *
     * @return true if the frame contains no start of a packet
     */
    public boolean isNoStartPacket() {
        return noStartPacket;
    }

    /**
     * This method returns the value of the bitstream pointer, in case of B_PDU type.
     *
     * @return the bitstream data pointer
     */
    public short getBitstreamDataPointer() {
        return bitstreamDataPointer;
    }

    /**
     * This method returns the index of the first byte of the bitstream zone for B_PDU frame types. The index takes into
     * account possible presence of the FHEC, insert zone and security header.
     *
     * @return the index of the first byte of the bitstream zone
     */
    public short getBitstreamDataZoneStart() {
        return bitstreamDataZoneStart;
    }

    /**
     * This method returns the length of the bitstream zone for B_PDU frame types, taking into account the possible presence of OCF, FECF and
     * security trailer.
     *
     * @return the length of the bitstream zone
     */
    public short getBitstreamDataZoneLength() {
        return (short) ((frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0)) - securityTrailerLength - getBitstreamDataPointer());
    }

    public boolean isBitstreamAllValid() {
        return bitstreamAllValid;
    }

    /**
     * This method returns a copy of the complete bitstream data zone.
     *
     * @return a copy of the bitstream data zone
     * @throws IllegalStateException if the frame has no B_PDU type
     */
    public byte[] getBitstreamDataZoneCopy() {
        if(userDataType == UserDataType.B_PDU) {
            return Arrays.copyOfRange(frame, bitstreamDataZoneStart, bitstreamDataZoneStart + getBitstreamDataZoneLength());
        } else {
            throw new IllegalStateException("Cannot return copy of Bitstream Data Zone, Bitstream Data Zone not present, userDataType invalid");
        }
    }

    /**
     * This method returns a copy of the complete packet zone.
     *
     * @return a copy of the packet zone
     * @throws IllegalStateException if the frame has no M_PDU type
     */
    public byte[] getPacketZoneCopy() {
        if(userDataType == UserDataType.M_PDU) {
            return Arrays.copyOfRange(frame, packetZoneStart, packetZoneStart + getPacketZoneLength());
        } else {
            throw new IllegalStateException("Cannot return copy of Packet Zone, Packet Zone not present, userDataType invalid");
        }
    }

    /**
     * This method returns a copy of the complete insert zone.
     *
     * @return a copy of the insert zone
     * @throws IllegalStateException if the frame has no insert zone
     */
    public byte[] getInsertZoneCopy() {
        if(transferFrameInsertZoneLength > 0) {
            int startIdx = frameHeaderErrorControlPresent ? AOS_PRIMARY_HEADER_LENGTH + AOS_PRIMARY_HEADER_FHEC_LENGTH : AOS_PRIMARY_HEADER_LENGTH;
            return Arrays.copyOfRange(frame, startIdx, startIdx + transferFrameInsertZoneLength);
        } else {
            throw new IllegalStateException("Cannot return copy of Insert Zone, Insert Zone not present");
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
     * @return a copy of the security header field, can have 0 length if not present
     */
    public byte[] getSecurityHeaderCopy() {
        return Arrays.copyOfRange(frame, AOS_PRIMARY_HEADER_LENGTH + (frameHeaderErrorControlPresent ? 2 : 0) + transferFrameInsertZoneLength, AOS_PRIMARY_HEADER_LENGTH + (frameHeaderErrorControlPresent ? 2 : 0) + transferFrameInsertZoneLength + securityHeaderLength);
    }

    /**
     * This method returns a copy of the security trailer field.
     *
     * @return a copy of the security trailer field, can have 0 length if not present
     */
    public byte[] getSecurityTrailerCopy() {
        return Arrays.copyOfRange(frame, frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0) - securityTrailerLength, frame.length - (fecfPresent ? 2 : 0) - (ocfPresent ? 4 : 0));
    }

    @Override
    public String toString() {
        return "AosTransferFrame{" +
                "userDataType=" + userDataType +
                ", frameHeaderErrorControlPresent=" + frameHeaderErrorControlPresent +
                ", validHeader=" + validHeader +
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
