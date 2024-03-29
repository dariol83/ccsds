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
 * The Suspend.request primitive shall be used to request that a transaction be suspended.
 *
 * Suspend.request is generated by any CFDP user at any time during the lifetime of any
 * transaction, except in the case of a transaction sent in unacknowledged mode, for which it
 * can only be generated by the transaction’s source user.
 *
 * Receipt of Suspend.request shall cause a Notice of Suspension at the local entity.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.3
 */
public class SuspendRequest implements ICfdpRequest {

    private final long transactionId;

    /**
     * SuspendRequest full constructor.
     *
     * @param transactionId the transaction ID that uniquely identify a single instance of FDU delivery
     */
    public SuspendRequest(long transactionId) {
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
        return "SuspendRequest{" +
                "transactionId=" + transactionId +
                '}';
    }
}
