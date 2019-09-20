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

import eu.dariolucia.ccsds.encdec.bit.BitEncoderDecoder;
import eu.dariolucia.ccsds.encdec.definition.*;
import eu.dariolucia.ccsds.encdec.extension.ILengthMapper;
import eu.dariolucia.ccsds.encdec.extension.ITypeMapper;
import eu.dariolucia.ccsds.encdec.extension.internal.ExtensionRegistry;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * This abstract class is a walker for packet definitions. It is used by subclasses to encode/decode packets according to
 * the specified definition.
 *
 * This class can be subclassed to perform other operations on a packet definition: subclasses will get invocation of the
 * various XXXstart and XXXend methods when appropriate.
 *
 * The walk() method can return an object, whose type is specified as type parameter.
 *
 * @param <T> the result type of the walk operation
 */
public abstract class StructureWalker<T> {

    protected final PacketDefinition definition;

    protected final Map<String, Object> encodedParameter2value = new TreeMap<>();
    protected final Map<String, Integer> encodedParameter2endPosition = new TreeMap<>();
    protected final Definition database;

    protected BitEncoderDecoder bitHandler;
    protected PathLocation currentLocation;

    public StructureWalker(Definition database, PacketDefinition definition, Supplier<BitEncoderDecoder> bitHandlerSupplier) {
        this.database = database;
        this.definition = definition;
        this.bitHandler = bitHandlerSupplier.get();
    }

    public T walk() {
        // Prepare the current location from the packet definition
        currentLocation = PathLocation.of(definition.getId());
        // Visit the definition starting from the top
        packetStructureStart(definition.getStructure(), currentLocation);
        for (AbstractEncodedItem ei : definition.getStructure().getEncodedItems()) {
            if (ei instanceof EncodedParameter) {
                visitParameter((EncodedParameter) ei);
            } else if (ei instanceof EncodedArray) {
                visitArray((EncodedArray) ei);
            } else if (ei instanceof EncodedStructure) {
                visitStructure((EncodedStructure) ei);
            } else {
                throw new RuntimeException("Type " + ei.getClass().getSimpleName() + " not supported");
            }
        }
        packetStructureEnd(definition.getStructure(), currentLocation);
        return finalizeResult();
    }

    protected void packetStructureEnd(PacketStructure structure, PathLocation currentLocation) {
    }

    protected void packetStructureStart(PacketStructure structure, PathLocation currentLocation) {
    }

    protected abstract T finalizeResult();

    protected void visitStructure(EncodedStructure es) {
        this.currentLocation = this.currentLocation.append(es.getId());
        structureStart(es, currentLocation);
        // Move to bit location
        moveToLocation(es.getLocation());
        for (AbstractEncodedItem ei : es.getEncodedItems()) {
            if (ei instanceof EncodedParameter) {
                visitParameter((EncodedParameter) ei);
            } else if (ei instanceof EncodedArray) {
                visitArray((EncodedArray) ei);
            } else if (ei instanceof EncodedStructure) {
                visitStructure((EncodedStructure) ei);
            } else {
                throw new RuntimeException("Type " + ei.getClass().getSimpleName() + " not supported");
            }
        }
        this.encodedParameter2endPosition.put(es.getId(), this.bitHandler.getCurrentBitIndex());
        structureEnd(es, currentLocation);
        this.currentLocation = this.currentLocation.parent();
    }

    protected void structureEnd(EncodedStructure es, PathLocation currentLocation) {
    }

    protected void structureStart(EncodedStructure es, PathLocation currentLocation) {
    }

    protected void visitArray(EncodedArray ea) {
        this.currentLocation = this.currentLocation.append(ea.getId());
        arrayStart(ea, currentLocation);
        // Move to bit location
        moveToLocation(ea.getLocation());
        // iterate according to the abstract array size
        int numElements = deriveArrayNumElements(ea);
        for (int idx = 0; idx < numElements; ++idx) {
            for (AbstractEncodedItem ei : ea.getEncodedItems()) {
                this.currentLocation = this.currentLocation.appendIndex(idx);
                arrayItemStart(ea, currentLocation, idx);
                if (ei instanceof EncodedParameter) {
                    visitParameter((EncodedParameter) ei);
                } else if (ei instanceof EncodedArray) {
                    visitArray((EncodedArray) ei);
                } else if (ei instanceof EncodedStructure) {
                    visitStructure((EncodedStructure) ei);
                } else {
                    throw new RuntimeException("Type " + ei.getClass().getSimpleName() + " not supported");
                }
                arrayItemEnd(ea, currentLocation, idx);
                this.currentLocation = this.currentLocation.parent();
            }
        }
        this.encodedParameter2endPosition.put(ea.getId(), this.bitHandler.getCurrentBitIndex());
        arrayEnd(ea, currentLocation);
        this.currentLocation = this.currentLocation.parent();
    }

