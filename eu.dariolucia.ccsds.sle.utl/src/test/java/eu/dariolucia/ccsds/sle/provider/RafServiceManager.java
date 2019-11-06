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

import eu.dariolucia.ccsds.sle.server.RafServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuProductionStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuUplinkStatusEnum;

import java.time.Instant;
import java.util.TimerTask;

public class RafServiceManager extends ServiceInstanceManager<RafServiceInstanceProvider> {

    private TimerTask frameGenerationTask;
    private byte[] frameToSend;

    public RafServiceManager(ServiceInstanceConfiguration configuration, PeerConfiguration peerConfiguration) {
        super(configuration, peerConfiguration);
    }

    @Override
    protected RafServiceInstanceProvider createServiceInstance(ServiceInstanceConfiguration configuration, PeerConfiguration peerConfiguration) {
        return new RafServiceInstanceProvider(peerConfiguration, (RafServiceInstanceConfiguration) configuration);
    }

    @Override
    protected void postActivation() {
        serviceInstance.setStartOperationHandler((o) -> true);
        serviceInstance.setUnbindReturnBehaviour(true);
        frameToSend = new byte[1115];
        serviceInstance.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, ProductionStatusEnum.RUNNING);
    }

    @Override
    public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
        super.onStateUpdated(si, state);
        if(state.getState() == ServiceInstanceBindingStateEnum.ACTIVE) {
            // Start generation thread
            startFrameGeneration();
        } else {
            // Stop generation thread
            stopFrameGeneration();
        }
    }

    private void stopFrameGeneration() {
        if(frameGenerationTask != null) {
            frameGenerationTask.cancel();
            frameGenerationTask = null;
        }
    }

    private void startFrameGeneration() {
        if(frameGenerationTask == null) {
            frameGenerationTask = new TimerTask() {
                @Override
                public void run() {
                    serviceInstance.transferData(frameToSend, 0, 0, Instant.now(), false, "AABBCCDDEE", false, new byte[] { 0, 1, 2, 3 });
                }
            };
            super.timer.schedule(frameGenerationTask, 0, 50); // 20 frames per second
        }
    }
}
