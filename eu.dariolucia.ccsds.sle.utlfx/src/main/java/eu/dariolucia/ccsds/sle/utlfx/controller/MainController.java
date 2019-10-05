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

package eu.dariolucia.ccsds.sle.utlfx.controller;

import eu.dariolucia.ccsds.sle.utl.config.PeerConfiguration;
import eu.dariolucia.ccsds.sle.utlfx.application.ApplicationConfiguration;
import eu.dariolucia.ccsds.sle.utlfx.application.SleFxTestTool;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.CreateServiceInstanceDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.SettingsDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.SettingsDialog.SettingsDialogResult;
import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.cltu.CltuServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.raf.RafServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rcf.RcfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.rocf.RocfServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.si.*;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.raf.RafServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.rcf.RcfServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.rocf.RocfServiceInstance;
import eu.dariolucia.ccsds.sle.utlfx.util.LogUtil;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class MainController implements Initializable, IServiceInstanceListener {

	private static final Logger LOG = Logger.getLogger(MainController.class.getName());

	@FXML
	private Button createSiButton;

	@FXML
	private Button destroySiButton;

	@FXML
	private Button settingsButton;

	@FXML
	private Button aboutButton;

	@FXML
	private ListView<ServiceInstance> serviceInstanceListView;

	@FXML
	private StackPane stackPane;

	@FXML
	private TableView<LogRecord> logTableView;

	@FXML
	private ToggleButton enableLogButton;

	@FXML
	private Button saveAsLogButton;

	@FXML
	private Button clearLogButton;

	@FXML
	private Pane emptyPane;

	@FXML
	private TitledPane logTitledPane;

	@FXML
	private Accordion logAccordion;

	@FXML
	private SplitPane mainSplitPane;

	private volatile boolean collectLogs = true;

	private final Map<ApplicationIdentifierEnum, Consumer<Object[]>> type2creator = new HashMap<>();

	private final Map<ServiceInstance, AbstractDisplayController> si2ctrl = new HashMap<>();
	private final Map<ServiceInstance, Node> si2node = new HashMap<>();
	private final Map<ServiceInstance, ServiceInstanceBindingStateEnum> si2state = new HashMap<>();
	private final Map<ServiceInstance, ServiceInstanceCell> si2cell = new HashMap<>();
	private final Map<ServiceInstanceBindingStateEnum, Image> imageCollection = new HashMap<>();

	@SuppressWarnings("unchecked")
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		this.type2creator.put(ApplicationIdentifierEnum.RCF, this::createRcfServiceInstance);
		this.type2creator.put(ApplicationIdentifierEnum.RAF, this::createRafServiceInstance);
		this.type2creator.put(ApplicationIdentifierEnum.ROCF, this::createRocfServiceInstance);
		this.type2creator.put(ApplicationIdentifierEnum.CLTU, this::createCltuServiceInstance);

		this.serviceInstanceListView.getSelectionModel().selectedItemProperty()
				.addListener(new ChangeListener<ServiceInstance>() {
					@Override
					public void changed(ObservableValue<? extends ServiceInstance> observable, ServiceInstance oldValue,
							ServiceInstance newValue) {
						selectServiceInstance(serviceInstanceListView.getSelectionModel().getSelectedItems());
						destroySiButton
								.setDisable(serviceInstanceListView.getSelectionModel().getSelectedItems().isEmpty());
					}
				});

		// Customise the machine status list view
		this.serviceInstanceListView.setCellFactory(param -> new ServiceInstanceCell());

		// Log table renderer
		((TableColumn<LogRecord, String>) logTableView.getColumns().get(0))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(new Date(o.getValue().getMillis()).toString()));
		((TableColumn<LogRecord, String>) logTableView.getColumns().get(1))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(o.getValue().getLevel().getName()));
		((TableColumn<LogRecord, String>) logTableView.getColumns().get(2))
				.setCellValueFactory(o -> new ReadOnlyObjectWrapper<>(buildTableMessage(o.getValue())));

		// Fix column size and autoresize
		logTableView.getColumns().get(0).prefWidthProperty().bind(logTableView.widthProperty().divide(8));
		logTableView.getColumns().get(1).prefWidthProperty().bind(logTableView.widthProperty().divide(8));
		logTableView.getColumns().get(2).prefWidthProperty()
				.bind(logTableView.widthProperty().divide(4).multiply(3).subtract(24));

		// Row factory with double click use for details inspection
		EventHandler<? super MouseEvent> logRecordDetailsDblClickHandler = new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() == 2) {
					DialogUtils.showLogRecordDetails("Details", ((TableRow<LogRecord>) event.getSource()).getItem());
				}
			}
		};
		this.logTableView.setRowFactory(tv -> {
			TableRow<LogRecord> r = new TableRow<>();
			r.setOnMouseClicked(logRecordDetailsDblClickHandler);
			return r;
		});

		// Cell factory for log table
		((TableColumn<LogRecord, String>) this.logTableView.getColumns().get(1)).setCellFactory(column -> {
			return new TableCell<LogRecord, String>() {
				@Override
				protected void updateItem(String item, boolean empty) {
					super.updateItem(item, empty);
					if (item != null && !empty && !isEmpty()) {
						setText(item);
						if (item.equals(Level.SEVERE.getName())) {
							setTextFill(Color.DARKRED);
							setStyle("-utlfx-font-weight: bold");
						} else if (item.equals(Level.WARNING.getName())) {
							setTextFill(Color.DARKORANGE);
							setStyle("-utlfx-font-weight: bold");
						} else {
							setTextFill(Color.BLACK);
							setStyle("");
						}
					} else {
						setText("");
						setGraphic(null);
					}
				}
			};
		});

		// Subscribe to logs
		Logger parentLog = Logger.getLogger("eu.dariolucia.ccsds.sle.utlfx");
		parentLog.addHandler(new Handler() {
			@Override
			public void publish(LogRecord record) {
				if (collectLogs && record.getLevel().intValue() >= Level.CONFIG.intValue()) {
					Platform.runLater(() -> {
						appendLogRecord(record);
					});
				}
			}

			@Override
			public void flush() {
				// Nothing to do
			}

			@Override
			public void close() throws SecurityException {
				// Nothing to do
			}
		});

		// Button graphics
		{
			Image image = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/plus-circle.png"));
			this.createSiButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/minus-circle.png"));
			this.destroySiButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass().getResourceAsStream(
					"/eu/dariolucia/ccsds/sle/utlfx/images/settings-2.png"));
			this.settingsButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/play-circle.png"));
			this.enableLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/save.png"));
			this.saveAsLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/trash-2.png"));
			this.clearLogButton.setGraphic(new ImageView(image));
		}
		{
			Image image = new Image(
					getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/info.png"));
			this.aboutButton.setGraphic(new ImageView(image));
		}

		// Create images
		this.imageCollection.put(ServiceInstanceBindingStateEnum.BIND_PENDING,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_yellow.png"), 32,
						32, true, true));
		this.imageCollection.put(ServiceInstanceBindingStateEnum.UNBIND_PENDING,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_yellow.png"), 32,
						32, true, true));
		this.imageCollection.put(ServiceInstanceBindingStateEnum.START_PENDING,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_yellow.png"), 32,
						32, true, true));
		this.imageCollection.put(ServiceInstanceBindingStateEnum.STOP_PENDING,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_yellow.png"), 32,
						32, true, true));
		this.imageCollection.put(ServiceInstanceBindingStateEnum.ACTIVE,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_green.png"), 32, 32,
						true, true));
		this.imageCollection.put(ServiceInstanceBindingStateEnum.UNBOUND_WAIT,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_black.png"), 32, 32,
						true, true));
		this.imageCollection.put(ServiceInstanceBindingStateEnum.UNBOUND,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_black.png"), 32, 32,
						true, true));
		this.imageCollection.put(ServiceInstanceBindingStateEnum.READY,
				new Image(getClass().getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/led_grey.png"), 32, 32,
						true, true));

		// Application log expansion behaviour
		this.logTitledPane.expandedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					// Expanded: splitpane enabled
					mainSplitPane.setDividerPositions(1.0, 0.0);

				} else {
					// Collapsed: splitpane maximized and disabled
					mainSplitPane.setDividerPositions(1.0, 0.0);
				}
				mainSplitPane.getDividers().get(0).positionProperty().addListener(new ChangeListener<Number>() {
					@Override
					public void changed(ObservableValue<? extends Number> observable, Number oldvalue,
							Number newvalue) {
						if (!logTitledPane.isExpanded()) {
							mainSplitPane.getDividers().get(0).setPosition(1.0);
						}
					}
				});
			}
		});

		this.logTitledPane.setExpanded(false);

		LOG.log(Level.INFO, SleFxTestTool.NAME + " started");
	}

	protected String buildTableMessage(LogRecord value) {
		String theMessage = value.getMessage().trim();
		if (value.getThrown() != null) {
			return theMessage + " - Exception message: " + value.getThrown().getMessage();
		} else {
			return theMessage;
		}
	}

	protected void selectServiceInstance(ObservableList<ServiceInstance> selectedItems) {
		this.emptyPane.toFront();
		if (selectedItems != null && !selectedItems.isEmpty()) {
			this.si2node.get(selectedItems.get(0)).toFront();
		}
	}

	protected void createRcfServiceInstance(Object[] args) {
		PeerConfiguration conf = (PeerConfiguration) args[0];
		RcfServiceInstanceConfiguration sic = (RcfServiceInstanceConfiguration) args[1];
		RcfServiceInstance si;
		try {
			si = new RcfServiceInstance(conf, sic);
		} catch (Exception e1) {
			LOG.log(Level.SEVERE, "Cannot create RCF service instance " + sic.getServiceInstanceIdentifier()
					+ " from the selected SLE API configuration and Service Instance configuration", e1);
			return;
		}
		// Create pane, add to stack pane and make association
		registerPane(si, "/eu/dariolucia/ccsds/sle/utlfx/fxml/rcf/RcfDisplay.fxml");
	}

	protected void createRafServiceInstance(Object[] args) {
		PeerConfiguration conf = (PeerConfiguration) args[0];
		RafServiceInstanceConfiguration sic = (RafServiceInstanceConfiguration) args[1];
		RafServiceInstance si;
		try {
			si = new RafServiceInstance(conf, sic);
		} catch (Exception e1) {
			LOG.log(Level.SEVERE, "Cannot create RAF service instance " + sic.getServiceInstanceIdentifier()
					+ " from the selected SLE API configuration and Service Instance configuration", e1);
			return;
		}
		// Create pane, add to stack pane and make association
		registerPane(si, "/eu/dariolucia/ccsds/sle/utlfx/fxml/raf/RafDisplay.fxml");
	}

	protected void createRocfServiceInstance(Object[] args) {
		PeerConfiguration conf = (PeerConfiguration) args[0];
		RocfServiceInstanceConfiguration sic = (RocfServiceInstanceConfiguration) args[1];
		RocfServiceInstance si;
		try {
			si = new RocfServiceInstance(conf, sic);
		} catch (Exception e1) {
			LOG.log(Level.SEVERE, "Cannot create ROCF service instance " + sic.getServiceInstanceIdentifier()
					+ " from the selected SLE API configuration and Service Instance configuration", e1);
			return;
		}
		// Create pane, add to stack pane and make association
		registerPane(si, "/eu/dariolucia/ccsds/sle/utlfx/fxml/rocf/RocfDisplay.fxml");
	}

	protected void createCltuServiceInstance(Object[] args) {
		PeerConfiguration conf = (PeerConfiguration) args[0];
		CltuServiceInstanceConfiguration sic = (CltuServiceInstanceConfiguration) args[1];
		CltuServiceInstance si;
		try {
			si = new CltuServiceInstance(conf, sic);
		} catch (Exception e1) {
			LOG.log(Level.SEVERE, "Cannot create CLTU service instance " + sic.getServiceInstanceIdentifier()
					+ " from the selected SLE API configuration and Service Instance configuration", e1);
			return;
		}
		// Create pane, add to stack pane and make association
		registerPane(si, "/eu/dariolucia/ccsds/sle/utlfx/fxml/cltu/CltuDisplay.fxml");
	}

	protected void registerPane(ServiceInstance si, String pane) {
		// Create pane, add to stack pane and make association
		Parent pp = null;
		try {
			URL paneUrl = getClass().getResource(pane);
			FXMLLoader loader = new FXMLLoader(paneUrl);
			pp = loader.load();
			AbstractDisplayController ppCtrl = (AbstractDisplayController) loader.getController();
			this.si2ctrl.put(si, ppCtrl);
			this.si2node.put(si, pp);
			this.stackPane.getChildren().add(pp);
			this.serviceInstanceListView.getItems().add(si);
			ppCtrl.startServiceInstanceMonitoring(si);
			si.register(this);
		} catch (IOException e) {
			LOG.log(Level.SEVERE,
					"Cannot create the view for RCF service instance " + si.getServiceInstanceIdentifier(), e);
		}
		// Select the service instance in the table using an asynch operation, to
		// trigger selection on the stack pane
		if (pp != null) {
			Platform.runLater(() -> {
				this.serviceInstanceListView.getSelectionModel().select(si);
				LOG.log(Level.INFO, "Service instance " + si.getServiceInstanceIdentifier() + " created");
			});
		}
	}

	protected void appendLogRecord(LogRecord record) {
		if (collectLogs) {
			if (record.getLevel().intValue() >= Level.CONFIG.intValue()) {
				Platform.runLater(() -> {
					logTableView.getItems().add(record);
				});
			}
		}
	}

	protected void createServiceInstance(Object[] args) {
		final Popup popup = new Popup();
		popup.setHideOnEscape(false);
		popup.setAutoHide(true);
		popup.setConsumeAutoHidingEvents(true);
		final ProgressIndicator pi = new ProgressIndicator(0);
		pi.setPrefSize(400, 400);
		popup.getContent().add(pi);
		popup.setAutoFix(true);
		popup.setX(this.stackPane.getScene().getWindow().getX() + this.stackPane.getScene().getWindow().getWidth() / 2
				- 200);
		popup.setY(this.stackPane.getScene().getWindow().getY() + this.stackPane.getScene().getWindow().getHeight() / 2
				- 200);

		Platform.runLater(() -> {
			popup.show(this.stackPane.getScene().getWindow());
		});

		PeerConfiguration conf = (PeerConfiguration) args[0];
		for (int i = 1; i < args.length; ++i) {
			final int theIdx = i;
			Platform.runLater(() -> {
				createServiceInstance(conf, (ServiceInstanceConfiguration) args[theIdx]);
				pi.setProgress(theIdx / (double) (args.length - 1));
			});
		}

		Platform.runLater(() -> {
			popup.hide();
		});
	}

	protected void destroyServiceInstance() {
		ServiceInstance toDestroy = this.serviceInstanceListView.getSelectionModel().getSelectedItem();
		LOG.log(Level.FINE, "About to destroy service instance " + toDestroy.getServiceInstanceIdentifier());
		if (toDestroy != null) {
			toDestroy.deregister(this);
			try {
				toDestroy.peerAbort(PeerAbortReasonEnum.OTHER_REASON);
			} catch (Exception e) {
				LOG.log(Level.WARNING, "Peer abort raised an exception while destroying service instance "
						+ toDestroy.getServiceInstanceIdentifier(), e);
			}
			this.serviceInstanceListView.getItems().remove(toDestroy);
			this.si2ctrl.remove(toDestroy).stopServiceInstanceMonitoring();
			Node toRemove = this.si2node.remove(toDestroy);
			this.stackPane.getChildren().remove(toRemove);
			if (!this.serviceInstanceListView.getItems().isEmpty()) {
				this.serviceInstanceListView.getSelectionModel().select(this.serviceInstanceListView.getItems().get(0));
			}
			this.si2cell.remove(toDestroy);
			LOG.log(Level.INFO, "Service instance " + toDestroy.getServiceInstanceIdentifier() + " destroyed");
		}
	}

	private void createServiceInstance(PeerConfiguration conf,
									   ServiceInstanceConfiguration serviceInstanceConfiguration) {
		LOG.log(Level.FINE,
				"About to create service instance " + serviceInstanceConfiguration.getServiceInstanceIdentifier());
		this.type2creator.get(serviceInstanceConfiguration.getType())
				.accept(new Object[] { conf, serviceInstanceConfiguration });
		LOG.log(Level.FINE,
				"Service instance " + serviceInstanceConfiguration.getServiceInstanceIdentifier() + " created");
	}

	@FXML
	private void collectLogsMenuItemSelected(ActionEvent e) {
		this.collectLogs = ((ToggleButton) e.getSource()).isSelected();
	}

	@FXML
	private void clearLogMenuItemSelected(ActionEvent e) {
		Optional<ButtonType> result = DialogUtils.showConfirmation("Clear",
				"Are you sure to clear all collected application logs?", "Clear application logs",
				new ImageView(((ImageView) clearLogButton.getGraphic()).getImage()));
		if (result.get() == ButtonType.OK) {
			clearLogArea();
		}
	}

	private void clearLogArea() {
		this.logTableView.getItems().removeAll(this.logTableView.getItems());
	}

	@FXML
	private void saveLogsMenuItemSelected(ActionEvent e) {
		LogUtil.saveLogsToFile(LOG, this.serviceInstanceListView.getScene().getWindow(), this.logTableView);
	}

	@FXML
	private void aboutButtonSelected(ActionEvent e) {
		DialogUtils.showInfo(SleFxTestTool.NAME + " " + SleFxTestTool.VERSION, SleFxTestTool.NAME + " "
				+ SleFxTestTool.VERSION + "\n" + SleFxTestTool.AUTHOR + "\n\n" + SleFxTestTool.YEARS);
	}

	@FXML
	private void createButtonSelected(ActionEvent e) {
		CreateServiceInstanceDialog sd = new CreateServiceInstanceDialog();
		sd.showAndWait().ifPresent(this::createServiceInstance);
	}

	@FXML
	private void destroyButtonSelected(ActionEvent e) {
		ServiceInstance toDestroy = this.serviceInstanceListView.getSelectionModel().getSelectedItem();
		Optional<ButtonType> result = DialogUtils.showConfirmation("Destroy service instance",
				"Are you sure to destroy the selected service instance?", toDestroy.getServiceInstanceIdentifier(),
				new ImageView(((ImageView) destroySiButton.getGraphic()).getImage()));
		if (result.get() == ButtonType.OK) {
			destroyServiceInstance();
		}
	}

	@FXML
	private void settingsButtonSelected(ActionEvent e) {
		SettingsDialog sd = new SettingsDialog();
		Optional<SettingsDialogResult> res = sd.showAndWait();
		res.ifPresent(obj -> {
			ApplicationConfiguration.instance().setMaxLogs(obj.maxNumLogEntries);
			ApplicationConfiguration.instance().setMaxPdus(obj.maxNumPdus);
			ApplicationConfiguration.instance().setDebugActive(obj.enableDebug);
		});
	}

	@Override
	public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
		Platform.runLater(() -> {
			ServiceInstanceBindingStateEnum currState = state.getState();
			ServiceInstanceBindingStateEnum oldState = this.si2state.get(si);
			if (currState != oldState) {
				this.si2state.put(si, currState);
				this.serviceInstanceListView.refresh();
			}
		});
	}

	@Override
	public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		// Not used
	}

	@Override
	public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		// Not used
	}

	@Override
	public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error,
			Exception exception) {
		// Not used
	}

	@Override
	public void onPduDecodingError(ServiceInstance serviceInstance, byte[] encodedOperation) {
		// Not used
	}

	@Override
	public void onPduHandlingError(ServiceInstance serviceInstance, Object operation, byte[] encodedOperation) {
		// Not used
	}

	private class ServiceInstanceCell extends ListCell<ServiceInstance> {
		private HBox content;
		private Text title;
		private Text update;
		private ImageView imageView = new ImageView();

		/**
		 *
		 */
		public ServiceInstanceCell() {
			super();
			this.title = new Text();
			this.title.setSmooth(true);
			this.title.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, Font.getDefault().getSize()));
			this.update = new Text();
			this.update.setSmooth(true);
			// this.update.setFont(Font.font("System", FontWeight.NORMAL, 12));
			VBox vBox = new VBox(this.title, this.update);
			this.content = new HBox(this.imageView, vBox);
			this.content.setSpacing(10);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see javafx.scene.control.Cell#updateItem(java.lang.Object, boolean)
		 */
		@Override
		protected void updateItem(ServiceInstance item, boolean empty) {
			super.updateItem(item, empty);
			if ((item != null) && !MainController.this.si2cell.containsKey(item)) {
				MainController.this.si2cell.put(item, this);
			}
			if (empty || (item == null)) {
				setGraphic(null);
			} else {
				this.imageView.setImage(MainController.this.imageCollection.get(item.getCurrentBindingState()));
				this.title.setText(item.getServiceInstanceIdentifier());
				this.update.setText("\tState: " + item.getCurrentBindingState());
				setGraphic(this.content);
			}
		}
	}
}
