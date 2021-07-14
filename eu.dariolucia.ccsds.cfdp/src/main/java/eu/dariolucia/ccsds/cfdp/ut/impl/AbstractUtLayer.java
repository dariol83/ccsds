/*
 *   Copyright (c) 2021 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.cfdp.ut.impl;

import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractUtLayer implements IUtLayer {

    private static final Logger LOG = Logger.getLogger(AbstractUtLayer.class.getName());

    private final Mib mib;

    protected final Map<Long, Boolean> id2txAvailable = new ConcurrentHashMap<>();
    protected final Map<Long, Boolean> id2rxAvailable = new ConcurrentHashMap<>();

    private final List<IUtLayerSubscriber> subscribers = new CopyOnWriteArrayList<>();

    private boolean activated = false;

    public AbstractUtLayer(Mib mib) {
        this.mib = mib;
    }

    public Mib getMib() {
        return mib;
    }

    @Override
    public synchronized void register(IUtLayerSubscriber s) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("UT Layer subscriber %s registering to UT Layer %s", s, getName()));
        }
        this.subscribers.add(s);
        // Inform subscriber
        for(Map.Entry<Long, Boolean> e : this.id2rxAvailable.entrySet()) {
            if(e.getValue()) { // NOSONAR: Boolean cannot be null, see ConcurrentHashMap contract
                s.startRxPeriod(this, e.getKey());
            } else {
                s.endRxPeriod(this, e.getKey());
            }
        }
        for(Map.Entry<Long, Boolean> e : this.id2txAvailable.entrySet()) {
            if(e.getValue()) { // NOSONAR: Boolean cannot be null, see ConcurrentHashMap contract
                s.startTxPeriod(this, e.getKey());
            } else {
                s.endTxPeriod(this, e.getKey());
            }
        }
        handleRegister(s);
    }

    protected void handleRegister(IUtLayerSubscriber s) {
        // Subclasses can override
    }

    @Override
    public synchronized void deregister(IUtLayerSubscriber s) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("UT Layer subscriber %s deregistering from UT Layer %s", s, getName()));
        }
        this.subscribers.remove(s);
        handleDeregister(s);
    }

    protected void handleDeregister(IUtLayerSubscriber s) {
        // Subclasses can override
    }

    @Override
    public synchronized void dispose() {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("Disposing UT Layer %s", getName()));
        }
        // Deactivate first
        if(isActivated()) {
            deactivate();
        }
        //
        handleDispose();
    }

    protected void handleDispose() {
        // Subclasses can override
    }

    protected void notifyPduReceived(CfdpPdu decoded) {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("UT Layer %s received PDU %s", getName(), decoded));
        }
        for(IUtLayerSubscriber s : this.subscribers) {
            try {
                s.indication(this, decoded);
            } catch (Exception e) {
                if(LOG.isLoggable(Level.WARNING)) {
                    LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on PDU received: %s", s, getName(), e.getMessage()), e);
                }
            }
        }
    }

    public synchronized void setTxAvailability(boolean available, long... entityIds) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("Setting TX availability to %s on UT Layer %s for entities %s", String.valueOf(available), getName(), Arrays.toString(entityIds)));
        }
        // Mark the entities as available or unavailable for TX
        for(long l : entityIds) {
            this.id2txAvailable.put(l, available);
            // Propagate
            for(IUtLayerSubscriber s : this.subscribers) {
                try {
                    if(available) {
                        s.startTxPeriod(this, l);
                    } else {
                        s.endTxPeriod(this, l);
                    }
                } catch (Exception e) {
                    if(LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on TX availability: %s", s, getName(), e.getMessage()), e);
                    }
                }
            }
        }
        //
        handleTxAvailability(available, entityIds);
    }

    protected void handleTxAvailability(boolean available, long... entityIds) {
        // Subclasses can override
    }

    public synchronized void setRxAvailability(boolean available, long... entityIds) {
        if(LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, String.format("Setting RX availability to %s on UT Layer %s for entities %s", String.valueOf(available), getName(), Arrays.toString(entityIds)));
        }
        // Mark the entities as available or unavailable for TX
        for(long l : entityIds) {
            this.id2rxAvailable.put(l, available);
            // Propagate
            for(IUtLayerSubscriber s : this.subscribers) {
                try {
                    if(available) {
                        s.startRxPeriod(this, l);
                    } else {
                        s.endRxPeriod(this, l);
                    }
                } catch (Exception e) {
                    if(LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on RX availability: %s", s, getName(), e.getMessage()), e);
                    }
                }
            }
        }
        //
        handleRxAvailability(available, entityIds);
    }

    protected void handleRxAvailability(boolean available, long... entityIds) {
        // Subclasses can override
    }

    @Override
    public synchronized boolean getRxAvailability(long destinationId) {
        Boolean available = this.id2rxAvailable.get(destinationId);
        return Objects.requireNonNullElse(available, false);
    }

    @Override
    public synchronized boolean getTxAvailability(long destinationId) {
        Boolean available = this.id2txAvailable.get(destinationId);
        return Objects.requireNonNullElse(available, false);
    }

    public synchronized void activate() throws UtLayerException { // NOSONAR: exception declaration must stay
        if (LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, String.format("Activating UT Layer %s", getName()));
        }
        if (activated) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("UT Layer %s already active, nothing to be done", getName()));
            }
            return;
        }
        this.activated = true;
        // inform all subscribers of the current state of ID, if any
        for (Map.Entry<Long, Boolean> e : this.id2rxAvailable.entrySet()) {
            for (IUtLayerSubscriber s : this.subscribers) {
                try {
                    if (e.getValue()) { // NOSONAR: Boolean cannot be null, see ConcurrentHashMap contract
                        s.startRxPeriod(this, e.getKey());
                    } else {
                        s.endRxPeriod(this, e.getKey());
                    }
                } catch (Exception ex) {
                    if (LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on RX activation: %s", s, getName(), ex.getMessage()), ex);
                    }
                }
            }
        }
        for (Map.Entry<Long, Boolean> e : this.id2txAvailable.entrySet()) {
            for (IUtLayerSubscriber s : this.subscribers) {
                try {
                    if (e.getValue()) { // NOSONAR: Boolean cannot be null, see ConcurrentHashMap contract
                        s.startTxPeriod(this, e.getKey());
                    } else {
                        s.endTxPeriod(this, e.getKey());
                    }
                } catch (Exception ex) {
                    if (LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on TX activation: %s", s, getName(), ex.getMessage()), ex);
                    }
                }
            }
        }
        // If you have an exception at this stage, deactivate and throw e
        try {
            handleActivate();
        } catch (UtLayerException e) {
            deactivate();
            throw e;
        }
    }

    protected abstract void handleActivate() throws UtLayerException;

    public synchronized void deactivate() {
        if(LOG.isLoggable(Level.INFO)) {
            LOG.log(Level.INFO, String.format("Deactivating UT Layer %s", getName()));
        }
        if (!activated) {
            if (LOG.isLoggable(Level.FINE)) {
                LOG.log(Level.FINE, String.format("UT Layer %s already inactive, nothing to be done", getName()));
            }
            return;
        }
        this.activated = false;
        // inform all subscribers of the unavailability
        for (Map.Entry<Long, Boolean> e : this.id2rxAvailable.entrySet()) {
            for (IUtLayerSubscriber s : this.subscribers) {
                try {
                    s.endRxPeriod(this, e.getKey());
                } catch (Exception ex) {
                    if (LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on RX deactivation: %s", s, getName(), ex.getMessage()), ex);
                    }
                }
            }
        }
        for (Map.Entry<Long, Boolean> e : this.id2txAvailable.entrySet()) {
            for (IUtLayerSubscriber s : this.subscribers) {
                try {
                    s.endTxPeriod(this, e.getKey());
                } catch (Exception ex) {
                    if (LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on TX deactivation: %s", s, getName(), ex.getMessage()), ex);
                    }
                }
            }
        }
        // No exception possible on deactivation here
        handleDeactivate();
    }

    protected abstract void handleDeactivate();

    public synchronized boolean isActivated() {
        return activated;
    }

    @Override
    public void request(CfdpPdu pdu, long destinationEntityId) throws UtLayerException {
        if(LOG.isLoggable(Level.FINEST)) {
            LOG.log(Level.FINEST, String.format("UT Layer %s: requesting transmission of PDU %s to entity %d", getName(), pdu, destinationEntityId));
        }
        // If the destination is not available for TX, exception
        if (!isActivated() || !getTxAvailability(destinationEntityId)) {      // NOSONAR: concurrent hash maps do not accept null values
            throw new UtLayerException(String.format("TX not available for destination entity %d", destinationEntityId));
        }
        RemoteEntityConfigurationInformation conf = getMib().getRemoteEntityById(destinationEntityId);
        if (conf == null) {
            throw new UtLayerException("Cannot retrieve connection information for remote entity " + destinationEntityId);
        }
        // Delegate to subclass
        handleRequest(pdu, conf);
    }

    protected abstract void handleRequest(CfdpPdu pdu, RemoteEntityConfigurationInformation destinationInformation) throws UtLayerException;

}
