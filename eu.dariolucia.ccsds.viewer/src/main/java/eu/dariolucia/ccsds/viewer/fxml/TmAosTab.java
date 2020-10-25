package eu.dariolucia.ccsds.viewer.fxml;

import eu.dariolucia.ccsds.tmtc.coding.decoder.TmRandomizerDecoder;
import eu.dariolucia.ccsds.tmtc.coding.encoder.TmAsmEncoder;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TmTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import eu.dariolucia.ccsds.viewer.utils.SlePduAttribute;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.fxml.MainController.I_DO_NOT_KNOW;

public class TmAosTab implements Initializable {

    public static final String YES = "YES";
    public static final String NO = "NO";
    public VBox tmAosViewbox;

    public TextArea tmAosTextArea;
    public ChoiceBox<String> tmAosTypeChoicebox;
    public ChoiceBox<String> tmAosRandomizedChoicebox;
    public ChoiceBox<String> tmAosOcfChoicebox;
    public ChoiceBox<String> tmAosFecfChoicebox;
    public ChoiceBox<String> tmAosFhcfChoicebox;
    public TextField tmAosInsertZoneTextField;
    public TextField tmAosSecurityHeaderTextField;
    public TextField tmAosSecurityTrailerTextField;
    public TextArea tmAosResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        tmAosTypeChoicebox.getItems().add(I_DO_NOT_KNOW);
        tmAosTypeChoicebox.getItems().addAll("TM Frame CADU", "AOS CADU", "TM Frame", "AOS");
        tmAosTypeChoicebox.getSelectionModel().select(0);

        tmAosRandomizedChoicebox.getItems().add(I_DO_NOT_KNOW);
        tmAosRandomizedChoicebox.getItems().addAll(YES, NO);
        tmAosRandomizedChoicebox.getSelectionModel().select(0);

        tmAosOcfChoicebox.getItems().addAll(YES, NO);
        tmAosOcfChoicebox.getSelectionModel().select(0);

        tmAosFecfChoicebox.getItems().addAll(YES, NO);
        tmAosFecfChoicebox.getSelectionModel().select(1);

        tmAosFhcfChoicebox.getItems().addAll(YES, NO);
        tmAosFhcfChoicebox.getSelectionModel().select(1);
    }

    public void onTmAosDecodeButtonClicked(ActionEvent actionEvent) {
        String data = tmAosTextArea.getText().toUpperCase();
        if(data.isBlank()) {
            return;
        }
        byte[] bdata = StringUtil.toByteArray(data);

        // Let's try to see what we have to do
        try {
            if (tmAosTypeChoicebox.getSelectionModel().getSelectedItem().equals(I_DO_NOT_KNOW)) {
                if (data.startsWith(StringUtil.toHexDump(TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER).toUpperCase())) {
                    // It is a CADU
                    processCadu(bdata);
                } else {
                    // Assume frame
                    processFrame(bdata);
                }
                return;
            }
            if (tmAosTypeChoicebox.getSelectionModel().getSelectedItem().equals("TM Frame CADU") || tmAosTypeChoicebox.getSelectionModel().getSelectedItem().equals("AOS CADU")) {
                processCadu(bdata);
            } else {
                processFrame(bdata);
            }
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void processFrame(byte[] bdata) {
        // Check if it is randomised
        if(tmAosRandomizedChoicebox.getSelectionModel().getSelectedItem().equals(YES)) {
            bdata = new TmRandomizerDecoder().apply(bdata);
        } else if(tmAosRandomizedChoicebox.getSelectionModel().getSelectedItem().equals(NO)){

        }
    }

    private void processCadu(byte[] bdata) {
        if(Arrays.equals(bdata, 0, 4, TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER, 0, 4)) {
            // Try to guess the frame size
            int idepth = (bdata.length - 4) / 255;
            // Extract n blocks of 223 bytes (assuming RS 223/255 is used)
            processFrame(Arrays.copyOfRange(bdata, 4, 4 + (223 * idepth)));
        } else {
            error("Data does not start with attached sync marker " + StringUtil.toHexDump(TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER).toUpperCase());
        }
    }

    private void error(String error) {
        tmAosResultTextArea.setText(error);
    }

    public void onTmAosClearButtonClicked(ActionEvent actionEvent) {
        tmAosTextArea.clear();
        tmAosResultTextArea.clear();
    }

}
