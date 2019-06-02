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

import java.io.File;
import java.util.*;

public final class ConnectorConfiguration {

	private final Map<String, ConnectorProperty<?>> properties = new LinkedHashMap<>();
	private final List<String> ids = new ArrayList<>();

	public ConnectorConfiguration(List<ConnectorProperty<?>> props) {
		for(ConnectorProperty<?> p : props) {
			properties.putIfAbsent(p.getId(), p);
			ids.add(p.getId());
		}
	}

	public ConnectorProperty<?> getProperty(String id) {
		return properties.get(id);
	}

	public <T> T getPropertyValue(String id, Class<T> type) {
		return (T) properties.get(id).getValue();
	}

	public boolean isPropertySet(String id) {
		return properties.containsKey(id) && getProperty(id) != null;
	}

	public Integer getIntProperty(String id) {
		return getPropertyValue(id, Integer.class);
	}

	public Boolean getBooleanProperty(String id) {
		return getPropertyValue(id, Boolean.class);
	}

	public Long getLongProperty(String id) {
		return getPropertyValue(id, Long.class);
	}

	public Double getDoubleProperty(String id) {
		return getPropertyValue(id, Double.class);
	}

	public String getStringProperty(String id) {
		return getPropertyValue(id, String.class);
	}

	public File getFileProperty(String id) {
		return getPropertyValue(id, File.class);
	}

	public Date getDateProperty(String id) {
		return getPropertyValue(id, Date.class);
	}

	public <T extends Enum<T>> T getEnumProperty(String id) {
		return (T) getProperty(id).getValue();
	}

	public List<String> getIds() {
		return List.copyOf(ids);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ConnectorConfiguration [\n");
		for(Map.Entry<String, ConnectorProperty<?>> e : this.properties.entrySet()) {
			sb.append("\t").append(e.getKey()).append(": ").append(e.getValue().getValueAsString()).append("\n");
		}
		sb.append("]");
		return sb.toString();
	}
}
