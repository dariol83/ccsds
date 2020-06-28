# Encoding/Decoding module
This module implements packet identification/encoding/decoding functionalities, which supports basic data types (integer, real, enumeration, CCSDS absolute and 
relative time formats, strings, octet streams, bit streams), array structures, ECSS TM/TC PUS headers, as derived from the ECSS Packet Utilisation Standard (http://everyspec.com/ESA/download.php?spec=ECSS-E-70-41A.047794.pdf). 

The focus on this implementation is on reliable and efficient packet identification and decoding performance: on my Intel i5 2.67 GHz (year 2010), packet identification is ca. 7M packets per second (with ca. 72K different packet definitions), and parameter decoding speed is ca. 890K parameters per second.   

Encoding performance has not been measured yet. 

The packet structure is defined using a custom XML format definition, built by means of JAXB annotations.

## Getting started
### Definitions
To use the majority of the functionalities implemented by The module, an XML file describing the packet identification
criteria and the packet structure shall be defined. The XML file shall be valid against the provided XSD and shall consider also
the constraints defined as part of the documentation of the JAXB classes in eu.dariolucia.ccsds.encdec.definition package.

The XML definition file is structured as follows:
- Definition of the identification fields at global level: bit start offset, length in byte, masks and shifts. These fields are 
globally defined to allow the correct construction of the identification tree by the identification algorithm.
- Definition of the packets in terms of (optional) identification criteria and (optional) structure: the identification criteria define the fields to be used to identify the specific packet and the related values; 
the structure defines which values are encoded in the packet: information to determine the location, type, length, whether the encoded field contains the value of a parameter.
- (optional) Definition of the global parameter entities: this is the definition of the parameters as self-standing entities, which may (or may not) be referred by encoded fields.

The split between packet encoded fields and (global) parameters is the same conceptual split that in the Packet Utilisation Standard is present between packet fields and On-Board Parameters.
 
 In terms of code, the Definition object can be loaded from the XML by invoking:
 
     Definition.load(InputStream input)
 
 Example: assuming that the Definition XML file is residing in /home/dev/myDefinitions.xml
 
     Definition.load(new FileInputStream("/home/dev/myDefinitions.xml"))
 
### Encoding
To encode a packet, you need:
- The definition of the encoding rules, i.e. the packet structure, as described in the previous section;
- A packet encoder;
- The values to be encoded, according to the packet structure.

The packet encoder must be an implementation of the IPacketEncoder interface. The module provides you with a concrete
implementation, i.e. DefaultPacketEncoder. It can be created as follows:

    Definition d = Definition.load(new FileInputStream(...));
    DefaultPacketEncoder encoder = new DefaultPacketEncoder(d); 

To provide values to be encoded, you need an implementation of the IEncodeResolver interface. In fact, as the encoding depends
on the structure of the packet, the encoding process will pull the required values by querying the provided IEncodeResolver interface.

The module provides a set of resolver implementations (in package eu.dariolucia.ccsds.encdec.structure.resolvers), which can be used, e.g. to 
wrap a map containing the values.

The ID of the encoded field that is provided to the resolver is built using a . (dot) separated path, where the first part
is the packet ID, and then the hierarchical path to the encoded field.

For instance, given the following packet definition:

        <packet id="DEF3">
            <structure>
                <parameter id="PARAM1">
                    <type_fixed type="SIGNED_INTEGER" length="3" />
                </parameter>
                <array id="ARRAY1">
                    <size_fixed len="3" />
                    <parameter id="PARAM_A1">
                        <type_fixed type="SIGNED_INTEGER" length="5" />
                    </parameter>
                    <parameter id="PARAM_A2">
                        <type_fixed type="SIGNED_INTEGER" length="5" />
                    </parameter>
                    <parameter id="PARAM_A3">
                        <type_fixed type="BOOLEAN" length="0" />
                    </parameter>
                </array>
                <parameter id="PARAM2">
                    <type_fixed type="SIGNED_INTEGER" length="4" />
                </parameter>
            </structure>
        </packet> 

This structure will cause the DefaultPacketEncoder to call for:
- The value of the encoded parameter PARAM1 with path `DEF3.PARAM1`
- The value of the encoded parameter PARAM_A1 in the first entry for ARRAY1 with path `DEF3.ARRAY1#0.PARAM_A1`
- The value of the encoded parameter PARAM2 with path `DEF3.PARAM2`

At the end of the process, the IPacketEncoder returns a byte array containing the encoded packet.

### Identification

The identification of a packet, i.e. deriving the packet ID that can be assigned to a byte array, is provided by 
implementations of the IPacketIdentifier interface.

The module provides you with a concrete implementation, i.e. FieldGroupBasedPacketIdentifier. It can be created as follows:

    Definition d = Definition.load(...);
    IPacketIdentifier identifier = new FieldGroupBasedPacketIdentifier(d);

The identification is invoked as follows:

    byte[] packet = ...;
    String packetId = identifier.identify(packet);

### Decoding

The decoding of a packet, i.e. the extraction of the contained values according to the packet structure and, when defined, the links
of these values to the globally defined parameters, is provided by implementations of the IPacketDecoder interface.

The module provides you with a concrete implementation, i.e. DefaultPacketDecoder. It can be created as follows:

    Definition d = Definition.load(...);
    IPacketDecoder decoder = new DefaultPacketDecoder(d);

The simplest way to start the decoding is to use the following code:

    String packetId = ...;
    byte[] packet = ...;
    DecodingResult dr = decoder.decode(packetId, packet);

The object DecodingResult contains the result of the decoding process:
- The packet definition;
- The hierarchical extraction of the encoded parameters, which can be navigated by means of the DecodingResult.IVisitor interface, plus a convenience method to retrieve all those values in the form of a map;
- The links, where defined, to the globally defined parameters.

To support the extraction of packets from a given offset, as well as to support time convertion from on-board time to UTC time of the extracted parameter values of type ABSOLUTE_TIME, the IPacketDecoder interface defines additional methods, where these information can be provided.

### PUS support

The supported parameter types defined by the module are compliant (with the exception of the PFC code for integer numbers) with the PUS defined encoding types.
The PUS parameter type DEDUCED is not supported as type by the module, but it is supported considering the conceptual meaning of such type. In case of parameter types DEDUCED,
a combination of ReferenceType, ReferenceLength, ParameterType, ParameterLength shall be used when defining the related encoded field in the packet structure. 

In addition, inside package eu.dariolucia.ccsds.encdec.pus, the module supports the definition of TM and TC space packet secondary headers, as well as packet checksum, as defined by the ECSS Packet Utilisation Standard, Version A.

To be noted that the current PUS standard is Version C, which is not freely retrievable on Internet. Therefore, the support for PUS in this module is limited to version A.

### Extensions

The module supports the specification of types not defined in the PUS standard. To define and use an extended type, the following steps are needed:
- The type of the encoded field shall be defined by an ExtendedType object, containing the ID identifying such type;
- An IDecoderExtension and IEncoderExtension implementions shall be present as module-provided services (i.e. declared in their module-info) to deal with the extension type;
- The IDecoderExtension and IEncoderExtension implementions shall be annotated with the required annotation ExtensionId, with the ID of the type.

The module already provides an extension with type ID "__java_serialization" to be able to encode and decode fields using Java Serialization.