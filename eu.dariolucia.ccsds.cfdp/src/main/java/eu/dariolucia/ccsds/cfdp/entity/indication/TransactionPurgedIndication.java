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

import java.util.List;

/**
 * The TransactionPurged.indication primitive is not part of the standard and it is introduced by this implementation
 * to notify subscribers that a given CFDP entity dropped some transaction history as part of its cleanup configuration.
 */
public class TransactionPurgedIndication implements ICfdpIndication {

    private final List<Long> purgedTransactionIds;

    public TransactionPurgedIndication(List<Long> purgedTransactionIds) {
        this.purgedTransactionIds = List.copyOf(purgedTransactionIds);
    }

    public List<Long> getPurgedTransactionIds() {
        return purgedTransactionIds;
    }

    @Override
    public String toString() {
        return "TransactionPurgedIndication{" +
                "purgedTransactionIds=" + purgedTransactionIds +
                '}';
    }
}
