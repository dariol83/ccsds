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

public enum PeerAbortReasonEnum {
	ACCESS_DENIED((byte)0), 
	UNEXPECTED_RESPONDER_ID((byte)1), 
	OPERATIONAL_REQUIREMENTS((byte)2), 
	PROTOCOL_ERROR((byte)3), 
	COMMUNICATIONS_FAILURE((byte)4), 
	ENCODING_ERROR((byte)5), 
	RETURN_TIMEOUT((byte)6), 
	END_OF_SERVICE_PROVISION_PERIOD((byte)7), 
	UNSOLICITED_INVOKE_ID((byte)8), 
	OTHER_REASON((byte)127),
	UNKNOWN((byte)-1);

	private final byte code;

	PeerAbortReasonEnum(byte code) {
		this.code = code;
	}
	
	public byte getCode() {
		return this.code;
	}

	public static PeerAbortReasonEnum fromCode(byte code) {
		for(PeerAbortReasonEnum en : PeerAbortReasonEnum.values()) {
			if(en.getCode() == code) {
				return  en;
			}
		}
		return UNKNOWN;
	}
}
