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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.function.Function;

/**
 * This class is an implementation of the Flow.Processor interface as specified by the {@link Flow} reactive programming
 * specifications. It can handle several subscriptions, it supports asynchronous as well as synchronous data processing and dispatch,
 * and also a timely mode that allows subscriptions to discard old data, if their buffer becomes full.
 *
 * The provision of the executor service drives the functionality of the {@link TransformationProcessor}: if the executor
 * is provided, then the transformation and forwarding of the transformed output is done asynchronously by the executor.
 * If the executor is not provided, then the transformation and forwarding of the transformed output is performed by the same
 * thread that injects the data into the processor.
 *
 * By default, the {@link TransformationProcessor} does not drop any injected byte[]. If the configuration is changed to work
 * in timely mode, if the internal subscription buffer (driven by the subscription request, as per reactive programming) becomes
 * full, then the complete buffer is discarded.
 *
 * @param <T> input type
 * @param <K> output type
 */
public abstract class AbstractTransformationProcessor<T,K> implements Flow.Processor<T,K> {

    private final List<TransformationSubscription> sink = new CopyOnWriteArrayList<>();

    private final Function mapper;

    private final ExecutorService executor;

    private final boolean timely;

    private boolean running;

    /**
     * Construct a processor to transform data.
     *
     * @param mapper the function, converting from an element T to an element K, it cannot be null
     * @param executor the {@link ExecutorService} used to perform the function: if null, the same thread used to inject the input will be used to compute the output
     * @param timely if true, data is allowed to be discarded in case of backpressure. If no data should be discarded, set it to false
     */
    public AbstractTransformationProcessor(Function mapper, ExecutorService executor, boolean timely) {
        if(mapper == null) {
            throw new NullPointerException("Data mapper cannot be null");
        }
        this.running = true;
        this.mapper = mapper;
        this.executor = executor;
        this.timely = timely;
    }

    public boolean isRunning() {
        return running;
    }

    protected Function getMapper() {
        return mapper;
    }

    protected void forwardToSink(Collection<K> data) {
        this.sink.forEach(o -> o.forwardItem(data));
    }

    @Override
    public void subscribe(Flow.Subscriber<? super K> subscriber) {
        if(subscriber == null) {
            throw new NullPointerException("Subscriber shall not be null");
        }
        // Make subscription
        TransformationSubscription ces = new TransformationSubscription(subscriber);
        if(this.sink.contains(ces)) {
            subscriber.onError(new IllegalStateException("Already subscribed"));
            return;
        }
        // Add to the list of subscribers
        this.sink.add(ces);
        // Inform the subscriber that it was correctly registered
        subscriber.onSubscribe(ces);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        // No flow control
        subscription.request(Long.MAX_VALUE);
        // No propagation to subscribers, they are already subscribed
    }

    @Override
    public void onError(Throwable throwable) {
        if(this.running) {
            this.sink.forEach(o -> o.forwardError(throwable));
        }
        this.running = false;
    }

    @Override
    public void onComplete() {
        if(this.running) {
            this.sink.forEach(TransformationSubscription::forwardComplete);
        }
        this.running = false;
    }

    private class TransformationSubscription implements Flow.Subscription {

        // The subscription is activated only when a request(...) invocation is performed.
        // If the invocation is done with Long.MAX_VALUE, the subscriber is effectively asking
        // for all data with no flow control.
        private long toBeSent = 0;

        private List<K> bufferedItems = new LinkedList<>();

        private volatile boolean active = true;

        private final Flow.Subscriber<? super K> subscriber;

        public TransformationSubscription(Flow.Subscriber<? super K> subscriber) {
            this.subscriber = subscriber;
        }

        public synchronized void forwardItem(Collection<K> data) {
            if(!active) {
                return;
            }
            // Add the item to the queue
            bufferedItems.addAll(data);
            // Forward the item
            if(executor == null) {
                doForwardItems();
            } else {
                executor.submit(this::doForwardItems);
            }
        }

        private synchronized void doForwardItems() {
            if(!active) {
                return;
            }

            // Forward as much data as you can: since this object is fully synchronized, we should not fear any
            // modification of the queue while we do this operation.
            while (!this.bufferedItems.isEmpty() && (this.toBeSent == Long.MAX_VALUE || (--this.toBeSent) >= 0)) {
                this.subscriber.onNext(this.bufferedItems.remove(0));
            }

            if(timely) {
                // If the processor works in timely behaviour, in case there are no pending requests from the
                // subscriber, remaining items in the temporary buffer can be safely dropped.
                this.bufferedItems.clear();
            }
        }

        public synchronized void forwardError(Throwable throwable) {
            if(executor == null) {
                doForwardError(throwable);
            } else {
                executor.submit(() -> doForwardError(throwable));
            }
        }

        private synchronized void doForwardError(Throwable throwable) {
            if(!active) {
                return;
            }
            this.active = false;
            this.subscriber.onError(throwable);
            cancel();
        }

        public synchronized void forwardComplete() {
            if(executor == null) {
                doForwardComplete();
            } else {
                executor.submit(this::doForwardComplete);
            }
        }

        private synchronized void doForwardComplete() {
            if(!active) {
                return;
            }
            this.active = false;
            this.subscriber.onComplete();
            cancel();
        }

        @Override
        public synchronized void request(long n) {
            if(!active) {
                return;
            }
            if(n <= 0) {
                this.active = false;
                this.subscriber.onError(new IllegalArgumentException("Number of received requests is <= 0"));
                cancel();
            } else {
                if(this.toBeSent != Long.MAX_VALUE) {
                    this.toBeSent += n;
                }
                // Forward the item
                if(executor == null) {
                    doForwardItems();
                } else {
                    executor.submit(this::doForwardItems);
                }
            }
        }

        @Override
        public synchronized void cancel() {
            this.active = false;
            this.bufferedItems.clear();
            AbstractTransformationProcessor.this.sink.remove(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TransformationSubscription that = (TransformationSubscription) o;
            return Objects.equals(subscriber, that.subscriber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subscriber);
        }

        public boolean isActive() {
            return active;
        }
    }

}
