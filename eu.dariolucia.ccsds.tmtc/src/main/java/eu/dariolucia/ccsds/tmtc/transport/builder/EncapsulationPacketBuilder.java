/*
 *   Copyright (c) 2022 Dario Lucia (https://www.dariolucia.eu)
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

package eu.dariolucia.ccsds.tmtc.transport.builder;

import eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * This class allows to build a CCSDS encapsulation packet using a typical Builder pattern. Once a packet is built, the builder
 * can be re-used to create additional packets: the payload data shall be explicitly cleared, as this is not done upon
 * build().
 *
 * This class is not thread-safe.
 */
public class EncapsulationPacketBuilder {

    /**
     * This method creates an instance of this class, initialising only the header fields from the provided {@link eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket}.
     * The quality indicator is retrieved from the provided packet, the packet data field is not copied.
     *
     * @param initialiser the encapsulation packet initialiser.
     * @return the builder object
     */
    public static EncapsulationPacketBuilder create(EncapsulationPacket initialiser) {
        return create(initialiser, false, initialiser.isQualityIndicator());
    }

    /**
     * This method creates an instance of this class, initialising the header fields from the provided {@link eu.dariolucia.ccsds.tmtc.transport.pdu.EncapsulationPacket}.
     * The quality indicator is retrieved from the provided packet.
     *
     * @param initialiser the encapsulation packet initialiser
     * @param copyDataField true if the packet data field should be copied over (up to 2 GB), otherwise false
     * @param qualityIndicator true if the quality is good, false otherwise
     * @return the builder object
     */
    public static EncapsulationPacketBuilder create(EncapsulationPacket initialiser, boolean copyDataField, boolean qualityIndicator) {
        EncapsulationPacketBuilder spb = create(qualityIndicator)
            .setEncapsulationProtocolId(initialiser.getEncapsulationProtocolId())
            .setCcsdsDefinedField(initialiser.getCcsdsDefinedField())
            .setUserDefinedField(initialiser.getUserDefinedField())
            .setEncapsulationProtocolIdExtension(initialiser.getEncapsulationProtocolIdExtension());
        if(copyDataField) {
            spb.setData(initialiser.getPacket(), initialiser.getPrimaryHeaderLength(), (int) initialiser.getEncapsulatedDataFieldLength());
        }
        return spb;
    }

    /**
     * This method creates an instance of this class.
     *
     * @param qualityIndicator true if the quality is good, false otherwise
     * @return the builder object
     */
    public static EncapsulationPacketBuilder create(boolean qualityIndicator) {
        return new EncapsulationPacketBuilder(qualityIndicator);
    }

    /**
     * This method creates an instance of this class, with positive quality indicator.
     *
     * @return the builder object
     */
    public static EncapsulationPacketBuilder create() {
        return create(true);
    }

    private boolean qualityIndicator;

    private EncapsulationPacket.ProtocolIdType encapsulationProtocolId;

    private boolean encapsulationProtocolIdExtensionPresent = false;

    private byte encapsulationProtocolIdExtension = -1;

    private boolean userDefinedFieldPresent = false;

    private byte userDefinedField = -1;

    private boolean ccsdsDefinedFieldPresent = false;

    private byte[] ccsdsDefinedField = null;

    private int lengthOfLength = -1;

    private byte[] payloadUnit = null;

    private EncapsulationPacketBuilder(boolean qualityIndicator) {
        this.qualityIndicator = qualityIndicator;
    }

