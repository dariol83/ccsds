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

package eu.dariolucia.ccsds.tmtc.cop1.farm;

import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;

public class FarmStatus {

    private final int bufferedTcFrames;
    private final Clcw lastClcw;
    private final FarmState previousState;
    private final FarmState currentState;
    private final FarmEvent.EventNumber event;

    public FarmStatus(int bufferedTcFrames, Clcw lastClcw, FarmState previousState, FarmState currentState, FarmEvent.EventNumber event) {
        this.bufferedTcFrames = bufferedTcFrames;
        this.lastClcw = lastClcw;
        this.previousState = previousState;
        this.currentState = currentState;
        this.event = event;
    }

    public int getBufferedTcFrames() {
        return bufferedTcFrames;
    }

    public Clcw getLastClcw() {
        return lastClcw;
    }

    public FarmState getPreviousState() {
        return previousState;
    }

    public FarmState getCurrentState() {
        return currentState;
    }

    public FarmEvent.EventNumber getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "FarmStatus{" +
                "previousState=" + getPreviousState() +
                ", currentState=" + getCurrentState() +
                ", event=" + getEvent() +
                ", bufferedTcFrames=" + getBufferedTcFrames() +
                ", lastClcw=" + getLastClcw() +
                '}';
    }
}
