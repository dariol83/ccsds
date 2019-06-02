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

package eu.dariolucia.ccsds.sle.utlfx.dialogs.rcf;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.rcf.RcfStartDialog.RcfStartDialogResult;
import eu.dariolucia.ccsds.sle.utl.si.GVCID;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.StageStyle;

public class RcfStartDialog extends Dialog<RcfStartDialogResult> {
	
	private static final Logger LOG = Logger.getLogger(RcfStartDialog.class.getName());
	
	private static RcfStartDialogResult LAST_VALUES = new RcfStartDialogResult(null, null, null);
	
	private TextField startTimeText;
	private TextField endTimeText;
	private TextField spacecraftText;
	private TextField tfvnText;
	private TextField channelText;
	
	private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS");

	public RcfStartDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Start");
		setHeaderText("Invoke a START operation");
		setGraphic(null);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		this.formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		startTimeText = new TextField();
		startTimeText.setPromptText("yyyy-mm-ddThh:mm:ss.SSS");
		startTimeText.setPrefWidth(200);
		startTimeText.setTooltip(
				new Tooltip("Start time (UTC) either empty (for VOID) or in the format yyyy-mm-ddThh:mm:ss.SSS"));
		endTimeText = new TextField();
		endTimeText.setPromptText("yyyy-mm-ddThh:mm:ss.SSS");
		endTimeText.setPrefWidth(200);
		endTimeText.setTooltip(
				new Tooltip("End time (UTC) either empty (for VOID) or in the format yyyy-mm-ddThh:mm:ss.SSS"));
		spacecraftText = new TextField();
		spacecraftText.setPromptText("");
		spacecraftText.setMaxWidth(80);
		spacecraftText.setTooltip(
				new Tooltip("Spacecraft ID (integer)"));
		tfvnText = new TextField();
		tfvnText.setPromptText("");
		tfvnText.setMaxWidth(80);
		tfvnText.setTooltip(
				new Tooltip("Transfer Frame Version Number (0 for TM, 1 for AOS, 2 for Proximity-1, 3 for Unified TM)"));
		channelText = new TextField();
		channelText.setPromptText("");
		channelText.setMaxWidth(80);
		channelText.setTooltip(
				new Tooltip("Virtual channel ID (integer) or empty to indicate the master channel"));
		
		
		grid.add(new Label("Start time"), 0, 0);
		grid.add(startTimeText, 1, 0);
		grid.add(new Label("End time"), 0, 1);
		grid.add(endTimeText, 1, 1);
		grid.add(new Label("Spacecraft ID"), 0, 2);
		grid.add(spacecraftText, 1, 2);
		grid.add(new Label("Transfer Frame Version Number"), 0, 3);
		grid.add(tfvnText, 1, 3);
		grid.add(new Label("Virtual Channel ID"), 0, 4);
		grid.add(channelText, 1, 4);

		startTimeText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		endTimeText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		spacecraftText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		tfvnText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		channelText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});

		getDialogPane().setContent(grid);
		
		// Restore last values
		if(LAST_VALUES.startTime != null) {
			startTimeText.setText(formatter.format(LAST_VALUES.startTime));
		}
		if(LAST_VALUES.endTime != null) {
			endTimeText.setText(formatter.format(LAST_VALUES.endTime));
		}
		if(LAST_VALUES.gvcid != null) {
			spacecraftText.setText(String.valueOf(LAST_VALUES.gvcid.getSpacecraftId()));
			tfvnText.setText(String.valueOf(LAST_VALUES.gvcid.getTransferFrameVersionNumber()));
			if(LAST_VALUES.gvcid.getVirtualChannelId() != null) {
				channelText.setText(String.valueOf(LAST_VALUES.gvcid.getVirtualChannelId().intValue()));
			}
		}
		
		Platform.runLater(() -> {
			spacecraftText.requestFocus();
			validate();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private RcfStartDialogResult buildResult() {
		try {
			Date startTime = parseTime(startTimeText.getText());
			Date endTime = parseTime(endTimeText.getText());
			GVCID gvcid = parseGvcid(spacecraftText.getText(), tfvnText.getText(), channelText.getText());
			RcfStartDialogResult res = new RcfStartDialogResult(startTime, endTime, gvcid);
			LAST_VALUES = res;
			return res;
		} catch (NumberFormatException | ParseException e) {
			LOG.log(Level.WARNING, "Cannot build result from the dialog: this should not happen", e);
		}
		return null;
	}

	private GVCID parseGvcid(String scId, String tfvn, String vcId) {
		int spacecrafId = Integer.parseInt(scId);
		if(spacecrafId < 0) {
			throw new NumberFormatException("Spacecraft ID cannot be < 0");
		}
		
		int transferFrameVn = Integer.parseInt(tfvn);
		if(transferFrameVn < 0 || transferFrameVn > 3) {
			throw new NumberFormatException("TFVN can be only 0, 1, 2 or 3 (currently supported by CCSDS standards)");
		}
		
		if(vcId.trim().isEmpty()) {
			return new GVCID(spacecrafId, transferFrameVn, null);
		} else {
			int virtualChId = Integer.parseInt(vcId);
			if(virtualChId < 0) {
				throw new NumberFormatException("Virtual Channel ID cannot be < 0");
			}
			return new GVCID(spacecrafId, transferFrameVn, virtualChId);
		}
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
			int spacecrafId = Integer.parseInt(spacecraftText.getText());
			if(spacecrafId < 0) {
				throw new NumberFormatException("Spacecraft ID cannot be < 0");
			}
			spacecraftText.setStyle("");
		} catch (NumberFormatException e) {
			spacecraftText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			int verNum = Integer.parseInt(tfvnText.getText());
			if(verNum < 0 || verNum > 3) {
				throw new NumberFormatException("TFVN can be only 0, 1, 2 or 3 (currently supported by CCSDS standards)");
			}
			tfvnText.setStyle("");
		} catch (NumberFormatException e) {
			tfvnText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		try {
			if(!channelText.getText().trim().isEmpty()) {
				int verNum = Integer.parseInt(channelText.getText());
				if(verNum < 0) {
					throw new NumberFormatException("Virtual Channel ID cannot be < 0");
				}
			}
			channelText.setStyle("");
		} catch (NumberFormatException e) {
			channelText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		Node okButton = getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(!valid);
	}

	public static class RcfStartDialogResult {
		public final Date startTime;
		public final Date endTime;
		public final GVCID gvcid;
		
		public RcfStartDialogResult(Date startTime, Date endTime, GVCID gvcid) {
			super();
			this.startTime = startTime;
			this.endTime = endTime;
			this.gvcid = gvcid;
		}		
	}
}
