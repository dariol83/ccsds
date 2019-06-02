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

package eu.dariolucia.ccsds.sle.utlfx.controller;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.sle.utlfx.manager.PduInspectorManager;
import eu.dariolucia.ccsds.sle.utlfx.manager.RawDataViewerManager.RawDataEntry;
import eu.dariolucia.ccsds.sle.utlfx.manager.SlePdu;
import eu.dariolucia.ccsds.sle.utlfx.manager.SlePduAttribute;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class PduInspectorController implements Initializable {

	// PDU Inspector
	@FXML
	protected TableView<SlePdu> pduTableView;
	@FXML
	protected TreeTableView<SlePduAttribute> pduDetailsTreeTableView;
	@FXML
	protected Label pduDescriptionText;

	@FXML
	private ToggleButton enablePduInspectionButton;

	@FXML
	private Button clearPduInspectionButton;

	@FXML
	protected TitledPane pduInspectionTitledPane;

	// PDU Inspector accordion - Raw Data
	@FXML
	protected Label rawDataDetailsDescriptionText;
	@FXML
	protected TableView<RawDataEntry> rawDataDetailsTableView;

	// PDU Inspector - PDU manager
	protected PduInspectorManager pduInspectorManager;

	protected volatile boolean monitorPdus = true;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.pduInspectorManager = new PduInspectorManager(pduTableView, pduDetailsTreeTableView, pduDescriptionText,
				rawDataDetailsDescriptionText, rawDataDetailsTableView);

		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/play-circle.png"));
			this.enablePduInspectionButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/trash-2.png"));
			this.clearPduInspectionButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/activity.png"));
			this.pduInspectionTitledPane.setGraphic(new ImageView(image));
		}
	}

	@FXML
	protected void enablePduInspectionCheckMenuItemSelected(ActionEvent e) {
		this.monitorPdus = ((ToggleButton) e.getSource()).isSelected();
	}

	@FXML
	protected void clearPduButtonSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Clear service instance PDUs",
				"Are you sure to remove all the collected PDUs for the selected service instance?", null, null);
		if (result.get() == ButtonType.OK) {
			this.pduInspectorManager.clear();
		}
	}

	public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> {
				this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "IN", false, name));
			});
		}
	}

	public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> {
				this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "OUT", false, name));
			});
		}
	}

	public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error,
			Exception exception) {
		if (this.monitorPdus) {
			Platform.runLater(() -> {
				this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "OUT", true, name));
			});
		}
	}

	public void onPduDecodingError(ServiceInstance si, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> {
				this.pduInspectorManager.addPdu(new SlePdu(null, encodedOperation, "IN", true, "<DECODING ERROR>"));
			});
		}
	}

	public void onPduHandlingError(ServiceInstance si, Object operation, byte[] encodedOperation) {
		if (this.monitorPdus) {
			Platform.runLater(() -> {
				this.pduInspectorManager.addPdu(new SlePdu(operation, encodedOperation, "IN", true, "<UNKNOWN>"));
			});
		}
	}

	public void deactivate() {
		// Deregister log console
		this.pduInspectorManager.deactivate();
	}
}
