/*
 * Elias-Fano compression for Terrier 5
 *
 * Copyright (C) 2018-2020 Nicola Tonellotto 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

/*
 * The original source code is it.unimi.di.big.mg4j.index.QuasiSuccinctIndexReader.CountReader class
 * 
 * http://mg4j.di.unimi.it/docs-big/it/unimi/di/big/mg4j/index/QuasiSuccinctIndexReader.html
 * 
 * being part of
 *  		 
 * MG4J: Managing Gigabytes for Java (big)
 *
 * Copyright (C) 2012 Sebastiano Vigna 
 */
package it.cnr.isti.hpclab.ef.util;

import static it.unimi.dsi.bits.Fast.MSBS_STEP_8;
import static it.unimi.dsi.bits.Fast.ONES_STEP_4;
import static it.unimi.dsi.bits.Fast.ONES_STEP_8;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.longs.LongBigList;

public class FreqReader 
{
	/** The longword bit reader for pointers. */
	private final LongWordBitReader skipPointers;
	/** The longword bit reader for the lower bits. */
	private final LongWordBitReader lowerBits;

	/** The underlying list. */
	private final LongBigList list;
	/** The 64-bit window. */
	private long window;
	/** The current word position in the list of upper bits. */
	private long curr;

	/** The starting position of the pointers. */
	private final long skipPointersStart;
	/** The starting position of the power bits. */
	private final long lowerBitsStart;
	/** The starting position of the upper bits. */
	private final long upperBitsStart;

	/** The number of lower bits. */
	private final int l;
	/** The size of a pointer. */
	private final int pointerSize;
	/** The number of pointers. */
	private final long numberOfPointers;
	/** The logarithm of the quantum, cached from the index. */
	private final int log2Quantum;
	/** The quantum. */
	private final int quantum;

	/** The current prefix sum (the sum of the first {@link #currentIndex} elements). */
	private long prefixSum;
	/** The previous prefix sum. */
	protected long prevPrefixSum;
	/** The index of the current prefix sum. */
	protected long currentIndex;

	public FreqReader(final LongBigList list, final long position, final long frequency, final long occurrency, final int log2Quantum) 
	{
		this.l = EFUtils.lowerBits(frequency, occurrency, true);
		this.pointerSize = EFUtils.pointerSize(frequency,	occurrency, true, false);
		numberOfPointers = EFUtils.numberOfPointers(frequency, -1, log2Quantum, true, false);

		skipPointers = new LongWordBitReader(list, pointerSize);
		lowerBits = new LongWordBitReader(list, l);
		this.list = list;

		skipPointersStart = position;
		lowerBitsStart = skipPointersStart + pointerSize * numberOfPointers;
		lowerBits.position(lowerBitsStart);
		upperBitsStart = lowerBitsStart + l * frequency;
		currentIndex = prevPrefixSum = prefixSum = 0;

		this.log2Quantum = log2Quantum;
		quantum = 1 << log2Quantum;

		position(upperBitsStart);
	}

	private void position(final long position) 
	{
		window = list.getLong(curr = position / Long.SIZE) & -1L << (int) (position);
	}

	public long getLong(final long index) 
	{

		long delta = index - currentIndex;

		if (delta == 0) { // shortcut
			prevPrefixSum = prefixSum;
			while (window == 0)
				window = list.getLong(++curr);
			prefixSum = curr * Long.SIZE + Long.numberOfTrailingZeros(window) - currentIndex++ - upperBitsStart << l | lowerBits.extract();
			window &= window - 1;
			return prefixSum - prevPrefixSum + 1;
		}

		if (delta >= quantum) {
			final long block = index >>> log2Quantum;
			assert block > 0;
			assert block <= numberOfPointers;
			final long skip = skipPointers.extract(skipPointersStart + (block - 1) * pointerSize);
			position(upperBitsStart + skip - 1);
			final long blockOnes = block << log2Quantum;
			delta = index - blockOnes + 1;
		}

		for (int bitCount; (bitCount = Long.bitCount(window)) < delta; delta -= bitCount)
			window = list.getLong(++curr);

		/*
		 * This appears to be faster than != 0.
		 * Note that for delta == 1 the following code is a NOP.
		 */
		if (--delta > 0) {
			// Phase 1: sums by byte
			final long word = window;
			assert delta < Long.bitCount(word) : delta + " >= "	+ Long.bitCount(word);
			long byteSums = word - ((word & 0xa * ONES_STEP_4) >>> 1);
			byteSums = (byteSums & 3 * ONES_STEP_4) + ((byteSums >>> 2) & 3 * ONES_STEP_4);
			byteSums = (byteSums + (byteSums >>> 4)) & 0x0f * ONES_STEP_8;
			byteSums *= ONES_STEP_8;
			// Phase 2: compare each byte sum with delta to obtain the relevant byte
			final long rankStep8 = delta * ONES_STEP_8;
			final long byteOffset = (((((rankStep8 | MSBS_STEP_8) - byteSums) & MSBS_STEP_8) >>> 7)	* ONES_STEP_8 >>> 53) & ~0x7;

			final int byteRank = (int) (delta - (((byteSums << 8) >>> byteOffset) & 0xFF));

			final int select = (int) (byteOffset + Fast.selectInByte[(int) (word >>> byteOffset & 0xFF)	| byteRank << 8]);

			// We cancel up to, but not including, the target one.
			window &= -1L << select;
		}

		assert window != 0;
		currentIndex = index + 1;
		prevPrefixSum = curr * Long.SIZE + Long.numberOfTrailingZeros(window) - (index - 1) - upperBitsStart << l | lowerBits.extract(lowerBitsStart + l * (index - 1));
		window &= window - 1;
		while (window == 0)
			window = list.getLong(++curr);
		prefixSum = curr * Long.SIZE + Long.numberOfTrailingZeros(window) - index - upperBitsStart << l | lowerBits.extract();
		window &= window - 1;
		return prefixSum - prevPrefixSum + 1;
	}

	@Override
	public String toString() 
	{
		return this.getClass().getSimpleName() + '@' + Integer.toHexString(System.identityHashCode(this));
	}
	
	// The following methods are used just for PositionReader
	public long prevPrefixSum() { return prevPrefixSum; }
	public long currentIndex()  { return currentIndex;  }
}