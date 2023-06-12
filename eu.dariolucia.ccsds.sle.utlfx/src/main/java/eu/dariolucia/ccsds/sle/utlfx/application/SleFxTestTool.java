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

package eu.dariolucia.ccsds.sle.utlfx.application;
	
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class SleFxTestTool extends Application {
	
	private static final Logger LOG = Logger.getLogger(SleFxTestTool.class.getName());
	
	public static final String NAME = "SLE FX User Test Library";
	public static final String VERSION = "1.0.0";
	public static final String AUTHOR = "Dario Lucia (dario.lucia@gmail.com) - https://www.dariolucia.eu";
	public static final String YEARS = "(c) 2018, 2019, 2020-2023";
	
	@Override
	public void start(Stage primaryStage) {
		try {
			VBox root = FXMLLoader.load(SleFxTestTool.class.getResource("/eu/dariolucia/ccsds/sle/utlfx/fxml/Main.fxml"));
			Scene scene = new Scene(root,1600,960);
			primaryStage.getIcons().add(new Image(SleFxTestTool.class.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/monitor.png")));
			primaryStage.setScene(scene);
			primaryStage.setTitle(NAME + " " + VERSION);
			primaryStage.setOnCloseRequest(SleFxTestTool::performExit);
			primaryStage.show();

		} catch(Exception e) {
			LOG.log(Level.SEVERE, "Cannot start the application", e);
		}
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
	public static void performExit(Event e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation(NAME + " - Confirm Exit", "Are you sure you want to exit?", null, null);
		if (result.isPresent() && result.get() == ButtonType.OK) {
			Platform.exit();
			System.exit(0);
		} else {
			if (e != null) {
				e.consume();
			}
		}
	}
}
