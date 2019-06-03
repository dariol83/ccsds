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
import eu.dariolucia.ccsds.sle.utl.util.DataRateCalculator;
import eu.dariolucia.ccsds.sle.utl.util.DataRateSample;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements all the required TML features as specified by CCSDS 913.1-B-2:
 * - TML context, pdu and heartbeat message;
 * - TX and RX heartbeat timers and dead factor;
 * - Server and client modes.
 *
 * Given the nature of the defined protocol, interactions with instances of this class have an asynchronous nature:
 * a callback object must be provided in the constructor and the relevant methods will be called, depending on the
 * type of data received from the underlying transport layer.
 *
 * The class includes support for rate calculation, but relies on an external polling mechanism.
 */
public class TmlChannel {

	/**
	 * Static creation function to instantiate a client TML channel, i.e. a channel that can be used by the SLE User
	 * Test Library to connect to a remote SLE service instance and initiate a session.
	 *
	 * @param host the hostname to connect to
	 * @param port the TCP port to connect to
	 * @param heartbeatTimer the heartbeat interval to propose in the TML context message and use later on
	 * @param deadFactor the deadfactor to propose in the TML context message and use later on
	 * @param observer the callback interface
	 * @return the TML channel, ready to be connected
	 */
	public static TmlChannel createClientTmlChannel(String host, int port, int heartbeatTimer, int deadFactor, ITmlChannelObserver observer) {
		return new TmlChannel(host, port, heartbeatTimer, deadFactor, observer);
	}

	public static TmlChannel createClientTmlChannel(String host, int port, int heartbeatTimer, int deadFactor, ITmlChannelObserver observer, int txBuffer, int rxBuffer) {
		return new TmlChannel(host, port, heartbeatTimer, deadFactor, observer, txBuffer, rxBuffer);
	}

	/**
	 * Static creation function to instantiate a server TML channel, i.e. a channel that can be used by the SLE User
     * Test Library to wait for connections from a remote SLE service instance.
     *
	 * @param port the TCP port to open, to wait for incoming connections
	 * @param observer the callback interface
	 * @return the TML channel, ready to be put in listen mode
	 * @throws TmlChannelException in case of issues with the construction of the channel
	 */
	public static TmlChannel createServerTmlChannel(int port, ITmlChannelObserver observer) throws TmlChannelException {
		return new TmlChannel(port, observer);
	}

	public static TmlChannel createServerTmlChannel(int port, ITmlChannelObserver observer, int txBuffer, int rxBuffer) throws TmlChannelException {
		return new TmlChannel(port, observer, txBuffer, rxBuffer);
	}
	
	private static final Logger LOG = Logger.getLogger(TmlChannel.class.getName());
	
