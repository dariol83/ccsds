/*
 *   Copyright (c) 2022 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.transport.pdu;

import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class is used to decode and manipulate an encapsulation packet, compliant to CCSDS 133.1-B-3.
 */
public class EncapsulationPacket extends AnnotatedObject implements IPacket {

    public static final int VERSION = 7;

    /**
     * The possible values for the Encapsulation Protocol ID field. The ordinal is equal to the value of the field as
     * prescribed by the standard.
     */
    public enum ProtocolIdType {
        /**
         * Encapsulation Idle Packet (the encapsulation data field, if present, contains no protocol data but only idle data)
         */
        PROTOCOL_ID_IDLE,
        /**
         * LTP over CCSDS Encapsulation Packets
         */
        PROTOCOL_ID_LTP,
        /**
         * Internet Protocol Extension (IPE)
         */
        PROTOCOL_ID_IPE,
        /**
         * CFDP
         */
        PROTOCOL_ID_CFDP,
        /**
         * Bundle Protocol (BP)
         */
        PROTOCOL_ID_BP,
        /**
         * No Entry
         */
        PROTOCOL_ID_NO_ENTRY,
        /**
         * Extended Protocol ID for Encapsulation Packet Protocol
         */
        PROTOCOL_ID_EXTENSION,
        /**
         * Mission-specific, privately defined data
         */
        PROTOCOL_ID_MISSION_SPECIFIC

    }


    /**
     * The maximum length of an encapsulation packet header
     */
    private static final int EP_PRIMARY_HEADER_MAX_LENGTH = 8;

    /**
     * The maximum encapsulation packet length
     */
    public static final long MAX_ENCAPSULATION_PACKET_LENGTH = 4294967287L + EP_PRIMARY_HEADER_MAX_LENGTH;

    /**
     * Decoding function for space packets, assuming a positive quality indicator.
     *
     * @return the encapsulation packet decoding function
     */
    public static Function<byte[], EncapsulationPacket> decodingFunction() {
        // Default quality indicator is set to true
        return input -> new EncapsulationPacket(input, true);
    }

    /**
     * Decoding bifunction for space packets.
     *
     * @return the space packet decoding bifunction
     */
    public static BiFunction<byte[], Boolean, EncapsulationPacket> decodingBiFunction() {
        return EncapsulationPacket::new;
    }

    /**
     * Return the length of the primary header based on the first octet of the encapsulation packet.
     *
     * @param firstOctet the first octet of the encapsulation packet
     * @return the length of the primary header in bytes
     */
    public static int getPrimaryHeaderLength(byte firstOctet) {
        // Length of length (2 bits)
        switch(firstOctet & 0x03) { // Possible values are from 0 to 3
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 4;
            default:
                return 8;
        }
    }

    /**
     * Return the length of the complete packet, based on the information contained in the primary header.
     *
     * @param primaryHeader the array containing the encapsulation packet primary header
     * @param offset the offset to start with
     * @return the length of the encapsulation packet (including the length of the primary header)
     */
    public static long getEncapsulationPacketLength(byte[] primaryHeader, int offset) {
        int primaryHeaderLength = getPrimaryHeaderLength(primaryHeader[offset]);
        // Let's parse the packet based on the detected length
        if(primaryHeaderLength == 1) {
            // Encapsulation idle packet
            return 1;
        } else if(primaryHeaderLength == 2) {
            return primaryHeader[offset + 1] & 0xFF;
        } else if(primaryHeaderLength == 4) {
            return (ByteBuffer.wrap(primaryHeader, offset + 2, 2)).getShort() & 0xFFFF;
        } else { // primaryHeaderLength == 8)
            return (ByteBuffer.wrap(primaryHeader, offset + 4, 4)).getInt() & 0x00000000FFFFFFFFL;
        }
    }

    /**
     * Return the length of the complete packet, based on the information contained in the primary header.
     *
     * @param primaryHeader the encapsulation packet primary header
     * @return the length of the encapsulation packet (including the length of the primary header)
     */
    public static long getEncapsulationPacketLength(byte[] primaryHeader) {
        return getEncapsulationPacketLength(primaryHeader, 0);
    }

    private final byte[] packet;

    private final boolean qualityIndicator;

    private final int primaryHeaderLength;

    private final ProtocolIdType encapsulationProtocolId;

    private final boolean encapsulationProtocolIdExtensionPresent;

    private final byte encapsulationProtocolIdExtension;

