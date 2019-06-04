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

package eu.dariolucia.ccsds.inspector.view.dialogs;

import eu.dariolucia.ccsds.inspector.api.*;
import eu.dariolucia.ccsds.inspector.api.internal.ConnectorFactoryRegistry;
import eu.dariolucia.ccsds.inspector.manager.ConnectorManager;
import eu.dariolucia.ccsds.inspector.view.util.FancyListCell;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;

import java.io.File;
import java.util.*;
import java.util.function.Function;

public class ConnectorWizard extends Dialog<ConnectorManager> {

	public static Optional<ConnectorManager> openWizard() {
		return new ConnectorWizard().showAndWait();
	}

	private IntegerProperty currentPage = new SimpleIntegerProperty(0);
	private BooleanProperty canFinish = new SimpleBooleanProperty(false);
	private StackPane stackPane;
	private ConnectorWizardPage[] pages;

	private ConnectorWizard() {
		super();
		initStyle(StageStyle.DECORATED);
		setResizable(false);
		setTitle("Connector Wizard");
		setHeaderText("Select the connector to be created, insert the required connector properties and\ndefine the transport layer specific parameters");

		getDialogPane().getButtonTypes().addAll(ButtonType.PREVIOUS, ButtonType.NEXT, ButtonType.FINISH, ButtonType.CANCEL);
		createPages();
		linkButtons();
		setResultConverter((o) -> {
			if(o.equals(ButtonType.FINISH)) {
				return buildConnectorManager();
			} else {
				return null;
			}
		});
		Platform.runLater(() -> ((ConnectorSelectorPage) pages[0]).focus());
	}

	private void createPages() {
		stackPane = new StackPane();
		this.pages = new ConnectorWizardPage[3];

		// First page: selection of the connector
		ConnectorSelectorPage p1 = new ConnectorSelectorPage();
		// Second page: connector properties
		ConnectorPropertyPage p2 = new ConnectorPropertyPage();
		// Link first page with second
		p1.selectedConnectorProperty().addListener(p2);
		// Third page: CCSDS-related options
		InspectorPropertyPage p3 = new InspectorPropertyPage();

		this.pages[0] = p1;
		this.pages[1] = p2;
		this.pages[2] = p3;

		this.stackPane.getChildren().addAll(p3, p2, p1);
		p1.toFront();

		ScrollPane sp = new ScrollPane(this.stackPane);
		sp.setFitToHeight(true);
		sp.setFitToWidth(true);
		sp.setPannable(true);
		getDialogPane().setContent(sp);
	}

	private void linkButtons() {
		Button previous = (Button) getDialogPane().lookupButton(ButtonType.PREVIOUS);
		previous.disableProperty().bind(currentPage.lessThanOrEqualTo(0));
		previous.addEventFilter(ActionEvent.ACTION, click -> {
			goPreviousPage();
			click.consume();
		});

		Button next = (Button) getDialogPane().lookupButton(ButtonType.NEXT);
		next.disableProperty().bind(currentPage.greaterThanOrEqualTo(stackPane.getChildren().size() - 1));
		next.addEventFilter(ActionEvent.ACTION, click -> {
			goNextPage();
			click.consume();
		});

		Button finish = (Button) getDialogPane().lookupButton(ButtonType.FINISH);
		finish.disableProperty().bind(canFinish.not());

		canFinish.bind(this.pages[0].validProperty().and(this.pages[1].validProperty()).and(this.pages[2].validProperty()));
	}

	private ConnectorManager buildConnectorManager() {
		if(!canFinish.get()) {
			throw new IllegalStateException("Cannot complete the wizard: not valid entries");
		}
		String name = ((ConnectorSelectorPage) pages[0]).getName();
		IConnectorFactory connector = ((ConnectorSelectorPage) pages[0]).selectedConnectorProperty().get();

		ConnectorProperty<Boolean> extractPackets = ((InspectorPropertyPage) pages[2]).getExtractPacketConfiguration();

		ConnectorConfiguration configuration = ((ConnectorPropertyPage) pages[1]).buildConfiguration(extractPackets);

		return new ConnectorManager(name, connector, configuration);
	}

