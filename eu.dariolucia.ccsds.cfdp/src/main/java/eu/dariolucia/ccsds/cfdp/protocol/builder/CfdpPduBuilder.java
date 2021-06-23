/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.protocol.builder;

import eu.dariolucia.ccsds.cfdp.common.BytesUtil;
import eu.dariolucia.ccsds.cfdp.common.CfdpRuntimeException;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.tmtc.algorithm.Crc16Algorithm;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parent class for all PDU builder objects.
 *
 * @param <T> the CFDP PDU type
 * @param <K> the CFDP PDU builder type (required to allow specific-type chains of builder method calls)
 */
public abstract class CfdpPduBuilder<T extends CfdpPdu, K extends CfdpPduBuilder<T, K>> {

    private static final Logger LOG = Logger.getLogger(CfdpPduBuilder.class.getName());

    private static final int VERSION = 0b001;
    private static final int INITIAL_BYTE_OUTPUT_ALLOCATION_BYTES = 512;
    private static final byte[] CRC_PLACEHOLDER = new byte[] {0,0};

    private CfdpPdu.PduType type;

    private CfdpPdu.Direction direction;

    private boolean acknowledged;

    private boolean crcPresent;

    private boolean largeFile;

    private boolean segmentationControlPreserved;

    private int entityIdLength;

    private boolean segmentMetadataPresent;

    private int transactionSequenceNumberLength;

    private long sourceEntityId;

    private long transactionSequenceNumber;

    private long destinationEntityId;

    protected K setType(CfdpPdu.PduType type) {
        this.type = type;
        return (K) this;
    }

    public K setDirection(CfdpPdu.Direction direction) {
        this.direction = direction;
        return (K) this;
    }

    public K setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
        return (K) this;
    }

    public K setCrcPresent(boolean crcPresent) {
        this.crcPresent = crcPresent;
        return (K) this;
    }

    public K setLargeFile(boolean largeFile) {
        this.largeFile = largeFile;
        return (K) this;
    }

    public K setSegmentationControlPreserved(boolean segmentationControlPreserved) {
        this.segmentationControlPreserved = segmentationControlPreserved;
        return (K) this;
    }

    public K setEntityIdLength(int entityIdLength) {
        this.entityIdLength = entityIdLength;
        return (K) this;
    }

    public K setSegmentMetadataPresent(boolean segmentMetadataPresent) {
        this.segmentMetadataPresent = segmentMetadataPresent;
        return (K) this;
    }

    public K setSourceEntityId(long sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
        return (K) this;
    }

    public K setTransactionSequenceNumber(long transactionSequenceNumber, int transactionSequenceNumberLength) {
        this.transactionSequenceNumberLength = transactionSequenceNumberLength;
        this.transactionSequenceNumber = transactionSequenceNumber;
        return (K) this;
    }

    public K setDestinationEntityId(long destinationEntityId) {
        this.destinationEntityId = destinationEntityId;
        return (K) this;
    }

    public int getVersion() {
        return VERSION;
    }

    public CfdpPdu.PduType getType() {
        return type;
    }

    public CfdpPdu.Direction getDirection() {
        return direction;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public boolean isCrcPresent() {
        return crcPresent;
    }

    public boolean isLargeFile() {
        return largeFile;
    }

    public boolean isSegmentationControlPreserved() {
        return segmentationControlPreserved;
    }

    public int getEntityIdLength() {
        return entityIdLength;
    }

    public boolean isSegmentMetadataPresent() {
        return segmentMetadataPresent;
    }

    public int getTransactionSequenceNumberLength() {
        return transactionSequenceNumberLength;
    }

    public long getSourceEntityId() {
        return sourceEntityId;
    }

    public long getTransactionSequenceNumber() {
        return transactionSequenceNumber;
    }

    public long getDestinationEntityId() {
        return destinationEntityId;
    }

    public T build() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(getInitialBufferAllocation());
            // Encode the 1st byte
            byte tempByte = (byte) (VERSION << 5);
            tempByte |= (byte) (this.type.ordinal() << 4);
            tempByte |= (byte) (this.direction.ordinal() << 3);
            if (!this.acknowledged) {
                tempByte |= 0x04;
            }
            if (this.crcPresent) {
                tempByte |= 0x02;
            }
            if (this.largeFile) {
                tempByte |= 0x01;
            }
            bos.write(tempByte);
            // Reserve a placeholder (2 bytes) for the length, not known at the moment
            bos.write(0);
            bos.write(0);
            // Encode the 4th byte
            tempByte = 0;
            if (segmentationControlPreserved) {
                tempByte |= 0x80;
            }
            tempByte |= ((this.entityIdLength - 1) << 4);
            if (this.segmentMetadataPresent) {
                tempByte |= 0x08;
            }
            tempByte |= (this.transactionSequenceNumberLength - 1);
            bos.write(tempByte);
            // Encode the source entity ID
            bos.write(BytesUtil.encodeInteger(this.sourceEntityId, this.entityIdLength));
            // Encode the transaction sequence number
            bos.write(BytesUtil.encodeInteger(this.transactionSequenceNumber, this.transactionSequenceNumberLength));
            // Encode the destination entity ID
            bos.write(BytesUtil.encodeInteger(this.destinationEntityId, this.entityIdLength));
            // Encode the data field
            int len = encodeDataField(bos);

            // If CRC is enabled, then add 2 bytes 0x00, 0x00 to the data field. Ref: 4.1.1, 4.1.3
            if (this.crcPresent) {
                bos.write(CRC_PLACEHOLDER);
                len += 2;
            }

            // Close and get the PDU
            byte[] cfdpPdu = bos.toByteArray();

            // Fix the length of the data field
            ByteBuffer.wrap(cfdpPdu, 1, 2).putShort((short) (len & 0xFFFF));

            // If CRC is enabled, compute the CRC and write the result in the last two bytes
            if (this.crcPresent) {
                short crc = Crc16Algorithm.getCrc16(cfdpPdu, 0, cfdpPdu.length - 2);
                ByteBuffer.wrap(cfdpPdu, cfdpPdu.length - 2, 2).putShort(crc);
            }

            if(LOG.isLoggable(Level.FINEST)) {
                LOG.log(Level.FINEST, String.format("CFDP PDU Builder %s: generated PDU: %s", getClass().getSimpleName(), StringUtil.toHexDump(cfdpPdu)));
            }
            // Build the Pdu and return
            return buildObject(cfdpPdu);
        } catch (IOException e) {
            // IOException on byte array output stream is not relevant
            throw new CfdpRuntimeException(e);
        }
    }

    protected int getInitialBufferAllocation() {
        return INITIAL_BYTE_OUTPUT_ALLOCATION_BYTES;
    }

    protected abstract int encodeDataField(ByteArrayOutputStream bos) throws IOException;

    protected abstract T buildObject(byte[] pdu);
}

