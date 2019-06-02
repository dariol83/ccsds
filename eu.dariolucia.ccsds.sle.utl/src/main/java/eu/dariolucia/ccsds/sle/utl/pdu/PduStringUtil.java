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
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types.SleUnbindReturn;

import javax.xml.bind.DatatypeConverter;
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
		register(SleUnbindReturn.class, this::toStringUnbindReturn);
	}

	@SuppressWarnings("unchecked")
	private <T> void register(Class<T> clazz, Function<T, String> fun) {
		this.stringRenderer.put(clazz, (Function<Object, String>) fun);
	}

	private String toStringBindInvoke(SleBindInvocation pdu) {
		return "Bind invocation from " + pdu.getInitiatorIdentifier().toString() + " to port "
				+ pdu.getResponderPortIdentifier().toString() + ": version " + pdu.getVersionNumber().intValue();
	}

	private String toStringBindReturn(SleBindReturn pdu) {
		return "Bind return from " + pdu.getResponderIdentifier().toString() + " with result "
				+ (pdu.getResult().getPositive() != null
						? "<positive>: version number is " + pdu.getResult().getPositive().intValue()
						: "<negative> " + pdu.getResult().getNegative().intValue());
	}
	
	private String toStringUnbindInvoke(SleUnbindInvocation pdu) {
		return "Unbind invocation with reason "
				+ pdu.getUnbindReason().intValue();
	}

	private String toStringUnbindReturn(SleUnbindReturn pdu) {
		return "Unbind return";
	}

	public String getPduDetails(Object pdu) {
		return this.stringRenderer.getOrDefault(pdu.getClass(), (o) -> "No additional information").apply(pdu);
	}

	public String toHexDump(byte[] encodedPdu) {
		return DatatypeConverter.printHexBinary(encodedPdu);
	}

}
