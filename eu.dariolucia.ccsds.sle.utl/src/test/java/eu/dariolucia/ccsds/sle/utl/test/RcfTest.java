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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfGetParameterReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfGetParameterReturnV1toV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfStartReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfSyncNotifyInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.DiagnosticRcfGet;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.DiagnosticRcfStart;
import eu.dariolucia.ccsds.sle.utl.OperationRecorder;
import eu.dariolucia.ccsds.sle.utl.si.ReturnServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.si.rcf.*;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.rcf.RcfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.*;
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

public class RcfTest {

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
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(6); // RCF
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.setUnbindReturnBehaviour(true);
        rafUser.waitForBind(true, null);

        // Provider
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigP = (RcfServiceInstanceConfiguration) providerFile.getServiceInstances().get(6); // RCF
        RcfServiceInstanceProvider rafProvider = new RcfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        rafProvider.bind(2);

        AwaitUtil.awaitCondition(4000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rafProvider.getCurrentBindingState());

        // The next two calls must be ignored by the implementation
        rafUser.waitForBind(true, null);
        rafProvider.bind(2);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rafUser.getCurrentBindingState());
        assertEquals(ServiceInstanceBindingStateEnum.READY, rafProvider.getCurrentBindingState());

        rafProvider.unbind(UnbindReasonEnum.SUSPEND);

        AwaitUtil.awaitCondition(4000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND_WAIT);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND_WAIT, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        assertFalse(recorder.getStates().isEmpty());
        assertNotNull(recorder.getStates().get(0).toString());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testNegativeOperationSequences() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(4); // RCF OLT
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Start
        rafUser.start(null, null, new GVCID(100, 0, 1));
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Stop
        rafUser.stop();
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Get parameter
        rafUser.getParameter(RcfParameterEnum.BUFFER_SIZE);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.SUSPEND);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Peer abort
        rafUser.peerAbort(PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Schedule status report
        rafUser.scheduleStatusReport(false, 20);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        rafUser.deregister(recorder);

        rafUser.dispose();
    }

    @Test
    void testStatusReportGetParameterV1() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigP = (RcfServiceInstanceConfiguration) providerFile.getServiceInstances().get(4); // RCF
        RcfServiceInstanceProvider rafProvider = new RcfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(4); // RCF
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.bind(1);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rafUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter requested GVCID: expect positive return, undefined result
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.REQUESTED_GVCID);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue().getUndefined());

        // Ask for parameter permitted GVCID: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.PERMITTED_GVCID_SET);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(4000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testStatusReportGetParameterV4() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigP = (RcfServiceInstanceConfiguration) providerFile.getServiceInstances().get(4); // RCF
        RcfServiceInstanceProvider rafProvider = new RcfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(4); // RCF
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.bind(4);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rafUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(DeliveryModeEnum.TIMELY_ONLINE.ordinal(), ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter requested GVCID: expect positive return, undefined result
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.REQUESTED_GVCID);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue().getUndefined());

        // Ask for parameter permitted GVCID: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.PERMITTED_GVCID_SET);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(4000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testStatusReportGetParameterWithAuthorizationV5() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider_auth.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigP = (RcfServiceInstanceConfiguration) providerFile.getServiceInstances().get(4); // RCF
        RcfServiceInstanceProvider rafProvider = new RcfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user_auth.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(4); // RCF
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.bind(5);

        AwaitUtil.await(4000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rafUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(DeliveryModeEnum.TIMELY_ONLINE.ordinal(), ((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter requested GVCID: expect positive return, undefined result
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.REQUESTED_GVCID);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue().getUndefined());

        // Ask for parameter permitted GVCID: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.PERMITTED_GVCID_SET);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedGvcidSet().getParameterValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMinReportingCycle().getParameterValue().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(8000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(8000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(8000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testNegativeStartAndSchedule() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigP = (RcfServiceInstanceConfiguration) providerFile.getServiceInstances().get(4); // RCF
        RcfServiceInstanceProvider rafProvider = new RcfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(4); // RCF
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        rafUser.bind(2);

        // Check reported state
        AwaitUtil.await(2000);
        assertTrue(recorder.getStates().size() > 0);
        assertEquals(ServiceInstanceBindingStateEnum.READY, recorder.getStates().get(recorder.getStates().size() - 1).getState());

        // Test negative start
        rafProvider.setStartOperationHandler(o -> RcfStartResult.errorSpecific(RcfStartDiagnosticsEnum.INVALID_STOP_TIME));
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        rafUser.start(null, null, new GVCID(100, 0, 1));
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, recorder.getPduSent().size());
        assertNotNull(((RcfStartReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());

        // Test negative schedule status report
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, 20);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, recorder.getPduSent().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());

        // Unbind
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        rafUser.unbind(UnbindReasonEnum.END);
        AwaitUtil.awaitCondition(4000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testTransferDataCompleteV1() throws IOException, InterruptedException {
        testTransferDataComplete(1);
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
        RcfServiceInstanceConfiguration rafConfigP = (RcfServiceInstanceConfiguration) providerFile.getServiceInstances().get(5); // RCF ONLC
        RcfServiceInstanceProvider rafProvider = new RcfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(5); // RCF ONLC
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        rafUser.bind(version);

        // Check reported state
        AwaitUtil.await(2000);
        assertTrue(recorder.getStates().size() > 0);
        assertEquals(ServiceInstanceBindingStateEnum.READY, recorder.getStates().get(recorder.getStates().size() - 1).getState());

        // Start
        recorder.getPduReceived().clear();
        rafUser.start(null, null, new GVCID(100, 0, 1));
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RcfStartReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Simulate lock and production status change
        recorder.getPduReceived().clear();
        rafProvider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, ProductionStatusEnum.RUNNING);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(ProductionStatusEnum.RUNNING.ordinal(), ((RcfSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getProductionStatusChange().intValue());

        // Ask for parameter requested GVCID: expect positive return, good result
        recorder.getPduReceived().clear();
        rafUser.getParameter(RcfParameterEnum.REQUESTED_GVCID);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        if(version <= 4) {
            assertNotNull(((RcfGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid());
        } else {
            assertNotNull(((RcfGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqGvcId().getParameterValue().getGvcid());
        }
        // Send 100 transfer data, fast
        byte[] frame = new byte[]{0x06, (byte) 0x42, 0x00, 0x00}; // TFVN 0, SCID 100, VCID 1
        byte[] badFrame = new byte[]{0x06, (byte) 0x92, 0x00, 0x00}; // TFVN 0, SCID wrong, VCID 1
        recorder.getPduReceived().clear();
        for (int i = 0; i < 100; ++i) {
            rafProvider.transferData(frame, ReturnServiceInstanceProvider.FRAME_QUALITY_GOOD, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
        }
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 100);
        assertEquals(100, recorder.getPduReceived().size());

        // Send 5 transfer data now, fast, one not in the GVCID, then wait for the buffer anyway (latency)
        recorder.getPduReceived().clear();
        for (int i = 0; i < 5; ++i) {
            if (i == 0) {
                rafProvider.transferData(badFrame, ReturnServiceInstanceProvider.FRAME_QUALITY_GOOD, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
            } else {
                rafProvider.transferData(frame, ReturnServiceInstanceProvider.FRAME_QUALITY_GOOD, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
            }
        }
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 4);
        assertEquals(4, recorder.getPduReceived().size());

        assertTrue(recorder.getStates().size() > 0);
        assertNotNull(recorder.getStates().get(0).toString());

        // Get data rate
        RateSample rs = rafUser.getCurrentRate();
        assertTrue(rs.getByteSample().getInRate() > 0);
        assertTrue(rs.getByteSample().getTotalInUnits() > 0);

        assertEquals(0, rs.getPduSample().getInRate()); // Cannot be computed at first sampling
        assertTrue(rs.getPduSample().getTotalInUnits() > 0);

        assertNotNull(rs.getInstant());
        assertNotNull(rs.toCompactByteRateString());

        // Send end of data
        recorder.getPduReceived().clear();
        rafProvider.endOfData();
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RcfSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getEndOfData());

        // Simulate unlock
        recorder.getPduReceived().clear();
        rafProvider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, ProductionStatusEnum.RUNNING);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(LockStatusEnum.OUT_OF_LOCK.ordinal(), ((RcfSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getLossFrameSync().getSymbolSyncLockStatus().intValue());

        // Stop
        recorder.getPduReceived().clear();
        rafUser.stop();
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleAcknowledgement) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(4000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testTransferDataTimelyWithAuthorization() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider_auth.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigP = (RcfServiceInstanceConfiguration) providerFile.getServiceInstances().get(4); // RCF
        RcfServiceInstanceProvider rafProvider = new RcfServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user_auth.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RcfServiceInstanceConfiguration rafConfigU = (RcfServiceInstanceConfiguration) userFile.getServiceInstances().get(4); // RCF
        RcfServiceInstance rafUser = new RcfServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.bind(2);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Start
        recorder.getPduReceived().clear();
        rafUser.start(new Date(1000000), new Date(System.currentTimeMillis() + 1000000000), new GVCID(100, 0, 1));
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RcfStartReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Send 500 transfer data, fast
        byte[] frame = new byte[]{0x06, (byte) 0x42, 0x00, 0x00}; // TFVN 0, SCID 100, VCID 1
        byte[] badFrame = new byte[]{0x06, (byte) 0x82, 0x00, 0x00}; // TFVN 0, SCID wrong, VCID 1

        recorder.getPduReceived().clear();
        for (int i = 0; i < 500; ++i) {
            rafProvider.transferData(frame, ReturnServiceInstanceProvider.FRAME_QUALITY_GOOD, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
        }
        // One data discarded
        rafProvider.dataDiscarded();

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() > 100);
        assertTrue(recorder.getPduReceived().size() <= 501);
        assertTrue(recorder.getPduReceived().size() > 0);
        // Wait the final delivery
        AwaitUtil.await(5000);
        // Send 5 transfer data now, fast, one bad, then wait for the buffer anyway (latency)
        recorder.getPduReceived().clear();
        for (int i = 0; i < 5; ++i) {
            if (i == 0) {
                rafProvider.transferData(badFrame, ReturnServiceInstanceProvider.FRAME_QUALITY_GOOD, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
            } else {
                rafProvider.transferData(frame, ReturnServiceInstanceProvider.FRAME_QUALITY_GOOD, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
            }
        }
        // AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 5);
        // assertEquals(5, recorder.getPduReceived().size());
        AwaitUtil.await(4000);
        // Stop
        recorder.getPduReceived().clear();
        rafUser.stop();
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((SleAcknowledgement) recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());
        assertNotNull(recorder.getStates().get(0).toString());

        // Unbind
        rafUser.peerAbort(PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);

        AwaitUtil.awaitCondition(4000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testStartDiagnosticsString() {
        DiagnosticRcfStart diagStart = new DiagnosticRcfStart();
        diagStart.setCommon(new Diagnostics(100));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("duplicateInvokeId"));
        diagStart.setCommon(new Diagnostics(127));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("otherReason"));
        diagStart.setCommon(new Diagnostics(101));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 101"));

        diagStart.setCommon(null);
        diagStart.setSpecific(new BerInteger(0));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("outOfService"));
        diagStart.setSpecific(new BerInteger(1));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("unableToComply"));
        diagStart.setSpecific(new BerInteger(2));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidStartTime"));
        diagStart.setSpecific(new BerInteger(3));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidStopTime"));
        diagStart.setSpecific(new BerInteger(4));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("missingTimeValue"));
        diagStart.setSpecific(new BerInteger(5));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidGvcId"));
        diagStart.setSpecific(new BerInteger(6));
        assertTrue(RcfDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 6"));
    }

    @Test
    void testGetParameterDiagnosticsString() {
        DiagnosticRcfGet diagGet = new DiagnosticRcfGet();
        diagGet.setCommon(new Diagnostics(100));
        assertTrue(RcfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("duplicateInvokeId"));
        diagGet.setCommon(new Diagnostics(127));
        assertTrue(RcfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("otherReason"));
        diagGet.setCommon(new Diagnostics(101));
        assertTrue(RcfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 101"));

        diagGet.setCommon(null);
        diagGet.setSpecific(new BerInteger(0));
        assertTrue(RcfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("unknownParameter"));
        diagGet.setSpecific(new BerInteger(5));
        assertTrue(RcfDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 5"));
    }

    @Test
    void testScheduleStatusReportDiagnosticsString() {
        DiagnosticScheduleStatusReport diagSched = new DiagnosticScheduleStatusReport();
        diagSched.setCommon(new Diagnostics(100));
        assertTrue(RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("duplicateInvokeId"));
        diagSched.setCommon(new Diagnostics(127));
        assertTrue(RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("otherReason"));
        diagSched.setCommon(new Diagnostics(101));
        assertTrue(RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 101"));

        diagSched.setCommon(null);
        diagSched.setSpecific(new BerInteger(0));
        assertTrue(RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("notSupportedInThisDeliveryMode"));
        diagSched.setSpecific(new BerInteger(1));
        assertTrue(RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("alreadyStopped"));
        diagSched.setSpecific(new BerInteger(2));
        assertTrue(RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("invalidReportingCycle"));
        diagSched.setSpecific(new BerInteger(5));
        assertTrue(RcfDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 5"));
    }

    @Test
    void testGenericNegativeDiagnosticsString() {
        assertTrue(RcfDiagnosticsStrings.getDiagnostic(new Diagnostics(100)).endsWith("duplicateInvokeId"));
        assertTrue(RcfDiagnosticsStrings.getDiagnostic(new Diagnostics(127)).endsWith("otherReason"));
        assertTrue(RcfDiagnosticsStrings.getDiagnostic(new Diagnostics(101)).endsWith("<unknown value> 101"));
    }

    @Test
    void testConfiguration() {
        RcfServiceInstanceConfiguration configuration = new RcfServiceInstanceConfiguration();
        RcfServiceInstanceConfiguration configuration2 = new RcfServiceInstanceConfiguration();
        assertNull(configuration.getStartTime());
        assertNull(configuration.getEndTime());
        assertEquals(configuration2.hashCode(), configuration.hashCode());
    }
}
