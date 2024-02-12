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
import eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder;
import eu.dariolucia.ccsds.tmtc.coding.IDecodingFunction;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.IntFunction;

/**
 * This class is used to decode and manipulate a TC transfer frame, compliant to CCSDS 232.0-B-3. It includes also support
 * for the security protocol, as defined by the same standard.
 */
public class TcTransferFrame extends AbstractTransferFrame {

    /**
     * Length of the TC Frame header
     */
    public static final int TC_PRIMARY_HEADER_LENGTH = 5;
    /**
     * Maximum length of the TC Frame according to CCSDS
     */
    public static final int MAX_TC_FRAME_LENGTH = 1024;

    /**
     * This method returns a decoding function (to be used in a {@link ChannelDecoder} chain,
     * which converts bytes arrays into TC frames. The function can deal with virtual fill bytes as decoded from a
     * CLTU, i.e. virtual fill bytes are removed before calling the {@link TcTransferFrame} constructor.
     * Segmentation depends on VC, so hardcoding it here is wrong: use a Function TC VC ID to boolean
     *
     * @param segmented function that returns true if the TC frame contains TC segments
     * @param fecfPresent true if the FECF is present
     * @return the decoding function
     */
    public static IDecodingFunction<TcTransferFrame> decodingFunction(IntFunction<Boolean> segmented, boolean fecfPresent) {
        return decodingFunction(segmented, fecfPresent, 0, 0);
    }

    /**
     * This method returns a decoding function (to be used in a {@link eu.dariolucia.ccsds.tmtc.coding.ChannelDecoder} chain,
     * which converts bytes arrays into TC frames and takes into account the security information. The function can deal
     * with virtual fill bytes as decoded from a CLTU, i.e. virtual fill bytes are removed before calling the
     * {@link TcTransferFrame} constructor.
     * Segmentation depends on VC, so hardcoding it here is wrong: use a Function TC VC ID to boolean
     *
     * @param segmented function that returns true if the TC frame contains TC segments
     * @param fecfPresent true if the FECF is present
     * @param secHeaderLength length of the security header
     * @param secTrailerLength length of the security trailer
     * @return the decoding function
     */
    public static IDecodingFunction<TcTransferFrame> decodingFunction(IntFunction<Boolean> segmented, boolean fecfPresent, int secHeaderLength, int secTrailerLength) {
        return input -> {
            int length = readTcFrameLength(input);
            if(length == input.length) {
                return new TcTransferFrame(input, segmented, fecfPresent, secHeaderLength, secTrailerLength);
            } else {
                return new TcTransferFrame(Arrays.copyOfRange(input, 0, length), segmented, fecfPresent, secHeaderLength, secTrailerLength);
            }
        };
    }

    /**
     * This static method decodes the length of the TC transfer frame. This method is typically used to identify the
     * correct length of the TC frame after decoding from the CLTU, to remove virtual fill bytes.
     *
     * @param data the data (assumed TC frame)
     * @return the length of the TC frame as reported from the TC frame header
     */
    public static int readTcFrameLength(byte[] data) {
        return (ByteBuffer.wrap(data).getInt() & 0x03FF) + 1;
    }

    /**
     * The type of TC frame.
     */
    public enum FrameType {
        /**
         * Sequence-controlled: bypass flag set to 0, control command flag set to 0
         */
        AD,
        /**
         * Reserved for future use: bypass flag set to 0, control command flag set to 1
         */
        RESERVED,
        /**
         * Expedited: bypass flag set to 1, control command flag set to 0
         */
        BD,
        /**
         * Control command (SetVR, Unlock): bypass flag set to 1, control command flag set to 1
         */
        BC
    }

    /**
     * The type of control command.
     */
    public enum ControlCommandType {
        /**
         * COP-1 Unlock directive
         */
        UNLOCK,
        /**
         * COP-1 SetV(R) directive
         */
        SET_VR,
        /**
         * Reserved for future use
         */
        RESERVED
    }

    /**
     * The meaning of the sequence flag in case of TC segment service.
     */
    public enum SequenceFlagType {
        /**
         * Continuation block
         */
        CONTINUE,
        /**
         * First block
         */
        FIRST,
        /**
         * Last block
         */
        LAST,
        /**
         * No segmentation
         */
        NO_SEGMENT
    }

    private final boolean bypassFlag;
    private final boolean controlCommandFlag;
    private final short frameLength;

