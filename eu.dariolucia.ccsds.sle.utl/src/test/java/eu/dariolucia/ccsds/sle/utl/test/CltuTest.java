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

package eu.dariolucia.ccsds.sle.utl.test;

import com.beanit.jasn1.ber.types.BerInteger;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuGetParameter;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuStart;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuThrowEvent;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures.DiagnosticCltuTransferData;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.DiagnosticScheduleStatusReport;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Diagnostics;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.OperationRecorder;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.BindDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceBindingStateEnum;
import eu.dariolucia.ccsds.sle.utl.si.UnbindReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class CltuTest {

    @BeforeAll
    static void setLogLevel() {
        Logger.getLogger("eu.dariolucia").setLevel(Level.ALL);
        Arrays.stream(Logger.getLogger("eu.dariolucia").getHandlers()).forEach(o -> o.setLevel(Level.ALL));
    }

    @Test
    void testNegativeBind() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigP = (CltuServiceInstanceConfiguration) providerFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstanceProvider cltuProvider = new CltuServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        cltuProvider.configure();
        cltuProvider.waitForBind(true, null);
        cltuProvider.setBindReturnBehaviour(false, BindDiagnosticsEnum.INCONSISTENT_SERVICE_TYPE);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigU = (CltuServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstance cltuUser = new CltuServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        cltuUser.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        cltuUser.register(recorder);

        // Bind
        cltuUser.bind(2);
        AwaitUtil.await(2000);
        AwaitUtil.awaitCondition(4000, () -> cltuProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(1, recorder.getPduReceived().size());
        assertTrue(recorder.getPduReceived().get(0) instanceof SleBindReturn);
        assertEquals(BindDiagnosticsEnum.INCONSISTENT_SERVICE_TYPE.getCode(), ((SleBindReturn) recorder.getPduReceived().get(0)).getResult().getNegative().intValue());

        cltuUser.deregister(recorder);

        cltuUser.dispose();
        cltuProvider.dispose();
    }

    @Test
    void testNegativeOperationSequences() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigU = (CltuServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstance cltuUser = new CltuServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        cltuUser.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        cltuUser.register(recorder);

        // Start
        cltuUser.start(1L);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Stop
        cltuUser.stop();
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Get parameter
        cltuUser.getParameter(CltuParameterEnum.ACQUISITION_SEQUENCE_LENGTH);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Throw event
        cltuUser.throwEvent(0, 100, new byte[3]);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Transfer data
        cltuUser.transferData(0, null, null, 100000, false, new byte[3]);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Unbind
        cltuUser.unbind(UnbindReasonEnum.SUSPEND);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Peer abort
        cltuUser.peerAbort(PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Schedule status report
        cltuUser.scheduleStatusReport(false, 20);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        cltuUser.deregister(recorder);

        cltuUser.dispose();
    }

    @Test
    void testStatusReportGetParameterV2() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigP = (CltuServiceInstanceConfiguration) providerFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstanceProvider cltuProvider = new CltuServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        cltuProvider.configure();
        cltuProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigU = (CltuServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstance cltuUser = new CltuServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        cltuUser.configure();
        cltuUser.bind(2);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        cltuUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        cltuUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter bit lock required: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.BIT_LOCK_REQUIRED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBitLockRequired().getParameterValue().intValue());

        // Ask for parameter rf available required: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.RF_AVAILABLE_REQUIRED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParRfAvailableRequired().getParameterValue().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter expected SLDU ID: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.EXPECTED_SLDU_IDENTIFICATION);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParCltuIdentification().getParameterValue().intValue());

        // Ask for parameter expected event ID: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.EXPECTED_EVENT_INVOCATION_IDENTIFICATION);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParEventInvocationIdentification().getParameterValue().intValue());

        // Ask for parameter max CLTU length: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MAXIMUM_SLDU_LENGTH);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1000, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMaximumCltuLength().getParameterValue().intValue());

        // Ask for parameter modulation frequency: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MODULATION_FREQUENCY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(700, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParModulationFrequency().getParameterValue().intValue());

        // Ask for parameter modulation index: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MODULATION_INDEX);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParModulationIndex().getParameterValue().intValue());

        // Ask for parameter PLOP in effect: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.PLOP_IN_EFFECT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPlopInEffect().getParameterValue().intValue());

        // Ask for parameter subcarrier to bitrate ratio: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.SUBCARRIER_TO_BITRATE_RATIO);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParSubcarrierToBitRateRatio().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOff());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        cltuUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((CltuGetParameterReturnV1toV3) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        cltuUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        cltuUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(4000, () -> cltuUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> cltuProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuProvider.getCurrentBindingState());

        cltuUser.dispose();
        cltuProvider.dispose();
    }

    @Test
    void testStatusReportGetParameterV4() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigP = (CltuServiceInstanceConfiguration) providerFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstanceProvider cltuProvider = new CltuServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        cltuProvider.configure();
        cltuProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigU = (CltuServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstance cltuUser = new CltuServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        cltuUser.configure();
        cltuUser.bind(4);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        cltuUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        cltuUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());


        // Ask for parameter bit lock required: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.BIT_LOCK_REQUIRED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBitLockRequired().getParameterValue().intValue());

        // Ask for parameter rf available required: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.RF_AVAILABLE_REQUIRED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParRfAvailableRequired().getParameterValue().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter expected SLDU ID: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.EXPECTED_SLDU_IDENTIFICATION);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParCltuIdentification().getParameterValue().intValue());

        // Ask for parameter expected event ID: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.EXPECTED_EVENT_INVOCATION_IDENTIFICATION);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParEventInvocationIdentification().getParameterValue().intValue());

        // Ask for parameter max CLTU length: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MAXIMUM_SLDU_LENGTH);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1000, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMaximumCltuLength().getParameterValue().intValue());

        // Ask for parameter modulation frequency: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MODULATION_FREQUENCY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(700, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParModulationFrequency().getParameterValue().intValue());

        // Ask for parameter modulation index: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MODULATION_INDEX);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParModulationIndex().getParameterValue().intValue());

        // Ask for parameter PLOP in effect: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.PLOP_IN_EFFECT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPlopInEffect().getParameterValue().intValue());

        // Ask for parameter subcarrier to bitrate ratio: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.SUBCARRIER_TO_BITRATE_RATIO);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParSubcarrierToBitRateRatio().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOff());

        // Ask for parameter acquisition sequence length: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.ACQUISITION_SEQUENCE_LENGTH);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(100, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParAcquisitionSequenceLength().getParameterValue().intValue());

        // Ask for parameter CLCW physical channel: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.CLCW_PHYSICAL_CHANNEL);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals("CLCW_PH_CH", ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParClcwPhysicalChannel().getParameterValue().toString());

        // Ask for parameter minimum delay: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MINIMUM_DELAY_TIME);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5000, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMinimumDelayTime().getParameterValue().intValue());

        // Ask for parameter notification mode: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.NOTIFICATION_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(CltuNotificationModeEnum.IMMEDIATE.ordinal(), ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParNotificationMode().getParameterValue().intValue());

        // Ask for parameter PLOP1 idle sequence length: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.PLOP1_IDLE_SEQUENCE_LENGTH);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(40, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPlop1IdleSequenceLength().getParameterValue().intValue());

        // Ask for parameter protocol abort mode: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.PROTOCOL_ABORT_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(CltuProtocolAbortModeEnum.ABORT_MODE.ordinal(), ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParProtocolAbortMode().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        cltuUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((CltuGetParameterReturnV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        cltuUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        cltuUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(4000, () -> cltuUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> cltuProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuProvider.getCurrentBindingState());

        assertDoesNotThrow(() -> recorder.getStates().get(0).toString());

        cltuUser.dispose();
        cltuProvider.dispose();
    }

    @Test
    void testStatusReportGetParameterWithAuthorizationV5() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider_auth.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigP = (CltuServiceInstanceConfiguration) providerFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstanceProvider cltuProvider = new CltuServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        cltuProvider.configure();
        cltuProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user_auth.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigU = (CltuServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstance cltuUser = new CltuServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        cltuUser.configure();
        cltuUser.bind(5);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        cltuUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        cltuUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());


        // Ask for parameter bit lock required: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.BIT_LOCK_REQUIRED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBitLockRequired().getParameterValue().intValue());

        // Ask for parameter rf available required: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.RF_AVAILABLE_REQUIRED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParRfAvailableRequired().getParameterValue().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter expected SLDU ID: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.EXPECTED_SLDU_IDENTIFICATION);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParCltuIdentification().getParameterValue().intValue());

        // Ask for parameter expected event ID: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.EXPECTED_EVENT_INVOCATION_IDENTIFICATION);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParEventInvocationIdentification().getParameterValue().intValue());

        // Ask for parameter max CLTU length: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MAXIMUM_SLDU_LENGTH);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1000, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMaximumCltuLength().getParameterValue().intValue());

        // Ask for parameter modulation frequency: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MODULATION_FREQUENCY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(700, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParModulationFrequency().getParameterValue().intValue());

        // Ask for parameter modulation index: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MODULATION_INDEX);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParModulationIndex().getParameterValue().intValue());

        // Ask for parameter PLOP in effect: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.PLOP_IN_EFFECT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPlopInEffect().getParameterValue().intValue());

        // Ask for parameter subcarrier to bitrate ratio: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.SUBCARRIER_TO_BITRATE_RATIO);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParSubcarrierToBitRateRatio().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOff());

        // Ask for parameter acquisition sequence length: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.ACQUISITION_SEQUENCE_LENGTH);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(100, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParAcquisitionSequenceLength().getParameterValue().intValue());

        // Ask for parameter CLCW physical channel: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.CLCW_PHYSICAL_CHANNEL);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals("CLCW_PH_CH", ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParClcwPhysicalChannel().getParameterValue().getConfigured().toString());

        // Ask for parameter minimum delay: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MINIMUM_DELAY_TIME);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5000, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMinimumDelayTime().getParameterValue().intValue());

        // Ask for parameter notification mode: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.NOTIFICATION_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(CltuNotificationModeEnum.IMMEDIATE.ordinal(), ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParNotificationMode().getParameterValue().intValue());

        // Ask for parameter PLOP1 idle sequence length: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.PLOP1_IDLE_SEQUENCE_LENGTH);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(40, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPlop1IdleSequenceLength().getParameterValue().intValue());

        // Ask for parameter protocol abort mode: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.PROTOCOL_ABORT_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(CltuProtocolAbortModeEnum.ABORT_MODE.ordinal(), ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParProtocolAbortMode().getParameterValue().intValue());

        // Ask for parameter CLCW global VC ID: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.CLCW_GLOBAL_VCID);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured());
        assertEquals(100, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().getSpacecraftId().intValue());
        assertEquals(0, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().getVersionNumber().intValue());
        assertEquals(0, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParClcwGlobalVcId().getParameterValue().getCongigured().getVcId().getVirtualChannel().intValue());

        // Ask for parameter min reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMinReportingCycle().getParameterValue().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        cltuUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        cltuUser.getParameter(CltuParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((CltuGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        cltuUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        cltuUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(4000, () -> cltuUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> cltuProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuProvider.getCurrentBindingState());

        cltuUser.dispose();
        cltuProvider.dispose();
    }

    @Test
    void testNegativeStartAndSchedule() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigP = (CltuServiceInstanceConfiguration) providerFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstanceProvider cltuProvider = new CltuServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        cltuProvider.configure();
        cltuProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigU = (CltuServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstance cltuUser = new CltuServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        cltuUser.configure();
        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        cltuUser.register(recorder);

        cltuUser.bind(2);

        // Check reported state
        AwaitUtil.await(2000);
        assertTrue(recorder.getStates().size() > 0);
        assertEquals(ServiceInstanceBindingStateEnum.READY, recorder.getStates().get(recorder.getStates().size() - 1).getState());

        // Test negative start
        cltuProvider.setStartOperationHandler(o -> CltuStartResult.errorSpecific(CltuStartDiagnosticsEnum.UNABLE_TO_COMPLY));
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        cltuUser.start(1);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, recorder.getPduSent().size());
        assertNotNull(((CltuStartReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());

        // Test negative schedule status report
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        cltuUser.scheduleStatusReport(true, 20);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, recorder.getPduSent().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());

        // Unbind
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        cltuUser.unbind(UnbindReasonEnum.END);
        AwaitUtil.awaitCondition(4000, () -> cltuUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> cltuProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuProvider.getCurrentBindingState());

        cltuUser.dispose();
        cltuProvider.dispose();
    }

    @Test
    void testTransferDataThrowEventNoAuthV2() throws IOException, InterruptedException {
        performTransferDataThrowEvent("configuration_test_user.xml","configuration_test_provider.xml", 2);
    }

    @Test
    void testTransferDataWithAuthorizationV2() throws IOException, InterruptedException {
        performTransferDataThrowEvent("configuration_test_user_auth.xml","configuration_test_provider_auth.xml", 2);
    }

    @Test
    void testTransferDataThrowEventNoAuthV4() throws IOException, InterruptedException {
        performTransferDataThrowEvent("configuration_test_user.xml","configuration_test_provider.xml", 4);
    }

    @Test
    void testTransferDataWithAuthorizationV4() throws IOException, InterruptedException {
        performTransferDataThrowEvent("configuration_test_user_auth.xml","configuration_test_provider_auth.xml", 4);
    }

    @Test
    void testTransferDataThrowEventNoAuthV5() throws IOException, InterruptedException {
        performTransferDataThrowEvent("configuration_test_user.xml","configuration_test_provider.xml", 5);
    }

    @Test
    void testTransferDataWithAuthorizationV5() throws IOException, InterruptedException {
        performTransferDataThrowEvent("configuration_test_user_auth.xml","configuration_test_provider_auth.xml", 5);
    }

    private void performTransferDataThrowEvent(String userConf, String providerConf, int bind) throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(providerConf);
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigP = (CltuServiceInstanceConfiguration) providerFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstanceProvider cltuProvider = new CltuServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        cltuProvider.configure();
        cltuProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream(userConf);
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        CltuServiceInstanceConfiguration rafConfigU = (CltuServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // CLTU
        CltuServiceInstance cltuUser = new CltuServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        cltuUser.configure();
        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        cltuUser.register(recorder);

        cltuUser.bind(bind);

        // Check reported state
        AwaitUtil.await(4000);
        assertTrue(recorder.getStates().size() > 0);
        assertEquals(ServiceInstanceBindingStateEnum.READY, recorder.getStates().get(recorder.getStates().size() - 1).getState());

        // Update production status
        cltuProvider.updateProductionStatus(CltuProductionStatusEnum.CONFIGURED, CltuUplinkStatusEnum.UPLINK_STATUS_NOT_AVAILABLE, 16000);

        // Start
        recorder.getPduReceived().clear();
        cltuUser.start(1);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((CltuStartReturn)recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Simulate lock and production status change
        recorder.getPduReceived().clear();
        cltuProvider.updateProductionStatus(CltuProductionStatusEnum.OPERATIONAL, CltuUplinkStatusEnum.NOMINAL, 16000);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(CltuProductionStatusEnum.OPERATIONAL.ordinal(), ((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(0)).getProductionStatus().intValue());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(0)).getCltuNotification().getProductionOperational());

        // Send a transfer data (it will fail because there is no handler)
        recorder.getPduReceived().clear();
        cltuUser.transferData(1, new Date(), new Date(System.currentTimeMillis() + 300000), 50000, true, new byte[] {0x00, 0x00, 0x00, 0x00});
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());
        assertEquals(16000, ((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getCltuBufferAvailable().intValue());
        assertEquals(1, ((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getCltuIdentification().intValue());

        // Register transfer data handler
        cltuProvider.setTransferDataOperationHandler((o) -> {
            // Always accept and immediate execution via thread
            new Thread(() -> {
                try {
                    AwaitUtil.await(300);
                    Date radiationStarted = new Date();
                    cltuProvider.cltuProgress(o.getCltuIdentification().intValue(), CltuStatusEnum.PRODUCTION_STARTED, radiationStarted, null, 16000);
                    AwaitUtil.await(200);
                    cltuProvider.cltuProgress(o.getCltuIdentification().intValue(), CltuStatusEnum.RADIATED, radiationStarted, new Date(), 16000);
                    AwaitUtil.await(200);
                    cltuProvider.bufferEmpty(16000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return CltuTransferDataResult.noError(15400);
        });

        // Send a transfer data (it will succeed): you will get positive return, async notify and buffer empty notification
        recorder.getPduReceived().clear();
        cltuUser.transferData(1, new Date(), new Date(System.currentTimeMillis() + 300000), 50000, true, new byte[] {0x00, 0x00, 0x00, 0x00});
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());
        assertNotNull(((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());
        assertEquals(15400, ((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getCltuBufferAvailable().intValue());
        assertEquals(2, ((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getCltuIdentification().intValue());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(1)).getCltuLastProcessed().getCltuProcessed());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(1)).getCltuLastOk().getCltuOk());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(1)).getCltuNotification().getCltuRadiated());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(2)).getCltuNotification().getBufferEmpty());

        // Register transfer data handler
        cltuProvider.setTransferDataOperationHandler((o) -> {
            // Always accept and immediate execution via thread
            new Thread(() -> {
                try {
                    AwaitUtil.await(300);
                    cltuProvider.cltuProgress(o.getCltuIdentification().intValue(), CltuStatusEnum.EXPIRED, null, null, 16000);
                    AwaitUtil.await(200);
                    cltuProvider.bufferEmpty(16000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return CltuTransferDataResult.noError(15400);
        });

        // Send a transfer data (it will succeed): you will get positive return, async notify and buffer empty notification
        recorder.getPduReceived().clear();
        cltuUser.transferData(2, new Date(), new Date(System.currentTimeMillis() + 1000), 50000, true, new byte[] {0x00, 0x00, 0x00, 0x00});
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());
        assertNotNull(((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());
        assertEquals(15400, ((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getCltuBufferAvailable().intValue());
        assertEquals(3, ((CltuTransferDataReturn) recorder.getPduReceived().get(0)).getCltuIdentification().intValue());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(1)).getCltuNotification().getSlduExpired());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(2)).getCltuNotification().getBufferEmpty());

        // Send production status update
        recorder.getPduReceived().clear();
        cltuProvider.updateProductionStatus(CltuProductionStatusEnum.INTERRUPTED, CltuUplinkStatusEnum.NO_BIT_LOCK, 16000);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(CltuProductionStatusEnum.INTERRUPTED.ordinal(), ((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(0)).getProductionStatus().intValue());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(0)).getCltuNotification().getProductionInterrupted());

        // Send production status update
        recorder.getPduReceived().clear();
        cltuProvider.updateProductionStatus(CltuProductionStatusEnum.HALTED, CltuUplinkStatusEnum.NO_RF_AVAILABLE, 16000);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(CltuProductionStatusEnum.HALTED.ordinal(), ((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(0)).getProductionStatus().intValue());
        assertNotNull(((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(0)).getCltuNotification().getProductionHalted());

        // Stop
        recorder.getPduReceived().clear();
        cltuUser.stop();
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleAcknowledgement)recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Throw event (it will fail because of no handler)
        recorder.getPduReceived().clear();
        cltuUser.throwEvent(0, 100, new byte[] { 0x00, 0x00 });
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());
        assertEquals(0, ((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getEventInvocationIdentification().intValue());

        // Register throw event handler
        cltuProvider.setThrowEventOperationHandler((o) -> {
            // Always accept and immediate execution via thread
            new Thread(() -> {
                try {
                    AwaitUtil.await(300);
                    cltuProvider.eventProgress(o.getEventInvocationIdentification().intValue(), false, true);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return CltuThrowEventResult.noError();
        });

        // Throw event (it will succeed)
        recorder.getPduReceived().clear();
        cltuUser.throwEvent(0, 100, new byte[] { 0x00, 0x00 });
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());
        assertNotNull(((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());
        assertEquals(1, ((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getEventInvocationIdentification().intValue());
        assertEquals(0, ((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(1)).getCltuNotification().getActionListCompleted().intValue());

        // Register throw event handler
        cltuProvider.setThrowEventOperationHandler((o) -> {
            // Always accept and immediate execution via thread
            new Thread(() -> {
                try {
                    AwaitUtil.await(300);
                    cltuProvider.eventProgress(o.getEventInvocationIdentification().intValue(), true, false);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return CltuThrowEventResult.noError();
        });

        // Throw event (it will succeed)
        recorder.getPduReceived().clear();
        cltuUser.throwEvent(1, 100, new byte[] { 0x00, 0x00 });
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());
        assertNotNull(((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());
        assertEquals(2, ((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getEventInvocationIdentification().intValue());
        assertEquals(1, ((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(1)).getCltuNotification().getEventConditionEvFalse().intValue());

        // Register throw event handler
        cltuProvider.setThrowEventOperationHandler((o) -> {
            // Always accept and immediate execution via thread
            new Thread(() -> {
                try {
                    AwaitUtil.await(300);
                    cltuProvider.eventProgress(o.getEventInvocationIdentification().intValue(), false, false);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
            return CltuThrowEventResult.noError();
        });

        // Throw event (it will succeed)
        recorder.getPduReceived().clear();
        cltuUser.throwEvent(2, 100, new byte[] { 0x00, 0x00 });
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());
        assertNotNull(((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());
        assertEquals(3, ((CltuThrowEventReturn) recorder.getPduReceived().get(0)).getEventInvocationIdentification().intValue());
        assertEquals(2, ((CltuAsyncNotifyInvocation) recorder.getPduReceived().get(1)).getCltuNotification().getActionListNotCompleted().intValue());

        // Unbind
        cltuUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(8000, () -> cltuUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(8000, () -> cltuProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, cltuProvider.getCurrentBindingState());

        cltuUser.dispose();
        cltuProvider.dispose();
    }

    @Test
    void testStartDiagnosticsString() {
        DiagnosticCltuStart diagStart = new DiagnosticCltuStart();
        diagStart.setCommon(new Diagnostics(100));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("duplicateInvokeId"));
        diagStart.setCommon(new Diagnostics(127));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("otherReason"));
        diagStart.setCommon(new Diagnostics(101));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 101"));

        diagStart.setCommon(null);
        diagStart.setSpecific(new BerInteger(0));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("outOfService"));
        diagStart.setSpecific(new BerInteger(1));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("unableToComply"));
        diagStart.setSpecific(new BerInteger(2));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("productionTimeExpired"));
        diagStart.setSpecific(new BerInteger(3));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidCltu"));
        diagStart.setSpecific(new BerInteger(5));
        assertTrue(CltuDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 5"));
    }

    @Test
    void testGetParameterDiagnosticsString() {
        DiagnosticCltuGetParameter diagGet = new DiagnosticCltuGetParameter();
        diagGet.setCommon(new Diagnostics(100));
        assertTrue(CltuDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("duplicateInvokeId"));
        diagGet.setCommon(new Diagnostics(127));
        assertTrue(CltuDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("otherReason"));
        diagGet.setCommon(new Diagnostics(101));
        assertTrue(CltuDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 101"));

        diagGet.setCommon(null);
        diagGet.setSpecific(new BerInteger(0));
        assertTrue(CltuDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("unknownParameter"));
        diagGet.setSpecific(new BerInteger(5));
        assertTrue(CltuDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 5"));
    }

    @Test
    void testTransferDataDiagnosticsString() {
        DiagnosticCltuTransferData diagSched = new DiagnosticCltuTransferData();
        diagSched.setCommon(new Diagnostics(100));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("duplicateInvokeId"));
        diagSched.setCommon(new Diagnostics(127));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("otherReason"));
        diagSched.setCommon(new Diagnostics(101));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("<unknown value> 101"));

        diagSched.setCommon(null);
        diagSched.setSpecific(new BerInteger(0));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("unableToProcess"));
        diagSched.setSpecific(new BerInteger(1));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("unableToStore"));
        diagSched.setSpecific(new BerInteger(2));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("outOfSequence"));
        diagSched.setSpecific(new BerInteger(3));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("inconsistentTimeRange"));
        diagSched.setSpecific(new BerInteger(4));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("invalidTime"));
        diagSched.setSpecific(new BerInteger(5));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("lateSldu"));
        diagSched.setSpecific(new BerInteger(6));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("invalidDelayTime"));
        diagSched.setSpecific(new BerInteger(7));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("cltuError"));
        diagSched.setSpecific(new BerInteger(8));
        assertTrue(CltuDiagnosticsStrings.getTransferDataDiagnostic(diagSched).endsWith("<unknown value> 8"));
    }

    @Test
    void testThrowEventDiagnosticsString() {
        DiagnosticCltuThrowEvent diagSched = new DiagnosticCltuThrowEvent();
        diagSched.setCommon(new Diagnostics(100));
        assertTrue(CltuDiagnosticsStrings.getThrowEventDiagnostic(diagSched).endsWith("duplicateInvokeId"));
        diagSched.setCommon(new Diagnostics(127));
        assertTrue(CltuDiagnosticsStrings.getThrowEventDiagnostic(diagSched).endsWith("otherReason"));
        diagSched.setCommon(new Diagnostics(101));
        assertTrue(CltuDiagnosticsStrings.getThrowEventDiagnostic(diagSched).endsWith("<unknown value> 101"));

        diagSched.setCommon(null);
        diagSched.setSpecific(new BerInteger(0));
        assertTrue(CltuDiagnosticsStrings.getThrowEventDiagnostic(diagSched).endsWith("operationNotSupported"));
        diagSched.setSpecific(new BerInteger(1));
        assertTrue(CltuDiagnosticsStrings.getThrowEventDiagnostic(diagSched).endsWith("eventInvocIdOutOfSequence"));
        diagSched.setSpecific(new BerInteger(2));
        assertTrue(CltuDiagnosticsStrings.getThrowEventDiagnostic(diagSched).endsWith("noSuchEvent"));
        diagSched.setSpecific(new BerInteger(5));
        assertTrue(CltuDiagnosticsStrings.getThrowEventDiagnostic(diagSched).endsWith("<unknown value> 5"));
    }

    @Test
    void testScheduleStatusReportDiagnosticsString() {
        DiagnosticScheduleStatusReport diagSched = new DiagnosticScheduleStatusReport();
        diagSched.setCommon(new Diagnostics(100));
        assertTrue(CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("duplicateInvokeId"));
        diagSched.setCommon(new Diagnostics(127));
        assertTrue(CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("otherReason"));
        diagSched.setCommon(new Diagnostics(101));
        assertTrue(CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 101"));

        diagSched.setCommon(null);
        diagSched.setSpecific(new BerInteger(0));
        assertTrue(CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("notSupportedInThisDeliveryMode"));
        diagSched.setSpecific(new BerInteger(1));
        assertTrue(CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("alreadyStopped"));
        diagSched.setSpecific(new BerInteger(2));
        assertTrue(CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("invalidReportingCycle"));
        diagSched.setSpecific(new BerInteger(5));
        assertTrue(CltuDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 5"));
    }

    @Test
    void testGenericNegativeDiagnosticsString() {
        assertTrue(CltuDiagnosticsStrings.getDiagnostic(new Diagnostics(100)).endsWith("duplicateInvokeId"));
        assertTrue(CltuDiagnosticsStrings.getDiagnostic(new Diagnostics(127)).endsWith("otherReason"));
        assertTrue(CltuDiagnosticsStrings.getDiagnostic(new Diagnostics(101)).endsWith("<unknown value> 101"));
    }

    @Test
    void testDataStructure() {
        CltuLastOk lastOk = new CltuLastOk(1L, new Date());
        assertDoesNotThrow(lastOk::toString);

        CltuLastProcessed lastProcessed = new CltuLastProcessed(1L, new Date(), CltuStatusEnum.RADIATED);
        assertDoesNotThrow(lastProcessed::toString);

        CltuNotification notification = new CltuNotification(CltuNotification.CltuNotificationTypeEnum.ACTION_LIST_NOT_COMPLETED, 1L);
        assertDoesNotThrow(notification::toString);
        assertEquals(1L, notification.getEventInvocationId());
        assertEquals(CltuNotification.CltuNotificationTypeEnum.ACTION_LIST_NOT_COMPLETED, notification.getType());
    }

    @Test
    void testConfiguration() {
        CltuServiceInstanceConfiguration configuration = new CltuServiceInstanceConfiguration();
        CltuServiceInstanceConfiguration configuration2 = new CltuServiceInstanceConfiguration();
        assertNull(configuration.getStartTime());
        assertNull(configuration.getEndTime());
        assertEquals(configuration2.hashCode(), configuration.hashCode());
    }
}
