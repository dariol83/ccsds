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

import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.ocf.pdu.Clcw;

public final class FopEvent {

    private final EventNumber number;
    private final Clcw clcw;
    private final TcTransferFrame frame;
    private final Object directiveTag;
    private final FopDirective directiveId;
    private final int directiveQualifier;
    private final boolean timerExpired;
    private final int ss;

    public FopEvent(EventNumber number, Clcw clcw, int ss) {
        this.number = number;
        this.clcw = clcw;
        this.frame = null;
        this.directiveTag = null;
        this.directiveId = null;
        this.directiveQualifier = 0;
        this.timerExpired = false;
        this.ss = ss;
    }

    public FopEvent(EventNumber number, TcTransferFrame frame, int ss) {
        this.number = number;
        this.clcw = null;
        this.frame = frame;
        this.directiveTag = null;
        this.directiveId = null;
        this.directiveQualifier = 0;
        this.timerExpired = false;
        this.ss = ss;
    }

    public FopEvent(EventNumber number, boolean timerExpired, int ss) {
        this.number = number;
        this.timerExpired = timerExpired;
        this.clcw = null;
        this.frame = null;
        this.directiveTag = null;
        this.directiveId = null;
        this.directiveQualifier = 0;
        this.ss = ss;
    }

    public FopEvent(EventNumber number, Object directiveTag, FopDirective directiveId, int directiveQualifier, int ss) {
        this.number = number;
        this.clcw = null;
        this.frame = null;
        this.directiveTag = directiveTag;
        this.directiveId = directiveId;
        this.directiveQualifier = directiveQualifier;
        this.timerExpired = false;
        this.ss = ss;
    }

    public EventNumber getNumber() {
        return number;
    }

    public Clcw getClcw() {
        return clcw;
    }

    public TcTransferFrame getFrame() {
        return frame;
    }

    public Object getDirectiveTag() {
        return directiveTag;
    }

    public FopDirective getDirectiveId() {
        return directiveId;
    }

    public int getDirectiveQualifier() {
        return directiveQualifier;
    }

    public int getSuspendState() {
        return this.ss;
    }

    public enum EventNumber {
        E1,
        E2,
        E3,
        E4,
        E5,
        E6,
        E7,
        E101,
        E102,
        E8,
        E9,
        E10,
        E11,
        E12,
        E103,
        E13,
        E14,
        E15,
        E16,
        E104,
        E17,
        E18,
        E19,
        E20,
        E21,
        E22,
        E23,
        E24,
        E25,
        E26,
        E27,
        E28,
        E29,
        E30,
        E31,
        E32,
        E33,
        E34,
        E35,
        E36,
        E37,
        E38,
        E39,
        E40,
        E41,
        E42,
        E43,
        E44,
        E45,
        E46
    }
}

