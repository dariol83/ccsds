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

package eu.dariolucia.ccsds.sle.utlfx.dialogs.raf;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.raf.RafStartDialog.RafStartDialogResult;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafRequestedFrameQualityEnum;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.StageStyle;

public class RafStartDialog extends Dialog<RafStartDialogResult> {

	private static final Logger LOG = Logger.getLogger(RafStartDialog.class.getName());
	
	private static RafStartDialogResult LAST_VALUES = new RafStartDialogResult(null, null, null);
	
	private TextField startTimeText;
	private TextField endTimeText;
	private ComboBox<RafRequestedFrameQualityEnum> requestedFrameQualityCombo;
	
	private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSS");

	public RafStartDialog() {
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

		requestedFrameQualityCombo = new ComboBox<>(FXCollections.observableArrayList(RafRequestedFrameQualityEnum.values()));
		requestedFrameQualityCombo.setTooltip(new Tooltip("The requested frame quality"));
		requestedFrameQualityCombo.getSelectionModel().select(0);
		
		grid.add(new Label("Start time"), 0, 0);
		grid.add(startTimeText, 1, 0);
		grid.add(new Label("End time"), 0, 1);
		grid.add(endTimeText, 1, 1);
		grid.add(new Label("Requested frame quality"), 0, 2);
		grid.add(requestedFrameQualityCombo, 1, 2);
		
		startTimeText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		endTimeText.textProperty().addListener((observable, oldValue, newValue) -> {
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
		if(LAST_VALUES.requestedFrameQuality != null) {
			requestedFrameQualityCombo.getSelectionModel().select(LAST_VALUES.requestedFrameQuality);
		}
		
		Platform.runLater(() -> {
			requestedFrameQualityCombo.requestFocus();
			validate();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private RafStartDialogResult buildResult() {
		try {
			Date startTime = parseTime(startTimeText.getText());
			Date endTime = parseTime(endTimeText.getText());
			RafRequestedFrameQualityEnum quality = requestedFrameQualityCombo.getSelectionModel().getSelectedItem();
			RafStartDialogResult res = new RafStartDialogResult(startTime, endTime, quality);
			LAST_VALUES = res;
			return res;
		} catch (NumberFormatException | ParseException e) {
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
		
		Node okButton = getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(!valid);
	}

	public static class RafStartDialogResult {
		public final Date startTime;
		public final Date endTime;
		public final RafRequestedFrameQualityEnum requestedFrameQuality;
		
		public RafStartDialogResult(Date startTime, Date endTime, RafRequestedFrameQualityEnum requestedFrameQuality) {
			super();
			this.startTime = startTime;
			this.endTime = endTime;
			this.requestedFrameQuality = requestedFrameQuality;
		}		
	}
}
