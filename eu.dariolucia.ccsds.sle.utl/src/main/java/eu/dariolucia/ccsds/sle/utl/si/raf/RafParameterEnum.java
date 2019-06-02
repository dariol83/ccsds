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

package eu.dariolucia.ccsds.sle.utl.si.raf;

public enum RafParameterEnum {
	BUFFER_SIZE(4),
	DELIVERY_MODE(6),
	LATENCY_LIMIT(15),
	MIN_REPORTING_CYCLE(301),
	PERMITTED_FRAME_QUALITY(302),
	REPORTING_CYCLE(26),
	REQUESTED_FRAME_QUALITY(27),
	RETURN_TIMEOUT_PERIOD(29);
	
	private final int code;
	
	RafParameterEnum(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return this.code;
	}
}
