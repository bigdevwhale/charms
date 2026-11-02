package app.charms.art

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.charms.domain.Creature
import app.charms.domain.Tier
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Turns a camera picture into a page of the grimoire: the picture engraved in hatching lines of
 * gold (or silver, or ink) on dark parchment, in a frame with the deed's name, sigil and patron.
 *
 * Light becomes metal: bright parts of the picture are drawn with thick, crossed lines, dark parts
 * are left as parchment, and the picture's edges are cut as fine outlines.
 */
object Engraver {
    const val W = 1080
    const val H = 1440

    class Spec(
        val title: String,
        val date: String,
        val tierLabel: String,
        val tier: Tier,
        val sigil: Int,
        val patron: Creature,
        val visitor: Creature?,
        val visitorLabel: String = "",
    )

    class Fonts(val title: Typeface, val italic: Typeface)

    /** Metal colours: dark, light, and the frame's. */
    class Metal(val lo: Int, val hi: Int, val frame: Int)

    fun metal(t: Tier) = when (t) {
        Tier.GOLD -> Metal(0xFF8A5A1C.toInt(), 0xFFFFE9A8.toInt(), 0xFFE8C26A.toInt())
        Tier.SILVER -> Metal(0xFF5C6676.toInt(), 0xFFF4F7FC.toInt(), 0xFFC9D2E0.toInt())
        Tier.INK -> Metal(0xFF655C4E.toInt(), 0xFFD6CAAE.toInt(), 0xFFA89D86.toInt())
    }

    private const val BG_IN = 0xFF1C1534.toInt()
    private const val BG_OUT = 0xFF0D0A1A.toInt()

    // the picture's place on the page
    private const val AX = 84
    private const val AY = 196
    private const val AW = W - 2 * AX
    private const val AH = 1060

