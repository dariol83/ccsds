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
 * This enumeration is used to specify the service instance initiator role:
 * - USER: the user is meant to connect to the remote peer, send the TML context message and start the binding process
 * - PROVIDER: the provider is meant to connect to the local peer, send the TML context message and start the binding process
 * - USER OR PROVIDER: the user can connect to the provider or the other way round.
 */
public enum InitiatorRoleEnum {
	USER,
	PROVIDER,
	USER_OR_PROVIDER
}