    private final boolean segmented;
    private byte mapId;
    private SequenceFlagType sequenceFlag;

    // The next attribute is valid only if controlCommandFlag == true and bypassFlag == true
    private ControlCommandType controlCommandType;
    // The next attribute is valid only if frameType == BC and Data Unit is 3 bytes that
    // conform to 4.1.3.3.3
    private short setVrValue;

    // Security header/trailer as per CCSDS 355.0-B-1
    private final int securityHeaderLength;
    private final int securityTrailerLength;

    /**
     * Constructor of a TC frame. The decoding and initialisation of the different properties of this object happens in
     * the constructor. The decoding enforces the following two conditions:
     * <ul>
     *     <li>The Transfer Frame Version Number is set to 0</li>
     *     <li>The length of the provided byte array is equal to the length extracted by the TC frame header</li>
     * </ul>
     * If any virtual fill is present (e.g. due to CLTU encoding), it must be extracted before the constructor is called.
     * The static function readTcFrameLength(byte[]) can be used for this purpose.
     *
     * @param frame the byte array representing a TC frame.
     * @param segmented function that returns true if TC segmentation is used, it depends on the VC ID (CCSDS 232.0-B-3, 4.1.3.2.2.1.2)
     * @param fecfPresent true if FECF is present
     * @param securityHeaderLength length of the security header, 0 to disable
     * @param securityTrailerLength length of the security trailer, 0 to disable
     * @throws IllegalArgumentException if wrong TFVN or length is detected
     */
    public TcTransferFrame(byte[] frame, IntFunction<Boolean> segmented, boolean fecfPresent, int securityHeaderLength, int securityTrailerLength) {
        super(frame, fecfPresent);

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

        // At this stage, you know if the TC frame has segmentation active (only if not BC)
        this.segmented = getFrameType() != FrameType.BC && segmented.apply(virtualChannelId);

        // 4.1.2.7.2
        frameLength = (short) ((twoOctets & (short) 0x03FF) + 1);

        // 4.1.2.7.3
        if(frameLength != frame.length) {
            throw new IllegalArgumentException("Wrong Frame Length: expected " + frame.length + ", actual " + frameLength);
        }

        // Last octet
        virtualChannelFrameCount = (short) Byte.toUnsignedInt(in.get());

        // If security is present, then the Transfer Frame Data Field is located as specified by CCSDS 232.0-B-3, 6.3.1.
        if(getFrameType() == FrameType.BC) {
            dataFieldStart = (short) (TC_PRIMARY_HEADER_LENGTH);
            dataFieldLength = frameLength - dataFieldStart - (fecfPresent ? 2 : 0);
            this.securityHeaderLength = 0;
            this.securityTrailerLength = 0;
        } else if(securityHeaderLength > 0) {
            dataFieldStart = (short) (TC_PRIMARY_HEADER_LENGTH + (this.segmented ? 1 : 0) + securityHeaderLength);
            dataFieldLength = frameLength - dataFieldStart - securityTrailerLength - (fecfPresent ? 2 : 0);
            this.securityHeaderLength = securityHeaderLength;
            this.securityTrailerLength = securityTrailerLength;
        } else {
            dataFieldStart = (short) (TC_PRIMARY_HEADER_LENGTH + (this.segmented ? 1 : 0));
            dataFieldLength = frameLength - dataFieldStart - (fecfPresent ? 2 : 0);
            this.securityHeaderLength = 0;
            this.securityTrailerLength = 0;
        }

        // 4.1.3.3
        if(getFrameType() == FrameType.BC) {
            if(dataFieldLength == 1) {
                controlCommandType = frame[dataFieldStart] == 0x00 ? ControlCommandType.UNLOCK : ControlCommandType.RESERVED;
            } else if (dataFieldLength == 3) {
                if(frame[dataFieldStart] == (byte) 0x82 && frame[dataFieldStart + 1] == 0x00) {
                    controlCommandType = ControlCommandType.SET_VR;
                    setVrValue = (short) Byte.toUnsignedInt(frame[dataFieldStart + 2]);
                } else {
                    controlCommandType = ControlCommandType.RESERVED;
                }
            } else {
                controlCommandType = ControlCommandType.RESERVED;
            }
        }

        if(this.segmented && getFrameType() != FrameType.BC) {
            // If security is present, the segment header is before the security header, i.e. immediately following the TC primary header
            // as per CCSDS 232.0-B-3, 6.3.1. This is in all cases, so we do not use the dataFieldStart here.
            byte segHeader = frame[TC_PRIMARY_HEADER_LENGTH];
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

    /**
     * Constructor of a TC frame, assuming no security fields. Shortcut for
     * <code>TcTransferFrame(frame, segmented, fecfPresent, 0, 0);</code>
     *
     * @param frame the byte array representing a TC frame.
     * @param segmented true if TC segmentation is used
     * @param fecfPresent true if FECF is present
     * @throws IllegalArgumentException if wrong TFVN or length is detected
     */
    public TcTransferFrame(byte[] frame, IntFunction<Boolean> segmented, boolean fecfPresent) {
        this(frame, segmented, fecfPresent, 0, 0);
    }

    private boolean checkValidity() {
        // As this method is called by the check() method, the fecfPresent check is already done
        short crc16 = Crc16Algorithm.getCrc16(this.frame, 0,  this.frame.length - 2);
        short crcFromFrame = getFecf();
        return crc16 == crcFromFrame;
    }

    /**
     * This method returns the type of the TC frame by checking the value of the bypass flag and the control
     * command flag.
     *
     * @return the type of the TC frame, as per {@link TcTransferFrame.FrameType}
     */
    public FrameType getFrameType() {
        if(bypassFlag) {
            return controlCommandFlag ? FrameType.BC : FrameType.BD;
        } else {
            return controlCommandFlag ? FrameType.RESERVED : FrameType.AD;
        }
    }

    /**
     * This method returns whether the frame is an idle frame. TC frames are never idle, so this method always
     * returns false.
     *
     * @return false
     */
    @Override
    public boolean isIdleFrame() {
        return false;
    }

    /**
     * This method returns the value of the bypass flag.
     *
     * @return true if the bypass flag is set (1), false otherwise
     */
    public boolean isBypassFlag() {
        return bypassFlag;
    }

    /**
     * This method returns the value of the control command flag.
     *
     * @return true if the bypass flag is set (1), false otherwise
     */
    public boolean isControlCommandFlag() {
        return controlCommandFlag;
    }

    /**
     * This method returns the type of control command. The return value is valid only if controlCommandFlag == true and bypassFlag == true.
     *
     * @return the type of control command, or null if the TC frame does not contain a control command
     */
    public ControlCommandType getControlCommandType() {
        return controlCommandType;
    }

    /**
     * This method returns the SetV(R) value. The return value is meaningful only if the control command is of type
     * SET_VR.
     *
     * @return the SetV(R) value
     */
    public short getSetVrValue() {
        return setVrValue;
    }

    /**
     * This method returns the value of the MAP ID. The return value is meaningful only if the TC frame contains
     * TC segments (isSegmented returns true).
     *
     * @return the value of the MAP ID
     */
    public byte getMapId() {
        return mapId;
    }

    /**
     * This method returns the value of the sequence flag. The return value is meaningful only if the TC frame contains
     * TC segments (isSegmented returns true).
     *
     * @return the value of the sequence flag
     */
    public SequenceFlagType getSequenceFlag() {
        return sequenceFlag;
    }

    /**
     * This method reports whether the TC frame contains TC segments or not.
     *
     * @return true if the TC frame contains TC segments, false otherwise
     */
    public boolean isSegmented() {
        return segmented;
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
        return Arrays.copyOfRange(frame, TC_PRIMARY_HEADER_LENGTH + (segmented ? 1 : 0), TC_PRIMARY_HEADER_LENGTH + (segmented ? 1 : 0) + securityHeaderLength);
    }

    /**
     * This method returns a copy of the security trailer field.
     *
     * @return a copy of the security trailer field
     */
    public byte[] getSecurityTrailerCopy() {
        return Arrays.copyOfRange(frame, frame.length - (fecfPresent ? 2 : 0) - securityTrailerLength, frame.length - (fecfPresent ? 2 : 0));
    }

    @Override
    public String toString() {
        return "TcTransferFrame{" +
                "bypassFlag=" + bypassFlag +
                ", controlCommandFlag=" + controlCommandFlag +
                ", frameLength=" + frameLength +
                ", segmented=" + segmented +
                ", mapId=" + mapId +
                ", sequenceFlag=" + sequenceFlag +
                ", controlCommandType=" + controlCommandType +
                ", setVrValue=" + setVrValue +
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
