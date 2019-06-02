/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.tmtc.coding.decoder;

import eu.dariolucia.ccsds.tmtc.algorithm.ReedSolomonAlgorithm;
import eu.dariolucia.ccsds.tmtc.coding.IEncodingFunction;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.util.function.Function;

public class ReedSolomonDecoder implements Function<byte[], byte[]> {

    private final ReedSolomonAlgorithm algorithm;

    public ReedSolomonDecoder(ReedSolomonAlgorithm rs) {
        if(rs == null) {
            throw new NullPointerException("Reed-Solomon algorithm cannot be null");
        }
        this.algorithm = rs;
    }

    @Override
    public byte[] apply(byte[] input) {
        if(input == null) {
            throw new NullPointerException("Input cannot be null");
        }
        return this.algorithm.decodeFrame(input);
    }
}
