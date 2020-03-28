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

package eu.dariolucia.ccsds.sle.utl.provider;

import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rcf.RcfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rocf.RocfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.pdu.PduStringUtil;
import eu.dariolucia.ccsds.sle.utl.si.IServiceInstanceListener;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceBindingStateEnum;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

public abstract class ServiceInstanceManager<T extends ServiceInstance> implements IServiceInstanceListener {

    protected static final Logger LOG = Logger.getLogger(ServiceInstanceManager.class.getName());

    public static ServiceInstanceManager build(ServiceInstanceConfiguration configuration, PeerConfiguration peerConfiguration) {
        if(configuration instanceof RafServiceInstanceConfiguration) {
            return new RafServiceManager(configuration, peerConfiguration);
        }
        if(configuration instanceof RcfServiceInstanceConfiguration) {
            return new RcfServiceManager(configuration, peerConfiguration);
        }
        if(configuration instanceof RocfServiceInstanceConfiguration) {
            return new RocfServiceManager(configuration, peerConfiguration);
        }
        if(configuration instanceof CltuServiceInstanceConfiguration) {
            return new CltuServiceManager(configuration, peerConfiguration);
        }
        throw new UnsupportedOperationException("Configuration type " + configuration + " not supported");
    }

    protected Timer timer = new Timer();
    protected T serviceInstance;

    public ServiceInstanceManager(ServiceInstanceConfiguration configuration, PeerConfiguration peerConfiguration) {
        this.serviceInstance = createServiceInstance(configuration, peerConfiguration);
    }

    public void activate() {
        serviceInstance.configure();
        this.serviceInstance.register(this);
        postActivation();
        this.serviceInstance.waitForBind(true, null);
        LOG.info(this.serviceInstance.getServiceInstanceIdentifier() + " started");
    }

    protected abstract T createServiceInstance(ServiceInstanceConfiguration configuration, PeerConfiguration peerConfiguration);

    protected abstract void postActivation();

    @Override
    public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
        if(state.getState() == ServiceInstanceBindingStateEnum.UNBOUND) {
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if(serviceInstance.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND) {
                        serviceInstance.waitForBind(true, null);
                    }
                }
            }, 2000); // Retry after 2 seconds
        }
    }

    @Override
    public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
        LOG.finer(si.getServiceInstanceIdentifier() + ": " + name + " received");
    }

    @Override
    public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
        LOG.finer(si.getServiceInstanceIdentifier() + ": " + name + " sent");
    }

    @Override
    public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error, Exception exception) {
        LOG.warning(si.getServiceInstanceIdentifier() + ": " + name + " sent error: " + error);
    }

    @Override
    public void onPduDecodingError(ServiceInstance si, byte[] encodedOperation) {
        LOG.warning(si.getServiceInstanceIdentifier() + ": decoding error " + PduStringUtil.toHexDump(encodedOperation));
    }

    @Override
    public void onPduHandlingError(ServiceInstance si, Object operation, byte[] encodedOperation) {
        LOG.warning(si.getServiceInstanceIdentifier() + ": handling error " + PduStringUtil.toHexDump(encodedOperation));
    }
}
