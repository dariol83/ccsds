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

package eu.dariolucia.ccsds.sle.utlfx.controller.raf;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.controller.AbstractDisplayController;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.ComboDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.ScheduleStatusReportDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.raf.RafStartDialog;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafParameterEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstanceState;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonType;

public class RafDisplayController extends AbstractDisplayController {
	
	private static final Logger LOG = Logger.getLogger(RafDisplayController.class.getName());
	
	// Toolbar - RAF specific menu button controller
	@FXML
	protected RafMenuButtonController sleOperationsMenuButtonController;

	@Override
	protected void configureExtension() {
		// Set controller for menu here
		this.sleOperationsMenuButtonController.setController(this);
	}
	
	@Override
	protected void doRegisterCommonProperties() {
		registerLabel("Latency Limit", "The latency limit for transfer buffers", RafServiceInstanceState.class, (state) -> toString(state.getLatencyLimit()));
		registerLabel("Transfer Buffer Size", "The transfer buffer maximum size", RafServiceInstanceState.class, (state) -> toString(state.getTransferBufferSize()));
		registerLabel("Permitted Frame Quality", "The permitted frame qualities that can be selected", RafServiceInstanceState.class, (state) -> state.getPermittedFrameQuality().toString());
		registerLabel("Min. Reporting Cycle", "The minimum reporting cycle for status reports", RafServiceInstanceState.class, (state) -> toString(state.getMinReportingCycle()));
		registerLabel("Reporting Cycle", "The current reporting cycle for status reports", RafServiceInstanceState.class, (state) -> toString(state.getReportingCycle()));
		registerLabel("Delivery Mode", "The configured delivery mode", RafServiceInstanceState.class, (state) -> Objects.toString(state.getDeliveryMode(), ""));
	}
	
	@Override
	protected void doRegisterNotificationProperties() {
		registerLabel("Delivered Frames", "The number of delivered transfer frames", RafServiceInstanceState.class, (state) -> toString(state.getDeliveredFrameNumber()));
		registerLabel("Error-Free Frames", "The number of error-free transfer frames", RafServiceInstanceState.class, (state) -> toString(state.getErrorFreeFrameNumber()));
		registerLabel("Frame Lock", "The lock status at frame level", RafServiceInstanceState.class, (state) -> Objects.toString(state.getFrameSyncLockStatus(), ""), (state) -> toStyle(state.getFrameSyncLockStatus()));
		registerLabel("Symbol Lock", "The lock status at symbol level", RafServiceInstanceState.class, (state) -> Objects.toString(state.getSymbolSyncLockStatus(), ""), (state) -> toStyle(state.getSymbolSyncLockStatus()));
		registerLabel("Carrier Lock", "The lock status of the carrier", RafServiceInstanceState.class, (state) -> Objects.toString(state.getCarrierLockStatus(), ""), (state) -> toStyle(state.getCarrierLockStatus()));
		registerLabel("Subcarrier Lock", "The lock status of the subcarrier", RafServiceInstanceState.class, (state) -> Objects.toString(state.getSubcarrierLockStatus(), ""), (state) -> toStyle(state.getSubcarrierLockStatus()));
		registerLabel("Production Status", "The production status of the service instance", RafServiceInstanceState.class, (state) -> Objects.toString(state.getProductionStatus(), ""), (state) -> toStyle(state.getProductionStatus()));
		registerLabel("Selected Frame Quality", "The selected frame quality", RafServiceInstanceState.class, (state) -> Objects.toString(state.getRequestedFrameQuality(), ""));
	}
	
	@Override
	protected void doRegisterParametersProperties() {
		// None
	}

	protected void sendStart(Object[] args) {
		try {
			((RafServiceInstance) this.serviceInstance).start((Date) args[0], (Date) args[1], (RafRequestedFrameQualityEnum) args[2]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding start request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendScheduleStatusReport(Object[] args) {
		try {
			((RafServiceInstance) this.serviceInstance).scheduleStatusReport((Boolean) args[0], (Integer) args[1]);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding schedule-status-report request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendStop() {
		try {
			((RafServiceInstance) this.serviceInstance).stop();
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding stop request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendGetParameter(RafParameterEnum param) {
		try {
			((RafServiceInstance) this.serviceInstance).getParameter(param);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding get-parameter request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	public void stopMenuItemSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Stop", null, "Invoke a STOP operation", null);
		if (result.get() == ButtonType.OK) {
			sendStop();
		}
	}

	public void startMenuItemSelected(ActionEvent e) {
		RafStartDialog diag = new RafStartDialog();
		diag.showAndWait().ifPresent((res) -> sendStart(new Object[] { res.startTime, res.endTime, res.requestedFrameQuality }));
	}

	public void scheduleStatusReportMenuItemSelected(ActionEvent e) {
		ScheduleStatusReportDialog diag = new ScheduleStatusReportDialog();
		diag.showAndWait().ifPresent((res) -> sendScheduleStatusReport(
				new Object[] { res.isStop, (res.isImmediate || res.isStop) ? null : res.periodInSeconds }));
	}

	public void getParameterMenuItemSelected(ActionEvent e) {
		ComboDialog<RafParameterEnum> getParamDialog = new ComboDialog<RafParameterEnum>("Get Parameter",
				"Invoke a GET-PARAMETER operation", "Parameter",
				"The parameter to be requested by the GET-PARAMETER operation", RafParameterEnum.values(),
				RafParameterEnum.BUFFER_SIZE);
		getParamDialog.showAndWait().ifPresent(this::sendGetParameter);
	}
}
