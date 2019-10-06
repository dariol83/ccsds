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

package eu.dariolucia.ccsds.encdec.structure.impl;

import eu.dariolucia.ccsds.encdec.bit.BitEncoderDecoder;
import eu.dariolucia.ccsds.encdec.definition.*;
import eu.dariolucia.ccsds.encdec.extension.IDecoderExtension;
import eu.dariolucia.ccsds.encdec.extension.internal.ExtensionRegistry;
import eu.dariolucia.ccsds.encdec.structure.*;
import eu.dariolucia.ccsds.encdec.time.IGenerationTimeProcessor;
import eu.dariolucia.ccsds.encdec.value.BitString;
import eu.dariolucia.ccsds.encdec.value.TimeUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * The internal class used to decode packets.
 */
public class DecodeWalker extends StructureWalker<DecodingResult, DecodingException> {

    private final Map<PathLocation, DecodingResult.Item> location2item = new LinkedHashMap<>();
    private final List<DecodingResult.Item> decodedItems = new LinkedList<>();
    private final List<ParameterValue> decodedParameters = new LinkedList<>();

    private final Deque<DecodingResult.Item> stack = new LinkedList<>();
    private final Instant agencyEpoch;
    private final IGenerationTimeProcessor generationTimeProcessor;

    public DecodeWalker(Definition database, PacketDefinition definition, byte[] data, int offset, int length, Instant agencyEpoch, IGenerationTimeProcessor timeProcessor) {
        super(database, definition, () -> new BitEncoderDecoder(data, offset, length));
        this.agencyEpoch = agencyEpoch;
        this.generationTimeProcessor = timeProcessor;
    }

    @Override
    protected DecodingResult finalizeResult() throws DecodingException {
        // Iterate once over the parameter structure definition and add in the decodedItems list the specified items
        for (AbstractEncodedItem ei : definition.getStructure().getEncodedItems()) {
            PathLocation pl = PathLocation.of(definition.getId(), ei.getId());
            DecodingResult.Item item = location2item.get(pl);
            if(item == null) {
                throw new DecodingException(String.format("Expecting to find item at path %s", pl));
            }
            decodedItems.add(item);
        }
        return new DecodingResult(decodedItems, decodedParameters);
    }

    @Override
    protected Object processValue(EncodedParameter ei) throws DecodingException {
        AbstractEncodedType type = ei.getType();
        DataTypeEnum dataType;
        Object value;
        if (type instanceof ExtensionType) {
            ExtensionType et = (ExtensionType) type;
            String id = et.getExternal();
            IDecoderExtension extDec = ExtensionRegistry.extensionDecoder(id);
            dataType = null;
            value = decodeValue(ei, extDec);
        } else {
            FixedType effectiveType = deriveEffectiveType(ei);
            dataType = effectiveType.getType();
            value = decodeValue(ei, effectiveType.getType(), effectiveType.getLength());
        }
        // Compute generation time
        Instant genTime = null;
        if(this.generationTimeProcessor != null) {
            // Retrieve the required information for the time processor
            Instant absTime = null;
            Duration relDuration = null;
            Integer offsetMs = null;
            if(ei.getTime() != null) {
                offsetMs = ei.getTime().getOffset();
                if(ei.getTime().getAbsoluteTimeReference() != null && !ei.getTime().getAbsoluteTimeReference().isEmpty()) {
                    Object absTimeVal = encodedParameter2value.get(ei.getTime().getAbsoluteTimeReference());
                    if(absTimeVal instanceof Instant) {
                        absTime = (Instant) absTimeVal;
                    }
                }
                if(ei.getTime().getRelativeTimeReference() != null && !ei.getTime().getRelativeTimeReference().isEmpty()) {
                    Object relTimeVal = encodedParameter2value.get(ei.getTime().getRelativeTimeReference());
                    if(relTimeVal instanceof Duration) {
                        relDuration = (Duration) relTimeVal;
                    }
                }
            }
            genTime = this.generationTimeProcessor.computeGenerationTime(ei, value, absTime, relDuration, offsetMs);
        }
        //
        DecodingResult.Parameter parameter = new DecodingResult.Parameter(currentLocation, ei.getId(), ei, dataType , value, genTime);
        // Add to map
        location2item.put(currentLocation, parameter);
        attachToParent(parameter);
        // If a mapping exists, map the value now
        if(ei.getLinkedParameter() != null) {
            AbstractLinkedParameter linkedParameter = ei.getLinkedParameter();
            if(linkedParameter instanceof FixedLinkedParameter) {
                decodedParameters.add(new ParameterValue(((FixedLinkedParameter) linkedParameter).getParameter().getId(), value));
            } else if(linkedParameter instanceof ReferenceLinkedParameter) {
                // Look up for the reference encoded parameter
                String refItem = ((ReferenceLinkedParameter) linkedParameter).getReference();
                Object linkedParamValue = this.encodedParameter2value.get(refItem);
                if (linkedParamValue == null) {
                    throw new DecodingException(String.format("No encoded item %s used as reference for linked parameter for %s, null value", refItem, ei.getId()));
                }
                if (!(linkedParamValue instanceof Number)) {
                    throw new DecodingException(String.format("Encoded item %s value used as reference for linked parameter for %s, is not a number", refItem, ei.getId()));
                }
                // Look up the definition using the external ID
                ParameterDefinition pd = retrieveParameterDefinitionByExternalId(((Number)linkedParamValue).intValue());
                if(pd == null) {
                    throw new DecodingException(String.format("No parameter with ID %d found as specified by encoded item %s connected to encoded parameter %s as linked parameter", ((Number) linkedParamValue).intValue(), refItem, ei.getId()));
                } else {
                    decodedParameters.add(new ParameterValue(pd.getId(), value));
                }
            }

        }
        return value;
    }

