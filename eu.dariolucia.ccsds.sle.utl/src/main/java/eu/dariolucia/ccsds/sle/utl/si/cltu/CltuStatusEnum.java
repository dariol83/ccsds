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

public enum CltuStatusEnum {
	RADIATED(0),
	EXPIRED(1),
	INTERRUPTED(2),
	PRODUCTION_STARTED(4),
	PRODUCTION_NOT_STARTED(5);

	final int code;

	CltuStatusEnum(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}
}
