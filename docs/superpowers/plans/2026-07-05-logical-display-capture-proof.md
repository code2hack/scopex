# Logical-Display Capture Proof Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the logical-display capture proof: whole-display MediaProjection frames rendered in ScopeX padded logical-display geometry with visible privacy controls.

**Architecture:** Implement this in two slices. First add a pure `scopex-core` layout model that reuses `ScopeXMapper`; then wire the Android app to MediaProjection through a minimal Activity-bound foreground service and a focused custom View. The Android side keeps only the latest frame and never persists captured pixels.

**Tech Stack:** Kotlin/JVM, Android SDK 36, `MediaProjection`, `ImageReader`, Android custom `View`, `kotlin.test`.

---

## File Structure

- Create `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/CaptureProofLayout.kt`
  - Pure layout calculator for the logical-display capture proof.
- Create `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/CaptureProofLayoutTest.kt`
  - JVM tests for geometry and validation.
- Modify `app/build.gradle.kts`
  - Add dependency on `:scopex-core` and app unit-test support.
- Create `app/src/main/java/com/code2hack/scopex/CaptureProofFrameStore.kt`
  - Pure latest-frame holder used by app tests and Activity state.
- Create `app/src/test/kotlin/com/code2hack/scopex/CaptureProofFrameStoreTest.kt`
  - Unit tests for latest-frame-only behavior.
- Create `app/src/main/java/com/code2hack/scopex/CaptureProofFrameBus.kt`
  - Activity-bound in-memory handoff from the capture service to the visible Activity.
- Create `app/src/main/java/com/code2hack/scopex/CaptureProofView.kt`
  - Custom View that draws the latest frame plus ScopeX geometry overlay.
- Create `app/src/main/java/com/code2hack/scopex/ScopeXCaptureService.kt`
  - Minimal `mediaProjection` foreground service.
- Modify `app/src/main/java/com/code2hack/scopex/MainActivity.kt`
  - Replace the current debug rows with proof controls, permission flow, and stop cleanup.
- Modify `app/src/main/AndroidManifest.xml`
  - Add MediaProjection foreground-service permissions and service declaration.
- Modify `app/src/main/res/values/strings.xml`
  - Add notification/channel/status text.

## Issue Slice 1: Pure Core Layout Model

### Task 1: Core Capture-Proof Layout

**Files:**
- Create: `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/CaptureProofLayoutTest.kt`
- Create: `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/CaptureProofLayout.kt`

- [ ] **Step 1: Write failing layout tests**

Create `scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/CaptureProofLayoutTest.kt`:

```kotlin
package com.code2hack.scopex.scopex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CaptureProofLayoutTest {
    private val frameSize = IntSize(1920, 1080)
    private val viewSize = IntSize(1200, 900)

    @Test
    fun capturedFrameSizeIsLogicalDisplaySize() {
        val layout = ScopeXCaptureProofLayoutCalculator.layout(
            frameSize = frameSize,
            viewSize = viewSize,
            crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center,
        )

        assertEquals(frameSize, layout.frameSize)
        assertEquals(FloatRect(200f, 225f, 1000f, 675f), layout.logicalDisplayDrawRect)
        assertPointEquals(FloatPoint(600f, 450f), layout.crosshairDrawPoint)
    }

    @Test
    fun paddedLogicalDisplayAddsHalfPhysicalScopeOnEverySide() {
        val layout = ScopeXCaptureProofLayoutCalculator.layout(
            frameSize = frameSize,
            viewSize = viewSize,
            crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center,
        )

        assertEquals(IntSize(960, 720), layout.physicalScopeContentSize)
        assertEquals(FloatRect(-480f, -360f, 2400f, 1440f), layout.scopeState.paddedLogicalDisplayRect)
        assertEquals(FloatRect(0f, 75f, 1200f, 825f), layout.paddedLogicalDisplayDrawRect)
        assertEquals(FloatRect(400f, 300f, 800f, 600f), layout.physicalScopeDrawRect)
    }

    @Test
    fun cornerAnchorsExposeDisplayPaddingAndScopeBounds() {
        val topLeft = ScopeXCaptureProofLayoutCalculator.layout(
            frameSize = frameSize,
            viewSize = viewSize,
            crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.TopLeft,
        )
        val bottomRight = ScopeXCaptureProofLayoutCalculator.layout(
            frameSize = frameSize,
            viewSize = viewSize,
            crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.BottomRight,
        )

        assertPointEquals(FloatPoint(200f, 225f), topLeft.crosshairDrawPoint)
        assertEquals(FloatRect(0f, 75f, 400f, 375f), topLeft.physicalScopeDrawRect)
        assertPointEquals(FloatPoint(1000f, 675f), bottomRight.crosshairDrawPoint)
        assertEquals(FloatRect(800f, 525f, 1200f, 825f), bottomRight.physicalScopeDrawRect)
    }

    @Test
    fun invalidSizesFailClearly() {
        assertFailsWith<IllegalArgumentException> {
            ScopeXCaptureProofLayoutCalculator.layout(
                frameSize = IntSize(1, 1),
                viewSize = IntSize(1, 1),
                crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center,
            )
        }
    }

    private fun assertPointEquals(expected: FloatPoint, actual: FloatPoint) {
        assertEquals(expected.x, actual.x, absoluteTolerance = 0.01f)
        assertEquals(expected.y, actual.y, absoluteTolerance = 0.01f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.CaptureProofLayoutTest --no-problems-report
```

