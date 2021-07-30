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

package eu.dariolucia.ccsds.cfdp.entity.util;

import eu.dariolucia.ccsds.cfdp.entity.ITransactionIdGenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * This class is internally used to generate transaction IDs in case no external instance of type {@link ITransactionIdGenerator}
 * is provided to the entity.
 */
public class SimpleTransactionIdGenerator implements ITransactionIdGenerator {

    private final AtomicLong transactionIdSequencer = new AtomicLong(0);

    public SimpleTransactionIdGenerator(long startFrom) {
        this.transactionIdSequencer.set(startFrom);
    }

    public SimpleTransactionIdGenerator() {
        this(0L);
    }

    @Override
    public long generateNextTransactionId(long generatingEntityId) {
        return (generatingEntityId << 16) | this.transactionIdSequencer.incrementAndGet(); // 65536 transactions from the same entity ID
    }
}
