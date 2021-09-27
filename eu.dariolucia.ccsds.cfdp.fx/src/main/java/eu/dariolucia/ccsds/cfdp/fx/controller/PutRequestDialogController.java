package eu.dariolucia.ccsds.cfdp.fx.controller;

import eu.dariolucia.ccsds.cfdp.entity.ICfdpEntity;
import eu.dariolucia.ccsds.cfdp.entity.request.PutRequest;
import eu.dariolucia.ccsds.cfdp.filestore.FilestoreException;
import eu.dariolucia.ccsds.cfdp.filestore.IVirtualFilestore;
import eu.dariolucia.ccsds.cfdp.fx.dialogs.DialogUtils;
import eu.dariolucia.ccsds.cfdp.mib.FaultHandlerStrategy;
import eu.dariolucia.ccsds.cfdp.mib.RemoteEntityConfigurationInformation;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.ConditionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.ActionCode;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FaultHandlerOverrideTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.FilestoreRequestTLV;
import eu.dariolucia.ccsds.cfdp.protocol.pdu.tlvs.MessageToUserTLV;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Pair;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PutRequestDialogController implements Initializable {

    private static final Logger LOG = Logger.getLogger(PutRequestDialogController.class.getName());
    public static final String ADD_IMAGE = "/eu/dariolucia/ccsds/cfdp/fx/images/plus.png";
    public static final String REMOVE_IMAGE = "/eu/dariolucia/ccsds/cfdp/fx/images/minus.png";
    public static final String MOVE_UP_IMAGE = "/eu/dariolucia/ccsds/cfdp/fx/images/arrowhead-up.png";
    public static final String MOVE_DOWN_IMAGE = "/eu/dariolucia/ccsds/cfdp/fx/images/arrowhead-down.png";

    @FXML
    private ChoiceBox<Long> destinationEntityCombo;
    @FXML
    private CheckBox acknowledgedCheckbox;
    @FXML
    private CheckBox closureCheckbox;
    @FXML
    private CheckBox flowLabelCheckbox;
    @FXML
    private TextField flowLabelTextField;
    @FXML
    private CheckBox fileTransferCheckbox;
    @FXML
    private ComboBox<String> sourceFileCombo;
    @FXML
    private TextField destinationFileTextField;
    @FXML
    private CheckBox segmentationControlCheckbox;
    @FXML
    private ChoiceBox<ActionCode> filestoreRequestActionCombo;
    @FXML
    private TextField firstFilenameTextField;
    @FXML
    private TextField secondFilenameTextField;
    @FXML
    private ListView<FilestoreRequestTLV> filestoreRequestListView;
    @FXML
    private Button filestoreRequestAddButton;
    @FXML
    private Button filestoreRequestRemoveButton;
    @FXML
    private Button filestoreRequestMoveUpButton;
    @FXML
    private Button filestoreRequestMoveDownButton;
    @FXML
    private TextField messageToUserTextField;
    @FXML
    private Button messageToUserAddButton;
    @FXML
    private ListView<MessageToUserTLV> messageToUserListView;
    @FXML
    private Button messageToUserRemoveButton;
    @FXML
    private Button messageToUserMoveUpButton;
    @FXML
    private Button messageToUserMoveDownButton;
    @FXML
    private ChoiceBox<ConditionCode> faultHandlerCodeCombo;
    @FXML
    private ChoiceBox<FaultHandlerOverrideTLV.HandlerCode> faultHandlerActionCombo;
    @FXML
    private Button faultHandlerAddButton;
    @FXML
    private ListView<FaultHandlerOverrideTLV> faultHandlerListView;
    @FXML
    private Button faultHandlerRemoveButton;
    @FXML
    private Button faultHandlerMoveUpButton;
    @FXML
    private Button faultHandlerMoveDownButton;

    private Button okButton;
    private ICfdpEntity entity;

    public static Pair<Node, PutRequestDialogController> createDialog(ICfdpEntity entity) throws IOException {
        URL datePickerUrl = PutRequestDialogController.class.getResource("/eu/dariolucia/ccsds/cfdp/fx/fxml/PutRequestDialog.fxml");
        FXMLLoader loader = new FXMLLoader(datePickerUrl);
        VBox root = loader.load();
        PutRequestDialogController controller = loader.getController();
        controller.setCfdpEntity(entity);
        return new Pair<>(root, controller);
    }

    private void setCfdpEntity(ICfdpEntity entity) {
        this.entity = entity;
        // Populate the destination IDs
        for(RemoteEntityConfigurationInformation remoteEntity : entity.getMib().getRemoteEntities()) {
            destinationEntityCombo.getItems().add(remoteEntity.getRemoteEntityId());
        }
        // Populate the source filestore entries
        IVirtualFilestore fs = entity.getFilestore();
        try {
            List<String> files = fs.listDirectory("/", true);
            sourceFileCombo.getItems().addAll(files);
        } catch (FilestoreException e) {
            if(LOG.isLoggable(Level.SEVERE)) {
                LOG.log(Level.SEVERE, String.format("Error while reading filestore: %s", e.getMessage()), e);
            }
            DialogUtils.showError("Error while reading filestore", "Cannot load list of files from local filestore: " + e.getMessage());
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        filestoreRequestActionCombo.getItems().addAll(ActionCode.values());
        filestoreRequestRemoveButton.disableProperty().bind(filestoreRequestListView.getSelectionModel().selectedItemProperty().isNull());
        filestoreRequestMoveUpButton.disableProperty().bind(filestoreRequestListView.getSelectionModel().selectedItemProperty().isNull());
        filestoreRequestMoveDownButton.disableProperty().bind(filestoreRequestListView.getSelectionModel().selectedItemProperty().isNull());

        faultHandlerCodeCombo.getItems().addAll(ConditionCode.values());
        faultHandlerActionCombo.getItems().addAll(FaultHandlerOverrideTLV.HandlerCode.values());
        faultHandlerRemoveButton.disableProperty().bind(faultHandlerListView.getSelectionModel().selectedItemProperty().isNull());
        faultHandlerMoveUpButton.disableProperty().bind(faultHandlerListView.getSelectionModel().selectedItemProperty().isNull());
        faultHandlerMoveDownButton.disableProperty().bind(faultHandlerListView.getSelectionModel().selectedItemProperty().isNull());

        messageToUserRemoveButton.disableProperty().bind(messageToUserListView.getSelectionModel().selectedItemProperty().isNull());
        messageToUserMoveUpButton.disableProperty().bind(messageToUserListView.getSelectionModel().selectedItemProperty().isNull());
        messageToUserMoveDownButton.disableProperty().bind(messageToUserListView.getSelectionModel().selectedItemProperty().isNull());

        closureCheckbox.disableProperty().bind(acknowledgedCheckbox.selectedProperty());

        sourceFileCombo.disableProperty().bind(fileTransferCheckbox.selectedProperty().not());
        destinationFileTextField.disableProperty().bind(fileTransferCheckbox.selectedProperty().not());
        segmentationControlCheckbox.disableProperty().bind(fileTransferCheckbox.selectedProperty().not());

        flowLabelTextField.disableProperty().bind(flowLabelCheckbox.selectedProperty().not());

        // Graphics
        DialogUtils.attachImage(getClass(), messageToUserAddButton, ADD_IMAGE);
        DialogUtils.attachImage(getClass(), messageToUserRemoveButton, REMOVE_IMAGE);
        DialogUtils.attachImage(getClass(), messageToUserMoveUpButton, MOVE_UP_IMAGE);
        DialogUtils.attachImage(getClass(), messageToUserMoveDownButton, MOVE_DOWN_IMAGE);

        DialogUtils.attachImage(getClass(), faultHandlerAddButton, ADD_IMAGE);
        DialogUtils.attachImage(getClass(), faultHandlerRemoveButton, REMOVE_IMAGE);
        DialogUtils.attachImage(getClass(), faultHandlerMoveUpButton, MOVE_UP_IMAGE);
        DialogUtils.attachImage(getClass(), faultHandlerMoveDownButton, MOVE_DOWN_IMAGE);

        DialogUtils.attachImage(getClass(), filestoreRequestAddButton, ADD_IMAGE);
        DialogUtils.attachImage(getClass(), filestoreRequestRemoveButton, REMOVE_IMAGE);
        DialogUtils.attachImage(getClass(), filestoreRequestMoveUpButton, MOVE_UP_IMAGE);
        DialogUtils.attachImage(getClass(), filestoreRequestMoveDownButton, MOVE_DOWN_IMAGE);
    }

    public void filestoreRequestAddButtonClicked(ActionEvent actionEvent) {
        ActionCode code = filestoreRequestActionCombo.getSelectionModel().getSelectedItem();
        if(code != null) {
            String firstFileName = firstFilenameTextField.getText().trim();
            String secondFileName = secondFilenameTextField.getText().trim();
            firstFileName = firstFileName.isEmpty() ? null : firstFileName;
            secondFileName = secondFileName.isEmpty() ? null : secondFileName;
            FilestoreRequestTLV req = new FilestoreRequestTLV(code, firstFileName, secondFileName);
            filestoreRequestListView.getItems().add(req);
        }
    }

    public void filestoreRequestRemoveButtonClicked(ActionEvent actionEvent) {
        removeSelected(filestoreRequestListView);
    }

    private void removeSelected(ListView<?> list) {
        int selected = list.getSelectionModel().getSelectedIndex();
        if(selected >= 0) {
            list.getItems().remove(selected);
        }
    }

    public void filestoreRequestMoveUpButtonClicked(ActionEvent actionEvent) {
        moveUpSelected(filestoreRequestListView);
    }

    private <T> void moveUpSelected(ListView<T> list) {
        int selected = list.getSelectionModel().getSelectedIndex();
        if(selected > 0) {
            T sel = list.getItems().remove(selected);
            list.getItems().add(selected - 1, sel);
            list.getSelectionModel().select(selected - 1);
        }
    }

    public void filestoreRequestMoveDownButtonClicked(ActionEvent actionEvent) {
        moveDownSelected(filestoreRequestListView);
    }

    private <T> void moveDownSelected(ListView<T> list) {
        int selected = list.getSelectionModel().getSelectedIndex();
        if(selected >= 0 && selected < list.getItems().size() - 1) {
            T sel = list.getItems().remove(selected);
            list.getItems().add(selected + 1, sel);
            list.getSelectionModel().select(selected + 1);
        }
    }

    public void messageToUserAddButtonClicked(ActionEvent actionEvent) {
        String hex = messageToUserTextField.getText().trim();
        if(!hex.isBlank()) {
            // Match with
            if(!"[0-9|a-f|A-F]*".matches(hex)) {
                DialogUtils.showError("Error parsing string", "The provided string is not a valid hex dump");
                return;
            }
            try {
                byte[] message = StringUtil.toByteArray(hex);
                messageToUserListView.getItems().add(new MessageToUserTLV(message));
            } catch (Exception e) {
                DialogUtils.showError("Error parsing string", "The provided string is not a valid hex dump");
            }
        }
    }

    public void messageToUserRemoveButtonClicked(ActionEvent actionEvent) {
        removeSelected(messageToUserListView);
    }

    public void messageToUserMoveUpButtonClicked(ActionEvent actionEvent) {
        moveUpSelected(messageToUserListView);
    }

    public void messageToUserMoveDownButtonClicked(ActionEvent actionEvent) {
        moveDownSelected(messageToUserListView);
    }

    public void faultHandlerAddButtonClicked(ActionEvent actionEvent) {
        ConditionCode ccode = faultHandlerCodeCombo.getSelectionModel().getSelectedItem();
        FaultHandlerOverrideTLV.HandlerCode code = faultHandlerActionCombo.getSelectionModel().getSelectedItem();
        if(code != null && ccode != null) {
            faultHandlerListView.getItems().add(new FaultHandlerOverrideTLV(ccode, code));
        }
    }

    public void faultHandlerRemoveButtonClicked(ActionEvent actionEvent) {
        removeSelected(faultHandlerListView);
    }

    public void faultHandlerMoveUpButtonClicked(ActionEvent actionEvent) {
        moveUpSelected(faultHandlerListView);
    }

    public void faultHandlerMoveDownButtonClicked(ActionEvent actionEvent) {
        moveDownSelected(faultHandlerListView);
    }

    public void bindOkButton(Button okButton) {
        this.okButton = okButton;
        // Maybe it should be a bit better than this, but this is a test tool after all...
        this.okButton.disableProperty().bind(destinationEntityCombo.getSelectionModel().selectedItemProperty().isNull());
    }

    public PutRequest createPutRequest() {
        return new PutRequest(destinationEntityCombo.getSelectionModel().getSelectedItem(),
                fileTransferCheckbox.isSelected() ? sourceFileCombo.getSelectionModel().getSelectedItem() : null,
                fileTransferCheckbox.isSelected() ? destinationFileTextField.getText() : null,
                fileTransferCheckbox.isSelected() && segmentationControlCheckbox.isSelected(),
                flowLabelCheckbox.isSelected() ? StringUtil.toByteArray(flowLabelTextField.getText()) : null,
                acknowledgedCheckbox.isSelected(),
                !acknowledgedCheckbox.isSelected() ? closureCheckbox.isSelected() : null,
                messageToUserListView.getItems().isEmpty() ? null : messageToUserListView.getItems(),
                faultHandlerListView.getItems().isEmpty() ? null : buildFaultHandlerMap(),
                filestoreRequestListView.getItems().isEmpty() ? null : filestoreRequestListView.getItems());
    }

    private Map<ConditionCode, FaultHandlerStrategy.Action> buildFaultHandlerMap() {
        Map<ConditionCode, FaultHandlerStrategy.Action> toReturn = new EnumMap<>(ConditionCode.class);
        for(FaultHandlerOverrideTLV tlv : faultHandlerListView.getItems()) {
            toReturn.put(tlv.getConditionCode(), tlv.getHandlerCode().toAction());
        }
        return toReturn;
    }
}
