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

public class S1FarmState extends AbstractFarmState {

    public S1FarmState(FarmEngine engine) {
        super(engine);
    }

    @Override
    protected void registerHandlers() {
        event2handler.put(FarmEvent.EventNumber.E1, this::e1);
        event2handler.put(FarmEvent.EventNumber.E2, this::e2);
        event2handler.put(FarmEvent.EventNumber.E3, this::e3);
        event2handler.put(FarmEvent.EventNumber.E4, this::discard);
        event2handler.put(FarmEvent.EventNumber.E5, this::e5);
        event2handler.put(FarmEvent.EventNumber.E6, this::e6);
        event2handler.put(FarmEvent.EventNumber.E7, this::e7);
        event2handler.put(FarmEvent.EventNumber.E8, this::e8);
        event2handler.put(FarmEvent.EventNumber.E9, this::discard);
        event2handler.put(FarmEvent.EventNumber.E10, this::e10);
        // Event E11 not handled here in this implementation
    }

    private AbstractFarmState e1(FarmEvent farmEvent) {
        engine.accept(farmEvent.getFrame());
        engine.increaseVr();
        engine.resetRetransmitFlag();
        return this;
    }

    private AbstractFarmState e2(FarmEvent farmEvent) {
        engine.discard(farmEvent.getFrame());
        engine.setRetransmitFlag();
        // set wait flag driven by the state
        return new S2FarmState(engine);
    }

    private AbstractFarmState e3(FarmEvent farmEvent) {
        engine.discard(farmEvent.getFrame());
        engine.setRetransmitFlag();
        return this;
    }

    private AbstractFarmState e5(FarmEvent farmEvent) {
        engine.discard(farmEvent.getFrame());
        return new S3FarmState(engine);
    }

    private AbstractFarmState e7(FarmEvent farmEvent) {
        engine.increaseFarmB();
        engine.resetRetransmitFlag();
        return this;
    }

    private AbstractFarmState e8(FarmEvent farmEvent) {
        engine.increaseFarmB();
        engine.resetRetransmitFlag();
        engine.setVr(farmEvent.getFrame().getSetVrValue());
        return this;
    }

    private AbstractFarmState e10(FarmEvent farmEvent) {
        // Ignore
        return this;
    }

    @Override
    public FarmState getState() {
        return FarmState.S1;
    }

}
