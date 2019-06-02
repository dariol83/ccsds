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

import eu.dariolucia.ccsds.sle.utlfx.dialogs.ScheduleStatusReportDialog.ScheduleStatusReportDialogResult;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.StageStyle;

public class ScheduleStatusReportDialog extends Dialog<ScheduleStatusReportDialogResult> {

	private static final Logger LOG = Logger.getLogger(ScheduleStatusReportDialog.class.getName());
	
	private static ScheduleStatusReportDialogResult LAST_VALUES = new ScheduleStatusReportDialogResult(true, false,
			false, 30);

	private ComboBox<String> statusReportCombo;
	private Spinner<Integer> periodSpinner;

	public ScheduleStatusReportDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Schedule Status Report");
		setHeaderText("Invoke a SCHEDULE-STATUS-REPORT operation");
		setGraphic(null);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		statusReportCombo = new ComboBox<>(FXCollections.observableArrayList("Immediate", "Start", "Stop"));
		statusReportCombo.getSelectionModel().select(0);
		statusReportCombo.setTooltip(new Tooltip("The type of the request"));

		periodSpinner = new Spinner<>(new SpinnerValueFactory<Integer>() {
			@Override
			public void decrement(int steps) {
				int newValue = periodSpinner.getValue() - steps;
				if (newValue <= 0) {
					newValue = 1;
				}
				periodSpinner.getValueFactory().setValue(newValue);
			}

			@Override
			public void increment(int steps) {
				int newValue = periodSpinner.getValue() + steps;
				periodSpinner.getValueFactory().setValue(newValue);
			}
		});
		periodSpinner.setEditable(false);
		periodSpinner.setTooltip(new Tooltip("Period (in seconds) used for periodic status report requests"));

		grid.add(new Label("Request type"), 0, 0);
		grid.add(statusReportCombo, 1, 0);
		grid.add(new Label("Period"), 0, 1);
		grid.add(periodSpinner, 1, 1);

		getDialogPane().setContent(grid);

		restoreLastValues();

		Platform.runLater(() -> {
			statusReportCombo.requestFocus();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private void restoreLastValues() {
		if(LAST_VALUES.isImmediate) {
			statusReportCombo.getSelectionModel().select("Immediate");
		} else if(LAST_VALUES.isStart) {
			statusReportCombo.getSelectionModel().select("Start");
		} else if(LAST_VALUES.isStop) {
			statusReportCombo.getSelectionModel().select("Stop");
		}
		periodSpinner.getValueFactory().setValue(LAST_VALUES.periodInSeconds);
	}

	private void storeLastValues(ScheduleStatusReportDialogResult res) {
		LAST_VALUES = res;
	}
	
	private ScheduleStatusReportDialogResult buildResult() {
		try {
			boolean isImmediate = statusReportCombo.getSelectionModel().getSelectedItem().equals("Immediate");
			boolean isStart = statusReportCombo.getSelectionModel().getSelectedItem().equals("Start");
			boolean isStop = statusReportCombo.getSelectionModel().getSelectedItem().equals("Stop");
			int period = periodSpinner.getValue();
			ScheduleStatusReportDialogResult res = new ScheduleStatusReportDialogResult(isImmediate, isStart, isStop, period);
			storeLastValues(res);
			return res;
		} catch (NumberFormatException e) {
			LOG.log(Level.WARNING, "Cannot build result from the dialog: this should not happen", e);
		}
		return null;
	}
	
	public static class ScheduleStatusReportDialogResult {
		public final boolean isImmediate;
		public final boolean isStart;
		public final boolean isStop;
		public final int periodInSeconds;

		public ScheduleStatusReportDialogResult(boolean isImmediate, boolean isStart, boolean isStop,
				int periodInSeconds) {
			super();
			this.isImmediate = isImmediate;
			this.isStart = isStart;
			this.isStop = isStop;
			this.periodInSeconds = periodInSeconds;
		}
	}
}