	private static final byte[] PDU_MESSAGE_HDR = new byte[] {0x01, 0x00, 0x00, 0x00}; 
	private static final byte[] HBT_MESSAGE = new byte[] {0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
	private static final byte[] CTX_MESSAGE_HDR = new byte[] {0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x53, 0x50, 0x31, 0x00, 0x00, 0x00, 0x01}; 
			
	private final String host;
	private final int port;

	private final AtomicInteger heartbeatTimer;
	private final AtomicInteger deadFactor;
	private final ITmlChannelObserver observer;
	
	private final Lock lock = new ReentrantLock();
	private final Timer hbtScheduler = new Timer(true);
	
	private final boolean serverMode;
	
	private final ServerSocket serverSocket;

	private final int txBuffer;
	private final int rxBuffer;

	private Socket sock;
	private InputStream rxStream;
	private OutputStream txStream;
	private Thread readingThread;
	
	private TimerTask hbtRxTimer;
	private TimerTask hbtTxTimer;
	
	private volatile boolean aboutToDisconnect = false;
	private volatile boolean running = false;
	
	private final DataRateCalculator statsCounter = new DataRateCalculator();

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
	private TmlChannel(String host, int port, int heartbeatTimer, int deadFactor, ITmlChannelObserver observer, int txBuffer, int rxBuffer) {
		if(host == null) {
			throw new NullPointerException("Host cannot be null");
		}
		if(observer == null) {
			throw new NullPointerException("Channel observer cannot be null");
		}
		this.host = host;
		this.port = port;
		this.heartbeatTimer = new AtomicInteger(heartbeatTimer);
		this.deadFactor = new AtomicInteger(deadFactor);
		this.observer = observer;
		this.serverMode = false;
		this.serverSocket = null;
		this.txBuffer = txBuffer;
		this.rxBuffer = rxBuffer;
	}

	/**
	 * Create a client TML channel, which will connect to the TML server upon invoking connect().
	 *
	 * @param host the remote host to connect to
	 * @param port the remote TCP port to connect to
	 * @param heartbeatTimer the heartbeat interval to use (set in the TML context message)
	 * @param deadFactor the dead factor to use (set in the TML context message)
	 * @param observer the callback interface
	 */
	private TmlChannel(String host, int port, int heartbeatTimer, int deadFactor, ITmlChannelObserver observer) {
		this(host, port, heartbeatTimer, deadFactor, observer, 0, 0);
	}

	/**
	 * Create a server TML channel, which will wait for the connection by a TML client, upon invoking connect().
	 *
	 * @param port the port used to bind the server socket
	 * @param observer the callback interface
	 * @param txBuffer the TCP transmission buffer in bytes (less or equal 0 means 'do not set')
	 * @param rxBuffer the TCP reception buffer in bytes (less or equal 0 means 'do not set')
	 * @throws TmlChannelException if the server socket cannot be bound to the specified port
	 */
	private TmlChannel(int port, ITmlChannelObserver observer, int txBuffer, int rxBuffer) throws TmlChannelException {
		if(observer == null) {
			throw new NullPointerException("Channel observer cannot be null");
		}
		this.host = "";
		this.port = port;
		this.heartbeatTimer = new AtomicInteger(60); // Hardcoded now, it will be overwritten
		this.deadFactor = new AtomicInteger(4); // Hardcoded now, it will be overwritten
		this.observer = observer;
		this.serverMode = true;
		try {
			this.serverSocket = new ServerSocket(this.port);
		} catch(IOException e) {
			throw new TmlChannelException("Cannot create server socket on port " + port, e);
		}
		this.txBuffer = txBuffer;
		this.rxBuffer = rxBuffer;
		if(this.rxBuffer > 0) {
			try {
				this.serverSocket.setReceiveBufferSize(this.rxBuffer);
			} catch (SocketException e) {
				throw new TmlChannelException("Cannot set RX buffer size " + this.rxBuffer + " on server socket on port " + port, e);
			}
		}
	}

	/**
	 * Create a server TML channel, which will wait for the connection by a TML client, upon invoking connect().
	 * 
	 * @param port the port used to bind the server socket
	 * @param observer the callback interface
	 * @throws TmlChannelException if the server socket cannot be bound to the specified port
	 */
	private TmlChannel(int port, ITmlChannelObserver observer) throws TmlChannelException {
		this(port, observer, 0, 0);
	}

	/**
	 * Depending on the channel mode, invoking this method will cause either the connection attempt to the provider, or
	 * the opening of a server port ready to accept incoming connections. This method returns upon establishment of
	 * the connection to the provider, or as soon as the server port is bound and listening.
	 *
	 * @throws TmlChannelException in case of error when establishing the connection
	 */
	public void connect() throws TmlChannelException {
		if(!serverMode) {
			doClientConnect();
		} else {
			doServerConnect();
		}
	}

	private void doServerConnect() throws TmlChannelException {
		this.lock.lock();
		try {
			if (this.sock != null) {
				throw new TmlChannelException("Already connected/connection pending");
			}
			if (this.readingThread != null) {
				throw new TmlChannelException("Already waiting for a connection");
			}
			// reset stats
			this.statsCounter.reset();
			// start read thread, it will initialise also the server socket
			startReadingThread();
			// return
		} finally {
			this.lock.unlock();
		}
	}

	private void doClientConnect() throws TmlChannelException {
		this.lock.lock();
		try {
			if (this.sock != null) {
				throw new TmlChannelException("Already connected");
			}
			try {
				// reset stats
				this.statsCounter.reset();
				// connect to endpoint (ref. CCSDS 913.1-B-2 3.3.4.1.1.1)
				connectEndpoint();
				// send CTX message (ref. CCSDS 913.1-B-2 3.3.4.1.1.2)
				sendCtxMessage();
				// start HBT timers, if needed (ref. CCSDS 913.1-B-2 3.3.4.1.1.2)
				startHbtTimers();
				// start read thread
				startReadingThread();
				// notify
				try {
					this.observer.onChannelConnected(this);
				} catch (Exception e) {
					LOG.log(Level.WARNING, "Notification of connection on channel " + toString() + " threw exception on observer", e);
				}
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

	/**
	 * This method is called by the SLE User Test Library service instance when a positive unbind return is received.
	 * It avoids detecting subsequent disconnection as a critical error.
 	 */
	public void aboutToDisconnect() {
		this.aboutToDisconnect = true;
	}
	
	private void connectEndpoint() throws IOException {
		this.sock = new Socket();
		if(this.rxBuffer > 0) {
			this.sock.setReceiveBufferSize(this.rxBuffer);
		}
		if(this.txBuffer > 0) {
			this.sock.setSendBufferSize(this.txBuffer);
		}
		this.rxStream = this.sock.getInputStream();
		this.txStream = this.sock.getOutputStream();
		this.aboutToDisconnect = false;
	}

	@SuppressWarnings("resource")
	private void startReadingThread() {
		this.running = true;
		this.readingThread = new Thread(() -> {
			// If server mode, wait for the connection (ref. CCSDS 913.1-B-2 3.3.4.2.1)
			if(this.serverMode) {
			    if(this.serverSocket == null) {
			        throw new IllegalAccessError("Server socket cannot be null at this stage, software bug");
                }
				boolean connectionOk = false;
				try {
					this.sock = this.serverSocket.accept();
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
					return;
				}
				this.lock.lock();
				try {
					this.rxStream = this.sock.getInputStream();
					this.txStream = this.sock.getOutputStream();
					this.aboutToDisconnect = false;
					connectionOk = true;
				} catch(IOException e) {
					if(!this.aboutToDisconnect) {
						// inform remote disconnection
						remoteDisconnectionDetected(e);
					}
				} finally {
					this.lock.unlock();
				}
				
				if(!connectionOk) {
					return;
				}
			}
			
			// If in server mode, the TML context message must be received first.
			// If in client mode, the client sends the TML context message, so everything is all right.
			boolean tmlContextMsgReceived = !this.serverMode;
			
			byte[] headerBuffer = new byte[8];
			InputStream is = getRxStream();
			while(is != null && this.running) {
				// Read TML message or HB
				int read = 0;
				try {
				    // Read at least 8 octets (ref. CCSDS 913.1-B-2 3.3.4.2.3.2)
					while(read < 8 && this.running) {
						read += is.read(headerBuffer, read, 8 - read);
					}
					this.statsCounter.addIn(read);
				} catch (IOException e) {
					if(!this.aboutToDisconnect) {
						// Inform remote disconnection: at this stage, the disconnection could be because a peer abort
						// or other reasons. If it is because of a peer abort, headerBuffer should contain a single
						// byte. If this is the case, then the peer abort can be decoded.
						if(read == 1) {
							remotePeerAbortDetected(e, headerBuffer[0]);
						} else {
							remoteDisconnectionDetected(e);
						}
					}
					return;
				}
				if(!this.running) {
					LOG.warning("Reading thread on channel " + toString() + " stopped");
					return;
				}

				if(!tmlContextMsgReceived) {
					// If TML context message is not received and the message is a TML context message, process it
                    // (ref. CCSDS 913.1-B-2 3.3.4.2.3.2.2)
					if(isTmlContextMsg(headerBuffer)) {
						// Read other 12 bytes (protocol ID, spare + version, HBT, DF)
						byte[] msg = new byte[12];
						int read2 = 0;
						try {
							while(read2 < 12 && this.running) {
								read2 += is.read(msg, read2, 12 - read2);
							}
							this.statsCounter.addIn(read2);
						} catch (IOException e) {
							if(!this.aboutToDisconnect) {
								// inform remote disconnection
								remoteDisconnectionDetected(e);
							}
							return;
						}
						ByteBuffer reader = ByteBuffer.wrap(msg, 8, 4);
						this.heartbeatTimer.set(reader.getShort());
						this.deadFactor.set(reader.getShort());
						LOG.info("HB interval set to " + this.heartbeatTimer.get() + ", dead factor set to " + this.deadFactor);
						// start HBT timers, if needed
						startHbtTimers();
						// notify
						try {
							this.observer.onChannelConnected(this);
						} catch (Exception e) {
							LOG.log(Level.WARNING, "Notification of connection on channel " + toString() + " threw exception on observer", e);
						}
						tmlContextMsgReceived = true;
					} else {
						LOG.warning("Expecting TML context message on channel " + toString() + " but received " + Arrays.toString(headerBuffer));
						protocolErrorDetected(headerBuffer, TmlDisconnectionReasonEnum.PROTOCOL_ERROR);
						return;
					}
				} else if(isTmlHbt(headerBuffer)) {
					// If TML HBT, restart Rx timer
					restartHbtRxTimer();
				} else if(isTmlPdu(headerBuffer)) {
					// Restart Rx timer
					restartHbtRxTimer();
					// The last 4 bytes of the header buffer are the length
					ByteBuffer reader = ByteBuffer.wrap(headerBuffer, 4, 4);
					int length = reader.getInt();
					byte[] pdu = new byte[length];
					int read2 = 0;
					try {
						while(read2 < length && this.running) {
							read2 += is.read(pdu, read2, length - read2);
						}
						this.statsCounter.addIn(read2);
					} catch (IOException e) {
						if(!this.aboutToDisconnect) {
							// inform remote disconnection
							remoteDisconnectionDetected(e);
						}
						return;
					}
					tmlPduReceived(pdu);
				} else {
				    // Ref. CCSDS 913.1-B-2 3.3.2.2.2, point a)
					protocolErrorDetected(headerBuffer, TmlDisconnectionReasonEnum.UNKNOWN_TYPE_ID);
					return;
				}
				is = getRxStream();
			}
			LOG.warning("Reading thread on channel " + toString() + " has null inputstream, thread returns");
		});
		this.readingThread.setName("TML Channel Reader - " + this.host + ":" + this.port);
		this.readingThread.start();
	}

	private boolean isTmlContextMsg(byte[] headerBuffer) {
		return headerBuffer != null && headerBuffer.length > 0 && headerBuffer[0] == 0x02;
	}

	private void tmlPduReceived(byte[] pdu) {
		try {
			this.observer.onPduReceived(this, pdu);
		} catch(Exception e) {
			LOG.log(Level.SEVERE, "Exception while forwarding PDU from channel " + toString() + " to observer", e);
		}
	}

	private void protocolErrorDetected(byte[] headerBuffer, TmlDisconnectionReasonEnum reason) {
		LOG.log(Level.SEVERE, "Protocol error detected on channel " + toString() + " with reason " + reason + ", header=" + Arrays.toString(headerBuffer));
		this.lock.lock();
		try {
			// stop HBT timers, if needed
			stopHbtTimers();
			// disconnect from endpoint
			disconnectEndpoint(reason);
			// cleanup
			cleanup();
			// return
			LOG.fine("Channel disconnected: " + toString() + " via protocolErrorDetected()");
		} finally {
			this.lock.unlock();
		}
	}
	
	private boolean isTmlPdu(byte[] headerBuffer) {
		return headerBuffer != null && headerBuffer.length > 0 && headerBuffer[0] == 0x01;
	}

	private void restartHbtRxTimer() {
		this.lock.lock();
		try {
			if(this.hbtRxTimer != null) {
				this.hbtRxTimer.cancel();
				this.hbtRxTimer = null;
			}
			if(this.heartbeatTimer.get() > 0) {
				this.hbtRxTimer = new TimerTask() {
					@Override
					public void run() {
						hbtRxTimerExpired();
					}
				};
				this.hbtScheduler.schedule(this.hbtRxTimer, this.heartbeatTimer.get() * 1000 * this.deadFactor.get());
			}
		} finally {
			this.lock.unlock();
		}
	}
	
	private void hbtRxTimerExpired() {
		LOG.log(Level.SEVERE, "HBT Rx expired detected on channel " + toString());
		this.lock.lock();
		try {
			// stop read thread
			stopReadingThread();
			// stop HBT timers, if needed
			stopHbtTimers();
			// disconnect from endpoint
			disconnectEndpoint(TmlDisconnectionReasonEnum.RX_HBT_EXPIRED);
			// cleanup
			cleanup();
			// return
			LOG.fine("Channel disconnected: " + toString() + " via hbtRxTimerExpired()");
		} finally {
			this.lock.unlock();
		}
	}
	
	private void restartHbtTxTimer() {
		this.lock.lock();
		try {
			if(this.hbtTxTimer != null) {
				this.hbtTxTimer.cancel();
				this.hbtTxTimer = null;
			}
			if(this.heartbeatTimer.get() > 0) {
				this.hbtTxTimer = new TimerTask() {
					@Override
					public void run() {
						sendHbtMessage();
					}
				};
				this.hbtScheduler.schedule(this.hbtTxTimer, this.heartbeatTimer.get() * 1000);
			}
		} finally {
			this.lock.unlock();
		}
	}

	private boolean isTmlHbt(byte[] headerBuffer) {
		return headerBuffer != null && headerBuffer.length > 0 && headerBuffer[0] == 0x03;
	}

	private void remotePeerAbortDetected(IOException e, byte code) {
		LOG.log(Level.SEVERE, "Remote peer abort detected on channel " + toString() + ", code " + PeerAbortReasonEnum.fromCode(code), e);
		this.lock.lock();
		try {
			// stop HBT timers, if needed
			stopHbtTimers();
			// disconnect from endpoint
			disconnectEndpoint(TmlDisconnectionReasonEnum.REMOTE_PEER_ABORT);
			// cleanup
			cleanup();
			// return
			LOG.fine("Channel disconnected: " + toString() + " via remotePeerAbortDetected()");
		} finally {
			this.lock.unlock();
		}
	}

	private void remoteDisconnectionDetected(IOException e) {
		LOG.log(Level.SEVERE, "Remote disconnection detected on channel " + toString(), e);
		this.lock.lock();
		try {
			// stop HBT timers, if needed
			stopHbtTimers();
			// disconnect from endpoint
			disconnectEndpoint(TmlDisconnectionReasonEnum.REMOTE_DISCONNECT);
			// cleanup
			cleanup();
			// return
			LOG.fine("Channel disconnected: " + toString() + " via remoteDisconnectionDetected()");
		} finally {
			this.lock.unlock();
		}
	}

	private InputStream getRxStream() {
		this.lock.lock();
		try {
			return this.rxStream;
		} finally {
			this.lock.unlock();
		}
	}

	private void startHbtTimers() {
		restartHbtRxTimer();
		restartHbtTxTimer();
	}

	private void sendCtxMessage() throws TmlChannelException {
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
	
	private void sendHbtMessage() {
		OutputStream os = getTxStream();
		if(os == null) {
			LOG.warning("Cannot send HBT on channel " + toString() + ", disconnected");
			return;
		}
		try {
			os.write(HBT_MESSAGE);
			this.statsCounter.addOut(HBT_MESSAGE.length);
		} catch (IOException e) {
			LOG.log(Level.SEVERE, "Exception while sending HBT on channel " + toString(), e);
			this.lock.lock();
			try {
				// stop read thread
				stopReadingThread();
				// stop HBT timers, if needed
				stopHbtTimers();
				// disconnect from endpoint
				disconnectEndpoint(TmlDisconnectionReasonEnum.HBT_TX_SEND_ERROR);
				// cleanup
				cleanup();
				// return
				LOG.fine("Channel disconnected: " + toString() + " via sendHbtMessage()");
			} finally {
				this.lock.unlock();
			}
		}
		restartHbtTxTimer();
	}

	private void cleanup() {
		this.sock = null;
		this.rxStream = null;
		this.txStream = null;
		this.readingThread = null;
	}

	/**
	 * This method sends a PEER-ABORT to the other end using the urgent byte, and disconnects the channel.
	 *
	 * @param reason the reason of the PEER-ABORT
	 */
	public void abort(byte reason) {
		this.lock.lock();
		try {
			// send urgent data
			try {
				if (this.sock != null) {
					this.sock.sendUrgentData(reason);
				} else {
					LOG.info("Aborting channel " + toString() + " but no connection is established, urgent data " + reason + " not sent");
				}
			} catch (IOException e) {
				LOG.log(Level.WARNING, "Exception while aborting channel " + toString() + " with reason " + reason, e);
			}
			// stop read thread
			stopReadingThread();
			// stop HBT timers, if needed
			stopHbtTimers();
			// disconnect from endpoint
			disconnectEndpoint(TmlDisconnectionReasonEnum.PEER_ABORT);
			// cleanup
			cleanup();
			// return
			LOG.fine("Channel disconnected: " + toString() + " via abort()");
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * This method disconnects the channel.
	 */
	public void disconnect() {
		this.lock.lock();
		try {
			if (this.sock == null) {
				LOG.info("Disconnecting channel " + toString() + " but it is already disconnected");
				return;
			}
			// stop read thread
			stopReadingThread();
			// stop HBT timers, if needed
			stopHbtTimers();
			// disconnect from endpoint
			disconnectEndpoint(TmlDisconnectionReasonEnum.LOCAL_DISCONNECT);
			// cleanup
			cleanup();
			// return
			LOG.fine("Channel disconnected: " + toString() + " via disconnect()");
		} finally {
			this.lock.unlock();
		}
	}

	private void disconnectEndpoint(TmlDisconnectionReasonEnum reason) {
		try {
			this.aboutToDisconnect = true;
			if(this.sock != null) {
				this.sock.close();
			}
			if(this.rxStream != null) {
				this.rxStream.close();
			}
			if(this.txStream != null) {
				this.txStream.close();
			}
			if(this.serverSocket != null) { // Server mode
				this.serverSocket.close();
			}
		} catch (IOException e) {
			LOG.log(Level.FINE, "Socket/stream on channel " + toString() + " threw exception on close()", e);
		}
		try {
			this.observer.onChannelDisconnected(this, reason);
		} catch (Exception e) {
			LOG.log(Level.WARNING, "Notification of disconnection on channel " + toString() + " threw exception on observer", e);
		}
	}

	private void stopHbtTimers() {
		if(this.hbtRxTimer != null) {
			this.hbtRxTimer.cancel();
			this.hbtRxTimer = null;
		}
		if(this.hbtTxTimer != null) {
			this.hbtTxTimer.cancel();
			this.hbtTxTimer = null;
		}
	}

	private void stopReadingThread() {
		this.running = false;
	}

	/**
	 * This method allows to send a PDU to the other endpoint.
	 *
	 * @param pdu the PDU to be sent
	 * @throws TmlChannelException if the channel is not connected
	 */
	public void sendPdu(byte[] pdu) throws TmlChannelException {
		OutputStream os = getTxStream();
		if(os == null) {
			throw new TmlChannelException("Channel " + toString() + " not connected");
		}
		try {
			byte[] toSend = ByteBuffer.allocate(8 + pdu.length).put(PDU_MESSAGE_HDR).putInt(pdu.length).put(pdu).array();
			os.write(toSend);
			this.statsCounter.addOut(toSend.length);
		} catch (IOException e) {
			throw new TmlChannelException("Exception while writing on channel " + toString(), e);
		}
	}

	private OutputStream getTxStream() {
		this.lock.lock();
		try {
			return this.txStream;
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * This method is used to compute the current data rate in bytes/seconds. The sampling time is driven by the
	 * frequency used to call this method.
	 *
	 * @return the current data rate (and other ancillary information)
	 */
	public DataRateSample getDataRate() {
		return this.statsCounter.sample();
	}

	/**
	 * This method is used to check whether this TML channel is running. A channel is considered running if it is
	 * actively trying to read data from a TCP/IP connection. Disconnected channels are by definition not running.
	 *
	 * @return true if the channel is running
	 */
	public boolean isRunning() {
		return this.running;
	}
	
	@Override
	public String toString() {
		return "TmlChannel[" + this.host + ":" + this.port + "]@" + hashCode();
	}
}
