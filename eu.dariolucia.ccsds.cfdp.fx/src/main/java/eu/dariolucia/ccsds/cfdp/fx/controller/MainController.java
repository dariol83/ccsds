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

package eu.dariolucia.ccsds.cfdp.fx.controller;

import eu.dariolucia.ccsds.cfdp.fx.application.CfdpFxTestTool;
import eu.dariolucia.ccsds.cfdp.fx.dialogs.DialogUtils;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.Date;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class MainController implements Initializable  {

	private static final Logger LOG = Logger.getLogger(MainController.class.getName());

	public Button putRequestButton;
	public Button suspendButton;
	public Button resumeButton;
	public Button cancelButton;
	public Button reportButton;
	public Button promptNakButton;
	public Button keepAliveButton;

	@FXML
	private StackPane stackPane;

	@FXML
	private TableView<LogRecord> logTableView;

	@FXML
	private ToggleButton enableLogButton;

	@FXML
	private Button saveAsLogButton;

	@FXML
	private Button clearLogButton;

	@FXML
	private Pane emptyPane;

	@FXML
	private TitledPane logTitledPane;

	@FXML
	private Accordion logAccordion;

	@FXML
	private SplitPane mainSplitPane;

	private volatile boolean collectLogs = true;

	@SuppressWarnings("unchecked")
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		// Log table renderer
		((TableColumn<LogRecord, String>) logTableView.getColumns().get(0))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(new Date(o.getValue().getMillis()).toString()));
		((TableColumn<LogRecord, String>) logTableView.getColumns().get(1))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().getLevel().getName()));
		((TableColumn<LogRecord, String>) logTableView.getColumns().get(2))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(buildTableMessage(o.getValue())));

		// Fix column size and autoresize
		logTableView.getColumns().get(0).prefWidthProperty().bind(logTableView.widthProperty().divide(8));
		logTableView.getColumns().get(1).prefWidthProperty().bind(logTableView.widthProperty().divide(8));
		logTableView.getColumns().get(2).prefWidthProperty()
				.bind(logTableView.widthProperty().divide(4).multiply(3).subtract(24));

		// Button graphics
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/cfdp/fx/images/play-circle.png"));
			this.enableLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/cfdp/fx/images/save.png"));
			this.saveAsLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/cfdp/fx/images/trash-2.png"));
			this.clearLogButton.setGraphic(new ImageView(image));
		}

	}

	protected String buildTableMessage(LogRecord value) {
		String theMessage = value.getMessage().trim();
		if (value.getThrown() != null) {
			return theMessage + " - Exception message: " + value.getThrown().getMessage();
		} else {
			return theMessage;
		}
	}

	@FXML
	private void collectLogsMenuItemSelected(ActionEvent e) {
		this.collectLogs = ((ToggleButton) e.getSource()).isSelected();
	}

	@FXML
	private void clearLogMenuItemSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Clear",
				"Are you sure to clear all collected application logs?", "Clear application logs",
				new ImageView(((ImageView) clearLogButton.getGraphic()).getImage()));
		if (result.get() == ButtonType.OK) {
			clearLogArea();
		}
	}

	private void clearLogArea() {
		this.logTableView.getItems().removeAll(this.logTableView.getItems());
	}

	@FXML
	private void saveLogsMenuItemSelected(ActionEvent e) {

	}

	@FXML
	private void aboutButtonSelected(ActionEvent e) {
		DialogUtils.showInfo(CfdpFxTestTool.NAME + " " + CfdpFxTestTool.VERSION, CfdpFxTestTool.NAME + " "
				+ CfdpFxTestTool.VERSION + "\n" + CfdpFxTestTool.AUTHOR + "\n\n" + CfdpFxTestTool.YEARS);
	}

	public void putRequestButtonSelected(ActionEvent actionEvent) {
	}

	public void suspendButtonSelected(ActionEvent actionEvent) {
	}

	public void resumeButtonSelected(ActionEvent actionEvent) {
	}

	public void cancelButtonSelected(ActionEvent actionEvent) {
	}

	public void reportButtonSelected(ActionEvent actionEvent) {
	}

	public void promptNakButtonSelected(ActionEvent actionEvent) {
	}

	public void keepAliveButtonSelected(ActionEvent actionEvent) {
	}
}
