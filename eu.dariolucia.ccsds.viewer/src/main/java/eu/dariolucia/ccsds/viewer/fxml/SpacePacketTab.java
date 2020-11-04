package eu.dariolucia.ccsds.viewer.fxml;

import eu.dariolucia.ccsds.encdec.pus.TcPusHeader;
import eu.dariolucia.ccsds.encdec.pus.TmPusHeader;
import eu.dariolucia.ccsds.encdec.time.AbsoluteTimeDescriptor;
import eu.dariolucia.ccsds.tmtc.transport.pdu.SpacePacket;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.time.Instant;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.utils.UI.addLine;

public class SpacePacketTab implements Initializable {

    public static final String YES = "YES";
    public static final String NO = "NO";

    public static final String CUC_3_2 = "CUC (3,2)";
    public static final String CUC_3_3 = "CUC (3,3)";
    public static final String CUC_3_4 = "CUC (3,4)";
    public static final String CUC_4_1 = "CUC (4,1)";
    public static final String CUC_4_2 = "CUC (4,2)";
    public static final String CUC_4_3 = "CUC (4,3)";
    public static final String CUC_4_4 = "CUC (4,4)";
    public static final String CDS_S_0 = "CDS short - milli";
    public static final String CDS_S_1 = "CDS short - micro";
    public static final String CDS_S_2 = "CDS short - pico";
    public static final String CDS_L_0 = "CDS long - milli";
    public static final String CDS_L_1 = "CDS long - micro";
    public static final String CDS_L_2 = "CDS long - pico";

    public VBox spViewbox;

    public ChoiceBox<String> spPusChoicebox;
    public ChoiceBox<String> spPusPacketSubcounterChoicebox;
    public ChoiceBox<String> spPusTimeExplicitChoicebox;
    public ChoiceBox<String> spPusTimeChoicebox;
    public TextField spPusSourceDestinationTextField;
    public TextField spPusAgencyEpochTextField;

    public TextArea spTextArea;
    public TextArea spResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        spPusChoicebox.getItems().addAll(YES, NO);
        spPusChoicebox.getSelectionModel().select(0);

        spPusPacketSubcounterChoicebox.getItems().addAll(YES, NO);
        spPusPacketSubcounterChoicebox.getSelectionModel().select(1);

        spPusTimeExplicitChoicebox.getItems().addAll(YES, NO);
        spPusTimeExplicitChoicebox.getSelectionModel().select(1);

