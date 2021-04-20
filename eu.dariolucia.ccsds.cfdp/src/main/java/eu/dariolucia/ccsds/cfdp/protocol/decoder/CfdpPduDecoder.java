package eu.dariolucia.ccsds.cfdp.protocol.decoder;

import eu.dariolucia.ccsds.cfdp.protocol.pdu.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * This class has utility methods to decode a {@link eu.dariolucia.ccsds.cfdp.protocol.pdu.CfdpPdu} from a byte array or
 * from a {@link java.io.ByteArrayInputStream}.
 */
public class CfdpPduDecoder {

    public static final int HEADER_SIZE = 4;

    private CfdpPduDecoder() {
        // Private constructor
    }

    public static CfdpPdu decode(byte[] data) {
        return decode(data, 0, data.length);
    }

    public static CfdpPdu decode(InputStream bos) throws IOException {
        // Read the first 4 bytes
        byte[] buffer = new byte[HEADER_SIZE];
        readNBytes(bos, buffer, 0, HEADER_SIZE);
        // Read and decode the first 4 bytes
        CfdpPdu.PduType type = ((buffer[0] & 0x10) >>> 4) == 0 ? CfdpPdu.PduType.FILE_DIRECTIVE : CfdpPdu.PduType.FILE_DATA;
        int dataFieldLength = Short.toUnsignedInt(ByteBuffer.wrap(buffer, 1, 2).getShort());
        int entityIdLength = ((buffer[3] & 0x70) >>> 4) + 1;
        int transactionSequenceNumberLength = (buffer[3] & 0x07) + 1;
        // So the full CFDP PDU is: HEADER_SIZE + transactionSequenceNumberLength + 2x entityIdLength + dataFieldLength
        int totalLength = HEADER_SIZE + transactionSequenceNumberLength + entityIdLength*2 + dataFieldLength;
        byte[] pdu = new byte[totalLength];
        System.arraycopy(buffer, 0, pdu, 0, HEADER_SIZE);
        readNBytes(bos, pdu, HEADER_SIZE, totalLength - HEADER_SIZE);
        return buildPdu(type, entityIdLength, transactionSequenceNumberLength, pdu);
    }

    private static void readNBytes(InputStream bos, byte[] buffer, int offset, int numBytes) throws IOException {
        int read = 0;
        while(read < numBytes) {
            int currentRead = bos.read(buffer, offset + read, numBytes - read);
            if(currentRead <= 0) {
                throw new IOException("Cannot read from stream: end of data reached");
            } else {
                read += currentRead;
            }
        }
    }

    public static CfdpPdu decode(byte[] data, int offset, int length) {
        if(length < 4) {
            throw new IllegalArgumentException("Cannot decode a CFDP PDU: CFDP header is 4 bytes but specified length is " + length);
        }
        // Read and decode the first 4 bytes
        CfdpPdu.PduType type = ((data[offset] & 0x10) >>> 4) == 0 ? CfdpPdu.PduType.FILE_DIRECTIVE : CfdpPdu.PduType.FILE_DATA;
        int dataFieldLength = Short.toUnsignedInt(ByteBuffer.wrap(data, offset + 1, 2).getShort());
        int entityIdLength = ((data[offset + 3] & 0x70) >>> 4) + 1;
        int transactionSequenceNumberLength = (data[offset + 3] & 0x07) + 1;
        // So the full CFDP PDU is: HEADER_SIZE + transactionSequenceNumberLength + 2x entityIdLength + dataFieldLength
        int totalLength = HEADER_SIZE + transactionSequenceNumberLength + entityIdLength*2 + dataFieldLength;
        if(totalLength > length) {
            throw new IllegalArgumentException("Cannot decode a CFDP PDU: derived PDU length is " + totalLength + " but specified length is " + length);
        }
        byte[] pdu = totalLength == length && offset == 0 ? data : Arrays.copyOfRange(data, offset, offset + totalLength);
        return buildPdu(type, entityIdLength, transactionSequenceNumberLength, pdu);
    }

    private static CfdpPdu buildPdu(CfdpPdu.PduType type, int entityIdLength, int transactionSequenceNumberLength, byte[] pdu) {
        // Now we need to understand what PDU we need to create
        if(type == CfdpPdu.PduType.FILE_DATA) {
            // File Data Pdu
            return new FileDataPdu(pdu);
        } else {
            // File Directive Pdu - Check the directive code byte: ref 5.2.1.2
            byte directiveCodeByte = pdu[HEADER_SIZE + transactionSequenceNumberLength + entityIdLength *2];
            switch (directiveCodeByte) {
                case 0x04: // EOF PDU
                    return new EndOfFilePdu(pdu);
                case 0x05: // Finished PDU
                    return new FinishedPdu(pdu);
                case 0x06: // ACK PDU
                    return new AckPdu(pdu);
                case 0x07: // Metadata PDU
                    return new MetadataPdu(pdu);
                case 0x08: // NAK PDU
                    return new NakPdu(pdu);
                case 0x09: // Prompt PDU
                    return new PromptPdu(pdu);
                case 0x0C: // Keep Alive
                    return new KeepAlivePdu(pdu);
                default:
                    throw new IllegalArgumentException("The provided data contains a directive code not supported by this implementation: " + String.format("0x%02X", directiveCodeByte));
            }
        }
    }
}
