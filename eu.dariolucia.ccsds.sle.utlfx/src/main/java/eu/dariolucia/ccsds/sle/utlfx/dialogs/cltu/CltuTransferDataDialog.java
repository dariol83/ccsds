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

package eu.dariolucia.ccsds.sle.utlfx.dialogs.cltu;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.cltu.CltuTransferDataDialog.CltuTransferDataDialogResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.StageStyle;

public class CltuTransferDataDialog extends Dialog<CltuTransferDataDialogResult> {

	private static final Logger LOG = Logger.getLogger(CltuTransferDataDialog.class.getName());
	
	private static CltuTransferDataDialogResult LAST_VALUES = new CltuTransferDataDialogResult(null, null, null, null, new byte[0], false);
	
	private TextField cltuIdText;
	private TextField startTimeText;
	private TextField endTimeText;
	
	private TextField delayMsecText;
	private TextField dataText;
	private CheckBox requestNotificationCheckBox;
	
	private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS");

	public CltuTransferDataDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Transfer Data");
		setHeaderText("Invoke a TRANSFER DATA operation");
		setGraphic(null);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		cltuIdText = new TextField();
		cltuIdText.setPromptText("1");
		cltuIdText.setPrefWidth(100);
		cltuIdText.setTooltip(
				new Tooltip("CLTU invocation ID (long)"));
		startTimeText = new TextField();
		startTimeText.setPromptText("yyyy-mm-ddThh:mm:ss.SSS");
		startTimeText.setPrefWidth(200);
		startTimeText.setTooltip(
				new Tooltip("Earliest radiation time (UTC) either empty (for VOID) or in the format yyyy-mm-ddThh:mm:ss.SSS"));
		endTimeText = new TextField();
		endTimeText.setPromptText("yyyy-mm-ddThh:mm:ss.SSS");
		endTimeText.setPrefWidth(200);
		endTimeText.setTooltip(
				new Tooltip("Latest radiation time (UTC) either empty (for VOID) or in the format yyyy-mm-ddThh:mm:ss.SSS"));
		delayMsecText = new TextField();
		delayMsecText.setPromptText("0");
		delayMsecText.setPrefWidth(100);
		delayMsecText.setTooltip(
				new Tooltip("Maximum delay in microseconds (long)"));
		dataText = new TextField();
		dataText.setPromptText("");
		dataText.setPrefWidth(300);
		dataText.setTooltip(
				new Tooltip("Data payload"));
		requestNotificationCheckBox = new CheckBox();
		requestNotificationCheckBox.setText("");
		requestNotificationCheckBox.setMaxWidth(180);
		requestNotificationCheckBox.setTooltip(
				new Tooltip("Select if the notification for CLTU radiation is requested"));
		
		grid.add(new Label("CLTU ID"), 0, 0);
		grid.add(cltuIdText, 1, 0);
		grid.add(new Label("Earliest rad. start time"), 0, 1);
		grid.add(startTimeText, 1, 1);
		grid.add(new Label("Latest rad. start time"), 0, 2);
		grid.add(endTimeText, 1, 2);
		grid.add(new Label("Max. delay"), 0, 3);
		grid.add(delayMsecText, 1, 3);
		grid.add(new Label("Data"), 0, 4);
		grid.add(dataText, 1, 4);
		grid.add(new Label("Notification"), 0, 5);
		grid.add(requestNotificationCheckBox, 1, 5);
		
		cltuIdText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		delayMsecText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		dataText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		startTimeText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		endTimeText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		
		getDialogPane().setContent(grid);
		
		// Restore last values
		if(LAST_VALUES.cltuId != null) {
			cltuIdText.setText(String.valueOf(LAST_VALUES.cltuId.longValue()));
		}
		if(LAST_VALUES.maxDelay != null) {
			delayMsecText.setText(String.valueOf(LAST_VALUES.maxDelay.intValue()));
		}
		requestNotificationCheckBox.setSelected(LAST_VALUES.notification);
		if(LAST_VALUES.data != null) {
			dataText.setText(DatatypeConverter.printHexBinary(LAST_VALUES.data));
		}
		if(LAST_VALUES.startTime != null) {
			startTimeText.setText(formatter.format(LAST_VALUES.startTime));
		}
		if(LAST_VALUES.endTime != null) {
			endTimeText.setText(formatter.format(LAST_VALUES.endTime));
		}
		
		Platform.runLater(() -> {
			cltuIdText.requestFocus();
			validate();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private CltuTransferDataDialogResult buildResult() {
		try {
			Long eiid = Long.parseLong(cltuIdText.getText());
			Long eid = Long.parseLong(delayMsecText.getText());
			byte[] eq = DatatypeConverter.parseHexBinary(dataText.getText());
			Date startTime = parseTime(startTimeText.getText());
			Date endTime = parseTime(endTimeText.getText());
			
			CltuTransferDataDialogResult res = new CltuTransferDataDialogResult(eiid, startTime, endTime, eid, eq, requestNotificationCheckBox.isSelected());
			LAST_VALUES = res;
			return res;
		} catch (ParseException | IllegalArgumentException e) {
			LOG.log(Level.WARNING, "Cannot build result from the dialog: this should not happen", e);
		}
		return null;
	}

	private Date parseTime(String text) throws ParseException {
		if(text.trim().isEmpty()) {
			return null;
		} else {
			return formatter.parse(text);
		}
	}
	
	private void validate() {
		boolean valid = true;

		try {
			parseTime(startTimeText.getText());
			startTimeText.setStyle("");
		} catch (NumberFormatException | ParseException e) {
			startTimeText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			parseTime(endTimeText.getText());
			endTimeText.setStyle("");
		} catch (NumberFormatException | ParseException e) {
			endTimeText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			Long.parseLong(cltuIdText.getText());
			cltuIdText.setStyle("");
		} catch (NumberFormatException e) {
			cltuIdText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			Long.parseLong(delayMsecText.getText());
			delayMsecText.setStyle("");
		} catch (NumberFormatException e) {
			delayMsecText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			DatatypeConverter.parseHexBinary(dataText.getText());
			dataText.setStyle("");
		} catch (IllegalArgumentException e) {
			dataText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		Node okButton = getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(!valid);
	}

	public static class CltuTransferDataDialogResult {
		public final Long cltuId;
		public final Date startTime;
		public final Date endTime;
		public final Long maxDelay;
		public final byte[] data;
		public final boolean notification;
		
		public CltuTransferDataDialogResult(Long cltuId, Date startTime, Date endTime, Long maxDelay, byte[] data,
				boolean notification) {
			super();
			this.cltuId = cltuId;
			this.startTime = startTime;
			this.endTime = endTime;
			this.maxDelay = maxDelay;
			this.data = data;
			this.notification = notification;
		}
	}
}
