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

package eu.dariolucia.ccsds.sle.utlfx.dialogs;

import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.application.ApplicationConfiguration;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.SettingsDialog.SettingsDialogResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.StageStyle;

public class SettingsDialog extends Dialog<SettingsDialogResult> {

	private static final Logger LOG = Logger.getLogger(SettingsDialog.class.getName());
	
	private TextField maxNumLogEntriesText;
	private TextField maxNumPdusText;
	private CheckBox enableDebugInformation;

	public SettingsDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Settings");
		setHeaderText("Change the settings of the application");
		setGraphic(new ImageView(new Image(getClass()
				.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/settings-2.png"))));
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		maxNumLogEntriesText = new TextField();
		maxNumLogEntriesText.setText(String.valueOf(ApplicationConfiguration.instance().getMaxLogs()));
		maxNumLogEntriesText.setTooltip(
				new Tooltip("Maximum number of log entries to be kept per Service Instance (positive integer > 0)"));
		maxNumPdusText = new TextField();
		maxNumPdusText.setText(String.valueOf(ApplicationConfiguration.instance().getMaxPdus()));
		maxNumPdusText.setTooltip(
				new Tooltip("Maximum number of PDUs to be kept per Service Instance (positive integer > 0)"));
		enableDebugInformation = new CheckBox("Enable debug information");
		enableDebugInformation.setSelected(ApplicationConfiguration.instance().isDebugActive());
		enableDebugInformation.setTooltip(
				new Tooltip("Select to activate debug information"));

		grid.add(new Label("Max. # log entries"), 0, 0);
		grid.add(maxNumLogEntriesText, 1, 0);
		grid.add(new Label("Max. # PDUs"), 0, 1);
		grid.add(maxNumPdusText, 1, 1);
		grid.add(enableDebugInformation, 0, 2);
		grid.add(new Label(), 1, 2);

		maxNumLogEntriesText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		maxNumPdusText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});

		getDialogPane().setContent(grid);

		Platform.runLater(() -> {
			maxNumLogEntriesText.requestFocus();
			validate();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private SettingsDialogResult buildResult() {
		try {
			return new SettingsDialogResult(
					Integer.parseInt(maxNumLogEntriesText.getText()),
					Integer.parseInt(maxNumPdusText.getText()),
					enableDebugInformation.isSelected()
			);
		} catch (NumberFormatException e) {
			LOG.log(Level.WARNING, "Cannot build result from the dialog: this should not happen", e);
		}
		return null;
	}

	private void validate() {
		boolean valid = true;

		try {
			if (Integer.parseInt(maxNumLogEntriesText.getText()) <= 0) {
				throw new NumberFormatException("0 or negative number");
			}
			maxNumLogEntriesText.setStyle("");
		} catch (NumberFormatException e) {
			maxNumLogEntriesText.setStyle("-utlfx-background-color: red");
			valid = false;
		}

		try {
			if (Integer.parseInt(maxNumPdusText.getText()) <= 0) {
				throw new NumberFormatException("0 or negative number");
			}
			maxNumPdusText.setStyle("");
		} catch (NumberFormatException e) {
			maxNumPdusText.setStyle("-utlfx-background-color: red");
			valid = false;
		}

		Node okButton = getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(!valid);
	}

	public static class SettingsDialogResult {
		public final int maxNumLogEntries;
		public final int maxNumPdus;
		public final boolean enableDebug;

		public SettingsDialogResult(int maxNumLogEntries, int maxNumPdus, boolean enableDebug) {
			super();
			this.maxNumLogEntries = maxNumLogEntries;
			this.maxNumPdus = maxNumPdus;
			this.enableDebug = enableDebug;
		}
	}
}
