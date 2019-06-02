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

package eu.dariolucia.ccsds.sle.utlfx.manager;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.application.ApplicationConfiguration;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.EventHandler;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.util.Callback;

public class SiLogManager {

	private static final Logger LOG = Logger.getLogger(SiLogManager.class.getName());

	private final TableView<LogRecord> siLogTableView;

	private final String siid;

	private volatile boolean monitorLogs = true;

	private final AtomicInteger maxLogsToShow = new AtomicInteger(ApplicationConfiguration.instance().getMaxLogs());

	private final Callback<ApplicationConfiguration, Void> updateConfigurationCallback = new Callback<ApplicationConfiguration, Void>() {
		@Override
		public Void call(ApplicationConfiguration param) {
			maxLogsToShow.set(param.getMaxLogs());
			return null;
		}
	};

	protected Handler siLogHandler = new Handler() {
		@Override
		public void publish(LogRecord record) {
			if (monitorLogs && record.getMessage().startsWith(siid)) {
				Platform.runLater(() -> {
					int toBeRemoved = siLogTableView.getItems().size() - maxLogsToShow.get();
					if (toBeRemoved > 0) {
						siLogTableView.getItems().remove(0, toBeRemoved);
					}
					siLogTableView.getItems().add(record);
				});
			}
		}

		@Override
		public void flush() {
			// Nothing to do
		}

		@Override
		public void close() throws SecurityException {
			// Nothing to do
		}
	};

	@SuppressWarnings("unchecked")
	public SiLogManager(String siid, TableView<LogRecord> siLogTableView) {
		this.siLogTableView = siLogTableView;
		this.siid = siid;

		// Log table renderer
		((TableColumn<LogRecord, String>) siLogTableView.getColumns().get(0))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(new Date(o.getValue().getMillis()).toString()));
		((TableColumn<LogRecord, String>) siLogTableView.getColumns().get(1))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().getLevel().getName()));
		((TableColumn<LogRecord, String>) siLogTableView.getColumns().get(2))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(buildTableMessage(o.getValue())));

		// Fix column size and autoresize
		siLogTableView.getColumns().get(0).prefWidthProperty().bind(siLogTableView.widthProperty().divide(6));
		siLogTableView.getColumns().get(1).prefWidthProperty().bind(siLogTableView.widthProperty().divide(6));
		siLogTableView.getColumns().get(2).prefWidthProperty()
				.bind(siLogTableView.widthProperty().divide(3).multiply(2).subtract(24));

		// Row factory with double click use for details inspection
		EventHandler<? super MouseEvent> logRecordDetailsDblClickHandler = new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() == 2) {
					DialogUtils.showLogRecordDetails("Details", ((TableRow<LogRecord>) event.getSource()).getItem());
				}
			}
		};
		this.siLogTableView.setRowFactory(tv -> {
			TableRow<LogRecord> r = new TableRow<>();
			r.setOnMouseClicked(logRecordDetailsDblClickHandler);
			return r;
		});

		// Cell factory
		((TableColumn<LogRecord, String>) siLogTableView.getColumns().get(1)).setCellFactory(column -> {
			return new TableCell<LogRecord, String>() {
				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					if (item != null && !empty && !isEmpty()) {
						setText(item);
						if (item.equals(Level.SEVERE.getName())) {
							setTextFill(Color.DARKRED);
							setStyle("-fx-font-weight: bold");
						} else if (item.equals(Level.WARNING.getName())) {
							setTextFill(Color.DARKORANGE);
							setStyle("-fx-font-weight: bold");
						} else {
							setTextFill(Color.BLACK);
							setStyle("");
						}
					} else {
						setText("");
						setGraphic(null);
					}
				}
			};
		});

		Logger parentLog = Logger.getLogger("eu.dariolucia.ccsds.sle.utl");
		parentLog.addHandler(this.siLogHandler);

		ApplicationConfiguration.instance().register(this.updateConfigurationCallback);
	}

	public void clear() {
		this.siLogTableView.getItems().removeAll(this.siLogTableView.getItems());
	}

	public void saveSiLogs() {
		FileChooser fc = new FileChooser();
		fc.setTitle("Save logs to...");
		File f = fc.showSaveDialog(this.siLogTableView.getScene().getWindow());
		if (f != null) {
			try {
				if (!f.exists()) {
					f.createNewFile();
				}
				PrintStream ps = new PrintStream(f);
				for (LogRecord lr : this.siLogTableView.getItems()) {
					ps.println(new Date(lr.getMillis()).toString() + "\t" + lr.getLevel().getName() + "\t"
							+ lr.getMessage());
				}
				ps.flush();
				ps.close();
				DialogUtils.showInfo("File saved", "Logs successfully saved to " + f.getAbsolutePath());
			} catch (IOException e1) {
				LOG.log(Level.SEVERE, "Error while saving logs to " + f.getAbsolutePath(), e1);
				DialogUtils.showError("Cannot save file to " + f.getAbsolutePath(), "Error while saving logs to "
						+ f.getAbsolutePath() + ", check the related log entry for the detailed error");
			}
		}
	}

	protected String buildTableMessage(LogRecord value) {
		String theMessage = value.getMessage().substring(value.getMessage().indexOf(':') + 1).trim();
		if (value.getThrown() != null) {
			if (value.getThrown().getMessage() != null) {
				return theMessage + " (Exception message: " + value.getThrown().getMessage() + ")";
			} else {
				return theMessage + " (Exception)";
			}
		} else {
			return theMessage;
		}
	}

	public void deactivate() {
		Logger parentLog = Logger.getLogger("eu.dariolucia.ccsds.sle.utl");
		parentLog.removeHandler(this.siLogHandler);
		clear();
		ApplicationConfiguration.instance().deregister(this.updateConfigurationCallback);
	}

	public void setMonitorLog(boolean selected) {
		this.monitorLogs = selected;
	}
}
