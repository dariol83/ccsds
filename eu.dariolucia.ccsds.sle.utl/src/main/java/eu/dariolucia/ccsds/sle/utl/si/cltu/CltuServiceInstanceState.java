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

package eu.dariolucia.ccsds.sle.utl.si.cltu;

import eu.dariolucia.ccsds.sle.utl.si.DeliveryModeEnum;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;

public class CltuServiceInstanceState extends ServiceInstanceState {

	// Configuration or GET_PARAMETER
	private Integer maxCltuLength;
	private Integer minCltuDelay;
	private Integer maxCltuDelay;
	private boolean bitlockRequired;
	private boolean rfAvailableRequired;
	private boolean protocolAbortClearEnabled;
	private Integer minReportingCycle;
	private Integer reportingCycle;
	//
	private Integer acquisitionSequenceLength;
	private GVCID clcwGlobalVcId; // can be null -> not configured
	private String clcwPhysicalChannel; // can be null -> not configured
	private Long cltuIdentification;
	private Long eventInvocationIdentification;
	private Long modulationFrequency;
	private Integer modulationIndex;
	private CltuNotificationModeEnum notificationMode;
	private Integer plop1IdleSequenceLength;
	private CltuPlopInEffectEnum plopInEffect;
	private CltuProtocolAbortModeEnum protocolAbortMode;
	private Integer subcarrierToBitRateRation;
	private DeliveryModeEnum deliveryMode;

	// START
	private Long firstCltuIdentification;
	// STATUS_REPORT
	private CltuLastProcessed lastProcessed; // also from async notify
	private CltuLastOk lastOk; // also from async notify
	private CltuProductionStatusEnum productionStatus; // also from async notify
	private CltuUplinkStatusEnum uplinkStatus; // also from async notify
	private Long nbCltuReceived;
	private Long nbCltuProcessed;
	private Long nbCltuRadiated;
	private Long bufferAvailable;
	// ASYNC_NOTIFY
	private CltuNotification notification;

	public final Integer getMaxCltuLength() {
		return maxCltuLength;
	}

	public final void setMaxCltuLength(Integer maxCltuLength) {
		this.maxCltuLength = maxCltuLength;
	}

	public final Integer getMinCltuDelay() {
		return minCltuDelay;
	}

	public final void setMinCltuDelay(Integer minCltuDelay) {
		this.minCltuDelay = minCltuDelay;
	}

	public final Integer getMaxCltuDelay() {
		return maxCltuDelay;
	}

	public final void setMaxCltuDelay(Integer maxCltuDelay) {
		this.maxCltuDelay = maxCltuDelay;
	}

	public final boolean isBitlockRequired() {
		return bitlockRequired;
	}

	public final void setBitlockRequired(boolean bitlockRequired) {
		this.bitlockRequired = bitlockRequired;
	}

	public final boolean isRfAvailableRequired() {
		return rfAvailableRequired;
	}

	public final void setRfAvailableRequired(boolean rfAvailableRequired) {
		this.rfAvailableRequired = rfAvailableRequired;
	}

	public final boolean isProtocolAbortClearEnabled() {
		return protocolAbortClearEnabled;
	}

