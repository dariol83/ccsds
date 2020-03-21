/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.encdec.pus;

import eu.dariolucia.ccsds.encdec.bit.BitEncoderDecoder;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.encdec.value.TimeUtil;

import java.time.Instant;

/**
 * Definition of the TM PUS Header as per section 5.4.3 of ECSS-E-70-41A.
 */
public class TmPusHeader {

    private final byte version;
    private final short serviceType;
    private final short serviceSubType;
    private final Short packetSubCounter;
    private final Integer destinationId;
    private final Instant absoluteTime;

    /**
     * Shorten version of the class constructor. This constructor set the version to 1,
     * and the packet sub-counter and destination ID to null.
     * @param serviceType the PUS service type
     * @param serviceSubType the PUS service subtype
     * @param absoluteTime the absolute time (or null)
     */
    public TmPusHeader(short serviceType, short serviceSubType, Instant absoluteTime) {
        this((byte) 1, serviceType, serviceSubType, null, null, absoluteTime);
    }

    /**
     * Constructor of the TM PUS Header. It allows to set the values for all the PUS defined fields. In case one
     * value should be omitted, the value null shall be provided.
     *
     * @param version the version
     * @param serviceType the PUS service type
     * @param serviceSubType the PUS service subtype
     * @param packetSubCounter the packet sub-counter (or null)
     * @param destinationId the destination ID (or null)
     * @param absoluteTime the absolute time (or null)
     */
    public TmPusHeader(byte version, short serviceType, short serviceSubType, Short packetSubCounter, Integer destinationId, Instant absoluteTime) {
        this.version = version;
        this.serviceType = serviceType;
        this.serviceSubType = serviceSubType;
        this.packetSubCounter = packetSubCounter;
        this.destinationId = destinationId;
        this.absoluteTime = absoluteTime;
    }

    /**
     * Return the PUS header version.
     *
     * @return the PUS header version
     */
    public byte getVersion() {
        return version;
    }

    /**
     * Return the service type.
     *
     * @return the PUS service type
     */
    public short getServiceType() {
        return serviceType;
    }

    /**
     * Return the service subtype.
     *
     * @return the service subtype
     */
    public short getServiceSubType() {
        return serviceSubType;
    }

    /**
     * Return the value of the packet sub-counter.
     *
     * @return the value of the packet sub-counter, or null if not defined
     */
    public Short getPacketSubCounter() {
        return packetSubCounter;
    }

    /**
     * Return the value of the destination ID.
     *
     * @return the value of the destination ID, or null if not defined
     */
    public Integer getDestinationId() {
        return destinationId;
    }

    /**
     * Return the value of the absolute time.
     *
     * @return the value of the absolute time, or null if not defined
     */
    public Instant getAbsoluteTime() {
        return absoluteTime;
    }

    /**
     * Return whether the packet sub-counter is present.
     *
     * @return true if the packet sub-counter is present, false otherwise
     */
    public boolean isPacketSubCounterSet() {
        return packetSubCounter != null;
    }

    /**
     * Return whether the destination ID is present.
     *
     * @return true if the destination ID is present, false otherwise
     */
    public boolean isDestinationIdSet() {
        return destinationId != null;
    }

    /**
     * Return whether the absolute time is present.
     *
     * @return true if the absolute time is present, false otherwise
     */
    public boolean isAbsoluteTimeSet() {
        return absoluteTime != null;
    }

    @Override
    public String toString() {
        return "TmPusHeader{" +
                "version=" + version +
                ", serviceType=" + serviceType +
                ", serviceSubType=" + serviceSubType +
                ", packetSubCounter=" + packetSubCounter +
                ", destinationId=" + destinationId +
                ", absoluteTime=" + absoluteTime +
                '}';
    }

