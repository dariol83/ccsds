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

package eu.dariolucia.ccsds.sle.test;

import com.beanit.jasn1.ber.types.BerNull;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafGetParameterReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafGetParameterReturnV1toV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafStartReturn;
import eu.dariolucia.ccsds.sle.server.OperationRecorder;
import eu.dariolucia.ccsds.sle.server.RafServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceBindingStateEnum;
import eu.dariolucia.ccsds.sle.utl.si.UnbindReasonEnum;
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
    void testProviderBindUnbind() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(2); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.configure();
        rafUser.waitForBind(true, null);

        // Provider
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(2); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.bind(2);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.READY);
        assertEquals(ServiceInstanceBindingStateEnum.READY, rafProvider.getCurrentBindingState());

        rafProvider.unbind(UnbindReasonEnum.SUSPEND);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND_WAIT);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND_WAIT, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());
        rafUser.dispose();
        rafProvider.dispose();
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
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REQUESTED_FRAME_QUALITY);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
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
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REQUESTED_FRAME_QUALITY);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testStatusReportGetParameterV5() throws IOException, InterruptedException {
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
        rafUser.bind(5);

        AwaitUtil.await(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rafUser.scheduleStatusReport(false, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.LATENCY_LIMIT);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REQUESTED_FRAME_QUALITY);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.RETURN_TIMEOUT_PERIOD);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.MIN_REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParMinReportingCycle().getParameterValue().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 2);
        assertEquals(2, recorder.getPduReceived().size());

        AwaitUtil.awaitCondition(6000, () -> recorder.getPduReceived().size() == 3);
        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RafGetParameterReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, null);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testTransferDataComplete() throws IOException, InterruptedException {
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

        // Start
        recorder.getPduReceived().clear();
        rafUser.start(null, null, RafRequestedFrameQualityEnum.GOOD_FRAMES_ONLY);
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RafStartReturn)recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Send 10 transfer data, fast
        recorder.getPduReceived().clear();
        for(int i = 0; i < 10; ++i) {
            rafProvider.transferData(new byte[300], 0, 0, Instant.now(), "AABBCCDD", false, new byte[10]);
        }
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 10);
        assertEquals(10, recorder.getPduReceived().size());

        // Send 5 transfer data now, fast, one bad, then wait for the buffer anyway (latency)
        recorder.getPduReceived().clear();
        for(int i = 0; i < 5; ++i) {
            if(i == 0) {
                rafProvider.transferData(new byte[300], 1, 0, Instant.now(), "AABBCCDD", false, new byte[10]);
            } else {
                rafProvider.transferData(new byte[300], 0, 0, Instant.now(), "AABBCCDD", false, new byte[10]);
            }
        }
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 4);
        assertEquals(4, recorder.getPduReceived().size());

        // Stop
        recorder.getPduReceived().clear();
        rafUser.stop();
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((SleAcknowledgement)recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.END);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testTransferDataTimely() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(3); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.configure();
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
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
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((RafStartReturn)recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Send 500 transfer data, fast
        recorder.getPduReceived().clear();
        for(int i = 0; i < 500; ++i) {
            rafProvider.transferData(new byte[300], 0, 0, Instant.now(), "AABBCCDD", false, new byte[10]);
        }
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() > 100);
        assertTrue(recorder.getPduReceived().size() <= 500);
        assertTrue(recorder.getPduReceived().size() > 0);
        // Wait the final delivery
        AwaitUtil.await(3000);
        // Send 5 transfer data now, fast, one bad, then wait for the buffer anyway (latency)
        recorder.getPduReceived().clear();
        for(int i = 0; i < 5; ++i) {
            if(i == 0) {
                rafProvider.transferData(new byte[300], 1, 0, Instant.now(), "AABBCCDD", false, new byte[10]);
            } else {
                rafProvider.transferData(new byte[300], 0, 0, Instant.now(), "AABBCCDD", false, new byte[10]);
            }
        }
        AwaitUtil.awaitCondition(4000, () -> recorder.getPduReceived().size() == 5);
        assertEquals(5, recorder.getPduReceived().size());

        // Stop
        recorder.getPduReceived().clear();
        rafUser.stop();
        AwaitUtil.awaitCondition(2000, () -> recorder.getPduReceived().size() == 1);
        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(new BerNull().toString(), ((SleAcknowledgement)recorder.getPduReceived().get(0)).getResult().getPositiveResult().toString());

        // Unbind
        rafUser.peerAbort(PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);

        AwaitUtil.awaitCondition(2000, () -> rafUser.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        AwaitUtil.awaitCondition(2000, () -> rafProvider.getCurrentBindingState() == ServiceInstanceBindingStateEnum.UNBOUND);
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }
}
