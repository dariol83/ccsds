open module eu.dariolucia.ccsds.cfdp.fx {
    requires javafx.base;
    requires javafx.fxml;
    requires javafx.controls;
    requires java.logging;
    requires java.xml.bind;
    requires eu.dariolucia.ccsds.cfdp;
    requires eu.dariolucia.ccsds.tmtc;

    exports eu.dariolucia.ccsds.cfdp.fx.application;
    exports eu.dariolucia.ccsds.cfdp.fx.controller;
    exports eu.dariolucia.ccsds.cfdp.fx.dialogs;
}