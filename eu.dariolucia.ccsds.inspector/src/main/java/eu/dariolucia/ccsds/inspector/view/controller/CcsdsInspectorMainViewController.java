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

package eu.dariolucia.ccsds.inspector.view.controller;

import eu.dariolucia.ccsds.inspector.api.ConnectorPropertyDescriptor;
import eu.dariolucia.ccsds.inspector.api.SeverityEnum;
import eu.dariolucia.ccsds.inspector.application.CcsdsInspector;
import eu.dariolucia.ccsds.inspector.manager.ConnectorManager;
import eu.dariolucia.ccsds.inspector.manager.ConnectorManagerState;
import eu.dariolucia.ccsds.inspector.manager.IConnectorManagerObserver;
import eu.dariolucia.ccsds.inspector.view.dialogs.ConnectorWizard;
import eu.dariolucia.ccsds.inspector.view.util.FancyListCell;
import eu.dariolucia.ccsds.tmtc.util.AnnotatedObject;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

public class CcsdsInspectorMainViewController implements Initializable, IConnectorManagerObserver {

	private static final Logger LOG = Logger.getLogger(CcsdsInspectorMainViewController.class.getName());

	private static final int MAX_STATUS_MESSAGES = 30;

	@FXML
	private Button addButton;

	@FXML
	private Button removeButton;

	@FXML
	private Button infoButton;

	@FXML
	private Button exitButton;

	@FXML
	private ListView<ConnectorManager> managersListView;

	private final ObservableList<ConnectorManager> managersToMonitor = FXCollections.observableArrayList();

	private final Map<ConnectorManager, Stage> manager2stage = new HashMap<>();

	private final Map<ConnectorManagerState, Image> imageCollection = new HashMap<>();

	private final SimpleDateFormat dateFormat = ConnectorPropertyDescriptor.DATE_FORMAT;

