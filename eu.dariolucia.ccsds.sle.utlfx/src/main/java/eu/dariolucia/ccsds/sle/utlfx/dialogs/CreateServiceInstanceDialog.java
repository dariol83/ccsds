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

import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CreateServiceInstanceDialog extends Dialog<Object[]> {

	private static final Logger LOG = Logger.getLogger(CreateServiceInstanceDialog.class.getName());
	
	private Button configurationFileButton;
	private TextField configurationFileText;
	private FileChooser configurationFileChooser = new FileChooser();

	private ListView<ServiceInstanceConfiguration> serviceInstanceTableView;

	private PeerConfiguration peerConfiguration = null;
	private Map<String, ServiceInstanceConfiguration> serviceInstanceConfigurations = null;
	private Map<ServiceInstanceConfiguration, SimpleBooleanProperty> selectedConfigurations = null;
	
	public CreateServiceInstanceDialog() {
		super();
		initStyle(StageStyle.DECORATED);
		setTitle("Create Service Instance");
		setHeaderText("Select the SLE configuration and service instances file");
		setGraphic(new ImageView(new Image(getClass()
				.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/plus-circle.png"))));
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
		getDialogPane().setPrefWidth(600);

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(10, 10, 10, 10));

		configurationFileText = new TextField();
		configurationFileText.setText("");
		configurationFileText.setTooltip(
				new Tooltip("Path to the configuration file"));

		configurationFileButton = new Button("Select...");
		configurationFileButton.setOnAction(this::configurationFileSelectButtonSelected);
				
		serviceInstanceTableView = new ListView<>();
		serviceInstanceTableView.setCellFactory(CheckBoxListCell.forListView(param -> selectedConfigurations.get(param), new StringConverter<>() {
			@Override
			public ServiceInstanceConfiguration fromString(String string) {
				return serviceInstanceConfigurations.get(string);
			}

			@Override
			public String toString(ServiceInstanceConfiguration object) {
				return object.getServiceInstanceIdentifier();
			}
		}));
		serviceInstanceTableView.setTooltip(
				new Tooltip("List of service instances to be loaded"));

		grid.add(new Label("Configuration file path"), 0, 0);
		grid.add(configurationFileText, 1, 0);
		grid.add(configurationFileButton, 2, 0);
		
		grid.add(serviceInstanceTableView, 0, 1, 3, 4);
		grid.add(new Label(), 1, 1);
		grid.add(new Label(), 2, 1);

		HBox.setHgrow(grid, Priority.ALWAYS);
		GridPane.setHgrow(configurationFileText, Priority.ALWAYS);
		GridPane.setHgrow(serviceInstanceTableView, Priority.ALWAYS);

		configurationFileText.textProperty().addListener((observable, oldValue, newValue) -> validate());

		getDialogPane().setContent(grid);

		Platform.runLater(() -> {
			getDialogPane().getScene().getWindow().sizeToScene();
			configurationFileButton.requestFocus();
			validate();
		});

		setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {
				return buildResult();
			}
			return null;
		});
	}

	private void configurationFileSelectButtonSelected(ActionEvent e) {
		// open file chooser
		File file = this.configurationFileChooser.showOpenDialog(getOwner());
		if (file != null) {
			this.configurationFileChooser.setInitialDirectory(file.getParentFile());
			try {
				// parse selected file
				UtlConfigurationFile configurationFile = UtlConfigurationFile.load(new FileInputStream(file));
				List<ServiceInstanceConfiguration> returnedList = configurationFile.getServiceInstances();
				this.serviceInstanceConfigurations = returnedList.stream().collect(Collectors.toMap(ServiceInstanceConfiguration::getServiceInstanceIdentifier, o -> o));
				this.selectedConfigurations = returnedList.stream().collect(Collectors.toMap(o -> o, o -> new SimpleBooleanProperty(false)));
				// update table
				this.serviceInstanceTableView.getItems().clear();
				this.serviceInstanceTableView.getItems().addAll(this.serviceInstanceConfigurations.values());
				// update configurationFileText field in case OK
				this.configurationFileText.setText(file.getAbsolutePath());
				this.peerConfiguration = configurationFile.getPeerConfiguration();
			} catch (IOException e1) {
				LOG.log(Level.WARNING, "Cannot parse the selected file " + file.getAbsolutePath(), e1);
				this.serviceInstanceConfigurations = null;
				this.serviceInstanceTableView.getItems().clear();
				this.selectedConfigurations = null;
				this.peerConfiguration = null;
				this.configurationFileText.setText("");
			}
		}

		// update okButton if conditions are met
		validate();
	}

	private Object[] buildResult() {
		Object[] configurations = retrieveSelected().toArray();
		Object[] toReturn = new Object[configurations.length + 1];
		toReturn[0] = this.peerConfiguration;
		System.arraycopy(configurations, 0, toReturn, 1, configurations.length);
		return toReturn;
	}

	private void validate() {
		boolean valid = this.peerConfiguration != null;
		valid &= this.serviceInstanceTableView != null && !this.serviceInstanceTableView.getItems().isEmpty();
		
		Node okButton = getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(!valid);
	}

	private List<ServiceInstanceConfiguration> retrieveSelected() {
		List<ServiceInstanceConfiguration> toReturn = new LinkedList<>();
		if(this.selectedConfigurations != null) {
			for(Map.Entry<ServiceInstanceConfiguration, SimpleBooleanProperty> e : this.selectedConfigurations.entrySet()) {
				if(e.getValue().getValue()) {
					toReturn.add(e.getKey());
				}
			}
		}
		return toReturn;
	}

}
