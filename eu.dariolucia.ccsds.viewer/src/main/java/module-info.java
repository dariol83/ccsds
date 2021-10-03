open module eu.dariolucia.ccsds.viewer {
    exports eu.dariolucia.ccsds.viewer.application;

    requires eu.dariolucia.ccsds.encdec;
    requires eu.dariolucia.ccsds.sle.utl;
    requires eu.dariolucia.ccsds.tmtc;
    requires eu.dariolucia.ccsds.cfdp;
    requires java.logging;
    requires javafx.graphics;
    requires javafx.fxml;
    requires javafx.controls;
    requires jasn1;
}