    /**
     * This method sets the quality indicator of the packet.
     *
     * @param qualityIndicator true if the quality is valid, otherwise false
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setQualityIndicator(boolean qualityIndicator) {
        this.qualityIndicator = qualityIndicator;
        return this;
    }

    /**
     * This method sets the encapsulation protocol ID. The value is masked with 0x07 (3 least significant bits).
     *
     * @param protocolId the protocol ID
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType protocolId) {
        this.encapsulationProtocolId = protocolId;
        return this;
    }

    /**
     * This method sets the encapsulation protocol ID extension. The value is masked with 0x0F (4 least significant bits).
     * If the value is -1, the field is marked as absent.
     *
     * @param encapsulationProtocolIdExtension the protocol ID extension or -1 to indicate its absence
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setEncapsulationProtocolIdExtension(int encapsulationProtocolIdExtension) {
        this.encapsulationProtocolIdExtension = (byte) (encapsulationProtocolIdExtension & 0x0F);
        this.encapsulationProtocolIdExtensionPresent = encapsulationProtocolIdExtension != -1;
        return this;
    }

    /**
     * This method sets the user defined field. The value is masked with 0x0F (4 least significant bits).
     * If the value is -1, the field is marked as absent.
     *
     * @param userDefinedField the user defined field or -1 to indicate its absence
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setUserDefinedField(int userDefinedField) {
        this.userDefinedField = (byte) (userDefinedField & 0x0F);
        this.userDefinedFieldPresent = userDefinedField != -1;
        return this;
    }

    /**
     * This method sets the CCSDS defined field. If the value is null, the field is marked as absent.
     *
     * @param ccsdsDefinedField the CCSDS defined field or null to indicate its absence
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setCcsdsDefinedField(byte[] ccsdsDefinedField) {
        if(ccsdsDefinedField != null && ccsdsDefinedField.length != 2) {
            throw new IllegalArgumentException("CCSDS Defined Field expected length: 2, got: " + ccsdsDefinedField.length);
        }
        if(ccsdsDefinedField == null) {
            this.ccsdsDefinedFieldPresent = false;
            this.ccsdsDefinedField = null;
        } else {
            this.ccsdsDefinedField = Arrays.copyOfRange(ccsdsDefinedField, 0, 2);
            this.ccsdsDefinedFieldPresent = true;
        }
        return this;
    }

    /**
     * This method is a shortcut for setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE)
     *
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setIdle() {
        return setEncapsulationProtocolId(EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE);
    }

    /**
     * This method sets the 'length of length' field of the encapsulation packet, according to 4.1.2.4.
     * If the value is -1, then the length to be used is calculated dynamically upon presence of the various fields
     * and length of the payload unit.
     *
     * @param length the length of length to use (0-3) or -1 for dynamic computation
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setLengthOfLength(int length) {
        if(length != -1 && length < 0 || length > 3) {
            throw new IllegalArgumentException("Length of Length between 0 and 3, or -1, got: " + length);
        }
        this.lengthOfLength = length;
        return this;
    }

    /**
     * Add the provided data to the packet data field. The data is copied to an intermediate buffer and further updates
     * to the original byte array are not reflected in the generated packet data field.
     *
     * If the array is null or the length is <= 0, the payload unit is cleared.
     *
     * @param b the byte array containing the data to put in the packet data field, can be null to reset the payload
     * @param offset the byte array start offset
     * @param length the number of bytes to put in the packet data field
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setData(byte[] b, int offset, int length) {
        // Compute if you can add the requested amount
        if(b != null && length > 0) {
            payloadUnit = Arrays.copyOfRange(b, offset, offset + length);
        } else {
            payloadUnit = null;
        }
        return this;
    }

    /**
     * Equivalent to setData(b, 0, b.length);
     *
     * @param b the byte array containing the data to put in the packet data field, can be null to reset the payload
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder setData(byte[] b) {
        return setData(b, 0, b == null ? 0 : b.length);
    }

    /**
     * Equivalent to setData(null);
     *
     * @return this {@link EncapsulationPacketBuilder} object
     */
    public EncapsulationPacketBuilder clearData() {
        return setData(null);
    }

