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

import eu.dariolucia.ccsds.encdec.definition.DataTypeEnum;
import eu.dariolucia.ccsds.encdec.definition.EncodedParameter;
import eu.dariolucia.ccsds.encdec.definition.ParameterDefinition;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An object of this class contains the result of a packet decoding operation. All the decoded {@link DecodingResult.Item}
 * can be retrieved, as well as the {@link ParameterValue} linked to associated {@link ParameterDefinition}.
 *
 * The class specifies a {@link IVisitor} interface that can be implemented and used to visit the result.
 */
public class DecodingResult {

    private final List<Item> decodedItems;
    private final List<ParameterValue> decodedParameters;

    /**
     * Construct an object with the provided list of decoded {@link Item} and list of {@link ParameterValue}.
     *
     * @param decodedItems the list of decoded items
     * @param decodedParameters the list of parameter values
     */
    public DecodingResult(List<Item> decodedItems, List<ParameterValue> decodedParameters) {
        this.decodedItems = List.copyOf(decodedItems);
        this.decodedParameters = List.copyOf(decodedParameters);
    }

    /**
     * This method returns the list of the decoded items.
     *
     * @return the list of decoded items
     */
    public List<Item> getDecodedItems() {
        return decodedItems;
    }

    /**
     * This method returns the list of decoded parameters.
     *
     * @return the list of decoded parameters
     */
    public List<ParameterValue> getDecodedParameters() {
        return decodedParameters;
    }

    /**
     * This method returns all the decoded items (parameters) as flat map. The key is the decoded item location, the value is the
     * value of the decoded parameter.
     *
     * @return the flat map of decoded parameters
     */
    public Map<String, Object> getDecodedItemsAsMap() {
        final Map<String, Object> map = new LinkedHashMap<>();
        visit(new IVisitor() {
            @Override
            public void visitParameter(Parameter p) {
                map.put(p.location.toString(), p.value);
            }
        });
        return map;
    }

    /**
     * This method allows to visit the hierarchical structure of the decoded result.
     *
     * @param v the visitor object
     */
    public void visit(IVisitor v) {
        for(Item i : decodedItems) {
            i.visit(v);
        }
    }

    /**
     * This interface allows to visit the hierarchical structure of the decoded result.
     */
    public interface IVisitor {
        default void visitParameter(Parameter p) {}
        default void visitArrayStart(Array a) {}
        default void visitArrayItemStart(Array a, int idx) {}
        default void visitArrayItemEnd(Array a, int idx) {}
        default void visitArrayEnd(Array a) {}
        default void visitStructureStart(Structure a) {}
        default void visitStructureItemStart(Structure a, int idx) {}
        default void visitStructureItemEnd(Structure a, int idx) {}
        default void visitStructureEnd(Structure a) {}
    }

    public abstract static class Item {
        public final PathLocation location;
        public final String name;

        public Item(PathLocation location, String name) {
            this.location = location;
            this.name = name;
        }

        public abstract void visit(IVisitor v);
    }

    public static class Parameter extends Item {
        public final EncodedParameter parameterItem;
        public final DataTypeEnum actualType;
        public final Object value;
        public final Instant generationTime;

        public Parameter(PathLocation location, String name, EncodedParameter parameterItem, DataTypeEnum actualType, Object value) {
            this(location, name, parameterItem, actualType, value, null);
        }

        public Parameter(PathLocation location, String name, EncodedParameter parameterItem, DataTypeEnum actualType, Object value, Instant generationTime) {
            super(location, name);
            this.parameterItem = parameterItem;
            this.actualType = actualType;
            this.value = value;
            this.generationTime = generationTime;
        }

        @Override
        public void visit(IVisitor v) {
            v.visitParameter(this);
        }
    }

    public static class Array extends Item {
        public final List<ArrayItem> arrayItems;

        public Array(PathLocation location, String name, List<ArrayItem> arrayItems) {
            super(location, name);
            this.arrayItems = arrayItems;
        }

        @Override
        public void visit(IVisitor v) {
            v.visitArrayStart(this);
            for(int i = 0; i < arrayItems.size(); ++i) {
                v.visitArrayItemStart(this, i);
                arrayItems.get(i).visit(v);
                v.visitArrayItemEnd(this, i);
            }
            v.visitArrayEnd(this);
        }
    }

    public static class ArrayItem extends Item {
        public final List<Item> array;

        public ArrayItem(PathLocation location, String name, List<Item> array) {
            super(location, name);
            this.array = array;
        }

        @Override
        public void visit(IVisitor v) {
            for (Item item : array) {
                item.visit(v);
            }
        }
    }

    public static class Structure extends Item {
        public final List<Item> properties;

        public Structure(PathLocation location, String name, List<Item> properties) {
            super(location, name);
            this.properties = properties;
        }

        @Override
        public void visit(IVisitor v) {
            v.visitStructureStart(this);
            for(int i = 0; i < properties.size(); ++i) {
                v.visitStructureItemStart(this, i);
                properties.get(i).visit(v);
                v.visitStructureItemEnd(this, i);
            }
            v.visitStructureEnd(this);
        }
    }
}
