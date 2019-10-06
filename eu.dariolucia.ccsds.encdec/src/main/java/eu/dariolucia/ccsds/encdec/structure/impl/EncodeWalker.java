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
import eu.dariolucia.ccsds.encdec.structure.EncodingException;
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
public class EncodeWalker extends StructureWalker<byte[], EncodingException> {

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
    protected Object processValue(EncodedParameter ei) throws EncodingException {
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

    private Object encodeValue(EncodedParameter ei, IEncoderExtension extEnc) throws EncodingException {
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

    private Object encodeValue(EncodedParameter ei, DataTypeEnum dataType, int dataLength) throws EncodingException {
        Object value;
        Integer paddedWidth = ei.getPaddedWidth();
        long initialPosition = this.bitHandler.getCurrentBitIndex();

        // Now that you have the final type and length, you can invoke the resolver to get the value and encode it
        switch (dataType) {
            case BOOLEAN:
                boolean booleanValue = this.resolver.getBooleanValue(ei, this.currentLocation);
                this.bitHandler.setNextBoolean(booleanValue);
                value = booleanValue;
            break;
            case ENUMERATED:
                int enumerationValue = this.resolver.getEnumerationValue(ei, this.currentLocation);
                this.bitHandler.setNextIntegerSigned(enumerationValue, dataLength);
                value = enumerationValue;
            break;
            case UNSIGNED_INTEGER:
                long unsignedIntegerValue = this.resolver.getUnsignedIntegerValue(ei, this.currentLocation);
                this.bitHandler.setNextLongUnsigned(unsignedIntegerValue, dataLength);
                value = unsignedIntegerValue;
            break;
            case SIGNED_INTEGER:
                long signedIntegerValue = this.resolver.getSignedIntegerValue(ei, this.currentLocation);
                this.bitHandler.setNextLongSigned(signedIntegerValue, dataLength);
                value = signedIntegerValue;
            break;
            case REAL:
                double realValue = this.resolver.getRealValue(ei, this.currentLocation);
                switch (dataLength) {
                    case 1:
                        this.bitHandler.setNextFloat((float) realValue);
                        break;
                    case 2:
                        this.bitHandler.setNextDouble(realValue);
                        break;
                    case 3:
                        this.bitHandler.setNextMil32Real(realValue);
                        break;
                    case 4:
                        this.bitHandler.setNextMil48Real(realValue);
                        break;
                    default:
                        throw new EncodingException("Length code " + dataLength + " for encoded parameter " + ei.getId() + " for real values not recognized, cannot encode");
                }
                value = realValue;
            break;
            case BIT_STRING:
                BitString bs = this.resolver.getBitStringValue(ei, this.currentLocation, dataLength);
                if (dataLength != 0 && bs.getLength() != dataLength) {
                    throw new EncodingException("Resolved bitstring length value " + bs.getLength() + " and PFC code " + dataLength + " for bit string do not match for encoded parameter " + ei.getId() + ", cannot encode");
                }
                this.bitHandler.setNextByte(bs.getData(), bs.getLength());
                value = bs;
            break;
            case OCTET_STRING:
                byte[] os = this.resolver.getOctetStringValue(ei, this.currentLocation, dataLength);
                if (dataLength != 0 && os.length != dataLength) {
                    throw new EncodingException("Resolved octet string length value " + os.length + " and PFC code " + dataLength + " for octet string do not match for encoded parameter " + ei.getId() + ", cannot encode");
                }
                this.bitHandler.setNextByte(os, os.length * Byte.SIZE);
                value = os;
            break;
            case CHARACTER_STRING:
                String cs = this.resolver.getCharacterStringValue(ei, this.currentLocation, dataLength);
                if (dataLength != 0 && cs.length() != dataLength) {
                    throw new EncodingException("Resolved char string length value " + cs.length() + " and PFC code " + dataLength + " for char string do not match for encoded parameter " + ei.getId() + ", cannot encode");
                }
                this.bitHandler.setNextString(cs, cs.length() * Byte.SIZE);
                value = cs;
            break;
            case ABSOLUTE_TIME:
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
                    throw new EncodingException("PFC value " + dataLength + " for PTC of type Absolute Time is not valid for encoded parameter " + ei.getId() + ", cannot encode");
                }
                value = t;
            break;
            case RELATIVE_TIME:
                Duration duration = this.resolver.getRelativeTimeValue(ei, this.currentLocation);
                if (dataLength == 0) {
                    // Explicit definition of time format (CUC), i.e. including the Pfield
                    IEncodeResolver.RelativeTimeDescriptor desc = this.resolver.getRelativeTimeDescriptor(ei, this.currentLocation, duration);
                    byte[] encoded = TimeUtil.toCUCduration(duration, desc.coarseTime, desc.fineTime, true);
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else if (dataLength >= 1 && dataLength <= 16) {
                    int coarse = (int) Math.floor((dataLength + 3) / 4.0);
                    int fine = (dataLength + 3) % 4;
                    byte[] encoded = TimeUtil.toCUCduration(duration, coarse, fine, false);
                    this.bitHandler.setNextByte(encoded, encoded.length * Byte.SIZE);
                } else {
                    throw new EncodingException("PFC value " + dataLength + " for PTC of type Relative Time is not valid for encoded parameter " + ei.getId() + ", cannot encode");
                }
                value = duration;
            break;
            case DEDUCED:
                throw new EncodingException("Deduced type for encoded parameter " + ei.getId() + " at this stage is not allowed, cannot encode");
            default:
                throw new EncodingException("Type " + dataType + " not supported");
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
    protected EncodingException newException(String message) {
        return new EncodingException(message);
    }
}
