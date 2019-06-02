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
import java.util.logging.LogRecord;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.sle.utlfx.manager.SiLogManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class LogInspectorController implements Initializable {

	// Log Inspector
	@FXML
	protected TableView<LogRecord> siLogTableView;

	@FXML
	private ToggleButton enableSiLogButton;

	@FXML
	private Button saveAsSiLogButton;

	@FXML
	private Button clearSiLogButton;

	@FXML
	protected TitledPane logInspectionTitledPane;

	// Log Manager - Log manager
	protected SiLogManager siLogManager;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/play-circle.png"));
			this.enableSiLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/save.png"));
			this.saveAsSiLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/trash-2.png"));
			this.clearSiLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/message-circle.png"));
			this.logInspectionTitledPane.setGraphic(new ImageView(image));
		}
	}

	public void activate(String siid) {
		// Register log console
		this.siLogManager = new SiLogManager(siid, this.siLogTableView);
	}

	public void deactivate() {
		// Deregister log console
		this.siLogManager.deactivate();
	}

	@FXML
	protected void enableSiLogToggleButtonSelected(ActionEvent e) {
		this.siLogManager.setMonitorLog(((ToggleButton) e.getSource()).isSelected());
	}

	@FXML
	protected void clearSiLogButtonSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Clear service instance logs",
				"Are you sure to remove all the collected log messages for the selected service instance?", null, null);
		if (result.get() == ButtonType.OK) {
			this.siLogManager.clear();
		}
	}

	@FXML
	protected void saveSiLogButtonSelected(ActionEvent e) {
		if (this.siLogManager != null) {
			this.siLogManager.saveSiLogs();
		}
	}

}
