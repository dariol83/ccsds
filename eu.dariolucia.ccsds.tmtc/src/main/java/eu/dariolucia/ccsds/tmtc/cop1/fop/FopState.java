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

public enum FopState {
    /**
     * Normal state of the state machine when there are no recent errors on
     * the link and no incidents have occurred leading to flow control problems
     */
    S1("Active"),
    /**
     * The state machine is in the 'Retransmit without Wait' State (S2) while the 'Retransmit' Flag
     * is 'on' in the CLCW of the Virtual Channel but no other exceptional circumstances prevail.
     */
    S2("Retransmit without wait"),
    /**
     * The state machine is in the 'Retransmit with Wait' State (S3) while the 'Wait' Flag is 'on' in
     * the CLCW of the Virtual Channel. (Some Transfer Frames must always be retransmitted
     * when the 'Wait' Flag is reset, since the 'Wait' Flag is set only when at least one Transfer
     * Frame has been discarded.) In this state the 'Retransmit' Flag will also be set (as a
     * consequence of the fact that Transfer Frames have been lost).
     */
    S3("Retransmit with wait"),
    /**
     * The state machine is in the 'Initializing without BC Frame' State (S4) after receiving an
     * 'Initiate AD Service (with CLCW check)' Directive while in the 'Initial' State. A successful
     * CLCW check will result in a transition to S1.
     */
    S4("Initialising without BC Frame"),
    /**
     * The state machine is in the 'Initializing with BC Frame' State (S5) after receiving an 'Initiate
     * AD Service (with Unlock)' Directive or 'Initiate AD Service (with Set V(R))' Directive
     * while in the 'Initial' State with BC_Out_Flag = Ready. A successful transmission of the
     * Type-BC Transfer Frame and a subsequent 'clean' CLCW status will result in a transition to
     * S1.
     */
    S5("Initialising with BC Frame"),
    /**
     * The state machine is in the 'Initial' State (S6) whenever it is necessary to terminate an
     * ongoing service (this happens when a 'Terminate AD Service' Directive is received or when
     * an 'exception', i.e., an event that causes an 'Alert', occurs). In principle, the 'Initial' State is
     * the first state entered by the state machine for a particular Virtual Channel.
     * State S6 shall also be used when the AD Service is suspended.
     */
    S6("Initial");

    private String description;

    FopState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.name() + " - " + description;
    }
}
