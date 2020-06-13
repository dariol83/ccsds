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

public class S6FopState extends AbstractFopState {

    public S6FopState(FopEngine engine) {
        super(engine);
    }

    @Override
    protected void registerHandlers() {
        event2handler.put(FopEvent.EventNumber.E1, this::ignore);
        event2handler.put(FopEvent.EventNumber.E2, this::ignore);
        event2handler.put(FopEvent.EventNumber.E3, this::ignore);
        event2handler.put(FopEvent.EventNumber.E4, this::ignore);
        event2handler.put(FopEvent.EventNumber.E5, this::ignore);
        event2handler.put(FopEvent.EventNumber.E6, this::ignore);
        event2handler.put(FopEvent.EventNumber.E7, this::ignore);
        event2handler.put(FopEvent.EventNumber.E101, this::ignore);
        event2handler.put(FopEvent.EventNumber.E8, this::ignore);
        event2handler.put(FopEvent.EventNumber.E9, this::ignore);
        event2handler.put(FopEvent.EventNumber.E10, this::ignore);
        event2handler.put(FopEvent.EventNumber.E11, this::ignore);
        event2handler.put(FopEvent.EventNumber.E12, this::ignore);
        event2handler.put(FopEvent.EventNumber.E103, this::ignore);
        event2handler.put(FopEvent.EventNumber.E13, this::ignore);
        event2handler.put(FopEvent.EventNumber.E14, this::ignore);
        event2handler.put(FopEvent.EventNumber.E15, this::ignore);
        event2handler.put(FopEvent.EventNumber.E19, this::reject);
        event2handler.put(FopEvent.EventNumber.E20, this::reject);
        event2handler.put(FopEvent.EventNumber.E21, this::e21);
        event2handler.put(FopEvent.EventNumber.E22, this::reject);
        event2handler.put(FopEvent.EventNumber.E23, this::e23);
        event2handler.put(FopEvent.EventNumber.E24, this::e24);
        event2handler.put(FopEvent.EventNumber.E25, this::e25);
        event2handler.put(FopEvent.EventNumber.E26, this::reject);
        event2handler.put(FopEvent.EventNumber.E27, this::e27);
        event2handler.put(FopEvent.EventNumber.E28, this::reject);
        event2handler.put(FopEvent.EventNumber.E29, this::e29);
        event2handler.put(FopEvent.EventNumber.E30, this::reject);

        event2handler.put(FopEvent.EventNumber.E31, this::e31);
        event2handler.put(FopEvent.EventNumber.E32, this::e32);
        event2handler.put(FopEvent.EventNumber.E33, this::e33);
        event2handler.put(FopEvent.EventNumber.E34, this::e34);
        event2handler.put(FopEvent.EventNumber.E35, this::e35);

        event2handler.put(FopEvent.EventNumber.E36, this::e36);
        event2handler.put(FopEvent.EventNumber.E37, this::e37);
        event2handler.put(FopEvent.EventNumber.E38, this::e38);
        event2handler.put(FopEvent.EventNumber.E39, this::e39);
        event2handler.put(FopEvent.EventNumber.E40, this::reject);

        event2handler.put(FopEvent.EventNumber.E41, this::e41);
        event2handler.put(FopEvent.EventNumber.E42, this::e42);
        event2handler.put(FopEvent.EventNumber.E43, this::e43);
        event2handler.put(FopEvent.EventNumber.E44, this::e44);
        event2handler.put(FopEvent.EventNumber.E45, this::e45);
        event2handler.put(FopEvent.EventNumber.E46, this::e46);
    }

    private AbstractFopState reject(FopEvent fopEvent) {
        engine.reject(fopEvent);
        return this;
    }

    private AbstractFopState ignore(FopEvent fopEvent) {
        // Ignore
        return this;
    }

    private AbstractFopState e23(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.initialise();
        engine.confirm(FopOperationStatus.POSIIVE_CONFIRM, fopEvent);
        return new S1FopState(engine);
    }

    private AbstractFopState e24(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.initialise();
        engine.restartTimer();
        return new S4FopState(engine);
    }

    private AbstractFopState e25(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.initialise();
        engine.transmitTypeBcFrameUnlock();
        return new S5FopState(engine);
    }

    private AbstractFopState e27(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.initialise();
        engine.prepareForSetVr(fopEvent.getDirectiveQualifier());
        engine.transmitTypeBcFrameSetVr(fopEvent.getDirectiveQualifier());
        return new S5FopState(engine);
    }

    private AbstractFopState e29(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.confirm(FopOperationStatus.POSIIVE_CONFIRM, fopEvent);
        return this;
    }

    private AbstractFopState e31(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.resume();
        engine.confirm(FopOperationStatus.POSIIVE_CONFIRM, fopEvent);
        return new S1FopState(engine);
    }

    private AbstractFopState e32(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.resume();
        engine.confirm(FopOperationStatus.POSIIVE_CONFIRM, fopEvent);
        return new S2FopState(engine);
    }

    private AbstractFopState e33(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.resume();
        engine.confirm(FopOperationStatus.POSIIVE_CONFIRM, fopEvent);
        return new S3FopState(engine);
    }

    private AbstractFopState e34(FopEvent fopEvent) {
        engine.accept(fopEvent);
        engine.resume();
        engine.confirm(FopOperationStatus.POSIIVE_CONFIRM, fopEvent);
        return new S4FopState(engine);
    }

    private AbstractFopState e35(FopEvent fopEvent) {
        if(fopEvent.getSS() == 0) {
            engine.accept(fopEvent);
            engine.setVs(fopEvent.getDirectiveQualifier());
            engine.confirm(FopOperationStatus.POSIIVE_CONFIRM, fopEvent);
        } else {
            engine.reject(fopEvent);
        }
        return this;
    }

    private AbstractFopState e41(FopEvent fopEvent) {
        engine.setAdOutReadyFlag(true);
        return this;
    }

    private AbstractFopState e43(FopEvent fopEvent) {
        engine.setBcOutReadyFlag(true);
        return this;
    }

    @Override
    public FopState getState() {
        return FopState.S6;
    }

}
