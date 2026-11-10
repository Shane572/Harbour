package com.harbour.reader

import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater

class ZipEntryInfo(
    val name: String,
    val method: Int,
    val compressedSize: Long,
    val size: Long,
    val localHeaderOffset: Long,
)

/**
 * A small random-access ZIP reader that works straight off a FileChannel.
 *
 * Android's folder access gives us file descriptors, not file paths, and java.util.zip.ZipFile
 * needs a path. Reading the central directory ourselves lets us jump to any page instantly
 * without copying the whole comic first.
 */
object MiniZip {
    private const val EOCD_SIG = 0x06054b50
    private const val ZIP64_LOCATOR_SIG = 0x07064b50
    private const val ZIP64_EOCD_SIG = 0x06064b50
    private const val CEN_SIG = 0x02014b50
    private const val LOC_SIG = 0x04034b50
    private const val MAX_ENTRY = 256L * 1024 * 1024
    private const val U32_MAX = 0xFFFFFFFFL

    fun readEntries(ch: FileChannel): List<ZipEntryInfo> {
        val fileSize = ch.size()
        if (fileSize < 22) throw IOException("Not a zip file")
        val tailLen = minOf(fileSize, 22L + 65535L).toInt()
        val tailStart = fileSize - tailLen
        val tail = readAt(ch, tailStart, tailLen)

        var eocd = -1
        for (i in tailLen - 22 downTo 0) {
            if (tail.getInt(i) == EOCD_SIG) {
                eocd = i
                break
            }
        }
        if (eocd < 0) throw IOException("Not a zip file")

        var cdSize = tail.u32(eocd + 12)
        var cdOffset = tail.u32(eocd + 16)
        val eocdAbs = tailStart + eocd
        if ((cdOffset == U32_MAX || cdSize == U32_MAX || tail.u16(eocd + 10) == 0xFFFF) && eocdAbs >= 20) {
            val loc = readAt(ch, eocdAbs - 20, 20)
            if (loc.getInt(0) == ZIP64_LOCATOR_SIG) {
                val z64 = readAt(ch, loc.getLong(8), 56)
                if (z64.getInt(0) == ZIP64_EOCD_SIG) {
                    cdSize = z64.getLong(40)
                    cdOffset = z64.getLong(48)
                }
            }
        }
        if (cdSize < 0 || cdSize > 64L * 1024 * 1024 || cdOffset < 0 || cdOffset + cdSize > fileSize) {
            throw IOException("Damaged zip directory")
        }

        val cd = readAt(ch, cdOffset, cdSize.toInt())
        val out = ArrayList<ZipEntryInfo>()
        var p = 0
        while (p + 46 <= cd.limit() && cd.getInt(p) == CEN_SIG) {
            val method = cd.u16(p + 10)
            var comp = cd.u32(p + 20)
            var size = cd.u32(p + 24)
            val nameLen = cd.u16(p + 28)
            val extraLen = cd.u16(p + 30)
            val commentLen = cd.u16(p + 32)
            var offset = cd.u32(p + 42)
            if (p + 46 + nameLen + extraLen > cd.limit()) break

            val nameBytes = ByteArray(nameLen)
            for (k in 0 until nameLen) nameBytes[k] = cd.get(p + 46 + k)

            if (size == U32_MAX || comp == U32_MAX || offset == U32_MAX) {
                var e = p + 46 + nameLen
                val end = e + extraLen
                while (e + 4 <= end) {
                    val id = cd.u16(e)
                    val len = cd.u16(e + 2)
                    if (id == 0x0001) {
                        var q = e + 4
                        if (size == U32_MAX && q + 8 <= end) { size = cd.getLong(q); q += 8 }
                        if (comp == U32_MAX && q + 8 <= end) { comp = cd.getLong(q); q += 8 }
                        if (offset == U32_MAX && q + 8 <= end) { offset = cd.getLong(q) }
                        break
                    }
                    e += 4 + len
                }
            }

            out += ZipEntryInfo(String(nameBytes, Charsets.UTF_8), method, comp, size, offset)
            p += 46 + nameLen + extraLen + commentLen
        }
        return out
    }

    fun read(ch: FileChannel, e: ZipEntryInfo): ByteArray {
        if (e.size > MAX_ENTRY || e.compressedSize > MAX_ENTRY) throw IOException("Page too large")
        val loc = readAt(ch, e.localHeaderOffset, 30)
        if (loc.getInt(0) != LOC_SIG) throw IOException("Damaged entry ${e.name}")
        val dataStart = e.localHeaderOffset + 30 + loc.u16(26) + loc.u16(28)
        val compressed = readAt(ch, dataStart, e.compressedSize.toInt()).array()

        return when (e.method) {
            0 -> compressed
            8 -> inflate(compressed, e.size.toInt())
            else -> throw IOException("Unsupported compression in ${e.name}")
        }
    }

    private fun inflate(input: ByteArray, size: Int): ByteArray {
        val inflater = Inflater(true)
        try {
            // Raw deflate can want one trailing dummy byte to signal the end of input.
            inflater.setInput(input.copyOf(input.size + 1))
            val out = ByteArray(size)
            var n = 0
            while (n < size) {
                val r = inflater.inflate(out, n, size - n)
                if (r == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                n += r
            }
            return if (n == size) out else out.copyOf(n)
        } finally {
            inflater.end()
        }
    }

    private fun readAt(ch: FileChannel, pos: Long, len: Int): ByteBuffer {
        val buf = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN)
        var p = pos
        while (buf.hasRemaining()) {
            val r = ch.read(buf, p)
            if (r < 0) throw EOFException()
            p += r
        }
        buf.flip()
        return buf
    }

    private fun ByteBuffer.u16(i: Int): Int = getShort(i).toInt() and 0xFFFF
    private fun ByteBuffer.u32(i: Int): Long = getInt(i).toLong() and U32_MAX
}
