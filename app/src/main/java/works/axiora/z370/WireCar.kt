package works.axiora.z370

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.SystemClock
import android.view.MotionEvent
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/*
 * The live wireframe 370Z.
 *
 * Technique and the TextureView/GL-thread host are adapted from Celica Dash by Kade
 * (github.com/Kaito-Yagami/celica-dash, MIT licence): the solid mesh writes depth only, so lines
 * on the far side of the car are hidden, then the feature edges are drawn over it.
 * Ours adds: doors and boot that swing open from the CAN data and turn the accent colour, and a
 * light-trace reveal from tail to nose on every wake, matching the boot animation.
 *
 * Model: "2020 Nissan 370Z Sport Touring" by Ddiaz Design, CC BY-NC-SA 4.0, decimated and packed
 * into assets/wire/car.{json,bin} (x to the nose, y up, z across, metres; uint16 quantised).
 */

class WireModel(
    val positions: FloatBuffer,
    val faces: ShortBuffer,
    val edges: ShortBuffer,
    val parts: List<Part>,
    val minX: Float, val maxX: Float, val height: Float, val minZ: Float, val maxZ: Float,
) {
    class Part(
        val name: String,
        val faceStart: Int, val faceCount: Int,
        val edgeStart: Int, val edgeCount: Int,
        val pivot: FloatArray?,      // wheel axle centre
        val radius: Float,
        val hinge: FloatArray?,      // door / boot hinge point
        val axis: FloatArray,
        val openDeg: Float,
        val lamp: String?,           // "front" / "rear" lamp glass, filled when the lights are on
    )

    companion object {
        @Volatile private var cached: WireModel? = null

        fun get(ctx: Context): WireModel = cached ?: synchronized(this) { cached ?: load(ctx).also { cached = it } }

        private fun load(ctx: Context): WireModel {
            val meta = JSONObject(ctx.assets.open("wire/car.json").bufferedReader().readText())
            val bin = ctx.assets.open("wire/car.bin").readBytes()
            val c = meta.getJSONObject("counts")
            val nPos = c.getInt("pos"); val nIdx = c.getInt("idx"); val nEdg = c.getInt("edg")
            val u16 = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val mn = meta.getJSONArray("min"); val sp = meta.getJSONArray("span")
            val raw = FloatArray(nPos)
            for (i in 0 until nPos) {
                val k = i % 3
                raw[i] = (mn.getDouble(k) + (u16.get(i).toInt() and 0xFFFF) / 65535.0 * sp.getDouble(k)).toFloat()
            }
            val pos = ByteBuffer.allocateDirect(nPos * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(raw).also { it.position(0) }
            fun slice(off: Int, n: Int): ShortBuffer {
                val b = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
                for (k in 0 until n) b.put(u16.get(off + k))
                b.position(0); return b
            }
            fun arr(o: JSONObject, key: String) = o.optJSONArray(key)?.let { a -> FloatArray(a.length()) { a.getDouble(it).toFloat() } }
            val pj = meta.getJSONArray("parts")
            val parts = (0 until pj.length()).map { i ->
                val p = pj.getJSONObject(i)
                Part(
                    p.getString("name"), p.getInt("faceStart"), p.getInt("faceCount"), p.getInt("edgeStart"), p.getInt("edgeCount"),
                    arr(p, "pivot"), p.optDouble("radius", 0.34).toFloat(), arr(p, "hinge"),
                    arr(p, "axis") ?: floatArrayOf(0f, 0f, 1f), p.optDouble("open", 0.0).toFloat(),
                    p.optString("lamp").ifEmpty { null },
                )
            }
            val minX = mn.getDouble(0).toFloat(); val minZ = mn.getDouble(2).toFloat()
            return WireModel(
                pos, slice(nPos, nIdx), slice(nPos + nIdx, nEdg), parts,
                minX, minX + sp.getDouble(0).toFloat(), sp.getDouble(1).toFloat(), minZ, minZ + sp.getDouble(2).toFloat(),
            )
        }
    }
}

class WireRenderer(private val model: WireModel, private val onNeedsFrame: () -> Unit) {
    // ---- written from the UI thread
    @Volatile var speedKmh = 0f
    @Volatile var lineColor = floatArrayOf(0.90f, 0.91f, 0.93f, 0.88f)
    @Volatile var accent = floatArrayOf(1f, 0.35f, 0.12f, 1f)
    /** Which hinged parts are open: door_FL (driver), door_FR (passenger), trunk. */
    @Volatile var open: Set<String> = emptySet()
    /** Headlights on (the firmware's night switch): lamps fill and glow. */
    @Volatile var lights = false
    @Volatile var reverse = false
    /** Steering-wheel angle, degrees, positive = left; the front wheels turn by it over the rack ratio. */
    @Volatile var steeringDeg = 0f
    @Volatile private var revealStart = 0L

    fun replay() { revealStart = SystemClock.uptimeMillis(); onNeedsFrame() }

    // ---- camera: front three-quarter, nose to the left, a little above
    private val homeAz = 2.35f
    private val homeEl = 0.20f
    @Volatile private var targetAz = homeAz
    @Volatile private var targetEl = homeEl
    @Volatile private var settleAt = 0L
    private var az = homeAz
    private var el = homeEl
    private var dist = 9f
    private var aspect = 1.4f
    private val fov = 26f

    fun drag(dx: Float, dy: Float) {
        targetAz -= dx * 0.007f
        targetEl = (targetEl + dy * 0.004f).coerceIn(-0.05f, 1.1f)
        settleAt = 0L; onNeedsFrame()
    }
    fun release() { settleAt = SystemClock.uptimeMillis() + 3000; onNeedsFrame() }

    private val proj = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private val mvp = FloatArray(16); private val modelM = FloatArray(16)
    private var program = 0; private var aPos = 0; private var uMvp = 0; private var uModel = 0
    private var uColor = 0; private var uGlow = 0; private var uEdge = 0
    private val vbo = IntArray(3)
    private var wheelAngle = 0f
    private val openNow = HashMap<String, Float>()
    private var lightNow = 0f
    private var reverseNow = 0f
    private var steerNow = 0f
    private var lastT = 0L

    fun onSurfaceCreated() {
        program = link(VS, FS)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uGlow = GLES20.glGetUniformLocation(program, "uGlow")
        uEdge = GLES20.glGetUniformLocation(program, "uEdge")
        GLES20.glGenBuffers(3, vbo, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, model.positions.capacity() * 4, model.positions, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vbo[1])
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, model.faces.capacity() * 2, model.faces, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vbo[2])
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, model.edges.capacity() * 2, model.edges, GLES20.GL_STATIC_DRAW)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        // Hairlines vanish on a 1080-wide panel; most mobile GPUs draw 2 px lines.
        val range = FloatArray(2); GLES20.glGetFloatv(GLES20.GL_ALIASED_LINE_WIDTH_RANGE, range, 0)
        GLES20.glLineWidth(minOf(2f, max(1f, range[1])))
        lastT = SystemClock.uptimeMillis()
    }

    fun onSurfaceChanged(w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        aspect = w.toFloat() / max(1, h)
        Matrix.perspectiveM(proj, 0, fov, aspect, 0.3f, 100f)
        fit()
    }

    /** Camera distance that just fits the car's box at the home angle, with a margin. */
    private fun fit() {
        val ty = tan(Math.toRadians(fov / 2.0)).toFloat(); val tx = ty * aspect
        val target = floatArrayOf(0f, model.height * 0.42f, 0f)
        val d = floatArrayOf(sin(homeAz) * cos(homeEl), sin(homeEl), cos(homeAz) * cos(homeEl))
        val right = norm(floatArrayOf(d[2], 0f, -d[0]))
        val up = floatArrayOf(d[1] * right[2] - d[2] * right[1], d[2] * right[0] - d[0] * right[2], d[0] * right[1] - d[1] * right[0])
        var worst = 0f
        for (cx in 0..1) for (cy in 0..1) for (cz in 0..1) {
            // corners pulled in a little: a car's bounding box corners are empty air
            val k = 0.92f
            val c = floatArrayOf((if (cx == 1) model.maxX else model.minX) * k, (if (cy == 1) model.height else 0f) - target[1], (if (cz == 1) model.maxZ else model.minZ) * k)
            val depth = c[0] * d[0] + c[1] * d[1] + c[2] * d[2]
            val sx = abs(c[0] * right[0] + c[1] * right[1] + c[2] * right[2]) / (tx * 0.84f)
            val sy = abs(c[0] * up[0] + c[1] * up[1] + c[2] * up[2]) / (ty * 0.84f)
            worst = max(worst, max(sx, sy) + depth)
        }
        dist = worst
    }

    private fun norm(v: FloatArray): FloatArray { val l = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).coerceAtLeast(1e-6f); return floatArrayOf(v[0] / l, v[1] / l, v[2] / l) }

    fun onDrawFrame() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastT).coerceAtMost(50)) / 1000f
        lastT = now
        var animating = false

        if (settleAt != 0L && now > settleAt) { targetAz = homeAz; targetEl = homeEl; settleAt = 0L }
        az += (targetAz - az) * 0.12f; el += (targetEl - el) * 0.12f
        if (abs(targetAz - az) > 1e-4f || abs(targetEl - el) > 1e-4f || settleAt != 0L) animating = true

        val mps = speedKmh / 3.6f
        if (mps > 0.3f) { wheelAngle -= mps / 0.34f * dt; animating = true }

        // Doors and boot ease toward their state: ~0.45 s to swing fully.
        val want = open
        for (p in model.parts) {
            if (p.hinge == null) continue
            val cur = openNow[p.name] ?: 0f
            val goal = if (p.name in want) 1f else 0f
            val next = if (goal > cur) minOf(goal, cur + dt / 0.45f) else max(goal, cur - dt / 0.45f)
            openNow[p.name] = next
            if (next != goal) animating = true
        }

        // Lights fade in and out like real bulbs; front wheels follow the steering (rack ~15:1).
        fun ease(cur: Float, goal: Float, rate: Float): Float {
            val n = if (goal > cur) minOf(goal, cur + dt * rate) else max(goal, cur - dt * rate)
            if (n != goal) animating = true
            return n
        }
        lightNow = ease(lightNow, if (lights) 1f else 0f, 3f)
        reverseNow = ease(reverseNow, if (reverse) 1f else 0f, 4f)
        val steerGoal = (steeringDeg / 15f).coerceIn(-34f, 34f)
        steerNow += (steerGoal - steerNow) * 0.25f
        if (abs(steerGoal - steerNow) > 0.05f) animating = true

        // Light-trace reveal: an edge of light runs from tail to nose and the drawing appears behind it.
        val rt = (now - revealStart) / 1000f
        val sweep = 1.6f
        val edgeX: Float
        val glowA: Float
        if (revealStart != 0L && rt < sweep + 0.6f) {
            val f = (rt / sweep).coerceIn(0f, 1f)
            val e = f * f * (3 - 2 * f)
            edgeX = model.minX - 0.4f + (model.maxX - model.minX + 0.8f) * e
            glowA = if (rt < sweep) 1f else 1f - (rt - sweep) / 0.6f
            animating = true
        } else { edgeX = 99f; glowA = 0f }

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val target = floatArrayOf(0f, model.height * 0.42f, 0f)
        val d = floatArrayOf(sin(az) * cos(el), sin(el), cos(az) * cos(el))
        Matrix.setLookAtM(view, 0, target[0] + d[0] * dist, target[1] + d[1] * dist, target[2] + d[2] * dist, target[0], target[1], target[2], 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        GLES20.glUseProgram(program)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, 0)
        val lc = lineColor; val ac = accent
        GLES20.glUniform4f(uGlow, ac[0], ac[1], ac[2], glowA)

        for (p in model.parts) {
            Matrix.setIdentityM(modelM, 0)
            p.pivot?.let { pv ->
                Matrix.translateM(modelM, 0, pv[0], pv[1], pv[2])
                if (p.name.startsWith("wheel_F")) Matrix.rotateM(modelM, 0, steerNow, 0f, 1f, 0f)
                Matrix.rotateM(modelM, 0, Math.toDegrees(wheelAngle.toDouble()).toFloat(), 0f, 0f, 1f)
                Matrix.translateM(modelM, 0, -pv[0], -pv[1], -pv[2])
            }
            val o = openNow[p.name] ?: 0f
            p.hinge?.let { h ->
                if (o > 0f) {
                    val e = o * o * (3 - 2 * o)
                    Matrix.translateM(modelM, 0, h[0], h[1], h[2])
                    Matrix.rotateM(modelM, 0, p.openDeg * e, p.axis[0], p.axis[1], p.axis[2])
                    Matrix.translateM(modelM, 0, -h[0], -h[1], -h[2])
                }
            }
            Matrix.multiplyMM(mvp, 0, vp, 0, modelM, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES20.glUniformMatrix4fv(uModel, 1, false, modelM, 0)

            // Occluder: depth only, nothing drawn, so the card shows through the car.
            GLES20.glColorMask(false, false, false, false)
            GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
            GLES20.glPolygonOffset(1.4f, 1.4f)
            GLES20.glUniform1f(uEdge, 99f)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vbo[1])
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, p.faceCount * 3, GLES20.GL_UNSIGNED_SHORT, p.faceStart * 3 * 2)
            GLES20.glColorMask(true, true, true, true)

            // Lamp glass, lit: a soft additive fill. Tail lamps red, headlamps warm white; reversing whitens the rear.
            val lit = if (p.lamp == "rear") max(lightNow, reverseNow) else lightNow
            if (p.lamp != null && lit > 0.01f) {
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
                GLES20.glDepthMask(false)
                if (p.lamp == "front") GLES20.glUniform4f(uColor, 1f, 0.93f, 0.82f, 0.75f * lit)
                else {
                    val w = reverseNow
                    GLES20.glUniform4f(uColor, 1f, 0.16f + 0.8f * w, 0.08f + 0.8f * w, 0.85f * lit)
                }
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, p.faceCount * 3, GLES20.GL_UNSIGNED_SHORT, p.faceStart * 3 * 2)
                GLES20.glDepthMask(true)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
            GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)

            // Lines: an open door or boot is drawn in the accent.
            if (p.lamp != null && (lightNow > 0.01f || reverseNow > 0.01f)) {
                val k = if (p.lamp == "rear") max(lightNow, reverseNow) else lightNow
                val tr = if (p.lamp == "front" || reverseNow > 0.5f) floatArrayOf(1f, 0.97f, 0.9f) else floatArrayOf(1f, 0.42f, 0.32f)
                GLES20.glUniform4f(uColor, lc[0] + (tr[0] - lc[0]) * k, lc[1] + (tr[1] - lc[1]) * k, lc[2] + (tr[2] - lc[2]) * k, 1f)
            } else if (p.hinge != null && o > 0f) {
                val k = o.coerceIn(0f, 1f)
                GLES20.glUniform4f(uColor, lc[0] + (ac[0] - lc[0]) * k, lc[1] + (ac[1] - lc[1]) * k, lc[2] + (ac[2] - lc[2]) * k, 1f)
            } else GLES20.glUniform4f(uColor, lc[0], lc[1], lc[2], lc[3])
            GLES20.glUniform1f(uEdge, edgeX)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, vbo[2])
            GLES20.glDrawElements(GLES20.GL_LINES, p.edgeCount * 2, GLES20.GL_UNSIGNED_SHORT, p.edgeStart * 2 * 2)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        if (animating) onNeedsFrame()
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
            val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        private const val VS = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPos;
            varying float vX;
            void main() { vX = (uModel * vec4(aPos, 1.0)).x; gl_Position = uMvp * vec4(aPos, 1.0); }
        """
        // Ahead of the light edge nothing is drawn; at the edge the line glows in the accent.
        private const val FS = """
            precision mediump float;
            uniform vec4 uColor;
            uniform vec4 uGlow;
            uniform float uEdge;
            varying float vX;
            void main() {
                float d = uEdge - vX;
                if (d < 0.0) discard;
                float g = exp(-d * d * 18.0) * uGlow.a;
                gl_FragColor = vec4(mix(uColor.rgb, vec3(1.0, 0.86, 0.72), g * 0.6) + uGlow.rgb * g * 0.5, max(uColor.a, g));
            }
        """
    }
}

/** A TextureView with its own GL thread: an ordinary, translucent view (a SurfaceView punched a black hole on this unit). */
@SuppressLint("ViewConstructor")
class WireSurface(ctx: Context, model: WireModel, private val onTap: () -> Unit) : TextureView(ctx), TextureView.SurfaceTextureListener {
    val renderer = WireRenderer(model) { requestRender() }
    private var thread: GlThread? = null
    private var lastX = 0f; private var lastY = 0f; private var moved = 0f

    init { isOpaque = false; surfaceTextureListener = this }

    fun requestRender() { thread?.requestRender() }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        thread = GlThread(st, renderer, width, height).also { it.start() }
    }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) { thread?.resize(width, height) }
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { thread?.finish(); thread = null; return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); if (visibility == VISIBLE) requestRender() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (hasWindowFocus) requestRender() }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; lastY = e.y; moved = 0f; parent?.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> {
                moved += abs(e.x - lastX) + abs(e.y - lastY)
                renderer.drag(e.x - lastX, e.y - lastY); lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> { renderer.release(); if (moved < 24f) onTap() }
            MotionEvent.ACTION_CANCEL -> renderer.release()
        }
        return true
    }
}

private class GlThread(private val texture: SurfaceTexture, private val renderer: WireRenderer, private var w: Int, private var h: Int) : Thread("wire-gl") {
    private val lock = Object()
    private var wantFrame = true
    private var sizeChanged = true
    private var done = false

    fun requestRender() = synchronized(lock) { wantFrame = true; lock.notifyAll() }
    fun resize(width: Int, height: Int) = synchronized(lock) { w = width; h = height; sizeChanged = true; wantFrame = true; lock.notifyAll() }
    fun finish() { synchronized(lock) { done = true; lock.notifyAll() }; runCatching { join(1500) } }

    override fun run() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val ver = IntArray(2)
        if (!EGL14.eglInitialize(display, ver, 0, ver, 1)) return
        val config = chooseConfig(display) ?: return
        val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surface = EGL14.eglCreateWindowSurface(display, config, texture, intArrayOf(EGL14.EGL_NONE), 0)
        if (context == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) {
            tearDown(display, surface, context); return
        }
        try {
            renderer.onSurfaceCreated()
            while (true) {
                var resize = false
                val stop = synchronized(lock) {
                    while (!done && !wantFrame && !sizeChanged) lock.wait()
                    if (!done) { resize = sizeChanged; sizeChanged = false; wantFrame = false }
                    done
                }
                if (stop) break
                if (resize) renderer.onSurfaceChanged(w, h)
                renderer.onDrawFrame()
                if (!EGL14.eglSwapBuffers(display, surface)) break
            }
        } catch (_: Throwable) {
        } finally {
            tearDown(display, surface, context)
        }
    }

    private fun chooseConfig(display: EGLDisplay): EGLConfig? {
        val base = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
        )
        for (attrs in listOf(base + intArrayOf(EGL14.EGL_SAMPLE_BUFFERS, 1, EGL14.EGL_SAMPLES, 4, EGL14.EGL_NONE), base + intArrayOf(EGL14.EGL_NONE))) {
            val out = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
            if (EGL14.eglChooseConfig(display, attrs, 0, out, 0, 1, n, 0) && n[0] > 0) return out[0]
        }
        return null
    }

    private fun tearDown(display: EGLDisplay, surface: EGLSurface?, context: EGLContext?) {
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
        if (context != null && context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        EGL14.eglTerminate(display)
    }
}

/**
 * The car in Compose: live doors and boot from the CAN box, wheels turning with GPS speed, drag to look
 * round it (it settles back), and the light-trace draw-in on start and every screen wake.
 */
@Composable
fun WireCar(car: CarState, speedKmh: () -> Int, accent: Color, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var model by remember { mutableStateOf<WireModel?>(null) }
    LaunchedEffect(Unit) { model = withContext(Dispatchers.IO) { runCatching { WireModel.get(ctx) }.getOrNull() } }
    val wake = remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: android.content.Intent?) { wake.intValue++ }
        }
        ctx.registerReceiver(rx, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON))
        onDispose { try { ctx.unregisterReceiver(rx) } catch (_: Exception) {} }
    }
    val m = model ?: return
    val open = buildSet {
        if (car.driverDoor.value) add("door_FL")
        if (car.passengerDoor.value) add("door_FR")
        if (car.boot.value) add("trunk")
    }
    val kmh = speedKmh()
    val w = wake.intValue
    val lights = car.night.value
    val reverse = car.reverse.value
    val steer = car.steeringDeg.value ?: 0f
    AndroidView(
        factory = { c -> WireSurface(c, m, onTap).apply { tag = -1 } },
        modifier = modifier,
        update = { v ->
            val r = v.renderer
            r.accent = floatArrayOf(accent.red, accent.green, accent.blue, 1f)
            var changed = false
            if (r.open != open) { r.open = open; changed = true }
            if (r.speedKmh != kmh.toFloat()) { r.speedKmh = kmh.toFloat(); changed = true }
            if (r.lights != lights) { r.lights = lights; changed = true }
            if (r.reverse != reverse) { r.reverse = reverse; changed = true }
            if (r.steeringDeg != steer) { r.steeringDeg = steer; changed = true }
            if (v.tag != w) { v.tag = w; r.replay() }
            if (changed) v.requestRender()
        },
    )
}
