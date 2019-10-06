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

package eu.dariolucia.ccsds.tmtc.util;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This class contains a set of utility functions to work with {@link Stream} objects.
 */
public class StreamUtil {

    private StreamUtil() {
        // Private constructor
    }

    /**
     * Utility function to convert a {@link Supplier} function into a {@link Stream}.
     * When the supplier returns null, the stream stops.
     *
     * @param s   the supplier that generates objects
     * @param <T> the object type returned by the stream
     * @return a stream that stops as soon as the supplier returns null
     */
    public static <T> Stream<T> from(Supplier<T> s) {
        return StreamSupport.stream(
                new Spliterators.AbstractSpliterator<T>(Long.MAX_VALUE, Spliterator.SIZED) {
                    public boolean tryAdvance(Consumer<? super T> action) {
                        T item = s.get();
                        if (item == null) {
                            return false;
                        } else {
                            action.accept(item);
                            return true;
                        }
                    }
                }, false);
    }
}
