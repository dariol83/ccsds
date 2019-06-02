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

import java.util.Arrays;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.stage.StageStyle;

public class ComboDialog<T> extends Dialog<T> {

	private ComboBox<T> values;

	public ComboDialog(String title, String heading, String labelText, String comboTooltip, T[] values,
			T selectedValue) {
		this(title, heading, labelText, comboTooltip, Arrays.asList(values), selectedValue);
	}
	
	public ComboDialog(String title, String heading, String labelText, String comboTooltip, Iterable<T> values,
			T selectedValue) {
		super();

		initStyle(StageStyle.DECORATED);
		setTitle(title);
		setHeaderText(heading);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		ObservableList<T> list = FXCollections.observableArrayList();
		for (T value : values) {
			list.add(value);
		}

		if (list.isEmpty()) {
			throw new IllegalArgumentException("Iterable set of values is empty");
		}

		this.values = new ComboBox<>(list);
		this.values.setTooltip(new Tooltip(comboTooltip));
		if (selectedValue != null) {
			this.values.getSelectionModel().select(selectedValue);
		} else {
			this.values.getSelectionModel().select(0);
		}

		grid.add(new Label(labelText), 0, 0);
		grid.add(this.values, 1, 0);

		getDialogPane().setContent(grid);

		Platform.runLater(() -> {
			this.values.requestFocus();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return this.values.getSelectionModel().getSelectedItem();
			}
			return null;
		});
	}
}
