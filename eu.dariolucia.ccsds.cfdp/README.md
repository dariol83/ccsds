# CFDP module
This module provides a CFDP library, implementing a CFDP entity and all the PDUs defined in the CCSDS standard CCSDS 727.0-B-5.

Full working examples can be found in the *examples* module and in the *cfdp.fx* module.

## Getting started
### MIB
The MIB is defined as an XML file. An example of such file, with comments, is provided on the box below.

    <ns1:mib xmlns:ns1="http://dariolucia.eu/ccsds/cfdp/mib">
        <!-- Definition of the local entity, as in 8.2 -->
        <local-entity id="1"                                    <!-- ID of the local entity (integer from 0 to 2^63 - 1) -->
                      EOF-sent-indication="true"                <!-- EOF-Sent.indication required -->
                      EOF-recv-indication="true"                <!-- EOF-Recv.indication required -->
                      file-segment-recv-indication="true"       <!-- File-Segment-Recv.indication required -->
                      transaction-finished-indication="true"    <!-- Transaction-Finished.indication required when acting as receiving entity -->
                      suspended-indication="true"               <!-- Suspended.indication required when acting as receiving entity -->
                      resumed-indication="true">                <!-- Resumed.indication required when acting as receiving entity -->
                      completed-transactions-cleanup-period="-1"<!-- Period to check for disposed transactions to be removed from the entity (-1: disabled, 0: remove upon disposal, >0 check period in seconds)  
            <!-- For each type of fault condition, a default handler (as enumerated in 4.8.2) -->
            <fault-handlers>
                <fault-handler condition="CC_POS_ACK_LIMIT_REACHED" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_KEEPALIVE_LIMIT_REACHED" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_INVALID_TX_MODE" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_FILESTORE_REJECTION" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_FILE_CHECKSUM_FAILURE" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_FILE_SIZE_ERROR" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_NAK_LIMIT_REACHED" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_INACTIVITY_DETECTED" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_INVALID_FILE_STRUCTURE" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_CHECK_LIMIT_REACHED" strategy="NOTICE_OF_CANCELLATION" /> 
                <fault-handler condition="CC_UNSUPPORTED_CHECKSUM_TYPE" strategy="NOTICE_OF_CANCELLATION" />
            </fault-handlers>
        </local-entity>
        <!-- Definition of the remote entities, as in 8.3. This example defines three remote entities, with three
         different UT layers. -->
        <remote-entities>
            <remote-entity id="2"                                       <!-- Entity ID -->
                           version="1"                                  <!-- CFDP protocol version implemented at this entity -->
                           positive-ack-timer-interval="2000"           <!-- Expressed in milliseconds, -1 to disable -->
                           nak-timer-interval="100000"                  <!-- Expressed in milliseconds, -1 to disable -->
                           keep-alive-interval="30000"                  <!-- Expressed in milliseconds, -1 to disable -->
                           immediate-nak-mode="true"                    <!-- If true, a NAK is sent upon reception of a file data PDU, which is non contiguous with the previous one -->
                           default-tx-mode-acknowledged="true"          <!-- Acknowledged (true) or unacknowledged (false) -->
                           transaction-closure-requested="true"         <!-- If true, unacknowledge transactions request closure -->
                           check-interval="10000"                       <!-- For use in determining imputed end of transaction, expressed in milliseconds, or -1 to disable -->
                           check-interval-expiration-limit="5"          <!-- How often the check-interval must be reset, before giving up -->
                           default-checksum="0"                         <!-- Default type of checksum to calculate for all file transmission to this remote entity, as defined in the SANA Checksum Types registry -->
                           retain-incomplete-files-on-cancel="true"     <!-- If true, incomplete file transfers from this entity are retained; if false, they are discarded -->
                           crc-required="true"                          <!-- If true, CRC is required; if false, CRC is not required -->
                           max-file-segment-length="1024"               <!-- Maximum file segment length in bytes -->
                           keep-alive-limit="-1"                        <!-- Keep-Alive discrepancy limit, in bytes, -1 to disable -->
                           positive-ack-expiration-limit="3"            <!-- Number of expirations of the positive-ack-timer-interval -->
                           nak-expiration-limit="4"                     <!-- Number of expirations of the nak-timer-interval -->
                           transaction-inactivity-limit="3600000"       <!-- Expressed in milliseconds, or -1 to disable -->
                           nak-recomputation-interval="60000">          <!-- Implementation-specific property: NAK recomputation periodically executed. Expressed in milliseconds, or -1 to disable -->
                <ut-layer>TCP</ut-layer>                    <!-- Name of the UT layer implementation, as registered in the code -->
                <address>tcp:127.0.0.1:23002</address>      <!-- Entity-linked address, format is UT layer implementation-specific -->
            </remote-entity>
            <remote-entity id="3"
                           version="1"
                           positive-ack-timer-interval="2000"
                           nak-timer-interval="100000"
                           keep-alive-interval="30000"
                           immediate-nak-mode="true"
                           default-tx-mode-acknowledged="true"
                           transaction-closure-requested="true"
                           check-interval="10000"
                           check-interval-expiration-limit="5"
                           default-checksum="0"
                           retain-incomplete-files-on-cancel="true"
                           crc-required="true"
                           max-file-segment-length="1024"
                           keep-alive-limit="-1"
                           positive-ack-expiration-limit="3"
                           nak-expiration-limit="4"
                           transaction-inactivity-limit="3600000"
                           nak-recomputation-interval="60000">
                <ut-layer>UDP</ut-layer>
                <address>udp:127.0.0.1:23002</address>
            </remote-entity>
            <remote-entity id="4"
                           version="1"
                           positive-ack-timer-interval="2000"
                           nak-timer-interval="100000"
                           keep-alive-interval="30000"
                           immediate-nak-mode="true"
                           default-tx-mode-acknowledged="true"
                           transaction-closure-requested="true"
                           check-interval="10000"
                           check-interval-expiration-limit="5"
                           default-checksum="0"
                           retain-incomplete-files-on-cancel="true"
                           crc-required="true"
                           max-file-segment-length="1024"
                           keep-alive-limit="-1"
                           positive-ack-expiration-limit="3"
                           nak-expiration-limit="4"
                           transaction-inactivity-limit="3600000"
                           nak-recomputation-interval="60000">
                <ut-layer>CUSTOM_LAYER_1</ut-layer>
                <address></address>
            </remote-entity>
            <remote-entity id="5"
                           version="1"
                           positive-ack-timer-interval="2000"
                           nak-timer-interval="100000"
                           keep-alive-interval="30000"
                           immediate-nak-mode="true"
                           default-tx-mode-acknowledged="true"
                           transaction-closure-requested="true"
                           check-interval="10000"
                           check-interval-expiration-limit="5"
                           default-checksum="0"
                           retain-incomplete-files-on-cancel="true"
                           crc-required="true"
                           max-file-segment-length="1024"
                           keep-alive-limit="-1"
                           positive-ack-expiration-limit="3"
                           nak-expiration-limit="4"
                           transaction-inactivity-limit="3600000"
                           nak-recomputation-interval="60000">
                <ut-layer>UDP</ut-layer>
                <address>udp:127.0.0.1:25002</address>
            </remote-entity>
        </remote-entities>
    </ns1:mib>
    
