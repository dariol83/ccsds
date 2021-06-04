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
import eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayer;
import eu.dariolucia.ccsds.cfdp.ut.IUtLayerSubscriber;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;

import java.util.List;
import java.util.Map;
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
    }

    @Override
    public synchronized void deregister(IUtLayerSubscriber s) {
        this.subscribers.remove(s);
    }

    @Override
    public void dispose() {
        try {
            deactivate();
        } catch (UtLayerException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("Exception when disposing UT Layer %s: %s", getName(), e.getMessage()), e);
            }
        }
        // Subclasses may override and call super
    }

    protected void notifyPduReceived(CfdpPdu decoded) {
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
        // Subclasses may override, but must call super
    }

    public synchronized void setRxAvailability(boolean available, long... entityIds) {
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
        // Subclasses may override, but must call super
    }

    public synchronized void activate() throws UtLayerException { // NOSONAR: exception declaration must stay
        if(activated) {
            return;
        }
        this.activated = true;
        // inform all subscribers of the current state of ID, if any
        for(Map.Entry<Long, Boolean> e : this.id2rxAvailable.entrySet()) {
            for(IUtLayerSubscriber s : this.subscribers) {
                try {
                    if(e.getValue()) { // NOSONAR: Boolean cannot be null, see ConcurrentHashMap contract
                        s.startRxPeriod(this, e.getKey());
                    } else {
                        s.endRxPeriod(this, e.getKey());
                    }
                } catch (Exception ex) {
                    if(LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on RX activation: %s", s, getName(), ex.getMessage()), ex);
                    }
                }
            }
        }
        for(Map.Entry<Long, Boolean> e : this.id2txAvailable.entrySet()) {
            for(IUtLayerSubscriber s : this.subscribers) {
                try {
                    if(e.getValue()) { // NOSONAR: Boolean cannot be null, see ConcurrentHashMap contract
                        s.startTxPeriod(this, e.getKey());
                    } else {
                        s.endTxPeriod(this, e.getKey());
                    }
                } catch (Exception ex) {
                    if(LOG.isLoggable(Level.WARNING)) {
                        LOG.log(Level.WARNING, String.format("Cannot notify subscriber %s from UT Layer %s on TX activation: %s", s, getName(), ex.getMessage()), ex);
                    }
                }
            }
        }
        // Subclasses may override, but must call super
    }

    public void deactivate() throws UtLayerException { // NOSONAR: exception declaration must stay
        synchronized (this) {
            if (!activated) {
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
        }
        // Subclasses may override, but must call super
    }

    public synchronized boolean isActivated() {
        return activated;
    }
}
