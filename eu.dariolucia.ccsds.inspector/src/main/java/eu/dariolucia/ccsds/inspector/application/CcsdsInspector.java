/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.inspector.application;

import eu.dariolucia.ccsds.inspector.view.controller.CcsdsInspectorMainViewController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;
import java.util.logging.Logger;

public class CcsdsInspector extends Application {

	public static final String NAME = "CCSDS TM/TC Inspector";
	public static final String VERSION = "1.0.0";
	public static final String TITLE = NAME + " - " + VERSION;
	public static final String FILE_SEPARATOR = System.getProperty("file.separator");

	private static final Logger LOG = Logger.getLogger(CcsdsInspector.class.getName());

	public static void main(String[] args) {
		Application.launch(CcsdsInspector.class, args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		LOG.info(TITLE + " started");
		Platform.setImplicitExit(false);

		URL resource = CcsdsInspector.class
				.getResource("/eu/dariolucia/ccsds/inspector/view/fxml/CcsdsInspectorMainView.fxml");
		FXMLLoader loader = new FXMLLoader(resource);
		Parent root = loader.load();
		CcsdsInspectorMainViewController ctrl = loader.getController();

		Scene scene = new Scene(root, -1, -1);

		stage.setTitle(TITLE);
		stage.getIcons().add(new Image(
				CcsdsInspector.class.getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/activity.png")));
		stage.setScene(scene);
		stage.setResizable(false);
		stage.show();

		stage.setOnCloseRequest(t -> {
			stage.setIconified(true);
			t.consume();
		});
	}

	/**
	 * Exit the application with a confirmation.
	 *
	 * @param e
	 */
	public static void performExit(Event e) {
		Alert alert = new Alert(AlertType.CONFIRMATION);
		alert.setTitle(NAME + " - Confirm Exit");
		alert.setHeaderText(null);
		alert.setContentText("Are you sure you want to exit?");

		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == ButtonType.OK) {
			Platform.exit();
			System.exit(0);
		} else {
			if (e != null) {
				e.consume();
			}
		}
	}
}
