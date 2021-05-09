package eu.dariolucia.ccsds.cfdp.entity.indication;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.MessageToUserTLV;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * The Metadata-Recv.indication primitive shall be used to indicate to the destination
 * CFDP user that the metadata associated with a transaction has been received.
 *
 * Metadata-Recv.indication is generated on receipt of a Metadata PDU.
 *
 * The effect on receipt of Metadata-Recv.indication by the destination CFDP user is
 * undefined.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.9
 */
public class MetadataRecvIndication implements ICfdpIndication {

    /**
     * The Transaction ID parameter shall uniquely identify a single instance of FDU
     * delivery and shall contain the ID of the source CFDP entity together with a sequence number
     * that is specific to that entity.
     *
     * At any moment, any given transaction ID is unique within the CFDP addressing
     * domain that encompasses the source CFDP entity.
     */
    private final long transactionId;

    /**
     * The source CFDP entity ID parameter shall identify the CFDP entity from which the
     * FDU is to be sent.
     */
    private final long sourceEntityId;

    /**
     * Optionally present (-1 means not present). The file size parameter shall indicate the length in octets of the file that is being
     * conveyed by means of the indicated transaction.
     */
    private final long fileSize;

    /**
     * Optionally present. The source file name parameter
     * a) shall contain the full path name at which the file to be copied is located at the
     * filestore associated with the source entity;
     * b) shall be omitted when the FDU to be Put contains only metadata, such as a message
     * to a user or a standalone filestore request.
     */
    private final String sourceFileName;

    /**
     * Optionally present. The destination file name parameter
     * a) shall contain the full path name to which the file to be copied will be placed at the
     * filestore associated with the destination entity;
     * b) shall be omitted when the FDU to be Put contains only metadata, such as a message
     * to a user or a standalone filestore request.
     */
    private final String destinationFileName;

    /**
     * Optionally present. If included, the optional Messages to User parameter shall be transmitted at the
     * beginning of the transaction and delivered to the destination CFDP user upon receipt. Certain
     * messages are defined in the User Operations section to allow remote initiation of CFDP
     * transactions.
     */
    private final List<MessageToUserTLV> messagesToUser = new LinkedList<>();

    /**
     * MetadataRecvIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param sourceEntityId The entity ID that generated the {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.MetadataPdu}
     * @param fileSize The size of the file, if present. To be set to -1 if not present
     * @param sourceFileName Can be null. The name of the source file.
     * @param destinationFileName Can be null. The name of the destination file.
     * @param messagesToUser Can be null.
     */
    public MetadataRecvIndication(long transactionId, long sourceEntityId, long fileSize, String sourceFileName, String destinationFileName, List<MessageToUserTLV> messagesToUser) {
        this.transactionId = transactionId;
        this.sourceEntityId = sourceEntityId;
        this.fileSize = fileSize;
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
        if (messagesToUser != null) {
            this.messagesToUser.addAll(messagesToUser);
        }
    }

    public long getTransactionId() {
        return transactionId;
    }

    public long getSourceEntityId() {
        return sourceEntityId;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }

    public List<MessageToUserTLV> getMessagesToUser() {
        return Collections.unmodifiableList(messagesToUser);
    }

    @Override
    public String toString() {
        return "MetadataRecvIndication{" +
                "transactionId=" + transactionId +
                ", sourceEntityId=" + sourceEntityId +
                ", fileSize=" + fileSize +
                ", sourceFileName='" + sourceFileName + '\'' +
                ", destinationFileName='" + destinationFileName + '\'' +
                ", messagesToUser=" + messagesToUser +
                '}';
    }
}
