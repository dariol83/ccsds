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

import eu.dariolucia.ccsds.tmtc.cop1.fop.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractFarmState {

    protected final FarmEngine engine;
    protected final Map<FarmEvent.EventNumber, Function<FarmEvent, AbstractFarmState>> event2handler = new EnumMap<>(FarmEvent.EventNumber.class);

    public AbstractFarmState(FarmEngine engine) {
        this.engine = engine;
        registerHandlers();
    }

    protected abstract void registerHandlers();

    public AbstractFarmState event(FarmEvent event) {
        Function<FarmEvent, AbstractFarmState> handler = event2handler.get(event.getNumber());
        if(handler != null) {
            return handler.apply(event);
        } else {
            return this;
        }
    }

    public abstract FarmState getState();


    protected AbstractFarmState e21(FopEvent fopEvent) {
        engine.accept(fopEvent.getFrame());
        engine.transmitTypeBdFrame(fopEvent.getFrame());
        return this;
    }

    protected AbstractFarmState e36(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setFopSlidingWindow(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFarmState e37(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setT1Initial(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFarmState e38(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setTransmissionLimit(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFarmState e39(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setTimeoutType(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFarmState e42(FopEvent fopEvent) {
        engine.alert(FopAlertCode.LLIF);
        return new S6FopState(engine);
    }

    protected AbstractFarmState e44(FopEvent fopEvent) {
        engine.alert(FopAlertCode.LLIF);
        return new S6FopState(engine);
    }

    protected AbstractFarmState e45(FopEvent fopEvent) {
        engine.setBdOutReadyFlag(true);
        engine.accept(fopEvent.getFrame());
        return this;
    }

    protected AbstractFarmState e46(FopEvent fopEvent) {
        engine.alert(FopAlertCode.LLIF);
        return new S6FopState(engine);
    }

}
