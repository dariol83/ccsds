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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Optional;
import java.util.logging.LogRecord;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class DialogUtils {

	public static void showLogRecordDetails(String title, LogRecord record) {
		Alert dialog = new Alert(AlertType.INFORMATION);
		dialog.setTitle(title + " - " + new Date(record.getMillis()));
		dialog.setHeaderText(null);
		dialog.setContentText(record.getLevel() + "\n\n" + record.getMessage());

		if(record.getThrown() != null) {
			Throwable ex = record.getThrown();
	
			// Create expandable Exception.
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			ex.printStackTrace(pw);
			String exceptionText = sw.toString();
	
			Label label = new Label("Exception stacktrace:");
	
			TextArea textArea = new TextArea(exceptionText);
			textArea.setEditable(false);
			textArea.setWrapText(true);
	
			textArea.setMaxWidth(Double.MAX_VALUE);
			textArea.setMaxHeight(Double.MAX_VALUE);
			GridPane.setVgrow(textArea, Priority.ALWAYS);
			GridPane.setHgrow(textArea, Priority.ALWAYS);
	
			GridPane expContent = new GridPane();
			expContent.setMaxWidth(Double.MAX_VALUE);
			expContent.add(label, 0, 0);
			expContent.add(textArea, 0, 1);
	
			dialog.getDialogPane().setExpandableContent(expContent);
		}
		
		dialog.showAndWait();
	}
	
	public static void showInfo(String title, String text) {
		showAlert(AlertType.INFORMATION, title, text, null, null);
	}
	
	public static void showError(String title, String text) {
		showAlert(AlertType.ERROR, title, text, null, null);
	}
	
	public static Optional<ButtonType> showConfirmation(String title, String text, String header, Node graphic) {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.setHeaderText(header);
		alert.setTitle(title);
		alert.setContentText(text);
		alert.setGraphic(graphic);
		return alert.showAndWait();
	}
	
	private static void showAlert(AlertType type, String title, String text, String header, Node graphic) {
		Alert alert = new Alert(type);
		alert.setHeaderText(header);
		alert.setTitle(title);
		alert.setContentText(text);
		alert.setGraphic(graphic);
		alert.show();
	}	
}
