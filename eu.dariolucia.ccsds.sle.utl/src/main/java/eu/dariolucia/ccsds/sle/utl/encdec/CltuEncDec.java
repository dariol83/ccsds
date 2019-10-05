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

package eu.dariolucia.ccsds.sle.utl.encdec;

import com.beanit.jasn1.ber.types.BerType;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuProviderToUserPduV1toV3;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuProviderToUserPduV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleStopInvocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class CltuEncDec extends CommonEncDec {

	private final List<Function<CltuProviderToUserPduV1toV3, BerType>> unwrapFunctionV1V3List;
	private final List<Function<CltuProviderToUserPduV4, BerType>> unwrapFunctionV4List;
	private final List<Function<CltuProviderToUserPdu, BerType>> unwrapFunctionV5List;

	public CltuEncDec() {
		register(1, CltuProviderToUserPduV1toV3::new);
		register(2, CltuProviderToUserPduV1toV3::new);
		register(3, CltuProviderToUserPduV1toV3::new);
		register(4, CltuProviderToUserPduV4::new);
		register(5, CltuProviderToUserPdu::new);

		// V1 V2 unwrappers
		unwrapFunctionV1V3List = new ArrayList<>();
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuTransferDataReturn);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuAsyncNotifyInvocation);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuStatusReportInvocation);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuGetParameterReturn);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuScheduleStatusReportReturn);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuThrowEventReturn);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuBindReturn);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuUnbindReturn);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuStartReturn);
		unwrapFunctionV1V3List.add(CltuProviderToUserPduV1toV3::getCltuStopReturn);

		// V3 V4 unwrappers
		unwrapFunctionV4List = new ArrayList<>();
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuTransferDataReturn);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuAsyncNotifyInvocation);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuStatusReportInvocation);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuGetParameterReturn);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuScheduleStatusReportReturn);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuThrowEventReturn);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuBindReturn);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuUnbindReturn);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuStartReturn);
		unwrapFunctionV4List.add(CltuProviderToUserPduV4::getCltuStopReturn);

		// V5 unwrappers
		unwrapFunctionV5List = new ArrayList<>();
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuTransferDataReturn);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuAsyncNotifyInvocation);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuStatusReportInvocation);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuGetParameterReturn);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuScheduleStatusReportReturn);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuThrowEventReturn);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuBindReturn);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuUnbindReturn);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuStartReturn);
		unwrapFunctionV5List.add(CltuProviderToUserPdu::getCltuStopReturn);
	}

	@Override
	protected Supplier<BerType> getDefaultDecodingProvider() {
		return CltuProviderToUserPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		CltuUserToProviderPdu wrapper = new CltuUserToProviderPdu();
		if(toEncode instanceof SleBindInvocation) {
			wrapper.setCltuBindInvocation((SleBindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindInvocation) {
			wrapper.setCltuUnbindInvocation((SleUnbindInvocation) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportInvocation) {
			wrapper.setCltuScheduleStatusReportInvocation((SleScheduleStatusReportInvocation) toEncode);
		} else if(toEncode instanceof CltuStartInvocation) {
			wrapper.setCltuStartInvocation((CltuStartInvocation) toEncode);
		} else if(toEncode instanceof SleStopInvocation) {
			wrapper.setCltuStopInvocation((SleStopInvocation) toEncode);
		} else if(toEncode instanceof CltuTransferDataInvocation) {
			wrapper.setCltuTransferDataInvocation((CltuTransferDataInvocation) toEncode);
		} else if(toEncode instanceof CltuGetParameterInvocation) {
			wrapper.setCltuGetParameterInvocation((CltuGetParameterInvocation) toEncode);
		} else if(toEncode instanceof CltuThrowEventInvocation) {
			wrapper.setCltuThrowEventInvocation((CltuThrowEventInvocation) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName());
		}
		return wrapper;
	}

	@Override
	protected BerType unwrapPdu(BerType toDecode) throws DecodingException {
		switch(getVersion()) {
			case 1:
			case 2:
			case 3:
				return returnOrThrow(this.unwrapFunctionV1V3List.parallelStream().map(o -> o.apply((CltuProviderToUserPduV1toV3) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
			case 4:
				return returnOrThrow(this.unwrapFunctionV4List.parallelStream().map(o -> o.apply((CltuProviderToUserPduV4) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
			default:
				return returnOrThrow(this.unwrapFunctionV5List.parallelStream().map(o -> o.apply((CltuProviderToUserPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
		}
	}
}
