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

package eu.dariolucia.ccsds.sle.utl.network.tml;

/**
 * This enumeration represents the possible reasons for the disconnection of the TML channel. The SLE User Test Library
 * does not signal only PEER- and PROTOCOL-ABORTs and uses custom error codes, not strictly following the list of error
 * codes as defined by CCSDS 913.1-B-2.
 */
public enum TmlDisconnectionReasonEnum {
	/**
	 * Heartbeat expired on reception
	 */
	RX_HBT_EXPIRED,
	/**
	 * TML context message expected, but not received
	 */
	PROTOCOL_ERROR,
	/**
	 * Abort request received by the SLE User Test Library, i.e. abort() called on the TML channel
	 */
	PEER_ABORT,
	/**
	 * Unknown TML message type detected when processing a TML message (ref. CCSDS 913.1-B-2 3.3.2.2.2, point a)
	 */
	UNKNOWN_TYPE_ID,
	/**
	 * Remote disconnection detected
	 */
	REMOTE_DISCONNECT,
	/**
	 * Local disconnection detected, after calling disconnect() on the TML channel
	 */
	LOCAL_DISCONNECT,
	/**
	 * Error detected when sending the heartbeat
	 */
	HBT_TX_SEND_ERROR,
	/**
	 * Remote peer abort detected, urgent byte received from the remote peer and connection closed
	 */
    REMOTE_PEER_ABORT
}