	private void goPreviousPage() {
		int previousPage = this.currentPage.get() - 1;
		if (previousPage >= 0) {
			this.pages[previousPage].toFront();
			this.currentPage.set(previousPage);
		}
	}

	private void goNextPage() {
		int nextPage = this.currentPage.get() + 1;
		if (nextPage <= this.pages.length - 1) {
			this.pages[nextPage].toFront();
			this.currentPage.set(nextPage);
		}
	}

	private abstract class ConnectorWizardPage extends GridPane {

		protected BooleanProperty valid = new SimpleBooleanProperty();

		protected ConnectorWizardPage() {
			super();

			setHgap(5);
			setVgap(5);
			setPadding(new Insets(5, 5, 5, 5));
			setStyle("-fx-background-color: derive(-fx-base,26.4%);");
		}

		public BooleanProperty validProperty() {
			return valid;
		}
	}

	protected class ConnectorSelectorPage extends ConnectorWizardPage {

		private ListView<IConnectorFactory> connectors;

		private TextField name;

		private final Image image;

		protected ConnectorSelectorPage() {
			super();

			this.connectors = new ListView<>();
			this.connectors.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
			this.connectors.getItems().addAll(ConnectorFactoryRegistry.instance().getRegisteredConnectorFactories());
			this.connectors.getSelectionModel().selectedItemProperty().addListener(change -> validate());

			this.name = new TextField();
			this.name.setPromptText("Name of the connector");
			this.name.textProperty().addListener(changed -> validate());
			// Add name list view
			add(new Label("Connector Name"), 0, 0);
			add(name, 1, 0);
			GridPane.setHgrow(name, Priority.ALWAYS);
			// Add connector list view
			add(connectors, 0, 1, 2, 5);
			GridPane.setHgrow(connectors, Priority.ALWAYS);

			this.image = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/inspector/view/res/cast.png"), 24, 24,
					true, true);
			this.connectors.setCellFactory(param -> new FancyListCell<>(this::connectorFactoryName, this::connectorFactoryDescription, (o) -> image));

			validate();
		}

		private String connectorFactoryName(IConnectorFactory iConnectorFactory) {
			return iConnectorFactory.getName() + " - Version: " + iConnectorFactory.getVersion();
		}

		private String connectorFactoryDescription(IConnectorFactory iConnectorFactory) {
			return iConnectorFactory.getDescription();
		}

		public ReadOnlyObjectProperty<IConnectorFactory> selectedConnectorProperty() {
			return this.connectors.getSelectionModel().selectedItemProperty();
		}

		public String getName() {
			return this.name.getText();
		}

		private void validate() {
			if(this.name.getText().isEmpty()) {
				this.name.setTooltip(new Tooltip("Connector name missing"));
				this.name.setStyle("-fx-background-color: red");
			} else {
				this.name.setTooltip(null);
				this.name.setStyle("");
			}
			super.valid.set(this.connectors.getSelectionModel().getSelectedItem() != null
					&& !this.name.getText().isEmpty());
		}