Expected: compile failure because `ScopeXCaptureProofLayoutCalculator`, `ScopeXCaptureProofCrosshairAnchor`, and `ScopeXCaptureProofLayout` do not exist.

- [ ] **Step 3: Add the minimal pure layout model**

Create `scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/CaptureProofLayout.kt`:

```kotlin
package com.code2hack.scopex.scopex

import kotlin.math.min
import kotlin.math.roundToInt

private const val PHYSICAL_SCOPE_SHORT_SIDE_VIEW_FRACTION: Float = 1f / 3f
private const val PHYSICAL_SCOPE_ASPECT_WIDTH: Float = 4f
private const val PHYSICAL_SCOPE_ASPECT_HEIGHT: Float = 3f
private const val DEFAULT_CAPTURE_PROOF_MAX_YAW_DEGREES: Float = 30f
private const val DEFAULT_CAPTURE_PROOF_MAX_PITCH_DEGREES: Float = 20f

enum class ScopeXCaptureProofCrosshairAnchor {
    Center,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

data class ScopeXCaptureProofLayout(
    val frameSize: IntSize,
    val viewSize: IntSize,
    val physicalScopeContentSize: IntSize,
    val scopeState: ScopeXState,
    val logicalDisplayDrawRect: FloatRect,
    val paddedLogicalDisplayDrawRect: FloatRect,
    val physicalScopeDrawRect: FloatRect,
    val crosshairDrawPoint: FloatPoint,
)

object ScopeXCaptureProofLayoutCalculator {
    fun layout(
        frameSize: IntSize,
        viewSize: IntSize,
        crosshairAnchor: ScopeXCaptureProofCrosshairAnchor,
    ): ScopeXCaptureProofLayout {
        val physicalScopeDrawSize = physicalScopeDrawSize(viewSize)
        val scale = min(
            (viewSize.width - physicalScopeDrawSize.width).toFloat() / frameSize.width,
            (viewSize.height - physicalScopeDrawSize.height).toFloat() / frameSize.height,
        )
        require(scale > 0f) { "view must have room for logical display and physical scope" }

        val physicalScopeContentSize = IntSize(
            width = (physicalScopeDrawSize.width / scale).roundToInt().coerceAtLeast(1),
            height = (physicalScopeDrawSize.height / scale).roundToInt().coerceAtLeast(1),
        )
        val mapper = ScopeXMapper(
            ScopeXConfig(
                contentSize = frameSize,
                physicalScopeSize = physicalScopeContentSize,
                maxYawDegrees = DEFAULT_CAPTURE_PROOF_MAX_YAW_DEGREES,
                maxPitchDegrees = DEFAULT_CAPTURE_PROOF_MAX_PITCH_DEGREES,
            ),
        )
        val scopeState = mapper.stateForCrosshairContentPoint(crosshairAnchor.contentPoint(frameSize))
        val paddedWidth = frameSize.width * scale + physicalScopeDrawSize.width
        val paddedHeight = frameSize.height * scale + physicalScopeDrawSize.height
        val paddedLeft = (viewSize.width - paddedWidth) / 2f
        val paddedTop = (viewSize.height - paddedHeight) / 2f
        val logicalDisplayDrawRect = FloatRect(
            left = paddedLeft + physicalScopeDrawSize.width / 2f,
            top = paddedTop + physicalScopeDrawSize.height / 2f,
            right = paddedLeft + physicalScopeDrawSize.width / 2f + frameSize.width * scale,
            bottom = paddedTop + physicalScopeDrawSize.height / 2f + frameSize.height * scale,
        )

        return ScopeXCaptureProofLayout(
            frameSize = frameSize,
            viewSize = viewSize,
            physicalScopeContentSize = physicalScopeContentSize,
            scopeState = scopeState,
            logicalDisplayDrawRect = logicalDisplayDrawRect,
            paddedLogicalDisplayDrawRect = scopeState.paddedLogicalDisplayRect.toDrawRect(
                logicalDisplayDrawRect,
                scale,
            ),
            physicalScopeDrawRect = scopeState.physicalScopeRect.toDrawRect(logicalDisplayDrawRect, scale),
            crosshairDrawPoint = scopeState.crosshairContentPoint.toDrawPoint(logicalDisplayDrawRect, scale),
        )
    }

    private fun physicalScopeDrawSize(viewSize: IntSize): IntSize {
        val scopeHeight = (min(viewSize.width, viewSize.height) *
            PHYSICAL_SCOPE_SHORT_SIDE_VIEW_FRACTION).roundToInt().coerceAtLeast(1)
        return IntSize(
            width = (scopeHeight * PHYSICAL_SCOPE_ASPECT_WIDTH / PHYSICAL_SCOPE_ASPECT_HEIGHT)
                .roundToInt()
                .coerceAtLeast(1),
            height = scopeHeight,
        )
    }
}

private fun ScopeXCaptureProofCrosshairAnchor.contentPoint(frameSize: IntSize): FloatPoint =
    when (this) {
        ScopeXCaptureProofCrosshairAnchor.Center ->
            FloatPoint(frameSize.width / 2f, frameSize.height / 2f)
        ScopeXCaptureProofCrosshairAnchor.TopLeft ->
            FloatPoint(0f, 0f)
        ScopeXCaptureProofCrosshairAnchor.TopRight ->
            FloatPoint(frameSize.width.toFloat(), 0f)
        ScopeXCaptureProofCrosshairAnchor.BottomLeft ->
            FloatPoint(0f, frameSize.height.toFloat())
        ScopeXCaptureProofCrosshairAnchor.BottomRight ->
            FloatPoint(frameSize.width.toFloat(), frameSize.height.toFloat())
    }

private fun FloatRect.toDrawRect(
    logicalDisplayDrawRect: FloatRect,
    scale: Float,
): FloatRect =
    FloatRect(
        left = logicalDisplayDrawRect.left + left * scale,
        top = logicalDisplayDrawRect.top + top * scale,
        right = logicalDisplayDrawRect.left + right * scale,
        bottom = logicalDisplayDrawRect.top + bottom * scale,
    )

private fun FloatPoint.toDrawPoint(
    logicalDisplayDrawRect: FloatRect,
    scale: Float,
): FloatPoint =
    FloatPoint(
        x = logicalDisplayDrawRect.left + x * scale,
        y = logicalDisplayDrawRect.top + y * scale,
    )
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :scopex-core:test --tests com.code2hack.scopex.scopex.CaptureProofLayoutTest --no-problems-report
```

