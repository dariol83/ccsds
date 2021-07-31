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
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;

/**
 * The Transaction.indication primitive shall be used to indicate the Transaction identifier
 * to the source CFDP user.
 *
 * Transaction.indication shall be generated on receipt of a Put.request primitive.
 *
 * The effect on receipt of Transaction.indication by a CFDP user is undefined.
 * The Transaction ID parameter is returned by the CFDP entity and provides the source CFDP
 * user with a means of uniquely identifying the associated transaction thereafter.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.6
 */
public class TransactionIndication implements ICfdpIndication {

    private final long transactionId;

    private final PutRequest originatingRequest;

    private final CfdpTransactionStatus statusReport;

    /**
     * TransactionIndication full constructor.
     *
     * @param transactionId the transaction ID that uniquely identify a single instance of FDU delivery
     * @param originatingRequest the originating {@link PutRequest}
     * @param statusReport The status report provides additional information on some change in the transaction status
     */
    public TransactionIndication(long transactionId, PutRequest originatingRequest, CfdpTransactionStatus statusReport) {
        this.transactionId = transactionId;
        this.originatingRequest = originatingRequest;
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
    public long getTransactionId() {
        return transactionId;
    }

    /**
     * The {@link PutRequest} that originated this transaction.
     *
     * @return the originating {@link PutRequest}
     */
    public PutRequest getOriginatingRequest() {
        return originatingRequest;
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
    public CfdpTransactionStatus getStatusReport() {
        return statusReport;
    }

    @Override
    public String toString() {
        return "TransactionIndication{" +
                "transactionId=" + getTransactionId() +
                ", originatingRequest=" + getOriginatingRequest() +
                ", statusReport=" + getStatusReport() +
                '}';
    }
}
