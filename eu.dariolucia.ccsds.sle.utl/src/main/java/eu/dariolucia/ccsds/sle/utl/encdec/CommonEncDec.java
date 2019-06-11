/*
 *  Copyright 2018-2019 Dario Lucia (https://www.dariolucia.eu)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package eu.dariolucia.ccsds.sle.utl.encdec;

import com.beanit.jasn1.ber.ReverseByteArrayOutputStream;
import com.beanit.jasn1.ber.types.BerType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * JASN.1 implements the encoding/decoding logic directly in each generated ASN.1 object.
 *
 * To handle different SLE versions, this class has a method called useSleVersion, which is used by the SLE User Test
 * Library to inform the decoder of the selected SLE version to be used. Hence specialisations of this class can
 * set the decoding function to use the correct PDU CHOICE class.
 *
 * Wrapping and unwrapping of PDUs is done by the related specialisations, since the wrapping and unwrapping are
 * type-specific (different classes). The coding is hardcoded and does not use reflection for performance
 * reasons. Unfortunately JASN.1 does not generate a method that allows to directly get the selected CHOICE object.
 */
public abstract class CommonEncDec {

	private final Map<Integer, Supplier<? extends BerType>> decodingTemplateProvider = new HashMap<>();
	private final Supplier<? extends BerType> defaultDecodingProvider;

	private volatile Supplier<? extends BerType> currentDecodingProvider;

	private volatile int version;

	CommonEncDec() {
		defaultDecodingProvider = getDefaultDecodingProvider();
		currentDecodingProvider = defaultDecodingProvider;
	}

	/**
	 * This method registers a constructor function, which is used to allocate the correct object factory of the
	 * SLE PDU wrapper class depending on the currently selected version.
	 *
	 * @param version the SLE version
	 * @param decoderProvider the supplier providing instances capable to deserialize SLE PDUs of the specified version
	 */
	protected final void register(int version, Supplier<? extends BerType> decoderProvider) {
		this.decodingTemplateProvider.put(version, decoderProvider);
	}

	/**
	 * This method is used by the {@link eu.dariolucia.ccsds.sle.utl.si.ServiceInstance} class to notify the encoder/
	 * decoder of a change in the version number.
	 *
	 * @param version the new SLE version to use for encoding/decoding
	 */
	public final void useSleVersion(int version) {
		this.version = version;
		currentDecodingProvider = decodingTemplateProvider.getOrDefault(version, defaultDecodingProvider);
	}

	/**
	 * This method returns the currently used SLE version.
	 *
	 * @return the currently used SLE version.
	 */
	protected final int getVersion() {
		return version;
	}

	/**
	 * This method performs a generic encoding of the provided SLE operation:
	 * <ul>
	 *     <li>Wrapping the operation in the appropriate wrapper class, depending on the version</li>
	 *     <li>Allocating a dynamic output stream</li>
	 *     <li>Encoding the wrapped object</li>
	 *     <li>Returning the produced byte array</li>
	 * </ul>
	 * @param toEncode the SLE PDU to encode
	 * @return the BER representation of the SLE PDU
	 * @throws IOException in case of problems during the encoding process
	 */
	public final byte[] encode(BerType toEncode) throws IOException {
		BerType o = wrapPdu(toEncode);
		ReverseByteArrayOutputStream os = new ReverseByteArrayOutputStream(140, true);
		o.encode(os); // by default calls encode(os, true)
		os.close();
		return os.getArray();
	}

	/**
	 * This method is used to decode a BER-encoded SLE PDU using the currently selected decoding wrapper class. This is
	 * selected depending on the notified SLE version. The decoding is performed using the following approach:
	 * <ul>
	 *     <li>The decoding wrapper class factory is called to build a clean instance of the wrapper class</li>
	 *     <li>The wrapper class instance is used to decode the SLE PDU</li>
	 *     <li>The result is unwrapped and returned</li>
	 * </ul>
	 * @param toDecode the BER representation of the SLE PDU
	 * @return the decoded SLE PDU
	 * @throws IOException in case of problems during the decoding process
	 */
	public final BerType decode(byte[] toDecode) throws IOException {
		if(currentDecodingProvider != null) {
			BerType wrapped = currentDecodingProvider.get();
			wrapped.decode(new ByteArrayInputStream(toDecode));
			return unwrapPdu(wrapped);
		} else {
			throw new IOException("Cannot find any decoding template provider set");
		}
	}

	protected abstract Supplier<? extends BerType> getDefaultDecodingProvider();

	protected abstract BerType wrapPdu(BerType toEncode) throws IOException;

	protected abstract BerType unwrapPdu(BerType toDecode) throws DecodingException;
}
