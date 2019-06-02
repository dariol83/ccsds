/*
 * Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.inspector.api;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ConnectorConfigurationDescriptor {

	private final List<ConnectorPropertyDescriptor<?>> properties = new CopyOnWriteArrayList<>();

	public void add(ConnectorPropertyDescriptor<?>... descriptors) {
		for(ConnectorPropertyDescriptor<?> descriptor : descriptors) {
			if (properties.contains(descriptor)) {
				throw new IllegalArgumentException("Property descriptor " + descriptor.getId() + " already registered");
			}
		}
		properties.addAll(Arrays.asList(descriptors));
	}

	public List<ConnectorPropertyDescriptor<?>> getProperties() {
		return List.copyOf(properties);
	}
}
