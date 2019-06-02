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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;

public class ConsumerWrapper<T> implements Flow.Subscriber<T> {

    private final Function<Consumer<T>, Integer> backlogProvider;
    private final Consumer<T> delegate;
    private volatile Flow.Subscription subscription;

    public ConsumerWrapper(Consumer<T> delegate) {
        this(delegate, null);
    }

    public ConsumerWrapper(Consumer<T> delegate, Function<Consumer<T>, Integer> backlogProvider) {
        if(delegate == null) {
            throw new NullPointerException("Consumer shall not be null");
        }

        this.delegate = delegate;
        this.backlogProvider = backlogProvider;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        if(this.backlogProvider == null) {
            subscription.request(Integer.MAX_VALUE);
        } else {
            Integer requests = this.backlogProvider.apply(this.delegate);
            subscription.request(requests);
        }
    }

    @Override
    public void onNext(T item) {
        this.delegate.accept(item);
        if(this.backlogProvider != null) {
            Integer requests = this.backlogProvider.apply(this.delegate);
            if(requests != null && this.subscription != null) {
                this.subscription.request(requests);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        //
    }

    @Override
    public void onComplete() {
        //
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumerWrapper<T> that = (ConsumerWrapper<T>) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }
}