    protected void arrayEnd(EncodedArray ea, PathLocation append) {
    }

    protected void arrayItemEnd(EncodedArray ea, PathLocation currentLocation, int idx) {
    }

    protected void arrayItemStart(EncodedArray ea, PathLocation currentLocation, int idx) {
    }

    protected void arrayStart(EncodedArray ea, PathLocation append) {
    }

    protected int deriveArrayNumElements(EncodedArray ea) {
        AbstractArraySize size = ea.getSize();
        if(size instanceof FixedArraySize) {
            return ((FixedArraySize) size).getLength();
        } else if (size instanceof ReferenceArraySize) {
            String param = ((ReferenceArraySize) size).getReference();
            Object value = this.encodedParameter2value.get(param);
            if(value instanceof Number) {
                return ((Number) value).intValue();
            } else {
                throw new IllegalArgumentException("Cannot map value of encoded parameter " + param + " to an integer for array size of " + ea.getId());
            }
        } else {
            throw new RuntimeException("No array size type recognized for " + ea.getId() + ": " + size.getClass().getSimpleName());
        }
    }

    protected void moveToLocation(AbstractEncodedLocation location) {
        // No location? Then do not change anything
        if (location == null) {
            return;
        }

        if (location instanceof FixedAbsoluteLocation) {
            this.bitHandler.setCurrentBitIndex(((FixedAbsoluteLocation) location).getAbsoluteLocation());
        } else if (location instanceof EncodedItemRelativeLocation) {
            EncodedItemRelativeLocation eirl = (EncodedItemRelativeLocation) location;
            Integer bitIndex = this.encodedParameter2endPosition.get(eirl.getReference());
            if (bitIndex == null) {
                throw new RuntimeException("No encoded item " + eirl.getReference() + " used as reference for location");
            } else {
                bitIndex += eirl.getBitOffset();
                if (eirl.getBitAlignment() > 1) {
                    int modRes = bitIndex % eirl.getBitAlignment();
                    if (modRes != 0) {
                        bitIndex += (eirl.getBitAlignment() - modRes);
                    }
                }
                this.bitHandler.setCurrentBitIndex(bitIndex);
            }
        } else if (location instanceof LastRelativeLocation) {
            LastRelativeLocation lrl = (LastRelativeLocation) location;
            int currBitIndex = this.bitHandler.getCurrentBitIndex();
            currBitIndex += lrl.getBitOffset();
            if (lrl.getBitAlignment() > 1) {
                int modRes = currBitIndex % lrl.getBitAlignment();
                if (modRes != 0) {
                    currBitIndex += (lrl.getBitAlignment() - modRes);
                }
            }
            this.bitHandler.setCurrentBitIndex(currBitIndex);
        } else {
            throw new IllegalArgumentException("Location class of type " + location.getClass().getSimpleName() + " not supported");
        }
    }

    protected void visitParameter(EncodedParameter ei) {
        // Set location
        this.currentLocation = this.currentLocation.append(ei.getId());
        // Hook start
        parameterStart(ei, currentLocation);
        // Move to bit location
        moveToLocation(ei.getLocation());
        // Retrieve value (depends on type) and encode (depends on PTC and PFC)
        Object value = processValue(ei);
        // Store value in map
        this.encodedParameter2value.put(ei.getId(), value);
        this.encodedParameter2endPosition.put(ei.getId(), this.bitHandler.getCurrentBitIndex());
        parameterEnd(ei, currentLocation);
        // Reset location
        this.currentLocation = this.currentLocation.parent();
    }

    protected void parameterEnd(EncodedParameter ei, PathLocation currentLocation) {
    }

    protected void parameterStart(EncodedParameter ei, PathLocation currentLocation) {
    }

    protected abstract Object processValue(EncodedParameter ei);

    protected ParameterDefinition retrieveParameterDefinitionByExternalId(int externalId) {
        // Naive implementation
        for (ParameterDefinition pd : this.database.getParameters()) {
            if (pd.getExternalId() != ParameterDefinition.EXTERNAL_ID_NOT_SET && pd.getExternalId() == externalId) {
                return pd;
            }
        }
        throw new RuntimeException("Cannot map externalId " + externalId + " to parameter definition");
    }

