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

import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;

public final class FarmEvent {

    private final EventNumber number;
    private final TcTransferFrame frame;
    private final boolean bufferReleaseSignal;

    public FarmEvent(EventNumber number, TcTransferFrame frame) {
        this.number = number;
        this.frame = frame;
        this.bufferReleaseSignal = false;
    }

    public FarmEvent(EventNumber number) {
        this.number = number;
        this.bufferReleaseSignal = true;
        this.frame = null;
    }

    public EventNumber getNumber() {
        return number;
    }

    public TcTransferFrame getFrame() {
        return frame;
    }

    public boolean isBufferReleaseSignal() {
        return bufferReleaseSignal;
    }

    @Override
    public String toString() {
        return "FarmEvent{" +
                "number=" + number +
                ", frame=" + frame +
                ", bufferReleaseSignal=" + bufferReleaseSignal +
                '}';
    }

    public enum EventNumber {
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
        E8,
        E9,
        E10,
        E11
    }
}

