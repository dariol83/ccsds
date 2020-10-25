package eu.dariolucia.ccsds.viewer.fxml;

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

        tmAosOcfChoicebox.getItems().add(I_DO_NOT_KNOW);
        tmAosOcfChoicebox.getItems().addAll(YES, NO);
        tmAosOcfChoicebox.getSelectionModel().select(0);

        tmAosFecfChoicebox.getItems().add(I_DO_NOT_KNOW);
        tmAosFecfChoicebox.getItems().addAll(YES, NO);
        tmAosFecfChoicebox.getSelectionModel().select(0);

        tmAosFhcfChoicebox.getItems().add(I_DO_NOT_KNOW);
        tmAosFhcfChoicebox.getItems().addAll(YES, NO);
        tmAosFhcfChoicebox.getSelectionModel().select(0);
    }

    public void onTmAosDecodeButtonClicked(ActionEvent actionEvent) {
        String data = tmAosTextArea.getText().toUpperCase();
        if(data.isBlank()) {
            return;
        }
        byte[] bdata = StringUtil.toByteArray(data);

        // Let's try to see what we have to do
        if(tmAosTypeChoicebox.getSelectionModel().getSelectedItem().equals(I_DO_NOT_KNOW)) {
            if(data.startsWith(StringUtil.toHexDump(TmAsmEncoder.DEFAULT_ATTACHED_SYNC_MARKER).toUpperCase())) {
                // It is a CADU
            }
        }

        // If you reach this position, it means no parsers
        tmAosResultTextArea.setText(null);
    }

    private void printPdu(TreeItem<SlePduAttribute> rootItem) {
        StringBuilder textBuilder = new StringBuilder("");
        printRecursive(0, rootItem, textBuilder);
        tmAosResultTextArea.setText(textBuilder.toString());
    }

    private void printRecursive(int level, TreeItem<SlePduAttribute> item, StringBuilder builder) {
        // Add tabs
        for(int i = 0; i < level; ++i) {
            builder.append('\t');
        }
        // Add values
        builder.append(String.format("%s %s %s", item.getValue().getName(), item.getValue().getType(), item.getValue().getValueAsString()));
        // Add new line
        builder.append('\n');
    }

    public void onTmAosClearButtonClicked(ActionEvent actionEvent) {
        tmAosTextArea.clear();
        tmAosResultTextArea.clear();
    }

}