		public void focus() {
			this.name.requestFocus();
		}
	}

	protected class ConnectorPropertyPage extends ConnectorWizardPage implements ChangeListener<IConnectorFactory> {

		private IConnectorFactory currentFactory;
		private ConnectorConfigurationDescriptor currentDescriptor;
		private Map<Control, Function<Control, ConnectorProperty<?>>> mappers = new HashMap<>();
		private Map<Control, Function<Control, String>> validators = new HashMap<>();
		private int nextRow = 0;

		protected ConnectorPropertyPage() {
			super();
			add(new Label("No connector selected"), 0, 0);
			validate();
		}

		@Override
		public void changed(ObservableValue<? extends IConnectorFactory> observableValue, IConnectorFactory oldValue, IConnectorFactory newValue) {
			resetPage();
			initPageWith(newValue);
		}

		private void initPageWith(IConnectorFactory newValue) {
			this.currentFactory = newValue;
			if (this.currentFactory != null) {
				this.currentDescriptor = this.currentFactory.getConfigurationDescriptor();
				for (ConnectorPropertyDescriptor<?> cpd : this.currentDescriptor.getProperties()) {
					addPropertyItem(cpd);
				}
			}
			validate();
			layout();
		}

		private void addPropertyItem(ConnectorPropertyDescriptor<?> cpd) {
			Control toReturn;
			Control optionalControl = null;
			Label propertyName = new Label(cpd.getName());
			if (cpd.getType().equals(Integer.class)
					|| cpd.getType().equals(Double.class)
					|| cpd.getType().equals(Long.class)
					|| cpd.getType().equals(String.class)) {
				TextField t = new TextField();
				t.setPromptText(cpd.getDefaultValueAsString() != null ? cpd.getDefaultValueAsString() : "");
				// Default value?
				if(cpd.getDefaultValue() != null) {
					t.setText(cpd.getDefaultValueAsString());
				}
				// Set the mapper
				this.mappers.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).build(t.getText()));
				// Set the validator
				this.validators.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).isValid(t.getText()));
				// Set verification on change
				t.textProperty().addListener(change -> validate());
				// Set the outer object
				toReturn = t;
			} else if (Enum.class.isAssignableFrom(cpd.getType())) {
				ComboBox t = new ComboBox();
				t.getItems().addAll(((Class<? extends Enum>) cpd.getType()).getEnumConstants());
				// Default value?
				if (cpd.getDefaultValue() != null) {
					t.getSelectionModel().select(cpd.getDefaultValue());
				}
				// Set the mapper
				this.mappers.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).build(t.getSelectionModel().getSelectedItem().toString()));
				// Set the validator
				this.validators.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).isValid(t.getSelectionModel().getSelectedItem().toString()));
				// Set verification on change
				t.getSelectionModel().selectedItemProperty().addListener(changed -> validate());
				// Set the outer object
				toReturn = t;
			} else if (cpd.getType().equals(File.class)) {
				TextField t = new TextField();
				t.setPromptText(cpd.getDefaultValueAsString() != null ? cpd.getDefaultValueAsString() : "");
				// Default value?
				if(cpd.getDefaultValue() != null) {
					t.setText(cpd.getDefaultValueAsString());
				}
				// Set the mapper
				this.mappers.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).build(t.getText()));
				// Set the validator
				this.validators.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).isValid(t.getText()));
				// Set verification on change
				t.textProperty().addListener(change -> validate());
				// Use optional control
				Button naviButton = new Button("Select...");
				naviButton.setOnAction((actionEvent) -> {
					FileChooser fc = new FileChooser();
					File selected = fc.showOpenDialog(getOwner());
					if(selected != null) {
						t.setText(selected.getAbsolutePath());
						validate();
					}
				});
				// Set the outer object
				toReturn = t;
				optionalControl = naviButton;
			} else if (cpd.getType().equals(Date.class)) {
				TextField t = new TextField();
				t.setPromptText(ConnectorPropertyDescriptor.DATE_FORMAT_PATTERN);
				// Default value?
				if(cpd.getDefaultValue() != null) {
					t.setText(cpd.getDefaultValueAsString());
				}
				// Set the mapper
				this.mappers.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).build(t.getText()));
				// Set the validator
				this.validators.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).isValid(t.getText()));
				// Set verification on change
				t.textProperty().addListener(change -> validate());
				// Set the outer object
				toReturn = t;
			} else if (cpd.getType().equals(Boolean.class)) {
				CheckBox t = new CheckBox();
				// Default value?
				if(cpd.getDefaultValue() != null) {
					t.setSelected(cpd.getDefaultValue() == Boolean.TRUE);
				}
				// Set the mapper
				this.mappers.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).build(t.isSelected() ? "true" : "false"));
				// Set the validator
				this.validators.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).isValid(t.isSelected() ? "true" : "false"));
				// Set verification on change
				t.textProperty().addListener(change -> validate());
				// Set the outer object
				toReturn = t;
			} else {
				// Not supported type, use generic text field
				TextField t = new TextField();
				t.setPromptText(cpd.getDefaultValueAsString() != null ? cpd.getDefaultValueAsString() : "");
				// Default value?
				if(cpd.getDefaultValue() != null) {
					t.setText(cpd.getDefaultValueAsString());
				}
				// Set the mapper
				this.mappers.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).build(t.getText()));
				// Set the validator
				this.validators.put(t, ctrl -> ((ConnectorPropertyDescriptor) ctrl.getUserData()).isValid(t.getText()));
				// Set verification on change
				t.textProperty().addListener(change -> validate());
				// Set the outer object
				toReturn = t;
			}

			toReturn.setTooltip(new Tooltip(cpd.getDescription()));
			toReturn.setUserData(cpd);

			add(propertyName, 0, this.nextRow);
			add(toReturn, 1, this.nextRow);
			add(Objects.requireNonNullElseGet(optionalControl, Label::new), 2, this.nextRow++);

			GridPane.setHgrow(toReturn, Priority.ALWAYS);
		}

		private void validate() {
			if(this.currentFactory == null) {
				super.valid.set(false);
				return;
			}
			boolean finalResult = true;
			for (Control c : this.validators.keySet()) {
				String validity = this.validators.get(c).apply(c);
				if (validity != null) {
					finalResult = false;
					ConnectorPropertyDescriptor cpd = (ConnectorPropertyDescriptor) c.getUserData();
					c.setTooltip(new Tooltip(validity + "\n\n" + cpd.getDescription()));
					c.setStyle("-fx-background-color: red");
				} else {
					ConnectorPropertyDescriptor cpd = (ConnectorPropertyDescriptor) c.getUserData();
					c.setTooltip(new Tooltip(cpd.getDescription()));
					c.setStyle("");
				}
			}
			super.valid.set(finalResult);
		}

		private void resetPage() {
			getChildren().clear();
			this.mappers.clear();
			this.validators.clear();
			this.currentDescriptor = null;
			this.currentFactory = null;
			this.nextRow = 0;
			super.valid.set(false);
		}

		public ConnectorConfiguration buildConfiguration(ConnectorProperty<?>... additionalProperties) {
			if (!valid.get()) {
				throw new IllegalStateException("Cannot build the connector configuration: not valid");
			}
			List<ConnectorProperty<?>> props = new ArrayList<>(this.mappers.keySet().size());
			for (Control c : this.mappers.keySet()) {
				Function<Control, ConnectorProperty<?>> mapper = this.mappers.get(c);
				props.add(mapper.apply(c));
			}
			props.addAll(Arrays.asList(additionalProperties));
			return new ConnectorConfiguration(props);
		}
	}

	protected class InspectorPropertyPage extends ConnectorWizardPage {

		private final CheckBox extractPacketBox;

		protected InspectorPropertyPage() {
			super();

			this.extractPacketBox = new CheckBox();
			this.extractPacketBox.setSelected(true);
			// Add name list view
			add(new Label("Space Packet Extraction"), 0, 0);
			add(extractPacketBox, 1, 0);

			super.valid.set(true);
		}

		public ConnectorProperty<Boolean> getExtractPacketConfiguration() {
			ConnectorPropertyDescriptor<Boolean> pd = ConnectorPropertyDescriptor.booleanDescriptor(ConnectorManager.CONFIGURATION_EXTRACT_PACKET_KEY, "Space Packet Extraction",
					"If enabled, the inspector will perform its best to extract Space Packet from the received transfer frames", true);
			return pd.build(this.extractPacketBox.isSelected() ? "true" : "false");
		}
	}
}
