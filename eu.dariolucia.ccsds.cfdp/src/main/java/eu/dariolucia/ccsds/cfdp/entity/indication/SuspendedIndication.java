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

/**
 * The Suspended.indication primitive shall be used to indicate to the CFDP user that the
 * transaction has been suspended.
 *
 * Suspended.indication is generated on Notice of Suspension of a file transmission
 * procedure.
 *
 * The effect on receipt of Suspended.indication by a CFDP user is undefined.
 *
 * Ref. CCSDS 727.0-B-5, 3.4.11
 */
public class SuspendedIndication implements ICfdpIndication {

    private final long transactionId;

    private final byte conditionCode;

    /**
     * SuspendedIndication full constructor.
     *
     * @param transactionId The Transaction ID parameter shall uniquely identify a single instance of FDU delivery
     * @param conditionCode The Condition code parameter shall provide additional information on some change in the transaction status
     */
    public SuspendedIndication(long transactionId, byte conditionCode) {
        this.transactionId = transactionId;
        this.conditionCode = conditionCode;
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
     * The Condition code parameter shall provide additional information on some change
     * in transaction status.
     *
     * @return the condition code
     */
    public byte getConditionCode() {
        return conditionCode;
    }

    @Override
    public String toString() {
        return "SuspendedIndication{" +
                "transactionId=" + getTransactionId() +
                ", conditionCode=" + getConditionCode() +
                '}';
    }
}
