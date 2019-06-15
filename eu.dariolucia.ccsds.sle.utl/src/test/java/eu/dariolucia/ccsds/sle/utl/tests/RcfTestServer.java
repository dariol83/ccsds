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

package eu.dariolucia.ccsds.sle.utl.tests;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannel;
import eu.dariolucia.ccsds.sle.utl.network.tml.TmlChannelException;

public class RcfTestServer {

	private final static Logger LOG = Logger.getLogger(RcfTestServer.class.getName());
	
	public static void main(String[] args) throws TmlChannelException, IOException, InterruptedException {
		// Set the log level
		Logger.getLogger("eu.dariolucia").setLevel(Level.WARNING);
		
		int port = Integer.parseInt(args[0]);
		while(true) {
			TmlChannel tsc = TmlChannel.createServerTmlChannel(port, new RcfChannelObserver(), 0,0 );
			LOG.info("Preparing to start connection... ");
			tsc.connect();
			LOG.info("Waiting for connection...");
			while(tsc.isRunning()) {
				Thread.sleep(1000);
			}
		}
	}
}
