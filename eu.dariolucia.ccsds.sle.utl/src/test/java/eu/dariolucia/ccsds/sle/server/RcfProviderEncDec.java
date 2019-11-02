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

package eu.dariolucia.ccsds.sle.server;

import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.utl.encdec.CommonEncDec;
import eu.dariolucia.ccsds.sle.utl.encdec.DecodingException;
import eu.dariolucia.ccsds.sle.utl.encdec.EncodingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * RCF encoding/decoding extension class.
 *
 * This class is meant for testing only. If you use this class to implement your own SLE provider, you have been warned.
 */
public class RcfProviderEncDec extends CommonEncDec {

	private final List<Function<RcfUserToProviderPdu, BerType>> unwrapFunctionList;

	public RcfProviderEncDec() {
		register(1, RcfUserToProviderPdu::new);
		register(2, RcfUserToProviderPdu::new);
		register(3, RcfUserToProviderPdu::new);
		register(4, RcfUserToProviderPdu::new);
		register(5, RcfUserToProviderPdu::new);

		// Unwrappers
		unwrapFunctionList = new ArrayList<>();
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfScheduleStatusReportInvocation);
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfGetParameterInvocation);
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfBindInvocation);
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfBindReturn);
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfUnbindInvocation);
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfUnbindReturn);
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfStartInvocation);
		unwrapFunctionList.add(RcfUserToProviderPdu::getRcfStopInvocation);
	}

	@Override
	protected Supplier<BerType> getDefaultDecodingProvider() {
		return RcfUserToProviderPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		switch(getVersion()) {
			case 1:
			{
				RcfProviderToUserPduV1 wrapper = new RcfProviderToUserPduV1();
				if(toEncode instanceof SleBindInvocation) {
					wrapper.setRcfBindInvocation((SleBindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindInvocation) {
					wrapper.setRcfUnbindInvocation((SleUnbindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindReturn) {
					wrapper.setRcfUnbindReturn((SleUnbindReturn) toEncode);
				} else if(toEncode instanceof SleBindReturn) {
					wrapper.setRcfBindReturn((SleBindReturn) toEncode);
				} else if(toEncode instanceof SleScheduleStatusReportReturn) {
					wrapper.setRcfScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
				} else if(toEncode instanceof RcfStatusReportInvocationV1) {
					wrapper.setRcfStatusReportInvocation((RcfStatusReportInvocationV1) toEncode);
				} else if(toEncode instanceof RcfStartReturn) {
					wrapper.setRcfStartReturn((RcfStartReturn) toEncode);
				} else if(toEncode instanceof SleAcknowledgement) {
					wrapper.setRcfStopReturn((SleAcknowledgement) toEncode);
				} else if(toEncode instanceof RcfGetParameterReturnV1toV4) {
					wrapper.setRcfGetParameterReturn((RcfGetParameterReturnV1toV4) toEncode);
				} else if(toEncode instanceof RcfTransferBuffer) {
					wrapper.setRcfTransferBuffer((RcfTransferBuffer) toEncode);
				} else {
					throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName());
				}
				return wrapper;
			}
			case 2:
			case 3:
			case 4:
			{
				RcfProviderToUserPduV2toV4 wrapper = new RcfProviderToUserPduV2toV4();
				if(toEncode instanceof SleBindInvocation) {
					wrapper.setRcfBindInvocation((SleBindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindInvocation) {
					wrapper.setRcfUnbindInvocation((SleUnbindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindReturn) {
					wrapper.setRcfUnbindReturn((SleUnbindReturn) toEncode);
				} else if(toEncode instanceof SleBindReturn) {
					wrapper.setRcfBindReturn((SleBindReturn) toEncode);
				} else if(toEncode instanceof SleScheduleStatusReportReturn) {
					wrapper.setRcfScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
				} else if(toEncode instanceof RcfStatusReportInvocation) {
					wrapper.setRcfStatusReportInvocation((RcfStatusReportInvocation) toEncode);
				} else if(toEncode instanceof RcfStartReturn) {
					wrapper.setRcfStartReturn((RcfStartReturn) toEncode);
				} else if(toEncode instanceof SleAcknowledgement) {
					wrapper.setRcfStopReturn((SleAcknowledgement) toEncode);
				} else if(toEncode instanceof RcfGetParameterReturnV1toV4) {
					wrapper.setRcfGetParameterReturn((RcfGetParameterReturnV1toV4) toEncode);
				} else if(toEncode instanceof RcfTransferBuffer) {
					wrapper.setRcfTransferBuffer((RcfTransferBuffer) toEncode);
				} else {
					throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName());
				}
				return wrapper;
			}
			default:
			{
				RcfProviderToUserPdu wrapper = new RcfProviderToUserPdu();
				if(toEncode instanceof SleBindInvocation) {
					wrapper.setRcfBindInvocation((SleBindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindInvocation) {
					wrapper.setRcfUnbindInvocation((SleUnbindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindReturn) {
					wrapper.setRcfUnbindReturn((SleUnbindReturn) toEncode);
				} else if(toEncode instanceof SleBindReturn) {
					wrapper.setRcfBindReturn((SleBindReturn) toEncode);
				} else if(toEncode instanceof SleScheduleStatusReportReturn) {
					wrapper.setRcfScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
				} else if(toEncode instanceof RcfStatusReportInvocation) {
					wrapper.setRcfStatusReportInvocation((RcfStatusReportInvocation) toEncode);
				} else if(toEncode instanceof RcfStartReturn) {
					wrapper.setRcfStartReturn((RcfStartReturn) toEncode);
				} else if(toEncode instanceof SleAcknowledgement) {
					wrapper.setRcfStopReturn((SleAcknowledgement) toEncode);
				} else if(toEncode instanceof RcfGetParameterReturn) {
					wrapper.setRcfGetParameterReturn((RcfGetParameterReturn) toEncode);
				} else if(toEncode instanceof RcfTransferBuffer) {
					wrapper.setRcfTransferBuffer((RcfTransferBuffer) toEncode);
				} else {
					throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName());
				}
				return wrapper;
			}
		}
	}

	@Override
	protected BerType unwrapPdu(BerType toDecode) throws DecodingException {
		return returnOrThrow(this.unwrapFunctionList.parallelStream().map(o -> o.apply((RcfUserToProviderPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
	}
}