    private Object decodeValue(EncodedParameter ei, IDecoderExtension extDec) throws DecodingException {
        return extDec.decode(super.definition, ei, currentLocation, bitHandler);
    }

    private Object decodeValue(EncodedParameter ei, DataTypeEnum dataType, int dataLength) throws DecodingException {
        Object value;
        Integer paddedWidth = ei.getPaddedWidth();
        long initialPosition = this.bitHandler.getCurrentBitIndex();
        // Now that you have the final PTC and PFC codes, you can invoke the correct operation on the bit handler
        switch (dataType) {
            case BOOLEAN:
                value = this.bitHandler.getNextBoolean();
            break;
            case ENUMERATED:
                value = this.bitHandler.getNextIntegerSigned(dataLength);
            break;
            case UNSIGNED_INTEGER:
                value = this.bitHandler.getNextLongUnsigned(dataLength);
            break;
            case SIGNED_INTEGER:
                value = this.bitHandler.getNextLongSigned(dataLength);
            break;
            case REAL:
                switch (dataLength) {
                    case 1:
                        value = (double) this.bitHandler.getNextFloat();
                        break;
                    case 2:
                        value = this.bitHandler.getNextDouble();
                        break;
                    case 3:
                        value = this.bitHandler.getNextMil32Real();
                        break;
                    case 4:
                        value = this.bitHandler.getNextMil48Real();
                        break;
                    default:
                        throw new DecodingException(String.format("Length code %d for encoded parameter %s for real values not recognized", dataLength, ei.getId()));
                }
            break;
            case BIT_STRING:
                byte[] val = this.bitHandler.getNextByte(dataLength);
                value = new BitString(val, dataLength);
            break;
            case OCTET_STRING:
                value = this.bitHandler.getNextByte(dataLength * Byte.SIZE);
            break;
            case CHARACTER_STRING:
                value = this.bitHandler.getNextString(dataLength * Byte.SIZE);
            break;
            case ABSOLUTE_TIME:
                Instant t;
                if (dataLength == 0) {
                    // Explicit definition of time format (CUC or CDS), i.e. including the Pfield
                    int currentBitIdx = this.bitHandler.getCurrentBitIndex();
                    byte firstPfield = (byte) Integer.toUnsignedLong(this.bitHandler.getNextIntegerUnsigned(Byte.SIZE));
                    this.bitHandler.setCurrentBitIndex(currentBitIdx);
                    if (TimeUtil.isCDS(firstPfield)) {
                        t = TimeUtil.fromCDS(this.bitHandler, this.agencyEpoch);
                    } else {
                        t = TimeUtil.fromCUC(this.bitHandler, this.agencyEpoch);
                    }
                } else if (dataLength == 1) {
                    byte[] tField = this.bitHandler.getNextByte(Byte.SIZE * 6);
                    t = TimeUtil.fromCDS(tField, this.agencyEpoch, true, 0);
                } else if (dataLength == 2) {
                    byte[] tField = this.bitHandler.getNextByte(Byte.SIZE * 8);
                    t = TimeUtil.fromCDS(tField, this.agencyEpoch, true, 1);
                } else if (dataLength >= 3 && dataLength <= 18) {
                    int coarse = (int) Math.floor((dataLength + 1) / 4.0);
                    int fine = (dataLength + 1) % 4;
                    byte[] tField = this.bitHandler.getNextByte(Byte.SIZE * (coarse + fine));
                    t = TimeUtil.fromCUC(tField, this.agencyEpoch, coarse, fine);
                } else {
                    throw new DecodingException(String.format("PFC value %d for PTC of type Absolute Time is not valid for encoded parameter %s", dataLength, ei.getId()));
                }
                value = t;
            break;
            case RELATIVE_TIME:
                Duration duration;
                if (dataLength == 0) {
                    // Explicit definition of time format (CUC), i.e. including the Pfield
                    duration = TimeUtil.fromCUCduration(this.bitHandler);
                } else if (dataLength >= 1 && dataLength <= 16) {
                    int coarse = (int) Math.floor((dataLength + 3) / 4.0);
                    int fine = (dataLength + 3) % 4;
                    byte[] tField = this.bitHandler.getNextByte(Byte.SIZE * (coarse + fine));
                    duration = TimeUtil.fromCUCduration(tField, coarse, fine);
                } else {
                    throw new DecodingException(String.format("PFC value %d for PTC of type Relative Time is not valid for encoded parameter %s", dataLength, ei.getId()));
                }
                value = duration;
            break;
            case DEDUCED:
                throw new DecodingException(String.format("Deduced type for encoded parameter %s at this stage is not allowed", ei.getId()));
            default:
                throw new DecodingException(String.format("Type %s not supported", dataType));
        }
        // Check padding
        if(paddedWidth != null) {
            long readBits = this.bitHandler.getCurrentBitIndex() - initialPosition;
            if(readBits < paddedWidth) {
                // Move the pointer
                this.bitHandler.addCurrentBitIndex((int) (paddedWidth - readBits));
            }
        }

        // All done, return the value
        return value;
    }

