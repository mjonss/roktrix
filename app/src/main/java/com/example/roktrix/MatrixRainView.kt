package com.example.roktrix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

private val MATRIX_CHARS = (
    "\uff71\uff72\uff73\uff74\uff75\uff76\uff77\uff78\uff79\uff7a" +
    "\uff7b\uff7c\uff7d\uff7e\uff7f\uff80\uff81\uff82\uff83\uff84" +
    "\uff85\uff86\uff87\uff88\uff89\uff8a\uff8b\uff8c\uff8d\uff8e" +
    "\uff8f\uff90\uff91\uff92\uff93\uff94\uff95\uff96\uff97\uff98" +
    "\uff99\uff9a\uff9b\uff9c\uff9d" +
    "0123456789" +
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
    ":<>|{}[]=$+*&@!?"
).toCharArray()

private class Drop(
    val colIndex: Int,
    var y: Float,
    var speed: Float,
    var length: Int,
    var chars: CharArray,
    var changeTimer: Int
)

class MatrixRainView(context: Context) : View(context), SensorEventListener {

    private val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = 14f * resources.displayMetrics.scaledDensity
        isAntiAlias = true
    }

    private val charWidth = paint.measureText("W")
    private val charHeight = paint.textSize * 1.4f

    private val drops = mutableListOf<Drop>()
    private var initialized = false

    // Full 360° horizontal: half circumference = π rad × 2000 px/rad
    private val worldExtraH = 6283f
    // ±45° vertical: plenty for head pitch
    private val worldExtraV = 1600f

    private val maxTrailLength = 40

    // Head tracking
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationMatrix = FloatArray(9)

    private var sensorReadCount = 0
    private var referenceYaw = Float.NaN
    private var referencePitch = Float.NaN
    private var offsetX = 0f
    private var offsetY = 0f

    private val pixelsPerRadian = 2000f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sensorReadCount = 0
        referenceYaw = Float.NaN
        referencePitch = Float.NaN
        offsetX = 0f
        offsetY = 0f
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onDetachedFromWindow() {
        sensorManager.unregisterListener(this)
        super.onDetachedFromWindow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Gaze direction = device -Z transformed to world coords
        val gazeX = -rotationMatrix[2]
        val gazeY = -rotationMatrix[5]
        val gazeZ = -rotationMatrix[8]

        val yaw = atan2(gazeX, gazeY)
        val horizontalDist = sqrt(gazeX * gazeX + gazeY * gazeY)
        val pitch = atan2(gazeZ, horizontalDist)

        sensorReadCount++

        if (referenceYaw.isNaN()) {
            if (sensorReadCount >= 15) {
                referenceYaw = yaw
                referencePitch = pitch
            }
            return
        }

        var deltaYaw = yaw - referenceYaw
        if (deltaYaw > Math.PI) deltaYaw -= (2 * Math.PI).toFloat()
        if (deltaYaw < -Math.PI) deltaYaw += (2 * Math.PI).toFloat()
        val deltaPitch = pitch - referencePitch

        offsetX = -deltaYaw * pixelsPerRadian
        // Flipped: head tilts up → pitch increases → content shifts down
        offsetY = deltaPitch * pixelsPerRadian
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initDrops()
    }

    private fun initDrops() {
        drops.clear()

        val worldWidth = width + 2 * worldExtraH
        val worldHeight = height + 2 * worldExtraV
        val totalCols = (worldWidth / charWidth).toInt()

        // Multiple drops per column to maintain density across the tall world
        val dropsPerCol = ceil(worldHeight / height.toDouble()).toInt().coerceAtLeast(1)
        val sliceHeight = worldHeight / dropsPerCol

        for (col in 0 until totalCols) {
            for (d in 0 until dropsPerCol) {
                val length = Random.nextInt(6, maxTrailLength)
                drops.add(
                    Drop(
                        colIndex = col,
                        // Spread drops evenly throughout the world height with jitter
                        y = d * sliceHeight + Random.nextFloat() * sliceHeight,
                        speed = Random.nextFloat() * 4f + 2f,
                        length = length,
                        chars = CharArray(length) { MATRIX_CHARS.random() },
                        changeTimer = Random.nextInt(2, 6)
                    )
                )
            }
        }
        initialized = true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        if (!initialized) {
            postInvalidateOnAnimation()
            return
        }

        val screenW = width.toFloat()
        val screenH = height.toFloat()
        val worldHeight = screenH + 2 * worldExtraV

        for (drop in drops) {
            drop.y += drop.speed
            drop.changeTimer--
            if (drop.changeTimer <= 0) {
                drop.chars[Random.nextInt(drop.chars.size)] = MATRIX_CHARS.random()
                drop.changeTimer = Random.nextInt(2, 6)
            }

            if (drop.y - drop.length * charHeight > worldHeight) {
                drop.length = Random.nextInt(6, maxTrailLength)
                drop.y = -Random.nextFloat() * screenH * 0.3f - charHeight
                drop.speed = Random.nextFloat() * 4f + 2f
                drop.chars = CharArray(drop.length) { MATRIX_CHARS.random() }
                drop.changeTimer = Random.nextInt(2, 6)
            }

            val screenX = -worldExtraH + drop.colIndex * charWidth + offsetX
            if (screenX < -charWidth || screenX > screenW + charWidth) continue

            val headRow = (drop.y / charHeight).toInt()

            for (i in 0 until drop.length) {
                val screenY = (headRow - i) * charHeight - worldExtraV + offsetY
                if (screenY < -charHeight || screenY > screenH + charHeight) continue

                val char = drop.chars[i % drop.chars.size].toString()

                when {
                    i == 0 -> paint.color = Color.argb(255, 200, 255, 200)
                    i <= 2 -> paint.color = Color.argb(240, 0, 255, 0)
                    else -> {
                        val fade = 1f - (i.toFloat() / drop.length)
                        val alpha = (fade * fade * 200).toInt().coerceIn(0, 255)
                        val green = (fade * 220 + 35).toInt().coerceIn(30, 255)
                        paint.color = Color.argb(alpha, 0, green, 0)
                    }
                }

                canvas.drawText(char, screenX, screenY, paint)
            }
        }

        postInvalidateOnAnimation()
    }
}
