/*
 * Reed-Solomon error-correcting code encoder/decoder.
 *
 * Copyright (c) 2017 Project Nayuki
 * https://www.nayuki.io/page/reed-solomon-error-correcting-code-decoder
 *
 */

package eu.dariolucia.ccsds.tmtc.algorithm.rs;

import java.util.Arrays;
import java.util.Objects;

/**
 * Performs Reed-Solomon encoding and error-detection. This object can encode a message into a codeword.
 * The codeword can have some values modified by external code. Then this object can try
 * to check the codeword and identify whether errors were introduced.
 * Original Nayuki's code can also attempt error correction.
 * <p>This class is immutable and thread-safe, but the argument arrays passed into methods are not thread-safe.</p>
 */
public final class ReedSolomon {
	
	/*---- Fields ----*/
	
	/** The number of values in each message. Always at least 1. */
	public final int messageLen;
	
	/** The number of error correction values to expand the message by. Always at least 1. */
	public final int eccLen;
	
	/** The number of values in each codeword, equal to messageLen + eccLen. Always at least 2. */
	public final int codewordLen;

	// The field for message and codeword values, and for performing arithmetic operations on values. Not null.
	private final BinaryField f;
	
	// An element of the field whose powers generate all the non-zero elements of the field. Not null.
	private final int generator;

	// Initial root
	private final int initialRoot;

	// Generator polynomial
	private final int[] genPoly;

	/*---- Constructor ----*/
	
	/**
	 * Constructs a Reed-Solomon encoder-decoder with the specified field, lengths, and other parameters.
	 * <p>Note: The class argument is used like this:
	 * {@code ReedSolomon<Integer> rs = new ReedSolomon<>(fieldMod, gen, msgLen, eccLen, initialRoot);}</p>
	 * @param fieldMod the field modulus
	 * @param gen a generator of the field {@code f} (not {@code null})
	 * @param msgLen the length of message arrays, which must be positive
	 * @param eccLen the number of values to expand each message by, which must be positive
	 * @param initialRoot the initial root of the code generator polynom (modification from original code)
	 * @throws NullPointerException if any of the object arguments is null
	 * @throws IllegalArgumentException if msgLen &le; 0, eccLen &le; 0, or mlgLen + eccLen > Integer.MAX_VALUE
	 */
	public ReedSolomon(int fieldMod, int gen, int msgLen, int eccLen, int initialRoot) {
		// Check arguments
		if (msgLen <= 0 || eccLen <= 0 || Integer.MAX_VALUE - msgLen < eccLen)
			throw new IllegalArgumentException("Invalid message or ECC length");
		
		// Assign fields
		this.f = new BinaryField(fieldMod);
		this.generator = gen;
		this.messageLen = msgLen;
		this.eccLen = eccLen;
		this.codewordLen = msgLen + eccLen;
		this.initialRoot = initialRoot;
		// Make the generator polynomial (this doesn't depend on the message)
		this.genPoly = makeGeneratorPolynomial();
	}

	/*---- Encoder methods ----*/
	
	/**
	 * Returns a new array representing the codeword produced by encoding the specified message.
	 * If the message has the correct length and all its values are
	 * valid in the field, then this method is guaranteed to succeed.
	 * @param message the message to encode, whose length must equal {@code this.messageLen}
	 * @return a new array representing the codeword values
	 * @throws NullPointerException if the message array or any of its elements are {@code null}
	 * @throws IllegalArgumentException if the message array has the wrong length
	 */
	public byte[] encode(byte[] message) {
		// Check arguments
		Objects.requireNonNull(message);
		if (message.length != messageLen)
			throw new IllegalArgumentException("Invalid message length");

		// Compute the remainder ((message(x) * x^eccLen) mod genPoly(x)) by performing polynomial division.
		// Process message bytes (polynomial coefficients) from the highest monomial power to the lowest power
		byte[] eccPoly = new byte[eccLen];
		Arrays.fill(eccPoly, (byte) f.zero());
		for (int i = messageLen - 1; i >= 0; i--) {
			int factor = f.fadd(Byte.toUnsignedInt(message[i]), Byte.toUnsignedInt(eccPoly[eccLen - 1]));
			System.arraycopy(eccPoly, 0, eccPoly, 1, eccLen - 1);
			eccPoly[0] = (byte) f.zero();
			for (int j = 0; j < eccLen; j++)
				eccPoly[j] = (byte) f.fadd(Byte.toUnsignedInt(eccPoly[j]), f.fmultiply(genPoly[j], factor)); // outer fsubtract replaced with fadd
		}
		
		// Negate the remainder
		for (int i = 0; i < eccPoly.length; i++) {
			eccPoly[i] = (byte) f.negate(Byte.toUnsignedInt(eccPoly[i]));
		}
		
		// Concatenate the message and ECC polynomials
		byte[] result = new byte[codewordLen];
		System.arraycopy(eccPoly, 0, result, 0, eccLen);
		System.arraycopy(message, 0, result, eccLen, messageLen);
		return result;
	}
	