Expected: `CaptureProofLayoutTest` passes.

- [ ] **Step 5: Commit the core slice**

Run:

```bash
git add scopex-core/src/main/kotlin/com/code2hack/scopex/scopex/CaptureProofLayout.kt \
  scopex-core/src/test/kotlin/com/code2hack/scopex/scopex/CaptureProofLayoutTest.kt
git commit -m "feat: add capture proof layout model"
```

Expected: one commit containing only the pure model and its tests.

## Issue Slice 2: Android MediaProjection Proof

### Task 2: App Dependency And Latest-Frame Store

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/code2hack/scopex/CaptureProofFrameStore.kt`
- Create: `app/src/test/kotlin/com/code2hack/scopex/CaptureProofFrameStoreTest.kt`

- [ ] **Step 1: Write failing latest-frame tests**

Create `app/src/test/kotlin/com/code2hack/scopex/CaptureProofFrameStoreTest.kt`:

```kotlin
package com.code2hack.scopex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureProofFrameStoreTest {
    @Test
    fun replaceKeepsOnlyLatestFrame() {
        val store = CaptureProofFrameStore<String>()

        store.replace("first")
        store.replace("second")

        assertEquals("second", store.latest)
    }

    @Test
    fun clearRemovesLatestFrame() {
        val store = CaptureProofFrameStore<String>()

        store.replace("frame")
        store.clear()

        assertNull(store.latest)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.code2hack.scopex.CaptureProofFrameStoreTest --no-problems-report
```

Expected: compile failure because `CaptureProofFrameStore` does not exist.

- [ ] **Step 3: Add app dependencies and frame store**

Modify `app/build.gradle.kts` so the file ends with:

```kotlin
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":scopex-core"))
    testImplementation(kotlin("test-junit"))
}
```

Create `app/src/main/java/com/code2hack/scopex/CaptureProofFrameStore.kt`:

```kotlin
package com.code2hack.scopex

