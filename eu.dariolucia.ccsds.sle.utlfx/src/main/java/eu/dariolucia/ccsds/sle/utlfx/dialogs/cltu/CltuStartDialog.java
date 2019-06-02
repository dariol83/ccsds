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

import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.cltu.CltuStartDialog.CltuStartDialogResult;
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

public class CltuStartDialog extends Dialog<CltuStartDialogResult> {

	private static final Logger LOG = Logger.getLogger(CltuStartDialog.class.getName());
	
	private static CltuStartDialogResult LAST_VALUES = new CltuStartDialogResult(null);

	private TextField firstCltuIdText;
	
	public CltuStartDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Start");
		setHeaderText("Invoke a START operation");
		setGraphic(null);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));
		
		firstCltuIdText = new TextField();
		firstCltuIdText.setPromptText("");
		firstCltuIdText.setMaxWidth(80);
		firstCltuIdText.setTooltip(
				new Tooltip("First CLTU ID (integer) - Can be empty if CLTU version is 1"));
		
		grid.add(new Label("First CLTU ID"), 0, 0);
		grid.add(firstCltuIdText, 1, 0);

		firstCltuIdText.textProperty().addListener((observable, oldValue, newValue) -> {
			validate();
		});
		
		getDialogPane().setContent(grid);
		
		// Restore last values
		if(LAST_VALUES.firstCltuId != null) {
			firstCltuIdText.setText(String.valueOf(LAST_VALUES.firstCltuId));
		}
		
		Platform.runLater(() -> {
			firstCltuIdText.requestFocus();
			validate();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private CltuStartDialogResult buildResult() {
		try {
			Long firstCltuId = null;
			if(!firstCltuIdText.getText().isEmpty()) {
				firstCltuId = Long.parseLong(firstCltuIdText.getText());
			}
			CltuStartDialogResult res = new CltuStartDialogResult(firstCltuId);
			LAST_VALUES = res;
			return res;
		} catch (NumberFormatException e) {
			LOG.log(Level.WARNING, "Cannot build result from the dialog: this should not happen", e);
		}
		return null;
	}

	private void validate() {
		boolean valid = true;

		try {
			if(!firstCltuIdText.getText().isEmpty()) {
				long cltuId = Long.parseLong(firstCltuIdText.getText());
				if(cltuId < 0) {
					throw new NumberFormatException("First CLTU ID cannot be < 0");
				}
			}
			firstCltuIdText.setStyle("");
		} catch (NumberFormatException e) {
			firstCltuIdText.setStyle("-utlfx-background-color: red");
			valid = false;
		}
		
		Node okButton = getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(!valid);
	}

	public static class CltuStartDialogResult {
		
		public final Long firstCltuId;
		
		public CltuStartDialogResult(Long firstCltuId) {
			super();
			this.firstCltuId = firstCltuId;
		}		
	}
}
