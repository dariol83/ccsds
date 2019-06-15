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

package eu.dariolucia.ccsds.sle.utl.pdu;

import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleBindReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuStartReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types.Time;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafStartReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfStartReturn;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfStartInvocation;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfStartReturn;
import eu.dariolucia.ccsds.sle.utl.si.UnbindReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfUpdateModeEnum;

import javax.xml.bind.DatatypeConverter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * This class is meant to collect printer functions for all the SLE PDUs.
 */
// TODO add support for all operations
public class PduStringUtil {

	private static final PduStringUtil INSTANCE = new PduStringUtil();

	public static PduStringUtil instance() {
		return INSTANCE;
	}

	private final Map<Class<?>, Function<Object, String>> stringRenderer;

	private PduStringUtil() {
		this.stringRenderer = new HashMap<>();
		register(SleBindInvocation.class, this::toStringBindInvoke);
		register(SleBindReturn.class, this::toStringBindReturn);
		register(SleUnbindInvocation.class, this::toStringUnbindInvoke);

		register(RafStartInvocation.class, this::toStringRafStartInvoke);
		register(RcfStartInvocation.class, this::toStringRcfStartInvoke);
		register(RocfStartInvocation.class, this::toStringRocfStartInvoke);
		register(CltuStartInvocation.class, this::toStringCltuStartInvoke);

		register(RafStartReturn.class, this::toStringRafStartReturn);
		register(RcfStartReturn.class, this::toStringRcfStartReturn);
		register(RocfStartReturn.class, this::toStringRocfStartReturn);
		register(CltuStartReturn.class, this::toStringCltuStartReturn);

	}

	private String toStringRcfStartReturn(RcfStartReturn t) {
		return "Start return "
				+ (t.getResult().getPositiveResult() != null
				? "<positive>" : "<negative>: diagnostics code: common "
				+ t.getResult().getNegativeResult().getCommon().intValue()
				+ ", specific: " + t.getResult().getNegativeResult().getSpecific().intValue());
	}

	private String toStringRocfStartReturn(RocfStartReturn t) {
		return "Start return "
				+ (t.getResult().getPositiveResult() != null
				? "<positive>" : "<negative>: diagnostics code: common "
				+ t.getResult().getNegativeResult().getCommon().intValue()
				+ ", specific: " + t.getResult().getNegativeResult().getSpecific().intValue());
	}

	private String toStringRafStartReturn(RafStartReturn t) {
		return "Start return "
				+ (t.getResult().getPositiveResult() != null
				? "<positive>" : "<negative>: diagnostics code: common "
				+ t.getResult().getNegativeResult().getCommon().intValue()
				+ ", specific: " + t.getResult().getNegativeResult().getSpecific().intValue());
	}

	private String toStringCltuStartReturn(CltuStartReturn t) {
		return "Start return "
				+ (t.getResult().getPositiveResult() != null
				? "<positive>" : "<negative>: diagnostics code: common "
				+ t.getResult().getNegativeResult().getCommon().intValue()
				+ ", specific: " + t.getResult().getNegativeResult().getSpecific().intValue());
	}

	private String toStringRcfStartInvoke(RcfStartInvocation t) {
		return "Start from "
				+ (t.getStartTime().getUndefined() != null ? "VOID" : toString(t.getStartTime().getKnown()))
				+ " to "
				+ (t.getStopTime().getUndefined() != null ? "VOID" : toString(t.getStopTime().getKnown()))
				+ " with"
				+ " GVCID " + t.getRequestedGvcId().getSpacecraftId().intValue() + ", " + t.getRequestedGvcId().getVersionNumber().intValue()
				+ ", " + (t.getRequestedGvcId().getVcId().getMasterChannel() != null ? "*" : t.getRequestedGvcId().getVcId().getVirtualChannel().intValue());
	}

	private String toStringRafStartInvoke(RafStartInvocation t) {
		return "Start from "
				+ (t.getStartTime().getUndefined() != null ? "VOID" : toString(t.getStartTime().getKnown()))
				+ " to "
				+ (t.getStopTime().getUndefined() != null ? "VOID" : toString(t.getStopTime().getKnown()))
				+ " with"
				+ " quality " + RafRequestedFrameQualityEnum.fromCode(t.getRequestedFrameQuality().intValue());
	}

	private String toStringRocfStartInvoke(RocfStartInvocation t) {
		return "Start from "
				+ (t.getStartTime().getUndefined() != null ? "VOID" : toString(t.getStartTime().getKnown()))
				+ " to "
				+ (t.getStopTime().getUndefined() != null ? "VOID" : toString(t.getStopTime().getKnown()))
				+ " with"
				+ " GVCID " + t.getRequestedGvcId().getSpacecraftId().intValue() + ", " + t.getRequestedGvcId().getVersionNumber().intValue()
				+ ", " + (t.getRequestedGvcId().getVcId().getMasterChannel() != null ? "*" : t.getRequestedGvcId().getVcId().getVirtualChannel().intValue())
				+ " with update mode "
				+ RocfUpdateModeEnum.values()[t.getUpdateMode().intValue()];
	}

	private String toStringCltuStartInvoke(CltuStartInvocation t) {
		return "Start with first CLTU identification "
				+ (t.getFirstCltuIdentification().intValue());
	}

	private String toStringBindInvoke(SleBindInvocation pdu) {
		return "Bind invocation from " + pdu.getInitiatorIdentifier().toString() + " to port "
				+ pdu.getResponderPortIdentifier().toString() + ": version " + pdu.getVersionNumber().intValue();
	}

	private String toStringBindReturn(SleBindReturn pdu) {
		return "Bind return from " + pdu.getResponderIdentifier().toString() + " with result "
				+ (pdu.getResult().getPositive() != null
						? "<positive>: version number is " + pdu.getResult().getPositive().intValue()
						: "<negative>: diagnostics code " + pdu.getResult().getNegative().intValue());
	}
	
	private String toStringUnbindInvoke(SleUnbindInvocation pdu) {
		return "Unbind invocation with reason "
				+ UnbindReasonEnum.fromCode((byte) pdu.getUnbindReason().intValue());
	}

	public String toString(Time t) {
		if(t.getCcsdsFormat() != null) {
			long[] tAsLong = PduFactoryUtil.buildTimeMillis(t.getCcsdsFormat().value);
			return new Date(tAsLong[0]).toString();
		} else if(t.getCcsdsPicoFormat() != null) {
			long[] tAsLong = PduFactoryUtil.buildTimeMillisPico(t.getCcsdsFormat().value);
			return new Date(tAsLong[0]).toString() + " (pico)";
		} else {
			return "<time format unknown>";
		}
	}

	public String getPduDetails(Object pdu) {
		return this.stringRenderer.getOrDefault(pdu.getClass(), o -> "No additional information").apply(pdu);
	}

	@SuppressWarnings("unchecked")
	private <T> void register(Class<T> clazz, Function<T, String> fun) {
		this.stringRenderer.put(clazz, (Function<Object, String>) fun);
	}

	public String toHexDump(byte[] encodedPdu) {
		return DatatypeConverter.printHexBinary(encodedPdu);
	}

}
