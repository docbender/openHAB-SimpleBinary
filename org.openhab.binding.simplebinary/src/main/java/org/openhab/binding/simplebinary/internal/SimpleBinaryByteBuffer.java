/**
 * 
 */
package org.openhab.binding.simplebinary.internal;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * @author Vita Tucek
 * 
 */
public class SimpleBinaryByteBuffer {

	public enum BufferMode {
		WRITE, READ
	}

	protected ByteBuffer _buffer;
	protected BufferMode _mode = BufferMode.WRITE;
	protected int _size;

	public SimpleBinaryByteBuffer(int size) {
		_size = size;
		_buffer = ByteBuffer.allocate(size);
		
		_buffer.order(ByteOrder.LITTLE_ENDIAN);
	}
	
	public void initialize() {
		_mode = BufferMode.WRITE;
		_buffer = ByteBuffer.allocate(_size);
		
		_buffer.order(ByteOrder.LITTLE_ENDIAN);
	}
	
	public BufferMode getMode()
	{		
		return _mode;
	}	

	public ByteBuffer flip() throws ModeChangeException {
		if (_mode != BufferMode.READ) {
			_mode = BufferMode.READ;
			_buffer.flip();
		} else
			throw new ModeChangeException("flip()", _mode);

		return _buffer;
	}

	public ByteBuffer compact() throws ModeChangeException {
		if (_mode == BufferMode.READ) {
			_mode = BufferMode.WRITE;
			_buffer.compact();
		} else
			throw new ModeChangeException("compact()", _mode);

		return _buffer;
	}

	public ByteBuffer clear() {
		if (_mode == BufferMode.READ) {
			_mode = BufferMode.WRITE;
			_buffer.clear();
		} else
			_buffer.position(0);

		return _buffer;
	}

	public ByteBuffer order(ByteOrder order) {
		return _buffer.order(order);
	}

	public int position() {
		return _buffer.position();
	}

	public Buffer position(int position) {
		return _buffer.position(position);
	}

	public int remaining() {
		return _buffer.remaining();
	}

	public int limit() {
		return _buffer.limit();
	}

	public int capacity() {
		return _buffer.capacity();
	}

	public Buffer rewind() throws ModeChangeException {
		if (_mode == BufferMode.READ) {
			return _buffer.rewind();
		} else
			throw new ModeChangeException("rewind()", _mode);
	}

	public byte get() throws ModeChangeException {
		if (_mode == BufferMode.READ) {
			return _buffer.get();
		} else
			throw new ModeChangeException("get()", _mode);
	}

	public ByteBuffer get(byte[] array) throws ModeChangeException {
		if (_mode == BufferMode.READ) {
			return _buffer.get(array);
		} else
			throw new ModeChangeException("get(byte[] array)", _mode);
	}

	public short getShort() throws ModeChangeException {
		if (_mode == BufferMode.READ) {
			return _buffer.getShort();
		} else
			throw new ModeChangeException("getShort()", _mode);
	}

	public ByteBuffer put(byte[] array, int start, int bytes) throws ModeChangeException {
		if (_mode == BufferMode.WRITE) {
			return _buffer.put(array, start, bytes);
		} else
			throw new ModeChangeException("put(byte[] array, int start, int bytes)", _mode);
	}



}
