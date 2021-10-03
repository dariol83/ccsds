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

import eu.dariolucia.ccsds.cfdp.entity.CfdpTransactionStatus;
import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntitySubscriber;
import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.ICfdpTransactionIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.MetadataRecvIndication;
import eu.dariolucia.ccsds.cfdp.entity.indication.TransactionIndication;
import eu.dariolucia.ccsds.cfdp.entity.request.*;
import eu.dariolucia.ccsds.cfdp.fx.application.CfdpFxTestTool;
import eu.dariolucia.ccsds.cfdp.fx.dialogs.DialogUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.util.Pair;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class MainController implements Initializable, ICfdpEntitySubscriber {

	private static final Logger LOG = Logger.getLogger(MainController.class.getName());

	private static final String LOG_FORMAT = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
	private static final String LOG_PACKAGE = "eu.dariolucia.ccsds.cfdp";
	private static final int MAX_LOG_RECORDS = 2000;

	@FXML
	private SplitPane mainSplitPane;

	// Toolbar

	@FXML
	private Button putRequestButton;
	@FXML
	private Button suspendButton;
	@FXML
	private Button resumeButton;
	@FXML
	private Button cancelButton;
	@FXML
	private Button reportButton;
	@FXML
	private Button promptNakButton;
	@FXML
	private Button keepAliveButton;

	// Main transaction table

	@FXML
	private TableView<CfdpTransactionItem> transactionTable;

	private final Map<Long, CfdpTransactionItem> transactionItemMap = new HashMap<>();

	@FXML
	private TableColumn<CfdpTransactionItem, Number> transactionIdColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, Number> sourceIdColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, Number> destinationIdColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, String> directionColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, String> sourceFileNameColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, String> destinationFileNameColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, Number> fileSizeColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, String> ackTypeColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, String> statusColumn;
	@FXML
	private TableColumn<CfdpTransactionItem, String> progressColumn;

	// Log part

	@FXML
	private Button saveAsLogButton;
	@FXML
	private Button clearLogButton;
	@FXML
	private TextArea logTextArea;
	@FXML
	public TitledPane logTitledPane;
	@FXML
	private ChoiceBox<Level> logLevelChoiceBox;

	private int logLines = 0;

	private Handler logHandler;

	// The CFDP entity

	private ICfdpEntity cfdpEntity;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.cfdpEntity = CfdpFxTestTool.getCfdpEntity();

		this.transactionTable.setPlaceholder(new Label("No transactions"));

		// Button graphics
		DialogUtils.attachImage(getClass(), this.saveAsLogButton, "/eu/dariolucia/ccsds/cfdp/fx/images/save.png");
		DialogUtils.attachImage(getClass(), this.clearLogButton, "/eu/dariolucia/ccsds/cfdp/fx/images/trash-2.png");

		// Register this subscriber to the CFDP entity
		this.cfdpEntity.register(this);

		// Button enablement bindings
		suspendButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		resumeButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		cancelButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		reportButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		promptNakButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));
		keepAliveButton.disableProperty().bind(Bindings.isEmpty(transactionTable.getSelectionModel().getSelectedItems()));

		// Register column factories
		transactionIdColumn.setCellValueFactory(o -> o.getValue().transactionIdProperty());
		sourceIdColumn.setCellValueFactory(o -> o.getValue().sourceIdProperty());
		destinationIdColumn.setCellValueFactory(o -> o.getValue().destinationIdProperty());
		directionColumn.setCellValueFactory(o -> o.getValue().directionProperty());
		sourceFileNameColumn.setCellValueFactory(o -> o.getValue().sourceFileNameProperty());
		destinationFileNameColumn.setCellValueFactory(o -> o.getValue().destinationFileNameProperty());
		fileSizeColumn.setCellValueFactory(o -> o.getValue().fileSizeProperty());
		statusColumn.setCellValueFactory(o -> o.getValue().stateProperty());
		progressColumn.setCellValueFactory(o -> o.getValue().progressProperty());
		ackTypeColumn.setCellValueFactory(o -> o.getValue().ackTypeProperty());

		// Set the log handler
		this.logHandler = new Handler() {
			@Override
			public void publish(LogRecord record) {
				appendLogLine(record);
			}

			@Override
			public void flush() { }

			@Override
			public void close() throws SecurityException { }
		};
		Logger.getLogger(LOG_PACKAGE).addHandler(this.logHandler);

		this.logTitledPane.expandedProperty().addListener((observableValue, aBoolean, t1) -> {
			if(!logTitledPane.isExpanded()) {
				mainSplitPane.setDividerPosition(0, 1.0);
			}
		});
		this.logHandler.setLevel(Level.INFO);
		this.logLevelChoiceBox.getItems().addAll(Level.SEVERE, Level.WARNING, Level.INFO, Level.FINE); // Rest not added, too many logs
		this.logLevelChoiceBox.getSelectionModel().select(Level.INFO);
		this.logLevelChoiceBox.getSelectionModel().selectedItemProperty().addListener((observableValue, level, t1) -> {
			logHandler.setLevel(t1);
		});
		// Ready to go
	}

	@FXML
	private void clearLogMenuItemSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Clear",
				"Are you sure to clear all collected application logs?", "Clear application logs",
				new ImageView(((ImageView) clearLogButton.getGraphic()).getImage()));
		if (result.isPresent() && result.get() == ButtonType.OK) {
			clearLogArea();
		}
	}

	private void clearLogArea() {
		this.logTextArea.clear();
	}

	@FXML
	private void saveLogsMenuItemSelected(ActionEvent e) {
		FileChooser fc = new FileChooser();
		fc.setTitle("Save logs to...");
		File f = fc.showSaveDialog(this.logTextArea.getScene().getWindow());
		if (f != null) {
			PrintStream ps = null;
			try {
				if (!f.exists()) {
					f.createNewFile();
				}
				ps = new PrintStream(f);
				ps.println(this.logTextArea.getText());
				ps.flush();
				LOG.log(Level.INFO, String.format("Logs exported to %s successfully", f.getAbsolutePath()));
				DialogUtils.showInfo("File saved", "Logs successfully saved to " + f.getAbsolutePath());
			} catch (IOException e1) {
				LOG.log(Level.SEVERE, String.format("Error while saving logs to %s", f.getAbsolutePath()), e1);
				DialogUtils.showError("Cannot save file to " + f.getAbsolutePath(), "Error while saving logs to "
						+ f.getAbsolutePath() + ", check the related log entry for the detailed error");
			} finally {
				if(ps != null) {
					ps.close();
				}
			}
		}
	}

	private void appendLogLine(LogRecord record) {
		String toAppend = String.format(LOG_FORMAT,
				new Date(record.getMillis()),
				record.getLevel().getLocalizedName(),
				record.getMessage());
		if(record.getLevel().intValue() >= logHandler.getLevel().intValue()) {
			Platform.runLater(() -> {
				++logLines;
				logTextArea.appendText(toAppend);
				while (logLines > MAX_LOG_RECORDS) {
					logTextArea.deleteText(0, logTextArea.getText().indexOf('\n') + 1);
					--logLines;
				}
			});
		}
	}

	@FXML
	private void aboutButtonSelected(ActionEvent e) {
		DialogUtils.showInfo(CfdpFxTestTool.NAME + " " + CfdpFxTestTool.VERSION, CfdpFxTestTool.NAME + " "
				+ CfdpFxTestTool.VERSION + "\n" + CfdpFxTestTool.AUTHOR + "\n\n" + CfdpFxTestTool.YEARS);
	}

	public void putRequestButtonSelected(ActionEvent actionEvent) {
		try {
			Pair<Node, PutRequestDialogController> putRequestDialogPair = PutRequestDialogController.createDialog(cfdpEntity);
			// Create the popup
			Dialog<ButtonType> d = new Dialog<>();
			d.setTitle("Put Request");
			d.initModality(Modality.APPLICATION_MODAL);
			d.initOwner(putRequestButton.getScene().getWindow());
			d.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
			d.getDialogPane().setContent(putRequestDialogPair.getKey());
			Button ok = (Button) d.getDialogPane().lookupButton(ButtonType.OK);
			putRequestDialogPair.getValue().bindOkButton(ok);
			Optional<ButtonType> result = d.showAndWait();
			if (result.isPresent() && result.get().equals(ButtonType.OK)) {
				cfdpEntity.request(putRequestDialogPair.getValue().createPutRequest());
			}
		} catch (IOException e) {
			if(LOG.isLoggable(Level.SEVERE)) {
				LOG.log(Level.SEVERE, String.format("Error while loading resource: %s", e.getMessage()), e);
			}
			DialogUtils.showError("Error while loading resource", "Cannot load resource: " + e.getMessage());
		}
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
		if (result.isPresent() && result.get() == ButtonType.OK) {
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
		if (result.isPresent() && result.get() == ButtonType.OK) {
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
		if (result.isPresent() && result.get() == ButtonType.OK) {
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
		if (result.isPresent() && result.get() == ButtonType.OK) {
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
		if (result.isPresent() && result.get() == ButtonType.OK) {
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
		if (result.isPresent() && result.get() == ButtonType.OK) {
			cfdpEntity.request(new KeepAliveRequest(item.getTransactionId()));
		}
	}

	@Override
	public void indication(ICfdpEntity emitter, ICfdpIndication indication) {

		Platform.runLater(() -> {
			updateTransaction(indication);
		});
	}

	private void updateTransaction(ICfdpIndication indication) {
		if(indication instanceof ICfdpTransactionIndication) {
			ICfdpTransactionIndication ind = (ICfdpTransactionIndication) indication;
			CfdpTransactionItem item = this.transactionItemMap.get(ind.getTransactionId());
			if(item == null) {
				// Create and add to map
				CfdpTransactionItem newItem = new CfdpTransactionItem(ind);
				this.transactionItemMap.put(ind.getTransactionId(), newItem);
				this.transactionTable.getItems().add(newItem);
			} else {
				// Update
				item.update(ind);
			}
		}
	}

	private static class CfdpTransactionItem {

		private final SimpleLongProperty sourceId = new SimpleLongProperty();
		private final SimpleLongProperty destinationId = new SimpleLongProperty();
		private final SimpleLongProperty transactionId = new SimpleLongProperty();
		private final SimpleStringProperty direction = new SimpleStringProperty("-");
		private final SimpleStringProperty sourceFileName = new SimpleStringProperty("");
		private final SimpleStringProperty destinationFileName = new SimpleStringProperty("");
		private final SimpleLongProperty fileSize = new SimpleLongProperty(0);
		private final SimpleStringProperty state = new SimpleStringProperty("N/A");
		private final SimpleStringProperty progress = new SimpleStringProperty("N/A");
		private final SimpleStringProperty ackType = new SimpleStringProperty("N/A");


		public CfdpTransactionItem(ICfdpTransactionIndication ind) {
			update(ind);
		}

		public SimpleStringProperty directionProperty() {
			return direction;
		}

		public SimpleStringProperty sourceFileNameProperty() {
			return sourceFileName;
		}

		public SimpleStringProperty destinationFileNameProperty() {
			return destinationFileName;
		}

		public SimpleLongProperty fileSizeProperty() {
			return fileSize;
		}

		public SimpleStringProperty stateProperty() {
			return state;
		}

		public SimpleStringProperty progressProperty() {
			return progress;
		}

		public SimpleLongProperty sourceIdProperty() {
			return sourceId;
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

		public SimpleStringProperty ackTypeProperty() {
			return ackType;
		}

		public void update(ICfdpTransactionIndication ind) {
			if(ind instanceof TransactionIndication) {
				String srcFileName = ((TransactionIndication) ind).getOriginatingRequest().getSourceFileName();
				String destFileName = ((TransactionIndication) ind).getOriginatingRequest().getDestinationFileName();
				if(srcFileName != null) {
					sourceFileName.setValue(srcFileName);
				}
				if(destFileName != null) {
					destinationFileName.setValue(destFileName);
				}
			} else if(ind instanceof MetadataRecvIndication) {
				String srcFileName = ((MetadataRecvIndication) ind).getSourceFileName();
				String destFileName = ((MetadataRecvIndication) ind).getDestinationFileName();
				if(srcFileName != null) {
					sourceFileName.setValue(srcFileName);
				}
				if(destFileName != null) {
					destinationFileName.setValue(destFileName);
				}
			}
			update(ind.getStatusReport());
		}

		private void update(CfdpTransactionStatus statusReport) {
			sourceId.setValue(statusReport.getSourceEntityId());
			destinationId.setValue(statusReport.getDestinationEntityId());
			transactionId.setValue(statusReport.getTransactionId());
			direction.setValue(statusReport.isDestination() ? "IN" : "OUT");
			fileSize.setValue(statusReport.getTotalFileSize());
			state.setValue(statusReport.getCfdpTransactionState().name());
			double progressPercentage = (double) statusReport.getProgress() / (double) statusReport.getTotalFileSize();
			progress.setValue((long) (progressPercentage * 100) + "%");
			ackType.setValue(statusReport.getTransmissionMode().toString());
		}
	}
}
