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

public class S2FopState extends AbstractFopState {

    public S2FopState(FopEngine engine) {
        super(engine);
    }

    @Override
    protected void registerHandlers() {
        event2handler.put(FopEvent.EventNumber.E1, this::e1e4e5);
        event2handler.put(FopEvent.EventNumber.E2, this::e2);
        event2handler.put(FopEvent.EventNumber.E3, this::e3e7e15);
        event2handler.put(FopEvent.EventNumber.E4, this::e1e4e5);
        event2handler.put(FopEvent.EventNumber.E5, this::e1e4e5);
        event2handler.put(FopEvent.EventNumber.E6, this::e6);
        event2handler.put(FopEvent.EventNumber.E7, this::e3e7e15);
        event2handler.put(FopEvent.EventNumber.E101, this::e101);
        event2handler.put(FopEvent.EventNumber.E102, this::e102);
        event2handler.put(FopEvent.EventNumber.E8, this::e8);
        event2handler.put(FopEvent.EventNumber.E9, this::e9);
        event2handler.put(FopEvent.EventNumber.E10, this::ignore);
        event2handler.put(FopEvent.EventNumber.E11, this::e11e103);
        event2handler.put(FopEvent.EventNumber.E12, this::ignore);
        event2handler.put(FopEvent.EventNumber.E103, this::e11e103);
        event2handler.put(FopEvent.EventNumber.E13, this::e13);
        event2handler.put(FopEvent.EventNumber.E14, this::e14);
        event2handler.put(FopEvent.EventNumber.E15, this::e3e7e15);
        event2handler.put(FopEvent.EventNumber.E16, this::e16e104);
        event2handler.put(FopEvent.EventNumber.E104, this::e16e104);
        event2handler.put(FopEvent.EventNumber.E17, this::e17);
        event2handler.put(FopEvent.EventNumber.E18, this::e18);
        event2handler.put(FopEvent.EventNumber.E19, this::e19);
        event2handler.put(FopEvent.EventNumber.E20, this::reject);
        event2handler.put(FopEvent.EventNumber.E21, this::e21);
        event2handler.put(FopEvent.EventNumber.E22, this::reject);
        event2handler.put(FopEvent.EventNumber.E23, this::reject);
        event2handler.put(FopEvent.EventNumber.E24, this::reject);
        event2handler.put(FopEvent.EventNumber.E25, this::reject);
        event2handler.put(FopEvent.EventNumber.E26, this::reject);
        event2handler.put(FopEvent.EventNumber.E27, this::reject);
        event2handler.put(FopEvent.EventNumber.E28, this::reject);
        event2handler.put(FopEvent.EventNumber.E29, this::e29);
        event2handler.put(FopEvent.EventNumber.E30, this::reject);
        event2handler.put(FopEvent.EventNumber.E35, this::reject);
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

    private AbstractFopState e1e4e5(FopEvent fopEvent) {
        engine.alert(FopAlertCode.SYNCH);
        return new S6FopState(engine);
    }

    private AbstractFopState e2(FopEvent fopEvent) {
        engine.removeAckFramesFromSentQueue(fopEvent.getClcw());
        engine.cancelTimer();
        engine.lookForFrame();
        return new S1FopState(engine);
    }

    private AbstractFopState e3e7e15(FopEvent fopEvent) {
        engine.alert(FopAlertCode.CLCW);
        return new S6FopState(engine);
    }

    private AbstractFopState e6(FopEvent fopEvent) {
        engine.removeAckFramesFromSentQueue(fopEvent.getClcw());
        engine.lookForFrame();
        return new S1FopState(engine);
    }

    private AbstractFopState e101(FopEvent fopEvent) {
        engine.removeAckFramesFromSentQueue(fopEvent.getClcw());
        engine.alert(FopAlertCode.LIMIT);
        return new S6FopState(engine);
    }

    private AbstractFopState e102(FopEvent fopEvent) {
        engine.alert(FopAlertCode.LIMIT);
        return new S6FopState(engine);
    }

    private AbstractFopState e8(FopEvent fopEvent) {
        engine.removeAckFramesFromSentQueue(fopEvent.getClcw());
        engine.initiateAdRetransmission();
        engine.lookForFrame();
        return this;
    }

    private AbstractFopState e9(FopEvent fopEvent) {
        engine.removeAckFramesFromSentQueue(fopEvent.getClcw());
        return new S3FopState(engine);
    }

    private AbstractFopState e11e103(FopEvent fopEvent) {
        // No action
        return new S3FopState(engine);
    }

    private AbstractFopState e13(FopEvent fopEvent) {
        engine.alert(FopAlertCode.NN_R);
        return new S6FopState(engine);
    }

    private AbstractFopState e14(FopEvent fopEvent) {
        engine.alert(FopAlertCode.LOCKOUT);
        return new S6FopState(engine);
    }

    private AbstractFopState e16e104(FopEvent fopEvent) {
        engine.initiateAdRetransmission();
        engine.lookForFrame();
        return this;
    }

    private AbstractFopState e17(FopEvent fopEvent) {
        engine.alert(FopAlertCode.T1);
        return new S6FopState(engine);
    }

    private AbstractFopState e18(FopEvent fopEvent) {
        engine.setSuspendState(2);
        engine.suspend();
        return new S6FopState(engine);
    }

    private AbstractFopState e19(FopEvent fopEvent) {
        engine.addToWaitQueue(fopEvent);
        engine.lookForFrame();
        return this;
    }

    private AbstractFopState e41(FopEvent fopEvent) {
        engine.setAdOutReadyFlag(true);
        engine.lookForFrame();
        return this;
    }

    private AbstractFopState e43(FopEvent fopEvent) {
        engine.setBcOutReadyFlag(true);
        return this;
    }

    @Override
    public FopState getState() {
        return FopState.S2;
    }

}
