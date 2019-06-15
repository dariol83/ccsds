/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.tests;

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream;
import com.beanit.jasn1.ber.types.BerInteger;
import com.beanit.jasn1.ber.types.BerNull;
import com.beanit.jasn1.ber.types.BerOctetString;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.DiagnosticScheduleStatusReport;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.ReportingCycle;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfTransferDataInvocation.PrivateAnnotation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.MasterChannelComposition.McOrVcList;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.MasterChannelComposition.McOrVcList.VcList;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures.RcfGetParameter.*;
import eu.dariolucia.ccsds.sle.utl.network.tml.ITmlChannelObserver;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannel;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannelException;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlDisconnectionReasonEnum;
import eu.dariolucia.ccsds.sle.utl.pdu.PduFactoryUtil;
import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RcfChannelObserver implements ITmlChannelObserver {

	private final static Logger LOG = Logger.getLogger(RcfChannelObserver.class.getName());

	private TmlChannel channel;

	private int version;

	private AtomicInteger sequencer = new AtomicInteger(1);

	private Thread transferDataThread;
	private volatile boolean transferDataThreadRunning;
	
	private Thread statusReportThread;
	private volatile boolean statusReportThreadRunning;

	private volatile int frameSent = 0;
	private volatile int reportingCycle = 0;

	private volatile GvcId requestedGvcid;
	
	@Override
	public void onChannelConnected(TmlChannel channel) {
		//
		this.channel = channel;
	}

	@Override
	public void onChannelDisconnected(TmlChannel channel, TmlDisconnectionReasonEnum reason, PeerAbortReasonEnum peerAbortReason) {
		//
	}

	@Override
	public void onPduReceived(TmlChannel channel, byte[] pdu) {
		processPdu(channel, pdu);
	}

	private void processPdu(TmlChannel channel, byte[] pdu) {
		try {
			if (pdu[0] == (byte) 0xBF && pdu[1] == 0x64) {
				// Bind
				ByteArrayInputStream bin = new ByteArrayInputStream(pdu);
				RcfUserToProviderPdu decoded = new RcfUserToProviderPdu();
				decoded.decode(bin);
				LOG.info(decoded.toString());

				SleBindInvocation bind = decoded.getRcfBindInvocation();

				this.version = bind.getVersionNumber().intValue();

				// Send Bind Return
				SleBindReturn resp = new SleBindReturn();
				resp.setResult(new SleBindReturn.Result());
				resp.getResult().setPositive(new VersionNumber(bind.getVersionNumber().intValue()));
				resp.setPerformerCredentials(new Credentials());
				resp.getPerformerCredentials().setUnused(new BerNull());
				resp.setResponderIdentifier(new AuthorityIdentifier("TEST-PROVIDER".getBytes()));

				RcfProviderToUserPdu toSend = new RcfProviderToUserPdu();
				toSend.setRcfBindReturn(resp);

				// Encode
				ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
				toSend.encode(ros);
				byte[] data = ros.getArray();

				LOG.info("Sending PDU: " + DatatypeConverter.printHexBinary(data));
				LOG.info(resp.toString());
				this.channel.sendPdu(data);
			} else if (pdu[0] == (byte) 0xBF && pdu[1] == 0x66) {
				// Unbind
				ByteArrayInputStream bin = new ByteArrayInputStream(pdu);
				RcfUserToProviderPdu decoded = new RcfUserToProviderPdu();
				decoded.decode(bin);
				LOG.info(decoded.toString());

				// Send Unbind Return
				SleUnbindReturn resp = new SleUnbindReturn();
				resp.setResult(new SleUnbindReturn.Result());
				resp.getResult().setPositive(new BerNull());
				resp.setResponderCredentials(new Credentials());
				resp.getResponderCredentials().setUnused(new BerNull());

				RcfProviderToUserPdu toSend = new RcfProviderToUserPdu();
				toSend.setRcfUnbindReturn(resp);

				// Encode
				ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
				toSend.encode(ros);
				byte[] data = ros.getArray();

				LOG.info("Sending PDU: " + DatatypeConverter.printHexBinary(data));
				LOG.info(resp.toString());
				channel.sendPdu(data);

				this.sequencer.set(1);
				this.frameSent = 0;
				deactivateStatusReport();
				this.reportingCycle = 0;
			} else if (pdu[0] == (byte) 0xA0) {
				// Start
				ByteArrayInputStream bin = new ByteArrayInputStream(pdu);
				RcfUserToProviderPdu decoded = new RcfUserToProviderPdu();
				decoded.decode(bin);
				LOG.info(decoded.toString());

				this.requestedGvcid = decoded.getRcfStartInvocation().getRequestedGvcId();
				
				// Send Start Return
				RcfStartReturn resp = new RcfStartReturn();
				resp.setResult(new RcfStartReturn.Result());
				resp.getResult().setPositiveResult(new BerNull());
				resp.setPerformerCredentials(new Credentials());
				resp.getPerformerCredentials().setUnused(new BerNull());
				resp.setInvokeId(new InvokeId(this.sequencer.getAndIncrement()));

				RcfProviderToUserPdu toSend = new RcfProviderToUserPdu();
				toSend.setRcfStartReturn(resp);

				// Encode
				ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
				toSend.encode(ros);
				byte[] data = ros.getArray();

				LOG.info("Sending PDU: " + DatatypeConverter.printHexBinary(data));
				LOG.info(resp.toString());
				this.channel.sendPdu(data);

				activateTransferDataThread();

			} else if (pdu[0] == (byte) 0xA2) {
				// Stop
				ByteArrayInputStream bin = new ByteArrayInputStream(pdu);
				RcfUserToProviderPdu decoded = new RcfUserToProviderPdu();
				decoded.decode(bin);
				LOG.info(decoded.toString());

				deactivateTransferDataThread();

				this.requestedGvcid = null;
				
				// Send Stop Return
				SleAcknowledgement resp = new SleAcknowledgement();
				resp.setResult(new SleAcknowledgement.Result());
				resp.getResult().setPositiveResult(new BerNull());
				resp.setCredentials(new Credentials());
				resp.getCredentials().setUnused(new BerNull());
				resp.setInvokeId(new InvokeId(this.sequencer.getAndIncrement()));

				RcfProviderToUserPdu toSend = new RcfProviderToUserPdu();
				toSend.setRcfStopReturn(resp);

				// Encode
				ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
				toSend.encode(ros);
				byte[] data = ros.getArray();

				LOG.info("Sending PDU: " + DatatypeConverter.printHexBinary(data));
				LOG.info(resp.toString());
				this.channel.sendPdu(data);
			} else if (pdu[0] == (byte) 0xA4) {
				// Schedule status report
				ByteArrayInputStream bin = new ByteArrayInputStream(pdu);
				RcfUserToProviderPdu decoded = new RcfUserToProviderPdu();
				decoded.decode(bin);
				LOG.info(decoded.toString());

				boolean result = true;
				if(decoded.getRcfScheduleStatusReportInvocation().getReportRequestType().getImmediately() != null) {
					sendStatusReport();
				} else if(decoded.getRcfScheduleStatusReportInvocation().getReportRequestType().getPeriodically() != null) {
					result = activateStatusReport(decoded.getRcfScheduleStatusReportInvocation().getReportRequestType().getPeriodically().intValue());
				} else if(decoded.getRcfScheduleStatusReportInvocation().getReportRequestType().getStop() != null) {
					result = deactivateStatusReport();
				}
				
				// Send Schedule Status Report Return
				SleScheduleStatusReportReturn resp = new SleScheduleStatusReportReturn();
				resp.setResult(new SleScheduleStatusReportReturn.Result());
				if(result) {
					resp.getResult().setPositiveResult(new BerNull());
				} else {
					resp.getResult().setNegativeResult(new DiagnosticScheduleStatusReport());
					resp.getResult().getNegativeResult().setSpecific(new BerInteger(0));
				}
				resp.setPerformerCredentials(new Credentials());
				resp.getPerformerCredentials().setUnused(new BerNull());
				resp.setInvokeId(new InvokeId(this.sequencer.getAndIncrement()));

				RcfProviderToUserPdu toSend = new RcfProviderToUserPdu();
				toSend.setRcfScheduleStatusReportReturn(resp);

				// Encode
				ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
				toSend.encode(ros);
				byte[] data = ros.getArray();

				LOG.info("Sending PDU: " + DatatypeConverter.printHexBinary(data));
				LOG.info(resp.toString());
				this.channel.sendPdu(data);
			} else if (pdu[0] == (byte) 0xA6) {
				// Get parameter
				ByteArrayInputStream bin = new ByteArrayInputStream(pdu);
				RcfUserToProviderPdu decoded = new RcfUserToProviderPdu();
				decoded.decode(bin);
				LOG.info(decoded.toString());
								
				// Send Get Parameter Return
				byte[] data;
				if(this.version == 5) {
					RcfGetParameterReturn resp = new RcfGetParameterReturn();
					resp.setResult(new RcfGetParameterReturn.Result());
					
					RcfGetParameter rgp = new RcfGetParameter();
					boolean result = setParameterValue(rgp, decoded.getRcfGetParameterInvocation().getRcfParameter().intValue());
					if(result) {
						resp.getResult().setPositiveResult(rgp);	
					} else {
						resp.getResult().setNegativeResult(new DiagnosticRcfGet());
						resp.getResult().getNegativeResult().setSpecific(new BerInteger(0));
					}
					resp.setPerformerCredentials(new Credentials());
					resp.getPerformerCredentials().setUnused(new BerNull());
					resp.setInvokeId(new InvokeId(this.sequencer.getAndIncrement()));

					RcfProviderToUserPdu toSend = new RcfProviderToUserPdu();
					toSend.setRcfGetParameterReturn(resp);

					// Encode
					ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
					toSend.encode(ros);
					data = ros.getArray();
					LOG.info("Sending PDU: " + DatatypeConverter.printHexBinary(data));
					LOG.info(resp.toString());
					this.channel.sendPdu(data);
				} else {
					RcfGetParameterReturnV1toV4 resp = new RcfGetParameterReturnV1toV4();
					resp.setResult(new RcfGetParameterReturnV1toV4.Result());
					RcfGetParameterV1toV4 rgp = new RcfGetParameterV1toV4();
					boolean result = setParameterValue(rgp, decoded.getRcfGetParameterInvocation().getRcfParameter().intValue());
					if(result) {
						resp.getResult().setPositiveResult(rgp);
					} else {
						resp.getResult().setNegativeResult(new DiagnosticRcfGet());
						resp.getResult().getNegativeResult().setSpecific(new BerInteger(0));
					}
					resp.setPerformerCredentials(new Credentials());
					resp.getPerformerCredentials().setUnused(new BerNull());
					resp.setInvokeId(new InvokeId(this.sequencer.getAndIncrement()));

					RcfProviderToUserPduV2toV4 toSend = new RcfProviderToUserPduV2toV4();
					toSend.setRcfGetParameterReturn(resp);

					// Encode
					ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
					toSend.encode(ros);
					data = ros.getArray();
					LOG.info("Sending PDU: " + DatatypeConverter.printHexBinary(data));
					LOG.info(resp.toString());
					this.channel.sendPdu(data);
				}
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while processing PDU", e);
		}
	}


	private boolean setParameterValue(RcfGetParameterV1toV4 positiveResult, int intValue) {
		boolean found = false;
		switch(intValue) {
		case 4: // Buffer size
			positiveResult.setParBufferSize(new RcfGetParameterV1toV4.ParBufferSize());
			positiveResult.getParBufferSize().setParameterName(new RcfParameterName(4));
			positiveResult.getParBufferSize().setParameterValue(new IntPosShort(20));
			found = true;
			break;
		case 6: // Delivery mode
			positiveResult.setParDeliveryMode(new RcfGetParameterV1toV4.ParDeliveryMode());
			positiveResult.getParDeliveryMode().setParameterName(new RcfParameterName(6));
			positiveResult.getParDeliveryMode().setParameterValue(new RcfDeliveryMode(1));
			found = true;
			break;
		case 15: // Latency limit
			positiveResult.setParLatencyLimit(new RcfGetParameterV1toV4.ParLatencyLimit());
			positiveResult.getParLatencyLimit().setParameterName(new RcfParameterName(15));
			positiveResult.getParLatencyLimit().setParameterValue(new RcfGetParameterV1toV4.ParLatencyLimit.ParameterValue());
			positiveResult.getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(1));
			found = true;
			break;
		case 26: // Reporting cycle
			positiveResult.setParReportingCycle(new RcfGetParameterV1toV4.ParReportingCycle());
			positiveResult.getParReportingCycle().setParameterName(new RcfParameterName(26));
			positiveResult.getParReportingCycle().setParameterValue(new CurrentReportingCycle());
			if(this.reportingCycle > 0) {
				positiveResult.getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
			} else {
				positiveResult.getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
			}
			found = true;
			break;
		case 28: // Requested GVCID
			positiveResult.setParReqGvcId(new RcfGetParameterV1toV4.ParReqGvcId());
			positiveResult.getParReqGvcId().setParameterName(new RcfParameterName(28));
			positiveResult.getParReqGvcId().setParameterValue(this.requestedGvcid);
			found = true;
			break;
		case 29: // Return timeout period
			positiveResult.setParReturnTimeout(new RcfGetParameterV1toV4.ParReturnTimeout());
			positiveResult.getParReturnTimeout().setParameterName(new RcfParameterName(29));
			positiveResult.getParReturnTimeout().setParameterValue(new TimeoutPeriod(88));
			found = true;
			break;
		case 24: // Permitted GVCID set
			positiveResult.setParPermittedGvcidSet(new RcfGetParameterV1toV4.ParPermittedGvcidSet());
			positiveResult.getParPermittedGvcidSet().setParameterName(new RcfParameterName(24));
			positiveResult.getParPermittedGvcidSet().setParameterValue(new GvcIdSetV1toV4());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().add(new MasterChannelCompositionV1toV4());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().get(0).setSpacecraftId(new BerInteger(3));
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().get(0).setVersionNumber(new BerInteger());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().get(0).setMcOrVcList(new MasterChannelCompositionV1toV4.McOrVcList());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().get(0).getMcOrVcList().getVcList().getVcId().add(new VcId(3));
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelCompositionV1toV4().get(0).getMcOrVcList().getVcList().getVcId().add(new VcId(1));
			found = true;
			break;
		}
		return found;
	}
	
	private boolean setParameterValue(RcfGetParameter positiveResult, int intValue) {
		boolean found = false;
		switch(intValue) {
		case 4: // Buffer size
			positiveResult.setParBufferSize(new ParBufferSize());
			positiveResult.getParBufferSize().setParameterName(new ParameterName(4));
			positiveResult.getParBufferSize().setParameterValue(new IntPosShort(20));
			found = true;
			break;
		case 6: // Delivery mode
			positiveResult.setParDeliveryMode(new ParDeliveryMode());
			positiveResult.getParDeliveryMode().setParameterName(new ParameterName(6));
			positiveResult.getParDeliveryMode().setParameterValue(new RcfDeliveryMode(1));
			found = true;
			break;
		case 15: // Latency limit
			positiveResult.setParLatencyLimit(new ParLatencyLimit());
			positiveResult.getParLatencyLimit().setParameterName(new ParameterName(15));
			positiveResult.getParLatencyLimit().setParameterValue(new RcfGetParameter.ParLatencyLimit.ParameterValue());
			positiveResult.getParLatencyLimit().getParameterValue().setOnline(new IntPosShort(1));
			found = true;
			break;
		case 301: // Min reporting cycle
			positiveResult.setParMinReportingCycle(new ParMinReportingCycle());
			positiveResult.getParMinReportingCycle().setParameterName(new ParameterName(301));
			positiveResult.getParMinReportingCycle().setParameterValue(new IntPosShort(34));
			found = true;
			break;
		case 26: // Reporting cycle
			positiveResult.setParReportingCycle(new ParReportingCycle());
			positiveResult.getParReportingCycle().setParameterName(new ParameterName(26));
			positiveResult.getParReportingCycle().setParameterValue(new CurrentReportingCycle());
			if(this.reportingCycle > 0) {
				positiveResult.getParReportingCycle().getParameterValue().setPeriodicReportingOn(new ReportingCycle(this.reportingCycle));
			} else {
				positiveResult.getParReportingCycle().getParameterValue().setPeriodicReportingOff(new BerNull());
			}
			found = true;
			break;
		case 28: // Requested GVCID
			positiveResult.setParReqGvcId(new ParReqGvcId());
			positiveResult.getParReqGvcId().setParameterName(new ParameterName(28));
			positiveResult.getParReqGvcId().setParameterValue(new RequestedGvcId());
			if(this.requestedGvcid != null) {
				positiveResult.getParReqGvcId().getParameterValue().setGvcid(this.requestedGvcid);
			} else {
				positiveResult.getParReqGvcId().getParameterValue().setUndefined(new BerNull());
			}
			found = true;
			break;
		case 29: // Return timeout period
			positiveResult.setParReturnTimeout(new ParReturnTimeout());
			positiveResult.getParReturnTimeout().setParameterName(new ParameterName(29));
			positiveResult.getParReturnTimeout().setParameterValue(new TimeoutPeriod(88));
			found = true;
			break;
		case 24: // Permitted GVCID set
			positiveResult.setParPermittedGvcidSet(new ParPermittedGvcidSet());
			positiveResult.getParPermittedGvcidSet().setParameterName(new ParameterName(24));
			positiveResult.getParPermittedGvcidSet().setParameterValue(new GvcIdSet());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().add(new MasterChannelComposition());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().get(0).setSpacecraftId(new BerInteger(3));
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().get(0).setVersionNumber(new BerInteger());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().get(0).setMcOrVcList(new McOrVcList());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().get(0).getMcOrVcList().setVcList(new VcList());
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().get(0).getMcOrVcList().getVcList().getVcId().add(new VcId(3));
			positiveResult.getParPermittedGvcidSet().getParameterValue().getMasterChannelComposition().get(0).getMcOrVcList().getVcList().getVcId().add(new VcId(1));
			found = true;
			break;
		}
		return found;
	}

	private synchronized boolean deactivateStatusReport() {
		if(!this.statusReportThreadRunning) {
			return false;
		}
		this.statusReportThreadRunning = false;
		if (this.statusReportThread != null) {
			try {
				this.statusReportThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.statusReportThread = null;
		this.reportingCycle = 0;
		return true;
	}

	private synchronized boolean activateStatusReport(int period) {
		if (this.statusReportThreadRunning) {
			return false;
		}
		this.statusReportThreadRunning = true;
		this.statusReportThread = new Thread(() -> {
			while (this.statusReportThreadRunning) {
				try {
					sendStatusReport();
					
					Thread.sleep(period * 1000);
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Exception while preparing/sending data units", e);
					this.channel.abort((byte) 0x02);
					return;
				}
			}
		});
		this.statusReportThread.start();
		this.reportingCycle = period;
		return true;
	}
	
	private void sendStatusReport() throws TmlChannelException, IOException {
		if(this.version == 1) {
			RcfStatusReportInvocationV1 pdu = new RcfStatusReportInvocationV1();
			pdu.setCarrierLockStatus(new LockStatus(0));
			pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.frameSent));
			pdu.setFrameSyncLockStatus(new LockStatus(1));
			pdu.setInvokerCredentials(new Credentials());
			pdu.getInvokerCredentials().setUnused(new BerNull());
			pdu.setProductionStatus(new RcfProductionStatus(1));
			pdu.setSubcarrierLockStatus(new LockStatus(2));
			pdu.setSymbolSyncLockStatus(new LockStatus(2));

			RcfProviderToUserPduV1 ddd = new RcfProviderToUserPduV1();
			ddd.setRcfStatusReportInvocation(pdu);

			// Encode
			ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
			ddd.encode(ros);
			byte[] toSend = ros.getArray();

			LOG.info("Sending Status Report V1 PDU: " + DatatypeConverter.printHexBinary(toSend));
			LOG.info(pdu.toString());
			this.channel.sendPdu(toSend);
		} else {
			RcfStatusReportInvocation pdu = new RcfStatusReportInvocation();
			pdu.setCarrierLockStatus(new CarrierLockStatus(0));
			pdu.setDeliveredFrameNumber(new IntUnsignedLong(this.frameSent));
			pdu.setFrameSyncLockStatus(new FrameSyncLockStatus(1));
			pdu.setInvokerCredentials(new Credentials());
			pdu.getInvokerCredentials().setUnused(new BerNull());
			pdu.setProductionStatus(new RcfProductionStatus(1));
			pdu.setSubcarrierLockStatus(new LockStatus(2));
			pdu.setSymbolSyncLockStatus(new SymbolLockStatus(2));

			RcfProviderToUserPdu ddd = new RcfProviderToUserPdu();
			ddd.setRcfStatusReportInvocation(pdu);

			// Encode
			ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
			ddd.encode(ros);
			byte[] toSend = ros.getArray();

			LOG.info("Sending Status Report PDU: " + DatatypeConverter.printHexBinary(toSend));
			LOG.info(pdu.toString());
			this.channel.sendPdu(toSend);
		}
	}

	private synchronized void deactivateTransferDataThread() {
		this.transferDataThreadRunning = false;
		if (this.transferDataThread != null) {
			try {
				this.transferDataThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		this.transferDataThread = null;
	}

	private synchronized void activateTransferDataThread() {
		if (this.transferDataThread != null) {
			deactivateTransferDataThread();
		}
		this.transferDataThreadRunning = true;
		this.transferDataThread = new Thread(() -> {
			Random r = new Random(System.currentTimeMillis());
			RcfTransferBuffer tb = new RcfTransferBuffer();
			while (this.transferDataThreadRunning) {
				try {
					FrameOrNotification fon = new FrameOrNotification();
					int gen = r.nextInt(100);
					if (gen < 90) {
						// Transfer data
						RcfTransferDataInvocation td = new RcfTransferDataInvocation();
						td.setDataLinkContinuity(new BerInteger(2));
						td.setInvokerCredentials(new Credentials());
						td.getInvokerCredentials().setUnused(new BerNull());
						td.setEarthReceiveTime(new Time());
						td.getEarthReceiveTime().setCcsdsFormat(
								new TimeCCSDS(PduFactoryUtil.buildCDSTime(System.currentTimeMillis(), 0)));
						td.setPrivateAnnotation(new PrivateAnnotation());
						td.getPrivateAnnotation().setNotNull(new BerOctetString(new byte[] { 0x00, 0x01, 0x02, 0x03 }));
						td.setAntennaId(new AntennaId());
						td.getAntennaId().setLocalForm(new BerOctetString(new byte[] { 0x24, 0x25, 0x26, 0x27 }));
						td.setData(new SpaceLinkDataUnit(new byte[1024]));

						fon.setAnnotatedFrame(td);
						
						++frameSent;
					} else {
						// Notify
						RcfSyncNotifyInvocation nt = new RcfSyncNotifyInvocation();
						nt.setInvokerCredentials(new Credentials());
						nt.getInvokerCredentials().setUnused(new BerNull());
						nt.setNotification(new Notification());
						if (gen > 98) {
							nt.getNotification().setExcessiveDataBacklog(new BerNull());
						} else if (gen > 96) {
							nt.getNotification().setEndOfData(new BerNull());
						} else if (gen > 94) {
							nt.getNotification().setLossFrameSync(buildLockStatusReport());
						} else {
							nt.getNotification().setProductionStatusChange(buildProductionStatusChange());
						}

						fon.setSyncNotification(nt);
					}
					// Append
					tb.getFrameOrNotification().add(fon);

					if (tb.getFrameOrNotification().size() == 20) {
						RcfProviderToUserPdu ddd = new RcfProviderToUserPdu();
						ddd.setRcfTransferBuffer(tb);
						// Encode
						ReverseByteArrayOutputStream ros = new ReverseByteArrayOutputStream(300, true);
						ddd.encode(ros);
						byte[] toSend = ros.getArray();

						LOG.info("Sending Buffer PDU of bytes: " + toSend.length);
						this.channel.sendPdu(toSend);

						tb = new RcfTransferBuffer();

						Thread.sleep(500);
					}
				} catch (Exception e) {
					LOG.log(Level.SEVERE, "Exception while preparing/sending data units", e);
					this.channel.abort((byte) 0x02);
					this.transferDataThread = null;
					this.transferDataThreadRunning = false;
					return;
				}
			}
		});
		this.transferDataThread.start();
	}

	private RcfProductionStatus buildProductionStatusChange() {
		return new RcfProductionStatus(2);
	}

	private LockStatusReport buildLockStatusReport() {
		LockStatusReport sr = new LockStatusReport();
		sr.setCarrierLockStatus(new CarrierLockStatus(0));
		sr.setSubcarrierLockStatus(new LockStatus(0));
		sr.setSymbolSyncLockStatus(new SymbolLockStatus(0));
		sr.setTime(new Time());
		sr.getTime().setCcsdsFormat(new TimeCCSDS(PduFactoryUtil.buildCDSTime(System.currentTimeMillis(), 0)));
		return sr;
	}
}
