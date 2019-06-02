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

public class ServiceInstanceState {

	private ServiceInstanceBindingStateEnum state;
	private String serviceInstanceIdentifier;
	private String initiatorIdentifier;
	private String responderIdentifier;
	private String responderPortIdentifier;
	private Integer sleVersion;
	private int returnTimeoutPeriod;
	private boolean statusReportScheduled;

	private String lastError;
	private Exception lastException;

	public final String getLastError() {
		return lastError;
	}

	public final void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public final Exception getLastException() {
		return lastException;
	}

	public final void setLastException(Exception lastException) {
		this.lastException = lastException;
	}

	public final boolean isStatusReportScheduled() {
		return statusReportScheduled;
	}

	public final void setStatusReportScheduled(boolean statusReportScheduled) {
		this.statusReportScheduled = statusReportScheduled;
	}

	public final int getReturnTimeoutPeriod() {
		return returnTimeoutPeriod;
	}

	public final void setReturnTimeoutPeriod(int returnTimeoutPeriod) {
		this.returnTimeoutPeriod = returnTimeoutPeriod;
	}

	public final ServiceInstanceBindingStateEnum getState() {
		return state;
	}

	public final void setState(ServiceInstanceBindingStateEnum state) {
		this.state = state;
	}

	public final String getServiceInstanceIdentifier() {
		return serviceInstanceIdentifier;
	}

	public final void setServiceInstanceIdentifier(String serviceInstanceIdentifier) {
		this.serviceInstanceIdentifier = serviceInstanceIdentifier;
	}

	public final String getInitiatorIdentifier() {
		return initiatorIdentifier;
	}

	public final void setInitiatorIdentifier(String initiatorIdentifier) {
		this.initiatorIdentifier = initiatorIdentifier;
	}

	public final String getResponderIdentifier() {
		return responderIdentifier;
	}

	public final void setResponderIdentifier(String responderIdentifier) {
		this.responderIdentifier = responderIdentifier;
	}

	public final String getResponderPortIdentifier() {
		return responderPortIdentifier;
	}

	public final void setResponderPortIdentifier(String responderPortIdentifier) {
		this.responderPortIdentifier = responderPortIdentifier;
	}

	public final Integer getSleVersion() {
		return sleVersion;
	}

	public final void setSleVersion(Integer sleVersion) {
		this.sleVersion = sleVersion;
	}

}
