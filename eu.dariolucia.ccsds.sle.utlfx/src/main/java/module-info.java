module eu.dariolucia.ccsds.sle.utlfx {
    requires javafx.base;
    requires javafx.fxml;
    requires javafx.controls;
    requires java.logging;
    requires jasn1;
    requires java.xml.bind;
    requires com.sun.xml.bind;
    requires eu.dariolucia.ccsds.sle.utl;

    exports eu.dariolucia.ccsds.sle.utlfx.manager;
    exports eu.dariolucia.ccsds.sle.utlfx.application to javafx.graphics;
    exports eu.dariolucia.ccsds.sle.utlfx.controller to javafx.fxml;
    exports eu.dariolucia.ccsds.sle.utlfx.controller.raf to javafx.fxml;
    exports eu.dariolucia.ccsds.sle.utlfx.controller.rcf to javafx.fxml;
    exports eu.dariolucia.ccsds.sle.utlfx.controller.cltu to javafx.fxml;
    exports eu.dariolucia.ccsds.sle.utlfx.controller.rocf to javafx.fxml;
    exports eu.dariolucia.fx.charts to javafx.fxml;

    opens eu.dariolucia.ccsds.sle.utlfx.fxml;
    opens eu.dariolucia.ccsds.sle.utlfx.fxml.cltu;
    opens eu.dariolucia.ccsds.sle.utlfx.fxml.raf;
    opens eu.dariolucia.ccsds.sle.utlfx.fxml.rcf;
    opens eu.dariolucia.ccsds.sle.utlfx.fxml.rocf;

    opens eu.dariolucia.ccsds.sle.utlfx.controller;
    opens eu.dariolucia.ccsds.sle.utlfx.controller.raf;
    opens eu.dariolucia.ccsds.sle.utlfx.controller.rcf;
    opens eu.dariolucia.ccsds.sle.utlfx.controller.rocf;
    opens eu.dariolucia.ccsds.sle.utlfx.controller.cltu;
    opens eu.dariolucia.fx.charts;
}