    private final boolean userDefinedFieldPresent;

    private final byte userDefinedField;

    private final boolean ccsdsDefinedFieldPresent;

    private final byte[] ccsdsDefinedField;

    private final long encapsulatedPacketLength;

    private final long encapsulatedDataFieldLength;

    /**
     * Constructor of an encapsulation packet. The size of the packet argument must match exactly the encapsulation packet, otherwise
     * am {@link IllegalArgumentException} will be thrown.
     *
     * @param packet the encapsulation packet data
     * @param qualityIndicator quality indicator of the packet, true if it is good, false otherwise
     */
    public EncapsulationPacket(byte[] packet, boolean qualityIndicator) {
        this.packet = packet;
        this.qualityIndicator = qualityIndicator;

        ByteBuffer in = ByteBuffer.wrap(packet);
        // First octet
        byte firstOctet = in.get();

        // First 3 bits are the encapsulation packet version number: it should be 111, not checked

        // Encapsulation protocol ID (3 bits)
        this.encapsulationProtocolId = ProtocolIdType.values()[((firstOctet >> 2) & 0x07)];

        // Length of length (2 bits)
        this.primaryHeaderLength = getPrimaryHeaderLength(firstOctet);

        // Let's parse the packet based on the detected length
        if(this.primaryHeaderLength == 1) {
            // Encapsulation idle packet
            this.encapsulationProtocolIdExtensionPresent = false;
            this.encapsulationProtocolIdExtension = -1;
            this.ccsdsDefinedFieldPresent = false;
            this.ccsdsDefinedField = null;
            this.userDefinedFieldPresent = false;
            this.userDefinedField = -1;
            this.encapsulatedPacketLength = 1;
        } else if(this.primaryHeaderLength == 2) {
            this.encapsulationProtocolIdExtensionPresent = false;
            this.encapsulationProtocolIdExtension = -1;
            this.ccsdsDefinedFieldPresent = false;
            this.ccsdsDefinedField = null;
            this.userDefinedFieldPresent = false;
            this.userDefinedField = -1;
            // 1 octet contains the packet length (whole packet length, incl. header)
            this.encapsulatedPacketLength = in.get() & 0xFF;
        } else if(this.primaryHeaderLength == 4) {
            this.ccsdsDefinedFieldPresent = false;
            this.ccsdsDefinedField = null;
            // 1 octet contains the other header info (user defined field, protocol extension)
            byte secondOctet = in.get();
            this.userDefinedFieldPresent = true;
            this.userDefinedField = (byte) ((secondOctet >> 4) & 0x0F);
            this.encapsulationProtocolIdExtensionPresent = true;
            this.encapsulationProtocolIdExtension = (byte) (secondOctet & 0x0F);
            // 2 octets contain the packet length (whole packet length, incl. header)
            this.encapsulatedPacketLength = in.getShort() & 0xFFFF;
        } else { // this.primaryHeaderLength == 8
            // 1 octet contains the other header info (user defined field, protocol extension)
            byte secondOctet = in.get();
            this.userDefinedFieldPresent = true;
            this.userDefinedField = (byte) ((secondOctet >> 4) & 0x0F);
            this.encapsulationProtocolIdExtensionPresent = true;
            this.encapsulationProtocolIdExtension = (byte) (secondOctet & 0x0F);
            // 2 octets contain the CCSDS defined field
            this.ccsdsDefinedFieldPresent = true;
            this.ccsdsDefinedField = new byte[2];
            in.get(this.ccsdsDefinedField);
            // 4 octets contain the packet length (whole packet length, incl. header)
            this.encapsulatedPacketLength = in.getInt() & 0x00000000FFFFFFFFL;
        }

        this.encapsulatedDataFieldLength = this.encapsulatedPacketLength - this.primaryHeaderLength;

        if(this.encapsulatedPacketLength != packet.length) {
            throw new IllegalArgumentException("Wrong Packet Length: expected " + packet.length + ", actual " + this.encapsulatedPacketLength);
        }
    }

    /**
     * This method returns the direct reference to the encapsulation packet array.
     *
     * @return the encapsulation packet array
     */
    @Override
    public byte[] getPacket() {
        return packet;
    }

    /**
     * This method returns the value of the quality indicator flag.
     *
     * @return true if the quality is good, false otherwise
     */
    public boolean isQualityIndicator() {
        return qualityIndicator;
    }

