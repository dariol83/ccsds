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

package eu.dariolucia.ccsds.tmtc.util.processor;

import eu.dariolucia.ccsds.tmtc.util.internal.TransformationListProcessor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This class allows to adapt a {@link Predicate} implementation into a reactive {@link Flow.Processor} object.
 *
 * @param <T> the data type being filtered.
 */
public class PredicateWrapper<T> extends TransformationListProcessor<T, T> {

	/**
	 * Create an instance that filters incoming data items using the provided function. If the {@link ExecutorService} is
	 * provided, then the instance works asynchronously. If timely is true, then data items can be discarded if the backlog
	 * (per incoming subscription) becomes too large.
	 *
	 * @param filter the filter to use
	 * @param executor the {@link ExecutorService} to use, can be null
	 * @param timely true if data can be discarded, otherwise false
	 */
	public PredicateWrapper(Predicate<T> filter, ExecutorService executor, boolean timely) {
		super(new FilterMap<>(filter), executor, timely);
	}

	/**
	 * Create an instance that filters incoming data items using the provided function. Data is processed synchronously using
	 * the same thread that notifies it. If timely is true, then data items can be discarded if the backlog
	 * becomes too large.
	 *
	 * @param filter the filter to use
	 * @param timely true if data can be discarded, otherwise false
	 */
	public PredicateWrapper(Predicate<T> filter, boolean timely) {
		this(filter, null, timely);
	}

	/**
	 * Create an instance that filters incoming data items using the provided function. Data is processed synchronously using
	 * the same thread that notifies it. Data is never discarded.
	 *
	 * @param filter the filter to use
	 */
	public PredicateWrapper(Predicate<T> filter) {
		this(filter, false);
	}

	private static class FilterMap<T> implements Function<T, List<T>> {

		private Predicate<T> filter;

		public FilterMap(Predicate<T> filter) {
			this.filter = filter;
		}

		@Override
		public List<T> apply(T t) {
			if(this.filter.test(t)) {
				return Collections.singletonList(t);
			} else {
				return Collections.emptyList();
			}
		}
	}
}