	// Computes the generator polynomial by multiplying powers of the generator value:
	// genPoly(x) = (x - gen^0) * (x - gen^1) * ... * (x - gen^(eccLen-1)).
	// The resulting array of coefficients is in little endian, i.e. from lowest to highest power, except
	// that the very highest power (the coefficient for the x^eccLen term) is omitted because it's always 1.
	// The result of this method can be pre-computed because it doesn't depend on the message to be encoded.

	// Modified to introduce the support of initialRoot
	private int[] makeGeneratorPolynomial() {
		// Start with the polynomial of 1*x^0, which is the multiplicative identity
		int[] result = new int[eccLen];
		Arrays.fill(result, f.zero());
		result[0] = f.one();

		int genPow = pow(generator, initialRoot); // Original: (2, initialRoot);
		for (int i = 0; i < eccLen; i++) {
			// At this point, genPow == generator^i.
			// Multiply the current genPoly by (x - generator^i)
			for (int j = eccLen - 1; j >= 0; j--) {
				result[j] = f.multiply(f.negate(genPow), result[j]);
				if (j >= 1)
					result[j] = f.add(result[j - 1], result[j]);
			}
			genPow = f.multiply(generator, genPow);
		}
		return result;
	}

	/*---- Decoder methods ----*/

	/**
	 * Remove the RS symbols from the specified codeword, returning either the correct message or {@code null}.
	 * @param codeword the codeword to check, whose length must equal {@code this.codewordLen}
	 * @return a new array representing the decoded message, or {@code null} to indicate failure
	 * @throws NullPointerException if the codeword is {@code null}
	 * @throws IllegalArgumentException if the codeword array has the wrong length
	 */
	public byte[] check(byte[] codeword, boolean checkForErrors) {
		// Check arguments
		Objects.requireNonNull(codeword);
		if (codeword.length != codewordLen)
			throw new IllegalArgumentException("Invalid codeword length");

		// Calculate and check syndromes
		if(checkForErrors) {
			int[] syndromes = calculateSyndromes(codeword);
			if (!areAllZero(syndromes)) {
				return null;
			}
		}
		// Extract the message part of the codeword
		return Arrays.copyOfRange(codeword, eccLen, codeword.length);
	}

	// Returns a new array representing the sequence of syndrome values for the given codeword.
	// To summarize the math, syndrome[i] = codeword(generator^i).

	// Modified to introduce the support of initialRoot
	private int[] calculateSyndromes(byte[] codeword) {
		// Check arguments
		Objects.requireNonNull(codeword);
		if (codeword.length != codewordLen)
			throw new IllegalArgumentException();

		// Evaluate the codeword polynomial at generator powers
		int[] result = new int[eccLen];
		int genPow = pow(generator, initialRoot); // f.one();
		for (int i = 0; i < result.length; i++) {
			result[i] = evaluatePolynomial(codeword, genPow);
			genPow = f.multiply(generator, genPow);
		}
		return result;
	}

	/*---- Simple utility methods ----*/

