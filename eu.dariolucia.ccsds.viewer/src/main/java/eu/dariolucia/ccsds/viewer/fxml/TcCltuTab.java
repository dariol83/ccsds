package eu.dariolucia.ccsds.viewer.fxml;

import eu.dariolucia.ccsds.tmtc.coding.decoder.CltuDecoder;
import eu.dariolucia.ccsds.tmtc.coding.decoder.CltuRandomizerDecoder;
import eu.dariolucia.ccsds.tmtc.datalink.channel.VirtualChannelAccessMode;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.AbstractReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.IVirtualChannelReceiverOutput;
import eu.dariolucia.ccsds.tmtc.datalink.channel.receiver.TcReceiverVirtualChannel;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.AbstractTransferFrame;
import eu.dariolucia.ccsds.tmtc.datalink.pdu.TcTransferFrame;
import eu.dariolucia.ccsds.tmtc.util.StringUtil;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;

import static eu.dariolucia.ccsds.viewer.utils.UI.addLine;

public class TcCltuTab implements Initializable {

    public static final String YES = "YES";
    public static final String NO = "NO";
    private static final byte[] DEFAULT_CLTU_PREFIX = new byte[]{(byte) 0xEB, (byte) 0x90};

    public VBox tcCltuViewbox;

    public TextArea tcCltuTextArea;
    public ChoiceBox<String> tcCltuRandomizedChoicebox;
    public ChoiceBox<String> tcCltuSegmentationChoicebox;
    public ChoiceBox<String> tcCltuFecfChoicebox;
    public TextField tcCltuSecurityHeaderTextField;
    public TextField tcCltuSecurityTrailerTextField;
    public TextArea tcCltuResultTextArea;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        tcCltuRandomizedChoicebox.getItems().addAll(YES, NO);
        tcCltuRandomizedChoicebox.getSelectionModel().select(1);

        tcCltuSegmentationChoicebox.getItems().addAll(YES, NO);
        tcCltuSegmentationChoicebox.getSelectionModel().select(0);

        tcCltuFecfChoicebox.getItems().addAll(YES, NO);
        tcCltuFecfChoicebox.getSelectionModel().select(1);
    }

    public void onTcCltuDecodeButtonClicked(ActionEvent actionEvent) {
        tcCltuResultTextArea.clear();
        String data = tcCltuTextArea.getText().toUpperCase();
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
            if (data.startsWith(StringUtil.toHexDump(DEFAULT_CLTU_PREFIX).toUpperCase())) {
                // It is a CADU
                processCltu(bdata);
            } else {
                // Assume frame
                processFrame(bdata);
            }
        } catch (Exception e) {
            error(e.getMessage());
        }
    }

    private void processFrame(byte[] bdata) {
        // Check if it is randomised
        boolean wasRandomized = false;
        if(tcCltuRandomizedChoicebox.getSelectionModel().getSelectedItem().equals(YES)) {
            bdata = new CltuRandomizerDecoder().apply(bdata);
            wasRandomized = true;
        }
        // Now the frame is not randomized, and ready to be processed
        String message = "";

        // TC
        try {
            // Remove virtual fill, if any
            int flen = TcTransferFrame.readTcFrameLength(bdata);
            if(flen != bdata.length) {
                bdata = Arrays.copyOfRange(bdata, 0, flen);
            }
            StringBuilder sb = new StringBuilder("");
            if(wasRandomized) {
                addLine(sb, "Derandomized TC Frame", StringUtil.toHexDump(bdata));
                sb.append("\n");
            }
            TcTransferFrame ttf = new TcTransferFrame(bdata,
                    o -> tcCltuSegmentationChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                    tcCltuFecfChoicebox.getSelectionModel().getSelectedItem().equals(YES),
                    Integer.parseInt(tcCltuSecurityHeaderTextField.getText()),
                    Integer.parseInt(tcCltuSecurityTrailerTextField.getText()));
            processTcFrame(ttf, sb);
            return;
        } catch (Exception e) {
            // Not a TC frame
            message = e.getMessage();
        }

        error("Provided dump is not a TC Frame: " + message);
    }

    private void extractPacketsFrom(StringBuilder sb, TcTransferFrame ttf) {
        TcReceiverVirtualChannel vc = new TcReceiverVirtualChannel(ttf.getVirtualChannelId(), VirtualChannelAccessMode.PACKET, false);
        vc.register(new IVirtualChannelReceiverOutput() {
            @Override
            public void spacePacketExtracted(AbstractReceiverVirtualChannel vc, AbstractTransferFrame firstFrame, byte[] packet, boolean qualityIndicator) {
                addLine(sb, "Packet", StringUtil.toHexDump(packet));
            }
        });
        vc.accept(ttf);
    }

    private void processTcFrame(TcTransferFrame ttf, StringBuilder sb) {
        addLine(sb, "Transfer Frame Version Number", ttf.getTransferFrameVersionNumber());
        addLine(sb, "Spacecraft ID", ttf.getSpacecraftId());
        addLine(sb, "Virtual Channel ID", ttf.getVirtualChannelId());
        addLine(sb, "Virtual Channel Frame Count", ttf.getVirtualChannelFrameCount());
        addLine(sb, "Bypass Flag", ttf.isBypassFlag());
        addLine(sb, "Control Command Flag", ttf.isControlCommandFlag());
        addLine(sb, "Length", ttf.getLength());

        if(ttf.isFecfPresent()) {
            addLine(sb, "FECF", String.format("%04X", ttf.getFecf()));
        }

        addLine(sb, "Frame Type", ttf.getFrameType());
        if(ttf.getFrameType() == TcTransferFrame.FrameType.BC) {
            addLine(sb, "BC Control Command", ttf.getControlCommandType());
            if(ttf.getControlCommandType() == TcTransferFrame.ControlCommandType.SET_VR) {
                addLine(sb, "Set_V(R)", ttf.getSetVrValue());
            }
        }
        if(ttf.getSecurityHeaderLength() > 0) {
            addLine(sb, "Security Header", StringUtil.toHexDump(ttf.getSecurityHeaderCopy()));
        }
        if(ttf.getSecurityTrailerLength() > 0) {
            addLine(sb, "Security Trailer", StringUtil.toHexDump(ttf.getSecurityTrailerCopy()));
        }
        addLine(sb, "Idle Frame", ttf.isIdleFrame());
        if(ttf.isSegmented()) {
            addLine(sb, "Map ID", ttf.getMapId());
            addLine(sb, "Sequence Flag", ttf.getSequenceFlag());
        }
        addLine(sb, "Data Field", StringUtil.toHexDump(ttf.getDataFieldCopy()));
        if(!ttf.isIdleFrame() && ttf.getFrameType() != TcTransferFrame.FrameType.BC) {
            sb.append("\n");
            extractPacketsFrom(sb, ttf);
        }

        tcCltuResultTextArea.setText(tcCltuResultTextArea.getText() + "\n" + sb.toString());
    }

    private void processCltu(byte[] bdata) {
        // Decode CLTU
        bdata = new CltuDecoder().apply(bdata);
        // Process
        processFrame(bdata);
    }

    private void error(String error) {
        tcCltuResultTextArea.setText(tcCltuResultTextArea.getText() + "\nERROR: " + error);
    }

    public void onTcCltuClearButtonClicked(ActionEvent actionEvent) {
        tcCltuResultTextArea.clear();
        tcCltuResultTextArea.clear();
    }

}
