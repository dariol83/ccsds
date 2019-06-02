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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfGetParameterInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPduV1;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPduV2toV4;

import java.util.function.Supplier;

public class RcfEncDec extends CommonEncDec {

	public RcfEncDec() {
		register(1, RcfProviderToUserPduV1::new);
		register(2, RcfProviderToUserPduV2toV4::new);
		register(3, RcfProviderToUserPduV2toV4::new);
		register(4, RcfProviderToUserPduV2toV4::new);
		register(5, RcfProviderToUserPdu::new);
	}

	@Override
	protected Supplier<? extends BerType> getDefaultDecodingProvider() {
		return RcfProviderToUserPdu::new;
	}

	@Override
	protected BerType wrapPdu(BerType toEncode) throws EncodingException {
		RcfUserToProviderPdu wrapper = new RcfUserToProviderPdu();
		if(toEncode instanceof SleBindInvocation) {
			wrapper.setRcfBindInvocation((SleBindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindInvocation) {
			wrapper.setRcfUnbindInvocation((SleUnbindInvocation) toEncode);
		} else if(toEncode instanceof SleUnbindReturn) {
			wrapper.setRcfUnbindReturn((SleUnbindReturn) toEncode);
		} else if(toEncode instanceof SleBindReturn) {
			wrapper.setRcfBindReturn((SleBindReturn) toEncode);
		} else if(toEncode instanceof SleScheduleStatusReportInvocation) {
			wrapper.setRcfScheduleStatusReportInvocation((SleScheduleStatusReportInvocation) toEncode);
		} else if(toEncode instanceof RcfStartInvocation) {
			wrapper.setRcfStartInvocation((RcfStartInvocation) toEncode);
		} else if(toEncode instanceof SleStopInvocation) {
			wrapper.setRcfStopInvocation((SleStopInvocation) toEncode);
		} else if(toEncode instanceof RcfGetParameterInvocation) {
			wrapper.setRcfGetParameterInvocation((RcfGetParameterInvocation) toEncode);
		} else {
			throw new EncodingException("Type " + toEncode + " not supported by encoder " +getClass().getSimpleName());
		}
		return wrapper;
	}

	@Override
	protected BerType unwrapPdu(BerType toDecode) throws DecodingException {
		switch(getVersion()) {
			case 1:
				return unwrap((RcfProviderToUserPduV1) toDecode);
			case 2:
			case 3:
			case 4:
				return unwrap((RcfProviderToUserPduV2toV4) toDecode);
			default:
				return unwrap((RcfProviderToUserPdu) toDecode);
		}
	}

	private BerType unwrap(RcfProviderToUserPdu toDecode) throws DecodingException {
		if(toDecode.getRcfTransferBuffer() != null) {
			return toDecode.getRcfTransferBuffer();
		}
		if(toDecode.getRcfStatusReportInvocation() != null) {
			return toDecode.getRcfStatusReportInvocation();
		}
		if(toDecode.getRcfBindReturn() != null) {
			return toDecode.getRcfBindReturn();
		}
		if(toDecode.getRcfBindInvocation() != null) {
			return toDecode.getRcfBindInvocation();
		}
		if(toDecode.getRcfGetParameterReturn() != null) {
			return toDecode.getRcfGetParameterReturn();
		}
		if(toDecode.getRcfScheduleStatusReportReturn() != null) {
			return toDecode.getRcfScheduleStatusReportReturn();
		}
		if(toDecode.getRcfStartReturn() != null) {
			return toDecode.getRcfStartReturn();
		}
		if(toDecode.getRcfStopReturn() != null) {
			return toDecode.getRcfStopReturn();
		}
		if(toDecode.getRcfUnbindInvocation() != null) {
			return toDecode.getRcfUnbindInvocation();
		}
		if(toDecode.getRcfUnbindReturn() != null) {
			return toDecode.getRcfUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

	private BerType unwrap(RcfProviderToUserPduV1 toDecode) throws DecodingException {
		if(toDecode.getRcfTransferBuffer() != null) {
			return toDecode.getRcfTransferBuffer();
		}
		if(toDecode.getRcfStatusReportInvocation() != null) {
			return toDecode.getRcfStatusReportInvocation();
		}
		if(toDecode.getRcfBindReturn() != null) {
			return toDecode.getRcfBindReturn();
		}
		if(toDecode.getRcfBindInvocation() != null) {
			return toDecode.getRcfBindInvocation();
		}
		if(toDecode.getRcfGetParameterReturn() != null) {
			return toDecode.getRcfGetParameterReturn();
		}
		if(toDecode.getRcfScheduleStatusReportReturn() != null) {
			return toDecode.getRcfScheduleStatusReportReturn();
		}
		if(toDecode.getRcfStartReturn() != null) {
			return toDecode.getRcfStartReturn();
		}
		if(toDecode.getRcfStopReturn() != null) {
			return toDecode.getRcfStopReturn();
		}
		if(toDecode.getRcfUnbindInvocation() != null) {
			return toDecode.getRcfUnbindInvocation();
		}
		if(toDecode.getRcfUnbindReturn() != null) {
			return toDecode.getRcfUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

	private BerType unwrap(RcfProviderToUserPduV2toV4 toDecode) throws DecodingException {
		if(toDecode.getRcfTransferBuffer() != null) {
			return toDecode.getRcfTransferBuffer();
		}
		if(toDecode.getRcfStatusReportInvocation() != null) {
			return toDecode.getRcfStatusReportInvocation();
		}
		if(toDecode.getRcfBindReturn() != null) {
			return toDecode.getRcfBindReturn();
		}
		if(toDecode.getRcfBindInvocation() != null) {
			return toDecode.getRcfBindInvocation();
		}
		if(toDecode.getRcfGetParameterReturn() != null) {
			return toDecode.getRcfGetParameterReturn();
		}
		if(toDecode.getRcfScheduleStatusReportReturn() != null) {
			return toDecode.getRcfScheduleStatusReportReturn();
		}
		if(toDecode.getRcfStartReturn() != null) {
			return toDecode.getRcfStartReturn();
		}
		if(toDecode.getRcfStopReturn() != null) {
			return toDecode.getRcfStopReturn();
		}
		if(toDecode.getRcfUnbindInvocation() != null) {
			return toDecode.getRcfUnbindInvocation();
		}
		if(toDecode.getRcfUnbindReturn() != null) {
			return toDecode.getRcfUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

}
