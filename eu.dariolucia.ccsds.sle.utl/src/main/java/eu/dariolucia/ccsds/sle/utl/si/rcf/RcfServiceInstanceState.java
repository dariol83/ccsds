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

package eu.dariolucia.ccsds.sle.utl.si.rcf;

import java.util.Date;
import java.util.List;

import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import eu.dariolucia.ccsds.sle.utl.si.LockStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.ProductionStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;

public class RcfServiceInstanceState extends ServiceInstanceState {

	// Configuration or GET_PARAMETER
	private Integer latencyLimit;
	private List<GVCID> permittedGvcid;
	private Integer minReportingCycle;
	private Integer reportingCycle;
	private int transferBufferSize;
	// Selected via START
	private GVCID requestedGvcid;
	private DeliveryModeEnum deliveryMode;
	private Date startTime;
	private Date endTime;
	// STATUS_REPORT
	private int numFramesDelivered;
	private LockStatusEnum frameSyncLockStatus;
	private LockStatusEnum symbolSyncLockStatus;
	private LockStatusEnum subcarrierLockStatus;
	private LockStatusEnum carrierLockStatus;
	private ProductionStatusEnum productionStatus;

	public final Integer getLatencyLimit() {
		return latencyLimit;
	}

	public final void setLatencyLimit(Integer latencyLimit) {
		this.latencyLimit = latencyLimit;
	}

	public final List<GVCID> getPermittedGvcid() {
		return permittedGvcid;
	}

	public final void setPermittedGvcid(List<GVCID> permittedGvcid) {
		this.permittedGvcid = permittedGvcid;
	}

	public final Integer getMinReportingCycle() {
		return minReportingCycle;
	}

	public final void setMinReportingCycle(Integer minReportingCycle) {
		this.minReportingCycle = minReportingCycle;
	}

	public final Integer getReportingCycle() {
		return reportingCycle;
	}

	public final void setReportingCycle(Integer reportingCycle) {
		this.reportingCycle = reportingCycle;
	}

	public final int getTransferBufferSize() {
		return transferBufferSize;
	}

	public final void setTransferBufferSize(int transferBufferSize) {
		this.transferBufferSize = transferBufferSize;
	}

	public final GVCID getRequestedGvcid() {
		return requestedGvcid;
	}

	public final void setRequestedGvcid(GVCID requestedGvcid) {
		this.requestedGvcid = requestedGvcid;
	}

	public final DeliveryModeEnum getDeliveryMode() {
		return deliveryMode;
	}

	public final void setDeliveryMode(DeliveryModeEnum deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

	public final int getNumFramesDelivered() {
		return numFramesDelivered;
	}

	public final void setNumFramesDelivered(int numFramesDelivered) {
		this.numFramesDelivered = numFramesDelivered;
	}

	public final LockStatusEnum getFrameSyncLockStatus() {
		return frameSyncLockStatus;
	}

	public final void setFrameSyncLockStatus(LockStatusEnum frameSyncLockStatus) {
		this.frameSyncLockStatus = frameSyncLockStatus;
	}

	public final LockStatusEnum getSymbolSyncLockStatus() {
		return symbolSyncLockStatus;
	}

	public final void setSymbolSyncLockStatus(LockStatusEnum symbolSyncLockStatus) {
		this.symbolSyncLockStatus = symbolSyncLockStatus;
	}

	public final LockStatusEnum getSubcarrierLockStatus() {
		return subcarrierLockStatus;
	}

	public final void setSubcarrierLockStatus(LockStatusEnum subcarrierLockStatus) {
		this.subcarrierLockStatus = subcarrierLockStatus;
	}

	public final LockStatusEnum getCarrierLockStatus() {
		return carrierLockStatus;
	}

	public final void setCarrierLockStatus(LockStatusEnum carrierLockStatus) {
		this.carrierLockStatus = carrierLockStatus;
	}

	public final ProductionStatusEnum getProductionStatus() {
		return productionStatus;
	}

	public final void setProductionStatus(ProductionStatusEnum productionStatus) {
		this.productionStatus = productionStatus;
	}

	public final Date getStartTime() {
		return startTime;
	}

	public final void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public final Date getEndTime() {
		return endTime;
	}

	public final void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	@Override
	public String toString() {
		return "RcfServiceInstanceState{" +
				"latencyLimit=" + getLatencyLimit() +
				", permittedGvcid=" + getPermittedGvcid() +
				", minReportingCycle=" + getMinReportingCycle() +
				", reportingCycle=" + getReportingCycle() +
				", transferBufferSize=" + getTransferBufferSize() +
				", requestedGvcid=" + getRequestedGvcid() +
				", deliveryMode=" + getDeliveryMode() +
				", startTime=" + getStartTime() +
				", endTime=" + getEndTime() +
				", numFramesDelivered=" + getNumFramesDelivered() +
				", frameSyncLockStatus=" + getFrameSyncLockStatus() +
				", symbolSyncLockStatus=" + getSymbolSyncLockStatus() +
				", subcarrierLockStatus=" + getSubcarrierLockStatus() +
				", carrierLockStatus=" + getCarrierLockStatus() +
				", productionStatus=" + getProductionStatus() +
				"} " + super.toString();
	}
}
