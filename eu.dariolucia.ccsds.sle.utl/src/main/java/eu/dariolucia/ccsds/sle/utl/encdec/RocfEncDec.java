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

import java.util.function.Supplier;

public class RocfEncDec extends CommonEncDec {

	public RocfEncDec() {
		register(1, RocfProviderToUserPduV1toV4::new);
		register(2, RocfProviderToUserPduV1toV4::new);
		register(3, RocfProviderToUserPduV1toV4::new);
		register(4, RocfProviderToUserPduV1toV4::new);
		register(5, RocfProviderToUserPdu::new);
	}

	@Override
	protected Supplier<? extends BerType> getDefaultDecodingProvider() {
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
				return unwrap((RocfProviderToUserPduV1toV4) toDecode);
			default:
				return unwrap((RocfProviderToUserPdu) toDecode);
		}
	}

	private BerType unwrap(RocfProviderToUserPdu toDecode) throws DecodingException {
		if(toDecode.getRocfTransferBuffer() != null) {
			return toDecode.getRocfTransferBuffer();
		}
		if(toDecode.getRocfStatusReportInvocation() != null) {
			return toDecode.getRocfStatusReportInvocation();
		}
		if(toDecode.getRocfBindReturn() != null) {
			return toDecode.getRocfBindReturn();
		}
		if(toDecode.getRocfBindInvocation() != null) {
			return toDecode.getRocfBindInvocation();
		}
		if(toDecode.getRocfGetParameterReturn() != null) {
			return toDecode.getRocfGetParameterReturn();
		}
		if(toDecode.getRocfScheduleStatusReportReturn() != null) {
			return toDecode.getRocfScheduleStatusReportReturn();
		}
		if(toDecode.getRocfStartReturn() != null) {
			return toDecode.getRocfStartReturn();
		}
		if(toDecode.getRocfStopReturn() != null) {
			return toDecode.getRocfStopReturn();
		}
		if(toDecode.getRocfUnbindInvocation() != null) {
			return toDecode.getRocfUnbindInvocation();
		}
		if(toDecode.getRocfUnbindReturn() != null) {
			return toDecode.getRocfUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}

	private BerType unwrap(RocfProviderToUserPduV1toV4 toDecode) throws DecodingException {
		if(toDecode.getRocfTransferBuffer() != null) {
			return toDecode.getRocfTransferBuffer();
		}
		if(toDecode.getRocfStatusReportInvocation() != null) {
			return toDecode.getRocfStatusReportInvocation();
		}
		if(toDecode.getRocfBindReturn() != null) {
			return toDecode.getRocfBindReturn();
		}
		if(toDecode.getRocfBindInvocation() != null) {
			return toDecode.getRocfBindInvocation();
		}
		if(toDecode.getRocfGetParameterReturn() != null) {
			return toDecode.getRocfGetParameterReturn();
		}
		if(toDecode.getRocfScheduleStatusReportReturn() != null) {
			return toDecode.getRocfScheduleStatusReportReturn();
		}
		if(toDecode.getRocfStartReturn() != null) {
			return toDecode.getRocfStartReturn();
		}
		if(toDecode.getRocfStopReturn() != null) {
			return toDecode.getRocfStopReturn();
		}
		if(toDecode.getRocfUnbindInvocation() != null) {
			return toDecode.getRocfUnbindInvocation();
		}
		if(toDecode.getRocfUnbindReturn() != null) {
			return toDecode.getRocfUnbindReturn();
		}
		throw new DecodingException("Cannot unwrap data from " + toDecode + ": no field set");
	}
}
