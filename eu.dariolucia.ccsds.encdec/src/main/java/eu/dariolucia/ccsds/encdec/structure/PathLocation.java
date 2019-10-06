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

package eu.dariolucia.ccsds.encdec.structure;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link PathLocation} represents a logical path inside a packet. It always starts with the ID of the packet definition.
 * Separation among path components is performed by dots, i.e. '.'.
 * Separation between the array name and the array index is performed by a hash.
 *
 * This class is immutable.
 */
public final class PathLocation {

    public static final char PATH_COMPONENT_SEPARATOR = '.';
    public static final char ARRAY_INDEX_SEPARATOR = '#';

    /**
     * Create a new {@link PathLocation} with the provided items.
     *
     * @param items the parts composing the path
     * @return the {@link PathLocation} instance
     */
    public static PathLocation of(String... items) {
        return new PathLocation(items);
    }

    /**
     * Create a new {@link PathLocation} with the provided list of items.
     *
     * @param items the parts composing the path
     * @return the {@link PathLocation} instance
     */
    public static PathLocation of(List<String> items) {
        return new PathLocation(items.toArray(new String[0]));
    }

    private final List<PathLocationItem> pathComponents;

    private PathLocation(String... pathComponents) {
        if(pathComponents == null) {
            throw new NullPointerException("Path component list cannot be null");
        }
        this.pathComponents = new LinkedList<>();
        for(String s : pathComponents) {
            if(s.contains("#")) {
                String name = s.substring(0, s.lastIndexOf('#'));
                int idx = Integer.parseInt(s.substring(s.lastIndexOf('#') + 1));
                this.pathComponents.add(new PathLocationItem(name));
                this.pathComponents.add(new PathLocationItem(idx));
            } else {
                this.pathComponents.add(new PathLocationItem(s));
            }
        }
    }

    private PathLocation(List<PathLocationItem> items) {
        this.pathComponents = List.copyOf(items);
    }

    /**
     * Return the first component of the path.
     *
     * @return the first component of the path
     */
    public String first() {
        return at(0);
    }

    /**
     * Return the last component of the path.
     *
     * @return the last component of the path
     */
    public String last() {
        return at(this.pathComponents.size() - 1);
    }

    /**
     * Return the length of the path in terms of components.
     *
     * @return the length of the path in terms of components
     */
    public int length() {
        return this.pathComponents.size();
    }

    /**
     * Return the component path at the specified position.
     *
     * @param idx the position in the path
     * @return the component path at the specified position
     */
    public String at(int idx) {
        PathLocationItem item = this.pathComponents.get(idx);
        if(item.name != null) {
            return item.name;
        } else {
            return this.pathComponents.get(idx - 1).name + ARRAY_INDEX_SEPARATOR + item.index;
        }
    }

    /**
     * Return a new {@link PathLocation} after appending the specified index. It assumes that the last component
     * indicates an array.
     *
     * @param idx the array index to append
     * @return the new {@link PathLocation}
     */
    public PathLocation appendIndex(int idx) {
        // Check if it can be appended
        if(pathComponents.isEmpty() || pathComponents.get(pathComponents.size() - 1).name == null) {
            throw new IllegalStateException("Cannot add index " + idx + " to path " + toString());
        }
        List<PathLocationItem> copy = new LinkedList<>(pathComponents);
        copy.add(new PathLocationItem(idx));
        return new PathLocation(copy);
    }

    /**
     * Return a new {@link PathLocation} after appending the specified path component.
     *
     * @param pathComponent the path component to append
     * @return the new {@link PathLocation}
     */
    public PathLocation append(String pathComponent) {
        List<PathLocationItem> copy = new LinkedList<>(pathComponents);
        copy.add(new PathLocationItem(pathComponent));
        return new PathLocation(copy);
    }

    /**
     * Return the parent path component.
     *
     * @return the parent path component
     */
    public PathLocation parent() {
        return new PathLocation(pathComponents.subList(0, pathComponents.size() - 1));
    }

    /**
     * Check if this {@link PathLocation} is parent of the provided path location.
     *
     * @param potentialChild the path location to check
     * @return true if this is a parent of the provided path location, otherwise false
     */
    public boolean isParentOf(PathLocation potentialChild) {
        return potentialChild.toString().startsWith(this.toString());
    }

    /**
     * Check if this {@link PathLocation} is child of the provided path location.
     *
     * @param potentialParent the path location to check
     * @return true if this is a child of the provided path location, otherwise false
     */
    public boolean isChildOf(PathLocation potentialParent) {
        return potentialParent.isParentOf(potentialParent);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for(PathLocationItem pc : pathComponents) {
            if(pc.name != null) {
                if(sb.length() > 0) {
                    sb.append(PATH_COMPONENT_SEPARATOR);
                }
                sb.append(pc.name);
            } else {
                if(sb.length() > 0) {
                    sb.append(ARRAY_INDEX_SEPARATOR);
                }
                sb.append(pc.index);
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathLocation that = (PathLocation) o;
        return pathComponents.equals(that.pathComponents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathComponents);
    }

    private class PathLocationItem {

        public final String name;
        public final int index;

        public PathLocationItem(String name) {
            this.name = name;
            this.index = -1;
        }

        public PathLocationItem(int index) {
            this.index = index;
            this.name = null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PathLocationItem that = (PathLocationItem) o;
            return index == that.index &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, index);
        }
    }
}
