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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.DiagnosticScheduleStatusReport;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Diagnostics;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafGetParameterReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafGetParameterReturnV1toV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafStartReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafSyncNotifyInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures.DiagnosticRafGet;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures.DiagnosticRafStart;
import eu.dariolucia.ccsds.sle.utl.OperationRecorder;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafDiagnosticsStrings;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstance;
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

public class RafTest {

    @BeforeAll
    static void setLogLevel() {
        Logger.getLogger("eu.dariolucia").setLevel(Level.ALL);
        Arrays.stream(Logger.getLogger("eu.dariolucia").getHandlers()).forEach(o -> o.setLevel(Level.ALL));
    }

    @Test
    void testWrongUIBPIB1() throws IOException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(2); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        assertThrows(IllegalStateException.class, rafProvider::configure);

        rafProvider.waitForBind(true, null);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(2); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        assertThrows(IllegalStateException.class, rafUser::stop);
        rafUser.configure();
        rafUser.setUnbindReturnBehaviour(true);
        rafUser.bind(2);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
    }

    @Test
    void testWrongUIBPIB2() throws IOException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.setUnbindReturnBehaviour(true);
        rafUser.waitForBind(true, null);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());

        // Provider
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.bind(2);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());
    }

    @Test
    void testProviderBindUnbind() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(2); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.setUnbindReturnBehaviour(true);
        rafUser.waitForBind(true, null);

        // Provider
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(2); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();

        assertNotNull(rafUser.getServiceInstanceConfiguration());

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
    void testNegativeBind() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);
        rafProvider.setBindReturnBehaviour(false, BindDiagnosticsEnum.INCONSISTENT_SERVICE_TYPE);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Bind
        rafUser.bind(2);
        AwaitUtil.await(2000);
        AwaitUtil.awaitCondition(4000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(1, recorder.getPduReceived().size());
        assertTrue(recorder.getPduReceived().get(0) instanceof SleBindReturn);
        assertEquals(BindDiagnosticsEnum.INCONSISTENT_SERVICE_TYPE.getCode(), ((SleBindReturn) recorder.getPduReceived().get(0)).getResult().getNegative().intValue());

        rafUser.deregister(recorder);

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testNegativeOperationSequences() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Start
        rafUser.start(null, null, RafRequestedFrameQualityEnum.ALL_FRAMES);
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Stop
        rafUser.stop();
        AwaitUtil.await(1000);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(0, recorder.getPduSent().size());

        // Get parameter
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);
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
    void testStatusReportGetParameterV2() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.bind(2);

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
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(DeliveryModeEnum.COMPLETE_ONLINE.ordinal(), ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REQUESTED_FRAME_QUALITY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

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
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
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

        // Ask for parameter reporting cycle: expect positive return, no value
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOff());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REQUESTED_FRAME_QUALITY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

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
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user_auth.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.bind(5);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rafUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return, no value
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOff());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter delivery mode: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.DELIVERY_MODE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(DeliveryModeEnum.COMPLETE_ONLINE.ordinal(), ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParDeliveryMode().getParameterValue().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REQUESTED_FRAME_QUALITY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMinReportingCycle().getParameterValue().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.PERMITTED_FRAME_QUALITY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParPermittedFrameQuality().getParameterValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

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
    void testNegativeStartAndSchedule() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
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
        rafProvider.setStartOperationHandler(o -> false);
        recorder.getPduSent().clear();
        recorder.getPduReceived().clear();
        rafUser.start(null, null, RafRequestedFrameQualityEnum.ALL_FRAMES);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(1, recorder.getPduSent().size());
        assertNotNull(((RafStartReturn) recorder.getPduReceived().get(0)).getResult().getNegativeResult());

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
    public void testTransferDataCompleteV2() throws IOException, InterruptedException {
        testTransferDataComplete(2);
    }

    @Test
    public void testTransferDataCompleteV4() throws IOException, InterruptedException {
        testTransferDataComplete(4);
    }

    @Test
    public void testTransferDataCompleteV5() throws IOException, InterruptedException {
        testTransferDataComplete(5);
    }

    private void testTransferDataComplete(int version) throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
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
        rafUser.start(null, null, RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RafStartReturn)recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Simulate lock and production status change
        recorder.getPduReceived().clear();
        rafProvider.updateProductionStatus(Instant.now(), LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, LockStatusEnum.IN_LOCK, ProductionStatusEnum.RUNNING);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(ProductionStatusEnum.RUNNING.ordinal(), ((RafSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getProductionStatusChange().intValue());

        // Send 100 transfer data, fast
        recorder.getPduReceived().clear();
        for(int i = 0; i < 100; ++i) {
            rafProvider.transferData(new byte[300], 0, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
        }
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 100);
        assertEquals(100, recorder.getPduReceived().size());

        // Send 5 transfer data now, fast, one bad, then wait for the buffer anyway (latency)
        recorder.getPduReceived().clear();
        for(int i = 0; i < 5; ++i) {
            if(i == 0) {
                rafProvider.transferData(new byte[300], 1, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
            } else {
                rafProvider.transferData(new byte[300], 0, 0, Instant.now(), false, "AABBCCDD", false, new byte[10]);
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
        assertNotNull(((RafSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getEndOfData());

        // Simulate unlock
        recorder.getPduReceived().clear();
        rafProvider.updateProductionStatus(Instant.now(), LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, ProductionStatusEnum.INTERRUPTED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        for(Object o : recorder.getPduReceived()) {
            RafSyncNotifyInvocation op = (RafSyncNotifyInvocation) o;
            if(op.getNotification().getLossFrameSync() != null) {
                assertEquals(LockStatusEnum.OUT_OF_LOCK.ordinal(), op.getNotification().getLossFrameSync().getSymbolSyncLockStatus().intValue());
            } else if(op.getNotification().getProductionStatusChange() != null) {
                assertEquals(ProductionStatusEnum.INTERRUPTED.ordinal(), op.getNotification().getProductionStatusChange().intValue());
            }
        }

        // Simulate halt production status
        recorder.getPduReceived().clear();
        rafProvider.updateProductionStatus(Instant.now(), LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, LockStatusEnum.OUT_OF_LOCK, ProductionStatusEnum.HALTED);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(ProductionStatusEnum.HALTED.ordinal(), ((RafSyncNotifyInvocation) recorder.getPduReceived().get(0)).getNotification().getProductionStatusChange().intValue());

        // Stop
        recorder.getPduReceived().clear();
        rafUser.stop();
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleAcknowledgement)recorder.getPduReceived().get(0)).getResult().getPositiveResult());

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
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(3); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user_auth.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(3); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.bind(2);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Start
        recorder.getPduReceived().clear();
        rafUser.start(new Date(1000000), new Date(System.currentTimeMillis() + 1000000000), RafRequestedFrameQualityEnum.ALL_FRAMES);
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RafStartReturn)recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Send 500 transfer data, fast
        recorder.getPduReceived().clear();
        for(int i = 0; i < 500; ++i) {
            rafProvider.transferData(new byte[300], 0, 0, Instant.now(), true,"AABBCCDD", false, new byte[10]);
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
        for(int i = 0; i < 5; ++i) {
            if(i == 0) {
                rafProvider.transferData(new byte[300], 1, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
            } else {
                rafProvider.transferData(new byte[300], 0, 0, Instant.now(), true, "AABBCCDD", false, new byte[10]);
            }
        }

        AwaitUtil.await(4000);
        // Stop
        recorder.getPduReceived().clear();
        rafUser.stop();
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((SleAcknowledgement)recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

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
    void testEnumerations() {
        try {
            RafRequestedFrameQualityEnum.fromCode(123);
            fail("Exception expected");
        } catch(IllegalArgumentException e) {
            // Good
        }
    }

    @Test
    void testStartDiagnosticsString() {
        DiagnosticRafStart diagStart = new DiagnosticRafStart();
        diagStart.setCommon(new Diagnostics(100));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("duplicateInvokeId"));
        diagStart.setCommon(new Diagnostics(127));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("otherReason"));
        diagStart.setCommon(new Diagnostics(101));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 101"));

        diagStart.setCommon(null);
        diagStart.setSpecific(new BerInteger(0));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("outOfService"));
        diagStart.setSpecific(new BerInteger(1));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("unableToComply"));
        diagStart.setSpecific(new BerInteger(2));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidStartTime"));
        diagStart.setSpecific(new BerInteger(3));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("invalidStopTime"));
        diagStart.setSpecific(new BerInteger(4));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("missingTimeValue"));
        diagStart.setSpecific(new BerInteger(5));
        assertTrue(RafDiagnosticsStrings.getStartDiagnostic(diagStart).endsWith("<unknown value> 5"));
    }

    @Test
    void testGetParameterDiagnosticsString() {
        DiagnosticRafGet diagGet = new DiagnosticRafGet();
        diagGet.setCommon(new Diagnostics(100));
        assertTrue(RafDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("duplicateInvokeId"));
        diagGet.setCommon(new Diagnostics(127));
        assertTrue(RafDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("otherReason"));
        diagGet.setCommon(new Diagnostics(101));
        assertTrue(RafDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 101"));

        diagGet.setCommon(null);
        diagGet.setSpecific(new BerInteger(0));
        assertTrue(RafDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("unknownParameter"));
        diagGet.setSpecific(new BerInteger(5));
        assertTrue(RafDiagnosticsStrings.getGetParameterDiagnostic(diagGet).endsWith("<unknown value> 5"));
    }

    @Test
    void testScheduleStatusReportDiagnosticsString() {
        DiagnosticScheduleStatusReport diagSched = new DiagnosticScheduleStatusReport();
        diagSched.setCommon(new Diagnostics(100));
        assertTrue(RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("duplicateInvokeId"));
        diagSched.setCommon(new Diagnostics(127));
        assertTrue(RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("otherReason"));
        diagSched.setCommon(new Diagnostics(101));
        assertTrue(RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 101"));

        diagSched.setCommon(null);
        diagSched.setSpecific(new BerInteger(0));
        assertTrue(RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("notSupportedInThisDeliveryMode"));
        diagSched.setSpecific(new BerInteger(1));
        assertTrue(RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("alreadyStopped"));
        diagSched.setSpecific(new BerInteger(2));
        assertTrue(RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("invalidReportingCycle"));
        diagSched.setSpecific(new BerInteger(5));
        assertTrue(RafDiagnosticsStrings.getScheduleStatusReportDiagnostic(diagSched).endsWith("<unknown value> 5"));
    }

    @Test
    void testGenericNegativeDiagnosticsString() {
        assertTrue(RafDiagnosticsStrings.getDiagnostic(new Diagnostics(100)).endsWith("duplicateInvokeId"));
        assertTrue(RafDiagnosticsStrings.getDiagnostic(new Diagnostics(127)).endsWith("otherReason"));
        assertTrue(RafDiagnosticsStrings.getDiagnostic(new Diagnostics(101)).endsWith("<unknown value> 101"));
    }

    @Test
    void testConfiguration() {
        RafServiceInstanceConfiguration configuration = new RafServiceInstanceConfiguration();
        RafServiceInstanceConfiguration configuration2 = new RafServiceInstanceConfiguration();
        assertEquals(RafRequestedFrameQualityEnum.ALL_FRAMES, configuration.getRequestedFrameQuality());
        assertNull(configuration.getStartTime());
        assertNull(configuration.getEndTime());
        assertEquals(configuration2.hashCode(), configuration.hashCode());
    }

    @Test
    void testSleTaskObject() {
        ServiceInstance.SleTask task = new ServiceInstance.SleTask((byte) 0, () -> {
            throw new RuntimeException("Test");
        });
        ServiceInstance.SleTask task2 = new ServiceInstance.SleTask((byte) 1, () -> {
            throw new RuntimeException("Test");
        });
        assertNotEquals(task, task2);
        assertDoesNotThrow(task::hashCode);
        assertDoesNotThrow(task::run);

    }
}
