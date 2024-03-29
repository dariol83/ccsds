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

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * A processor that transform input data of type T into an output data of type K via the provided {@link Function}.
 * Each element of the returned list is processed.
 *
 * @param <T> the input type
 * @param <K> the output type
 */
public class TransformationListProcessor<T,K> extends AbstractTransformationProcessor<T,K> {

    public TransformationListProcessor(Function<T, ? extends Collection<K>> mapper, ExecutorService executor, boolean timely) {
        super(mapper, executor, timely);
    }

    @Override
    public void onNext(T item) {
        if(isRunning()) {
            Collection<K> mapResult = (Collection<K>) getMapper().apply(item);
            forwardToSink(mapResult);
        }
    }

}
