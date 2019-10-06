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

package eu.dariolucia.ccsds.tmtc.transport.pdu;

import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This class is used to decode and manipulate a space packet, compliant to CCSDS 133.0-B-1.
 */
public class SpacePacket extends AnnotatedObject {

    /**
     * The length of the primary header
     */
    public static final int SP_PRIMARY_HEADER_LENGTH = 6;

    /**
     * The value of the APID field for idle packets
     */
    public static final short SP_IDLE_APID_VALUE = 0x07FF;

    /**
     * The maximum space packet length
     */
    public static final int MAX_SPACE_PACKET_LENGTH = 65536 + SP_PRIMARY_HEADER_LENGTH;

    /**
     * Enumeration for sequence flag possible values
     */
    public enum SequenceFlagType {
        CONTINUE,
        FIRST,
        LAST,
        UNSEGMENTED
    }

    /**
     * Decoding function for space packets, assuming a positive quality indicator.
     *
     * @return the space packet decoding function
     */
    public static Function<byte[], SpacePacket> decodingFunction() {
        // Default quality indicator is set to true
        return input -> new SpacePacket(input, true);
    }

    /**
     * Decoding bifunction for space packets.
     *
     * @return the space packet decoding bifunction
     */
    public static BiFunction<byte[], Boolean, SpacePacket> decodingBiFunction() {
        return SpacePacket::new;
    }

    private final byte[] packet;

    private final boolean qualityIndicator;

    private final boolean telemetryPacket;

    private final boolean secondaryHeaderFlag;

    private final short apid;

    private final SequenceFlagType sequenceFlag;

    private final short packetSequenceCount;

    private final int packetDataLength;

    /**
     * Constructor of a space packet. The size of the packet argument must match exactly the space packet, otherwise
     * am {@link IllegalArgumentException} will be thrown.
     *
     * @param packet the space packet data
     * @param qualityIndicator quality indicator of the packet, true if it is good, false otherwise
     */
    public SpacePacket(byte[] packet, boolean qualityIndicator) {
        this.packet = packet;
        this.qualityIndicator = qualityIndicator;

        ByteBuffer in = ByteBuffer.wrap(packet);
        // First 2 octets
        short twoOctets = in.getShort();

        // First 3 bits are the space packet version number: it should be 000, not checked

        telemetryPacket = (twoOctets & (short) 0x1000) == 0;
        secondaryHeaderFlag = (twoOctets & (short) 0x0800) != 0;
        apid = (short) (twoOctets & (short) 0x07FF);

        // Next 2 octets
        twoOctets = in.getShort();

        int seqFlagValue = ((twoOctets & (short) 0xC000) & 0xFFFF);
        seqFlagValue >>= 14;
        sequenceFlag = SequenceFlagType.values()[seqFlagValue];
        packetSequenceCount = (short) (twoOctets & (short) 0x3FFF);

        // Next 2 octets
        twoOctets = in.getShort();
        packetDataLength = Short.toUnsignedInt(twoOctets) + 1;

        // 4.1.2.7.3
        if(packetDataLength + SP_PRIMARY_HEADER_LENGTH != packet.length) {
            throw new IllegalArgumentException("Wrong Packet Length: expected " + packet.length + ", actual " + (packetDataLength + SP_PRIMARY_HEADER_LENGTH));
        }
    }

    /**
     * This method returns the direct reference to the space packet array.
     *
     * @return the space packet array
     */
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
     * This method returns true if the space packet is a TM packet, false if it is a TC packet.
     *
     * @return true if TM packet, false if TC packet
     */
    public boolean isTelemetryPacket() {
        return telemetryPacket;
    }

    /**
     * This method returns whether the secondary header is present.
     *
     * @return true if the secondary header is present, false otherwise
     */
    public boolean isSecondaryHeaderFlag() {
        return secondaryHeaderFlag;
    }

    /**
     * This method returns the value of the application process ID.
     *
     * @return the value of the APID field
     */
    public short getApid() {
        return apid;
    }

    /**
     * This method returns the value of the sequence flag.
     *
     * @return the value of the sequence flag
     */
    public SequenceFlagType getSequenceFlag() {
        return sequenceFlag;
    }

    /**
     * This method returns the value of the packet sequence count.
     *
     * @return the value of the packet sequence count
     */
    public short getPacketSequenceCount() {
        return packetSequenceCount;
    }

    /**
     * This method returns the value of the packet user data length in bytes.
     *
     * @return the value of the packet user data length in bytes
     */
    public int getPacketDataLength() {
        return packetDataLength;
    }

    /**
     * This method returns whether the packet is an idle packet.
     *
     * @return true if the packet is an idle packet, false otherwise
     */
    public boolean isIdle() {
        return this.apid == SP_IDLE_APID_VALUE;
    }

    /**
     * This method returns a copy of the space packet array.
     *
     * @return a copy of the space packet array
     */
    public byte[] getPacketCopy() {
        return Arrays.copyOfRange(this.packet, 0, this.packet.length);
    }

    @Override
    public int getLength() {
        return this.packet.length;
    }

    /**
     * This method returns a copy of the space packet data field.
     *
     * @return a copy of the space packet data field
     */
    public byte[] getDataFieldCopy() {
        return Arrays.copyOfRange(this.packet, SP_PRIMARY_HEADER_LENGTH, this.packet.length);
    }

    @Override
    public String toString() {
        return "SpacePacket{" +
                "goodQuality=" + qualityIndicator +
                ", telemetryPacket=" + telemetryPacket +
                ", shFlag=" + secondaryHeaderFlag +
                ", apid=" + apid +
                ", seqFlag=" + sequenceFlag +
                ", packetSequenceCount=" + packetSequenceCount +
                ", packetDataLength=" + packetDataLength +
                '}';
    }
}
