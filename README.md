[![Build](https://github.com/dariol83/ccsds/actions/workflows/maven.yml/badge.svg)](https://github.com/dariol83/ccsds/actions/workflows/maven.yml) [<img src="https://img.shields.io/maven-central/v/eu.dariolucia.ccsds/eu.dariolucia.ccsds?color=greem&style=flat">](https://search.maven.org/search?q=eu.dariolucia.ccsds) [<img src="https://img.shields.io/sonar/quality_gate/eu.dariolucia.ccsds:eu.dariolucia.ccsds?server=https%3A%2F%2Fsonarcloud.io&style=flat">](https://sonarcloud.io/dashboard?id=eu.dariolucia.ccsds%3Aeu.dariolucia.ccsds) [<img src="https://img.shields.io/sonar/violations/eu.dariolucia.ccsds:eu.dariolucia.ccsds?format=long&server=https%3A%2F%2Fsonarcloud.io&style=flat">](https://sonarcloud.io/project/issues?id=eu.dariolucia.ccsds%3Aeu.dariolucia.ccsds&resolved=false) [<img src="https://img.shields.io/sonar/coverage/eu.dariolucia.ccsds:eu.dariolucia.ccsds?server=https%3A%2F%2Fsonarcloud.io&style=flat">](https://sonarcloud.io/component_measures?id=eu.dariolucia.ccsds%3Aeu.dariolucia.ccsds&metric=coverage&view=list) [![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fdariol83%2Fccsds.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fdariol83%2Fccsds?ref=badge_shield)

# An open source implementation of CCSDS protocols and formats in Java 11

This repository contains an open source implementation of public available CCSDS standards:

- **CCSDS 131.0-B-4** TM Synchronization and Channel Coding
- **CCSDS 132.0-B-3** TM Space Data Link Protocol
- **CCSDS 133.0-B-2** Space Packet Protocol
- **CCSDS 133.1-B-3** Encapsulation Packet Protocol
- **CCSDS 231.0-B-4** TC Synchronization and Channel Coding
- **CCSDS 232.0-B-4** TC Space Data Link Protocol
- **CCSDS 232.1-B-2** Communications Operation Procedure-1
- **CCSDS 301.0-B-4** Time Code Formats
- **CCSDS 727.0-B-5** CCSDS File Delivery Protocol (CFDP)
- **CCSDS 732.0-B-4** AOS Space Data Link Protocol
- **CCSDS 911.1-B-4** Space Link Extension--Return All Frames Service Specification
- **CCSDS 911.2-B-3** Space Link Extension--Return Channel Frames Service Specification
- **CCSDS 911.5-B-3** Space Link Extension--Return Operational Control Fields Service Specification
- **CCSDS 912.1-B-4** Space Link Extension--Forward CLTU Service Specification
- **CCSDS 913.1-B-2** Space Link Extension--Internet Protocol for Transfer Services

The implementation has been done in Java and follows the latest modular changes in the Java platform since version 9. 
Stable releases are published on Maven Central. The latest code version is always available on the master branch here on 
GitHub.

**Disclaimer: this code is implemented as personal hobby, and it is not endorsed by any organization, 
association or company. As such, strict compliance to the different standards as well as correct behaviour cannot be 
guaranteed. As the Apache License makes clear, use the modules of this repository at your own risk.**

## SLE
The SLE User Test Library (eu.dariolucia.ccsds.sle.utl) is a library that implements the user side and provider side of 
an SLE data exchange session. 
It supports RAF, RCF, ROCF and CLTU. Support for FSP is currently missing and it will be implemented the request/need 
for it will be reported. 
It uses a custom format for the configuration of the service instances (currently not documented, can be derived by the 
classes inside the "config" package).
Features:
- support of multiple SLE versions;
- support of credentials encoding SHA-1 and SHA-256 independently from the SLE version;
- support of user and provider as initiator;
- light codebase, as it heavily relies on the generated code from the ASN.1 definitions.

A simple but effective graphical interface to inspect the user-side peer is provided as separate module 
(eu.dariolucia.ccsds.sle.utlfx). 

## TM/TC
The TM/TC Library (eu.dariolucia.ccsds.tmtc) is a library that provide support to parse and build CCSDS TM frames, TC 
frames, AOS frames, Space Packets, Encapsulation Packets and full FARM/FOP implementation for COP-1. It allows 
encoding/decoding of space packets and encapsulation packets into TM frames, TC frames or AOS frames. It also provides 
some basic encodings (e.g. CLTU encoding/decoding, randomization, FECF encoding/checking, Reed-Solomon encoding/checking) 
but no error correction capabilities. 

A simple graphical interface to inspect TM/TC/AOS/Space Packet data as stream is provided as separate module 
(eu.dariolucia.ccsds.inspector). It is possible to implement custom connectors to allow inspection of data delivered 
from a custom source/protocol/format by providing a new service (in Java 11 terms) implementing the interface 
eu.dariolucia.ccsds.inspector.api.IConnectorFactory. Some connectors to read data from file or socket are already 
available.

## ENC/DEC
A packet identification/encoding/decoding library (eu.dariolucia.ccsds.encdec), which supports basic data types 
(integer, real, enumeration, CCSDS absolute and relative time formats, strings, octet streams, bit streams), array 
structures, ECSS TM/TC PUS headers, as derived from the ECSS Packet Utilisation Standard 
(http://everyspec.com/ESA/download.php?spec=ECSS-E-70-41A.047794.pdf) with main focus on performance. 

The packet structure is defined using a custom XML format definition, built by means of JAXB annotations.

## CFDP
The CCSDS File Delivery Protocol Library (eu.dariolucia.ccsds.cfdp) is a library that implements a full, 
standard-compliant CFDP entity (without support for proxy operations and relay operations, which can be easily developed 
in the application). It supports Class 1 (with and without closure) and Class 2 operations, with built-in support for 
TCP and UDP UT layers.

A simple but effective graphical interface to monitor transactions in a local CFDP entity using TCP and UDP as transport 
layers is provided as separate module (eu.dariolucia.ccsds.cfdp.fx).

## Data Viewer
To ease the debug and visualisation of encoded information in telemetry and telecommand data, the module viewer 
(eu.dariolucia.ccsds.viewer) contains an application that can display decoded and structure information from all the 
data types supported by this library (CADUs, TM frames, AOS frames, TC frames, CLTU, Space and Encapsulation Packets, 
all SLE operations, time encodings, CLCW, CFDP PDUs).

## Examples
A modules providing examples on how to use the three modules above in a combined way, to demonstrate the capabilities of 
the library to write compact code. The following examples are provided:
- TM generator with output to file and/or TCP socket, from packet definition to TM CADU or TM frame;
- TM processor with output to console, from CADU or TM frames read from a TCP socket to encoded parameter extraction;
- TM processor with output to console, from SLE RAF to encoded parameter extraction;
- TC generator from list of parameters (as defined in the packet definition) to CLTU with output to file and/or TCP socket;
- TC generator from list of parameters to SLE CLTU;
- TC processor with output to console, from CLTU to encoded parameter extraction;
- CFDP Entity with input from/output to console, working on TCP and UDP UT layer implementations.

The main target of the examples module is not to provide final applications ready to be used, rather to explain the 
different levels and concepts of the libraries, and how to link the various objects together. 

# Code Targets
One of the targets of this repository is to produce simple, well-designed, well-documented, well-tested code with top-class 
performance. For each module (examples and tools excluded) the targets are:
- full CI integration with GitHub Actions;
- at least 90% line coverage with unit tests (measured by JaCoCo and reported in Sonarcloud);
- a set of performance tests;
- quality gate and QA metrics measured by Sonarcloud;
- good documentation at package, class, instance variable and method level;
- provision of simple, readable, specific code.

Each module is built using Maven and can be compiled out of the box (if all its dependencies are satisfied) with Maven. 
If you want to compile them all in one go, just run 'mvn clean install' on the root folder.

# License
All original source code on this repository is released under the terms and conditions of the Apache License 2.0. 
The source code includes code and resources from other authors, which can be freely obtained from the web. To be more specific:
- eu.dariolucia.ccsds.inspector and eu.dariolucia.ccsds.sle.utlfx contains a derivation of the DateAxis class from Christian
Schudt, Diego Cirujano "DateAxis" and Pedro Duque Vieira (http://myjavafx.blogspot.com/2013/09/javafx-charts-display-date-values-on.html)
- eu.dariolucia.ccsds.sle.utlfx and eu.dariolucia.ccsds.inspector contains part of the open source icon set Eva Icons
by Akveo (https://github.com/akveo/eva-icons), released under the terms of the MIT License;
- eu.dariolucia.ccsds.sle.utl contains copy of the ASN.1 definition as listed on https://public.ccsds.org/Publications/BlueBooks.aspx, 
with modifications to include support for older SLE versions, which can be retrieved at https://public.ccsds.org/Publications/SilverBooks.aspx;
- eu.dariolucia.ccsds.encdec contains code derived from the work of Millau Julien, author of the Bit-lib4j library (https://github.com/devnied/Bit-lib4j), released under the terms of the Apache License, 2.0;
- eu.dariolucia.ccsds.tmtc contains code derived from the work of Nayuki (https://www.nayuki.io), author of the Reed-Solomon implementation at (https://www.nayuki.io/page/reed-solomon-error-correcting-code-decoder). Nayuki has all rights reserved for the original code. The derivative work in this module is released under the terms of the Apache License, 2.0;
- eu.dariolucia.ccsds.encdec contains code derived from the work of David Overeem, author of the xtcetools library (https://gitlab.com/dovereem/xtcetools), released under the terms of the Apache License, 2.0;
- Snippets from StackOverflow should be reported with their SO link and are released under Creative Commons. I might have missed some though. In such case, please inform me.

All other source code and resources, such as test data files, FXML and some images are genuine new implementation or 
generated using the library itself (e.g. TM data file, SLE configuration file). These resources are therefore released 
under the terms of the Apache License, 2.0.

Each module contains a NOTICE file that contains all the required attributions. 

**By using any of the resources on this repository, it is required to comply with the terms and conditions of the Apache 
License, including the distribution of the NOTICE files of the modules you use/modify in any software/products you 
deliver commercially or free of charge, in binary or source code. A copy of the relevant licenses must be provided: 
relevant links are provided in the NOTICE file.** 

**_Make sure you comply with all license terms. I take this point very seriously. Really._**

If by any chance I forgot/missed to provide due credits to authors or I made inappropriate use of any license terms, please contact me here on GitHub.

# Acknowledgements
I would like to thank the people behind the following technologies/libraries, which the code in this repository depends on. 
Too often we give things for granted, forgetting the amazing amount of work that people spend everyday to deliver good resources, efficient code and effective solutions to everybody free of charge:
- The OpenJDK development team (https://openjdk.java.net);
- The OpenJFX development team (https://openjfx.io);
- The Glassfish/JAXB development team (https://javaee.github.io/glassfish);
- The jASN.1 development team (https://github.com/beanit/jasn1);
- Akveo for the amazing Eva Icons (https://akveo.github.io/eva-icons);
- Christian Schudt, Diego Cirujano "DateAxis" and Pedro Duque Vieira for their DateAxis class (http://myjavafx.blogspot.com/2013/09/javafx-charts-display-date-values-on.html) (https://github.com/dukke/FXCharts);
- Millau Julien, author of the Bit-lib4j library (https://github.com/devnied/Bit-lib4j), whose code has been adapted and improved to implement an efficient bit encoder/decoder;
- David Overeem, author of the xtcetools library (https://gitlab.com/dovereem/xtcetools), whose MIL-STD-1750A code has been adapted and improvded to implement MIL real values conversion from/to native Java values;
- Nayuki, author of the Reed Solomon implementation (https://www.nayuki.io/page/reed-solomon-error-correcting-code-decoder), who was so kind to allow me using the code for the implementation of the CCSDS-compliant RS encoder. Make sure you pay a visit to her website at https://www.nayuki.io, it is a real gem;
- The StackOverflow community;
- ... and many many others.

A special mention goes to the people that took some of their time to contribute to this project by providing improvements via Pull Requests, suggestions, or simply their time to test the various implementations and providing feedback:
- Alessio Di Fazio (https://github.com/slux83) for the POM update to generate OSGi compliant bundles
- Javier Peña (https://github.com/xavipen) for the .gitignore and POM update to improve the support in Eclipse IDE  
- nculijun (https://github.com/nculijun) for the approach to package UI applications into self-executable JARs
- sv5d (https://github.com/sv5d) for bug fixes to the SLE code
- Artur Scholz (https://gitlab.com/artur-scholz) and Sarah Quehl (https://gitlab.com/Sarah2610) for the interoperability tests with the Python CFDP implementation from LibreCube: https://gitlab.com/librecube/lib/python-cfdp 
- Lucas Bremond (https://github.com/lucas-bremond) for the optimisation of the COP-1 ACK implementation

# Contributions
If you find any issue with the provided code or you would like to see a specific feature implemented, please raise a 
request on GitHub. If you spot and fix any issue by using the provided code, please consider contributing back to the 
project, by raising the issue on GitHub, hopefully with your solution attached and/or a pull request. 
