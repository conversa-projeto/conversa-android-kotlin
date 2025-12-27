package com.conversa.conversa.ui.chamada

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.conversa.conversa.R
import kotlin.math.abs

/**
 * Botão customizado que requer deslizar pelo menos 100px para ativar.
 * Usado para aceitar ou recusar chamadas.
 */
class SwipeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val SWIPE_THRESHOLD_PX = 100f // Mínimo de pixels para ativar
    }

    enum class SwipeType {
        ACCEPT, // Aceitar (verde)
        DECLINE // Recusar (vermelho)
    }

    private var swipeType: SwipeType = SwipeType.ACCEPT
    private var onSwipeCompleteListener: (() -> Unit)? = null

    // Cores
    private val colorAccept = ContextCompat.getColor(context, R.color.colorAcceptCall)
    private val colorDecline = ContextCompat.getColor(context, R.color.colorRejectCall)
    private val colorWhite = ContextCompat.getColor(context, android.R.color.white)
    private val colorGray = ContextCompat.getColor(context, android.R.color.darker_gray)

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorWhite
        textAlign = Paint.Align.CENTER
        textSize = 48f
    }

    // Posições
    private var thumbX = 0f
    private var initialX = 0f
    private var isDragging = false
    private var isEnabled = true

    // Geometria
    private val backgroundRect = RectF()
    private val thumbRect = RectF()
    private var thumbRadius = 0f

    init {
        // Parse attributes
        context.theme.obtainStyledAttributes(attrs, R.styleable.SwipeButton, 0, 0).apply {
            try {
                val typeInt = getInt(R.styleable.SwipeButton_swipeType, 0)
                swipeType = if (typeInt == 0) SwipeType.ACCEPT else SwipeType.DECLINE
            } finally {
                recycle()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        thumbRadius = h / 2f - 16f
        resetThumbPosition()
    }

    private fun resetThumbPosition() {
        thumbX = if (swipeType == SwipeType.ACCEPT) {
            paddingStart + thumbRadius + 16f
        } else {
            width - paddingEnd - thumbRadius - 16f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        backgroundRect.set(
            paddingStart.toFloat(),
            paddingTop.toFloat(),
            (width - paddingEnd).toFloat(),
            (height - paddingBottom).toFloat()
        )

        backgroundPaint.color = if (isEnabled) colorGray else ContextCompat.getColor(context, android.R.color.darker_gray)
        canvas.drawRoundRect(backgroundRect, height / 2f, height / 2f, backgroundPaint)

        // Progress indicator (preenche conforme desliza)
        if (isDragging) {
            val progressRect = RectF()
            val progress = calculateProgress()

            if (swipeType == SwipeType.ACCEPT) {
                progressRect.set(
                    paddingStart.toFloat(),
                    paddingTop.toFloat(),
                    thumbX + thumbRadius,
                    (height - paddingBottom).toFloat()
                )
                backgroundPaint.color = colorAccept
                backgroundPaint.alpha = (progress * 255).toInt()
            } else {
                progressRect.set(
                    thumbX - thumbRadius,
                    paddingTop.toFloat(),
                    (width - paddingEnd).toFloat(),
                    (height - paddingBottom).toFloat()
                )
                backgroundPaint.color = colorDecline
                backgroundPaint.alpha = (progress * 255).toInt()
            }

            canvas.drawRoundRect(progressRect, height / 2f, height / 2f, backgroundPaint)
        }

        // Text
        val text = if (swipeType == SwipeType.ACCEPT) {
            "Deslize para aceitar →"
        } else {
            "← Deslize para recusar"
        }

        val textY = height / 2f + (textPaint.descent() - textPaint.ascent()) / 2f - textPaint.descent()
        textPaint.alpha = if (isDragging) 128 else 255
        canvas.drawText(text, width / 2f, textY, textPaint)

        // Thumb (círculo deslizante)
        thumbPaint.color = if (swipeType == SwipeType.ACCEPT) colorAccept else colorDecline
        canvas.drawCircle(thumbX, height / 2f, thumbRadius, thumbPaint)

        // Icon no thumb (seta ou X)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorWhite
            strokeWidth = 8f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        if (swipeType == SwipeType.ACCEPT) {
            // Desenha seta para direita
            canvas.drawLine(thumbX - 20f, height / 2f, thumbX + 20f, height / 2f, iconPaint)
            canvas.drawLine(thumbX + 10f, height / 2f - 15f, thumbX + 20f, height / 2f, iconPaint)
            canvas.drawLine(thumbX + 10f, height / 2f + 15f, thumbX + 20f, height / 2f, iconPaint)
        } else {
            // Desenha X
            canvas.drawLine(thumbX - 15f, height / 2f - 15f, thumbX + 15f, height / 2f + 15f, iconPaint)
            canvas.drawLine(thumbX - 15f, height / 2f + 15f, thumbX + 15f, height / 2f - 15f, iconPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Verifica se tocou no thumb
                if (abs(event.x - thumbX) <= thumbRadius + 50f) {
                    isDragging = true
                    initialX = event.x
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val minX = paddingStart + thumbRadius + 16f
                    val maxX = width - paddingEnd - thumbRadius - 16f

                    thumbX = if (swipeType == SwipeType.ACCEPT) {
                        event.x.coerceIn(minX, maxX)
                    } else {
                        event.x.coerceIn(minX, maxX)
                    }

                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    val distance = if (swipeType == SwipeType.ACCEPT) {
                        thumbX - (paddingStart + thumbRadius + 16f)
                    } else {
                        (width - paddingEnd - thumbRadius - 16f) - thumbX
                    }

                    if (distance >= SWIPE_THRESHOLD_PX) {
                        // Swipe completo!
                        animateToEnd {
                            onSwipeCompleteListener?.invoke()
                        }
                    } else {
                        // Volta ao início
                        animateToStart()
                    }

                    isDragging = false
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    private fun calculateProgress(): Float {
        val totalDistance = if (swipeType == SwipeType.ACCEPT) {
            width - paddingStart - paddingEnd - 2 * thumbRadius - 32f
        } else {
            width - paddingStart - paddingEnd - 2 * thumbRadius - 32f
        }

        val currentDistance = if (swipeType == SwipeType.ACCEPT) {
            thumbX - (paddingStart + thumbRadius + 16f)
        } else {
            (width - paddingEnd - thumbRadius - 16f) - thumbX
        }

        return (currentDistance / totalDistance).coerceIn(0f, 1f)
    }

    private fun animateToStart() {
        val startX = thumbX
        val endX = if (swipeType == SwipeType.ACCEPT) {
            paddingStart + thumbRadius + 16f
        } else {
            width - paddingEnd - thumbRadius - 16f
        }

        ValueAnimator.ofFloat(startX, endX).apply {
            duration = 200
            addUpdateListener { animation ->
                thumbX = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun animateToEnd(onComplete: () -> Unit) {
        val startX = thumbX
        val endX = if (swipeType == SwipeType.ACCEPT) {
            width - paddingEnd - thumbRadius - 16f
        } else {
            paddingStart + thumbRadius + 16f
        }

        ValueAnimator.ofFloat(startX, endX).apply {
            duration = 200
            addUpdateListener { animation ->
                thumbX = animation.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isEnabled = false
                    onComplete()
                }
            })
            start()
        }
    }

    fun setOnSwipeCompleteListener(listener: () -> Unit) {
        onSwipeCompleteListener = listener
    }

    fun reset() {
        isEnabled = true
        isDragging = false
        resetThumbPosition()
        invalidate()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isEnabled = enabled
        invalidate()
    }
}
