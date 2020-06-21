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

package eu.dariolucia.ccsds.tmtc.cop1.fop;

public class FopStatus {

    private final int expectedAckFrameSequenceNumber;
    private final int sentQueueItems;
    private final boolean waitQueueFull;
    private final boolean adOutReadyFlag;
    private final boolean bcOutReadyFlag;
    private final boolean bdOutReadyFlag;

    private final FopState previousState;
    private final FopState currentState;
    private final FopEvent.EventNumber event;

    public FopStatus(int expectedAckFrameSequenceNumber, int sentQueueItems, boolean waitQueueFull, boolean adOutReadyFlag, boolean bcOutReadyFlag, boolean bdOutReadyFlag, FopState previousState, FopState currentState, FopEvent.EventNumber event) { // NOSONAR number of parameters is necessary
        this.expectedAckFrameSequenceNumber = expectedAckFrameSequenceNumber;
        this.sentQueueItems = sentQueueItems;
        this.waitQueueFull = waitQueueFull;
        this.adOutReadyFlag = adOutReadyFlag;
        this.bcOutReadyFlag = bcOutReadyFlag;
        this.bdOutReadyFlag = bdOutReadyFlag;
        this.previousState = previousState;
        this.currentState = currentState;
        this.event = event;
    }

    public int getExpectedAckFrameSequenceNumber() {
        return expectedAckFrameSequenceNumber;
    }

    public int getSentQueueItems() {
        return sentQueueItems;
    }

    public boolean isWaitQueueFull() {
        return waitQueueFull;
    }

    public boolean isAdOutReadyFlag() {
        return adOutReadyFlag;
    }

    public boolean isBcOutReadyFlag() {
        return bcOutReadyFlag;
    }

    public boolean isBdOutReadyFlag() {
        return bdOutReadyFlag;
    }

    public FopState getPreviousState() {
        return previousState;
    }

    public FopState getCurrentState() {
        return currentState;
    }

    public FopEvent.EventNumber getEvent() {
        return event;
    }

    @Override
    public String toString() {
        return "FopStatus{" +
                "NN(R)=" + getExpectedAckFrameSequenceNumber() +
                ", previousState=" + getPreviousState() +
                ", currentState=" + getCurrentState() +
                ", event=" + getEvent() +
                ", sentQueueItems=" + getSentQueueItems() +
                ", waitQueueFull=" + isWaitQueueFull() +
                ", adOutReadyFlag=" + isAdOutReadyFlag() +
                ", bcOutReadyFlag=" + isBcOutReadyFlag() +
                ", bdOutReadyFlag=" + isBdOutReadyFlag() +
                '}';
    }
}
