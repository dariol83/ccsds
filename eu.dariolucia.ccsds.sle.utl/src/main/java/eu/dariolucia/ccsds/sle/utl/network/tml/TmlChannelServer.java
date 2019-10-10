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
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements all the required TML features as specified by CCSDS 913.1-B-2:
 * <ul>
 * <li>TML context, pdu and heartbeat message</li>
 * <li>TX and RX heartbeat timers and dead factor</li>
 * <li>Server mode</li>
 * </ul>
 * Given the nature of the defined protocol, interactions with instances of this class have an asynchronous nature:
 * a callback object must be provided in the constructor and the relevant methods will be called, depending on the
 * type of data received from the underlying transport layer.
 *
 * The class includes support for rate calculation, but relies on an external polling mechanism.
 */
public class TmlChannelServer extends TmlChannel{

	private static final Logger LOG = Logger.getLogger(TmlChannelServer.class.getName());

	private final int port;

	private ServerSocket serverSocket;

	/**
	 * Create a server TML channel, which will wait for the connection by a TML client, upon invoking connect().
	 *
	 * @param port the port used to bind the server socket
	 * @param observer the callback interface
	 * @param txBuffer the TCP transmission buffer in bytes (less or equal 0 means 'do not set')
	 * @param rxBuffer the TCP reception buffer in bytes (less or equal 0 means 'do not set')
	 */
	TmlChannelServer(int port, ITmlChannelObserver observer, int txBuffer, int rxBuffer) {
		super(observer, txBuffer, rxBuffer);
		this.port = port;
	}

	protected void performConnect() throws TmlChannelException {
		this.lock.lock();
		try {
			if (this.sock != null) {
				throw new TmlChannelException("Already connected/connection pending");
			}
			if (this.readingThread != null) {
				throw new TmlChannelException("Already waiting for a connection");
			}
			//
			try {
				this.serverSocket = new ServerSocket(this.port);
			} catch(IOException e) {
				throw new TmlChannelException("Cannot create server socket on port " + port, e);
			}
			if(this.rxBuffer > 0) {
				try {
					this.serverSocket.setReceiveBufferSize(this.rxBuffer);
				} catch (SocketException e) {
					throw new TmlChannelException("Cannot set RX buffer size " + this.rxBuffer + " on server socket on port " + port, e);
				}
			}
			// reset stats
			this.statsCounter.reset();
			// start read thread, it will initialise also the server socket
			startReadingThread("TML Channel Reader - *:" + this.port);
			// return
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	protected boolean performPreConnectionOperations() {
		// Wait for the connection (ref. CCSDS 913.1-B-2 3.3.4.2.1)
		if(this.serverSocket == null) {
			// If we are at this stage, it means that the channel was closed even before this thread started, so bye.
			if(LOG.isLoggable(Level.FINE)) {
				LOG.log(Level.FINE, String.format("Server socket on channel %s closed immediately after the startup", toString()));
			}
			return false;
		}

		try {
			if(LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, String.format("Server socket on channel %s about to be on accept", toString()));
			}
			this.sock = this.serverSocket.accept();
			if(LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, String.format("Server socket on channel %s exited from accept", toString()));
			}
			// Activate the reception of OOB inline (needed for peer abort detection)
			this.sock.setOOBInline(true);
			if(this.txBuffer > 0) {
				this.sock.setSendBufferSize(this.txBuffer);
			}
		} catch(IOException e) {
			if(!this.aboutToDisconnect) {
				// inform remote disconnection
				remoteDisconnectionDetected(e);
			}
			return false;
		}
		this.lock.lock();
		try {
			this.rxStream = this.sock.getInputStream();
			this.txStream = this.sock.getOutputStream();
			this.aboutToDisconnect = false;
			return true;
		} catch(IOException e) {
			if(!this.aboutToDisconnect) {
				// inform remote disconnection
				remoteDisconnectionDetected(e);
			}
			return false;
		} finally {
			this.lock.unlock();
		}
	}

	@Override
	protected boolean isTmlContextMsgExpected() {
		// Server mode, the TML context message must be received first.
		return true;
	}

	@Override
	protected boolean checkIfAlreadyDisconnected() {
		return this.sock == null && (this.serverSocket == null || this.serverSocket.isClosed());
	}

	@Override
	protected void performPostDisconnectionOperations() throws IOException {
		if(this.serverSocket != null) { // Server mode
			if(LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, String.format("Server socket on channel %s about to be closed", toString()));
			}
			this.serverSocket.close();
			if(LOG.isLoggable(Level.FINEST)) {
				LOG.log(Level.FINEST, String.format("Server socket on channel %s closed", toString()));
			}
		}
		this.serverSocket = null;
	}

	@Override
	public String toString() {
		return "TmlChannelServer[*:" + this.port + "]@" + hashCode();
	}
}
