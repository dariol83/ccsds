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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfProviderToUserPduV1toV4;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class RocfEncDec extends CommonEncDec {


	private final List<Function<RocfProviderToUserPduV1toV4, BerType>> unwrapFunctionV1V4List;
	private final List<Function<RocfProviderToUserPdu, BerType>> unwrapFunctionV5List;

	public RocfEncDec() {
		register(1, RocfProviderToUserPduV1toV4::new);
		register(2, RocfProviderToUserPduV1toV4::new);
		register(3, RocfProviderToUserPduV1toV4::new);
		register(4, RocfProviderToUserPduV1toV4::new);
		register(5, RocfProviderToUserPdu::new);

		// V1 V4 unwrappers
		unwrapFunctionV1V4List = new ArrayList<>();
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfTransferBuffer);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfStatusReportInvocation);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfGetParameterReturn);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfScheduleStatusReportReturn);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfBindInvocation);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfBindReturn);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfUnbindInvocation);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfUnbindReturn);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfStartReturn);
		unwrapFunctionV1V4List.add(RocfProviderToUserPduV1toV4::getRocfStopReturn);

		// V5 unwrappers
		unwrapFunctionV5List = new ArrayList<>();
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfTransferBuffer);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfStatusReportInvocation);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfGetParameterReturn);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfScheduleStatusReportReturn);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfBindInvocation);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfBindReturn);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfUnbindInvocation);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfUnbindReturn);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfStartReturn);
		unwrapFunctionV5List.add(RocfProviderToUserPdu::getRocfStopReturn);
	}

	@Override
	protected Supplier<BerType> getDefaultDecodingProvider() {
		return RocfProviderToUserPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		RocfUserToProviderPdu wrapper = new RocfUserToProviderPdu();
		if(toEncode instanceof SleBindInvocation) {
			wrapper.setRocfBindInvocation((SleBindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindInvocation) {
			wrapper.setRocfUnbindInvocation((SleUnbindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindReturn) {
			wrapper.setRocfUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setRocfBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportInvocation) {
			wrapper.setRocfScheduleStatusReportInvocation((SleScheduleStatusReportInvocation) toEncode);
		} else if(toEncode instanceof RocfStartInvocation) {
			wrapper.setRocfStartInvocation((RocfStartInvocation) toEncode);
		} else if(toEncode instanceof SleStopInvocation) {
			wrapper.setRocfStopInvocation((SleStopInvocation) toEncode);
		} else if(toEncode instanceof RocfGetParameterInvocation) {
			wrapper.setRocfGetParameterInvocation((RocfGetParameterInvocation) toEncode);
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
			case 4:
				return returnOrThrow(this.unwrapFunctionV1V4List.parallelStream().map(o -> o.apply((RocfProviderToUserPduV1toV4) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
			default:
				return returnOrThrow(this.unwrapFunctionV5List.parallelStream().map(o -> o.apply((RocfProviderToUserPdu) toDecode)).filter(Objects::nonNull).findFirst(), toDecode);
		}
	}
}
