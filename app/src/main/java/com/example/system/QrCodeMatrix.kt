package com.example.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Compact pure-Kotlin QR Code encoder (Versions 1..4, Error Correction Level L, Byte Mode)
 * for zero-bloat LAN Web Console pairing QR codes on 1GB RAM dictionary pens.
 */
object QrCodeMatrix {

    private data class VersionSpec(val version: Int, val size: Int, val dataCodewords: Int, val eccCodewords: Int, val alignPos: Int)

    private val specs = listOf(
        VersionSpec(1, 21, 19, 7, -1),
        VersionSpec(2, 25, 34, 10, 18),
        VersionSpec(3, 29, 55, 15, 22),
        VersionSpec(4, 33, 80, 20, 26)
    )

    // Precomputed GF(256) exp/log tables with primitive polynomial 0x11D
    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) gfExp[i] = gfExp[i - 255]
    }

    private fun gfMul(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else gfExp[gfLog[a] + gfLog[b]]

    private fun reedSolomon(data: IntArray, eccLen: Int): IntArray {
        var gen = intArrayOf(1)
        for (i in 0 until eccLen) {
            val next = IntArray(gen.size + 1)
            val root = gfExp[i]
            for (j in gen.indices) {
                next[j] = next[j] xor gen[j]
                next[j + 1] = next[j + 1] xor gfMul(gen[j], root)
            }
            gen = next
        }
        val rem = IntArray(eccLen)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, eccLen - 1)
            rem[eccLen - 1] = 0
            if (factor != 0) {
                for (j in 0 until eccLen) {
                    rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
                }
            }
        }
        return rem
    }

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val spec = specs.firstOrNull { bytes.size + 2 <= it.dataCodewords } ?: specs.last()
        val payload = bytes.take((spec.dataCodewords - 2).coerceAtLeast(1))

        val bits = ArrayList<Int>(spec.dataCodewords * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits.add((value ushr i) and 1)
        }
        appendBits(0b0100, 4) // Byte mode
        appendBits(payload.size, 8)
        for (b in payload) appendBits(b.toInt() and 0xFF, 8)
        val totalDataBits = spec.dataCodewords * 8
        appendBits(0, minOf(4, totalDataBits - bits.size))
        while (bits.size % 8 != 0) bits.add(0)
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < totalDataBits) {
            appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        val dataCw = IntArray(spec.dataCodewords) { i ->
            var v = 0
            for (b in 0 until 8) v = (v shl 1) or bits[i * 8 + b]
            v
        }
        val eccCw = reedSolomon(dataCw, spec.eccCodewords)
        val allCw = IntArray(dataCw.size + eccCw.size)
        System.arraycopy(dataCw, 0, allCw, 0, dataCw.size)
        System.arraycopy(eccCw, 0, allCw, dataCw.size, eccCw.size)

        val n = spec.size
        val modules = Array(n) { BooleanArray(n) }
        val isFunction = Array(n) { BooleanArray(n) }

        fun setFn(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until n && c in 0 until n) {
                modules[r][c] = dark
                isFunction[r][c] = true
            }
        }

        fun drawFinder(top: Int, left: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = top + dr
                    val c = left + dc
                    if (r !in 0 until n || c !in 0 until n) continue
                    val dark = dr in 0..6 && dc in 0..6 &&
                        (dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4))
                    setFn(r, c, dark)
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, n - 7)
        drawFinder(n - 7, 0)

        for (i in 8 until n - 8) {
            setFn(6, i, i % 2 == 0)
            setFn(i, 6, i % 2 == 0)
        }
        setFn(n - 8, 8, true)

        if (spec.alignPos > 0) {
            val p = spec.alignPos
            for (dr in -2..2) {
                for (dc in -2..2) {
                    val dark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                    setFn(p + dr, p + dc, dark)
                }
            }
        }

        // Reserve format info bits (Level L = 01, Mask 0 = 000 -> format word 0x77C4)
        val formatBits = 0x77C4
        for (i in 0..5) setFn(8, i, ((formatBits ushr (14 - i)) and 1) != 0)
        setFn(8, 7, ((formatBits ushr 8) and 1) != 0)
        setFn(8, 8, ((formatBits ushr 7) and 1) != 0)
        setFn(7, 8, ((formatBits ushr 6) and 1) != 0)
        for (i in 0..5) setFn(5 - i, 8, ((formatBits ushr i) and 1) != 0)

        for (i in 0..6) setFn(n - 1 - i, 8, ((formatBits ushr (14 - i)) and 1) != 0)
        for (i in 0..7) setFn(8, n - 8 + i, ((formatBits ushr (7 - i)) and 1) != 0)

        // Place data bits in zig-zag columns with Mask 0 ((r + c) % 2 == 0)
        var bitIdx = 0
        val totalBits = allCw.size * 8
        var col = n - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--
            for (step in 0 until n) {
                val r = if (upward) n - 1 - step else step
                for (dx in 0..1) {
                    val c = col - dx
                    if (!isFunction[r][c]) {
                        val bit = if (bitIdx < totalBits) {
                            ((allCw[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else false
                        bitIdx++
                        val masked = if ((r + c) % 2 == 0) !bit else bit
                        modules[r][c] = masked
                    }
                }
            }
            upward = !upward
            col -= 2
        }
        return modules
    }
}

@Composable
fun QrCodeCanvas(
    content: String,
    modifier: Modifier = Modifier,
    darkColor: Color = Color(0xFF111318),
    lightColor: Color = Color.White
) {
    val matrix = remember(content) { QrCodeMatrix.encode(content) }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(lightColor)
            .padding(6.dp)
    ) {
        val count = matrix.size
        if (count == 0) return@Canvas
        val cell = minOf(size.width, size.height) / count
        val offsetX = (size.width - cell * count) / 2f
        val offsetY = (size.height - cell * count) / 2f
        for (r in 0 until count) {
            for (c in 0 until count) {
                if (matrix[r][c]) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(offsetX + c * cell, offsetY + r * cell),
                        size = Size(cell + 0.5f, cell + 0.5f)
                    )
                }
            }
        }
    }
}