    protected FixedType deriveEffectiveType(EncodedParameter ei) {
        AbstractEncodedType type = ei.getType();
        AbstractEncodedLength length = ei.getLength();
        if (type instanceof ExtensionType) {
            throw new IllegalArgumentException("Extension type for parameter " + ei.getId() + " cannot be effectively derived");
        }
        DataTypeEnum dataTypeEnum;
        int dataLength;
        if (type instanceof FixedType) {
            dataTypeEnum = ((FixedType) type).getType();
            // If there is a length, the provided length is overwritten;
            if (length != null) {
                dataLength = deriveLength(ei, currentLocation, dataTypeEnum, length);
            } else {
                dataLength = ((FixedType) type).getLength();
            }
        } else if (type instanceof ReferenceType) {
            String refItem = ((ReferenceType) type).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw new RuntimeException("No encoded item " + refItem + " used as reference for type of " + ei.getId() + ", cannot encode");
            }
            if (!(value instanceof Number)) {
                throw new RuntimeException("Encoded item " + refItem + " used as reference for type of " + ei.getId() + " is not a number, cannot encode");
            }
            dataTypeEnum = DataTypeEnum.fromCode(((Number) value).intValue());
            if (length != null) {
                dataLength = deriveLength(ei, currentLocation, dataTypeEnum, length);
            } else {
                throw new RuntimeException("Encoded item " + ei.getId() + " use reference for type but there is no indication for length, cannot encode");
            }
        } else if (type instanceof ParameterType) {
            String refItem = ((ParameterType) type).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw new RuntimeException("No encoded item " + refItem + " used as parameter reference for type of " + ei.getId() + ", cannot encode");
            }
            if (value instanceof Number) {
                int externalId = ((Number) value).intValue();
                ParameterDefinition pd = retrieveParameterDefinitionByExternalId(externalId);
                if (pd == null) {
                    ITypeMapper tm = ExtensionRegistry.typeMapper();
                    dataTypeEnum = tm.mapType(ei, currentLocation, value);
                    if (length != null) {
                        dataLength = deriveLength(ei, currentLocation, dataTypeEnum, length);
                    } else {
                        throw new RuntimeException("No length specified for encoded parameter " + ei.getId() + " even if type references a parameter that cannot be found via external ID, cannot encode");
                    }
                } else {
                    dataTypeEnum = pd.getType().getType();
                    // If there is a length, the PFC is overwritten;
                    if (length != null) {
                        dataLength = deriveLength(ei, currentLocation, dataTypeEnum, length);
                    } else {
                        dataLength = pd.getType().getLength();
                    }
                }
            } else {
                ITypeMapper tm = ExtensionRegistry.typeMapper();
                dataTypeEnum = tm.mapType(ei, currentLocation, value);
                if (length != null) {
                    dataLength = deriveLength(ei, currentLocation, dataTypeEnum, length);
                } else {
                    throw new RuntimeException("No length specified for encoded parameter " + ei.getId() + " even if type references a parameter, whose referenced value is not an external ID, cannot encode");
                }
            }
        } else {
            throw new IllegalArgumentException("Type class of type " + type.getClass().getSimpleName() + " not supported");
        }
        return new FixedType(dataTypeEnum, dataLength);
    }

    protected int deriveLength(EncodedParameter ei, PathLocation location, DataTypeEnum dataType, AbstractEncodedLength length) {
        if (length instanceof FixedLength) {
            return ((FixedLength) length).getLength();
        } else if (length instanceof ReferenceLength) {
            String refItem = ((ReferenceLength) length).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw new RuntimeException("No encoded item " + refItem + " used as reference for length");
            }
            if (!(value instanceof Number)) {
                throw new RuntimeException("Encoded item " + refItem + " used as reference for length is not a number");
            }
            return ((Number) value).intValue();
        } else if (length instanceof ParameterLength) {
            String refItem = ((ParameterLength) length).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw new RuntimeException("No encoded item " + refItem + " used as parameter reference for length");
            }
            if (value instanceof Number) {
                int externalId = ((Number) value).intValue();
                ParameterDefinition pd = retrieveParameterDefinitionByExternalId(externalId);
                if (pd == null) {
                    ILengthMapper lm = ExtensionRegistry.lengthMapper();
                    return lm.mapLength(ei, location, dataType, value);
                } else {
                    return pd.getType().getLength();
                }
            } else {
                ILengthMapper lm = ExtensionRegistry.lengthMapper();
                return lm.mapLength(ei, location, dataType, value);
            }
        } else {
            throw new IllegalArgumentException("Length class of type " + length.getClass().getSimpleName() + " not supported");
        }
    }
}
