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
import java.util.function.Function;
import java.util.function.Predicate;

public class PredicateWrapper<T> extends TransformationListProcessor<T, T> {

	public PredicateWrapper(Predicate<T> filter, ExecutorService executor, boolean timely) {
		super(new FilterMap(filter), executor, timely);
	}

	public PredicateWrapper(Predicate<T> filter, boolean timely) {
		this(filter, null, timely);
	}

	public PredicateWrapper(Predicate<T> filter) {
		this(filter, null, false);
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
