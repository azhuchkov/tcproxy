package me.azhuchkov.tcproxy;

import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Pool of direct byte buffers. Actually main goal of this pool is to cache buffers
 * since cost of their creation is quite high. Also high rate of their instantiation
 * may cause {@link java.lang.OutOfMemoryError}. Therefore there is no strong restriction
 * to return buffers to the pool back, but it is highly recommended due to GC may
 * fail buffers disposal in time.
 *
 * @author Andrey Zhuchkov
 *         Date: 22.08.14
 */
public class BufferPool {
    /** Count of iterations that must be performed before new buffer would be created. */
    private final static int SPIN_ITERATIONS = 1000;

    /** Cached buffers. */
    private final Queue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();

    /** Size of one buffer. */
    private final int bufferSize;

    /**
     * Creates new pool with buffers of given size.
     *
     * @param bufferSize Size of one buffer.
     */
    public BufferPool(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * Retrieves buffer from pool.
     *
     * @return Cached buffer or new one if cache is empty.
     */
    public ByteBuffer getBuffer() {
        ByteBuffer buffer;

        int iter = 0;

        do {
            buffer = queue.poll();
        } while (buffer == null && iter++ < SPIN_ITERATIONS);

        if (buffer == null)
            buffer = ByteBuffer.allocateDirect(bufferSize);
        else
            buffer.clear();

        return buffer;
    }

    /**
     * Returns given buffer back to the pool.
     * Once it's done client MUST not continue using the buffer.
     *
     * @param buffer Buffer to return.
     */
    public void returnBuffer(ByteBuffer buffer) {
        queue.add(buffer);
    }
}
