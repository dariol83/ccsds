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

package eu.dariolucia.ccsds.sle.utl.si;

/**
 * Enumeration that lists all the possible diagnostics values for a negative BIND return.
 */
public enum BindDiagnosticsEnum {
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.1: the value of the initiator-identifier parameter is not
	 * recognized by the responder (e.g., the value does not identify the authorized initiator
	 * of any service instance known to the responder)
	 */
	ACCESS_DENIED((byte)0),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.2: the value of the service-type parameter of the
	 * BIND invocation does not identify a service type supported by the responder
	 */
	SERVICE_TYPE_NOT_SUPPORTED((byte)1),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.3: the responder does not support the requested version, and
	 * the responder implementation does not permit version negotiation or does not support
	 * any version of the service lower than the one requested by the initiator
	 */
	VERSION_NOT_SUPPORTED((byte)2),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.4: the requested service instance is not defined in any
	 * agreed upon service package known to the responder
	 */
	NO_SUCH_SERVICE_INSTANCE((byte)3),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.5: the service instance is already bound via a different association
	 */
	ALREADY_BOUND((byte)4),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.6: the authorized initiator for the
	 * service instance identified by the service-instance-identifier parameter
	 * does not match the initiator identified by the initiator-identifier parameter
	 * of the BIND invocation
	 */
	SI_NOT_ACCESSIBLE_TO_THIS_INITIATOR((byte)5),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.7: the value of the service-type parameter of the
	 * BIND invocation is not the expected one, or the value of the service-type
	 * parameter does not match the service type of the service instance identified by the
	 * service-instance-identifier parameter
	 */
	INCONSISTENT_SERVICE_TYPE((byte)6),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.8: the BIND operation was invoked outside the service instance
	 * provision period of the service instance identified by the service-instance identifier parameter
	 */
	INVALID_TIME((byte)7),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.9: the responder has been taken out of service for an indefinite period
	 * by management action
	 */
	OUT_OF_SERVICE((byte)8),
	/**
	 * CCSDS 911.1-B-4 3.2.2.11.10: the reason for the negative result will have to be found by other means
	 */
	OTHER_REASON((byte)127);
	
	private final byte code;

	BindDiagnosticsEnum(byte code) {
		this.code = code;
	}
	
	public byte getCode() {
		return this.code;
	}
	
	public static BindDiagnosticsEnum getBindDiagnostics(int intValue) {
		for(BindDiagnosticsEnum b : BindDiagnosticsEnum.values()) {
			if(b.getCode() == intValue) {
				return b;
			}
		}
		throw new IllegalArgumentException("Cannot decode value: " + intValue);
	}

}
