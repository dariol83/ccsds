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

package eu.dariolucia.ccsds.cfdp.entity.request;

/**
 * The KeepAlive Request is a request not part of the standard.
 *
 * It is used to cover the following clause:
 *
 * 4.6.4.5 Incremental Lost Segment Detection Procedures at the Sending Entity
 * In response to an implementation-specific external event, the sending CFDP entity may issue
 * a Prompt (NAK) PDU.
 */
public class PromptNakRequest implements ICfdpRequest {

    private final long transactionId;

    /**
     * PromptNakRequest full constructor.
     *
     * @param transactionId the transaction ID that uniquely identify a single instance of FDU delivery
     */
    public PromptNakRequest(long transactionId) {
        this.transactionId = transactionId;
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

    @Override
    public String toString() {
        return "PromptNakRequest{" +
                "transactionId=" + transactionId +
                '}';
    }
}
