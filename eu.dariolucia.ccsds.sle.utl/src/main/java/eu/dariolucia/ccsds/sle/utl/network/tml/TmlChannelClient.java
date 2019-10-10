/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.network.tml;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 * This class implements all the required TML features as specified by CCSDS 913.1-B-2:
 * <ul>
 * <li>TML context, pdu and heartbeat message</li>
 * <li>TX and RX heartbeat timers and dead factor</li>
 * <li>Client mode</li>
 * </ul>
 * Given the nature of the defined protocol, interactions with instances of this class have an asynchronous nature:
 * a callback object must be provided in the constructor and the relevant methods will be called, depending on the
 * type of data received from the underlying transport layer.
 *
 * The class includes support for rate calculation, but relies on an external polling mechanism.
 */
public class TmlChannelClient extends TmlChannel {

	private final String host;
	private final int port;

	/**
	 * Create a client TML channel, which will connect to the TML server upon invoking connect().
	 *
	 * @param host the remote host to connect to
	 * @param port the remote TCP port to connect to
	 * @param heartbeatTimer the heartbeat interval to use (set in the TML context message)
	 * @param deadFactor the dead factor to use (set in the TML context message)
	 * @param observer the callback interface
	 * @param txBuffer the TCP transmission buffer in bytes (less or equal 0 means 'do not set')
	 * @param rxBuffer the TCP reception buffer in bytes (less or equal 0 means 'do not set')
	 */
	TmlChannelClient(String host, int port, int heartbeatTimer, int deadFactor, ITmlChannelObserver observer, int txBuffer, int rxBuffer) {
		super(observer, txBuffer, rxBuffer);
		if(host == null) {
			throw new NullPointerException("Host cannot be null");
		}
		this.host = host;
		this.port = port;
		this.heartbeatTimer.set(heartbeatTimer);
		this.deadFactor.set(deadFactor);
	}

	@Override
	protected void performConnect() throws TmlChannelException {
		this.lock.lock();
		try {
			if (this.sock != null) {
				throw new TmlChannelException("Already connected");
			}
			try {
				// reset stats
				this.statsCounter.reset();
				// connect to endpoint (ref. CCSDS 913.1-B-2 3.3.4.1.1.1)
				connectToRemoteHost();
				// send CTX message (ref. CCSDS 913.1-B-2 3.3.4.1.1.2)
				sendTmlCtxMessage();
				// start HBT timers, if needed (ref. CCSDS 913.1-B-2 3.3.4.1.1.2)
				startHbtTimers();
				// start read thread
				startReadingThread("TML Channel Reader - " + this.host + ":" + this.port);
				// notify
				notifyChannelConnected();
				// return
			} catch (UnknownHostException e) {
				cleanup();
				throw new TmlChannelException("Unknown host: " + this.host, e);
			} catch (IOException e) {
				cleanup();
				throw new TmlChannelException("Cannot open connection to host: " + this.host + ":" + this.port, e);
			}
		} finally {
			this.lock.unlock();
		}
	}

	private void connectToRemoteHost() throws IOException {
		this.sock = new Socket(this.host, this.port);
		if(this.rxBuffer > 0) {
			this.sock.setReceiveBufferSize(this.rxBuffer);
		}
		if(this.txBuffer > 0) {
			this.sock.setSendBufferSize(this.txBuffer);
		}
		this.rxStream = this.sock.getInputStream();
		this.txStream = this.sock.getOutputStream();
	}

	@Override
	protected boolean performPreConnectionOperations() {
		// Nothing to be done, all fine
		return true;
	}

	@Override
	protected boolean isTmlContextMsgExpected() {
		// Client mode, the client sends the TML context message, so everything is all right.
		return false;
	}

	private void sendTmlCtxMessage() throws TmlChannelException {
		OutputStream os = getTxStream();
		if(os == null) {
			throw new TmlChannelException("Channel " + toString() + " not connected");
		}
		try {
			byte[] toSend = ByteBuffer.allocate(CTX_MESSAGE_HDR.length + 4).put(CTX_MESSAGE_HDR).putShort((short) this.heartbeatTimer.get()).putShort((short) this.deadFactor.get()).array();
			os.write(toSend);
			this.statsCounter.addOut(toSend.length);
		} catch (IOException e) {
			throw new TmlChannelException("Exception while writing on channel " + toString(), e);
		}		
	}

	@Override
	protected boolean checkIfAlreadyDisconnected() {
		return this.sock == null;
	}

	@Override
	protected void performPostDisconnectionOperations() {
		// Nothing to be done for client TML channels
	}

	@Override
	public String toString() {
		return "TmlChannelClient[" + this.host + ":" + this.port + "]@" + hashCode();
	}
}
