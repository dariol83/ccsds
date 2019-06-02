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

public class SpacePacket extends AnnotatedObject {

    public static final int SP_PRIMARY_HEADER_LENGTH = 6;

    public static final short SP_IDLE_APID_VALUE = 0x07FF;

    public static final int MAX_SPACE_PACKET_LENGTH = 65536 + SP_PRIMARY_HEADER_LENGTH;

    public enum SequenceFlagType {
        CONTINUE,
        FIRST,
        LAST,
        UNSEGMENTED
    }

    public static Function<byte[], SpacePacket> decodingFunction() {
        // Default quality indicator is set to true
        return (input) -> new SpacePacket(input, true);
    }

    public static BiFunction<byte[], Boolean, SpacePacket> decodingBiFunction() {
        return SpacePacket::new;
    }

    private final byte[] packet;

    private final boolean qualityIndicator;

    private boolean telemetryPacket;

    private boolean secondaryHeaderFlag;

    private short apid;

    private SequenceFlagType sequenceFlag;

    private short packetSequenceCount;

    private int packetDataLength;

    public SpacePacket(byte[] packet, boolean qualityIndicator) {
        this.packet = packet;
        this.qualityIndicator = qualityIndicator;
        decode();
    }

    protected void decode() {
        ByteBuffer in = ByteBuffer.wrap(packet);
        // First 2 octets
        short twoOctets = in.getShort();

        short versionNumber = (short) (((twoOctets & (short) 0xE000) & 0xFFFF) >> 13);
        // 4.1.2.2.2
        // if (versionNumber != 0) {
        //     throw new IllegalArgumentException("Packet Version Number: expected 0, actual " + versionNumber);
        // }

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

    public byte[] getPacket() {
        return packet;
    }

    public boolean isQualityIndicator() {
        return qualityIndicator;
    }

    public boolean isTelemetryPacket() {
        return telemetryPacket;
    }

    public boolean isSecondaryHeaderFlag() {
        return secondaryHeaderFlag;
    }

    public short getApid() {
        return apid;
    }

    public SequenceFlagType getSequenceFlag() {
        return sequenceFlag;
    }

    public short getPacketSequenceCount() {
        return packetSequenceCount;
    }

    public int getPacketDataLength() {
        return packetDataLength;
    }

    public boolean isIdle() {
        return this.apid == SP_IDLE_APID_VALUE;
    }

    public byte[] getPacketCopy() {
        return Arrays.copyOfRange(this.packet, 0, this.packet.length);
    }

    @Override
    public int getLength() {
        return this.packet.length;
    }

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
