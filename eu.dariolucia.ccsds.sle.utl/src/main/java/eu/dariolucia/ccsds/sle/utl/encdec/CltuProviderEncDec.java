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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleAcknowledgement;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportReturn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * CLTU encoding/decoding extension class.
 *
 * This class is meant for testing only. If you use this class to implement your own SLE provider, you have been warned.
 */
public class CltuProviderEncDec extends CommonEncDec {

	private final List<Function<CltuUserToProviderPdu, BerType>> unwrapFunctionList;

	public CltuProviderEncDec() {
		register(1, CltuUserToProviderPdu::new);
		register(2, CltuUserToProviderPdu::new);
		register(3, CltuUserToProviderPdu::new);
		register(4, CltuUserToProviderPdu::new);
		register(5, CltuUserToProviderPdu::new);

		// Unwrappers
		unwrapFunctionList = new ArrayList<>();
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuScheduleStatusReportInvocation);
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuGetParameterInvocation);
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuBindInvocation);
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuThrowEventInvocation);
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuUnbindInvocation);
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuTransferDataInvocation);
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuStartInvocation);
		unwrapFunctionList.add(CltuUserToProviderPdu::getCltuStopInvocation);
	}

	@Override
	protected Supplier<BerType> getDefaultDecodingProvider() {
		return CltuUserToProviderPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		switch(getVersion()) {
			case 1:
			case 2:
			case 3:
				return wrapIntoV1toV3(toEncode);
			case 4:
				return wrapIntoV4(toEncode);
			default:
				return wrapIntoLatest(toEncode);
		}
	}

	private BerType wrapIntoLatest(BerType toEncode) throws EncodingException {
		CltuProviderToUserPdu wrapper = new CltuProviderToUserPdu();
		if(toEncode instanceof SleUnbindReturn) {
			wrapper.setCltuUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setCltuBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportReturn) {
			wrapper.setCltuScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
		} else if(toEncode instanceof CltuStatusReportInvocation) {
			wrapper.setCltuStatusReportInvocation((CltuStatusReportInvocation) toEncode);
		} else if(toEncode instanceof CltuStartReturn) {
			wrapper.setCltuStartReturn((CltuStartReturn) toEncode);
		} else if(toEncode instanceof SleAcknowledgement) {
			wrapper.setCltuStopReturn((SleAcknowledgement) toEncode);
		} else if(toEncode instanceof CltuGetParameterReturn) {
			wrapper.setCltuGetParameterReturn((CltuGetParameterReturn) toEncode);
		} else if(toEncode instanceof CltuAsyncNotifyInvocation) {
			wrapper.setCltuAsyncNotifyInvocation((CltuAsyncNotifyInvocation) toEncode);
		} else if(toEncode instanceof CltuTransferDataReturn) {
			wrapper.setCltuTransferDataReturn((CltuTransferDataReturn) toEncode);
		} else if(toEncode instanceof CltuThrowEventReturn) {
			wrapper.setCltuThrowEventReturn((CltuThrowEventReturn) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName()); // NOSONAR: text in log
		}
		return wrapper;
	}

	private BerType wrapIntoV4(BerType toEncode) throws EncodingException {
		CltuProviderToUserPduV4 wrapper = new CltuProviderToUserPduV4();
		if(toEncode instanceof SleUnbindReturn) {
			wrapper.setCltuUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setCltuBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportReturn) {
			wrapper.setCltuScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
		} else if(toEncode instanceof CltuStatusReportInvocation) {
			wrapper.setCltuStatusReportInvocation((CltuStatusReportInvocation) toEncode);
		} else if(toEncode instanceof CltuStartReturn) {
			wrapper.setCltuStartReturn((CltuStartReturn) toEncode);
		} else if(toEncode instanceof SleAcknowledgement) {
			wrapper.setCltuStopReturn((SleAcknowledgement) toEncode);
		} else if(toEncode instanceof CltuGetParameterReturnV4) {
			wrapper.setCltuGetParameterReturn((CltuGetParameterReturnV4) toEncode);
		} else if(toEncode instanceof CltuAsyncNotifyInvocation) {
			wrapper.setCltuAsyncNotifyInvocation((CltuAsyncNotifyInvocation) toEncode);
		} else if(toEncode instanceof CltuTransferDataReturn) {
			wrapper.setCltuTransferDataReturn((CltuTransferDataReturn) toEncode);
		} else if(toEncode instanceof CltuThrowEventReturn) {
			wrapper.setCltuThrowEventReturn((CltuThrowEventReturn) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName()); // NOSONAR: text in log
		}
		return wrapper;
	}

	private BerType wrapIntoV1toV3(BerType toEncode) throws EncodingException {
		CltuProviderToUserPduV1toV3 wrapper = new CltuProviderToUserPduV1toV3();
		if(toEncode instanceof SleUnbindReturn) {
			wrapper.setCltuUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setCltuBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportReturn) {
			wrapper.setCltuScheduleStatusReportReturn((SleScheduleStatusReportReturn) toEncode);
		} else if(toEncode instanceof CltuStatusReportInvocation) {
			wrapper.setCltuStatusReportInvocation((CltuStatusReportInvocation) toEncode);
		} else if(toEncode instanceof CltuStartReturn) {
			wrapper.setCltuStartReturn((CltuStartReturn) toEncode);
		} else if(toEncode instanceof SleAcknowledgement) {
			wrapper.setCltuStopReturn((SleAcknowledgement) toEncode);
		} else if(toEncode instanceof CltuGetParameterReturnV1toV3) {
			wrapper.setCltuGetParameterReturn((CltuGetParameterReturnV1toV3) toEncode);
		} else if(toEncode instanceof CltuAsyncNotifyInvocation) {
			wrapper.setCltuAsyncNotifyInvocation((CltuAsyncNotifyInvocation) toEncode);
		} else if(toEncode instanceof CltuTransferDataReturn) {
			wrapper.setCltuTransferDataReturn((CltuTransferDataReturn) toEncode);
		} else if(toEncode instanceof CltuThrowEventReturn) {
			wrapper.setCltuThrowEventReturn((CltuThrowEventReturn) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName()); // NOSONAR: text in log
		}
		return wrapper;
	}

	@Override
	protected BerType unwrapPdu(BerType toDecode) throws DecodingException {
		return returnOrThrow(this.unwrapFunctionList.parallelStream().map(o -> o.apply((CltuUserToProviderPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
	}
}