    @Override
    protected void structureStart(EncodedStructure es, PathLocation currentLocation) throws DecodingException {
        DecodingResult.Structure struct = new DecodingResult.Structure(currentLocation, currentLocation.last(), new LinkedList<>());
        stackPush(currentLocation, struct);
    }

    @Override
    protected void structureEnd(EncodedStructure es, PathLocation currentLocation) throws DecodingException {
        stackPop(currentLocation);
    }

    @Override
    protected void arrayStart(EncodedArray ea, PathLocation currentLocation) throws DecodingException {
        DecodingResult.Array arr = new DecodingResult.Array(currentLocation, currentLocation.last(), new LinkedList<>());
        stackPush(currentLocation, arr);
    }

    @Override
    protected void arrayEnd(EncodedArray ea, PathLocation append) throws DecodingException {
        stackPop(currentLocation);
    }

    @Override
    protected void arrayItemStart(EncodedArray ea, PathLocation currentLocation, int idx) throws DecodingException {
        DecodingResult.ArrayItem arrItem = new DecodingResult.ArrayItem(currentLocation, currentLocation.last(), new LinkedList<>());
        stackPush(currentLocation, arrItem);
    }

    @Override
    protected void arrayItemEnd(EncodedArray ea, PathLocation currentLocation, int idx) throws DecodingException {
        stackPop(currentLocation);
    }

    private void stackPush(PathLocation currentLocation, DecodingResult.Item item) throws DecodingException {
        this.location2item.put(currentLocation, item);
        attachToParent(item);
        stack.push(item);
    }

    private void stackPop(PathLocation currentLocation) throws DecodingException {
        if (stack.isEmpty()) {
            throw new DecodingException(String.format("Stack empty, not expected when processing end of location %s", currentLocation));
        }
        if (!stack.peek().location.equals(currentLocation)) {
            throw new DecodingException(String.format("Stack head points to %s but expecting %s", stack.peek().location, currentLocation));
        }
        stack.pop();
    }

    private void attachToParent(DecodingResult.Item toBeAttached) throws DecodingException {
        if (toBeAttached.location.length() <= 2) {
            // No need to attach, no parent at this stage
            return;
        }
        if (stack.isEmpty()) {
            throw new DecodingException(String.format("Stack empty, cannot attach item %s", toBeAttached.location));
        }
        DecodingResult.Item potentialParent = stack.peek();
        if(!potentialParent.location.equals(toBeAttached.location.parent())) {
            throw new DecodingException(String.format("Expected parent %s does not match actual parent %s", toBeAttached.location.parent(), potentialParent.location));
        }
        if(potentialParent instanceof DecodingResult.Structure) {
            ((DecodingResult.Structure) potentialParent).properties.add(toBeAttached);
        } else if(potentialParent instanceof DecodingResult.Array) {
            if(toBeAttached instanceof DecodingResult.ArrayItem) {
                ((DecodingResult.Array) potentialParent).arrayItems.add((DecodingResult.ArrayItem) toBeAttached);
            } else {
                throw new DecodingException(String.format("Provided item %s is not an array item", toBeAttached.location));
            }
        } else if(potentialParent instanceof DecodingResult.ArrayItem) {
            ((DecodingResult.ArrayItem) potentialParent).array.add(toBeAttached);
        } else {
            throw new DecodingException(String.format("Parent is not of supported type: %s", potentialParent.getClass().getName()));
        }
    }

    @Override
    protected DecodingException newException(String message) {
        return new DecodingException(message);
    }
}
