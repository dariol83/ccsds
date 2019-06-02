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

import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.cltu.CltuThrowEventDialog.CltuThrowEventDialogResult;
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

public class CltuThrowEventDialog extends Dialog<CltuThrowEventDialogResult> {

	private static final Logger LOG = Logger.getLogger(CltuThrowEventDialog.class.getName());
	
	private static CltuThrowEventDialogResult LAST_VALUES = new CltuThrowEventDialogResult(null, null, null, false);
	
	private TextField eventInvocationIdText;
	private TextField eventIdText;
	private TextField eventQualifierText;
	private CheckBox hexDumpCheckBox;
	
	public CltuThrowEventDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Throw Event");
		setHeaderText("Invoke a THROW EVENT operation");
		setGraphic(null);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		eventInvocationIdText = new TextField();
		eventInvocationIdText.setPromptText("1");
		eventInvocationIdText.setPrefWidth(100);
		eventInvocationIdText.setTooltip(
				new Tooltip("Event invocation ID (long)"));
		eventIdText = new TextField();
		eventIdText.setPromptText("1");
		eventIdText.setPrefWidth(100);
		eventIdText.setTooltip(
				new Tooltip("Event ID (integer)"));
		eventQualifierText = new TextField();
		eventQualifierText.setPromptText("");
		eventQualifierText.setPrefWidth(300);
		eventQualifierText.setTooltip(
				new Tooltip("Event qualifier"));
		hexDumpCheckBox = new CheckBox();
		hexDumpCheckBox.setText("As hex dump");
		hexDumpCheckBox.setMaxWidth(180);
		hexDumpCheckBox.setTooltip(
				new Tooltip("Select if the event qualifier is expressed as an octet string in hex format"));
		
		grid.add(new Label("Event invocation ID"), 0, 0);
		grid.add(eventInvocationIdText, 1, 0);
		grid.add(new Label("Event ID"), 0, 1);
		grid.add(eventIdText, 1, 1);
		grid.add(new Label("Event qualifier"), 0, 2);
		grid.add(eventQualifierText, 1, 2);
		grid.add(new Label(""), 0, 3);
		grid.add(hexDumpCheckBox, 1, 3);
		
		eventInvocationIdText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		eventIdText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		eventQualifierText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		hexDumpCheckBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		
		getDialogPane().setContent(grid);
		
		// Restore last values
		if(LAST_VALUES.eiid != null) {
			eventInvocationIdText.setText(String.valueOf(LAST_VALUES.eiid.longValue()));
		}
		if(LAST_VALUES.eid != null) {
			eventIdText.setText(String.valueOf(LAST_VALUES.eid.intValue()));
		}
		if(LAST_VALUES.eq != null) {
			hexDumpCheckBox.setSelected(LAST_VALUES.hex);
			if(LAST_VALUES.hex) {
				eventQualifierText.setText(DatatypeConverter.printHexBinary(LAST_VALUES.eq));
			} else {
				eventQualifierText.setText(new String(LAST_VALUES.eq));
			}
		}
		
		Platform.runLater(() -> {
			eventQualifierText.requestFocus();
			validate();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private CltuThrowEventDialogResult buildResult() {
		try {
			Long eiid = Long.parseLong(eventInvocationIdText.getText());
			Integer eid = Integer.parseInt(eventIdText.getText());
			byte[] eq = null;
			if(hexDumpCheckBox.isSelected()) {
				eq = DatatypeConverter.parseHexBinary(eventQualifierText.getText());
			} else {
				eq = eventQualifierText.getText().getBytes(Charset.defaultCharset());
			}
			CltuThrowEventDialogResult res = new CltuThrowEventDialogResult(eiid, eid, eq, hexDumpCheckBox.isSelected());
			LAST_VALUES = res;
			return res;
		} catch (IllegalArgumentException e) {
			LOG.log(Level.WARNING, "Cannot build result from the dialog: this should not happen", e);
		}
		return null;
	}

	private void validate() {
		boolean valid = true;

		try {
			Long.parseLong(eventInvocationIdText.getText());
			eventInvocationIdText.setStyle("");
		} catch (NumberFormatException e) {
			eventInvocationIdText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			Integer.parseInt(eventIdText.getText());
			eventIdText.setStyle("");
		} catch (NumberFormatException e) {
			eventIdText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			if(hexDumpCheckBox.isSelected()) {
				DatatypeConverter.parseHexBinary(eventQualifierText.getText());
			} else {
				byte[] b = eventQualifierText.getText().getBytes(Charset.defaultCharset());
				if(b.length == 0) {
					throw new IllegalArgumentException("Empty string");
				}
			}
			eventQualifierText.setStyle("");
		} catch (IllegalArgumentException e) {
			eventQualifierText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		Node okButton = getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(!valid);
	}

	public static class CltuThrowEventDialogResult {
		public final Long eiid;
		public final Integer eid;
		public final byte[] eq;
		public final boolean hex;
		
		public CltuThrowEventDialogResult(Long eiid, Integer eid, byte[] eq, boolean hex) {
			this.eiid = eiid;
			this.eid = eid;
			this.eq = eq;
			this.hex = hex;
		}
	}
}