	/*
	 * (non-Javadoc)
	 *
	 * @see javafx.fxml.Initializable#initialize(java.net.URL,
	 * java.util.ResourceBundle)
	 */
	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		// button images
		Image m1 = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/plus-circle.png"), 24, 24,
				true, true);
		ImageView addButtonImage = new ImageView(m1);
		this.addButton.setGraphic(addButtonImage);

		Image m11 = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/minus-circle.png"), 24, 24,
				true, true);
		ImageView removeButtonImage = new ImageView(m11);
		this.removeButton.setGraphic(removeButtonImage);

		Image m2 = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/stop-circle.png"), 24, 24,
				true, true);
		this.exitButton.setGraphic(new ImageView(m2));

		Image m3 = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/info.png"), 24, 24,
				true, true);
		this.infoButton.setGraphic(new ImageView(m3));

		this.managersListView.setItems(this.managersToMonitor);
		this.managersListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		this.managersListView.setOnMouseClicked(click -> {
			if (click.getClickCount() == 2) {
				ConnectorManager selItem = CcsdsInspectorMainViewController.this.managersListView.getSelectionModel()
						.getSelectedItem();
				if (selItem != null) {
					showWindow(selItem);
				}
			}
		});

		// Create images
		this.imageCollection.put(ConnectorManagerState.ERROR, new Image(
				getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/led_red.png"), 32, 32, true, true));
		this.imageCollection.put(ConnectorManagerState.STARTING,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/led_yellow.png"), 32, 32,
						true, true));
		this.imageCollection.put(ConnectorManagerState.STOPPING,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/led_yellow.png"), 32, 32,
						true, true));
		this.imageCollection.put(ConnectorManagerState.RUNNING,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/led_green.png"), 32, 32,
						true, true));
		this.imageCollection.put(ConnectorManagerState.NO_FLOW,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/led_grey.png"), 32, 32,
						true, true));
		this.imageCollection.put(ConnectorManagerState.IDLE,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/led_black.png"), 32, 32,
						true, true));

		// Customise the machine status list view
		this.managersListView.setCellFactory(param -> new FancyListCell<>(this::connectorName, this::connectorDescription, this::connectorImage));

		// Set date format to UTC
		this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private <T> Image connectorImage(ConnectorManager t) {
		return this.imageCollection.get(t.getState());
	}

	private <T> String connectorDescription(ConnectorManager t) {
		return t.getConnectorFactory().getDescription();
	}

	private <T> String connectorName(ConnectorManager t) {
		return t.getName() + " - " + t.getConnectorFactory().getName();
	}

	@FXML
	protected void exitButtonClicked(ActionEvent e) {
		CcsdsInspector.performExit(e);
	}

	@FXML
	protected void infoButtonClicked(ActionEvent e) {
		Alert alert = new Alert(AlertType.INFORMATION);
		alert.setTitle(CcsdsInspector.NAME);
		alert.setHeaderText(null);
		alert.setContentText("Version: " + CcsdsInspector.VERSION + " - Author: Dario Lucia - 2019, 2020\n\n" +
				"Icons made by Akveo (https://github.com/akveo/eva-icons)\n");
		alert.show();
	}

	@FXML
	protected void addButtonClicked(ActionEvent e) {
		Optional<ConnectorManager> cm = ConnectorWizard.openWizard();
		if(cm.isPresent()) {
			Stage cmWindow = createConnectorWindow(cm.get());
			this.manager2stage.put(cm.get(), cmWindow);
			this.managersToMonitor.add(cm.get());
			// Register this view to the connector
			cm.get().register(this);
			cm.get().initialise();
			cmWindow.show();
		}
	}

	private Stage createConnectorWindow(ConnectorManager connectorManager) {
		Stage window = new Stage();
		window.initStyle(StageStyle.UNDECORATED);
		window.setResizable(false);
		window.setTitle(connectorManager.getName() + " - " + connectorManager.getConnectorFactory().getName());
		window.setWidth(1024);
		window.setHeight(768);

		URL resource = CcsdsInspector.class
				.getResource("/eu/dariolucia/ccsds/inspector/view/fxml/CcsdsInspectorConnectorView.fxml");
		FXMLLoader loader = new FXMLLoader(resource);
		try {
			Parent root = loader.load();
			CcsdsInspectorConnectorViewController ctrl = loader.getController();
			ctrl.setConnectorManager(connectorManager);
			Scene scene = new Scene(root, -1, -1);
			window.setScene(scene);
			return window;
		} catch(IOException e) {
			throw new RuntimeException(e);
		}
	}

	@FXML
	protected void removeButtonClicked(ActionEvent e) {
		ConnectorManager selItem = CcsdsInspectorMainViewController.this.managersListView.getSelectionModel()
				.getSelectedItem();
		if (selItem != null) {
			Stage window = this.manager2stage.get(selItem);

			Alert alert = new Alert(AlertType.CONFIRMATION);
			alert.setTitle("Confirm shutdown");
			alert.setHeaderText("Connector " + selItem.getName());
			alert.setContentText("Are you sure you want to stop and remove connector " + selItem.getName() + "?");

			Optional<ButtonType> result = alert.showAndWait();
			if (result.isPresent() && result.get() == ButtonType.OK) {
				// Stop the connector
				selItem.stop();
				// Remove any knowledge of it from the system
				this.manager2stage.remove(selItem);
				this.managersToMonitor.remove(selItem);
				// Close and destroy window
				window.close();
				// Dispose
				selItem.dispose();
				// Last reference to the connector gone
				selItem = null;
				// Last reference to the window gone
				window = null;
				// Clean up with GC
				Runtime.getRuntime().gc();
			}
		}
	}

	protected void showWindow(ConnectorManager selItem) {
		Stage s = this.manager2stage.get(selItem);
		if(s.isIconified()) {
			s.setIconified(false);
		} else {
			s.show();
			s.toFront();
		}
	}

	@Override
	public void stateReported(ConnectorManager connectorManager, ConnectorManagerState currentState) {
		Platform.runLater(() -> managersListView.refresh());
	}

	@Override
	public void dataReported(ConnectorManager connectorManager, AnnotatedObject o) {
		// Nothing to be done
	}

	@Override
	public void errorReported(ConnectorManager connectorManager, Instant when, Exception e) {
		// Nothing to be done
	}

	@Override
	public void infoReported(ConnectorManager connectorManager, Instant when, SeverityEnum severity, String message) {
		// Nothing to be done
	}

	@Override
	public void disposedReported(ConnectorManager connectorManager) {
		// Nothing to be done
	}
}
