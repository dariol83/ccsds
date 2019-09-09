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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Supplier;

/**
 * This class allows to adapt a {@link Supplier} implementation into a reactive {@link Flow.Publisher} object.
 *
 * @param <T> the data type being published.
 */
public class SupplierWrapper<T> extends SubmissionPublisher<T> {

    private final Supplier<T> supplier;
    private final boolean timely;
    private volatile boolean running;
    private volatile Thread activationThread;

    /**
     * Create an instance that produces data items using the provided supplier. An {@link ExecutorService} must be
     * provided, as per {@link SubmissionPublisher} constructor. If timely is true, then data items can be discarded if the backlog
     * (per incoming subscription) becomes too large.
     *
     * @param supplier the supplier to use
     * @param executor the {@link ExecutorService} to use, cannot be null or {@link NullPointerException} thrown
     * @param timely true if data can be discarded, otherwise false
     */
    public SupplierWrapper(Supplier<T> supplier, ExecutorService executor, boolean timely) {
        super(executor, Flow.defaultBufferSize());
        if (supplier == null) {
            throw new NullPointerException("Supplier cannot be null");
        }
        this.running = false;
        this.supplier = supplier;
        this.timely = timely;
    }

    /**
     * Create an instance with a fixed thread pool executor, using a single thread.
     *
     * @param supplier the supplier to use
     * @param timely true if data can be discarded, otherwise false
     */
    public SupplierWrapper(Supplier<T> supplier, boolean timely) {
        this(supplier, Executors.newFixedThreadPool(1), timely);
    }

    /**
     * Create an instance with a fixed thread pool executor, using a single thread, which does not discard data (timely set to false).
     *
     * @param supplier the supplier to use
     */
    public SupplierWrapper(Supplier<T> supplier) {
        this(supplier, false);
    }

    /**
     * This method starts reading data from the underlying supplier:
     * <ul>
     *     <li>If async is true, a new thread is started and this method returns immediately</li>
     *     <li>If async is false, the reading of data items from the supplier is performed by the same thread that invoked this method</li>
     * </ul>
     *
     * @param async true if a new reading thread must be spawned, false if the reading shall be performed by the calling thread
     */
    public void activate(boolean async) {
        if (this.running) {
            throw new IllegalStateException("Publisher already activated");
        }
        this.running = true;
        if (async) {
            this.activationThread = new Thread(this::doActivate);
            this.activationThread.start();
        } else {
            this.activationThread = Thread.currentThread();
            doActivate();
            this.activationThread = null;
        }
    }

    private void doActivate() {
        T item = null;
        while (this.running) {
            item = this.supplier.get();
            if (item == null) {
                // End of data
                close();
                return;
            } else {
                if (timely) {
                    offer(item, null);
                } else {
                    submit(item);
                }
            }
        }
    }

    /**
     * This method sets the state of the object to stop reading from the supplier. The reading thread is interrupted.
     */
    public void deactivate() {
        this.running = false;
        if (this.activationThread != null) {
            this.activationThread.interrupt();
            this.activationThread = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplierWrapper<?> that = (SupplierWrapper<?>) o;
        return supplier.equals(that.supplier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supplier);
    }
}