	public final void setProtocolAbortClearEnabled(boolean protocolAbortClearEnabled) {
		this.protocolAbortClearEnabled = protocolAbortClearEnabled;
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

	public final Integer getAcquisitionSequenceLength() {
		return acquisitionSequenceLength;
	}

	public final void setAcquisitionSequenceLength(Integer acquisitionSequenceLength) {
		this.acquisitionSequenceLength = acquisitionSequenceLength;
	}

	public final GVCID getClcwGlobalVcId() {
		return clcwGlobalVcId;
	}

	public final void setClcwGlobalVcId(GVCID clcwGlobalVcId) {
		this.clcwGlobalVcId = clcwGlobalVcId;
	}

	public final String getClcwPhysicalChannel() {
		return clcwPhysicalChannel;
	}

	public final void setClcwPhysicalChannel(String clcwPhysicalChannel) {
		this.clcwPhysicalChannel = clcwPhysicalChannel;
	}

	public final Long getCltuIdentification() {
		return cltuIdentification;
	}

	public final void setCltuIdentification(Long cltuIdentification) {
		this.cltuIdentification = cltuIdentification;
	}

	public final Long getEventInvocationIdentification() {
		return eventInvocationIdentification;
	}

	public final void setEventInvocationIdentification(Long eventInvocationIdentification) {
		this.eventInvocationIdentification = eventInvocationIdentification;
	}

	public final Long getModulationFrequency() {
		return modulationFrequency;
	}

	public final void setModulationFrequency(Long modulationFrequency) {
		this.modulationFrequency = modulationFrequency;
	}

	public final Integer getModulationIndex() {
		return modulationIndex;
	}

	public final void setModulationIndex(Integer modulationIndex) {
		this.modulationIndex = modulationIndex;
	}

	public final CltuNotificationModeEnum getNotificationMode() {
		return notificationMode;
	}

	public final void setNotificationMode(CltuNotificationModeEnum notificationMode) {
		this.notificationMode = notificationMode;
	}

	public final Integer getPlop1IdleSequenceLength() {
		return plop1IdleSequenceLength;
	}

	public final void setPlop1IdleSequenceLength(Integer plop1IdleSequenceLength) {
		this.plop1IdleSequenceLength = plop1IdleSequenceLength;
	}

	public final CltuPlopInEffectEnum getPlopInEffect() {
		return plopInEffect;
	}

	public final void setPlopInEffect(CltuPlopInEffectEnum plopInEffect) {
		this.plopInEffect = plopInEffect;
	}

	public final CltuProtocolAbortModeEnum getProtocolAbortMode() {
		return protocolAbortMode;
	}

	public final void setProtocolAbortMode(CltuProtocolAbortModeEnum protocolAbortMode) {
		this.protocolAbortMode = protocolAbortMode;
	}

	public final Integer getSubcarrierToBitRateRation() {
		return subcarrierToBitRateRation;
	}

	public final void setSubcarrierToBitRateRation(Integer subcarrierToBitRateRation) {
		this.subcarrierToBitRateRation = subcarrierToBitRateRation;
	}

	public final Long getFirstCltuIdentification() {
		return firstCltuIdentification;
	}

	public final void setFirstCltuIdentification(Long firstCltuIdentification) {
		this.firstCltuIdentification = firstCltuIdentification;
	}

	public final CltuLastProcessed getLastProcessed() {
		return lastProcessed;
	}

	public final void setLastProcessed(CltuLastProcessed lastProcessed) {
		this.lastProcessed = lastProcessed;
	}

	public final CltuLastOk getLastOk() {
		return lastOk;
	}

	public final void setLastOk(CltuLastOk lastOk) {
		this.lastOk = lastOk;
	}

	public final CltuUplinkStatusEnum getUplinkStatus() {
		return uplinkStatus;
	}

	public final void setUplinkStatus(CltuUplinkStatusEnum uplinkStatus) {
		this.uplinkStatus = uplinkStatus;
	}

	public final Long getNbCltuReceived() {
		return nbCltuReceived;
	}

	public final void setNbCltuReceived(Long nbCltuReceived) {
		this.nbCltuReceived = nbCltuReceived;
	}

	public final Long getNbCltuProcessed() {
		return nbCltuProcessed;
	}

	public final void setNbCltuProcessed(Long nbCltuProcessed) {
		this.nbCltuProcessed = nbCltuProcessed;
	}

	public final Long getNbCltuRadiated() {
		return nbCltuRadiated;
	}

	public final void setNbCltuRadiated(Long nbCltuRadiated) {
		this.nbCltuRadiated = nbCltuRadiated;
	}

	public final Long getBufferAvailable() {
		return bufferAvailable;
	}

	public final void setBufferAvailable(Long bufferSize) {
		this.bufferAvailable = bufferSize;
	}

	public final CltuNotification getNotification() {
		return notification;
	}

	public final void setNotification(CltuNotification notification) {
		this.notification = notification;
	}

	public final CltuProductionStatusEnum getProductionStatus() {
		return productionStatus;
	}

	public final void setProductionStatus(CltuProductionStatusEnum productionStatus) {
		this.productionStatus = productionStatus;
	}

	public final DeliveryModeEnum getDeliveryMode() {
		return deliveryMode;
	}

	public final void setDeliveryMode(DeliveryModeEnum deliveryMode) {
		this.deliveryMode = deliveryMode;
	}

}
