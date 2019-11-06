/*
 *   Copyright (c) 2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package eu.dariolucia.ccsds.sle.provider;

import eu.dariolucia.ccsds.sle.utl.config.ServiceInstanceConfiguration;
import eu.dariolucia.ccsds.sle.utl.config.UtlConfigurationFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Sample test application that loads the service instances as specified in an SLE configuration file and instantiates
 * a test provider for each specified service instance. In case of port conflicts, only the first service instance
 * using the conflicting port is instantiated.
 *
 * Frame generation data rate is hardcoded.
 */
public class SleTestProvider {

    protected static final Logger LOG = Logger.getLogger(SleTestProvider.class.getName());

    public static void main(String[] args) throws IOException, InterruptedException {
        // Load the configuration
        UtlConfigurationFile file = null;
        if(args.length > 1) {
            file = UtlConfigurationFile.load(new FileInputStream(args[0]));
        } else {
            file = UtlConfigurationFile.load(SleTestProvider.class.getClassLoader().getResourceAsStream("configuration_test_provider.xml"));
        }

        // Load the service instance if the port is not already busy
        List<ServiceInstanceManager> managers = new LinkedList<>();
        Set<String> usedPorts = new HashSet<>();
        for(ServiceInstanceConfiguration configuration : file.getServiceInstances()) {
            if(!usedPorts.contains(configuration.getResponderPortIdentifier())) {
                usedPorts.add(configuration.getResponderPortIdentifier());
                ServiceInstanceManager manager = ServiceInstanceManager.build(configuration, file.getPeerConfiguration());
                managers.add(manager);
            } else {
                LOG.warning("Service instance " + configuration.getServiceInstanceIdentifier() + " skipped, conflicting port " + configuration.getResponderPortIdentifier());
            }
        }
        // Activate the managers
        managers.forEach(ServiceInstanceManager::activate);
        // Wait for the end
        while(true) {
            Thread.sleep(1000);
        }
    }
}
