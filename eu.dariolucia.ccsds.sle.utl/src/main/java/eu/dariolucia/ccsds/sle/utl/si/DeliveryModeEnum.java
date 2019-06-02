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
 * Enumeration used to describe the delivery mode configured for each service instance. Forward service instances
 * supported by the SLE User Test Library (such as CLTU) enforce the delivery mode to FWD_ONLINE. Return service
 * instances support timely and complete online, as well as offline.
 */
public enum DeliveryModeEnum {
	TIMELY_ONLINE,
	COMPLETE_ONLINE,
	OFFLINE,
	FWD_ONLINE,
	FWD_OFFLINE;
}
