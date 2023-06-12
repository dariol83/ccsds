open module eu.dariolucia.ccsds.sle.utl {
    requires jasn1;
    requires jakarta.xml.bind;
    requires java.logging;

  exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.isp1.credentials;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.bind.types;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.common.types;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.incoming.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.outgoing.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.cltu.structures;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.fsp.incoming.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.fsp.outgoing.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.fsp.structures;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.incoming.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.outgoing.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.raf.structures;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.incoming.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.outgoing.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rcf.structures;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.incoming.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.outgoing.pdus;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.rocf.structures;
    exports eu.dariolucia.ccsds.sle.generated.ccsds.sle.transfer.service.service.instance.id;

    exports eu.dariolucia.ccsds.sle.utl.config;
    exports eu.dariolucia.ccsds.sle.utl.config.network;
    exports eu.dariolucia.ccsds.sle.utl.config.rocf;
    exports eu.dariolucia.ccsds.sle.utl.config.rcf;
    exports eu.dariolucia.ccsds.sle.utl.config.raf;
    exports eu.dariolucia.ccsds.sle.utl.config.cltu;
    exports eu.dariolucia.ccsds.sle.utl.encdec;
    exports eu.dariolucia.ccsds.sle.utl.network.tml;
    exports eu.dariolucia.ccsds.sle.utl.pdu;
    exports eu.dariolucia.ccsds.sle.utl.si;
    exports eu.dariolucia.ccsds.sle.utl.si.cltu;
    exports eu.dariolucia.ccsds.sle.utl.si.raf;
    exports eu.dariolucia.ccsds.sle.utl.si.rcf;
    exports eu.dariolucia.ccsds.sle.utl.si.rocf;
    exports eu.dariolucia.ccsds.sle.utl.util;
}