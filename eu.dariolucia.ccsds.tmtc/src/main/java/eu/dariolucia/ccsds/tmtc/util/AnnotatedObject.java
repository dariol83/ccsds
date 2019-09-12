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

package eu.dariolucia.ccsds.tmtc.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A binary object (i.e. with a length) that allows to attach annotations in the form of key-value pairs.
 *
 * This class is not thread-safe.
 */
public abstract class AnnotatedObject {

	public abstract int getLength();

	// Annotations
	private final Map<Object, Object> annotations = new LinkedHashMap<>();

	/**
	 * This method returns the set of keys present for the registered annotations.
	 *
	 * @return the set of annotation keys
	 */
	public final Set<Object> getAnnotationKeys() {
		return this.annotations.keySet();
	}

	/**
	 * This method returns the value linked to the provided key.
	 *
	 * @param key the annotation key
	 * @return the value linked to the key, null if no value
	 */
	public final Object getAnnotationValue(Object key) {
		return this.annotations.get(key);
	}

	/**
	 * This method sets the value linked to the provided key.
	 *
	 * @param key the annotation key
	 * @param value the annotation value
	 */
	public final void setAnnotationValue(Object key, Object value) {
		this.annotations.put(key, value);
	}

	/**
	 * This method sets the value linked to the provided key, if absent.
	 *
	 * @param key the annotation key
	 * @param value the annotation value
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key.
	 */
	public final Object setAnnotationValueIfAbsent(Object key, Object value) {
		return this.annotations.putIfAbsent(key, value);
	}

	/**
	 * This method removes the annotation (if present) by key.
	 *
	 * @param key the annotation key
	 * @return the value linked to the key, null if no value
	 */
	public final Object clearAnnotationValue(Object key) {
		return this.annotations.remove(key);
	}

	/**
	 * This method clears all the annotations.
	 */
	public final void clearAnnotations() {
		this.annotations.clear();
	}

	/**
	 * This method checks if an annotation key is present.
	 *
	 * @param key the annotation key
	 * @return true if the annotation key is present, false otherwise
	 */
	public final boolean isAnnotationPresent(Object key) {
		return this.annotations.containsKey(key);
	}
}
