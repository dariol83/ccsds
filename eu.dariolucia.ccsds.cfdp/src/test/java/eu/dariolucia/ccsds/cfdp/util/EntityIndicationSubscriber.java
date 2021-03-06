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

package eu.dariolucia.ccsds.cfdp.util;

import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntitySubscriber;
import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EntityIndicationSubscriber implements ICfdpEntitySubscriber {

    private static final Logger LOG = Logger.getLogger(EntityIndicationSubscriber.class.getName());

    private final List<ICfdpIndication> indicationList = new LinkedList<>();

    @Override
    public synchronized void indication(ICfdpEntity emitter, ICfdpIndication indication) {
        this.indicationList.add(indication);
        notifyAll();
    }

    public synchronized void print() {
        for(ICfdpIndication i : this.indicationList) {
            LOG.info(i.toString());
        }
    }

    public synchronized int getIndicationListSize() {
        return indicationList.size();
    }

    public synchronized ICfdpIndication getIndicationAt(int pos) {
        return indicationList.get(pos);
    }

    public synchronized void assertSize(int size) {
        assertEquals(size, this.indicationList.size());
    }

    public synchronized <T extends ICfdpIndication> T assertPresentAt(int position, Class<T> indication) {
        assertTrue(this.indicationList.size() > position);
        ICfdpIndication i = this.indicationList.get(position);
        assertEquals(indication, i.getClass());
        return (T) i;
    }

    public synchronized int assertPresentAfter(int position, Class<? extends ICfdpIndication> indication) {
        assertTrue(this.indicationList.size() > position);
        for(int i = position; i < this.indicationList.size(); ++i) {
            ICfdpIndication ind = this.indicationList.get(i);
            if(indication.isAssignableFrom(ind.getClass())) {
                return i;
            }
        }
        throw new AssertionError("Indication " + indication + " not found after position " + position);
    }

    public synchronized <T extends ICfdpIndication> T waitForIndication(Class<T> indication, long timeoutMillis) {
        long now = System.currentTimeMillis();
        long target = now + timeoutMillis;
        ICfdpIndication present = indicationPresent(indication);
        while(present == null) {
            long toWait = target - System.currentTimeMillis();
            if(toWait > 0) {
                try {
                    wait(toWait);
                } catch (InterruptedException e) {
                    // Nothing
                }
            } else {
                throw new AssertionError("Indication " + indication + " not received after " + timeoutMillis + " ms");
            }
            present = indicationPresent(indication);

        }
        // If you reach this stage, the indication is present
        return (T) present;
    }

    // To be called under monitor
    private ICfdpIndication indicationPresent(Class<? extends ICfdpIndication> indication) {
        for(int i = this.indicationList.size() - 1; i >= 0; --i) {
            ICfdpIndication ind = this.indicationList.get(i);
            if(indication.isAssignableFrom(ind.getClass())) {
                return ind;
            }
        }
        return null;
    }

    public synchronized void clear() {
        this.indicationList.clear();
    }
}
