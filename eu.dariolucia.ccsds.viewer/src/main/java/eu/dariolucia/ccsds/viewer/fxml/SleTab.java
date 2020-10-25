package eu.dariolucia.ccsds.viewer.fxml;

import eu.dariolucia.ccsds.encdec.value.StringUtil;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus.CltuUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuProviderToUserPduV1toV3;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus.CltuProviderToUserPduV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus.RafUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPduV1toV2;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus.RafProviderToUserPduV3toV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus.RcfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPduV1;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus.RcfProviderToUserPduV2toV4;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus.RocfUserToProviderPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfProviderToUserPdu;
import eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus.RocfProviderToUserPduV1toV4;
import eu.dariolucia.ccsds.sle.utl.si.ApplicationIdentifierEnum;
import eu.dariolucia.ccsds.viewer.utils.SleParser;
import eu.dariolucia.ccsds.viewer.utils.SlePdu;
import eu.dariolucia.ccsds.viewer.utils.SlePduAttribute;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SleTab implements Initializable {

    public VBox sleViewbox;

    public TextArea sleTextArea;
    public ChoiceBox<String> sleTypeChoicebox;
    public ChoiceBox<String> sleSenderChoicebox;
    public ChoiceBox<String> sleVersionChoicebox;
    public TextArea sleResultTextArea;

    private List<SleParser> parsers = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        sleTypeChoicebox.getItems().add(MainController.I_DO_NOT_KNOW);
        sleTypeChoicebox.getItems().addAll(Arrays.stream(ApplicationIdentifierEnum.values()).map(ApplicationIdentifierEnum::name).collect(Collectors.toList()));
        sleTypeChoicebox.getSelectionModel().select(0);

        sleSenderChoicebox.getItems().add(MainController.I_DO_NOT_KNOW);
        sleSenderChoicebox.getItems().addAll(MainController.SLE_USER, MainController.SLE_PROVIDER);
        sleSenderChoicebox.getSelectionModel().select(0);

        sleVersionChoicebox.getItems().add(MainController.I_DO_NOT_KNOW);
        sleVersionChoicebox.getItems().addAll("1", "2", "3", "4", "5");
        sleVersionChoicebox.getSelectionModel().select(0);

        initialiseSleParserClasses();
    }

    private void initialiseSleParserClasses() {
        parsers.add(new SleParser(ApplicationIdentifierEnum.RAF.name(), MainController.SLE_USER, new String[] {"1", "2", "3", "4", "5"}, RafUserToProviderPdu.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.RAF.name(), MainController.SLE_PROVIDER, new String[] {"1", "2"}, RafProviderToUserPduV1toV2.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.RAF.name(), MainController.SLE_PROVIDER, new String[] {"3", "4"}, RafProviderToUserPduV3toV4.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.RAF.name(), MainController.SLE_PROVIDER, new String[] {"5"}, RafProviderToUserPdu.class));

        parsers.add(new SleParser(ApplicationIdentifierEnum.RCF.name(), MainController.SLE_USER, new String[] {"1", "2", "3", "4", "5"}, RcfUserToProviderPdu.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.RCF.name(), MainController.SLE_PROVIDER, new String[] {"1"}, RcfProviderToUserPduV1.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.RCF.name(), MainController.SLE_PROVIDER, new String[] {"2", "3", "4"}, RcfProviderToUserPduV2toV4.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.RCF.name(), MainController.SLE_PROVIDER, new String[] {"5"}, RcfProviderToUserPdu.class));

        parsers.add(new SleParser(ApplicationIdentifierEnum.ROCF.name(), MainController.SLE_USER, new String[] {"1", "2", "3", "4", "5"}, RocfUserToProviderPdu.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.ROCF.name(), MainController.SLE_PROVIDER, new String[] {"1", "2", "3", "4"}, RocfProviderToUserPduV1toV4.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.ROCF.name(), MainController.SLE_PROVIDER, new String[] {"5"}, RocfProviderToUserPdu.class));

        parsers.add(new SleParser(ApplicationIdentifierEnum.CLTU.name(), MainController.SLE_USER, new String[] {"1", "2", "3", "4", "5"}, CltuUserToProviderPdu.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.CLTU.name(), MainController.SLE_PROVIDER, new String[] {"1", "2", "3"}, CltuProviderToUserPduV1toV3.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.CLTU.name(), MainController.SLE_PROVIDER, new String[] {"4"}, CltuProviderToUserPduV4.class));
        parsers.add(new SleParser(ApplicationIdentifierEnum.CLTU.name(), MainController.SLE_PROVIDER, new String[] {"5"}, CltuProviderToUserPdu.class));
    }

    public void onSleDecodeButtonClicked(ActionEvent actionEvent) {
        String data = sleTextArea.getText();
        if(data.isBlank()) {
            return;
        }
        byte[] bdata = StringUtil.toByteArray(data.toUpperCase());
        List<SleParser>  parsers = lookUpParsers(sleTypeChoicebox.getSelectionModel().getSelectedItem(), sleSenderChoicebox.getSelectionModel().getSelectedItem(), sleVersionChoicebox.getSelectionModel().getSelectedItem());
        for(SleParser p : parsers) {
            try {
                SlePdu pdu = p.parse(bdata);
                printPdu(pdu.buildTreeItem());
                return;
            } catch (Exception e) {
                // Next parser
            }
        }
        // If you reach this position, it means no parsers
        sleResultTextArea.setText(null);
    }

    private void printPdu(TreeItem<SlePduAttribute> rootItem) {
        StringBuilder textBuilder = new StringBuilder("");
        printRecursive(0, rootItem, textBuilder);
        sleResultTextArea.setText(textBuilder.toString());
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
        //
        for(TreeItem<SlePduAttribute> a : item.getChildren()) {
            printRecursive(level + 1, a, builder);
        }
    }

    private List<SleParser> lookUpParsers(String type, String sender, String version) {
        return this.parsers.stream().filter(o -> o.select(type, sender, version)).collect(Collectors.toList());
    }

    public void onSleClearButtonClicked(ActionEvent actionEvent) {
        sleTextArea.clear();
        sleResultTextArea.clear();
    }

}