In the MIB above, it can be observed that the defined remote entities have 3 different UT layer implementation assigned, with 2 entities both using the UDP layer.

In order to create the related MIB object programmatically from a file, the following code snippet can be used.

    Mib mib = Mib.load(new FileInputStream(pathToFile));
    
A MIB object is required to create the CFDP entity. Such object can be created from any InputStream delivering the required data.

### Virtual filestore
A virtual filestore must implement the IVirtualFilestore interface, providing methods to access and 
manipulate files in the filestore. 

This library provides an implementation of a virtual filestore based on the local filesystem. A filesystem-based filestore can be instantiated in code using the following snipped.

    File fs1Folder = new File(filestoreFolder);
    if(!fs1Folder.exists()) {
        fs1Folder.mkdirs();
    }
    FilesystemBasedFilestore filestore = new FilesystemBasedFilestore(fs1Folder);

A IVirtualFilestore implementation is require to create the CFDP entity.

It must be noted that this implementation enables a single level of nesting for files in the filestore. The path to indicate a file is <folder>/<filename>, or <filename or folder> to indicate items
in the root path. 

### UT layers
A UT layer must implement the IUtLayer interface. The design of the UT layer in this library foresees that a UT layer implementation can handle multiple data stream to different destinations, so the implementation
shall be able to multiplex/demultiplex from/to different remote endpoints.

