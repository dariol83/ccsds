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
import eu.dariolucia.ccsds.encdec.structure.IEncodeResolver;
import eu.dariolucia.ccsds.encdec.extension.IEncoderExtension;
import eu.dariolucia.ccsds.encdec.extension.internal.ExtensionRegistry;
import eu.dariolucia.ccsds.encdec.structure.StructureWalker;
import eu.dariolucia.ccsds.encdec.value.BitString;
import eu.dariolucia.ccsds.encdec.value.TimeUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

/**
 * The internal class used to encode packets.
 */
public class EncodeWalker extends StructureWalker<byte[]> {

    private final IEncodeResolver resolver;
    private final Instant agencyEpoch;

    public EncodeWalker(Definition database, PacketDefinition definition, int maxPacketSize, Instant agencyEpoch, IEncodeResolver resolver) {
        super(database, definition, () -> new BitEncoderDecoder(maxPacketSize));
        this.agencyEpoch = agencyEpoch;
        this.resolver = resolver;
    }

    @Override
    protected byte[] finalizeResult() {
        // Get the data up to the last written bit index
        byte[] allData = bitHandler.getData();
        int maxWrittenIdx = bitHandler.getMaxCurrentBitIndex();
        // Return the copy of the array that contains the encoded part
        return Arrays.copyOfRange(allData, 0, (maxWrittenIdx / 8 + (maxWrittenIdx % 8 == 0 ? 0 : 1)));
    }

    @Override
    protected Object processValue(EncodedParameter ei) {
        AbstractEncodedType type = ei.getType();
        if (type instanceof ExtensionType) {
            ExtensionType et = (ExtensionType) type;
            String id = et.getExternal();
            IEncoderExtension extEnc = ExtensionRegistry.extensionEncoder(id);
            return encodeValue(ei, extEnc);
        } else {
            FixedType effectiveType = deriveEffectiveType(ei);
            return encodeValue(ei, effectiveType.getType(), effectiveType.getLength());
        }
    }

    private Object encodeValue(EncodedParameter ei, IEncoderExtension extEnc) {
        Integer paddedWidth = ei.getPaddedWidth();
        long initialPosition = this.bitHandler.getCurrentBitIndex();

        Object value = this.resolver.getExtensionValue(ei, this.currentLocation);
        extEnc.encode(super.definition, ei, currentLocation, bitHandler, value);
        // Check padding
        if(paddedWidth != null) {
            long readBits = this.bitHandler.getCurrentBitIndex() - initialPosition;
            if(readBits < paddedWidth) {
                // Move the pointer
                this.bitHandler.addCurrentBitIndex((int) (paddedWidth - readBits));
            }
        }
        return value;
    }

