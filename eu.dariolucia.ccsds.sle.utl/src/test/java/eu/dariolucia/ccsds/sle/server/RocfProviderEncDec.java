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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.utl.encdec.CommonEncDec;
import eu.dariolucia.ccsds.sle.utl.encdec.DecodingException;
import eu.dariolucia.ccsds.sle.utl.encdec.EncodingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ROCF encoding/decoding extension class.
 *
 * This class is meant for testing only. If you use this class to implement your own SLE provider, you have been warned.
 */
public class RocfProviderEncDec extends CommonEncDec {

	private final List<Function<RocfUserToProviderPdu, BerType>> unwrapFunctionList;

	public RocfProviderEncDec() {
		register(1, RocfUserToProviderPdu::new);
		register(2, RocfUserToProviderPdu::new);
		register(3, RocfUserToProviderPdu::new);
		register(4, RocfUserToProviderPdu::new);
		register(5, RocfUserToProviderPdu::new);

		// Unwrappers
		unwrapFunctionList = new ArrayList<>();
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfScheduleStatusReportInvocation);
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfGetParameterInvocation);
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfBindInvocation);
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfBindReturn);
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfUnbindInvocation);
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfUnbindReturn);
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfStartInvocation);
		unwrapFunctionList.add(RocfUserToProviderPdu::getRocfStopInvocation);
	}

	@Override
	protected Supplier<BerType> getDefaultDecodingProvider() {
		return RocfUserToProviderPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		switch(getVersion()) {
			case 1:
			case 2:
			case 3:
			case 4:
			{
				RocfProviderToUserPduV1toV4 wrapper = new RocfProviderToUserPduV1toV4();
				if(toEncode instanceof SleBindInvocation) {
					wrapper.setRocfBindInvocation((SleBindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindInvocation) {
					wrapper.setRocfUnbindInvocation((SleUnbindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindReturn) {
					wrapper.setRocfUnbindReturn((SleUnbindReturn) toEncode);
				} else if(toEncode instanceof SleBindReturn) {
					wrapper.setRocfBindReturn((SleBindReturn) toEncode);
				} else if(toEncode instanceof SleScheduleStatusReportReturn) {
					wrapper.setRocfScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
				} else if(toEncode instanceof RocfStatusReportInvocation) {
					wrapper.setRocfStatusReportInvocation((RocfStatusReportInvocation) toEncode);
				} else if(toEncode instanceof RocfStartReturn) {
					wrapper.setRocfStartReturn((RocfStartReturn) toEncode);
				} else if(toEncode instanceof SleAcknowledgement) {
					wrapper.setRocfStopReturn((SleAcknowledgement) toEncode);
				} else if(toEncode instanceof RocfGetParameterReturnV1toV4) {
					wrapper.setRocfGetParameterReturn((RocfGetParameterReturnV1toV4) toEncode);
				} else if(toEncode instanceof RocfTransferBuffer) {
					wrapper.setRocfTransferBuffer((RocfTransferBuffer) toEncode);
				} else {
					throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName());
				}
				return wrapper;
			}
			default:
			{
				RocfProviderToUserPdu wrapper = new RocfProviderToUserPdu();
				if(toEncode instanceof SleBindInvocation) {
					wrapper.setRocfBindInvocation((SleBindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindInvocation) {
					wrapper.setRocfUnbindInvocation((SleUnbindInvocation) toEncode);
				} else if(toEncode instanceof SleUnbindReturn) {
					wrapper.setRocfUnbindReturn((SleUnbindReturn) toEncode);
				} else if(toEncode instanceof SleBindReturn) {
					wrapper.setRocfBindReturn((SleBindReturn) toEncode);
				} else if(toEncode instanceof SleScheduleStatusReportReturn) {
					wrapper.setRocfScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
				} else if(toEncode instanceof RocfStatusReportInvocation) {
					wrapper.setRocfStatusReportInvocation((RocfStatusReportInvocation) toEncode);
				} else if(toEncode instanceof RocfStartReturn) {
					wrapper.setRocfStartReturn((RocfStartReturn) toEncode);
				} else if(toEncode instanceof SleAcknowledgement) {
					wrapper.setRocfStopReturn((SleAcknowledgement) toEncode);
				} else if(toEncode instanceof RocfGetParameterReturn) {
					wrapper.setRocfGetParameterReturn((RocfGetParameterReturn) toEncode);
				} else if(toEncode instanceof RocfTransferBuffer) {
					wrapper.setRocfTransferBuffer((RocfTransferBuffer) toEncode);
				} else {
					throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName());
				}
				return wrapper;
			}
		}
	}

	@Override
	protected BerType unwrapPdu(BerType toDecode) throws DecodingException {
		return returnOrThrow(this.unwrapFunctionList.parallelStream().map(o -> o.apply((RocfUserToProviderPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
	}
}
