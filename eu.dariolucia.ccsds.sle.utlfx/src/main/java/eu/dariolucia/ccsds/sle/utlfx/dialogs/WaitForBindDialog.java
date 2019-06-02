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

import eu.dariolucia.ccsds.sle.utlfx.dialogs.WaitForBindDialog.WaitForBindDialogResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.StageStyle;

public class WaitForBindDialog extends Dialog<WaitForBindDialogResult> {

	private static final Logger LOG = Logger.getLogger(WaitForBindDialog.class.getName());
	
	private CheckBox sendPosBindRetCheckbox;
	private CheckBox sendPosUnbindRetCheckbox;

	public WaitForBindDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Wait for bind");
		setHeaderText("Start the service instance in Provider-Initiating-Bind mode");
		setGraphic(null);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		sendPosBindRetCheckbox = new CheckBox();
		sendPosBindRetCheckbox.setText("");
		sendPosBindRetCheckbox.setTooltip(new Tooltip(
				"Send a positive BIND-RETURN upon reception of BIND invocation. If not checked, a negative BIND-RETURN will be sent instead"));
		sendPosBindRetCheckbox.setSelected(true);
		
		sendPosUnbindRetCheckbox = new CheckBox();
		sendPosUnbindRetCheckbox.setText("");
		sendPosUnbindRetCheckbox.setTooltip(new Tooltip(
				"Send a positive UNBIND-RETURN upon reception of UNBIND invocation. If not checked, no UNBIND-RETURN will be sent"));
		sendPosUnbindRetCheckbox.setSelected(true);
		
		grid.add(new Label("Send a positive BIND-RETURN upon BIND invocation"), 0, 0);
		grid.add(sendPosBindRetCheckbox, 1, 0);
		grid.add(new Label("Send a positive UNBIND-RETURN upon UNBIND invocation"), 0, 1);
		grid.add(sendPosUnbindRetCheckbox, 1, 1);

		getDialogPane().setContent(grid);

		Platform.runLater(() -> {
			sendPosBindRetCheckbox.requestFocus();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private WaitForBindDialogResult buildResult() {
		try {
			return new WaitForBindDialogResult(sendPosBindRetCheckbox.isSelected(), sendPosUnbindRetCheckbox.isSelected());
		} catch (NumberFormatException e) {
			LOG.log(Level.WARNING, "Cannot build result from the dialog: this should not happen", e);
		}
		return null;
	}

	public static class WaitForBindDialogResult {
		public final boolean sendPosBindReturn;
		public final boolean sendPosUnbindReturn;

		public WaitForBindDialogResult(boolean sendPosBindReturn, boolean sendPosUnbindReturn) {
			super();
			this.sendPosBindReturn = sendPosBindReturn;
			this.sendPosUnbindReturn = sendPosUnbindReturn;
		}
	}
}
