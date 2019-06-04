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

public final class PathLocation {

    public static final char PATH_COMPONENT_SEPARATOR = '.';
    public static final char ARRAY_INDEX_SEPARATOR = '#';

    public static PathLocation of(String... items) {
        return new PathLocation(items);
    }

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

    public String first() {
        return at(0);
    }

    public String last() {
        return at(this.pathComponents.size() - 1);
    }

    public int length() {
        return this.pathComponents.size();
    }

    public String at(int idx) {
        PathLocationItem item = this.pathComponents.get(idx);
        if(item.name != null) {
            return item.name;
        } else {
            return this.pathComponents.get(idx - 1).name + ARRAY_INDEX_SEPARATOR + item.index;
        }
    }

    public PathLocation appendIndex(int idx) {
        // Check if it can be appended
        if(pathComponents.isEmpty() || pathComponents.get(pathComponents.size() - 1).name == null) {
            throw new IllegalStateException("Cannot add index " + idx + " to path " + toString());
        }
        List<PathLocationItem> copy = new LinkedList<>(pathComponents);
        copy.add(new PathLocationItem(idx));
        return new PathLocation(copy);
    }

    public PathLocation append(String pathComponent) {
        List<PathLocationItem> copy = new LinkedList<>(pathComponents);
        copy.add(new PathLocationItem(pathComponent));
        return new PathLocation(copy);
    }

    public PathLocation parent() {
        return new PathLocation(pathComponents.subList(0, pathComponents.size() - 1));
    }

    public boolean isParentOf(PathLocation potentialChild) {
        return potentialChild.toString().startsWith(this.toString());
    }

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
