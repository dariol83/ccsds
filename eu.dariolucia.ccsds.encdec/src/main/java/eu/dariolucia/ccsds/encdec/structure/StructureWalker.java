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
public abstract class StructureWalker<T,K extends Exception> {

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

    public T walk() throws K {
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
                throw newException(String.format("Structural type %s not supported", ei.getClass().getSimpleName()));
            }
        }
        packetStructureEnd(definition.getStructure(), currentLocation);
        return finalizeResult();
    }

    protected void packetStructureEnd(PacketStructure structure, PathLocation currentLocation) throws K {
    }

    protected void packetStructureStart(PacketStructure structure, PathLocation currentLocation) throws K {
    }

    protected abstract T finalizeResult() throws K;

    protected abstract K newException(String message);

    protected void visitStructure(EncodedStructure es) throws K {
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
                throw newException(String.format("Inner structural type %s not supported", ei.getClass().getSimpleName()));
            }
        }
        this.encodedParameter2endPosition.put(es.getId(), this.bitHandler.getCurrentBitIndex());
        structureEnd(es, currentLocation);
        this.currentLocation = this.currentLocation.parent();
    }

    protected void structureEnd(EncodedStructure es, PathLocation currentLocation) throws K {
    }

    protected void structureStart(EncodedStructure es, PathLocation currentLocation) throws K {
    }

    protected void visitArray(EncodedArray ea) throws K {
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
                    throw newException(String.format("Array inner type %s not supported", ei.getClass().getSimpleName()));
                }
                arrayItemEnd(ea, currentLocation, idx);
                this.currentLocation = this.currentLocation.parent();
            }
        }
        this.encodedParameter2endPosition.put(ea.getId(), this.bitHandler.getCurrentBitIndex());
        arrayEnd(ea, currentLocation);
        this.currentLocation = this.currentLocation.parent();
    }

    protected void arrayEnd(EncodedArray ea, PathLocation append) throws K {
    }

    protected void arrayItemEnd(EncodedArray ea, PathLocation currentLocation, int idx) throws K {
    }

    protected void arrayItemStart(EncodedArray ea, PathLocation currentLocation, int idx) throws K {
    }

    protected void arrayStart(EncodedArray ea, PathLocation append) throws K {
    }

    protected int deriveArrayNumElements(EncodedArray ea) throws K {
        AbstractArraySize size = ea.getSize();
        if(size instanceof FixedArraySize) {
            return ((FixedArraySize) size).getLength();
        } else if (size instanceof ReferenceArraySize) {
            String param = ((ReferenceArraySize) size).getReference();
            Object value = this.encodedParameter2value.get(param);
            if(value instanceof Number) {
                return ((Number) value).intValue();
            } else {
                throw newException(String.format("Cannot map value of encoded parameter %s to an integer for array size of %s", param, ea.getId()));
            }
        } else {
            throw newException(String.format("No array size type recognized for %s: %s", ea.getId(), size.getClass().getSimpleName()));
        }
    }

    protected void moveToLocation(AbstractEncodedLocation location) throws K {
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
                throw newException(String.format("No encoded item %s used as reference for location", eirl.getReference()));
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
            throw newException(String.format("Location class of type %s not supported", location.getClass().getSimpleName()));
        }
    }

    protected void visitParameter(EncodedParameter ei) throws K {
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

    protected void parameterEnd(EncodedParameter ei, PathLocation currentLocation) throws K {
    }

    protected void parameterStart(EncodedParameter ei, PathLocation currentLocation) throws K {
    }

    protected abstract Object processValue(EncodedParameter ei) throws K;

    protected ParameterDefinition retrieveParameterDefinitionByExternalId(int externalId) throws K {
        // Naive implementation
        for (ParameterDefinition pd : this.database.getParameters()) {
            if (pd.getExternalId() != ParameterDefinition.EXTERNAL_ID_NOT_SET && pd.getExternalId() == externalId) {
                return pd;
            }
        }
        throw newException(String.format("Cannot map externalId %d to parameter definition", externalId));
    }

    protected FixedType deriveEffectiveType(EncodedParameter ei) throws K {
        AbstractEncodedType type = ei.getType();
        AbstractEncodedLength length = ei.getLength();
        if (type instanceof ExtensionType) {
            throw newException(String.format("Extension type for parameter %s cannot be effectively derived", ei.getId()));
        }
        DataTypeEnum dataTypeEnum;
        int dataLength;
        if (type instanceof FixedType) {
            dataTypeEnum = ((FixedType) type).getType();
            // If there is a length, the provided length is overwritten
            if (length != null) {
                dataLength = deriveLength(ei, currentLocation, dataTypeEnum, length);
            } else {
                dataLength = ((FixedType) type).getLength();
            }
        } else if (type instanceof ReferenceType) {
            String refItem = ((ReferenceType) type).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw newException(String.format("No encoded item %s used as reference for type of %s", refItem, ei.getId()));
            }
            if (!(value instanceof Number)) {
                throw newException(String.format("Encoded item %s used as reference for type of %s is not a number", refItem, ei.getId()));
            }
            dataTypeEnum = DataTypeEnum.fromCode(((Number) value).intValue());
            if (length != null) {
                dataLength = deriveLength(ei, currentLocation, dataTypeEnum, length);
            } else {
                throw newException(String.format("Encoded item %s use reference for type but there is no indication for length", ei.getId()));
            }
        } else if (type instanceof ParameterType) {
            String refItem = ((ParameterType) type).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw newException(String.format("No encoded item %s used as parameter reference for type of %s", refItem, ei.getId()));
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
                        throw newException(String.format("No length specified for encoded parameter %s even if type references a parameter that cannot be found via external ID", ei.getId()));
                    }
                } else {
                    dataTypeEnum = pd.getType().getType();
                    // If there is a length, the PFC is overwritten
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
                    throw newException(String.format("No length specified for encoded parameter %s even if type references a parameter, whose referenced value is not an external ID", ei.getId()));
                }
            }
        } else {
            throw newException(String.format("Type class of type %s not supported", type.getClass().getSimpleName()));
        }
        return new FixedType(dataTypeEnum, dataLength);
    }

    protected int deriveLength(EncodedParameter ei, PathLocation location, DataTypeEnum dataType, AbstractEncodedLength length) throws K {
        if (length instanceof FixedLength) {
            return ((FixedLength) length).getLength();
        } else if (length instanceof ReferenceLength) {
            String refItem = ((ReferenceLength) length).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw newException(String.format("No encoded item %s used as reference for length, null value", refItem));
            }
            if (!(value instanceof Number)) {
                throw newException(String.format("Encoded item %s value used as reference for length is not a number", refItem));
            }
            return ((Number) value).intValue();
        } else if (length instanceof ParameterLength) {
            String refItem = ((ParameterLength) length).getReference();
            Object value = this.encodedParameter2value.get(refItem);
            if (value == null) {
                throw newException(String.format("No encoded item %s used as parameter reference for length, null value", refItem));
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
            throw newException(String.format("Length class of type %s not supported", length.getClass().getSimpleName()));
        }
    }
}
