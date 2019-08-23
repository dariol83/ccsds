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

package eu.dariolucia.ccsds.tmtc.util.internal;

import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TransformationStreamProcessor<T, K> extends AbstractTransformationProcessor<T, K> {

    public TransformationStreamProcessor(Function<T, Stream<K>> mapper, ExecutorService executor, boolean timely) {
        super(mapper, executor, timely);
    }

    public TransformationStreamProcessor(Function<T, Stream<K>> mapper, boolean timely) {
        this(mapper, null, timely);
    }

    public TransformationStreamProcessor(Function<T, Stream<K>> mapper) {
        this(mapper, false);
    }

    @Override
    public void onNext(T item) {
        if(isRunning()) {
            Stream<K> mapResult = (Stream<K>) getMapper().apply(item);
            forwardToSink(mapResult.collect(Collectors.toList()));
        }
    }
}
