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

import java.net.URL;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import eu.dariolucia.ccsds.sle.utlfx.dialogs.ComboDialog;
import eu.dariolucia.ccsds.sle.utlfx.dialogs.WaitForBindDialog;
import eu.dariolucia.ccsds.sle.utlfx.manager.DataRateManager;
import eu.dariolucia.ccsds.sle.utl.si.BindDiagnosticsEnum;
import eu.dariolucia.ccsds.sle.utl.si.IServiceInstanceListener;
import eu.dariolucia.ccsds.sle.utl.si.LockStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.PeerAbortReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.ProductionStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstance;
import eu.dariolucia.ccsds.sle.utl.si.ServiceInstanceState;
import eu.dariolucia.ccsds.sle.utl.si.UnbindReasonEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuProductionStatusEnum;
import eu.dariolucia.ccsds.sle.utl.si.cltu.CltuUplinkStatusEnum;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.chart.AreaChart;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

public abstract class AbstractDisplayController implements Initializable, IServiceInstanceListener {

	private static final Logger LOG = Logger.getLogger(AbstractDisplayController.class.getName());

	// Service instance
	protected ServiceInstance serviceInstance;

	// Toolbar
	@FXML
	protected MenuButton sleOperationsMenuButton;

	@FXML
	protected Region springer;

	// Accordion
	@FXML
	protected Accordion accordion;

	@FXML
	protected TitledPane overviewTitledPane;

	// Overview accordion - Common
	@FXML
	protected VBox propertiesVBox;

	protected GridPane propertiesGrid;

	@FXML
	protected Label siidText;

	// Overview accordion - Data/PDU rate charts
	@FXML
	protected Label txBitrateLabel;
	@FXML
	protected Label rxBitrateLabel;
	@FXML
	protected Label txDataLabel;
	@FXML
	protected Label rxDataLabel;

	@FXML
	protected AreaChart<Instant, Number> mbitSecChart;
	@FXML
	protected AreaChart<Instant, Number> pduSecChart;

	// Overview accordion - Charts manager
	protected DataRateManager dataRateManager;

	// PDU Inspector Accordion
	@FXML
	protected PduInspectorController pduInspectorController;

	// Log Inspector Accordion
	@FXML
	protected LogInspectorController logInspectorController;

	// Label mappers: from service instance state to label text, from service
	// instance state to style
	protected Map<TextField, Pair<Function<ServiceInstanceState, String>, Function<ServiceInstanceState, String>>> mappers = new HashMap<>();

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		TabPane propertyTab = new TabPane();
		propertiesVBox.getChildren().add(propertyTab);
		VBox.setVgrow(propertyTab, Priority.ALWAYS);
		// General props
		createPropertyGrid();
		Tab t = new Tab("General");
		t.setClosable(false);
		t.setContent(propertiesGrid);
		registerGeneralProperties();
		if (!propertiesGrid.getChildren().isEmpty()) {
			propertyTab.getTabs().add(t);
		}

		// Parameter props
		createPropertyGrid();
		t = new Tab("Parameters");
		t.setClosable(false);
		t.setContent(propertiesGrid);
		doRegisterParametersProperties();
		if (!propertiesGrid.getChildren().isEmpty()) {
			propertyTab.getTabs().add(t);
		}

		// Notification props
		createPropertyGrid();
		t = new Tab("Notification");
		t.setClosable(false);
		t.setContent(propertiesGrid);
		doRegisterNotificationProperties();
		if (!propertiesGrid.getChildren().isEmpty()) {
			propertyTab.getTabs().add(t);
		}

		propertiesGrid = null;

