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

package eu.dariolucia.ccsds.tmtc.coding;

import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * This interface embodies the concept of an encoding function, i.e. a byte[]-to-byte[] transformation function. It is
 * defined as a subtype of the {@link BiFunction} functional interface, restricting the second and third type parameters
 * to byte[] and the first type parameter to a subclass of {@link AbstractTransferFrame}, i.e. the original transfer frame.
 *
 * This interface also provides a default method asFunction, which provides an equivalent {@link Function} object, where
 * the first parameter of supertype {@link AbstractTransferFrame} is set to null.
 *
 * @param <T> a subtype of {@link AbstractTransferFrame}
 */
@FunctionalInterface
public interface IEncodingFunction<T extends AbstractTransferFrame> extends BiFunction<T, byte[], byte[]> {

    default Function<byte[], byte[]> asFunction() {
        return (byte[] input) -> apply(null, input);
    }
}
