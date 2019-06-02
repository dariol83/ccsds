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

import java.util.function.Supplier;

public class CltuEncDec extends CommonEncDec {

	public CltuEncDec() {
		register(1, CltuProviderToUserPduV1toV3::new);
		register(2, CltuProviderToUserPduV1toV3::new);
		register(3, CltuProviderToUserPduV1toV3::new);
		register(4, CltuProviderToUserPduV4::new);
		register(5, CltuProviderToUserPdu::new);
	}

	@Override
	protected Supplier<? extends BerType> getDefaultDecodingProvider() {
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
				return unwrap((CltuProviderToUserPduV1toV3) toDecode);
			case 4:
				return unwrap((CltuProviderToUserPduV4) toDecode);
			default:
				return unwrap((CltuProviderToUserPdu) toDecode);
		}
	}

	private BerType unwrap(CltuProviderToUserPdu toDecode) throws DecodingException {
		if(toDecode.getCltuAsyncNotifyInvocation() != null) {
			return toDecode.getCltuAsyncNotifyInvocation();
		}
		if(toDecode.getCltuBindReturn() != null) {
			return toDecode.getCltuBindReturn();
		}
		if(toDecode.getCltuGetParameterReturn() != null) {
			return toDecode.getCltuGetParameterReturn();
		}
		if(toDecode.getCltuScheduleStatusReportReturn() != null) {
			return toDecode.getCltuScheduleStatusReportReturn();
		}
		if(toDecode.getCltuStartReturn() != null) {
			return toDecode.getCltuStartReturn();
		}
		if(toDecode.getCltuStatusReportInvocation() != null) {
			return toDecode.getCltuStatusReportInvocation();
		}
		if(toDecode.getCltuStopReturn() != null) {
			return toDecode.getCltuStopReturn();
		}
		if(toDecode.getCltuThrowEventReturn() != null) {
			return toDecode.getCltuThrowEventReturn();
		}
		if(toDecode.getCltuTransferDataReturn() != null) {
			return toDecode.getCltuTransferDataReturn();
		}
		if(toDecode.getCltuUnbindReturn() != null) {
			return toDecode.getCltuUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

	private BerType unwrap(CltuProviderToUserPduV4 toDecode) throws DecodingException {
		if(toDecode.getCltuAsyncNotifyInvocation() != null) {
			return toDecode.getCltuAsyncNotifyInvocation();
		}
		if(toDecode.getCltuBindReturn() != null) {
			return toDecode.getCltuBindReturn();
		}
		if(toDecode.getCltuGetParameterReturn() != null) {
			return toDecode.getCltuGetParameterReturn();
		}
		if(toDecode.getCltuScheduleStatusReportReturn() != null) {
			return toDecode.getCltuScheduleStatusReportReturn();
		}
		if(toDecode.getCltuStartReturn() != null) {
			return toDecode.getCltuStartReturn();
		}
		if(toDecode.getCltuStatusReportInvocation() != null) {
			return toDecode.getCltuStatusReportInvocation();
		}
		if(toDecode.getCltuStopReturn() != null) {
			return toDecode.getCltuStopReturn();
		}
		if(toDecode.getCltuThrowEventReturn() != null) {
			return toDecode.getCltuThrowEventReturn();
		}
		if(toDecode.getCltuTransferDataReturn() != null) {
			return toDecode.getCltuTransferDataReturn();
		}
		if(toDecode.getCltuUnbindReturn() != null) {
			return toDecode.getCltuUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

	private BerType unwrap(CltuProviderToUserPduV1toV3 toDecode) throws DecodingException {
		if(toDecode.getCltuAsyncNotifyInvocation() != null) {
			return toDecode.getCltuAsyncNotifyInvocation();
		}
		if(toDecode.getCltuBindReturn() != null) {
			return toDecode.getCltuBindReturn();
		}
		if(toDecode.getCltuGetParameterReturn() != null) {
			return toDecode.getCltuGetParameterReturn();
		}
		if(toDecode.getCltuScheduleStatusReportReturn() != null) {
			return toDecode.getCltuScheduleStatusReportReturn();
		}
		if(toDecode.getCltuStartReturn() != null) {
			return toDecode.getCltuStartReturn();
		}
		if(toDecode.getCltuStatusReportInvocation() != null) {
			return toDecode.getCltuStatusReportInvocation();
		}
		if(toDecode.getCltuStopReturn() != null) {
			return toDecode.getCltuStopReturn();
		}
		if(toDecode.getCltuThrowEventReturn() != null) {
			return toDecode.getCltuThrowEventReturn();
		}
		if(toDecode.getCltuTransferDataReturn() != null) {
			return toDecode.getCltuTransferDataReturn();
		}
		if(toDecode.getCltuUnbindReturn() != null) {
			return toDecode.getCltuUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}
}
