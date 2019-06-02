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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Supplier;

public class SupplierWrapper<T> extends SubmissionPublisher<T> {

    private final Supplier<T> supplier;
    private final boolean timely;
    private volatile boolean running;
    private volatile Thread activationThread;

    public SupplierWrapper(Supplier<T> supplier, ExecutorService executor, boolean timely) {
        super(executor, Flow.defaultBufferSize());
        if (supplier == null) {
            throw new NullPointerException("Supplier cannot be null");
        }
        this.running = false;
        this.supplier = supplier;
        this.timely = timely;
    }

    public SupplierWrapper(Supplier<T> supplier, boolean timely) {
        this(supplier, Executors.newFixedThreadPool(1), timely);
    }

    public SupplierWrapper(Supplier<T> supplier) {
        this(supplier, false);
    }

    public void activate(boolean async) {
        if (this.running) {
            throw new IllegalStateException("Publisher already activated");
        }
        this.running = true;
        if (async) {
            this.activationThread = new Thread(this::doActivate);
            this.activationThread.start();
        } else {
            doActivate();
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

    public void deactivate() {
        this.running = false;
        if (this.activationThread != null) {
            this.activationThread.interrupt();
            this.activationThread = null;
        }
    }
}