        spPusTimeChoicebox.getItems().addAll(
                CUC_3_2,
                CUC_3_3,
                CUC_3_4,
                CUC_4_1,
                CUC_4_2,
                CUC_4_3,
                CUC_4_4,
                CDS_S_0,
                CDS_S_1,
                CDS_S_2,
                CDS_L_0,
                CDS_L_1,
                CDS_L_2
                );
        spPusTimeChoicebox.getSelectionModel().select(0);
    }

    public void onSpDecodeButtonClicked(ActionEvent actionEvent) {
        spResultTextArea.clear();
        String data = spTextArea.getText().toUpperCase();
        data = data.trim();
        data = data.replace("\n", "");
        data = data.replace("\t", "");
        data = data.replace(" ", "");
        if(data.isBlank()) {
            return;
        }
        // Let's try to see what we have to do
        try {
            byte[] bdata = StringUtil.toByteArray(data);
            processPacket(bdata);
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void processPacket(byte[] bdata) {
        String message = "";

        // Space Packet
        try {
            StringBuilder sb = new StringBuilder("");

            SpacePacket ttf = new SpacePacket(bdata,true);
            processSpacePacket(ttf, sb);
            return;
        } catch (Exception e) {
            // Not a space packet
            message = e.getMessage();
        }

        error("Provided dump is not a space packet: " + message);
    }

    private void processSpacePacket(SpacePacket ttf, StringBuilder sb) {
        addLine(sb, "Type", ttf.isTelemetryPacket() ? "TM" : "TC");
        addLine(sb, "APID", ttf.getApid());
        addLine(sb, "SSC", ttf.getPacketSequenceCount());
        addLine(sb, "Sequence Flag", ttf.getSequenceFlag());
        addLine(sb, "Secondary Header Flag", ttf.isSecondaryHeaderFlag());
        addLine(sb, "Idle Packet", ttf.isIdle());
        addLine(sb, "Length", ttf.getLength());
        addLine(sb, "Data Field", StringUtil.toHexDump(ttf.getDataFieldCopy()));

        if(spPusChoicebox.getSelectionModel().getSelectedItem().equals(YES)) {
            processPus(ttf, sb);
        }
        spResultTextArea.setText(sb.toString());
    }

    private void processPus(SpacePacket ttf, StringBuilder sb) {
        if(ttf.isTelemetryPacket()) {
            TmPusHeader h = TmPusHeader.decodeFrom(ttf.getDataFieldCopy(), 0,
                    spPusPacketSubcounterChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                    Integer.parseInt(spPusSourceDestinationTextField.getText()),
                    spPusTimeExplicitChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                    toInstant(spPusAgencyEpochTextField.getText()),
                    toTimeDescriptor(spPusTimeChoicebox.getSelectionModel().getSelectedItem()));
            addLine(sb, "PUS Version", h.getVersion());
            addLine(sb, "PUS Packet Subcounter", h.getPacketSubCounter());
            addLine(sb, "PUS Type", h.getServiceType());
            addLine(sb, "PUS Subtype", h.getServiceSubType());
            addLine(sb, "PUS Destination ID", h.getDestinationId());
            addLine(sb, "PUS OBT", h.getAbsoluteTime());
        } else {
            TcPusHeader h = TcPusHeader.decodeFrom(ttf.getDataFieldCopy(), 0, Integer.parseInt(spPusSourceDestinationTextField.getText()));
            addLine(sb, "PUS Version", h.getVersion());
            addLine(sb, "PUS Ack Flags", h.getAckField());
            addLine(sb, "PUS Type", h.getServiceType());
            addLine(sb, "PUS Subtype", h.getServiceSubType());
            addLine(sb, "PUS Source ID", h.getSourceId());
        }
    }

    private AbsoluteTimeDescriptor toTimeDescriptor(String selectedItem) {
        if(selectedItem.equals(CUC_3_2)) {
            return AbsoluteTimeDescriptor.newCucDescriptor(3,2);
        }
        if(selectedItem.equals(CUC_3_3)) {
            return AbsoluteTimeDescriptor.newCucDescriptor(3,3);
        }
        if(selectedItem.equals(CUC_3_4)) {
            return AbsoluteTimeDescriptor.newCucDescriptor(3,4);
        }
        if(selectedItem.equals(CUC_4_1)) {
            return AbsoluteTimeDescriptor.newCucDescriptor(4,1);
        }
        if(selectedItem.equals(CUC_4_2)) {
            return AbsoluteTimeDescriptor.newCucDescriptor(4,2);
        }
        if(selectedItem.equals(CUC_4_3)) {
            return AbsoluteTimeDescriptor.newCucDescriptor(4,3);
        }
        if(selectedItem.equals(CUC_4_4)) {
            return AbsoluteTimeDescriptor.newCucDescriptor(4,4);
        }
        if(selectedItem.equals(CDS_S_0)) {
            return AbsoluteTimeDescriptor.newCdsDescriptor(true,0);
        }
        if(selectedItem.equals(CDS_S_1)) {
            return AbsoluteTimeDescriptor.newCdsDescriptor(true,1);
        }
        if(selectedItem.equals(CDS_S_2)) {
            return AbsoluteTimeDescriptor.newCdsDescriptor(true,2);
        }
        if(selectedItem.equals(CDS_L_0)) {
            return AbsoluteTimeDescriptor.newCdsDescriptor(false,0);
        }
        if(selectedItem.equals(CDS_L_1)) {
            return AbsoluteTimeDescriptor.newCdsDescriptor(false,0);
        }
        if(selectedItem.equals(CDS_L_2)) {
            return AbsoluteTimeDescriptor.newCdsDescriptor(false,0);
        }
        return null;
    }

    private Instant toInstant(String text) {
        if(text.trim().isBlank()) {
            return null;
        } else {
            return Instant.parse(text.trim());
        }
    }

    private void error(String error) {
        spResultTextArea.setText(spResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onSpClearButtonClicked(ActionEvent actionEvent) {
        spTextArea.clear();
        spResultTextArea.clear();
    }

}
