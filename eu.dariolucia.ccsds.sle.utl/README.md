# SLE User Test Library module
This module is a test library that implements the user side and provider side of an SLE data exchange session. 
It supports RAF, RCF, ROCF and CLTU from version 1 to version 5. Support for FSP is currently missing and it is not planned to be added. 
It uses a custom format for the configuration of the service instances.

Main features are:
- support of multiple SLE versions
- support of credentials encoding SHA-1 and SHA-256 independently from the SLE version
- support of user and provider as initiator
- light codebase, as it heavily relies on the generated code from the ASN.1 definitions

In terms of performances: on my Intel i5 2.67 GHz (year 2010), the TML implementation can reach a reception and sending data rate of 2 Gbps.
A test using a RAF service instance with a transfer buffer of 80 frames demonstrates the capability of the module to receive and decode up to 2 Gpbs as well,
i.e. receiving more than 4.7M frames (1115 bytes per frame) in 20 seconds.

## Getting started
### Configuration files
Each service instance is independent from the others, and requires two information to be instantiated:
- The peer configuration, i.e. PeerConfiguration
- The service instance configuration, i.e. ServiceInstanceConfiguration in one of its concrete implementations (for RAF, RCF, ROCF, CLTU types).

In the module, these two information are contained in the same XML configuration file, which is defined according to the JAXB annotations
defined on the UtlConfigurationFile class. 
 
In terms of code, the UtlConfigurationFile object can be loaded from the XML by invoking:
 
     UtlConfigurationFile.load(InputStream input)
 
Example: assuming that the UtlConfigurationFile XML file is residing in /home/dev/myDefinitions.xml
 
     UtlConfigurationFile configFile = UtlConfigurationFile.load(new FileInputStream("/home/dev/myDefinitions.xml"))
     
### User side
To instantiate an SLE service instance as user, the following code (e.g. with reference to RAF service instance) can be used:

    UtlConfigurationFile userFile = UtlConfigurationFile.load(new FileInputStream(...));
    RafServiceInstanceConfiguration rafConfigU = (RafServiceInstanceConfiguration) userFile.getServiceInstances().get(0); // Assuming that the first service instance in the file is the RAF SI we want to load
    RafServiceInstance rafUser = new RafServiceInstance(userFile.getPeerConfiguration(), rafConfigU);
    rafUser.configure();

Once the configure() method is invoked, it is possible to monitor the status of the service instance, including receiving all provider-initiated SLE operations, by registering to the service instance providing an 
implementation of the IServiceInstanceListener interface.
    
    IServiceInstanceListener listener = ...;
    rafUser.register(listener);
    
Finally, it is possible to request SLE operations (e.g. a bind):

    rafUser.bind(2); // Bind with version 2

In case of binds being sent by the provider, the service instance can be instructed to wait for a bind (e.g. send a positive return):
    
    rafUser.waitForBind(true, null);

It is important to notice that operation requests are asynchronous and the service instance manages its internals by thread confinement.

Once the service instance is no longer needed, it must be disposed to release its internal resources:

    rafUser.dispose();

### Provider side
To instantiate an SLE service instance as provider, the following code (e.g. with reference to CLTU service instance) can be used:

    UtlConfigurationFile providerFile = UtlConfigurationFile.load(new FileInputStream(...));
    CltuServiceInstanceConfiguration cltuConfigP = (CltuServiceInstanceConfiguration) providerFile.getServiceInstances().get(0); // Assuming that the first service instance in the file is the CLTU SI we want to load
    CltuServiceInstanceProvider cltuProvider = new CltuServiceInstanceProvider(userFile.getPeerConfiguration(), cltuConfigP);
    cltuProvider.configure();

Once the configure() method is invoked, it is possible to monitor the status of the service instance, including receiving all provider-initiated SLE operations, by registering to the service instance providing an 
implementation of the IServiceInstanceListener interface.
    
    IServiceInstanceListener listener = ...;
    cltuProvider.register(listener);

For provider services, additional handlers in the form of functions can be registered, to manage the START, TRANSFER-DATA (CLTU only) and THROW-EVENT (CLTU only) operations:
For a CLTU service instance, the registration of the TRANSFER-DATA handler is mandatory to avoid all TRANSFER-DATA operations to be rejected with a negative response. 
The same applies to the THROW-EVENT handler.

    cltuProvider.setTransferDataOperationHandler(...);
    cltuProvider.setThrowEventOperationHandler(...);

In case of binds being sent by the user, the service instance can be instructed to wait for a bind (e.g. send a positive return):

    cltuProvider.waitForBind(true, null);

Once the service instance is no longer needed, it must be disposed to release its internal resources:

    cltuProvider.dispose();
