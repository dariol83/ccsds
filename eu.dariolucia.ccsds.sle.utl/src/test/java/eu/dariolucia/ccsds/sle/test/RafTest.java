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

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafGetParameterReturnV1toV4;
import eu.dariolucia.ccsds.sle.server.OperationRecorder;
import eu.dariolucia.ccsds.sle.server.RafServiceInstanceProvider;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceBindingStateEnum;
import eu.dariolucia.ccsds.sle.utl.si.UnbindReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstance;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RafTest {

    @Test
    void testProviderBindUnbind() throws IOException, InterruptedException {
        // User
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(2); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.waitForBind(true, null);

        // Provider
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(2); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.bind(2);

        Thread.sleep(2000);

        assertEquals(ServiceInstanceBindingStateEnum.READY, rafUser.getCurrentBindingState());
        assertEquals(ServiceInstanceBindingStateEnum.READY, rafProvider.getCurrentBindingState());

        rafProvider.unbind(UnbindReasonEnum.SUSPEND);

        Thread.sleep(2000);

        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }

    @Test
    void testStatusReportGetParameter() throws IOException, InterruptedException {
        // Provider
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_provider.xml");
        UtlConfigurationFile providerFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigP = (RafServiceInstanceConfiguration) providerFile.getServiceInstances().get(1); // RAF
        RafServiceInstanceProvider rafProvider = new RafServiceInstanceProvider(providerFile.getPeerConfiguration(), rafConfigP);
        rafProvider.waitForBind(true, null);

        // User
        in = this.getClass().getClassLoader().getResourceAsStream("configuration_test_user.xml");
        UtlConfigurationFile userFile = UtlConfigurationFile.load(in);
        RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(1); // RAF
        RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
        rafUser.bind(2);

        Thread.sleep(2000);

        // Register listener
        OperationRecorder recorder = new OperationRecorder();
        rafUser.register(recorder);

        // Ask for schedule status report (immediate): expect a positive return and a report
        rafUser.scheduleStatusReport(false, null);

        Thread.sleep(2000);

        assertEquals(2, recorder.getPduReceived().size());

        // Ask for parameter transfer buffer size: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.BUFFER_SIZE);

        Thread.sleep(2000);

        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(10, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParBufferSize().getParameterValue().intValue());

        // Ask for parameter latency limit: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.LATENCY_LIMIT);

        Thread.sleep(2000);

        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(3, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParLatencyLimit().getParameterValue().getOnline().intValue());

        // Ask for parameter permitted frame quality: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REQUESTED_FRAME_QUALITY);

        Thread.sleep(2000);

        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReqFrameQuality().getParameterValue().intValue());

        // Ask for parameter return timeout: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.RETURN_TIMEOUT_PERIOD);

        Thread.sleep(2000);

        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(120, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReturnTimeout().getParameterValue().intValue());

        // Ask for parameter min reporting cycle: expect negative return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.MIN_REPORTING_CYCLE);

        Thread.sleep(2000);

        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(0, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getNegativeResult().getSpecific().intValue());

        // Schedule status report every 5 seconds
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(false, 5);

        Thread.sleep(2000);

        assertEquals(2, recorder.getPduReceived().size());

        Thread.sleep(4000);

        assertEquals(3, recorder.getPduReceived().size());

        // Ask for parameter reporting cycle: expect positive return
        recorder.getPduReceived().clear();
        rafUser.getParameter(RafParameterEnum.REPORTING_CYCLE);

        Thread.sleep(1000);

        assertEquals(1, recorder.getPduReceived().size());
        assertEquals(5, ((RafGetParameterReturnV1toV4) recorder.getPduReceived().get(0)).getResult().getPositiveResult().getParReportingCycle().getParameterValue().getPeriodicReportingOn().intValue());

        // Stop status report
        recorder.getPduReceived().clear();
        rafUser.scheduleStatusReport(true, null);

        Thread.sleep(2000);

        assertEquals(1, recorder.getPduReceived().size());
        assertNotNull(((SleScheduleStatusReportReturn) recorder.getPduReceived().get(0)).getResult().getPositiveResult());

        // Unbind
        rafUser.unbind(UnbindReasonEnum.SUSPEND);

        Thread.sleep(2000);

        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafUser.getCurrentBindingState());
        assertEquals(ServiceInstanceBindingStateEnum.UNBOUND, rafProvider.getCurrentBindingState());

        rafUser.dispose();
        rafProvider.dispose();
    }
}