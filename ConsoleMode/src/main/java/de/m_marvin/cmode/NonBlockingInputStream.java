package de.m_marvin.cmode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class NonBlockingInputStream extends InputStream {

	protected final int maxSize;
	protected int wpos = 0;
	protected int rpos = 0;
	protected byte[] buffer;
	protected final Thread reader;
	protected final InputStream stream;
	protected Exception readerEx;
	
	public NonBlockingInputStream(InputStream stream) {
		this(stream, 1024, 16384);
	}
	
	public NonBlockingInputStream(InputStream stream, int initialSize, int maxSize) {
		this.maxSize = maxSize;
		this.stream = stream;
		this.buffer = new byte[initialSize];
		this.reader = new Thread(this::pollStream, "BufferedInputStream-Reader");
		this.reader.setDaemon(true);
		this.reader.start();
	}
	
	protected boolean manageBuffer() {
		if (rpos > buffer.length / 2) {
			synchronized (buffer) {
				for (int i = rpos; i < buffer.length; i++)
					buffer[i] = buffer[i + rpos];
				wpos -= rpos;
				rpos = 0;
			}
		} else if (wpos == buffer.length) {
			if (wpos == maxSize) return false;
			synchronized (buffer) {
				int nbuflen = Math.max((int) Math.round(buffer.length * 1.2), this.maxSize);
				buffer = Arrays.copyOf(buffer, nbuflen);
			}
		}
		return true;
	}
	
	protected void pollStream() {
		try {
			while (true) {
				int i = this.stream.read();
				synchronized (buffer) {
					while (!manageBuffer())
						buffer.wait();
					buffer[wpos++] = (byte) (i & 0xFF);
					buffer.notifyAll();
				}
			}
		} catch (Exception e) {
			readerEx = e;
		}
	}
	
	@Override
	public byte[] readAllBytes() throws IOException {
		try {
			synchronized (buffer) {
				while (available() == 0)
					buffer.wait();
				return readNBytes(available());
			}
		} catch (InterruptedException e) {
			return new byte[0];
		}
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (this.readerEx != null) throw new IOException("internal buffer error!", this.readerEx);
		synchronized (buffer) {
			int read = Math.min(len, available());
			for (int i = 0; i < read; i++)
				b[i] = buffer[rpos + i];
			rpos += read;
			return read;
		}
	}
	
	@Override
	public int read() throws IOException {
		synchronized (buffer) {
			return buffer[rpos++];
		}
	}
	
	@Override
	public int available() throws IOException {
		synchronized (buffer) {
			return wpos - rpos;	
		}
	}

	@Override
	public void close() throws IOException {
		this.stream.close();
		try {
			this.reader.join();
		} catch (InterruptedException e) {}
		this.readerEx = null;
	}
	
}
