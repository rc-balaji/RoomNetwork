package com.laconfianza.roommapper.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Surface
import androidx.core.content.ContextCompat
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.hypot
import kotlin.math.min
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Small immutable snapshot emitted by the GL renderer for the Compose HUD.
 * The camera and tracking work stays in the renderer; only low-rate telemetry
 * crosses back to the UI thread.
 */
data class ArFrameState(
    val trackingLabel: String = "SEARCHING",
    val trackingReady: Boolean = false,
    val phaseLabel: String = "INITIALISING SPATIAL MAP",
    val pointCount: Int = 0,
    val nodeCount: Int = 0,
    val pathMeters: Float = 0f,
    val distanceFromOriginMeters: Float = 0f,
    val qualityPercent: Int = 0,
    val errorMessage: String? = null,
    val frameTimeNanos: Long = 0L
)

data class ArNodePlacement(val normalizedX: Float, val normalizedY: Float)

/**
 * ARCore-backed live camera surface. It renders the real camera texture,
 * sparse feature points, and anchored scan nodes. Guided mode remains the
 * fallback when ARCore or camera permission is unavailable.
 */
class ArCoreLiveScanView(
    context: Context,
    onFrameState: (ArFrameState) -> Unit,
    onNodePlaced: (ArNodePlacement) -> Unit
) : GLSurfaceView(context) {
    private var closed = false
    private var touchLocation: Pair<Float, Float>? = null
    private val renderer = ArCoreRenderer(
        context = context,
        onFrameState = onFrameState,
        onNodePlaced = onNodePlaced
    )

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        contentDescription = "Live AR camera. Tap a detected surface to place a scan node."
    }

    fun resumeSession() {
        if (!closed && renderer.resumeSession()) onResume()
    }

    fun pauseSession() {
        if (closed) return
        // Wait for the GL thread before pausing the session it reads.
        onPause()
        renderer.pauseSession()
    }

    fun closeSession() {
        if (closed) return
        closed = true
        onPause()
        renderer.closeSession()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                touchLocation = event.x to event.y
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> touchLocation = null
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        val location = touchLocation
        touchLocation = null
        // Accessibility activation places at the centre of the camera view.
        if (!closed) renderer.queueTap(location?.first ?: width / 2f, location?.second ?: height / 2f, width, height)
        return true
    }
}

