/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl;

import eu.dariolucia.ccsds.sle.utl.si.IServiceInstanceListener;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;

/**
 * This listener processes the incoming SLE PDU very slowly, simulating a slow processing facility to investigate backpressure
 * propagation effects to the server side.
 */
public class SlowSleListener implements IServiceInstanceListener {

    private volatile int slowDownMs;

    public SlowSleListener(int slowDownMs) {
        this.slowDownMs = slowDownMs;
    }

    public void setSlowDownMs(int slowDownMs) {
        this.slowDownMs = slowDownMs;
    }

    @Override
    public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
        // Nothing
    }

    @Override
    public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
        try {
            Thread.sleep(slowDownMs);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
        // Ignore for now
    }

    @Override
    public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error, Exception exception) {
        // Ignore for now
    }

    @Override
    public void onPduDecodingError(ServiceInstance serviceInstance, byte[] encodedOperation) {
        // Ignore for now
    }

    @Override
    public void onPduHandlingError(ServiceInstance serviceInstance, Object operation, byte[] encodedOperation) {
        // Ignore for now
    }
}