	// Returns the value of the given polynomial at the given point. The polynomial is represented
	// in little endian. In other words, this method evaluates result = polynomial(point)
	// = polynomial[0]*point^0 + polynomial[1]*point^1 + ... + ponylomial[len-1]*point^(len-1).
	private int evaluatePolynomial(byte[] polynomial, int point) {
		// Horner's method
		int result = f.zero();
		for (int i = polynomial.length - 1; i >= 0; i--) {
			result = f.fmultiply(point, result);
			result = f.fadd(Byte.toUnsignedInt(polynomial[i]), result);
		}
		return result;
	}

	// Tests whether all elements of the given array are equal to the field's zero element.
	private boolean areAllZero(int[] array) {
		for (int val : array) {
			if (!f.equals(val, f.zero())) {
				return false;
			}
		}
		return true;
	}

	// Returns the given field element raised to the given power. The power must be non-negative.
	private int pow(int base, int exp) {
		if (exp < 0) {
			throw new IllegalArgumentException("Power " + exp + " is negative");
		}
		int result = f.one();
		for (int i = 0; i < exp; i++) {
			result = f.multiply(base, result);
		}
		return result;
	}

	/**
	 * A Galois field of the form GF(2^n/mod). Each element of this kind of field is a
	 * polynomial of degree less than n where each monomial coefficient is either 0 or 1.
	 * Both the field and the elements are immutable and thread-safe.
	 */
	public static final class BinaryField {

		/*---- Fields ----*/

		/**
		 * The modulus of this field represented as a string of bits in natural order.
		 * For example, the modulus x^5 + x^1 + x^0 is represented by the integer value 0b100011 (binary) or 35 (decimal).
		 */
		final int modulus;


		/**
		 * The number of (unique) elements in this field. It is a positive power of 2, e.g. 2, 4, 8, 16, etc.
		 * The size of the field is equal to 2 to the power of the degree of the modulus.
		 */
		final int size;

		final int[][] precomputedAddTable;

		final int[][] precomputedMulTable;

		/*---- Constructor ----*/

		/**
		 * Constructs a binary field with the specified modulus. The modulus must have degree
		 * between 1 and 30, inclusive. Also the modulus must be irreducible (not factorable)
		 * in Z_2, but this critical property is not checked by the constructor.
		 *
		 * @param mod the modulus
		 */
		BinaryField(int mod) {
			if (mod == 0) {
				throw new IllegalArgumentException("Division by zero");
			}
			if (mod == 1) {
				throw new IllegalArgumentException("Degenerate field");
			}
			if (mod < 0) {
				throw new IllegalArgumentException("Modulus too large");
			}
			int degree = 31 - Integer.numberOfLeadingZeros(mod);
			modulus = mod;
			size = 1 << degree;

			// Compute operation tables
			precomputedAddTable = new int[size][size];
			precomputedMulTable = new int[size][size];
			for(int i = 0; i < size; ++i) {
				for(int j = 0; j < size; ++j) {
					precomputedAddTable[i][j] = add(i,j);
					precomputedMulTable[i][j] = multiply(i,j);
				}
			}
		}

		/*---- Methods ----*/

		// Checks if the given object is non-null and within the range
		// of valid values, and returns the unboxed primitive value.
		private int check(int x) {
			int y = x;
			if (y < 0 || y >= size) {
				throw new IllegalArgumentException("Not an element of this field: " + y);
			}
			return y;
		}


		public boolean equals(int x, int y) {
			return check(x) == check(y);
		}


		public int zero() {
			return 0;
		}


		public int one() {
			return 1;
		}


		public int negate(int x) {
			return check(x);
		}


		public int fadd(int x, int y) {
			return precomputedAddTable[x][y];
		}

		public int add(int x, int y) {
			return check(x) ^ check(y);
		}

		public int fsubtract(int x, int y) {
			return fadd(x, y);
		}

		public int subtract(int x, int y) {
			return add(x, y);
		}

		public int fmultiply(int x, int y) {
			return precomputedMulTable[x][y];
		}

		public int multiply(int x, int y) {
			return multiplyImpl(check(x), check(y));
		}

		private int multiplyImpl(int x, int y) {
			int result = 0;
			for (; y != 0; y >>>= 1) {
				if ((y & 1) != 0) {
					result ^= x;
				}
				x <<= 1;
				if (x >= size) {
					x ^= modulus;
				}
			}
			return result;
		}
	}
}
