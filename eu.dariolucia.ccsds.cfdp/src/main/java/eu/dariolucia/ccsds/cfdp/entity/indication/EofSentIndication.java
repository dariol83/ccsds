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

/**
 * The EOF-Sent.indication primitive shall be used to notify the source CFDP user of the
 * initial transmission of a transaction’s EOF PDU.
 *
 * EOF-Sent.indication shall be generated on initial transmission of an EOF PDU, at the
 * conclusion of initial transmission of a transaction’s metadata and file data.
 *
 * The effect on receipt of EOF-Sent.indication by a CFDP user is undefined.
 *
 * Generation of EOF-Sent.indication is optional. The user may choose to base a flow
 * control system in part on this mechanism, for example, to refrain from submitting a new
 * Put.request primitive until the EOF-Sent.indication resulting from the previous one has
 * been received.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.7
 */
public class EofSentIndication implements ICfdpIndication {

    private final long transactionId;

    private final CfdpTransactionStatus statusReport;

    /**
     * EofSentIndication full constructor.
     *
     * @param transactionId the transaction ID that uniquely identify a single instance of FDU delivery
     * @param statusReport The status report provides additional information on some change in the transaction status
     */
    public EofSentIndication(long transactionId, CfdpTransactionStatus statusReport) {
        this.transactionId = transactionId;
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
        return "EofSentIndication{" +
                "transactionId=" + getTransactionId() +
                ", statusReport=" + getStatusReport() +
                '}';
    }
}
