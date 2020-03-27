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

package eu.dariolucia.ccsds.sle.utl.encdec;

import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * RAF encoding/decoding extension class.
 *
 * This class is meant for testing only. If you use this class to implement your own SLE provider, you have been warned.
 */
public class RafProviderEncDec extends CommonEncDec {

	private final List<Function<RafUserToProviderPdu, BerType>> unwrapFunctionList;

	public RafProviderEncDec() {
		register(1, RafUserToProviderPdu::new);
		register(2, RafUserToProviderPdu::new);
		register(3, RafUserToProviderPdu::new);
		register(4, RafUserToProviderPdu::new);
		register(5, RafUserToProviderPdu::new);

		// Unwrappers
		unwrapFunctionList = new ArrayList<>();
		unwrapFunctionList.add(RafUserToProviderPdu::getRafScheduleStatusReportInvocation);
		unwrapFunctionList.add(RafUserToProviderPdu::getRafGetParameterInvocation);
		unwrapFunctionList.add(RafUserToProviderPdu::getRafBindInvocation);
		unwrapFunctionList.add(RafUserToProviderPdu::getRafBindReturn);
		unwrapFunctionList.add(RafUserToProviderPdu::getRafUnbindInvocation);
		unwrapFunctionList.add(RafUserToProviderPdu::getRafUnbindReturn);
		unwrapFunctionList.add(RafUserToProviderPdu::getRafStartInvocation);
		unwrapFunctionList.add(RafUserToProviderPdu::getRafStopInvocation);
	}

	@Override
	protected Supplier<BerType> getDefaultDecodingProvider() {
		return RafUserToProviderPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		switch(getVersion()) {
			case 1:
			case 2:
				return wrapIntoV1toV2(toEncode);
			case 3:
			case 4:
				return wrapIntoV3toV4(toEncode);
			default:
				return wrapIntoLatest(toEncode);
		}
	}

	private BerType wrapIntoLatest(BerType toEncode) throws EncodingException {
		RafProviderToUserPdu wrapper = new RafProviderToUserPdu();
		if(toEncode instanceof SleBindInvocation) {
			wrapper.setRafBindInvocation((SleBindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindInvocation) {
			wrapper.setRafUnbindInvocation((SleUnbindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindReturn) {
			wrapper.setRafUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setRafBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportReturn) {
			wrapper.setRafScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
		} else if(toEncode instanceof RafStatusReportInvocation) {
			wrapper.setRafStatusReportInvocation((RafStatusReportInvocation) toEncode);
		} else if(toEncode instanceof RafStartReturn) {
			wrapper.setRafStartReturn((RafStartReturn) toEncode);
		} else if(toEncode instanceof SleAcknowledgement) {
			wrapper.setRafStopReturn((SleAcknowledgement) toEncode);
		} else if(toEncode instanceof RafGetParameterReturn) {
			wrapper.setRafGetParameterReturn((RafGetParameterReturn) toEncode);
		} else if(toEncode instanceof RafTransferBuffer) {
			wrapper.setRafTransferBuffer((RafTransferBuffer) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName()); // NOSONAR: text in log
		}
		return wrapper;
	}

	private BerType wrapIntoV3toV4(BerType toEncode) throws EncodingException {
		RafProviderToUserPduV3toV4 wrapper = new RafProviderToUserPduV3toV4();
		if(toEncode instanceof SleBindInvocation) {
			wrapper.setRafBindInvocation((SleBindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindInvocation) {
			wrapper.setRafUnbindInvocation((SleUnbindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindReturn) {
			wrapper.setRafUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setRafBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportReturn) {
			wrapper.setRafScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
		} else if(toEncode instanceof RafStatusReportInvocation) {
			wrapper.setRafStatusReportInvocation((RafStatusReportInvocation) toEncode);
		} else if(toEncode instanceof RafStartReturn) {
			wrapper.setRafStartReturn((RafStartReturn) toEncode);
		} else if(toEncode instanceof SleAcknowledgement) {
			wrapper.setRafStopReturn((SleAcknowledgement) toEncode);
		} else if(toEncode instanceof RafGetParameterReturnV1toV4) {
			wrapper.setRafGetParameterReturn((RafGetParameterReturnV1toV4) toEncode);
		} else if(toEncode instanceof RafTransferBuffer) {
			wrapper.setRafTransferBuffer((RafTransferBuffer) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName()); // NOSONAR: text in log
		}
		return wrapper;
	}

	private BerType wrapIntoV1toV2(BerType toEncode) throws EncodingException {
		RafProviderToUserPduV1toV2 wrapper = new RafProviderToUserPduV1toV2();
		if(toEncode instanceof SleBindInvocation) {
			wrapper.setRafBindInvocation((SleBindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindInvocation) {
			wrapper.setRafUnbindInvocation((SleUnbindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindReturn) {
			wrapper.setRafUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setRafBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportReturn) {
			wrapper.setRafScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
		} else if(toEncode instanceof RafStatusReportInvocationV1toV2) {
			wrapper.setRafStatusReportInvocation((RafStatusReportInvocationV1toV2) toEncode);
		} else if(toEncode instanceof RafStartReturn) {
			wrapper.setRafStartReturn((RafStartReturn) toEncode);
		} else if(toEncode instanceof SleAcknowledgement) {
			wrapper.setRafStopReturn((SleAcknowledgement) toEncode);
		} else if(toEncode instanceof RafGetParameterReturnV1toV4) {
			wrapper.setRafGetParameterReturn((RafGetParameterReturnV1toV4) toEncode);
		} else if(toEncode instanceof RafTransferBuffer) {
			wrapper.setRafTransferBuffer((RafTransferBuffer) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName()); // NOSONAR: text in log
		}
		return wrapper;
	}

	@Override
	protected BerType unwrapPdu(BerType toDecode) throws DecodingException {
		return returnOrThrow(this.unwrapFunctionList.parallelStream().map(o -> o.apply((RafUserToProviderPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
	}
}
