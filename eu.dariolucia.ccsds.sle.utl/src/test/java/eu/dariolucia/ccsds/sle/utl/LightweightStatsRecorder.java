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

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafTransferDataInvocation;
import eu.dariolucia.ccsds.sle.utl.si.IServiceInstanceListener;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LightweightStatsRecorder implements IServiceInstanceListener {

    private long pduReceived = 0L;
    private long transferDataBytesReceived = 0L;
    private List<ServiceInstanceState> states = new CopyOnWriteArrayList<>();

    @Override
    public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
        this.states.add(state);
    }

    @Override
    public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
        pduReceived++;
        if(operation instanceof RafTransferDataInvocation) {
            transferDataBytesReceived += ((RafTransferDataInvocation) operation).getData().value.length;
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

    public long getPduReceived() {
        return pduReceived;
    }

    public long getTransferDataBytesReceived() {
        return transferDataBytesReceived;
    }

    public List<ServiceInstanceState> getStates() {
        return states;
    }
}