class CaptureProofFrameStore<T> {
    var latest: T? = null
        private set

    fun replace(frame: T) {
        latest = frame
    }

    fun clear() {
        latest = null
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.code2hack.scopex.CaptureProofFrameStoreTest --no-problems-report
```

Expected: `CaptureProofFrameStoreTest` passes.

- [ ] **Step 5: Commit the frame-store slice**

Run:

```bash
git add app/build.gradle.kts \
  app/src/main/java/com/code2hack/scopex/CaptureProofFrameStore.kt \
  app/src/test/kotlin/com/code2hack/scopex/CaptureProofFrameStoreTest.kt
git commit -m "feat: add capture proof frame store"
```

Expected: one commit containing the app dependency and the pure latest-frame test.

### Task 3: Custom Capture-Proof View

**Files:**
- Create: `app/src/main/java/com/code2hack/scopex/CaptureProofView.kt`

- [ ] **Step 1: Create the focused custom View**

Create `app/src/main/java/com/code2hack/scopex/CaptureProofView.kt`:

```kotlin
package com.code2hack.scopex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.code2hack.scopex.scopex.FloatPoint
import com.code2hack.scopex.scopex.FloatRect
import com.code2hack.scopex.scopex.IntSize
import com.code2hack.scopex.scopex.ScopeXCaptureProofCrosshairAnchor
import com.code2hack.scopex.scopex.ScopeXCaptureProofLayoutCalculator

class CaptureProofView(context: Context) : View(context) {
    private val frameStore = CaptureProofFrameStore<Bitmap>()
    private var crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center
    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paddingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 43, 52)
        style = Paint.Style.FILL
    }
    private val logicalDisplayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(132, 196, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val physicalScopePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 214, 102)
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 92, 92)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 196, 204)
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }

    fun replaceFrame(frame: Bitmap) {
        frameStore.latest?.takeIf { it !== frame }?.recycle()
        frameStore.replace(frame)
        invalidate()
    }

    fun clearFrame() {
        frameStore.latest?.recycle()
        frameStore.clear()
        invalidate()
    }

    fun setCrosshairAnchor(anchor: ScopeXCaptureProofCrosshairAnchor) {
        crosshairAnchor = anchor
        invalidate()
    }

    override fun onDetachedFromWindow() {
        clearFrame()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(15, 18, 22))

        val frame = frameStore.latest
        if (frame == null || width <= 0 || height <= 0) {
            canvas.drawText("No active capture", width / 2f, height / 2f, emptyPaint)
            return
        }

        val layout = ScopeXCaptureProofLayoutCalculator.layout(
            frameSize = IntSize(frame.width, frame.height),
            viewSize = IntSize(width, height),
            crosshairAnchor = crosshairAnchor,
        )

        canvas.drawRect(layout.paddedLogicalDisplayDrawRect.toRectF(), paddingPaint)
        canvas.drawBitmap(frame, null, layout.logicalDisplayDrawRect.toRectF(), framePaint)
        canvas.drawRect(layout.logicalDisplayDrawRect.toRectF(), logicalDisplayPaint)
        canvas.drawRect(layout.physicalScopeDrawRect.toRectF(), physicalScopePaint)
        canvas.drawCrosshair(layout.crosshairDrawPoint)
    }

    private fun Canvas.drawCrosshair(point: FloatPoint) {
        val radius = 22f
        drawLine(point.x - radius, point.y, point.x + radius, point.y, crosshairPaint)
        drawLine(point.x, point.y - radius, point.x, point.y + radius, crosshairPaint)
    }

    private fun FloatRect.toRectF(): RectF = RectF(left, top, right, bottom)
}
```

- [ ] **Step 2: Compile app to verify the View and core dependency**

Run:

```bash
./gradlew :app:assembleDebug --no-problems-report
```

Expected: `:app:assembleDebug` succeeds.

- [ ] **Step 3: Commit the custom View**

Run:

```bash
git add app/src/main/java/com/code2hack/scopex/CaptureProofView.kt
git commit -m "feat: add capture proof view"
```

Expected: one commit containing only the custom View.

### Task 4: Manifest And Strings For Capture

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add capture permissions and service declaration**

Replace `app/src/main/AndroidManifest.xml` with:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".ScopeXCaptureService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />
    </application>
</manifest>
```

