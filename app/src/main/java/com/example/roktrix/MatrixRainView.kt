package com.example.roktrix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
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

private class MatrixColumn(
    var y: Float,
    var speed: Float,
    var length: Int,
    var chars: CharArray,
    var changeTimer: Int
)

class MatrixRainView(context: Context) : View(context) {

    private val paint = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = 14f * resources.displayMetrics.scaledDensity
        isAntiAlias = true
    }

    private val charWidth = paint.measureText("W")
    private val charHeight = paint.textSize * 1.4f

    private val columns = mutableListOf<MatrixColumn>()
    private var initialized = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initColumns()
    }

    private fun initColumns() {
        columns.clear()
        val numCols = (width / charWidth).toInt()
        val maxLen = (height / charHeight).toInt() + 5
        val h = height.toFloat()
        for (i in 0 until numCols) {
            val length = Random.nextInt(6, maxLen.coerceAtLeast(10))
            columns.add(
                MatrixColumn(
                    y = Random.nextFloat() * h * 2f - h * 0.5f,
                    speed = Random.nextFloat() * 4f + 2f,
                    length = length,
                    chars = CharArray(length) { MATRIX_CHARS.random() },
                    changeTimer = Random.nextInt(2, 6)
                )
            )
        }
        initialized = true
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)

        if (!initialized) {
            postInvalidateOnAnimation()
            return
        }

        val h = height.toFloat()
        val maxLen = (h / charHeight).toInt() + 5

        for ((colIndex, col) in columns.withIndex()) {
            // Update position
            col.y += col.speed
            col.changeTimer--
            if (col.changeTimer <= 0) {
                col.chars[Random.nextInt(col.chars.size)] = MATRIX_CHARS.random()
                col.changeTimer = Random.nextInt(2, 6)
            }
            if (col.y - col.length * charHeight > h) {
                col.length = Random.nextInt(6, maxLen.coerceAtLeast(10))
                col.y = -Random.nextFloat() * h * 0.3f - charHeight
                col.speed = Random.nextFloat() * 4f + 2f
                col.chars = CharArray(col.length) { MATRIX_CHARS.random() }
                col.changeTimer = Random.nextInt(2, 6)
            }

            // Draw characters
            val x = colIndex * charWidth
            val headRow = (col.y / charHeight).toInt()

            for (i in 0 until col.length) {
                val cy = (headRow - i) * charHeight
                if (cy < -charHeight || cy > h + charHeight) continue

                val char = col.chars[i % col.chars.size].toString()

                when {
                    i == 0 -> paint.color = Color.argb(255, 200, 255, 200)
                    i <= 2 -> paint.color = Color.argb(240, 0, 255, 0)
                    else -> {
                        val fade = 1f - (i.toFloat() / col.length)
                        val alpha = (fade * fade * 200).toInt().coerceIn(0, 255)
                        val green = (fade * 220 + 35).toInt().coerceIn(30, 255)
                        paint.color = Color.argb(alpha, 0, green, 0)
                    }
                }

                canvas.drawText(char, x, cy, paint)
            }
        }

        postInvalidateOnAnimation()
    }
}
