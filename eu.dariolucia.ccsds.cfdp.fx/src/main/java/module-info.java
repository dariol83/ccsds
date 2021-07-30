open module eu.dariolucia.ccsds.cfdp.fx {
    requires javafx.base;
    requires javafx.fxml;
    requires javafx.controls;
    requires java.logging;
    requires java.xml.bind;
    requires com.sun.xml.bind;
    requires eu.dariolucia.ccsds.cfdp;

    exports eu.dariolucia.ccsds.cfdp.fx.application to javafx.graphics;
    exports eu.dariolucia.ccsds.cfdp.fx.controller to javafx.fxml;
}