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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class allows to adapt a {@link Consumer} implementation into a reactive {@link Flow.Subscriber} object.
 *
 * @param <T> the data type being consumed.
 */
public class ConsumerWrapper<T> implements Flow.Subscriber<T> {

    private final Function<Consumer<T>, Integer> backlogProvider;
    private final Consumer<T> delegate;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

    /**
     * Create a {@link ConsumerWrapper} that requests Integer.MAX elements when the subscription is performed. Data
     * received by the subscription is forwarded to the {@link Consumer} delegate.
     *
     * @param delegate the object that will ultimately receive all the data items received from the subscription
     */
    public ConsumerWrapper(Consumer<T> delegate) {
        this(delegate, null);
    }

    /**
     * Create a {@link ConsumerWrapper} that requests a specific number of elements when the subscription is performed. Data
     * received by the subscription is forwarded to the {@link Consumer} delegate. The exact number of elements to be
     * requested is obtained by using the {@link Function} backlogProvider.
     *
     * @param delegate the object that will ultimately receive all the data items received from the subscription
     * @param backlogProvider the function responsible to compute the number of requests that the {@link ConsumerWrapper}
     *                        will perform upon registration and upon reception of a new data item
     */
    public ConsumerWrapper(Consumer<T> delegate, Function<Consumer<T>, Integer> backlogProvider) {
        if(delegate == null) {
            throw new NullPointerException("Consumer shall not be null");
        }

        this.delegate = delegate;
        this.backlogProvider = backlogProvider;
    }

    /**
     * This method registers the subscription and requests the next items to it.
     *
     * @param subscription the subscription
     */
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription.set(subscription);
        if(this.backlogProvider == null) {
            subscription.request(Integer.MAX_VALUE);
        } else {
            Integer requests = this.backlogProvider.apply(this.delegate);
            subscription.request(requests);
        }
    }

    /**
     * This method forwards the received item to the registered delegate and, if there is a backlogProvider set,
     * it will query it and, should the backlogProvider return a non-null Integer, the request(...) method will be called
     * again on the subscription.
     *
     * @param item the item to be forwarded
     */
    @Override
    public void onNext(T item) {
        this.delegate.accept(item);
        if(this.backlogProvider != null) {
            Integer requests = this.backlogProvider.apply(this.delegate);
            Flow.Subscription theSubscription = this.subscription.get();
            if(requests != null && theSubscription != null) {
                theSubscription.request(requests);
            }
        }
    }

    /**
     * This method does nothing.
     *
     * @param throwable the raised Throwable
     */
    @Override
    public void onError(Throwable throwable) {
        //
    }

    /**
     * This method does nothing.
     */
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