    fun engrave(photo: Bitmap, spec: Spec, fonts: Fonts): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val m = metal(spec.tier)
        parchment(c)
        val art = hatch(cropTo(photo, AW, AH), m)
        c.drawBitmap(art, AX.toFloat(), AY.toFloat(), null)
        art.recycle()
        frame(c, spec, m, fonts)
        return out
    }

    /** The picture filling [w]×[h], cut from its middle. */
    private fun cropTo(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val k = max(w / src.width.toFloat(), h / src.height.toFloat())
        val mx = Matrix().apply { postScale(k, k); postTranslate((w - src.width * k) / 2, (h - src.height * k) / 2) }
        Canvas(out).drawBitmap(src, mx, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun parchment(c: Canvas) {
        val p = Paint().apply { shader = RadialGradient(W / 2f, H * .45f, H * .75f, BG_IN, BG_OUT, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        // grain and a few motes of old gold dust
        val r = Random(7)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(9000) {
            dot.color = if (r.nextInt(30) == 0) 0x22E8C26A else if (r.nextBoolean()) 0x0EFFFFFF else 0x14000000
            c.drawCircle(r.nextFloat() * W, r.nextFloat() * H, .6f + r.nextFloat() * 1.1f, dot)
        }
    }

    // ---------------- the engraving ----------------

    private fun hatch(src: Bitmap, m: Metal): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        src.recycle()
        val n = w * h
        var lum = FloatArray(n) { i ->
            val p = px[i]
            (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)) / 255f
        }
        lum = blur(blur(lum, w, h, 2), w, h, 1)
        stretch(lum)
        val local = blur(lum, w, h, 16)
        val v = FloatArray(n) { i -> (lum[i] + .75f * (lum[i] - local[i])).coerceIn(0f, 1f).pow(.9f) }

        // outlines from the picture's edges
        val mag = FloatArray(n)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = lum[i - w + 1] + 2 * lum[i + 1] + lum[i + w + 1] - lum[i - w - 1] - 2 * lum[i - 1] - lum[i + w - 1]
            val gy = lum[i + w - 1] + 2 * lum[i + w] + lum[i + w + 1] - lum[i - w - 1] - 2 * lum[i - w] - lum[i - w + 1]
            mag[i] = sqrt(gx * gx + gy * gy)
        }
        val edgeTop = percentile(mag, .965f).coerceAtLeast(.05f)

        val period = 9.5f
        val aa = 2.4f / period
        val a1 = 32 * PI / 180
        val a2 = -58 * PI / 180
        val c1 = cos(a1).toFloat(); val s1 = sin(a1).toFloat()
        val c2 = cos(a2).toFloat(); val s2 = sin(a2).toFloat()
        val feather = 46f

        val lo = m.lo; val hi = m.hi
        val loR = Color.red(lo); val loG = Color.green(lo); val loB = Color.blue(lo)
        val hiR = Color.red(hi); val hiG = Color.green(hi); val hiB = Color.blue(hi)

        val res = IntArray(n)
        for (y in 0 until h) {
            val ey = min(y, h - 1 - y) / feather
            for (x in 0 until w) {
                val i = y * w + x
                val t = v[i]
                // a hand-cut line wavers a little
                val wav = 1.3f * sin(y * .043f + x * .011f) + .7f * sin(x * .031f - y * .007f)
                val d1 = lineDist((x * c1 + y * s1 + wav) / period)
                var cov = cover(t * .82f, d1, aa)
                if (t > .55f) cov = max(cov, cover((t - .55f) * 1.5f, lineDist((x * c2 + y * s2 - wav) / period), aa))
                if (t > .9f) cov = max(cov, (t - .9f) / .1f * .35f)
                val e = smooth(.5f, 1.1f, mag[i] / edgeTop)
                cov = max(cov, e * .9f)
                cov = max(cov, .05f * t)
                // the engraving fades into the page at its edges
                val ex = min(x, w - 1 - x) / feather
                val mask = smooth(0f, 1f, min(min(ex, ey), 1f))
                val a = (cov * mask).coerceIn(0f, 1f)
                val r = loR + (hiR - loR) * t
                val g = loG + (hiG - loG) * t
                val b = loB + (hiB - loB) * t
                res[i] = Color.argb((a * 255).toInt(), r.toInt(), g.toInt(), b.toInt())
            }
        }
        return Bitmap.createBitmap(res, w, h, Bitmap.Config.ARGB_8888)
    }

    /** 0 on a line's centre, 1 halfway to the next. */
    private fun lineDist(s: Float): Float { val f = s - floor(s); return min(f, 1 - f) * 2 }

    private fun cover(thick: Float, d: Float, aa: Float) = ((thick - d) / aa + .5f).coerceIn(0f, 1f)

    private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3 - 2 * t) }

    private fun percentile(a: FloatArray, q: Float): Float {
        val hist = IntArray(1024)
        var top = 0f
        for (v in a) top = max(top, v)
        if (top <= 0f) return 0f
        for (v in a) hist[((v / top) * 1023).toInt().coerceIn(0, 1023)]++
        val target = (a.size * q).toInt()
        var acc = 0
        for (k in 0 until 1024) { acc += hist[k]; if (acc >= target) return k / 1023f * top }
        return top
    }

    /** Spreads the picture's tones over the whole range, so a dim room still engraves. */
    private fun stretch(a: FloatArray) {
        val lo = percentile(a, .02f)
        val hi = percentile(a, .985f)
        // A blank wall shouldn't have its sensor noise blown up into texture.
        val span = max(hi - lo, .32f)
        for (i in a.indices) a[i] = ((a[i] - lo) / span).coerceIn(0f, 1f)
    }

    private fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val k = 1f / (2 * r + 1)
        for (y in 0 until h) {
            val row = y * w
            var acc = 0f
            for (x in -r..r) acc += src[row + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = acc * k
                acc += src[row + (x + r + 1).coerceAtMost(w - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = acc * k
                acc += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    // ---------------- the frame ----------------

    private fun frame(c: Canvas, spec: Spec, m: Metal, fonts: Fonts) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = m.frame; strokeWidth = 3f }
        val thin = Paint(line).apply { strokeWidth = 1.4f; alpha = 170 }
        c.drawRect(RectF(36f, 36f, W - 36f, H - 36f), line)
        c.drawRect(RectF(50f, 50f, W - 50f, H - 50f), thin)
        for ((x, y) in listOf(36f to 36f, W - 36f to 36f, 36f to H - 36f, W - 36f to H - 36f)) star(c, x, y, 22f, m.frame)
        // dotted rule under the title and above the foot
        val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = m.frame; alpha = 150 }
        for (yy in listOf(174f, 1284f)) {
            var x = 120f
            while (x < W - 120f) { c.drawCircle(x, yy, 2.2f, dots); x += 14f }
            star(c, W / 2f, yy, 11f, m.frame)
        }

        // the deed's name between its sigils
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.title; color = m.hi; textAlign = Paint.Align.CENTER; textSize = 64f
            setShadowLayer(14f, 0f, 0f, (m.frame and 0x00FFFFFF) or 0x66000000)
        }
        var size = 64f
        while (tp.measureText(spec.title) > W - 330 && size > 34f) { size -= 2f; tp.textSize = size }
        c.drawText(spec.title, W / 2f, 128f, tp)
        Sigils.draw(c, spec.sigil, 118f, 112f, 54f, m.frame, 4.5f)
        Sigils.draw(c, spec.sigil, W - 118f, 112f, 54f, m.frame, 4.5f)

        // a creature that came to visit stands in the picture's corner, glowing
        spec.visitor?.let { v ->
            val cx = 250f; val cy = 1110f; val s = 300f
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, s * .75f, intArrayOf((m.frame and 0x00FFFFFF) or 0x77000000, m.frame and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            }
            c.drawCircle(cx, cy, s * .75f, glow)
            Creatures.draw(c, v, cx, cy - 10f, s, m.hi, BG_IN)
        }

        // the foot: date, metal, patron
        val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.italic; color = m.frame; textAlign = Paint.Align.LEFT; textSize = 40f }
        c.drawText(spec.date, 100f, 1334f, ip)
        val tl = Paint(ip).apply { typeface = fonts.title; textSize = 34f; color = m.hi; letterSpacing = .12f }
        val label = spec.tierLabel.uppercase()
        c.drawText(label, 100f, 1374f, tl)
        if (spec.visitorLabel.isNotEmpty()) {
            val vl = Paint(ip).apply { textSize = 30f; alpha = 210 }
            c.drawText(spec.visitorLabel, 100f + tl.measureText(label) + 28f, 1374f, vl)
        }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = m.frame; strokeWidth = 2.5f }
        c.drawCircle(W - 150f, 1346f, 56f, ring)
        c.drawCircle(W - 150f, 1346f, 48f, Paint(ring).apply { strokeWidth = 1.2f; alpha = 150 })
        Creatures.draw(c, spec.patron, W - 150f, 1346f, 70f, m.frame, BG_OUT)

        // a soft sheen across the page, like light on gilt
        if (spec.tier == Tier.GOLD) {
            val sheen = Paint().apply {
                shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), intArrayOf(0x00FFFFFF, 0x10FFF1C1, 0x00FFFFFF), floatArrayOf(.3f, .5f, .7f), Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), sheen)
        }
    }

    /** A four-pointed star. */
    fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        val p = Path().apply {
            moveTo(x, y - r); lineTo(x + r * .22f, y - r * .22f); lineTo(x + r, y); lineTo(x + r * .22f, y + r * .22f)
            lineTo(x, y + r); lineTo(x - r * .22f, y + r * .22f); lineTo(x - r, y); lineTo(x - r * .22f, y - r * .22f); close()
        }
        c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
}
