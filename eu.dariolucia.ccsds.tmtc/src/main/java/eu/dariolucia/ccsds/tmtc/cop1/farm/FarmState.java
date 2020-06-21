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

public enum FarmState {
    /**
     * In 'Open' State, the state machine accepts in-sequence Transfer Frames and passes them to
     * the Higher Procedures.
     */
    S1("Open"),
    /**
     * In 'Wait' State, there is no buffer space available in which to place any further received
     * Type-A FDUs. The state machine enters the 'Wait' State when it has received a Type-A
     * FDU, but is unable to deliver it to the Higher Procedures because there is no buffer available
     * (see 3.2.2). It leaves the 'Wait' State when at least one buffer becomes available for placing
     * a Type-A FDU.
     */
    S2("Wait"),
    /**
     * 'Lockout' State is entered if the state machine receives a Transfer Frame with sequence
     * number N(S) outside the range expected if FOP-1 is operating correctly. It is a safe state in
     * that no Type-A FDUs will be accepted or transferred to the Higher Procedures when in the
     * 'Lockout' State. The state machine leaves the 'Lockout' State upon receipt of an 'Unlock'
     * Control Command.
     */
    S3("Lockout");

    private String description;

    FarmState(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return name() + " - " + description;
    }
}
