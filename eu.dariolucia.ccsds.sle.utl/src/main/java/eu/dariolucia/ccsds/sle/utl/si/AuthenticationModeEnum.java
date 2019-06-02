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
 * The authentication mode as defined by CCSDS 913.1-B-2, 3.1.2.
 */
public enum AuthenticationModeEnum {
	/**
	 * Authentication not used, credentials not generated and not checked for any operation.
	 */
	NONE,
	/**
	 * Authentication used for BIND operations only.
	 */
	BIND,
	/**
	 * Credentials generated and checked for all operations.
	 */
	ALL
}
