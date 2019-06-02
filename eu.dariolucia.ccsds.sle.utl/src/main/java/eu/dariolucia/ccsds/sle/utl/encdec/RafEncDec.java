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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.*;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPduV1toV2;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPduV3toV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleScheduleStatusReportInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus.SleStopInvocation;

import java.util.function.Supplier;

public class RafEncDec extends CommonEncDec {

	public RafEncDec() {
		register(1, RafProviderToUserPduV1toV2::new);
		register(2, RafProviderToUserPduV1toV2::new);
		register(3, RafProviderToUserPduV3toV4::new);
		register(4, RafProviderToUserPduV3toV4::new);
		register(5, RafProviderToUserPdu::new);
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
				return unwrap((RafProviderToUserPduV1toV2) toDecode);
			case 3:
			case 4:
				return unwrap((RafProviderToUserPduV3toV4) toDecode);
			default:
				return unwrap((RafProviderToUserPdu) toDecode);
		}
	}

	private BerType unwrap(RafProviderToUserPdu toDecode) throws DecodingException {
		if(toDecode.getRafTransferBuffer() != null) {
			return toDecode.getRafTransferBuffer();
		}
		if(toDecode.getRafStatusReportInvocation() != null) {
			return toDecode.getRafStatusReportInvocation();
		}
		if(toDecode.getRafBindReturn() != null) {
			return toDecode.getRafBindReturn();
		}
		if(toDecode.getRafBindInvocation() != null) {
			return toDecode.getRafBindInvocation();
		}
		if(toDecode.getRafGetParameterReturn() != null) {
			return toDecode.getRafGetParameterReturn();
		}
		if(toDecode.getRafScheduleStatusReportReturn() != null) {
			return toDecode.getRafScheduleStatusReportReturn();
		}
		if(toDecode.getRafStartReturn() != null) {
			return toDecode.getRafStartReturn();
		}
		if(toDecode.getRafStopReturn() != null) {
			return toDecode.getRafStopReturn();
		}
		if(toDecode.getRafUnbindInvocation() != null) {
			return toDecode.getRafUnbindInvocation();
		}
		if(toDecode.getRafUnbindReturn() != null) {
			return toDecode.getRafUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

	private BerType unwrap(RafProviderToUserPduV1toV2 toDecode) throws DecodingException {
		if(toDecode.getRafTransferBuffer() != null) {
			return toDecode.getRafTransferBuffer();
		}
		if(toDecode.getRafStatusReportInvocation() != null) {
			return toDecode.getRafStatusReportInvocation();
		}
		if(toDecode.getRafBindReturn() != null) {
			return toDecode.getRafBindReturn();
		}
		if(toDecode.getRafBindInvocation() != null) {
			return toDecode.getRafBindInvocation();
		}
		if(toDecode.getRafGetParameterReturn() != null) {
			return toDecode.getRafGetParameterReturn();
		}
		if(toDecode.getRafScheduleStatusReportReturn() != null) {
			return toDecode.getRafScheduleStatusReportReturn();
		}
		if(toDecode.getRafStartReturn() != null) {
			return toDecode.getRafStartReturn();
		}
		if(toDecode.getRafStopReturn() != null) {
			return toDecode.getRafStopReturn();
		}
		if(toDecode.getRafUnbindInvocation() != null) {
			return toDecode.getRafUnbindInvocation();
		}
		if(toDecode.getRafUnbindReturn() != null) {
			return toDecode.getRafUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

	private BerType unwrap(RafProviderToUserPduV3toV4 toDecode) throws DecodingException {
		if(toDecode.getRafTransferBuffer() != null) {
			return toDecode.getRafTransferBuffer();
		}
		if(toDecode.getRafStatusReportInvocation() != null) {
			return toDecode.getRafStatusReportInvocation();
		}
		if(toDecode.getRafBindReturn() != null) {
			return toDecode.getRafBindReturn();
		}
		if(toDecode.getRafBindInvocation() != null) {
			return toDecode.getRafBindInvocation();
		}
		if(toDecode.getRafGetParameterReturn() != null) {
			return toDecode.getRafGetParameterReturn();
		}
		if(toDecode.getRafScheduleStatusReportReturn() != null) {
			return toDecode.getRafScheduleStatusReportReturn();
		}
		if(toDecode.getRafStartReturn() != null) {
			return toDecode.getRafStartReturn();
		}
		if(toDecode.getRafStopReturn() != null) {
			return toDecode.getRafStopReturn();
		}
		if(toDecode.getRafUnbindInvocation() != null) {
			return toDecode.getRafUnbindInvocation();
		}
		if(toDecode.getRafUnbindReturn() != null) {
			return toDecode.getRafUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}
	
}
