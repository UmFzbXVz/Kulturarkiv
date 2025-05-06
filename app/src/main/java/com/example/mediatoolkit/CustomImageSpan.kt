package com.example.mediatoolkit

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan
import androidx.core.graphics.withTranslation

class VerticalImageSpan(drawable: Drawable, private val verticalAlignment: Int) : ImageSpan(drawable) {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val size = super.getSize(paint, text, start, end, fm)
        if (fm != null) {
            fm.ascent += verticalAlignment // Juster her
        }
        return size
    }
}