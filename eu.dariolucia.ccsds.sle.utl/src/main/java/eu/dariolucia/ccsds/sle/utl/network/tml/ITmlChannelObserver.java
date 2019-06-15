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

import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;

/**
 * TML channel callback interface: it is used to notify an observer about the status of the channel and to provide
 * received SLE operations.
 */
public interface ITmlChannelObserver {

	/**
	 * This method is called by the TML channel provided as method argument and indicates that the TML channel is
	 * 'successfully' connected: if the TML channel is in client mode, this method is called only after the sending
	 * of the TML context message and the start of the receiving thread. If the TML channel is in server mode, this
	 * method is called only when a correct TML context message is received and accepted.
	 *
	 * @param channel the TML channel announcing the connection state
	 */
	void onChannelConnected(TmlChannel channel);

	/**
	 * This method is called by the TML channel provided as method argument and indicates that the TML channel is
	 * now disconnected.
	 *
	 * @param channel the TML channel announcing the disconnection
	 * @param reason the reason for the disconnection
	 * @param peerAbortReason if reason is PEER_ABORT or REMOTE_PEER_ABORT, the peer abort reason is provided; otherwise null
	 */
	void onChannelDisconnected(TmlChannel channel, TmlDisconnectionReasonEnum reason, PeerAbortReasonEnum peerAbortReason);

	/**
	 * This method is called by the TML channel provided as method argument and it is used to deliver a TML PDU (i.e.
	 * a BER-encoded SLE operation).
	 *
	 * @param channel the TML channel announcing the reception of an SLE operation
	 * @param pdu the BER-encoded SLE operation
	 */
	void onPduReceived(TmlChannel channel, byte[] pdu);
}
