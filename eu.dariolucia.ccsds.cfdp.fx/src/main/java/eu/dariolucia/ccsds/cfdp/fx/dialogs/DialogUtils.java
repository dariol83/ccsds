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

package eu.dariolucia.ccsds.cfdp.fx.dialogs;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.Optional;
import java.util.logging.LogRecord;

public class DialogUtils {

	public static void attachImage(Class<?> clazz, Button b, String path) {
		Image image = new Image(clazz.getResourceAsStream(path));
		b.setGraphic(new ImageView(image));
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
