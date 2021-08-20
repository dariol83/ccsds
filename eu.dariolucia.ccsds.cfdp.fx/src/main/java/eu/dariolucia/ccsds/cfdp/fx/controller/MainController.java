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

import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntitySubscriber;
import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.*;
import eu.dariolucia.ccsds.cfdp.filestore.impl.FilesystemBasedFilestore;
import eu.dariolucia.ccsds.cfdp.fx.application.CfdpFxTestTool;
import eu.dariolucia.ccsds.cfdp.fx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.cfdp.mib.Mib;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.ut.UtLayerException;
import eu.dariolucia.ccsds.cfdp.ut.impl.TcpLayer;
import eu.dariolucia.ccsds.cfdp.ut.impl.UdpLayer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleLongProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;

public class MainController implements Initializable, ICfdpEntitySubscriber {

	private static final Logger LOG = Logger.getLogger(MainController.class.getName());

	public Button putRequestButton;
	public Button suspendButton;
	public Button resumeButton;
	public Button cancelButton;
	public Button reportButton;
	public Button promptNakButton;
	public Button keepAliveButton;

	public TableView<CfdpTransactionItem> transactionTable;

	// Indication log part

	@FXML
	private TableView<ICfdpIndication> logTableView;

	@FXML
	private Button saveAsLogButton;

	@FXML
	private Button clearLogButton;


	private ICfdpEntity cfdpEntity;

	@SuppressWarnings("unchecked")
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		// Log table renderer
		((TableColumn<ICfdpIndication, String>) logTableView.getColumns().get(0))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(buildTableMessage(o.getValue())));

		// Button graphics
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

		// Register this subscriber to the CFDP entity
		CfdpFxTestTool.getCfdpEntity().register(this);

		// Button enablement bindings
		suspendButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		resumeButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		cancelButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		reportButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		promptNakButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		keepAliveButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));

		// Ready to go
	}

	protected String buildTableMessage(ICfdpIndication value) {
		return value.toString();
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
		CfdpTransactionItem item = this.transactionTable.getSelectionModel().getSelectedItem();
		if(item == null) {
			return;
		}
		Optional<ButtonType> result = DialogUtils.showConfirmation("Confirm request",
				"Do you want to issue a Suspend Request to transaction " + item.getTransactionId() + "?" ,
				"New request for transaction " + item.getTransactionId(),
				null);
		if (result.get() == ButtonType.OK) {
			cfdpEntity.request(new SuspendRequest(item.getTransactionId()));
		}
	}

	public void resumeButtonSelected(ActionEvent actionEvent) {
		CfdpTransactionItem item = this.transactionTable.getSelectionModel().getSelectedItem();
		if(item == null) {
			return;
		}
		Optional<ButtonType> result = DialogUtils.showConfirmation("Confirm request",
				"Do you want to issue a Resume Request to transaction " + item.getTransactionId() + "?" ,
				"New request for transaction " + item.getTransactionId(),
				null);
		if (result.get() == ButtonType.OK) {
			cfdpEntity.request(new ResumeRequest(item.getTransactionId()));
		}
	}

	public void cancelButtonSelected(ActionEvent actionEvent) {
		CfdpTransactionItem item = this.transactionTable.getSelectionModel().getSelectedItem();
		if(item == null) {
			return;
		}
		Optional<ButtonType> result = DialogUtils.showConfirmation("Confirm request",
				"Do you want to issue a Cancel Request to transaction " + item.getTransactionId() + "?" ,
				"New request for transaction " + item.getTransactionId(),
				null);
		if (result.get() == ButtonType.OK) {
			cfdpEntity.request(new CancelRequest(item.getTransactionId()));
		}
	}

	public void reportButtonSelected(ActionEvent actionEvent) {
		CfdpTransactionItem item = this.transactionTable.getSelectionModel().getSelectedItem();
		if(item == null) {
			return;
		}
		Optional<ButtonType> result = DialogUtils.showConfirmation("Confirm request",
				"Do you want to issue a Report Request to transaction " + item.getTransactionId() + "?" ,
				"New request for transaction " + item.getTransactionId(),
				null);
		if (result.get() == ButtonType.OK) {
			cfdpEntity.request(new ReportRequest(item.getTransactionId()));
		}
	}

	public void promptNakButtonSelected(ActionEvent actionEvent) {
		CfdpTransactionItem item = this.transactionTable.getSelectionModel().getSelectedItem();
		if(item == null) {
			return;
		}
		Optional<ButtonType> result = DialogUtils.showConfirmation("Confirm request",
				"Do you want to issue a Prompt Nak Request to transaction " + item.getTransactionId() + "?" ,
				"New request for transaction " + item.getTransactionId(),
				null);
		if (result.get() == ButtonType.OK) {
			cfdpEntity.request(new PromptNakRequest(item.getTransactionId()));
		}
	}

	public void keepAliveButtonSelected(ActionEvent actionEvent) {
		CfdpTransactionItem item = this.transactionTable.getSelectionModel().getSelectedItem();
		if(item == null) {
			return;
		}
		Optional<ButtonType> result = DialogUtils.showConfirmation("Confirm request",
				"Do you want to issue a Keep Alive Request to transaction " + item.getTransactionId() + "?" ,
				"New request for transaction " + item.getTransactionId(),
				null);
		if (result.get() == ButtonType.OK) {
			cfdpEntity.request(new KeepAliveRequest(item.getTransactionId()));
		}
	}

	@Override
	public void indication(ICfdpEntity emitter, ICfdpIndication indication) {
		Platform.runLater(() -> {
			logTableView.getItems().add(indication);
			updateTransaction(indication);
		});
	}

	private void updateTransaction(ICfdpIndication indication) {
		// TODO
	}

	private class CfdpTransactionItem {

		private final SimpleLongProperty sourceId = new SimpleLongProperty();
		private final SimpleLongProperty destinationId = new SimpleLongProperty();
		private final SimpleLongProperty transactionId = new SimpleLongProperty();

		public long getSourceId() {
			return sourceId.get();
		}

		public SimpleLongProperty sourceIdProperty() {
			return sourceId;
		}

		public long getDestinationId() {
			return destinationId.get();
		}

		public SimpleLongProperty destinationIdProperty() {
			return destinationId;
		}

		public long getTransactionId() {
			return transactionId.get();
		}

		public SimpleLongProperty transactionIdProperty() {
			return transactionId;
		}
	}
}