    private Object encodeValue(EncodedParameter ei, DataTypeEnum dataType, int dataLength) {
        Object value;
        Integer paddedWidth = ei.getPaddedWidth();
        long initialPosition = this.bitHandler.getCurrentBitIndex();

        // Now that you have the final type and length, you can invoke the resolver to get the value and encode it
        switch (dataType) {
            case BOOLEAN: {
                boolean v = this.resolver.getBooleanValue(ei, this.currentLocation);
                this.bitHandler.setNextBoolean(v);
                value = v;
            }
            break;
            case ENUMERATED: {
                int v = this.resolver.getEnumerationValue(ei, this.currentLocation);
                this.bitHandler.setNextIntegerSigned(v, dataLength);
                value = v;
            }
            break;
            case UNSIGNED_INTEGER: {
                long v = this.resolver.getUnsignedIntegerValue(ei, this.currentLocation);
                this.bitHandler.setNextLongUnsigned(v, dataLength);
                value = v;
            }
            break;
            case SIGNED_INTEGER: {
                long v = this.resolver.getSignedIntegerValue(ei, this.currentLocation);
                this.bitHandler.setNextLongSigned(v, dataLength);
                value = v;
            }
            break;
            case REAL: {
                double v = this.resolver.getRealValue(ei, this.currentLocation);
                switch (dataLength) {
                    case 1:
                        this.bitHandler.setNextFloat((float) v);
                        break;
                    case 2:
                        this.bitHandler.setNextDouble(v);
                        break;
                    case 3:
                        this.bitHandler.setNextMil32Real(v);
                        break;
                    case 4:
                        this.bitHandler.setNextMil48Real(v);
                        break;
                    default:
                        throw new IllegalArgumentException("Length code " + dataLength + " for encoded parameter " + ei.getId() + " for real values not recognized, cannot encode");
                }
                value = v;
            }
            break;
            case BIT_STRING: {
                BitString bs = this.resolver.getBitStringValue(ei, this.currentLocation, dataLength);
                if (dataLength != 0 && bs.getLength() != dataLength) {
                    throw new IllegalStateException("Resolved bitstring length value " + bs.getLength() + " and PFC code " + dataLength + " for bit string do not match for encoded parameter " + ei.getId() + ", cannot encode");
                }
                this.bitHandler.setNextByte(bs.getData(), bs.getLength());
                value = bs;
            }
            break;
            case OCTET_STRING: {
                byte[] bs = this.resolver.getOctetStringValue(ei, this.currentLocation, dataLength);
                if (dataLength != 0 && bs.length != dataLength) {
                    throw new IllegalStateException("Resolved octet string length value " + bs.length + " and PFC code " + dataLength + " for octet string do not match for encoded parameter " + ei.getId() + ", cannot encode");
                }
                this.bitHandler.setNextByte(bs, bs.length * Byte.SIZE);
                value = bs;
            }
            break;
            case CHARACTER_STRING: {
                String bs = this.resolver.getCharacterStringValue(ei, this.currentLocation, dataLength);
                if (dataLength != 0 && bs.length() != dataLength) {
                    throw new IllegalStateException("Resolved char string length value " + bs.length() + " and PFC code " + dataLength + " for char string do not match for encoded parameter " + ei.getId() + ", cannot encode");
                }
                this.bitHandler.setNextString(bs, bs.length() * Byte.SIZE);
                value = bs;
            }
            break;
            case ABSOLUTE_TIME: {
                Instant t = this.resolver.getAbsoluteTimeValue(ei, this.currentLocation);
                if (dataLength == 0) {
                    // Explicit definition of time format (CUC or CDS), i.e. including the Pfield
                    IEncodeResolver.AbsoluteTimeDescriptor desc = this.resolver.getAbsoluteTimeDescriptor(ei, this.currentLocation, t);
                    byte[] encoded;
                    if(desc.cuc) {
                        encoded = TimeUtil.toCUC(t, this.agencyEpoch, desc.coarseTime, desc.fineTime, true);
                    } else {
                        encoded = TimeUtil.toCDS(t, this.agencyEpoch, desc.use16bits, desc.subMsPart, true);
                    }
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else if (dataLength == 1) {
                    byte[] encoded = TimeUtil.toCDS(t, this.agencyEpoch, true, 0, false);
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else if (dataLength == 2) {
                    byte[] encoded = TimeUtil.toCDS(t, this.agencyEpoch, true, 1, false);
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else if (dataLength >= 3 && dataLength <= 18) {
                    int coarse = (int) Math.floor((dataLength + 1) / 4.0);
                    int fine = (dataLength + 1) % 4;
                    byte[] encoded = TimeUtil.toCUC(t, this.agencyEpoch, coarse, fine, false);
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else {
                    throw new IllegalArgumentException("PFC value " + dataLength + " for PTC of type Absolute Time is not valid for encoded parameter " + ei.getId() + ", cannot encode");
                }
                value = t;
            }
            break;
            case RELATIVE_TIME: {
                Duration t = this.resolver.getRelativeTimeValue(ei, this.currentLocation);
                if (dataLength == 0) {
                    // Explicit definition of time format (CUC), i.e. including the Pfield
                    IEncodeResolver.RelativeTimeDescriptor desc = this.resolver.getRelativeTimeDescriptor(ei, this.currentLocation, t);
                    byte[] encoded = TimeUtil.toCUCduration(t, desc.coarseTime, desc.fineTime, true);
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else if (dataLength >= 1 && dataLength <= 16) {
                    int coarse = (int) Math.floor((dataLength + 3) / 4.0);
                    int fine = (dataLength + 3) % 4;
                    byte[] encoded = TimeUtil.toCUCduration(t, coarse, fine, false);
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else {
                    throw new IllegalArgumentException("PFC value " + dataLength + " for PTC of type Relative Time is not valid for encoded parameter " + ei.getId() + ", cannot encode");
                }
                value = t;
            }
            break;
            case DEDUCED:
                throw new RuntimeException("Deduced type for encoded parameter " + ei.getId() + " at this stage is not allowed, cannot encode");
            default:
                throw new IllegalArgumentException("Type " + dataType + " not supported");
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

}
