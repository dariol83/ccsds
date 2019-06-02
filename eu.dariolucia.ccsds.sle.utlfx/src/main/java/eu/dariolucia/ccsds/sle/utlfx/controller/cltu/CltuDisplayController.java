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

package eu.dariolucia.ccsds.sle.utlfx.controller.cltu;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.controller.AbstractDisplayController;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.ComboDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.ScheduleStatusReportDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.cltu.CltuStartDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.cltu.CltuThrowEventDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.cltu.CltuTransferDataDialog;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuServiceInstanceState;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;

public class CltuDisplayController extends AbstractDisplayController {

	private static final Logger LOG = Logger.getLogger(CltuDisplayController.class.getName());
	
	// Toolbar - CLTU specific menu button controller
	@FXML
	protected CltuMenuButtonController sleOperationsMenuButtonController;

	@Override
	protected void configureExtension() {
		// Set controller for menu here
		this.sleOperationsMenuButtonController.setController(this);
	}
	
	@Override
	protected void doRegisterCommonProperties() {
		registerLabel("Max. CLTU Length", "The maximum length of CLTUs", CltuServiceInstanceState.class, (state) -> toString(state.getMaxCltuLength()));
		registerLabel("Min. CLTU Delay", "The minimum CLTU delay", CltuServiceInstanceState.class, (state) -> toString(state.getMinCltuDelay()));
		registerLabel("Max. CLTU Delay", "The maximum CLTU delay", CltuServiceInstanceState.class, (state) -> toString(state.getMaxCltuDelay()));
		registerLabel("Bitlock Required", "Bitlock required for radiation", CltuServiceInstanceState.class, (state) -> String.valueOf(state.isBitlockRequired()));
		registerLabel("RF Available Required", "RF available required for radiation", CltuServiceInstanceState.class, (state) -> String.valueOf(state.isRfAvailableRequired()));
		registerLabel("Protocol Abort Clear Enabled", "Protocol abort clear enablement", CltuServiceInstanceState.class, (state) -> String.valueOf(state.isProtocolAbortClearEnabled()));
		registerLabel("Min. Reporting Cycle", "The minimum reporting cycle for status reports", CltuServiceInstanceState.class, (state) -> toString(state.getMinReportingCycle()));
		registerLabel("Reporting Cycle", "The current reporting cycle for status reports", CltuServiceInstanceState.class, (state) -> toString(state.getReportingCycle()));
		registerLabel("Delivery Mode", "The configured delivery mode", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getDeliveryMode(), ""));
	}

	@Override
	protected void doRegisterNotificationProperties() {
		registerLabel("First CLTU Identification", "The first CLTU identification set with the START operation", CltuServiceInstanceState.class, (state) -> toString(state.getFirstCltuIdentification()));
		registerLabel("Last Processed", "The state of the last processed CLTU", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getLastProcessed(), ""));
		registerLabel("Last OK", "The state of the last CLTU correctly processed", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getLastOk(), ""));
		registerLabel("Production Status", "The production status", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getProductionStatus(), ""), (state) -> toStyle(state.getProductionStatus()));
		registerLabel("Uplink Status", "The uplink status", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getUplinkStatus(), ""), (state) -> toStyle(state.getUplinkStatus()));
		registerLabel("CLTUs Received", "The number of received CLTUs", CltuServiceInstanceState.class, (state) -> toString(state.getNbCltuReceived()));
		registerLabel("CLTUs Processed", "The number of processed CLTUs", CltuServiceInstanceState.class, (state) -> toString(state.getNbCltuProcessed()));
		registerLabel("CLTUs Radiated", "The number of radiated CLTUs", CltuServiceInstanceState.class, (state) -> toString(state.getNbCltuRadiated()));
		registerLabel("Available Buffer", "The amount of available buffer", CltuServiceInstanceState.class, (state) -> toString(state.getBufferAvailable()));
		registerLabel("Last Notification", "The last received notification", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getNotification(), ""));
	}
	
	@Override
	protected void doRegisterParametersProperties() {
		registerLabel("Acquisition Signal Length", "The length of the acquisition signal", CltuServiceInstanceState.class, (state) -> toString(state.getAcquisitionSequenceLength()));
		registerLabel("CLCW Global VCID", "The global VCID of the CLCW", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getClcwGlobalVcId(),""));
		registerLabel("CLCW Physical Channel", "The physical channel of the CLCW", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getClcwPhysicalChannel(), ""));
		registerLabel("CLTU Identification", "The CLTU identification", CltuServiceInstanceState.class, (state) -> toString(state.getCltuIdentification()));
		registerLabel("Event Invocation Identification", "The event invocation identification", CltuServiceInstanceState.class, (state) -> toString(state.getEventInvocationIdentification()));
		registerLabel("Modulation Frequency", "The modulation frequency", CltuServiceInstanceState.class, (state) -> toString(state.getModulationFrequency()));
		registerLabel("Modulation Index", "The modulation index", CltuServiceInstanceState.class, (state) -> toString(state.getModulationIndex()));
		registerLabel("Notification Mode", "The notification mode", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getNotificationMode(), ""));
		registerLabel("PLOP1 Idle Sequence Length", "The length of the PLOP1 idle sequence", CltuServiceInstanceState.class, (state) -> toString(state.getPlop1IdleSequenceLength()));
		registerLabel("PLOP In Effect", "The PLOP in effect", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getPlopInEffect(), ""));
		registerLabel("Protocol Abort Mode", "The selected mode of the protocol abort", CltuServiceInstanceState.class, (state) -> Objects.toString(state.getProtocolAbortMode(), ""));
		registerLabel("Subcarrier to Bitrate Ratio", "The subcarrier to bitrate ratio", CltuServiceInstanceState.class, (state) -> toString(state.getSubcarrierToBitRateRation()));
	}
	
	protected void sendStart(Object[] args) {
		try {
			((CltuServiceInstance) this.serviceInstance).start((Long) args[0]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding start request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendScheduleStatusReport(Object[] args) {
		try {
			((CltuServiceInstance) this.serviceInstance).scheduleStatusReport((Boolean) args[0], (Integer) args[1]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding schedule-status-report request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendStop() {
		try {
			((CltuServiceInstance) this.serviceInstance).stop();
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding stop request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendGetParameter(CltuParameterEnum param) {
		try {
			((CltuServiceInstance) this.serviceInstance).getParameter(param);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding get-parameter request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}
	
	protected void sendThrowEvent(Object[] args) {
		try {
			((CltuServiceInstance) this.serviceInstance).throwEvent((Long) args[0], (Integer) args[1], (byte[]) args[2]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding throw-event request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}
	
	protected void sendTransferData(Object[] args) {
		try {
			((CltuServiceInstance) this.serviceInstance).transferData((Long) args[0], (Date) args[1], (Date) args[2], (Long) args[3], (boolean) args[4], (byte[]) args[5]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding transfer-data request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	public void stopMenuItemSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Stop", null, "Invoke a STOP operation", null);
		if (result.get() == ButtonType.OK) {
			sendStop();
		}
	}

	public void startMenuItemSelected(ActionEvent e) {
		CltuStartDialog diag = new CltuStartDialog();
		diag.showAndWait().ifPresent((res) -> sendStart(new Object[] { res.firstCltuId }));
	}

	public void scheduleStatusReportMenuItemSelected(ActionEvent e) {
		ScheduleStatusReportDialog diag = new ScheduleStatusReportDialog();
		diag.showAndWait().ifPresent((res) -> sendScheduleStatusReport(
				new Object[] { res.isStop, (res.isImmediate || res.isStop) ? null : res.periodInSeconds }));
	}

	public void getParameterMenuItemSelected(ActionEvent e) {
		ComboDialog<CltuParameterEnum> getParamDialog = new ComboDialog<CltuParameterEnum>("Get Parameter",
				"Invoke a GET-PARAMETER operation", "Parameter",
				"The parameter to be requested by the GET-PARAMETER operation", CltuParameterEnum.values(),
				CltuParameterEnum.ACQUISITION_SEQUENCE_LENGTH);
		getParamDialog.showAndWait().ifPresent(this::sendGetParameter);
	}
	
	public void transferDataMenuItemSelected(ActionEvent e) {
		CltuTransferDataDialog diag = new CltuTransferDataDialog();
		diag.showAndWait().ifPresent((res) -> sendTransferData(new Object[] { res.cltuId, res.startTime, res.endTime, res.maxDelay, res.notification, res.data}));
	}
	
	public void throwEventMenuItemSelected(ActionEvent e) {
		CltuThrowEventDialog diag = new CltuThrowEventDialog();
		diag.showAndWait().ifPresent((res) -> sendThrowEvent(new Object[] { res.eiid, res.eid, res.eq }));
	}
}
