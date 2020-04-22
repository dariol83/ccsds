[<img src="https://img.shields.io/maven-central/v/eu.dariolucia.ccsds/eu.dariolucia.ccsds?color=greem&style=flat">](https://search.maven.org/search?q=eu.dariolucia.ccsds)
[<img src="https://img.shields.io/travis/dariol83/ccsds?style=flat">](https://travis-ci.org/dariol83/ccsds)
[<img src="https://img.shields.io/sonar/violations/eu.dariolucia.ccsds:eu.dariolucia.ccsds?format=long&server=https%3A%2F%2Fsonarcloud.io&style=flat">](https://sonarcloud.io/project/issues?id=eu.dariolucia.ccsds%3Aeu.dariolucia.ccsds&resolved=false)
[<img src="https://img.shields.io/sonar/coverage/eu.dariolucia.ccsds:eu.dariolucia.ccsds?server=https%3A%2F%2Fsonarcloud.io&style=flat">](https://sonarcloud.io/component_measures?id=eu.dariolucia.ccsds%3Aeu.dariolucia.ccsds&metric=coverage&view=list)
[<img src="https://img.shields.io/sonar/quality_gate/eu.dariolucia.ccsds:eu.dariolucia.ccsds?server=https%3A%2F%2Fsonarcloud.io&style=flat">](https://sonarcloud.io/dashboard?id=eu.dariolucia.ccsds%3Aeu.dariolucia.ccsds)

# An open source implementation of CCSDS protocols and formats in Java 11
[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fdariol83%2Fccsds.svg?type=shield)](https://app.fossa.io/projects/git%2Bgithub.com%2Fdariol83%2Fccsds?ref=badge_shield)

This repository contains an attempt to code an open source, not validated (but hopefully working and performant) implementation of some of the public available CCSDS standards (Time Code Format, SLE, TM/TC/AOS, Space Packet) and support utilities. The implementation has been done in Java and follows the latest modular changes in the Java platform since version 9.

Stable releases are published on Maven Central. The latest code version is always available on the master branch here on GitHub.

**Disclaimer: this code is implemented as personal hobby, without any use of external systems or software to test or validate its behaviour (it is neither operationally nor effectively tested). It is not endorsed by any organization, association or company. It is the result of my passion for software engineering and coding, and of hours spent developing during my evenings. I put the best of me into this, but compliance to the different standards as well as correct behaviour is not guaranteed. As the Apache License makes clear, use the modules of this repository at your own risk.**

## SLE
The SLE User Test Library (eu.dariolucia.ccsds.sle.utl) is a test library that implements the user side (fully compliant) and provider side (partially compliant) of an SLE data exchange session. 
It supports RAF, RCF, ROCF and CLTU. Support for FSP is currently missing. 
It uses a custom format for the configuration of the service instances (currently not documented, can be derived by the classes inside 
the "config" package).
Highlights are:
- support of multiple SLE versions
- support of credentials encoding SHA-1 and SHA-256 independently from the SLE version
- support of user and provider as initiator
- light codebase, as it heavily relies on the generated code from the ASN.1 definitions

A simple but effective graphical interface to inspect the user-side peer is provided as separate module (eu.dariolucia.ccsds.sle.utlfx). 

## TM/TC
The TM/TC Library (eu.dariolucia.ccsds.tmtc) is a library that provide support to parse and build CCSDS TM frames, TC frames, AOS frames, Space Packets. It allows encoding/decoding of space packets into TM frames, TC frames or AOS frames. It also provides some basic encodings (e.g. CLTU encoding/decoding, randomization, FECF encoding/checking, Reed-Solomon encoding/checking) but no error correction capabilities. 
A simple graphical interface to inspect TM/TC/AOS/Space Packet data is provided as separate module (eu.dariolucia.ccsds.inspector). It is possible to implement custom connectors to allow inspection of data delivered from a custom source/protocol/format by providing a new 
service (in Java 11 terms) implementing the interface eu.dariolucia.ccsds.inspector.api.IConnectorFactory. Some connectors to read data from file or socket are already available.

## ENC/DEC
A packet identification/encoding/decoding library, which supports basic data types (integer, real, enumeration, CCSDS absolute and 
relative time formats, strings, octet streams, bit streams), array structures, ECSS TM/TC PUS headers, as derived from the ECSS Packet Utilisation Standard (http://everyspec.com/ESA/download.php?spec=ECSS-E-70-41A.047794.pdf). The focus on this implementation is on reliable and efficient packet identification and decoding performance. Encoding performance has not been measured. 
The packet structure is defined using a custom XML format definition, built by means of JAXB annotations.

## Examples
A modules providing examples on how to use the three modules above in a combined way, to demonstrate the capabilities of the library to write compact code. The following examples are provided:
- TM generator with output to file and/or TCP socket, from packet definition to TM CADU or TM frame;
- TM processor with output to console, from CADU or TM frames read from a TCP socket to encoded parameter extraction;
- TM processor with output to console, from SLE RAF to encoded parameter extraction;
- TC generator from list of parameters (as defined in the packet definition) to CLTU with output to file and/or TCP socket;
- TC generator from list of parameters to SLE CLTU;
- TC processor with output to console, from CLTU to encoded parameter extraction.

**The main target of the examples module is not to provide final applications ready to be used**, rather to explain the different levels and concepts of the libraries, and how to link the various objects together. 

# Goals
The purpose of this repository is to produce, as far as my possibilities allow, simple, well-designed, well-documented, well-tested code with top performance. For each module (examples and tools excluded) I have the following targets:
- full CI integration with Travis CI;
- at least 90% line coverage with unit tests (measured by JaCoCo and reported in Sonarcloud);
- a set of performance tests;
- no warnings when compiling;
- a *reasonable* quality gate, with *reasonable* QA metrics measured by Sonarcloud;
- good documentation at package, class, instance variable and method level;
- provide specific code, and not generic, one-fits-all code: generic code is usually slow because it is over-engineered. Here simplicity is the key.

Each module is built using Maven and can be compiled out of the box (if all its dependencies are satisfied) with Maven. If you want to compile them all in one go, just run 'mvn clean install' on the root folder.

# License
All original source code on this repository is released under the terms and conditions of the Apache License 2.0. The source code includes code and resources from other authors, which can be freely obtained from the web. To be more specific:
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

All other source code and resources, such as test data files, FXML and some images are genuine new implementation or generated using the library itself (e.g. TM data file, SLE configuration file). These resources are therefore released under the terms of the Apache License, 2.0.

As a general rule, I tried to make sure that each external contribution is acknowledged where the contribution appears. Each module contains a NOTICE file that contains all the required attributions. By using any of the resources on this repository, you need to comply with the terms and conditions of the Apache License, including the distribution of the NOTICE files of the modules you use/modify in any software/products you deliver commercially or free of charge, in binary or source code. 
A copy of the relevant licenses must be provided: I usually include the relevant links in the NOTICE file. 

**_Make sure you comply with all license terms. I take this point very seriously. Really._**

If by any chance I forgot/missed to provide due credits to authors or I made inappropriate use of any license terms, please contact me here on GitHub.


[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fdariol83%2Fccsds.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fdariol83%2Fccsds?ref=badge_large)

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

A special mention goes to the people that took some of their time to contribute to this project by providing improvements via Pull Requests:
- Alessio Di Fazio, for the POM update to generate OSGi compliant bundles;
- Javier Peña, for the .gitignore and POM update to improve the support in Eclipse IDE.  

# Contributions
If you find any issue with the provided code or you would like to see a specific feature implemented, please raise a request on GitHub.
If you spot and fix any issue by using the provided code, please consider contributing back to the project, by raising the issue on 
GitHub, hopefully with your solution attached and/or a pull request :)

I am not looking for active contributors, because I would pretend from them the same goals I set a few paragraphs above. However, if you want to actively contribute to this project and you think you can accept my way of dealing with the code, please contact me :)