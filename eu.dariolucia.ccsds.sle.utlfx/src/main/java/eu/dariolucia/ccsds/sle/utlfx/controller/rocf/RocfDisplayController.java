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

package eu.dariolucia.ccsds.sle.utlfx.controller.rocf;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.controller.AbstractDisplayController;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.ComboDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.ScheduleStatusReportDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.rocf.RocfStartDialog;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfControlWordTypeEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfServiceInstanceState;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfUpdateModeEnum;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;

public class RocfDisplayController extends AbstractDisplayController {

	private static final Logger LOG = Logger.getLogger(RocfDisplayController.class.getName());

	// Toolbar - ROCF specific menu button controller
	@FXML
	protected RocfMenuButtonController sleOperationsMenuButtonController;

	@Override
	protected void configureExtension() {
		// Set controller for menu here
		this.sleOperationsMenuButtonController.setController(this);
	}

	@Override
	protected void doRegisterCommonProperties() {
		registerLabel("Latency Limit", "The latency limit for transfer buffers", RocfServiceInstanceState.class,
				(state) -> toString(state.getLatencyLimit()));
		registerLabel("Transfer Buffer Size", "The transfer buffer maximum size", RocfServiceInstanceState.class,
				(state) -> toString(state.getTransferBufferSize()));
		registerLabel("Permitted GVCIDs", "The permitted virtual channels that can be selected",
				RocfServiceInstanceState.class, (state) -> state.getPermittedGvcid().toString());
		registerLabel("Permitted TC VCIDs", "The permitted TC virtual channels that can be selected",
				RocfServiceInstanceState.class, (state) -> state.getPermittedTcVcid().toString());
		registerLabel("Permitted Control Word Types", "The permitted control word types to be delivered",
				RocfServiceInstanceState.class, (state) -> state.getPermittedControlWordTypes().toString());
		registerLabel("Permitted Update Modes", "The permitted update modes that can be selected",
				RocfServiceInstanceState.class, (state) -> state.getPermittedUpdateModes().toString());
		registerLabel("Min. Reporting Cycle", "The minimum reporting cycle for status reports",
				RocfServiceInstanceState.class, (state) -> toString(state.getMinReportingCycle()));
		registerLabel("Reporting Cycle", "The current reporting cycle for status reports",
				RocfServiceInstanceState.class, (state) -> toString(state.getReportingCycle()));
		registerLabel("Delivery Mode", "The configured delivery mode", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getDeliveryMode(), ""));
	}

	@Override
	protected void doRegisterNotificationProperties() {
		registerLabel("Processed Frames", "The number of processed transfer frames", RocfServiceInstanceState.class,
				(state) -> toString(state.getProcessedFrameNumber()));
		registerLabel("Delivered OCFs", "The number of delivered operational control fields",
				RocfServiceInstanceState.class, (state) -> toString(state.getDeliveredOcfsNumber()));
		registerLabel("Frame Lock", "The lock status at frame level", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getFrameSyncLockStatus(), ""),
				(state) -> toStyle(state.getFrameSyncLockStatus()));
		registerLabel("Symbol Lock", "The lock status at symbol level", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getSymbolSyncLockStatus(), ""),
				(state) -> toStyle(state.getSymbolSyncLockStatus()));
		registerLabel("Carrier Lock", "The lock status of the carrier", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getCarrierLockStatus(), ""),
				(state) -> toStyle(state.getCarrierLockStatus()));
		registerLabel("Subcarrier Lock", "The lock status of the subcarrier", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getSubcarrierLockStatus(), ""),
				(state) -> toStyle(state.getSubcarrierLockStatus()));
		registerLabel("Production Status", "The production status of the service instance",
				RocfServiceInstanceState.class, (state) -> Objects.toString(state.getProductionStatus(), ""),
				(state) -> toStyle(state.getProductionStatus()));
		registerLabel("Selected GVCID", "The selected GVCID", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getRequestedGvcid(), ""));
		registerLabel("Selected TC VCID", "The selected TC VCID", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getRequestedTcVcid(), ""));
		registerLabel("Selected Control Word Type", "The selected control word type", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getRequestedControlWordType(), ""));
		registerLabel("Selected Update Mode", "The selected update mode", RocfServiceInstanceState.class,
				(state) -> Objects.toString(state.getRequestedUpdateMode(), ""));
	}

	@Override
	protected void doRegisterParametersProperties() {
		// None
	}

	protected void sendStart(Object[] args) {
		try {
			((RocfServiceInstance) this.serviceInstance).start((Date) args[0], (Date) args[1], (GVCID) args[2],
					(Integer) args[3], (RocfControlWordTypeEnum) args[4], (RocfUpdateModeEnum) args[5]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE,
					"Error while forwarding start request to " + this.serviceInstance.getServiceInstanceIdentifier(),
					e);
		}
	}

	protected void sendScheduleStatusReport(Object[] args) {
		try {
			((RocfServiceInstance) this.serviceInstance).scheduleStatusReport((Boolean) args[0], (Integer) args[1]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding schedule-status-report request to "
					+ this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendStop() {
		try {
			((RocfServiceInstance) this.serviceInstance).stop();
		} catch (Exception e) {
			LOG.log(Level.SEVERE,
					"Error while forwarding stop request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendGetParameter(RocfParameterEnum param) {
		try {
			((RocfServiceInstance) this.serviceInstance).getParameter(param);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding get-parameter request to "
					+ this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	public void stopMenuItemSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Stop", null, "Invoke a STOP operation", null);
		if (result.get() == ButtonType.OK) {
			sendStop();
		}
	}

	public void startMenuItemSelected(ActionEvent e) {
		RocfStartDialog diag = new RocfStartDialog();
		diag.showAndWait().ifPresent((res) -> sendStart(
				new Object[] { res.startTime, res.endTime, res.gvcid, res.tcvcid, res.cwt, res.um }));
	}

	public void scheduleStatusReportMenuItemSelected(ActionEvent e) {
		ScheduleStatusReportDialog diag = new ScheduleStatusReportDialog();
		diag.showAndWait().ifPresent((res) -> sendScheduleStatusReport(
				new Object[] { res.isStop, (res.isImmediate || res.isStop) ? null : res.periodInSeconds }));
	}

	public void getParameterMenuItemSelected(ActionEvent e) {
		ComboDialog<RocfParameterEnum> getParamDialog = new ComboDialog<RocfParameterEnum>("Get Parameter",
				"Invoke a GET-PARAMETER operation", "Parameter",
				"The parameter to be requested by the GET-PARAMETER operation", RocfParameterEnum.values(),
				RocfParameterEnum.BUFFER_SIZE);
		getParamDialog.showAndWait().ifPresent(this::sendGetParameter);
	}
}
