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
import com.beanit.jasn1.ber.types.BerNull;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.DiagnosticScheduleStatusReport;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Diagnostics;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfGetParameterReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfGetParameterReturnV1toV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfStartReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfSyncNotifyInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.structures.DiagnosticRocfGet;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.structures.DiagnosticRocfStart;
import eu.dariolucia.ccsds.sle.utl.OperationRecorder;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.rocf.RocfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.rocf.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class RocfTest {

    @BeforeAll
    static void setLogLevel() {
        Logger.getLogger("eu.dariolucia").setLevel(Level.ALL);
        Arrays.stream(Logger.getLogger("eu.dariolucia").getHandlers()).forEach(o -> o.setLevel(Level.ALL));
    }
    
    @Test
    void testProviderBindUnbind() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigU = (RocfServiceInstanceConfiguration) userFile.getServiceInstances().get(9); // ROCF
        RocfServiceInstance rocfUser = new RocfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rocfUser.configure();
        rocfUser.setUnbindReturnBehaviour(true);
        rocfUser.waitForBind(true, null);

        // Provider
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigP = (RocfServiceInstanceConfiguration) providerFile.getServiceInstances().get(9); // ROCF
        RocfServiceInstanceProvider rocfProvider = new RocfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rocfProvider.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rocfUser.register(recorder);

        rocfProvider.bind(2);

        AwaitUtil.awaitCondition(2000, () -> rocfUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rocfUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rocfProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rocfProvider.getCurrentBindingState());

        // The next two calls must be ignored by the implementation
        rocfUser.waitForBind(true, null);
        rocfProvider.bind(2);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rocfUser.getCurrentBindingState());
        assertEquals(ServiceInstanceBindingStateEnum.READY, rocfProvider.getCurrentBindingState());

        rocfProvider.unbind(UnbindReasonEnum.SUSPEND);

        AwaitUtil.awaitCondition(2000, () -> rocfUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND_WAIT);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND_WAIT, rocfUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rocfProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfProvider.getCurrentBindingState());

        rocfUser.dispose();
        rocfProvider.dispose();
    }

    @Test
    void testNegativeOperationSequences() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigU = (RocfServiceInstanceConfiguration) userFile.getServiceInstances().get(7); // ROCF OLT
        RocfServiceInstance rocfUser = new RocfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rocfUser.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rocfUser.register(recorder);

        // Start
        rocfUser.start(null, null, new GVCID(100, 0, 1), 1, RocfControlWordTypeEnum.CLCW, RocfUpdateModeEnum.CONTINUOUS);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Stop
        rocfUser.stop();
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Get parameter
        rocfUser.getParameter(RocfParameterEnum.BUFFER_SIZE);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Unbind
        rocfUser.unbind(UnbindReasonEnum.SUSPEND);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Peer abort
        rocfUser.peerAbort(PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Schedule status report
        rocfUser.scheduleStatusReport(false, 20);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        rocfUser.deregister(recorder);

        rocfUser.dispose();
    }

    @Test
    void testStatusReportGetParameterV4() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigP = (RocfServiceInstanceConfiguration) providerFile.getServiceInstances().get(7); // ROCF
        RocfServiceInstanceProvider rocfProvider = new RocfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rocfProvider.configure();
        rocfProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigU = (RocfServiceInstanceConfiguration) userFile.getServiceInstances().get(7); // ROCF
        RocfServiceInstance rocfUser = new RocfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rocfUser.configure();
        rocfUser.bind(4);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rocfUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rocfUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(DeliveryModeEnum.TIMELY_ONLINE.ordinal(), ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter requested GVCID: expect positive return, undefined result
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_GVCID);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue().getUndefined());

        // Ask for parameter permitted GVCID: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_GVCID_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Ask for parameter permitted control word type: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_CONTROL_WORD_TYPE_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedRprtTypeSet().getParameterValue());

        // Ask for parameter permitted update mode: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_UPDATE_MODE_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedUpdModeSet().getParameterValue());

        // Ask for parameter permitted TC VC ID: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_TC_VCID_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rocfUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rocfUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rocfUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rocfUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rocfProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfProvider.getCurrentBindingState());

        rocfUser.dispose();
        rocfProvider.dispose();
    }

    @Test
    void testStatusReportGetParameterWithAuthorizationV5() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider_auth.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigP = (RocfServiceInstanceConfiguration) providerFile.getServiceInstances().get(7); // RCF
        RocfServiceInstanceProvider rocfProvider = new RocfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rocfProvider.configure();
        rocfProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user_auth.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigU = (RocfServiceInstanceConfiguration) userFile.getServiceInstances().get(7); // RCF
        RocfServiceInstance rocfUser = new RocfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rocfUser.configure();
        rocfUser.bind(5);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rocfUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rocfUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(DeliveryModeEnum.TIMELY_ONLINE.ordinal(), ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter requested GVCID: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_GVCID);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue());

        // Ask for parameter permitted GVCID: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_GVCID_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMinReportingCycle().getParameterValue().intValue());

        // Ask for parameter permitted control word type: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_CONTROL_WORD_TYPE_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedRprtTypeSet().getParameterValue());

        // Ask for parameter permitted update mode: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_UPDATE_MODE_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedUpdModeSet().getParameterValue());

        // Ask for parameter permitted TC VC ID: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.PERMITTED_TC_VCID_SET);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedTcVcidSet().getParameterValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rocfUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rocfUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rocfUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rocfUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rocfProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfProvider.getCurrentBindingState());

        rocfUser.dispose();
        rocfProvider.dispose();
    }

    @Test
    void testNegativeStartAndSchedule() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigP = (RocfServiceInstanceConfiguration) providerFile.getServiceInstances().get(7); // ROCF
        RocfServiceInstanceProvider rocfProvider = new RocfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rocfProvider.configure();
        rocfProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigU = (RocfServiceInstanceConfiguration) userFile.getServiceInstances().get(7); // ROCF
        RocfServiceInstance rocfUser = new RocfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rocfUser.configure();
        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rocfUser.register(recorder);

        rocfUser.bind(2);

        // Check reported state
        AwaitUtil.await(2000);
        assertTrue(recorder.getStates().size() > 0);
        assertEquals(ServiceInstanceBindingStateEnum.READY, recorder.getStates().get(recorder.getStates().size() - 1).getState());

        // Test negative start
        rocfProvider.setStartOperationHandler(o -> false);
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        rocfUser.start(null, null, new GVCID(100, 0, 1), 1, RocfControlWordTypeEnum.CLCW, RocfUpdateModeEnum.CONTINUOUS);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, recorder.getPduSent().size());
        assertNotNull(((RocfStartReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());

        // Test negative schedule status report
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        rocfUser.scheduleStatusReport(true, 20);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, recorder.getPduSent().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());

        // Unbind
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        rocfUser.unbind(UnbindReasonEnum.END);
        AwaitUtil.awaitCondition(2000, () -> rocfUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rocfProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfProvider.getCurrentBindingState());

        rocfUser.dispose();
        rocfProvider.dispose();
    }

    @Test
    void testTransferDataCompleteV2() throws IOException, InterruptedException {
        testTransferDataComplete(2);
    }

    @Test
    void testTransferDataCompleteV5() throws IOException, InterruptedException {
        testTransferDataComplete(5);
    }

    private void testTransferDataComplete(int version) throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigP = (RocfServiceInstanceConfiguration) providerFile.getServiceInstances().get(8); // ROCF ONLC
        RocfServiceInstanceProvider rocfProvider = new RocfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rocfProvider.configure();
        rocfProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigU = (RocfServiceInstanceConfiguration) userFile.getServiceInstances().get(8); // ROCF ONLC
        RocfServiceInstance rocfUser = new RocfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rocfUser.configure();
        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rocfUser.register(recorder);

        rocfUser.bind(version);

        // Check reported state
        AwaitUtil.await(2000);
        assertTrue(recorder.getStates().size() > 0);
        assertEquals(ServiceInstanceBindingStateEnum.READY, recorder.getStates().get(recorder.getStates().size() - 1).getState());

        // Start
        recorder.getPduReceived().clear();
        rocfUser.start(null, null, new GVCID(100, 0, 1), 1, RocfControlWordTypeEnum.CLCW, RocfUpdateModeEnum.CONTINUOUS);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RocfStartReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Simulate lock and production status change
        recorder.getPduReceived().clear();
        rocfProvider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, ProductionStatusEnum.RUNNING);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(ProductionStatusEnum.RUNNING.ordinal(), ((RocfSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getProductionStatusChange().intValue());

        // Ask for parameter requested GVCID: expect positive return, good result
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_GVCID);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid());

        // Ask for parameter requested TC VC ID, expected positive return, 1
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_TC_VCID);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqTcVcid().getParameterValue().getTcVcid().getTcVcid().intValue());

        // Ask for parameter requested OCF type, expected positive return, CLCW
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_CONTROL_WORD_TYPE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(RocfControlWordTypeEnum.CLCW.ordinal(), ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqControlWordType().getParameterValue().intValue());

        // Ask for parameter requested update mode, expected positive return, continuous
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_UPDATE_MODE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(RocfUpdateModeEnum.CONTINUOUS.ordinal(), ((RocfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqUpdateMode().getParameterValue().intValue());

        // Send 100 transfer data, fast
        byte[] frame = new byte[]{0x06, (byte) 0x42, 0x00, 0x00,     0x00, 0x04, 0x00, 0x00}; // TFVN 0, SCID 100, VCID 1 -- CLCW, TC VC ID 1
        byte[] badFrame = new byte[]{0x06, (byte) 0x92, 0x00, 0x00,     0x00, 0x04, 0x00, 0x00}; // TFVN 0, SCID wrong, VCID 1 -- CLCW, TC VC ID 1
        recorder.getPduReceived().clear();
        for (int i = 0; i < 100; ++i) {
            rocfProvider.transferData(frame, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
        }
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 100);
        assertEquals(100, recorder.getPduReceived().size());

        // Send 5 transfer data now, fast, one not in the GVCID, then wait for the buffer anyway (latency)
        recorder.getPduReceived().clear();
        for (int i = 0; i < 5; ++i) {
            if (i == 0) {
                rocfProvider.transferData(badFrame, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
            } else {
                rocfProvider.transferData(frame, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
            }
        }
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 4);
        assertEquals(4, recorder.getPduReceived().size());

        assertTrue(recorder.getStates().size() > 0);
        assertNotNull(recorder.getStates().get(0).toString());

        // Get data rate
        RateSample rs = rocfUser.getCurrentRate();
        assertTrue(rs.getByteSample().getInRate() > 0);
        assertTrue(rs.getByteSample().getTotalInUnits() > 0);

        assertEquals(0, rs.getPduSample().getInRate()); // Cannot be computed at first sampling
        assertTrue(rs.getPduSample().getTotalInUnits() > 0);

        assertNotNull(rs.getInstant());
        assertNotNull(rs.toCompactByteRateString());

        // Send end of data
        recorder.getPduReceived().clear();
        rocfProvider.endOfData();
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RocfSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getEndOfData());

        // Simulate unlock
        recorder.getPduReceived().clear();
        rocfProvider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, ProductionStatusEnum.RUNNING);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(LockStatusEnum.OUT_OF_LOCK.ordinal(), ((RocfSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getLossFrameSync().getSymbolSyncLockStatus().intValue());

        // Stop
        recorder.getPduReceived().clear();
        rocfUser.stop();
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleAcknowledgement) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rocfUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rocfUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rocfProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfProvider.getCurrentBindingState());

        rocfUser.dispose();
        rocfProvider.dispose();
    }

    @Test
    void testTransferDataTimelyWithAuthorization() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider_auth.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigP = (RocfServiceInstanceConfiguration) providerFile.getServiceInstances().get(7); // RCF
        RocfServiceInstanceProvider rocfProvider = new RocfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rocfProvider.configure();
        rocfProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user_auth.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RocfServiceInstanceConfiguration rafConfigU = (RocfServiceInstanceConfiguration) userFile.getServiceInstances().get(7); // RCF
        RocfServiceInstance rocfUser = new RocfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rocfUser.configure();
        rocfUser.bind(5);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rocfUser.register(recorder);

        // Start
        recorder.getPduReceived().clear();
        rocfUser.start(new Date(1000000), new Date(System.currentTimeMillis() + 1000000000), new GVCID(100, 0, 1), 1, RocfControlWordTypeEnum.CLCW, RocfUpdateModeEnum.CONTINUOUS);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RocfStartReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Ask for parameter requested TC VC ID, expected positive return, 1
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_TC_VCID);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqTcVcid().getParameterValue().getTcVcid().intValue());

        // Ask for parameter requested OCF type, expected positive return, CLCW
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_CONTROL_WORD_TYPE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(RocfControlWordTypeEnum.CLCW.ordinal(), ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqControlWordType().getParameterValue().intValue());

        // Ask for parameter requested update mode, expected positive return, continuous
        recorder.getPduReceived().clear();
        rocfUser.getParameter(RocfParameterEnum.REQUESTED_UPDATE_MODE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(RocfUpdateModeEnum.CONTINUOUS.ordinal(), ((RocfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqUpdateMode().getParameterValue().intValue());


        // Send 500 transfer data, fast
        byte[] frame = new byte[]{0x06, (byte) 0x42, 0x00, 0x00,     0x00, 0x04, 0x00, 0x00}; // TFVN 0, SCID 100, VCID 1 -- CLCW, TC VC ID 1
        byte[] badFrame = new byte[]{0x06, (byte) 0x92, 0x00, 0x00,     0x00, 0x04, 0x00, 0x00}; // TFVN 0, SCID wrong, VCID 1 -- CLCW, TC VC ID 1

        recorder.getPduReceived().clear();
        for (int i = 0; i < 500; ++i) {
            rocfProvider.transferData(frame, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
        }
        // One data discarded
        rocfProvider.dataDiscarded();

        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() > 100);
        assertTrue(recorder.getPduReceived().size() <= 501);
        assertTrue(recorder.getPduReceived().size() > 0);
        // Wait the final delivery
        AwaitUtil.await(5000);
        // Send 5 transfer data now, fast, one bad, then wait for the buffer anyway (latency)
        recorder.getPduReceived().clear();
        for (int i = 0; i < 5; ++i) {
            if (i == 0) {
                rocfProvider.transferData(badFrame, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
            } else {
                rocfProvider.transferData(frame, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
            }
        }
        // AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 5);
        // assertEquals(5, recorder.getPduReceived().size());
        AwaitUtil.await(4000);
        // Stop
        recorder.getPduReceived().clear();
        rocfUser.stop();
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((SleAcknowledgement) recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());
        assertNotNull(recorder.getStates().get(0).toString());

        // Unbind
        rocfUser.peerAbort(PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);

        AwaitUtil.awaitCondition(2000, () -> rocfUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rocfProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rocfProvider.getCurrentBindingState());

        rocfUser.dispose();
        rocfProvider.dispose();
    }

    @Test
    void testStartDiagnosticsString() {
        DiagnosticRocfStart diagStart = new DiagnosticRocfStart();
        diagStart.setCommon(new Diagnostics(100));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("duplicateInvokeId"));
        diagStart.setCommon(new Diagnostics(127));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("otherReason"));
        diagStart.setCommon(new Diagnostics(101));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 101"));

        diagStart.setCommon(null);
        diagStart.setSpecific(new BerInteger(0));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("outOfService"));
        diagStart.setSpecific(new BerInteger(1));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("unableToComply"));
        diagStart.setSpecific(new BerInteger(2));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidStartTime"));
        diagStart.setSpecific(new BerInteger(3));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidStopTime"));
        diagStart.setSpecific(new BerInteger(4));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("missingTimeValue"));
        diagStart.setSpecific(new BerInteger(5));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidGvcId"));
        diagStart.setSpecific(new BerInteger(6));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidControlWordType"));
        diagStart.setSpecific(new BerInteger(7));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidTcVcid"));
        diagStart.setSpecific(new BerInteger(8));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidUpdateMode"));
        diagStart.setSpecific(new BerInteger(9));
        assertTrue(RocfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 9"));
    }

    @Test
    void testGetParameterDiagnosticsString() {
        DiagnosticRocfGet diagGet = new DiagnosticRocfGet();
        diagGet.setCommon(new Diagnostics(100));
        assertTrue(RocfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("duplicateInvokeId"));
        diagGet.setCommon(new Diagnostics(127));
        assertTrue(RocfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("otherReason"));
        diagGet.setCommon(new Diagnostics(101));
        assertTrue(RocfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 101"));

        diagGet.setCommon(null);
        diagGet.setSpecific(new BerInteger(0));
        assertTrue(RocfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("unknownParameter"));
        diagGet.setSpecific(new BerInteger(5));
        assertTrue(RocfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 5"));
    }

    @Test
    void testScheduleStatusReportDiagnosticsString() {
        DiagnosticScheduleStatusReport diagSched = new DiagnosticScheduleStatusReport();
        diagSched.setCommon(new Diagnostics(100));
        assertTrue(RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("duplicateInvokeId"));
        diagSched.setCommon(new Diagnostics(127));
        assertTrue(RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("otherReason"));
        diagSched.setCommon(new Diagnostics(101));
        assertTrue(RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 101"));

        diagSched.setCommon(null);
        diagSched.setSpecific(new BerInteger(0));
        assertTrue(RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("notSupportedInThisDeliveryMode"));
        diagSched.setSpecific(new BerInteger(1));
        assertTrue(RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("alreadyStopped"));
        diagSched.setSpecific(new BerInteger(2));
        assertTrue(RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("invalidReportingCycle"));
        diagSched.setSpecific(new BerInteger(5));
        assertTrue(RocfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 5"));
    }

    @Test
    void testGenericNegativeDiagnosticsString() {
        assertTrue(RocfDiagnosticsStrings.getDiagnostic(new Diagnostics(100)).endsWith("duplicateInvokeId"));
        assertTrue(RocfDiagnosticsStrings.getDiagnostic(new Diagnostics(127)).endsWith("otherReason"));
        assertTrue(RocfDiagnosticsStrings.getDiagnostic(new Diagnostics(101)).endsWith("<unknown value> 101"));
    }

    @Test
    void testConfiguration() {
        RocfServiceInstanceConfiguration configuration = new RocfServiceInstanceConfiguration();
        RocfServiceInstanceConfiguration configuration2 = new RocfServiceInstanceConfiguration();
        assertNull(configuration.getStartTime());
        assertNull(configuration.getEndTime());
        assertEquals(configuration2.hashCode(), configuration.hashCode());
    }
}
