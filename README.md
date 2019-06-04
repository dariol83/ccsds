# An open source implementation of the CCSDS protocols and formats in Java 11
This repository contains an attempt to code an open source, not validated (but hopefully working and performant) implementation of some of the public available CCSDS standards (Time Code Format, SLE, TM/TC/AOS, Space Packet) and support utilities. The implementation has been done in Java and follows the latest modular changes in the Java platform since version 9.

**Disclaimer: this code is implemented as personal hobby, without any use of external systems or software to test or validate its behaviour (it is neither operationally nor effectively tested). It is not endorsed by any organization, association or company. It is the result of my passion for software engineering and coding, and hours spent developing during evenings (often with a beer or snacks in front of me, and my ukulele on the side). Compliance to the different standards is not guaranteed. As the Apache License makes clear, use the modules of this repository at your own risk.**

## SLE
The SLE User Test Library (eu.dariolucia.ccsds.sle.utl) is a test library that implements the user side of an SLE data exchange session. 
It supports RAF, RCF, ROCF and CLTU. Support for FSP is currently missing, but it is foreseen to be added shortly. 
It uses a custom format for the configuration of the service instances (currently not documented, can be derived by the classes inside 
the "config" package).
Highlights are:
- support of multiple SLE versions
- support of credentials encoding SHA-1 and SHA-256 independently from the SLE version
- support of user and provider as initiator
- light codebase, as it heavily relies on the generated code from the ASN.1 definitions

A simple but effective graphical interface is provided as separate module (eu.dariolucia.ccsds.sle.utlfx). 

## TM/TC
The TM/TC Library (eu.dariolucia.ccsds.tmtc) is a library that provide support to parse and build TM frames, TC frames, AOS frames, Space Packets. It allows encoding/decoding of space packets into TM frames, TC frames or AOS frames. It also provides some basic encodings (e.g. CLTU encoding/decoding, randomization, FECF encoding/checking) but no error detection or error correction capabilities. Support for Reed-Solomon encoding/checking is currently missing, but it is foreseen to be added shortly. 
A simple graphical interface to inspect TM/TC/AOS/Space Packet data is provided as separate module (eu.dariolucia.ccsds.inspector). It is possible to implement custom connectors to allow inspection of data delivered from a custom source/protocol/format by implement a new 
service (in Java 11 terms) implementing the interface eu.dariolucia.ccsds.inspector.api.IConnectorFactory.

## ENC/DEC
A packet identification/encoding/decoding library, which supports basic data types (integer, real, enumeration, CCSDS absolute and 
relative time formats, strings, octet streams, bit streams) and array structures, as derived from the ECSS Packet Utilisation Standard (http://everyspec.com/ESA/download.php?spec=ECSS-E-70-41A.047794.pdf). The focus on this implementation is on reliable and efficient packet identification and decoding performance. Encoding performance has not been measured. 
The packet structure is defined using a custom XML format definition, built by means of JAXB annotations.

# Goals
The purpose of this repository is to produce, as far as my possibilities allow, simple, well-designed, well-documented, well-tested code with top performance. For each module (graphical elements and tools excluded) I have the following targets:
- at least 90% line coverage with unit tests (easy to check with IntelliJ);
- a set of performance tests;
- no warnings when compiling;
- good documentation at package, class, instance variable and method level;
- provide specific code, and not generic, one-fits-all code: generic code is usually slow because it is over-engineered. No way. Simplicity is the key.

Each module is built using Maven and can be compiled out of the box (if all its dependencies are satisfied) with Maven.

# License
All original source code on this repository is released under the terms and conditions of the Apache License 2.0. The source code includes code and resources from other authors, which can be freely obtained from the web. To be more specific:
- eu.dariolucia.ccsds.inspector and eu.dariolucia.ccsds.sle.utlfx contains a derivation of the DateAxis class from Christian
Schudt, Diego Cirujano "DateAxis" and Pedro Duque Vieira (http://myjavafx.blogspot.com/2013/09/javafx-charts-display-date-values-on.html)
- eu.dariolucia.ccsds.sle.utlfx and eu.dariolucia.ccsds.inspector contains part of the open source icon set Eva Icons
by Akveo (https://github.com/akveo/eva-icons), released under the terms of the MIT License;
- eu.dariolucia.ccsds.sle.utl contains copy of the ASN.1 definition as listed on https://public.ccsds.org/Publications/BlueBooks.aspx, 
with modifications to include support for older SLE versions, which can be retrieved at https://public.ccsds.org/Publications/SilverBooks.aspx;
- Snippets from StackOverflow should be reported with their SO link and are released under Creative Commons. I might have missed some though. In such case, please inform me.

All other source code and resources, such as test data files, FXML and some images are genuine new implementation or generated using the library itself (e.g. TM data file, SLE configuration file). These resources are therefore released under the terms of the Apache License, 2.0.

As a general rule, I tried to make sure that each external contribution is acknowledged where the contribution appears. Each module contains a NOTICE file that contains all the required attributions. By using any of the resources on this repository, you need to comply with the terms and conditions of the Apache License, including the carry-over and distribution of the NOTICE file in derived code/software/products.

If by any chance I forgot/missed to provide due credits to authors, please contact me here on GitHub and I will provide to add the credits in this page and in the NOTICE file.

# Acknowledgements
I would like to thank the people behind the following technologies/libraries, which the code in this repository depends on. 
Too often we give things for granted, forgetting the amazing amount of work that people spend everyday to deliver good resources, efficient code and effective solutions to everybody free of charge:
- The OpenJDK development team;
- The OpenJFX development team;
- The Glassfish/JAXB development team;
- The jASN.1 development team;
- Akveo for the amazing Eva Icons;
- Christian Schudt, Diego Cirujano "DateAxis" and Pedro Duque Vieira for their DateAxis class;
- The StackOverflow community.

# Contributions
If you find any issue with the provided code or you would like to see a specific feature implemented, please raise a request on GitHub.
If you spot and fix any issue by using the provided code, please consider contributing back to the project, by raising the issue on 
GitHub, hopefully with your solution attached and/or a pull request :)
If you want to actively contribute to this project, please contact me.
