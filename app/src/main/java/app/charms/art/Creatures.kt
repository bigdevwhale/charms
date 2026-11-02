package app.charms.art

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import app.charms.domain.Creature

/**
 * The grimoire's creatures as engraved silhouettes, drawn in a box of [s] centred on (cx, cy):
 * [ink] for the body, [bg] for the eyes and the cuts in it.
 */
object Creatures {
    fun draw(c: Canvas, who: Creature, cx: Float, cy: Float, s: Float, ink: Int, bg: Int) {
        val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.FILL }
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.STROKE; strokeWidth = s * .045f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.FILL }
        val cutS = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg; style = Paint.Style.STROKE; strokeWidth = s * .025f; strokeCap = Paint.Cap.ROUND }
        c.save()
        c.translate(cx - s / 2, cy - s / 2)
        val u = s // unit: the box
        fun x(v: Float) = v * u
        fun oval(l: Float, t: Float, r: Float, b: Float, p: Paint) = c.drawOval(RectF(x(l), x(t), x(r), x(b)), p)
        fun circle(px: Float, py: Float, r: Float, p: Paint) = c.drawCircle(x(px), x(py), x(r), p)
        fun poly(p: Paint, vararg v: Float) {
            val path = Path(); path.moveTo(x(v[0]), x(v[1]))
            var i = 2; while (i < v.size) { path.lineTo(x(v[i]), x(v[i + 1])); i += 2 }
            path.close(); c.drawPath(path, p)
        }
        when (who) {
            Creature.OWL -> {
                oval(.22f, .3f, .78f, .95f, f)
                poly(f, .26f, .38f, .3f, .12f, .44f, .3f)
                poly(f, .74f, .38f, .7f, .12f, .56f, .3f)
                circle(.39f, .45f, .1f, cut); circle(.61f, .45f, .1f, cut)
                circle(.39f, .45f, .045f, f); circle(.61f, .45f, .045f, f)
                poly(cut, .46f, .55f, .54f, .55f, .5f, .64f)
                // breast feathers
                for (r in 0..2) for (k in 0..(2 - r % 2)) circle(.4f + k * .1f + (r % 2) * .05f, .7f + r * .07f, .025f, cut)
                st.color = ink; c.drawLine(x(.12f), x(.96f), x(.88f), x(.96f), st)
            }
            Creature.CAT -> {
                oval(.25f, .45f, .75f, .97f, f)
                circle(.5f, .36f, .19f, f)
                poly(f, .33f, .3f, .34f, .08f, .47f, .2f)
                poly(f, .67f, .3f, .66f, .08f, .53f, .2f)
                val tail = Path().apply { moveTo(x(.7f), x(.9f)); cubicTo(x(.98f), x(.9f), x(.98f), x(.6f), x(.86f), x(.5f)) }
                st.strokeWidth = s * .07f; c.drawPath(tail, st)
                // sleepy eyes: Bayun sings, he doesn't stare
                cutS.strokeWidth = s * .02f
                c.drawArc(RectF(x(.36f), x(.31f), x(.47f), x(.39f)), 10f, 160f, false, cutS)
                c.drawArc(RectF(x(.53f), x(.31f), x(.64f), x(.39f)), 10f, 160f, false, cutS)
                poly(cut, .48f, .43f, .52f, .43f, .5f, .46f)
            }
            Creature.FOX -> {
                // tail first, curled round the feet
                val tail = Path().apply {
                    moveTo(x(.6f), x(.95f)); cubicTo(x(1f), x(.95f), x(1f), x(.45f), x(.8f), x(.4f))
                    cubicTo(x(.88f), x(.6f), x(.82f), x(.82f), x(.6f), x(.86f)); close()
                }
                c.drawPath(tail, f)
                oval(.28f, .48f, .68f, .97f, f)
                poly(f, .22f, .26f, .48f, .62f, .74f, .26f)
                poly(f, .24f, .28f, .26f, .05f, .4f, .22f)
                poly(f, .72f, .28f, .7f, .05f, .56f, .22f)
                circle(.4f, .36f, .03f, cut); circle(.56f, .36f, .03f, cut)
                poly(cut, .44f, .58f, .48f, .62f, .52f, .58f, .48f, .66f)
                poly(cut, .86f, .44f, .93f, .52f, .84f, .5f)
            }
            Creature.HARE -> {
                // the moon behind
                val moon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 70; style = Paint.Style.FILL }
                circle(.62f, .32f, .3f, moon)
                oval(.22f, .55f, .72f, .97f, f)
                circle(.44f, .48f, .15f, f)
                oval(.3f, .02f, .4f, .42f, f)
                oval(.43f, .04f, .53f, .42f, f)
                circle(.4f, .46f, .025f, cut)
                circle(.74f, .82f, .07f, f)
                cutS.strokeWidth = s * .015f
                c.drawLine(x(.35f), x(.08f), x(.35f), x(.36f), cutS); c.drawLine(x(.48f), x(.1f), x(.48f), x(.36f), cutS)
            }
            Creature.RAVEN -> {
                val body = Path().apply {
                    moveTo(x(.24f), x(.36f)); cubicTo(x(.5f), x(.3f), x(.74f), x(.48f), x(.8f), x(.7f))
                    lineTo(x(.98f), x(.86f)); lineTo(x(.72f), x(.8f)); cubicTo(x(.5f), x(.82f), x(.3f), x(.7f), x(.24f), x(.5f)); close()
                }
                c.drawPath(body, f)
                circle(.26f, .38f, .13f, f)
                poly(f, .16f, .34f, .0f, .42f, .16f, .44f)
                circle(.24f, .35f, .025f, cut)
                st.strokeWidth = s * .03f
                c.drawLine(x(.48f), x(.78f), x(.44f), x(.96f), st); c.drawLine(x(.56f), x(.8f), x(.56f), x(.96f), st)
                cutS.strokeWidth = s * .018f
                for (k in 0..2) c.drawLine(x(.42f + k * .09f), x(.48f + k * .05f), x(.6f + k * .07f), x(.62f + k * .05f), cutS)
            }
            Creature.HEDGEHOG -> {
                val spikes = Path().apply {
                    moveTo(x(.1f), x(.86f))
                    val n = 11
                    for (k in 0..n) {
                        val a = Math.PI * (1 - k / n.toDouble())
                        val r = if (k % 2 == 0) .44f else .34f
                        lineTo(x(.52f + r * Math.cos(a).toFloat()), x(.86f - r * Math.sin(a).toFloat() * 1.05f))
                    }
                    lineTo(x(.94f), x(.86f)); close()
                }
                c.drawPath(spikes, f)
                val snout = Path().apply { moveTo(x(.22f), x(.6f)); lineTo(x(.02f), x(.8f)); lineTo(x(.22f), x(.88f)); close() }
                c.drawPath(snout, f)
                circle(.03f, .8f, .03f, f)
                circle(.17f, .72f, .025f, cut)
                oval(.2f, .84f, .34f, .94f, f); oval(.62f, .84f, .76f, .94f, f)
                // an apple on the back
                circle(.55f, .4f, .09f, cut); circle(.55f, .41f, .06f, f)
            }
            Creature.SNAIL -> {
                val foot = Path().apply {
                    moveTo(x(.05f), x(.9f)); lineTo(x(.9f), x(.9f)); cubicTo(x(.98f), x(.9f), x(.98f), x(.78f), x(.86f), x(.76f))
                    lineTo(x(.86f), x(.6f)); lineTo(x(.8f), x(.6f)); lineTo(x(.8f), x(.8f)); lineTo(x(.1f), x(.82f)); close()
                }
                c.drawPath(foot, f)
                st.strokeWidth = s * .025f
                c.drawLine(x(.83f), x(.62f), x(.78f), x(.38f), st); c.drawLine(x(.86f), x(.62f), x(.94f), x(.4f), st)
                circle(.78f, .37f, .025f, f); circle(.94f, .39f, .025f, f)
                circle(.45f, .55f, .3f, f)
                val sp = Path()
                for (i in 0..60) {
                    val t = i / 60f; val a = t * 2.6 * 2 * Math.PI
                    val px = .45f + .26f * t * Math.cos(a).toFloat(); val py = .55f + .26f * t * Math.sin(a).toFloat()
                    if (i == 0) sp.moveTo(x(px), x(py)) else sp.lineTo(x(px), x(py))
                }
                cutS.strokeWidth = s * .03f; c.drawPath(sp, cutS)
            }
            Creature.FIREFLY -> {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(x(.5f), x(.72f), x(.42f), intArrayOf(ink, ink and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                }
                circle(.5f, .72f, .42f, glow)
                val wing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 120 }
                c.save(); c.rotate(-30f, x(.4f), x(.35f)); oval(.12f, .22f, .46f, .42f, wing); c.restore()
                c.save(); c.rotate(30f, x(.6f), x(.35f)); oval(.54f, .22f, .88f, .42f, wing); c.restore()
                circle(.5f, .26f, .08f, f)
                oval(.42f, .3f, .58f, .56f, f)
                circle(.5f, .7f, .13f, f)
                st.strokeWidth = s * .02f
                c.drawLine(x(.46f), x(.2f), x(.38f), x(.06f), st); c.drawLine(x(.54f), x(.2f), x(.62f), x(.06f), st)
            }
        }
        c.restore()
    }
}
