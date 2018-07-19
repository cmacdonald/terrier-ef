/*
 * The original source code is it.unimi.di.big.mg4j.index.QuasiSuccinctIndexWriter class
 * 
 * http://mg4j.di.unimi.it/docs-big/it/unimi/di/big/mg4j/index/QuasiSuccinctIndexWriter.html
 * 
 * being part of
 *  		 
 * MG4J: Managing Gigabytes for Java (big)
 *
 * Copyright (C) 2012 Sebastiano Vigna 
 */
package it.cnr.isti.hpclab.ef.util;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.nio.channels.FileChannel;

/**
 * This is a cache for long (i.e., 64 bits) objects, accessible at bit level. It seems that
 * the cache must be populated first, then "rewinded", then accessed sequentially.
 * It is backed by a file {@link #spill_file} on disk accessed via Java NIO {@link #spill_channel}. 
 * Up to {@link #cache_bit_length} bits are held in memory, everything else on disk.
 */
public final class LongWordCache implements Closeable 
{
	/** The spill file. */
	private final File spill_file;
	/** A channel opened on {@link #spill_file}. */
	private final FileChannel spill_channel;
	/** Whether {@link #spill_channel} should be repositioned at 0 <b>before usage</b>. */
	private boolean spill_must_be_rewind;
	
	/** A cache for longwords. Will be spilled to {@link #spill_channel} in case more than {@link #cache_bit_length} bits are added. */
	private final ByteBuffer cache;
	
	/** The current bit buffer. */
	private long buffer;
	
	/** The current number of free bits in {@link #buffer}. */
	private int free;
	
	/** The length of the cache, in <b>bits</b>. */
	private long cache_bit_length;
	
	/** The number of bits currently stored. */
	private long length;
	
	/**
	 * Creates a cache with a length of <code>cache_size</code> length in bits. 
	 * The <code>suffix</code> is the suffix of the temporary file created to back up
	 * the cache on disk. It is deleted on exit.
	 * 
	 * @param cache_bit_size the length of the cache memory buffer in bits
	 * @param tmp_suffix the suffix of the temporary file backing up the cache on disk
	 * @throws IOException if something goes wrong
	 */
	@SuppressWarnings("resource")
	public LongWordCache(final int cache_bit_size, final String tmp_suffix) throws IOException 
	{
		spill_file = File.createTempFile(LongWordCache.class.getName(), tmp_suffix);
		spill_file.deleteOnExit();
		spill_channel = new RandomAccessFile(spill_file, "rw").getChannel();
		cache = ByteBuffer.allocateDirect(cache_bit_size).order(ByteOrder.nativeOrder());
		cache_bit_length = cache_bit_size * 8L; // in bits
		free = Long.SIZE;
	}

	/**
	 * Insert in cache a long <code>value</code> on <code>bit_width</code> bits (lower positions).
	 * @param value the value to insert in cache
	 * @param bit_width the size in bits of the value to insert
	 * @return the number of bits written
	 * @throws IOException if something goes wrong
	 */
	public int append(final long value, final int bit_width) throws IOException 
	{
		buffer |= value << (Long.SIZE - free);
		length += bit_width;

		if (bit_width < free)
			free -= bit_width;
		else {
			flushBuffer();

			if (bit_width == free) {
				buffer = 0;
				free = Long.SIZE;
			} else {
				// free < Long.SIZE
				buffer = value >>> free;
				free = Long.SIZE - bit_width + free; // width > free
			}
		}
		return bit_width;
	}

	/**
	 * Empty the cache
	 */
	public void clear() 
	{
		length = buffer = 0;
		free = Long.SIZE;
		((Buffer)cache).clear();
		spill_must_be_rewind = true;
	}

	/**
	 * Close the underlying stream and delete the file
	 */
	@Override
	public void close() throws IOException 
	{
		spill_channel.close();
		spill_file.delete();
	}

	/**
	 * Return the number of bits currently stored in cache
	 * @return the number of bits currently stored
	 */
	public long length() 
	{
		return length;
	}

	/**
	 * Write an integer in unary coding
	 * @param l the integer to write
	 * @throws IOException if something goes wrong
	 */
	public void writeUnary(int l) throws IOException 
	{
		if (l >= free) {
			// Phase 1: align
			l -= free;
			length += free;
			flushBuffer();

			// Phase 2: jump over longwords
			buffer = 0;
			free = Long.SIZE;
			while (l >= Long.SIZE) {
				flushBuffer();
				l -= Long.SIZE;
				length += Long.SIZE;
			}
		}

		append(1L << l, l + 1);
	}

	/**
	 * Return the next long from the cache
	 * @return the next long from the cache 
	 * @throws IOException if something goes wrong
	 */
	public long readLong() throws IOException 
	{
		if (!cache.hasRemaining()) {
			((Buffer)cache).clear();
			spill_channel.read(cache);
			((Buffer)cache).flip();
		}
		return cache.getLong();
	}

	/**
	 * Convert the cache from writing to reading. No write operations allowed after rewind.
	 * @throws IOException if something goes wrong
	 */
	public void rewind() throws IOException 
	{
		if (free != Long.SIZE)
			cache.putLong(buffer);

		if (length > cache_bit_length) {
			((Buffer)cache).flip();
			spill_channel.write(cache);
			spill_channel.position(0);
			((Buffer)cache).clear();
			spill_channel.read(cache);
			((Buffer)cache).flip();
		} else
			((Buffer)cache).rewind();
	}
	
	private void flushBuffer() throws IOException 
	{
		cache.putLong(buffer);
		if (!cache.hasRemaining()) {
			if (spill_must_be_rewind) {
				spill_must_be_rewind = false;
				spill_channel.position(0);
			}
			((Buffer)cache).flip();
			spill_channel.write(cache);
			((Buffer)cache).clear();
		}
	}
	
	public static void main(String[] args) throws IOException
	{
		LongWordCache cache = new LongWordCache(8, "tmp");
		
		cache.append(11, Long.SIZE);
		cache.append(12, Long.SIZE);
		cache.append(13, Long.SIZE);
		cache.append(14, Long.SIZE);
		cache.append(15, Long.SIZE);
		cache.append(16, Long.SIZE);
		cache.append(17, Long.SIZE);
		cache.append(18, Long.SIZE);
		cache.append(19, Long.SIZE);
		cache.append(20, Long.SIZE);
		
		cache.append(30, Long.SIZE);
		
		cache.rewind();
		
		System.err.println(cache.readLong());
		System.err.println(cache.readLong());
		System.err.println(cache.readLong());
		System.err.println(cache.readLong());
		
		System.err.println(cache.readLong());
		System.err.println(cache.readLong());
		System.err.println(cache.readLong());
		System.err.println(cache.readLong());
		
		cache.close();
	}
}