This library provides an implementation of two UT layers: UDP and TCP. They can be instantiated as presented in the following snipped.

    TcpLayer tcpLayer = new TcpLayer(mib, tcpPort);
    tcpLayer.activate();
    UdpLayer udpLayer = new UdpLayer(mib, udpPort);
    udpLayer.activate();

It must be noted that the activate() method is specific to the implementations of this library, which are based on an abstract AbstractUtLayer class. This class can be used as base class for 
IUtLayer implementations. The CFDP entity implementation of this library works against the IUtLayer interface, therefore it is not calling the activate() method.
An important design aspect of this library is related to the way the CFDP entity selects the right IUtLayer implementation for the given remove destination: the implementation
looks up for a UT layer, whose getName() method returns the name as specified in the MIB. For instance, the getName() method of the TCP UT layer of this implementation
returns "TCP". Therefore, remote destinations configured on such UT layer must use the tag <ut-layer>TCP</ut-layer> in the MIB. 

For the TCP layer, the address format is:

    'tcp:'\<host name or address>':'\<port number>

For the UDP layer, the address format is:

    'udp:'\<host name or address>':'\<port number>

The designed approach provides flexibility to add any additional UT layer implementation and to deliver to the implementation the required configuration for each configured remote entity destination.

As soon as the UT layer is not needed anymore, the dispose() method should be called to release any internal resource.

    tcpLayer.dispose();
    
The CFDP entity is *not* disposing the UT layers registered in its constructor call, when the CFDP entity is disposed.

### Entity creation and disposal
In order to create a CFDP entity, it is necessary to provide:
- The MIB (a Mib object)
- The virtual filestore to use (an object implementing IVirtualFilestore)
- An optional transaction ID generator 
- A list of UT layers (one or more objects implementing IUtLayer)


    ICfdpEntity entity = ICfdpEntity.create(mib, filestore, tcpLayer, udpLayer);

Once the CFDP entity is created, it can be immediately used. In order to receive indications from the entity, one or more subscribers
implementing the ICfdpEntitySubscriber interface can be registered to the entity.

    this.entity.register(this);

In order to allow the entity to receive/transmit CFDP PDU, the different UT layers must be informed about the TX/RX availability.
This is done by calling the setRx/TxAvailability methods on each supplied IUtLayer implementation.
If the TX/RX availability is not relevant, in the example below this is set to true (available) for the registered UT layers (TCP and UDP) 
in relation to all MIB-defined remote entities. 

    // RX/TX availability
    for(RemoteEntityConfigurationInformation r : conf1File.getRemoteEntities()) {
        tcpLayer.setRxAvailability(true, r.getRemoteEntityId());
        tcpLayer.setTxAvailability(true, r.getRemoteEntityId());
        udpLayer.setRxAvailability(true, r.getRemoteEntityId());
        udpLayer.setTxAvailability(true, r.getRemoteEntityId());
    }
    
Once operations on the CFDP entity are over, the method dispose() shall be called to stop the internal threading and to release the resources.

### Entity operations
Get operations on the ICfdpEntity are synchronous. The other operations (including requests) on the ICfdpEntity are fully asynchronous. Requests must be created and provided to the entity by means of the request() method.
Indications are provided to all registered subscribers. The javadoc of each request and indication objects explain their usage and purpose in detail.


