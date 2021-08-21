package eu.dariolucia.ccsds.cfdp.fx.controller;

import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.util.ResourceBundle;

public class PutRequestDialog implements Initializable {

    public ChoiceBox destinationEntityCombo;
    public CheckBox acknowledgedCheckbox;
    public CheckBox closureCheckbox;
    public CheckBox flowLabelCheckbox;
    public TextField flowLabelTextField;
    public CheckBox fileTransferCheckbox;
    public ComboBox sourceFileCombo;
    public TextField destinationFileTextField;
    public CheckBox segmentationControlCheckbox;
    public ChoiceBox filestoreRequestActionCombo;
    public TextField firstFilenameTextField;
    public TextField secondFilenameTextField;
    public ListView filestoreRequestListView;
    public Button filestoreRequestAddButton;
    public Button filestoreRequestRemoveButton;
    public Button filestoreRequestMoveUpButton;
    public Button filestoreRequestMoveDownButton;
    public TextField messageToUserTextField;
    public Button messageToUserAddButton;
    public ListView messageToUserListView;
    public Button messageToUserRemoveButton;
    public Button messageToUserMoveUpButton;
    public Button messageToUserMoveDownButton;
    public ChoiceBox faultHandlerCodeCombo;
    public ChoiceBox faultHandlerActionCombo;
    public Button faultHandlerAddButton;
    public ListView faultHandlerListView;
    public Button faultHandlerRemoveButton;
    public Button faultHandlerMoveUpButton;
    public Button faultHandlerMoveDownButton;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }

    public void filestoreRequestAddButtonClicked(ActionEvent actionEvent) {
    }

    public void filestoreRequestRemoveButtonClicked(ActionEvent actionEvent) {
    }

    public void filestoreRequestMoveUpButtonClicked(ActionEvent actionEvent) {
    }

    public void filestoreRequestMoveDownButtonClicked(ActionEvent actionEvent) {
    }

    public void messageToUserAddButtonClicked(ActionEvent actionEvent) {
    }

    public void messageToUserRemoveButtonClicked(ActionEvent actionEvent) {
    }

    public void messageToUserMoveUpButtonClicked(ActionEvent actionEvent) {
    }

    public void messageToUserMoveDownButtonClicked(ActionEvent actionEvent) {
    }

    public void faultHandlerAddButtonClicked(ActionEvent actionEvent) {
    }

    public void faultHandlerRemoveButtonClicked(ActionEvent actionEvent) {
    }

    public void faultHandlerMoveUpButtonClicked(ActionEvent actionEvent) {
    }

    public void faultHandlerMoveDownButtonClicked(ActionEvent actionEvent) {
    }
}