- [ ] **Step 2: Add user-visible strings**

Replace `app/src/main/res/values/strings.xml` with:

```xml
<resources>
    <string name="app_name">scopex</string>
    <string name="capture_notification_channel">ScopeX capture</string>
    <string name="capture_notification_title">ScopeX screen capture active</string>
    <string name="capture_notification_stop">Stop</string>
    <string name="capture_status_idle">Capture idle</string>
    <string name="capture_status_requesting">Requesting screen capture permission</string>
    <string name="capture_status_active">Capture active</string>
    <string name="capture_status_denied">Screen capture permission denied</string>
    <string name="capture_status_stopped">Capture stopped</string>
    <string name="capture_status_notification_denied">Notification permission is required for this capture proof</string>
    <string name="capture_status_error">Capture error</string>
</resources>
```

- [ ] **Step 3: Compile resources**

Run:

```bash
./gradlew :app:assembleDebug --no-problems-report
```

Expected: compile failure because `ScopeXCaptureService` is declared but not created yet.

Commit is intentionally skipped until Task 5 creates the service.

### Task 5: Activity-Bound MediaProjection Service

**Files:**
- Create: `app/src/main/java/com/code2hack/scopex/CaptureProofFrameBus.kt`
- Create: `app/src/main/java/com/code2hack/scopex/ScopeXCaptureService.kt`

- [ ] **Step 1: Add the Activity-bound frame bus**

Create `app/src/main/java/com/code2hack/scopex/CaptureProofFrameBus.kt`:

```kotlin
package com.code2hack.scopex

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper

object CaptureProofFrameBus {
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var listener: ((Bitmap) -> Unit)? = null

    fun setListener(nextListener: ((Bitmap) -> Unit)?) {
        listener = nextListener
    }

    fun publish(frame: Bitmap) {
        val currentListener = listener
        if (currentListener == null) {
            frame.recycle()
            return
        }

        mainHandler.post {
            val postedListener = listener
            if (postedListener == null) {
                frame.recycle()
            } else {
                postedListener(frame)
            }
        }
    }
}
```

- [ ] **Step 2: Add the foreground service**

Create `app/src/main/java/com/code2hack/scopex/ScopeXCaptureService.kt`:

