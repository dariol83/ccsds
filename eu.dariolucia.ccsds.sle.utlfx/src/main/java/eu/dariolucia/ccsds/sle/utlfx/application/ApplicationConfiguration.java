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

package eu.dariolucia.ccsds.sle.utlfx.application;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.util.Callback;

public class ApplicationConfiguration {

	private static final ApplicationConfiguration INSTANCE = new ApplicationConfiguration();
	
	public static final ApplicationConfiguration instance() {
		return INSTANCE;
	}
	
	private CopyOnWriteArrayList<Callback<ApplicationConfiguration, Void>> callbacks = new CopyOnWriteArrayList<>(); 
	
	private volatile int maxLogs;
	private volatile int maxPdus;
	private volatile boolean debugActive;

	private ApplicationConfiguration() {
		this.maxLogs = 50000;
		this.maxPdus = 50000;

		this.debugActive = false;
		Logger.getLogger("eu.dariolucia").setLevel(Level.INFO);
		for(Handler h : Logger.getLogger("eu.dariolucia").getHandlers()) {
			h.setLevel(Level.INFO);
		}
	}
	
	public final int getMaxLogs() {
		return maxLogs;
	}

	public final void setMaxLogs(int maxLogs) {
		if(maxLogs == this.maxLogs) return;
		this.maxLogs = maxLogs;
		notifyCallbacks();
	}

	public final int getMaxPdus() {
		return maxPdus;
	}

	public final void setMaxPdus(int maxPdus) {
		if(maxPdus == this.maxPdus) return;
		this.maxPdus = maxPdus;
		notifyCallbacks();
	}

	public final boolean isDebugActive() {
		return debugActive;
	}

	public final void setDebugActive(boolean debugActive) {
		if(debugActive == this.debugActive) return;
		this.debugActive = debugActive;
		// Apply
		if(this.debugActive) {
			Logger.getLogger("eu.dariolucia").setLevel(Level.ALL);
			for(Handler h : Logger.getLogger("eu.dariolucia").getHandlers()) {
				h.setLevel(Level.ALL);
			}
		} else {
			Logger.getLogger("eu.dariolucia").setLevel(Level.INFO);
			for(Handler h : Logger.getLogger("eu.dariolucia").getHandlers()) {
				h.setLevel(Level.INFO);
			}
		}
		notifyCallbacks();
	}

	public void register(Callback<ApplicationConfiguration, Void> e) {
		this.callbacks.add(e);
	}
	
	public void deregister(Callback<ApplicationConfiguration, Void> e) {
		this.callbacks.remove(e);
	}
	
	protected void notifyCallbacks() {
		this.callbacks.forEach(o -> o.call(this));
	}
}
