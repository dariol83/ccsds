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

package eu.dariolucia.ccsds.cfdp.entity.indication;

import eu.dariolucia.ccsds.cfdp.entity.CfdpTransactionStatus;
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
public class MetadataRecvIndication implements ICfdpTransactionIndication {

    private final long transactionId;

    private final long sourceEntityId;

    private final long fileSize;

    private final String sourceFileName;

    private final String destinationFileName;

    private final List<MessageToUserTLV> messagesToUser = new LinkedList<>();

    private final CfdpTransactionStatus statusReport;

    /**
     * MetadataRecvIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param sourceEntityId The entity ID that generated the {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.MetadataPdu}
     * @param fileSize The size of the file, if present. To be set to -1 if not present
     * @param sourceFileName Can be null. The name of the source file.
     * @param destinationFileName Can be null. The name of the destination file.
     * @param messagesToUser Can be null.
     * @param statusReport The status report provides additional information on some change in the transaction status
     */
    public MetadataRecvIndication(long transactionId, long sourceEntityId, long fileSize, String sourceFileName, String destinationFileName, List<MessageToUserTLV> messagesToUser, CfdpTransactionStatus statusReport) {
        this.transactionId = transactionId;
        this.sourceEntityId = sourceEntityId;
        this.fileSize = fileSize;
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
        if (messagesToUser != null) {
            this.messagesToUser.addAll(messagesToUser);
        }
        this.statusReport = statusReport;
    }

    /**
     * The Transaction ID parameter shall uniquely identify a single instance of FDU
     * delivery and shall contain the ID of the source CFDP entity together with a sequence number
     * that is specific to that entity.
     *
     * At any moment, any given transaction ID is unique within the CFDP addressing
     * domain that encompasses the source CFDP entity.
     *
     * @return the transaction ID
     */
    @Override
    public long getTransactionId() {
        return transactionId;
    }

    /**
     * The source CFDP entity ID parameter shall identify the CFDP entity from which the
     * FDU is to be sent.
     *
     * @return the source entity ID
     */
    public long getSourceEntityId() {
        return sourceEntityId;
    }

    /**
     * Optionally present (-1 means not present). The file size parameter shall indicate the length in octets of the file that is being
     * conveyed by means of the indicated transaction.
     *
     * @return the file size in bytes, or -1 if not present
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * Optionally present. The source file name parameter
     * <ol>
     * <li>shall contain the full path name at which the file to be copied is located at the
     * filestore associated with the source entity;</li>
     * <li>b) shall be omitted when the FDU to be Put contains only metadata, such as a message
     * to a user or a standalone filestore request.</li>
     * </ol>
     * @return the full path name of the source file, or null if not present
     */
    public String getSourceFileName() {
        return sourceFileName;
    }

    /**
     * Optionally present. The destination file name parameter
     * <ol>
     * <li>shall contain the full path name to which the file to be copied will be placed at the
     * filestore associated with the destination entity;</li>
     * <li>shall be omitted when the FDU to be Put contains only metadata, such as a message
     * to a user or a standalone filestore request.</li>
     * </ol>
     * @return the full path name of the destination file, or null if not present
     */
    public String getDestinationFileName() {
        return destinationFileName;
    }

    /**
     * Optionally present. If included, the optional Messages to User parameter shall be transmitted at the
     * beginning of the transaction and delivered to the destination CFDP user upon receipt. Certain
     * messages are defined in the User Operations section to allow remote initiation of CFDP
     * transactions.
     *
     * @return the list of messages to user (can be empty, it is never null)
     */
    public List<MessageToUserTLV> getMessagesToUser() {
        return Collections.unmodifiableList(messagesToUser);
    }

    /**
     * The Status report parameter shall indicate the status of the indicated file delivery
     * transaction. The format and scope of the status report parameter are specific to the
     * implementation. It could contain information such as:
     * <ol>
     *     <li>whether the transaction is finished, canceled, suspended, or active;</li>
     *     <li>what extents of the FDU are known to have been successfully received by the
     * receiving CFDP entity;</li>
     *     <li>what extents of the FDU are known to have been transmitted by the sending CFDP
     * entity.</li>
     * </ol>
     *
     * @return the status report
     */
    @Override
    public CfdpTransactionStatus getStatusReport() {
        return statusReport;
    }

    @Override
    public String toString() {
        return "MetadataRecvIndication{" +
                "transactionId=" + getTransactionId() +
                ", sourceEntityId=" + getSourceEntityId() +
                ", fileSize=" + getFileSize() +
                ", sourceFileName='" + getSourceFileName() + '\'' +
                ", destinationFileName='" + getDestinationFileName() + '\'' +
                ", messagesToUser=" + messagesToUser +
                ", statusReport=" + getStatusReport() +
                '}';
    }
}