```kotlin
package com.code2hack.scopex

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log

class ScopeXCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        if (resultCode == 0 || resultData == null) {
            stopCapture()
            return START_NOT_STICKY
        }

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData).also { projection ->
            projection.registerCallback(projectionCallback, null)
            startVirtualDisplay(projection)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun startVirtualDisplay(projection: MediaProjection) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val densityDpi = metrics.densityDpi
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ availableReader ->
            publishLatestImage(availableReader, width, height)
        }, null)
        virtualDisplay = projection.createVirtualDisplay(
            "ScopeXCaptureProof",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        )
    }

    private fun publishLatestImage(reader: ImageReader, width: Int, height: Int) {
        val image = reader.acquireLatestImage() ?: return
        try {
            val plane = image.planes.firstOrNull() ?: return
            val rowPadding = plane.rowStride - plane.pixelStride * width
            val paddedWidth = width + rowPadding / plane.pixelStride
            val paddedBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            paddedBitmap.copyPixelsFromBuffer(plane.buffer)
            val frame = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
            paddedBitmap.recycle()
            CaptureProofFrameBus.publish(frame)
        } catch (error: RuntimeException) {
            Log.w(TAG, "Failed to publish capture frame", error)
        } finally {
            image.close()
        }
    }

    private fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun notification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.capture_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, ScopeXCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.capture_notification_stop),
                stopIntent,
            )
            .build()
    }

    companion object {
        private const val TAG = "ScopeXCaptureService"
        private const val CHANNEL_ID = "scopex_capture"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.code2hack.scopex.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"

        fun startIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
        ): Intent =
            Intent(context, ScopeXCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)

        fun stopIntent(context: Context): Intent =
            Intent(context, ScopeXCaptureService::class.java).setAction(ACTION_STOP)
    }
}
```

- [ ] **Step 3: Compile service**

Run:

```bash
./gradlew :app:assembleDebug --no-problems-report
```

Expected: `:app:assembleDebug` succeeds.

- [ ] **Step 4: Commit manifest, strings, bus, and service**

Run:

```bash
git add app/src/main/AndroidManifest.xml \
  app/src/main/res/values/strings.xml \
  app/src/main/java/com/code2hack/scopex/CaptureProofFrameBus.kt \
  app/src/main/java/com/code2hack/scopex/ScopeXCaptureService.kt
git commit -m "feat: add capture proof service"
```

Expected: one commit containing only service-related files.

### Task 6: MainActivity Proof UI And Permission Flow

**Files:**
- Modify: `app/src/main/java/com/code2hack/scopex/MainActivity.kt`

- [ ] **Step 1: Replace the debug-home Activity**

Replace `app/src/main/java/com/code2hack/scopex/MainActivity.kt` with:

