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

package eu.dariolucia.ccsds.inspector.api;

import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;

public abstract class AbstractConnector implements IConnector {

	private final String name;
	private final String description;
	private final String version;
	private final ConnectorConfiguration configuration;
	private IConnectorObserver observer;

	private volatile boolean disposed;
	private volatile ConnectorState state;

	protected AbstractConnector(String name, String description, String version, ConnectorConfiguration configuration, IConnectorObserver observer) {
		this.name = name;
		this.description = description;
		this.version = version;
		this.configuration = configuration;
		this.observer = observer;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public void start() {
		if(this.state == ConnectorState.RUNNING || this.state == ConnectorState.STARTING) {
			return;
		}
		updateState(ConnectorState.STARTING);
		doStart();
		updateState(ConnectorState.RUNNING);
	}

	@Override
	public void step() {
		if(this.state == ConnectorState.RUNNING || this.state == ConnectorState.STARTING || this.state == ConnectorState.STOPPING) {
			return;
		}
		updateState(ConnectorState.STARTING);
		doStep();
		updateState(ConnectorState.STOPPING);
		updateState(ConnectorState.IDLE);
	}

	@Override
	public void stop() {
		if(this.state == ConnectorState.STOPPING || this.state == ConnectorState.IDLE || this.state == ConnectorState.ERROR) {
			return;
		}
		updateState(ConnectorState.STOPPING);
		doStop();
		updateState(ConnectorState.IDLE);
	}

	@Override
	public boolean isDisposed() {
		return disposed;
	}

	@Override
	public boolean isStarted() {
		return state == ConnectorState.RUNNING;
	}

	@Override
	public ConnectorConfiguration getConfiguration() {
		return configuration;
	}

	@Override
	public void dispose() {
		if(this.disposed) {
			return;
		}
		this.disposed = true;
		stop();
		doDispose();
	}

	protected final void notifyData(AnnotatedObject annotatedObject) {
		if(this.disposed) {
			return;
		}
		try {
			this.observer.dataReported(this, annotatedObject);
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	protected final void notifyError(Exception e, boolean irrecoverable) {
		if(this.disposed) {
			return;
		}
		try {
			this.observer.errorReported(this, e);
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		if(irrecoverable) {
			updateState(ConnectorState.ERROR);
		}
	}

	protected final void notifyInfo(SeverityEnum severity, String message) {
		if(this.disposed) {
			return;
		}
		try {
			this.observer.infoReported(this, severity, message);
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	protected final void updateState(ConnectorState state) {
		if(this.disposed) {
			return;
		}
		this.state = state;
		try {
			this.observer.stateReported(this, this.state);
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}

	protected ConnectorState getState() { return this.state; }

	protected abstract void doStart();

	protected abstract void doStop();

	protected abstract void doStep();

	protected abstract void doDispose();
}
