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

package eu.dariolucia.ccsds.sle.utl.si.cltu;

public enum CltuParameterEnum {
	ACQUISITION_SEQUENCE_LENGTH(201),
	BIT_LOCK_REQUIRED(3),
	CLCW_GLOBAL_VCID(202),
	CLCW_PHYSICAL_CHANNEL(203),
	DELIVERY_MODE(6),
	EXPECTED_SLDU_IDENTIFICATION(10),
	EXPECTED_EVENT_INVOCATION_IDENTIFICATION(9),
	MAXIMUM_SLDU_LENGTH(21),
	MINIMUM_DELAY_TIME(204),
	MIN_REPORTING_CYCLE(301),
	MODULATION_FREQUENCY(22),
	MODULATION_INDEX(23),
	NOTIFICATION_MODE(205),
	PLOP1_IDLE_SEQUENCE_LENGTH(206),
	PLOP_IN_EFFECT(25),
	PROTOCOL_ABORT_MODE(207),
	REPORTING_CYCLE(26),
	RETURN_TIMEOUT_PERIOD(29),
	RF_AVAILABLE_REQUIRED(31),
	SUBCARRIER_TO_BITRATE_RATIO(34);
	
	private final int code;
	
	CltuParameterEnum(int code) {
		this.code = code;
	}
	
	public int getCode() {
		return this.code;
	}
}
