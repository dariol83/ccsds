package eu.dariolucia.ccsds.cfdp.protocol.pdu;

import eu.dariolucia.ccsds.cfdp.common.CfdpRuntimeException;

import java.nio.ByteBuffer;

public class CfdpPdu {

    public enum PduType {
        FILE_DIRECTIVE,
        FILE_DATA
    }

    public enum Direction {
        TOWARD_FILE_RECEIVER,
        TOWARD_FILE_SENDER
    }


    private final int version;

    private final PduType type;

    private final Direction direction;

    private final boolean unacknowledged;

    private final boolean crcPresent;

    /** Every file whose size can't be represented in an unsigned 32-bit integer shall be flagged 'large'. All files of unbounded size shall be flagged 'large'. All other files shall be flagged 'small'. */
    private final boolean largeFile;

    private final int dataFieldLength;

    private final boolean segmentationControlPreserved;

    private final int entityIdLength;

    private final boolean segmentMetadata;

    private final int transactionSequenceNumberLength;

    private final long sourceEntityId;

    private final long transactionSequenceNumber;

    private final long destinationEntityId;

    private final int headerLength;

    private final byte[] pdu;

    public CfdpPdu(byte[] pdu) {
        this.version = (pdu[0] & 0xE0) >>> 5;
        this.type = ((pdu[0] & 0x10) >>> 4) == 0 ? PduType.FILE_DIRECTIVE : PduType.FILE_DATA;
        this.direction = ((pdu[0] & 0x08) >>> 3) == 0 ? Direction.TOWARD_FILE_RECEIVER : Direction.TOWARD_FILE_SENDER;
        this.unacknowledged = ((pdu[0] & 0x04) >>> 2) == 0;
        this.crcPresent = ((pdu[0] & 0x02) >>> 1) == 1;
        this.largeFile = (pdu[0] & 0x01) == 1;
        this.dataFieldLength = Short.toUnsignedInt(ByteBuffer.wrap(pdu, 1, pdu.length - 1).getShort());
        this.segmentationControlPreserved = ((pdu[3] & 0x80) >>> 7) == 1;
        this.entityIdLength = ((pdu[3] & 0x70) >>> 4) + 1;
        this.segmentMetadata = ((pdu[3] & 0x08) >>> 3) == 1;
        this.transactionSequenceNumberLength = (pdu[3] & 0x07) + 1;
        // Let's parse the source entity ID
        this.sourceEntityId = readInteger(pdu, 4, entityIdLength);
        // Let's parse the transaction sequence number
        this.transactionSequenceNumber = readInteger(pdu, 4 + entityIdLength, transactionSequenceNumberLength);
        // Let's parse the destination entity ID
        this.destinationEntityId = readInteger(pdu, 4 + entityIdLength + transactionSequenceNumberLength, entityIdLength);
        // Store the header length
        this.headerLength = 4 + 2 * entityIdLength + transactionSequenceNumberLength;
        // Store the full PDU here
        this.pdu = pdu;
    }

    protected final Long readInteger(byte[] data, int offset, int size) {
        if(size > 8) {
            throw new CfdpRuntimeException("Cannot read an unsigned integer larger than 8, actual is " + size);
        }
        if(size == 0) {
            return null;
        }
        long tempAccumulator = 0;
        for(int i = 0; i < size; ++i) {
            tempAccumulator |= Byte.toUnsignedLong(data[offset + i]);
            tempAccumulator <<= 8;
        }
        return tempAccumulator;
    }

    public int getVersion() {
        return version;
    }

    public int getHeaderLength() {
        return headerLength;
    }

    public PduType getType() {
        return type;
    }

    public Direction getDirection() {
        return direction;
    }

    public boolean isUnacknowledged() {
        return unacknowledged;
    }

    public boolean isCrcPresent() {
        return crcPresent;
    }

    public boolean isLargeFile() {
        return largeFile;
    }

    public int getDataFieldLength() {
        return dataFieldLength;
    }

    public boolean isSegmentationControlPreserved() {
        return segmentationControlPreserved;
    }

    public int getEntityIdLength() {
        return entityIdLength;
    }

    public boolean isSegmentMetadata() {
        return segmentMetadata;
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

    public byte[] getPdu() {
        return pdu;
    }

    @Override
    public String toString() {
        return "CfdpPdu{" +
                "version=" + version +
                ", type=" + type +
                ", direction=" + direction +
                ", unacknowledged=" + unacknowledged +
                ", crcPresent=" + crcPresent +
                ", largeFile=" + largeFile +
                ", dataFieldLength=" + dataFieldLength +
                ", segmentationControlPreserved=" + segmentationControlPreserved +
                ", entityIdLength=" + entityIdLength +
                ", segmentMetadata=" + segmentMetadata +
                ", transactionSequenceNumberLength=" + transactionSequenceNumberLength +
                ", sourceEntityId=" + sourceEntityId +
                ", transactionSequenceNumber=" + transactionSequenceNumber +
                ", destinationEntityId=" + destinationEntityId +
                ", headerLength=" + headerLength +
                '}';
    }
}