private class ArCoreRenderer(
    private val context: Context,
    private val onFrameState: (ArFrameState) -> Unit,
    private val onNodePlaced: (ArNodePlacement) -> Unit
) : GLSurfaceView.Renderer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingTap = AtomicReference<Tap?>(null)
    private val anchors = mutableListOf<Anchor>()

    @Volatile private var session: Session? = null
    @Volatile private var sessionRunning = false
    @Volatile private var closed = false
    private var installRequested = false
    private var cameraTextureId = 0
    private var cameraProgram = 0
    private var pointProgram = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private var lastDisplayRotation = Surface.ROTATION_0
    @Volatile private var displayGeometrySet = false
    @Volatile private var textureBoundToSession = false
    private var lastUiReportNanos = 0L

    private var originPose: Pose? = null
    private var lastPose: Pose? = null
    private var pathMeters = 0f
    private var distanceFromOriginMeters = 0f
    private var pointCount = 0

    private val ndcQuad = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )
    private val ndcQuadBuffer = directFloatBuffer(ndcQuad.size)
    private val cameraTexCoords = FloatArray(8)
    private val cameraTexBuffer = directFloatBuffer(cameraTexCoords.size)
    private val pointVertices = FloatArray(MAX_POINT_COUNT * 3)
    private val pointBuffer = directFloatBuffer(pointVertices.size)
    private val markerVertices = FloatArray(MAX_ANCHOR_COUNT * 3)
    private val markerBuffer = directFloatBuffer(markerVertices.size)

    private var cameraPositionAttribute = -1
    private var cameraTexCoordAttribute = -1
    private var cameraTextureUniform = -1
    private var pointPositionAttribute = -1
    private var pointViewUniform = -1
    private var pointProjectionUniform = -1
    private var pointSizeUniform = -1
    private var pointColorUniform = -1

    init {
        ndcQuadBuffer.put(ndcQuad).position(0)
    }

    fun resumeSession(): Boolean {
        if (closed) return false
        if (sessionRunning) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            reportError("Camera permission is required for live AR")
            return false
        }
        val activity = context.findActivity()
        if (activity == null) {
            reportError("AR needs a foreground activity")
            return false
        }
        try {
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, !installRequested)
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true
                reportState(ArFrameState(phaseLabel = "INSTALLING AR RUNTIME", errorMessage = "Finish the ARCore setup, then return to Room Mapper"))
                return false
            }
            if (session == null) {
                session = Session(context).also { configure(it) }
                textureBoundToSession = false
                displayGeometrySet = false
                originPose = null
                lastPose = null
                pathMeters = 0f
                distanceFromOriginMeters = 0f
                pointCount = 0
            }
            displayGeometrySet = false
            session?.resume()
            sessionRunning = true
            reportState(ArFrameState(phaseLabel = "LOOK AROUND TO INITIALISE", errorMessage = null))
            return true
        } catch (error: Exception) {
            sessionRunning = false
            reportError(error.message ?: "ARCore could not start on this device")
            return false
        }
    }

    fun pauseSession() {
        val wasRunning = sessionRunning
        sessionRunning = false
        if (wasRunning) runCatching { session?.pause() }
        pendingTap.set(null)
        lastPose = null
    }

    fun closeSession() {
        if (closed) return
        pauseSession()
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        // Anchor owns no close() API. detach() releases its tracking work.
        anchors.forEach { anchor ->
            runCatching { anchor.detach() }
        }
        anchors.clear()
        val sessionToClose = session
        session = null
        textureBoundToSession = false
        displayGeometrySet = false
        // Session.close() can take seconds; the GL thread is stopped and no
        // callbacks can escape, so native cleanup can happen off the UI thread.
        if (sessionToClose != null) {
            Thread({ runCatching { sessionToClose.close() } }, "RoomMapper-ArClose").start()
        }
    }

    fun queueTap(x: Float, y: Float, width: Int, height: Int) {
        if (closed || !sessionRunning || width <= 0 || height <= 0) return
        pendingTap.set(
            Tap(
                x = x,
                y = y,
                normalizedX = (x / width).coerceIn(0f, 1f),
                normalizedY = (y / height).coerceIn(0f, 1f)
            )
        )
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        try {
            cameraProgram = createProgram(CAMERA_VERTEX_SHADER, CAMERA_FRAGMENT_SHADER)
            pointProgram = createProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER)
            cameraPositionAttribute = GLES20.glGetAttribLocation(cameraProgram, "a_Position")
            cameraTexCoordAttribute = GLES20.glGetAttribLocation(cameraProgram, "a_TexCoord")
            cameraTextureUniform = GLES20.glGetUniformLocation(cameraProgram, "sTexture")
            pointPositionAttribute = GLES20.glGetAttribLocation(pointProgram, "a_Position")
            pointViewUniform = GLES20.glGetUniformLocation(pointProgram, "u_View")
            pointProjectionUniform = GLES20.glGetUniformLocation(pointProgram, "u_Projection")
            pointSizeUniform = GLES20.glGetUniformLocation(pointProgram, "u_PointSize")
            pointColorUniform = GLES20.glGetUniformLocation(pointProgram, "u_Color")
            cameraTextureId = createCameraTexture()
            textureBoundToSession = false
        } catch (error: Exception) {
            reportError(error.message ?: "AR renderer initialisation failed")
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        // Apply geometry in onDrawFrame only while the session is running.
        displayGeometrySet = false
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val activeSession = session ?: return
        if (!sessionRunning || cameraProgram == 0 || pointProgram == 0 || cameraTextureId == 0) return

        try {
            if (!textureBoundToSession) {
                activeSession.setCameraTextureName(cameraTextureId)
                textureBoundToSession = true
            }
            val rotation = displayRotation()
            if (!displayGeometrySet || rotation != lastDisplayRotation) {
                lastDisplayRotation = rotation
                activeSession.setDisplayGeometry(rotation, surfaceWidth, surfaceHeight)
                displayGeometrySet = true
            }
            val frame = activeSession.update()
            drawCamera(frame)

            val camera = frame.camera
            val trackingState = camera.trackingState
            if (trackingState == TrackingState.TRACKING) {
                updateOriginAndPath(camera.pose)
                processTap(frame)
                try {
                    val pointCloud = frame.acquirePointCloud()
                    try {
                        pointCount = copyPointCloud(pointCloud.points)
                        drawPointCloud(camera)
                    } finally {
                        pointCloud.close()
                    }
                } catch (_: NotYetAvailableException) {
                    // ARCore may not have a fresh cloud during the first few
                    // frames. Keep the camera and existing anchors responsive.
                }
                drawAnchors(camera)
            } else {
                lastPose = null
                pendingTap.set(null)
                pointCount = 0
            }
            reportTelemetry(trackingState)
        } catch (error: Exception) {
            if (sessionRunning) reportError(error.message ?: "AR frame unavailable")
        }
    }

    private fun configure(target: Session) {
        val config = Config(target).apply {
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        }
        target.configure(config)
    }

    private fun drawCamera(frame: com.google.ar.core.Frame) {
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            ndcQuad,
            Coordinates2d.TEXTURE_NORMALIZED,
            cameraTexCoords
        )
        cameraTexBuffer.clear()
        cameraTexBuffer.put(cameraTexCoords).position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(cameraTextureUniform, 0)

        GLES20.glEnableVertexAttribArray(cameraPositionAttribute)
        GLES20.glVertexAttribPointer(cameraPositionAttribute, 2, GLES20.GL_FLOAT, false, 0, ndcQuadBuffer)
        GLES20.glEnableVertexAttribArray(cameraTexCoordAttribute)
        GLES20.glVertexAttribPointer(cameraTexCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, cameraTexBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(cameraPositionAttribute)
        GLES20.glDisableVertexAttribArray(cameraTexCoordAttribute)
    }

    private fun drawPointCloud(camera: com.google.ar.core.Camera) {
        if (pointCount <= 0) return
        val view = FloatArray(16)
        val projection = FloatArray(16)
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(projection, 0, 0.05f, 40f)

        pointBuffer.clear()
        pointBuffer.put(pointVertices, 0, pointCount * 3).position(0)
        drawWorldPoints(pointBuffer, pointCount, view, projection, 4.4f, floatArrayOf(.16f, .93f, 1f, .74f))
    }

    private fun drawAnchors(camera: com.google.ar.core.Camera) {
        val active = anchors.filter { it.trackingState == TrackingState.TRACKING }.take(MAX_ANCHOR_COUNT)
        if (active.isEmpty()) return
        val view = FloatArray(16)
        val projection = FloatArray(16)
        camera.getViewMatrix(view, 0)
        camera.getProjectionMatrix(projection, 0, 0.05f, 40f)
        markerBuffer.clear()
        active.forEachIndexed { index, anchor ->
            val pose = anchor.pose
            markerVertices[index * 3] = pose.tx()
            markerVertices[index * 3 + 1] = pose.ty() + .04f
            markerVertices[index * 3 + 2] = pose.tz()
        }
        markerBuffer.put(markerVertices, 0, active.size * 3).position(0)
        drawWorldPoints(markerBuffer, active.size, view, projection, 17f, floatArrayOf(1f, .34f, .78f, .96f))
    }

    private fun drawWorldPoints(
        vertices: FloatBuffer,
        count: Int,
        view: FloatArray,
        projection: FloatArray,
        size: Float,
        color: FloatArray
    ) {
        GLES20.glUseProgram(pointProgram)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUniformMatrix4fv(pointViewUniform, 1, false, view, 0)
        GLES20.glUniformMatrix4fv(pointProjectionUniform, 1, false, projection, 0)
        GLES20.glUniform1f(pointSizeUniform, size)
        GLES20.glUniform4f(pointColorUniform, color[0], color[1], color[2], color[3])
        GLES20.glEnableVertexAttribArray(pointPositionAttribute)
        GLES20.glVertexAttribPointer(pointPositionAttribute, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        GLES20.glDisableVertexAttribArray(pointPositionAttribute)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun processTap(frame: com.google.ar.core.Frame) {
        val tap = pendingTap.getAndSet(null) ?: return
        val hit = frame.hitTest(tap.x, tap.y).firstOrNull { result ->
            val trackable = result.trackable
            when (trackable) {
                is com.google.ar.core.Plane -> trackable.isPoseInPolygon(result.hitPose)
                is com.google.ar.core.Point -> true
                else -> false
            }
        } ?: return
        val anchor = runCatching { hit.createAnchor() }.getOrNull() ?: return
        anchors += anchor
        while (anchors.size > MAX_ANCHOR_COUNT) {
            val expired = anchors.removeAt(0)
            runCatching { expired.detach() }
        }
        mainHandler.post {
            if (!closed && sessionRunning) onNodePlaced(ArNodePlacement(tap.normalizedX, tap.normalizedY))
        }
    }

    private fun copyPointCloud(source: FloatBuffer): Int {
        source.rewind()
        val count = min(MAX_POINT_COUNT, source.remaining() / 4)
        for (index in 0 until count) {
            val sourceIndex = index * 4
            pointVertices[index * 3] = source.get(sourceIndex)
            pointVertices[index * 3 + 1] = source.get(sourceIndex + 1)
            pointVertices[index * 3 + 2] = source.get(sourceIndex + 2)
        }
        return count
    }

    private fun updateOriginAndPath(pose: Pose) {
        if (originPose == null) {
            originPose = pose
            lastPose = pose
            return
        }
        val previous = lastPose
        if (previous != null) {
            val step = hypot(pose.tx() - previous.tx(), pose.tz() - previous.tz())
            if (step in .003f..0.35f) pathMeters = (pathMeters + step).coerceAtMost(999f)
        }
        val relative = originPose!!.inverse().compose(pose)
        distanceFromOriginMeters = hypot(relative.tx(), relative.tz()).coerceAtMost(999f)
        lastPose = pose
    }

    private fun reportTelemetry(trackingState: TrackingState) {
        val now = System.nanoTime()
        if (now - lastUiReportNanos < 100_000_000L) return
        lastUiReportNanos = now
        val tracking = trackingState == TrackingState.TRACKING
        val quality = if (!tracking) 0 else ((pointCount / 3) + (pathMeters * 12).toInt()).coerceIn(0, 100)
        val phase = when {
            !tracking -> "RE-ALIGN CAMERA TO CONTINUE"
            pointCount < 80 -> "CALIBRATING ROOM SURFACE"
            anchors.isEmpty() -> "MAP READY • TAP TO DROP A NODE"
            else -> "LIVE SIGNAL FIELD CAPTURE"
        }
        reportState(
            ArFrameState(
                trackingLabel = if (tracking) "TRACKING LOCKED" else "TRACKING PAUSED",
                trackingReady = tracking,
                phaseLabel = phase,
                pointCount = pointCount,
                nodeCount = anchors.count { it.trackingState == TrackingState.TRACKING },
                pathMeters = pathMeters,
                distanceFromOriginMeters = distanceFromOriginMeters,
                qualityPercent = quality,
                frameTimeNanos = now
            )
        )
    }

    private fun reportError(message: String) {
        reportState(
            ArFrameState(
                trackingLabel = "AR UNAVAILABLE",
                phaseLabel = "GUIDED FALLBACK AVAILABLE",
                errorMessage = message
            )
        )
    }

    private fun reportState(state: ArFrameState) {
        if (!closed) mainHandler.post { if (!closed) onFrameState(state) }
    }

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        val activity = context.findActivity() ?: return Surface.ROTATION_0
        return if (Build.VERSION.SDK_INT >= 30) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            activity.windowManager.defaultDisplay.rotation
        }
    }

    private fun createCameraTexture(): Int {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return texture[0]
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("OpenGL link failed: $log")
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("OpenGL compile failed: $log")
        }
        return shader
    }

    private data class Tap(
        val x: Float,
        val y: Float,
        val normalizedX: Float,
        val normalizedY: Float
    )

    companion object {
        private const val MAX_POINT_COUNT = 2_400
        private const val MAX_ANCHOR_COUNT = 12

        private const val CAMERA_VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val CAMERA_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """

        private const val POINT_VERTEX_SHADER = """
            attribute vec4 a_Position;
            uniform mat4 u_View;
            uniform mat4 u_Projection;
            uniform float u_PointSize;
            void main() {
                gl_Position = u_Projection * u_View * a_Position;
                gl_PointSize = u_PointSize;
            }
        """

        private const val POINT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                vec2 coordinate = gl_PointCoord - vec2(0.5);
                float alpha = 1.0 - smoothstep(0.18, 0.5, length(coordinate));
                gl_FragColor = vec4(u_Color.rgb, u_Color.a * alpha);
            }
        """

        private fun directFloatBuffer(size: Int): FloatBuffer =
            ByteBuffer.allocateDirect(size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
