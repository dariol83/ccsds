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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleStopInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPduV1toV2;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPduV3toV4;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * RAF encoding/decoding extension class.
 */
public class RafEncDec extends CommonEncDec {

	private final List<Function<RafProviderToUserPduV1toV2, BerType>> unwrapFunctionV1V2List;
	private final List<Function<RafProviderToUserPduV3toV4, BerType>> unwrapFunctionV3V4List;
	private final List<Function<RafProviderToUserPdu, BerType>> unwrapFunctionV5List;

	public RafEncDec() {
		register(1, RafProviderToUserPduV1toV2::new);
		register(2, RafProviderToUserPduV1toV2::new);
		register(3, RafProviderToUserPduV3toV4::new);
		register(4, RafProviderToUserPduV3toV4::new);
		register(5, RafProviderToUserPdu::new);

		// V1 V2 unwrappers
		unwrapFunctionV1V2List = new ArrayList<>();
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafTransferBuffer);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafStatusReportInvocation);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafGetParameterReturn);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafScheduleStatusReportReturn);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafBindInvocation);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafBindReturn);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafUnbindInvocation);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafUnbindReturn);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafStartReturn);
		unwrapFunctionV1V2List.add(RafProviderToUserPduV1toV2::getRafStopReturn);

		// V3 V4 unwrappers
		unwrapFunctionV3V4List = new ArrayList<>();
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafTransferBuffer);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafStatusReportInvocation);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafGetParameterReturn);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafScheduleStatusReportReturn);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafBindInvocation);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafBindReturn);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafUnbindInvocation);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafUnbindReturn);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafStartReturn);
		unwrapFunctionV3V4List.add(RafProviderToUserPduV3toV4::getRafStopReturn);

		// V5 unwrappers
		unwrapFunctionV5List = new ArrayList<>();
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafTransferBuffer);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafStatusReportInvocation);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafGetParameterReturn);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafScheduleStatusReportReturn);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafBindInvocation);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafBindReturn);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafUnbindInvocation);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafUnbindReturn);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafStartReturn);
		unwrapFunctionV5List.add(RafProviderToUserPdu::getRafStopReturn);
	}

	@Override
	protected Supplier<? extends BerType> getDefaultDecodingProvider() {
		return RafProviderToUserPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		RafUserToProviderPdu wrapper = new RafUserToProviderPdu();
		if(toEncode instanceof SleBindInvocation) {
			wrapper.setRafBindInvocation((SleBindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindInvocation) {
			wrapper.setRafUnbindInvocation((SleUnbindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindReturn) {
			wrapper.setRafUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setRafBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportInvocation) {
			wrapper.setRafScheduleStatusReportInvocation((SleScheduleStatusReportInvocation) toEncode);
		} else if(toEncode instanceof RafStartInvocation) {
			wrapper.setRafStartInvocation((RafStartInvocation) toEncode);
		} else if(toEncode instanceof SleStopInvocation) {
			wrapper.setRafStopInvocation((SleStopInvocation) toEncode);
		} else if(toEncode instanceof RafGetParameterInvocation) {
			wrapper.setRafGetParameterInvocation((RafGetParameterInvocation) toEncode);
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
				return returnOrThrow(this.unwrapFunctionV1V2List.parallelStream().map(o -> o.apply((RafProviderToUserPduV1toV2) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
			case 3:
			case 4:
				return returnOrThrow(this.unwrapFunctionV3V4List.parallelStream().map(o -> o.apply((RafProviderToUserPduV3toV4) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
			default:
				return returnOrThrow(this.unwrapFunctionV5List.parallelStream().map(o -> o.apply((RafProviderToUserPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
		}
	}
}
