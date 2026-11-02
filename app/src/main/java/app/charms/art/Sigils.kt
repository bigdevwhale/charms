package app.charms.art

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The deeds' sigils: little one-stroke signs in a unit box, drawn in gold on the pages. */
object Sigils {
    private fun arc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float, n: Int) =
        (0..n).map { i -> val a = (a0 + (a1 - a0) * i / n) * PI / 180; floatArrayOf(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()) }

    private fun pts(vararg p: Float) = p.toList().chunked(2).map { floatArrayOf(it[0], it[1]) }

    val all: List<List<FloatArray>> = listOf(
        // spark: teeth
        pts(.62f, 0f, .2f, .52f, .8f, .48f, .38f, 1f),
        // ring: washing
        arc(.5f, .5f, .5f, -90f, 270f, 40),
        // mountain: the bed
        pts(0f, 1f, .5f, 0f, 1f, 1f),
        // bowl: a meal
        pts(0f, 0f, .5f, 1f, 1f, 0f),
        // scroll: lessons, reading
        (0..80).map { i -> val t = i / 80f; val a = t * 2.25 * 2 * PI; floatArrayOf(.5f + .5f * t * cos(a).toFloat(), .5f + .5f * t * sin(a).toFloat()) },
        // star: tidying
        (0..5).map { k -> val a = (-90 + 144 * k) * PI / 180; floatArrayOf(.5f + .5f * cos(a).toFloat(), .5f + .5f * sin(a).toFloat()) },
        // key: the school bag
        pts(.75f, 0f, .75f, .65f) + arc(.45f, .65f, .3f, 0f, 180f, 14),
        // moon: sleep
        arc(.5f, .5f, .5f, -60f, -300f, 34) + arc(.95f, .5f, .477f, 114.8f, 245.2f, 20).drop(1),
        // wave: water
        (0..48).map { i -> val x = i / 48f; floatArrayOf(x, .5f - .25f * sin(x * 4 * PI).toFloat()) },
        // loop: going out
        (0..80).map { i -> val t = i / 80.0 * 2 * PI; val d = 1 + sin(t) * sin(t); floatArrayOf(.5f + .5f * (cos(t) / d).toFloat(), .5f + .5f * (sin(t) * cos(t) / d).toFloat()) },
        // tree: chores outside, plants
        pts(.5f, 1f, .5f, .2f, .2f, .5f, .5f, .2f, .8f, .5f, .5f, .2f, .5f, 0f),
        // eye: practice, music, focus
        arc(.5f, 1.1f, .75f, -128f, -52f, 16) + arc(.5f, -.1f, .75f, 52f, 128f, 16) + arc(.5f, .5f, .12f, 180f, 540f, 16),
    )

    fun path(i: Int, x: Float, y: Float, size: Float): Path {
        val p = Path()
        all.getOrElse(i) { all[0] }.forEachIndexed { k, q ->
            if (k == 0) p.moveTo(x + q[0] * size, y + q[1] * size) else p.lineTo(x + q[0] * size, y + q[1] * size)
        }
        return p
    }

    fun draw(c: Canvas, i: Int, cx: Float, cy: Float, size: Float, color: Int, width: Float = size * .09f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = color
        }
        c.drawPath(path(i, cx - size / 2, cy - size / 2, size), paint)
    }
}