    /**
     * This method returns whether the packet is an idle packet.
     *
     * @return true if the packet is an idle packet, false otherwise
     */
    public boolean isIdle() {
        return this.primaryHeaderLength == 1 || getEncapsulationProtocolId() == ProtocolIdType.PROTOCOL_ID_IDLE;
    }

    /**
     * This method returns a copy of the encapsulation packet array.
     *
     * @return a copy of the encapsulation packet array
     */
    public byte[] getPacketCopy() {
        return Arrays.copyOfRange(this.packet, 0, this.packet.length);
    }

    /**
     * This method returns the length of the encapsulation packet.
     *
     * @return the length of the encapsulation packet
     */
    @Override
    public int getLength() {
        return this.packet.length;
    }

    /**
     * This method returns a copy of the encapsulation packet data field.
     *
     * @return a copy of the encapsulation packet data field
     */
    public byte[] getDataFieldCopy() {
        return Arrays.copyOfRange(this.packet, this.primaryHeaderLength, this.packet.length);
    }

    /**
     * This method returns the length of the primary header.
     *
     * @return the length of the primary header
     */
    public int getPrimaryHeaderLength() {
        return primaryHeaderLength;
    }

    /**
     * This method returns the Encapsulation Protocol ID. The EPI shall be used to identify the protocol whose data unit is encapsulated
     * within the Encapsulation Packet.
     *
     * @return the Encapsulation Protocol ID
     */
    public ProtocolIdType getEncapsulationProtocolId() {
        return encapsulationProtocolId;
    }

    /**
     * This method returns whether the Encapsulation Protocol Extension ID field is present.
     *
     * @return true if the Encapsulation Protocol Extension ID is present, otherwise false
     */
    public boolean isEncapsulationProtocolIdExtensionPresent() {
        return encapsulationProtocolIdExtensionPresent;
    }

    /**
     * This method returns the value of the Encapsulation Protocol Extension ID field, if present.
     *
     * @return the value of the Encapsulation Protocol Extension ID field if present, otherwise -1
     */
    public byte getEncapsulationProtocolIdExtension() {
        return encapsulationProtocolIdExtension;
    }

    /**
     * This method returns whether the User Defined field is present.
     *
     * @return true if the User Defined is present, otherwise false
     */
    public boolean isUserDefinedFieldPresent() {
        return userDefinedFieldPresent;
    }

    /**
     * This method returns the value of the User Defined field, if present.
     *
     * @return the value of the User Defined field if present, otherwise -1
     */
    public byte getUserDefinedField() {
        return userDefinedField;
    }

    /**
     * This method returns whether the CCSDS Defined field is present.
     *
     * @return true if the CCSDS Defined is present, otherwise false
     */
    public boolean isCcsdsDefinedFieldPresent() {
        return ccsdsDefinedFieldPresent;
    }

    /**
     * This method returns the value of the CCSDS Defined field, if present.
     *
     * @return the value of the CCSDS Defined field if present, otherwise null
     */
    public byte[] getCcsdsDefinedField() {
        return ccsdsDefinedField;
    }

    /**
     * This method returns the length of the data field of the encapsulation packet.
     *
     * @return the length in bytes of the data field of the encapsulation packet
     */
    public long getEncapsulatedDataFieldLength() {
        return encapsulatedDataFieldLength;
    }

    @Override
    public String toString() {
        return "EncapsulationPacket{" +
                "qualityIndicator=" + qualityIndicator +
                ", primaryHeaderLength=" + primaryHeaderLength +
                ", encapsulationProtocolId=" + encapsulationProtocolId +
                ", encapsulationProtocolIdExtensionPresent=" + encapsulationProtocolIdExtensionPresent +
                ", encapsulationProtocolIdExtension=" + encapsulationProtocolIdExtension +
                ", userDefinedFieldPresent=" + userDefinedFieldPresent +
                ", userDefinedField=" + userDefinedField +
                ", ccsdsDefinedFieldPresent=" + ccsdsDefinedFieldPresent +
                ", ccsdsDefinedField=" + Arrays.toString(ccsdsDefinedField) +
                ", encapsulatedPacketLength=" + encapsulatedPacketLength +
                ", encapsulatedDataFieldLength=" + encapsulatedDataFieldLength +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EncapsulationPacket that = (EncapsulationPacket) o;
        return Arrays.equals(getPacket(), that.getPacket());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getPacket());
    }

    @Override
    public int getVersion() {
        return VERSION;
    }
}