    /**
     * This method builds an encapsulation packet depending on the builder state.
     *
     * @return the {@link EncapsulationPacket}
     */
    public EncapsulationPacket build() {
        int payloadDataLength = payloadUnit == null ? 0 : payloadUnit.length;
        int headerLength;
        if(this.lengthOfLength == -1) {
            // Compute length based on provided information
            headerLength = computeDynamicHeaderLength();
            if(headerLength == 1 && this.encapsulationProtocolId != EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE) {
                throw new IllegalStateException("Computed header length is 0, but Encapsulation Protocol ID field is "
                        + this.encapsulationProtocolId + ", expected " + EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE + ". This is a bug.");
            }
        } else {
            switch (this.lengthOfLength) {
                case 0:
                    headerLength = 1;
                    if(this.encapsulationProtocolId != EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE) {
                        throw new IllegalStateException("Length of Length value is 0, but Encapsulation Protocol ID field is "
                                + this.encapsulationProtocolId + ", expected " + EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE);
                    }
                    break;
                case 1:
                    headerLength = 2;
                    break;
                case 2:
                    headerLength = 4;
                    break;
                case 3:
                    headerLength = 8;
                    break;
                 default:
                    throw new IllegalStateException("Length of Length field not supported: " + this.lengthOfLength);
            }
        }

        int packetLength = headerLength + payloadDataLength;

        ByteBuffer bb = ByteBuffer.allocate(packetLength);

        if(headerLength == 1) {
            bb.put((byte)((this.encapsulationProtocolId.ordinal() << 2 | 0b011100000) & 0xFF));
        } else if(headerLength == 2) {
            bb.put((byte)((this.encapsulationProtocolId.ordinal() << 2 | 0b011100001) & 0xFF));
            bb.put((byte) (packetLength & 0xFF));
            if(payloadUnit != null) {
                if(payloadUnit.length > 253) {
                    throw new IllegalStateException("Header length is set to 2 bytes (1 octet for length - 2) but actual length of data field is " + payloadUnit.length);
                }
                bb.put(payloadUnit);
            }
        } else if(headerLength == 4) {
            bb.put((byte)((this.encapsulationProtocolId.ordinal() << 2 | 0b011100010) & 0xFF));
            int secondOctet = this.userDefinedFieldPresent ? this.userDefinedField & 0xFF : 0;
            secondOctet <<= 4;
            secondOctet |= this.encapsulationProtocolIdExtensionPresent ? this.encapsulationProtocolIdExtension & 0xFF : 0;
            bb.put((byte)(secondOctet & 0xFF));
            bb.putShort((short) (packetLength & 0xFFFF));
            if(payloadUnit != null) {
                if(payloadUnit.length > 65531) {
                    throw new IllegalStateException("Header length is set to 4 bytes (2 octet for length - 2) but actual length of data field is " + payloadUnit.length);
                }
                bb.put(payloadUnit);
            }
        } else if(headerLength == 8) {
            bb.put((byte)((this.encapsulationProtocolId.ordinal() << 2 | 0b011100011) & 0xFF));
            int secondOctet = this.userDefinedFieldPresent ? this.userDefinedField & 0xFF : 0;
            secondOctet <<= 4;
            secondOctet |= this.encapsulationProtocolIdExtensionPresent ? this.encapsulationProtocolIdExtension & 0xFF : 0;
            bb.put((byte)(secondOctet & 0xFF));
            if(this.ccsdsDefinedFieldPresent) {
                bb.put(this.ccsdsDefinedField);
            } else {
                bb.put(new byte[2]);
            }
            bb.putInt(packetLength);
            if(payloadUnit != null) {
                bb.put(payloadUnit);
            }
        }

        byte[] encodedPacket = bb.array();

        // Return the packet
        return new EncapsulationPacket(encodedPacket, qualityIndicator);
    }

    private int computeDynamicHeaderLength() {
        if(this.ccsdsDefinedFieldPresent) {
            // If this field is present, then the header can be only 8 bytes
            return 8;
        } else if(this.userDefinedFieldPresent || this.encapsulationProtocolIdExtensionPresent) {
            // If one of these fields is present, then the header is 4 or 8 bytes, depending on the size of the payload unit
            if(payloadUnit == null || payloadUnit.length <= 65535 - 4) {
                return 4;
            } else {
                return 8;
            }
        } else {
            // No CCSDS defined field, no user defined field and no encapsulation protocol ID extension
            // At this stage, then the header is 1, 2, 4 or 8 bytes, depending on the size of the payload unit
            if(payloadUnit == null && this.encapsulationProtocolId == EncapsulationPacket.ProtocolIdType.PROTOCOL_ID_IDLE) {
                return 1;
            } else if(payloadUnit == null || payloadUnit.length <= 255 - 2) {
                return 2;
            } else if(payloadUnit.length <= 65535 - 4) {
                return 4;
            } else {
                return 8;
            }
        }
    }
}
