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

package eu.dariolucia.ccsds.sle.provider;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuThrowEventInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuTransferDataInvocation;
import eu.dariolucia.ccsds.sle.server.CltuServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.server.RafServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceBindingStateEnum;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuProductionStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuUplinkStatusEnum;

import java.time.Instant;
import java.util.Date;
import java.util.TimerTask;

public class CltuServiceManager extends ServiceInstanceManager<CltuServiceInstanceProvider> {

    private static final int FULL_BUFFER_SIZE = 15000;

    private volatile int availableBuffer = FULL_BUFFER_SIZE;

    public CltuServiceManager(ServiceInstanceConfiguration configuration, PeerConfiguration peerConfiguration) {
        super(configuration, peerConfiguration);
    }

    @Override
    protected CltuServiceInstanceProvider createServiceInstance(ServiceInstanceConfiguration configuration, PeerConfiguration peerConfiguration) {
        return new CltuServiceInstanceProvider(peerConfiguration, (CltuServiceInstanceConfiguration) configuration);
    }

    @Override
    protected void postActivation() {
        serviceInstance.setStartOperationHandler((o) -> true);
        serviceInstance.setUnbindReturnBehaviour(true);
        serviceInstance.setThrowEventOperationHandler(this::throwEventHandler);
        serviceInstance.setTransferDataOperationHandler(this::transferDataHandler);
        serviceInstance.updateProductionStatus(CltuProductionStatusEnum.OPERATIONAL, CltuUplinkStatusEnum.NOMINAL, availableBuffer);
    }

    private Long transferDataHandler(CltuTransferDataInvocation cltuTransferDataInvocation) {
        byte[] cltu = cltuTransferDataInvocation.getCltuData().value;
        long id = cltuTransferDataInvocation.getCltuIdentification().longValue();
        LOG.info(serviceInstance.getServiceInstanceIdentifier() + ": Transfer data received: " + cltu.length + " bytes with id " + id);
        if(availableBuffer - cltu.length < 0) {
            // Buffer full: reject with whatever error: not sure if this is appropriate but this is a protocol test tool
            return -1L;
        } else {
            // Decrease the buffer size
            this.availableBuffer -= cltu.length;
            // Schedule the radiation
            this.timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Date radStarted = new Date();
                    availableBuffer += cltu.length;
                    serviceInstance.cltuProgress(id, CltuStatusEnum.PRODUCTION_STARTED, radStarted, null, availableBuffer);
                    if(availableBuffer == FULL_BUFFER_SIZE) {
                        serviceInstance.bufferEmpty(availableBuffer);
                    }
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException e) {
                        // Whatever
                    }
                    serviceInstance.cltuProgress(id, CltuStatusEnum.RADIATED, radStarted, new Date(), availableBuffer);
                }
            }, 800); // 800 ms for radiation
            return (long) availableBuffer;
        }
    }

    private Long throwEventHandler(CltuThrowEventInvocation cltuThrowEventInvocation) {
        long event = cltuThrowEventInvocation.getEventIdentifier().longValue();
        long id = cltuThrowEventInvocation.getEventInvocationIdentification().longValue();
        LOG.info(serviceInstance.getServiceInstanceIdentifier() + ": Throw event received: " + event + " with id " + id);
        if(event > 10) {
            return 1L; // Unknown event
        }
        // Schedule the execution
        this.timer.schedule(new TimerTask() {
            @Override
            public void run() {
                serviceInstance.eventProgress(id, false, true);
            }
        }, 1000); // 1000 ms for processing
        return null;
    }
}
