/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.tmtc.datalink.channel;

/**
 * This enumeration lists the different access mode for a virtual channel, as supported by this library.
 */
public enum VirtualChannelAccessMode {
	/**
	 * The virtual channel delivers space packets
	 */
	Packet,
	/**
	 * The virtual channel delivers bitstream data (AOS only)
	 */
	Bitstream,
	/**
	 * The virtual channel delivers raw data (direct access, or VCA)
	 */
	Data
}
