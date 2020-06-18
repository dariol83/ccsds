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

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractFopState {

    protected final FopEngine engine;
    protected final Map<FopEvent.EventNumber, Function<FopEvent, AbstractFopState>> event2handler = new EnumMap<>(FopEvent.EventNumber.class);

    public AbstractFopState(FopEngine engine) {
        this.engine = engine;
        registerHandlers();
    }

    protected abstract void registerHandlers();

    public AbstractFopState event(FopEvent event) {
        Function<FopEvent, AbstractFopState> handler = event2handler.get(event.getNumber());
        if(handler != null) {
            return handler.apply(event);
        } else {
            return this;
        }
    }

    public abstract FopState getState();


    protected AbstractFopState e21(FopEvent fopEvent) {
        engine.accept(fopEvent.getFrame());
        engine.transmitTypeBdFrame(fopEvent.getFrame());
        return this;
    }

    protected AbstractFopState e36(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setFopSlidingWindow(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFopState e37(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setT1Initial(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFopState e38(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setTransmissionLimit(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFopState e39(FopEvent fopEvent) {
        engine.accept(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        engine.setTimeoutType(fopEvent.getDirectiveQualifier());
        engine.confirm(fopEvent.getDirectiveTag(), fopEvent.getDirectiveId(), fopEvent.getDirectiveQualifier());
        return this;
    }

    protected AbstractFopState e42(FopEvent fopEvent) { // NOSONAR part of CCSDS standard
        engine.alert(FopAlertCode.LLIF);
        return new S6FopState(engine);
    }

    protected AbstractFopState e44(FopEvent fopEvent) { // NOSONAR part of CCSDS standard, separate event
        engine.alert(FopAlertCode.LLIF);
        return new S6FopState(engine);
    }

    protected AbstractFopState e45(FopEvent fopEvent) {
        engine.setBdOutReadyFlag(true);
        engine.accept(fopEvent.getFrame());
        return this;
    }

    protected AbstractFopState e46(FopEvent fopEvent) { // NOSONAR part of CCSDS standard, separate event
        engine.alert(FopAlertCode.LLIF);
        return new S6FopState(engine);
    }

}