    /**
     * Encode the contents of this TM PUS Header into the provided byte buffer, starting at the
     * specified offset.
     *
     * @param output the output byte buffer. It shall be able to accommodate the full length of the PUS header
     * @param offset the offset, the writing will start from
     * @param destinationIdLength the encoded length in bits of the destination ID (used only if the destination ID is present)
     * @param absoluteTimeExplicit if true, then the time is encoded with a P-Field, otherwise no P-Field is used
     * @param absoluteTimeAgencyEpoch if set, then the absolute time set in this class is encoded using the provided epoch
     * @param absoluteTimeFormat the format of the absolute time
     * @param spare the number of spare bits to be added at the end of the encoding process (0 if unused)
     */
    public void encodeTo(byte[] output, int offset, int destinationIdLength, boolean absoluteTimeExplicit, Instant absoluteTimeAgencyEpoch, AbsoluteTimeDescriptor absoluteTimeFormat, int spare) {
        BitEncoderDecoder encoder = new BitEncoderDecoder(output, offset, output.length - offset);
        // Write first bit (0), TM Packet PUS Version Number, 4 bits spare and the two octets for type and subtype
        int firstThreeOctets = version & 0x07;
        firstThreeOctets <<= 12;
        firstThreeOctets |= Short.toUnsignedInt(serviceType);
        firstThreeOctets <<= 8;
        firstThreeOctets |= Short.toUnsignedInt(serviceSubType);
        encoder.setNextIntegerUnsigned(firstThreeOctets, 24);
        // Packet sub-counter if set
        if(isPacketSubCounterSet()) {
            encoder.setNextIntegerUnsigned(packetSubCounter, 8);
        }
        // Destination ID if set
        if(isDestinationIdSet()) {
            encoder.setNextIntegerUnsigned(destinationId, destinationIdLength);
        }
        // Absolute time if set
        if(isAbsoluteTimeSet()) {
            // Convert the instant to the correct format
            byte[] encoded;
            if(absoluteTimeFormat.cuc) {
                encoded = TimeUtil.toCUC(absoluteTime, absoluteTimeAgencyEpoch, absoluteTimeFormat.coarseTime, absoluteTimeFormat.fineTime, absoluteTimeExplicit);
            } else {
                encoded = TimeUtil.toCDS(absoluteTime, absoluteTimeAgencyEpoch, absoluteTimeFormat.use16bits, absoluteTimeFormat.subMsPart, absoluteTimeExplicit);
            }
            encoder.setNextByte(encoded, encoded.length * Byte.SIZE);
        }
        encoder.setNextIntegerUnsigned(0, spare);
    }

    /**
     * Decode the contents of the provided byte array, starting at the specified offset, into a TM PUS Header.
     * If absoluteTimeExplicit is false and absoluteTimeFormat is null, it means that the Time field is not present.
     * Bear in mind that the absoluteTimeFormat argument shall be provided also in case absoluteTimeExplicit is set to
     * true: in such case, only the variable 'cuc' is read and the rest is ignored.
     *
     * @param input the input byte buffer. It shall contain the full PUS header
     * @param offset the offset, the reading will start from
     * @param packetSubCounterPresent true if the packet sub-counter is present, false otherwise
     * @param destinationIdLength the encoded length in bits of the destination ID (used only if the destination ID is present), less or equal to 0 means not present
     * @param absoluteTimeExplicit if true, then the time is encoded with a P-Field, otherwise no P-Field is used
     * @param absoluteTimeAgencyEpoch if set, then the absolute time set in this class is decoded using the provided epoch
     * @param absoluteTimeFormat the type and (if not explicit) format of the absolute time
     * @return the TM PUS Header
     */
    public static TmPusHeader decodeFrom(byte[] input, int offset, boolean packetSubCounterPresent, int destinationIdLength, boolean absoluteTimeExplicit, Instant absoluteTimeAgencyEpoch, AbsoluteTimeDescriptor absoluteTimeFormat) {
        BitEncoderDecoder decoder = new BitEncoderDecoder(input, offset, input.length - offset);
        int firstThreeOctets = decoder.getNextIntegerUnsigned(24);
        byte version = (byte) ((firstThreeOctets & 0x00700000) >>> 20);
        short serviceType = (short) ((firstThreeOctets & 0x0000FF00) >>> 8);
        short serviceSubType = (short) (firstThreeOctets & 0x000000FF);
        Short subPacketCounter = null;
        if(packetSubCounterPresent) {
            subPacketCounter = (short) decoder.getNextIntegerUnsigned(8);
        }
        Integer destinationId = null;
        if(destinationIdLength > 0) {
            destinationId = decoder.getNextIntegerUnsigned(destinationIdLength);
        }
        Instant absoluteTime = null;
        if(absoluteTimeExplicit) {
            // Parse from P-Field
            if(absoluteTimeFormat.cuc) {
                absoluteTime = TimeUtil.fromCUC(decoder, absoluteTimeAgencyEpoch);
            } else {
                absoluteTime = TimeUtil.fromCDS(decoder, absoluteTimeAgencyEpoch);
            }
        } else if(absoluteTimeFormat != null) {
            // Implicit, use time format
            if(absoluteTimeFormat.cuc) {
                byte[] cucRead = decoder.getNextByte(Byte.SIZE * (absoluteTimeFormat.coarseTime + absoluteTimeFormat.fineTime));
                absoluteTime = TimeUtil.fromCUC(cucRead, absoluteTimeAgencyEpoch, absoluteTimeFormat.coarseTime, absoluteTimeFormat.fineTime);
            } else {
                int tFieldLengthBytes = 4 + (absoluteTimeFormat.use16bits ? 2 : 3) + (absoluteTimeFormat.subMsPart * Short.BYTES);
                byte[] cdsRead = decoder.getNextByte(Byte.SIZE * tFieldLengthBytes);
                absoluteTime = TimeUtil.fromCDS(cdsRead, absoluteTimeAgencyEpoch, absoluteTimeFormat.use16bits, absoluteTimeFormat.subMsPart);
            }
        } // Otherwise, not used

        return new TmPusHeader(version, serviceType, serviceSubType, subPacketCounter, destinationId, absoluteTime);
    }
}
