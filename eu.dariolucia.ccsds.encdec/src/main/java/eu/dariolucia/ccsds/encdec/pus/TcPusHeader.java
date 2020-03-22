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

/**
 * Definition of the TC PUS Header as per section 5.3.3 of ECSS-E-70-41A.
 */
public class TcPusHeader {

    private final byte version;
    private final AckField ackField;
    private final short serviceType;
    private final short serviceSubType;
    private final Integer sourceId;
    private final Integer encodedLength;

    /**
     * Shorten version of the class constructor. This constructor set the version to 1, the ack field to all 1s,
     * and the source ID to null (no source ID).
     *
     * @param serviceType the PUS service type
     * @param serviceSubType the PUS service subtype
     */
    public TcPusHeader(short serviceType, short serviceSubType) {
        this((byte) 1, AckField.allAcks(), serviceType, serviceSubType, null, null);
    }

    /**
     * Constructor of the TC PUS Header. It allows to set the values for all the PUS defined fields. In case one
     * value should be omitted, the value null shall be provided.
     *
     * @param version the version
     * @param ackField ACK field
     * @param serviceType the PUS service type
     * @param serviceSubType the PUS service subtype
     * @param sourceId the source ID (or null)
     * @param encodedLength the encoded length (if known, otherwise null)
     */
    public TcPusHeader(byte version, AckField ackField, short serviceType, short serviceSubType, Integer sourceId, Integer encodedLength) {
        this.version = version;
        this.ackField = ackField;
        this.serviceType = serviceType;
        this.serviceSubType = serviceSubType;
        this.sourceId = sourceId;
        this.encodedLength = encodedLength;
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
     * Return the ack field.
     *
     * @return the {@link AckField}
     */
    public AckField getAckField() {
        return ackField;
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
     * Return the source ID.
     *
     * @return the source ID or null if not present
     */
    public Integer getSourceId() {
        return sourceId;
    }

    /**
     * Return whether the source ID is present.
     *
     * @return true if the source ID is present, false otherwise
     */
    public boolean isSourceIdSet() {
        return sourceId != null;
    }

    /**
     * Return the encoded length in bytes, if known. Otherwise null. This field is always set on decoding.
     *
     * @return the encoded length
     */
    public Integer getEncodedLength() {
        return encodedLength;
    }

    @Override
    public String toString() {
        return "TcPusHeader{" +
                "version=" + version +
                ", ackField=" + ackField +
                ", serviceType=" + serviceType +
                ", serviceSubType=" + serviceSubType +
                ", sourceId=" + sourceId +
                '}';
    }

    /**
     * Encode the contents of this TC PUS Header into the provided byte buffer, starting at the
     * specified offset.
     *
     * @param output the output byte buffer. It shall be able to accomodate the full length of the header
     * @param offset the offset, the writing will start from
     * @param sourceIdLength the encoded length in bits of the source ID (used only if the source ID is present)
     * @param spare the number of spare bits to be added at the end of the encoding process (0 if unused)
     * return number of encoded bytes
     */
    public int encodeTo(byte[] output, int offset, int sourceIdLength, int spare) {
        BitEncoderDecoder encoder = new BitEncoderDecoder(output, offset, output.length - offset);
        int startBitNumber = encoder.getCurrentBitIndex();
        // Write first bit (0), TC Packet PUS Version Number, ack field and the two octets for type and subtype
        int firstThreeOctets = version & 0x07;
        firstThreeOctets <<= 4;
        firstThreeOctets |= ackField.getBitRepresentation();
        firstThreeOctets <<= 8;
        firstThreeOctets |= Short.toUnsignedInt(serviceType);
        firstThreeOctets <<= 8;
        firstThreeOctets |= Short.toUnsignedInt(serviceSubType);
        encoder.setNextIntegerUnsigned(firstThreeOctets, 24);
        if(isSourceIdSet()) {
            encoder.setNextIntegerUnsigned(sourceId, sourceIdLength);
        }
        encoder.setNextIntegerUnsigned(0, spare);

        int finalBitNumber = encoder.getCurrentBitIndex();
        return (int) Math.ceil((finalBitNumber - startBitNumber) / (double) Byte.SIZE);
    }

    /**
     * Decode the contents of the provided byte array, starting at the specified offset, into a TC PUS Header.
     *
     * @param input the input byte buffer. It shall contain the full PUS header
     * @param offset the offset, the reading will start from
     * @param sourceIdLength the encoded length in bits of the source ID (used only if the source ID is present), less or equal to 0 means not present
     * @return the TC PUS header
     */
    public static TcPusHeader decodeFrom(byte[] input, int offset, int sourceIdLength) {
        BitEncoderDecoder decoder = new BitEncoderDecoder(input, offset, input.length - offset);
        int startBitIdx = decoder.getCurrentBitIndex();
        int firstThreeOctets = decoder.getNextIntegerUnsigned(24);
        byte version = (byte) ((firstThreeOctets & 0x00700000) >>> 20);
        byte ackField = (byte) ((firstThreeOctets & 0x000F0000) >>> 16);
        short serviceType = (short) ((firstThreeOctets & 0x0000FF00) >>> 8);
        short serviceSubType = (short) (firstThreeOctets & 0x000000FF);
        Integer sourceId = null;
        if(sourceIdLength > 0) {
            sourceId = decoder.getNextIntegerUnsigned(sourceIdLength);
        }
        // Reach byte alignment
        int cBitIdx = decoder.getCurrentBitIndex();
        int mod = cBitIdx % Byte.SIZE;
        if(mod != 0) {
            decoder.addCurrentBitIndex(Byte.SIZE - mod);
        }
        int bytesRead = (decoder.getCurrentBitIndex() - startBitIdx) / Byte.SIZE;
        return new TcPusHeader(version, new AckField(ackField), serviceType, serviceSubType, sourceId, bytesRead);
    }
}
