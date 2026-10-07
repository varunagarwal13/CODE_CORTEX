package com.vocis.vcd.audio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Thread-safe circular sample buffer designed for streaming 16kHz audio.
 * Allows continuous streaming writes from audio capture threads while
 * WindowSlicingEngine reads overlapping frames.
 */
class AudioRingBuffer(val capacity: Int = 160000) {

    private val buffer = FloatArray(capacity)
    private var writePos = 0
    private var readPos = 0
    private var count = 0
    private val lock = ReentrantLock()

    /**
     * Writes incoming float samples into the circular buffer.
     * If capacity is exceeded, oldest unread samples are overwritten.
     */
    fun write(samples: FloatArray, offset: Int = 0, length: Int = samples.size) {
        lock.withLock {
            for (i in 0 until length) {
                val sample = samples[offset + i]
                buffer[writePos] = sample
                writePos = (writePos + 1) % capacity
                if (count < capacity) {
                    count++
                } else {
                    // Buffer overflow: advance readPos to overwrite oldest sample
                    readPos = (readPos + 1) % capacity
                }
            }
        }
    }

    /**
     * Reads up to destination.size samples without advancing read position (peek).
     * Returns actual number of samples copied.
     */
    fun peek(destination: FloatArray, countToRead: Int = destination.size): Int {
        lock.withLock {
            val toRead = minOf(countToRead, count, destination.size)
            var current = readPos
            for (i in 0 until toRead) {
                destination[i] = buffer[current]
                current = (current + 1) % capacity
            }
            return toRead
        }
    }

    /**
     * Discards (hops forward) specified number of samples from the read pointer.
     */
    fun advance(samplesToDiscard: Int): Int {
        lock.withLock {
            val toDiscard = minOf(samplesToDiscard, count)
            readPos = (readPos + toDiscard) % capacity
            count -= toDiscard
            return toDiscard
        }
    }

    /**
     * Returns total unread samples currently available in the buffer.
     */
    fun available(): Int {
        lock.withLock {
            return count
        }
    }

    /**
     * Resets read and write pointers, clearing the buffer.
     */
    fun clear() {
        lock.withLock {
            writePos = 0
            readPos = 0
            count = 0
        }
    }
}
