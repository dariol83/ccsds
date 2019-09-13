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

package eu.dariolucia.ccsds.tmtc.transport.builder;

import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class allows to build a CCSDS space packet using a typical Builder pattern. Once a packet is built, the builder should
 * not be re-used, as the internals (such as the payload data) are not cleaned up upon build.
 *
 * This class is not thread-safe.
 */
public class SpacePacketBuilder {

    /**
     * This method creates an instance of this class.
     *
     * @param qualityIndicator true if the quality is good, false otherwise
     * @return the builder object
     */
    public static SpacePacketBuilder create(boolean qualityIndicator) {
        return new SpacePacketBuilder(qualityIndicator);
    }

    /**
     * This method creates an instance of this class, with positive quality indicator.
     *
     * @return the builder object
     */
    public static SpacePacketBuilder create() {
        return create(true);
    }

    private boolean qualityIndicator;

    private boolean telemetryPacket;

    private boolean secondaryHeaderFlag;

    private short apid;

    private SpacePacket.SequenceFlagType sequenceFlag = SpacePacket.SequenceFlagType.UNSEGMENTED;

    private short packetSequenceCount;

    private int freeUserDataLength;

    private List<byte[]> payloadUnits = new LinkedList<>();

    private SpacePacketBuilder(boolean qualityIndicator) {
        this.qualityIndicator = qualityIndicator;
        this.freeUserDataLength = SpacePacket.MAX_SPACE_PACKET_LENGTH - SpacePacket.SP_PRIMARY_HEADER_LENGTH;
    }

    public SpacePacketBuilder setQualityIndicator(boolean qualityIndicator) {
        this.qualityIndicator = qualityIndicator;
        return this;
    }

    public SpacePacketBuilder setTelemetryPacket() {
        this.telemetryPacket = true;
        return this;
    }

    public SpacePacketBuilder setTelecommandPacket() {
        this.telemetryPacket = false;
        return this;
    }

    public SpacePacketBuilder setSecondaryHeaderFlag(boolean secondaryHeaderFlag) {
        this.secondaryHeaderFlag = secondaryHeaderFlag;
        return this;
    }

    public SpacePacketBuilder setApid(int apid) {
        if(apid < 0 || apid > 2047) {
            throw new IllegalArgumentException("Application Process ID must be 0 <= AP ID <= 2047, actual " + apid);
        }
        this.apid = (short) apid;
        return this;
    }

    public SpacePacketBuilder setIdle() {
        this.apid = SpacePacket.SP_IDLE_APID_VALUE;
        return this;
    }

    public SpacePacketBuilder setSequenceFlag(SpacePacket.SequenceFlagType sequenceFlag) {
        this.sequenceFlag = sequenceFlag;
        return this;
    }

    public SpacePacketBuilder setPacketSequenceCount(int packetSequenceCount) {
        if(packetSequenceCount < 0 || packetSequenceCount > (short) 0x3FFF) {
            throw new IllegalArgumentException("Packet sequence counter must be 0 <= PSC <= 16383, actual " + packetSequenceCount);
        }
        this.packetSequenceCount = (short) packetSequenceCount;
        return this;
    }

    public int addData(byte[] b, int offset, int length) {
        // Compute if you can add the requested amount
        int dataToBeWritten = Math.min(freeUserDataLength, length);
        int notWrittenData = freeUserDataLength < length ? length - freeUserDataLength : 0;
        if(dataToBeWritten > 0) {
            this.payloadUnits.add(Arrays.copyOfRange(b, offset, offset + dataToBeWritten));
            freeUserDataLength -= dataToBeWritten;
        }
        return notWrittenData;
    }

    public int addData(byte[] b) {
        return addData(b, 0, b.length);
    }

    public int getFreeUserDataLength() {
        return this.freeUserDataLength;
    }

    public boolean isFull() {
        return this.freeUserDataLength == 0;
    }

    public SpacePacket build() {
        int payloadDataLength = this.payloadUnits.stream().map(o -> o.length).reduce(0, Integer::sum);
        int packetLength = SpacePacket.SP_PRIMARY_HEADER_LENGTH + payloadDataLength;

        ByteBuffer bb = ByteBuffer.allocate(packetLength);

        short first2octets = 0;

        if(!telemetryPacket) {
            first2octets |= 0x1000;
        }

        if(secondaryHeaderFlag) {
            first2octets |= 0x0800;
        }

        first2octets |= (this.apid & (short) 0x07FF);

        bb.putShort(first2octets);

        short next2octets = (short) this.sequenceFlag.ordinal();
        next2octets <<= 14;

        next2octets |= (short) (this.packetSequenceCount & (short) 0x3FFF);

        bb.putShort(next2octets);

        bb.putShort((short) (payloadDataLength - 1));

        // Write the user data
        for(byte[] pu : this.payloadUnits) {
            bb.put(pu);
        }

        byte[] encodedPacket = bb.array();

        // Return the packet
        return new SpacePacket(encodedPacket, qualityIndicator);
    }
}