```kotlin
package com.code2hack.scopex

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.code2hack.scopex.scopex.ScopeXCaptureProofCrosshairAnchor

class MainActivity : Activity() {
    private lateinit var captureView: CaptureProofView
    private lateinit var statusText: TextView
    private lateinit var activeIndicator: TextView
    private var captureActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        CaptureProofFrameBus.setListener { frame ->
            captureView.replaceFrame(frame)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CAPTURE) {
            return
        }
        if (resultCode != RESULT_OK || data == null) {
            setCaptureState(active = false, status = getString(R.string.capture_status_denied))
            return
        }

        startForegroundService(ScopeXCaptureService.startIntent(this, resultCode, data))
        setCaptureState(active = true, status = getString(R.string.capture_status_active))
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            requestCaptureConsent()
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            setCaptureState(
                active = false,
                status = getString(R.string.capture_status_notification_denied),
            )
        }
    }

    override fun onStop() {
        if (captureActive) {
            stopCapture(getString(R.string.capture_status_stopped))
        }
        super.onStop()
    }

    override fun onDestroy() {
        CaptureProofFrameBus.setListener(null)
        captureView.clearFrame()
        super.onDestroy()
    }

    private fun buildContent(): View {
        captureView = CaptureProofView(this)
        statusText = label(getString(R.string.capture_status_idle), 16f, Typeface.NORMAL)
        activeIndicator = label("ACTIVE CAPTURE", 14f, Typeface.BOLD).apply {
            setTextColor(Color.rgb(255, 92, 92))
            visibility = View.GONE
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(label("scopex logical-display capture proof", 22f, Typeface.BOLD))
            addView(statusText)
            addView(activeIndicator)
            addView(captureView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ))
            addView(buttonRow(
                button("Start Capture") { startCapture() },
                button("Stop Capture") { stopCapture(getString(R.string.capture_status_stopped)) },
            ))
            addView(buttonRow(
                button("Center") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.Center)
                },
                button("TL") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.TopLeft)
                },
                button("TR") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.TopRight)
                },
                button("BL") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.BottomLeft)
                },
                button("BR") {
                    captureView.setCrosshairAnchor(ScopeXCaptureProofCrosshairAnchor.BottomRight)
                },
            ))
            addView(label("Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", 14f))
        }
    }

    private fun startCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        requestCaptureConsent()
    }

    private fun requestCaptureConsent() {
        setCaptureState(active = false, status = getString(R.string.capture_status_requesting))
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    private fun stopCapture(status: String) {
        stopService(ScopeXCaptureService.stopIntent(this))
        captureView.clearFrame()
        setCaptureState(active = false, status = status)
    }

    private fun setCaptureState(active: Boolean, status: String) {
        captureActive = active
        statusText.text = status
        activeIndicator.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun label(
        value: String,
        sizeSp: Float = 18f,
        style: Int = Typeface.NORMAL,
    ) = TextView(this).apply {
        text = value
        textSize = sizeSp
        typeface = if (style == Typeface.BOLD) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        gravity = Gravity.START
        setPadding(0, 0, 0, 14)
    }

    private fun button(
        value: String,
        onClick: () -> Unit,
    ) = Button(this).apply {
        text = value
        setOnClickListener { onClick() }
    }

    private fun buttonRow(vararg buttons: Button) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (button in buttons) {
                addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    companion object {
        private const val REQUEST_CAPTURE = 10
        private const val REQUEST_NOTIFICATIONS = 11
    }
}
```

- [ ] **Step 2: Compile the Activity**

Run:

```bash
./gradlew :app:assembleDebug --no-problems-report
```

Expected: `:app:assembleDebug` succeeds.

- [ ] **Step 3: Commit the proof UI**

Run:

```bash
git add app/src/main/java/com/code2hack/scopex/MainActivity.kt
git commit -m "feat: wire capture proof UI"
```

Expected: one commit containing only `MainActivity.kt`.

### Task 7: Required Verification

**Files:**
- Verify all touched files.

- [ ] **Step 1: Run core tests, app unit tests, and app build**

Run:

```bash
./gradlew :scopex-core:test :app:testDebugUnitTest :app:assembleDebug --no-problems-report
```

Expected: build succeeds and all unit tests pass.

- [ ] **Step 2: Check worktree**

Run:

```bash
git status --short --branch
```

Expected: no unstaged or staged changes.

- [ ] **Step 3: Manual smoke test**

Run:

```bash
./gradlew :app:assembleDebug --no-problems-report
```

Install the debug APK on a device or emulator. Open scopex, tap `Start Capture`,
grant notification permission if prompted, grant Android screen-capture consent,
observe the captured display rendered inside display padding, move the crosshair
with `Center`, `TL`, `TR`, `BL`, and `BR`, then stop capture from both the app
and notification in separate runs.

Expected: capture starts only after consent, active capture is visible in-app,
the notification has a Stop action, the latest frame updates live, center/corner
buttons move the overlay, Stop clears the frame and returns to idle/stopped
status, and no frame file is created by the app.

## Self-Review

- Spec coverage: Task 1 covers the pure core layout model and JVM tests. Tasks
  2-6 cover latest-frame-only app state, custom View drawing, MediaProjection
  consent, foreground service, notification Stop action, Activity stop cleanup,
  permission denial, and no captured-frame persistence.
- Placeholder scan: the plan contains no deferred implementation markers.
- Type consistency: `ScopeXCaptureProofCrosshairAnchor`,
  `ScopeXCaptureProofLayoutCalculator`, `CaptureProofFrameStore`,
  `CaptureProofView`, `CaptureProofFrameBus`, and `ScopeXCaptureService` are
  introduced before use.
