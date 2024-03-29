open module eu.dariolucia.ccsds.encdec {
  requires transitive jakarta.xml.bind;

  uses eu.dariolucia.ccsds.encdec.extension.ILengthMapper;
    uses eu.dariolucia.ccsds.encdec.extension.ITypeMapper;
    uses eu.dariolucia.ccsds.encdec.extension.IEncoderExtension;
    uses eu.dariolucia.ccsds.encdec.extension.IDecoderExtension;

    exports eu.dariolucia.ccsds.encdec.definition;
    exports eu.dariolucia.ccsds.encdec.bit;
    exports eu.dariolucia.ccsds.encdec.extension;
    exports eu.dariolucia.ccsds.encdec.extension.impl;
    exports eu.dariolucia.ccsds.encdec.identifier;
    exports eu.dariolucia.ccsds.encdec.identifier.impl;
    exports eu.dariolucia.ccsds.encdec.structure;
    exports eu.dariolucia.ccsds.encdec.structure.impl;
    exports eu.dariolucia.ccsds.encdec.structure.resolvers;
    exports eu.dariolucia.ccsds.encdec.time;
    exports eu.dariolucia.ccsds.encdec.time.impl;
    exports eu.dariolucia.ccsds.encdec.value;
    exports eu.dariolucia.ccsds.encdec.pus;

    provides eu.dariolucia.ccsds.encdec.extension.IDecoderExtension with eu.dariolucia.ccsds.encdec.extension.impl.JavaSerializationDecoderExtension;
    provides eu.dariolucia.ccsds.encdec.extension.IEncoderExtension with eu.dariolucia.ccsds.encdec.extension.impl.JavaSerializationEncoderExtension;
}