		this.accordion.setExpandedPane(this.accordion.getPanes().get(0));
		HBox.setHgrow(this.springer, Priority.ALWAYS);
		//
		{
			Image image = new Image(getClass()
					.getResourceAsStream("/eu/dariolucia/ccsds/sle/utlfx/images/cast.png"));
			this.overviewTitledPane.setGraphic(new ImageView(image));
		}
		// Configure operations (subclasses can configure custom operations/objects)
		configureExtension();
	}

	private void createPropertyGrid() {
		propertiesGrid = new GridPane();
		propertiesGrid.setHgap(10);
		propertiesGrid.setVgap(5);
		propertiesGrid.setPadding(new Insets(5, 10, 5, 10));
		propertiesGrid.setPrefWidth(350);
		propertiesGrid.setMaxWidth(350);
	}

	private void registerGeneralProperties() {
		registerLabel("Initiator ID", "The ID of the initiator", (state) -> state.getInitiatorIdentifier());
		registerLabel("Responder ID", "The ID of the responder", (state) -> state.getResponderIdentifier());
		registerLabel("Responder Port ID", "The ID of the responder port",
				(state) -> state.getResponderPortIdentifier());
		registerLabel("State", "The current state of the SLE service instance", (state) -> state.getState().name(),
				this::mapSiStateStyle);
		registerLabel("Version", "The version of the SLE protocol to use (BIND delivered)",
				(state) -> toString(state.getSleVersion()));
		registerLabel("Return Timeout", "The timeout in seconds for return operations",
				(state) -> toString(state.getReturnTimeoutPeriod()));
		registerSeparator();
		doRegisterCommonProperties();

		this.propertiesVBox.getParent().getParent().layout();
	}

	private String mapSiStateStyle(ServiceInstanceState sis) {
		switch (sis.getState()) {
		case UNBOUND:
		case UNBOUND_WAIT:
			return "-utlfx-background-color: gray;";
		case READY:
			return "-utlfx-background-color: blue;";
		case ACTIVE:
			return "-utlfx-background-color: green;";
		case BIND_PENDING:
		case UNBIND_PENDING:
		case START_PENDING:
		case STOP_PENDING:
			return "-utlfx-background-color: yellow;";
		default:
			return "";
		}
	}

	protected String toStyle(LockStatusEnum ls) {
		if (ls == null) {
			return "";
		} else {
			switch (ls) {
			case IN_LOCK:
				return "-utlfx-background-color: green;";
			case NOT_IN_USE:
				return "-utlfx-background-color: gray;";
			case OUT_OF_LOCK:
				return "-utlfx-background-color: yellow;";
			default:
				return "";
			}
		}
	}

	protected String toStyle(CltuUplinkStatusEnum ls) {
		if (ls == null) {
			return "";
		} else {
			switch (ls) {
			case NOMINAL:
				return "-utlfx-background-color: green;";
			case UPLINK_STATUS_NOT_AVAILABLE:
				return "-utlfx-background-color: gray;";
			case NO_BIT_LOCK:
			case NO_RF_AVAILABLE:
				return "-utlfx-background-color: yellow;";
			default:
				return "";
			}
		}
	}

	protected String toStyle(CltuProductionStatusEnum ps) {
		if (ps == null) {
			return "";
		} else {
			switch (ps) {
			case OPERATIONAL:
				return "-utlfx-background-color: green;";
			case CONFIGURED:
				return "-utlfx-background-color: blue;";
			case INTERRUPTED:
			case HALTED:
				return "-utlfx-background-color: yellow;";
			default:
				return "";
			}
		}
	}

	protected String toStyle(ProductionStatusEnum ps) {
		if (ps == null) {
			return "";
		} else {
			switch (ps) {
			case RUNNING:
				return "-utlfx-background-color: green;";
			case INTERRUPTED:
			case HALTED:
				return "-utlfx-background-color: yellow;";
			default:
				return "";
			}
		}
	}

	protected String toString(Integer n) {
		if (n == null) {
			return "";
		} else {
			return String.valueOf(n.intValue());
		}
	}

	protected String toString(Long n) {
		if (n == null) {
			return "";
		} else {
			return String.valueOf(n.longValue());
		}
	}

	protected void registerSeparator() {
		Separator s = new Separator(Orientation.HORIZONTAL);
		this.propertiesGrid.add(s, 0, propertiesGrid.getChildren().size() / 2, 2, 1);
		this.propertiesGrid.add(new Label(), 1, propertiesGrid.getChildren().size() / 2);
	}

	protected <T extends ServiceInstanceState> TextField registerLabel(String labelText, String description,
			Function<T, String> valueMapper) {
		return registerLabel(labelText, description, null, valueMapper, null);
	}

	protected <T extends ServiceInstanceState> TextField registerLabel(String labelText, String description,
			Function<T, String> valueMapper, Function<T, String> styleMapper) {
		return registerLabel(labelText, description, null, valueMapper, styleMapper);
	}

	protected <T extends ServiceInstanceState> TextField registerLabel(String labelText, String description,
			Class<T> clazz, Function<T, String> valueMapper) {
		return registerLabel(labelText, description, clazz, valueMapper, null);
	}

	@SuppressWarnings("unchecked")
	protected <T extends ServiceInstanceState> TextField registerLabel(String labelText, String description,
			Class<T> clazz, Function<T, String> valueMapper, Function<T, String> styleMapper) {
		Label text = new Label(labelText);
		text.setMinWidth(160);
		text.setMaxWidth(160);
		text.setMinHeight(26);
		text.setMaxHeight(26);
		TextField value = new TextField();
		value.setEditable(false);
		value.setMinWidth(240);
		value.setMaxWidth(240);
		value.setMinHeight(26);
		value.setMaxHeight(26);
		value.setStyle("");
		if (description != null) {
			value.setTooltip(new Tooltip(description));
		}
		this.mappers.put(value,
				new Pair<Function<ServiceInstanceState, String>, Function<ServiceInstanceState, String>>(
						(Function<ServiceInstanceState, String>) valueMapper,
						(Function<ServiceInstanceState, String>) styleMapper));
		this.propertiesGrid.add(text, 0, propertiesGrid.getChildren().size() / 2);
		this.propertiesGrid.add(value, 1, propertiesGrid.getChildren().size() / 2);

		return value;
	}

	protected void updateProperties(ServiceInstanceState state) {
		this.siidText.setText(state.getServiceInstanceIdentifier());

		for (Map.Entry<TextField, Pair<Function<ServiceInstanceState, String>, Function<ServiceInstanceState, String>>> entry : this.mappers
				.entrySet()) {
			TextField l = entry.getKey();
			Function<ServiceInstanceState, String> valMapper = entry.getValue().getKey();
			Function<ServiceInstanceState, String> styleMapper = entry.getValue().getValue();

			String value = valMapper.apply(state);
			if (value == null) {
				value = "";
			}

			String style = styleMapper != null ? styleMapper.apply(state) : "";
			if (style == null) {
				style = "";
			}

			l.setText(value);
			l.setStyle(style);
		}
	}

	protected void sendWaitForBind(Object[] args) {
		try {
			boolean sendPositiveBindReturn = (Boolean) args[0];
			BindDiagnosticsEnum negativeBindReturnDiagnostics = (BindDiagnosticsEnum) args[1];
			boolean sendPositiveUnbindReturn = (Boolean) args[2];

			this.serviceInstance.setUnbindReturnBehaviour(sendPositiveUnbindReturn);
			this.serviceInstance.waitForBind(sendPositiveBindReturn, negativeBindReturnDiagnostics);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding wait-for-bind request to "
					+ this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendBind(int versionToUse) {
		try {
			this.serviceInstance.bind(versionToUse);
		} catch (Exception e) {
			LOG.log(Level.SEVERE,
					"Error while forwarding bind request to " + this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	protected void sendUnbind(UnbindReasonEnum reason) {
		try {
			this.serviceInstance.unbind(reason);
		} catch (Exception e) {
			LOG.log(Level.SEVERE,
					"Error while forwarding unbind request to " + this.serviceInstance.getServiceInstanceIdentifier(),
					e);
		}
	}

	protected void sendPeerAbort(PeerAbortReasonEnum reason) {
		try {
			this.serviceInstance.peerAbort(reason);
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Error while forwarding peer abort request to "
					+ this.serviceInstance.getServiceInstanceIdentifier(), e);
		}
	}

	public void waitForBindMenuItemSelected(ActionEvent e) {
		WaitForBindDialog wbDialog = new WaitForBindDialog();
		wbDialog.showAndWait().ifPresent(
				(res) -> sendWaitForBind(new Object[] { res.sendPosBindReturn, null, res.sendPosUnbindReturn }));
	}

	public void bindMenuItemSelected(ActionEvent e) {
		ComboDialog<Integer> bindDialog = new ComboDialog<Integer>("Bind", "Invoke a BIND operation", "Version number",
				"The version number to be set in the BIND operation", new Integer[] { 1, 2, 3, 4, 5 }, 1);
		bindDialog.showAndWait().ifPresent(this::sendBind);
	}

	public void unbindMenuItemSelected(ActionEvent e) {
		ComboDialog<UnbindReasonEnum> unbindDialog = new ComboDialog<UnbindReasonEnum>("Unbind",
				"Invoke a UNBIND operation", "Reason", "The unbind reason to be set in the UNBIND operation",
				UnbindReasonEnum.values(), UnbindReasonEnum.SUSPEND);
		unbindDialog.showAndWait().ifPresent(this::sendUnbind);
	}

	public void peerAbortMenuItemSelected(ActionEvent e) {
		ComboDialog<PeerAbortReasonEnum> peerAbortDialog = new ComboDialog<PeerAbortReasonEnum>("Peer Abort",
				"Invoke a PEER-ABORT operation", "Reason", "The abort reason to be set in the PEER-ABORT operation",
				PeerAbortReasonEnum.values(), PeerAbortReasonEnum.OPERATIONAL_REQUIREMENTS);
		peerAbortDialog.showAndWait().ifPresent(this::sendPeerAbort);
	}

	public void startServiceInstanceMonitoring(ServiceInstance theServiceInstance) {
		if (this.serviceInstance != null) {
			throw new IllegalStateException("Only a single service instance can be set, once and forever");
		}
		this.serviceInstance = theServiceInstance;
		// Subscribe to the service instance
		this.serviceInstance.register(this);
		// Activate log console
		this.logInspectorController.activate(this.serviceInstance.getServiceInstanceIdentifier());
		// Create and activate chart managers for data rates
		this.dataRateManager = new DataRateManager(this.serviceInstance, this.mbitSecChart, this.pduSecChart,
				this.txBitrateLabel, this.rxBitrateLabel, this.txDataLabel, this.rxDataLabel);
		this.dataRateManager.activate();
	}

	public void stopServiceInstanceMonitoring() {
		if (this.serviceInstance == null) {
			throw new IllegalStateException("No service instance set");
		}
		// Unsubscribe to the service instance
		this.serviceInstance.deregister(this);
		// Deactivate chart managers
		this.dataRateManager.deactivate();
		// Deregister log console
		this.logInspectorController.deactivate();
		// Deactivate pdu manager
		this.pduInspectorController.deactivate();
	}

	@Override
	public void onStateUpdated(ServiceInstance si, ServiceInstanceState state) {
		Platform.runLater(() -> {
			// this.stateAttributeManager.update(state);
			updateProperties(state);
		});
	}

	@Override
	public void onPduReceived(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		this.pduInspectorController.onPduReceived(si, operation, name, encodedOperation);
	}

	@Override
	public void onPduSent(ServiceInstance si, Object operation, String name, byte[] encodedOperation) {
		this.pduInspectorController.onPduSent(si, operation, name, encodedOperation);
	}

	@Override
	public void onPduSentError(ServiceInstance si, Object operation, String name, byte[] encodedOperation, String error,
			Exception exception) {
		this.pduInspectorController.onPduSentError(si, operation, name, encodedOperation, error, exception);
	}

	@Override
	public void onPduDecodingError(ServiceInstance si, byte[] encodedOperation) {
		this.pduInspectorController.onPduDecodingError(si, encodedOperation);
	}

	@Override
	public void onPduHandlingError(ServiceInstance si, Object operation, byte[] encodedOperation) {
		this.pduInspectorController.onPduHandlingError(si, operation, encodedOperation);
	}

	protected abstract void configureExtension();

	protected abstract void doRegisterCommonProperties();

	protected abstract void doRegisterParametersProperties();

	protected abstract void doRegisterNotificationProperties();
}
