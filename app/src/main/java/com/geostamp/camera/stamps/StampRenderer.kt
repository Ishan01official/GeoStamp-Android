package com.geostamp.camera.stamps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.geostamp.camera.qr.QrDrawing
import com.geostamp.camera.qr.QrLocationEncoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Visual parameters derived from preferences. */
data class StampStyle(
    val position: StampPosition,
    val fontScale: Float,
    val textColor: Int,
    val backgroundOpacity: Float
) {
    companion object {
        fun from(preferences: StampPreferences) = StampStyle(
            position = preferences.position,
            fontScale = preferences.fontScale,
            textColor = preferences.textColor.argb,
            backgroundOpacity = preferences.backgroundOpacity
        )
    }
}

/**
 * Draws the information card onto an image. All measurements derive from the image's short side,
 * so a 12 MP photo, a 1080p preview and a small settings preview look proportionally identical.
 * The renderer only draws over the card area; the photograph itself is never replaced.
 */
class StampRenderer(
    private val iconProvider: (StampIcon) -> Drawable? = { null }
) {
    fun render(
        canvas: Canvas,
        width: Int,
        height: Int,
        content: StampContent,
        style: StampStyle,
        map: Bitmap? = null,
        logo: Bitmap? = null
    ) {
        if (content.isEmpty || width <= 0 || height <= 0) return
        val metrics = Metrics(min(width, height), style.fontScale, content.compact)
        val showMap = content.showMap
        val showLogo = content.showLogo && logo != null
        val qr = content.qrPayload?.let { runCatching { QrLocationEncoder.encode(it) }.getOrNull() }

        val maxCardWidth = width - 2 * metrics.margin
        val mapSize = if (showMap) metrics.mapSize else 0f
        val logoSize = if (showLogo) metrics.logoSize else 0f
        val qrSize = if (qr != null) metrics.qrSize else 0f
        val textMaxWidth = maxCardWidth - 2 * metrics.padding - mapSize.withGap(metrics) - logoSize.withGap(metrics) - qrSize.withGap(metrics)
        if (textMaxWidth <= metrics.body) return

        val rows = buildRows(content, style, metrics, textMaxWidth.toInt())
        val textHeight = rows.sumOf { it.height.toDouble() }.toFloat() +
            metrics.rowGap * (rows.size - 1).coerceAtLeast(0) + separatorSpace(rows, metrics)
        val innerHeight = maxOf(textHeight, mapSize, logoSize, qrSize)
        val cardHeight = innerHeight + 2 * metrics.padding
        val cardWidth = if (content.compact && !showMap) {
            val widest = rows.maxOfOrNull { it.contentWidth } ?: 0f
            min(maxCardWidth, widest + metrics.iconSize + metrics.iconGap + 2 * metrics.padding + logoSize.withGap(metrics) + qrSize.withGap(metrics))
        } else {
            maxCardWidth
        }

        val left = metrics.margin
        val top = when (style.position) {
            StampPosition.BOTTOM -> height - metrics.margin - cardHeight
            StampPosition.TOP -> metrics.margin
        }
        val card = RectF(left, top, left + cardWidth, top + cardHeight)
        drawCard(canvas, card, style, metrics)

        var contentLeft = card.left + metrics.padding
        if (showMap) {
            val mapTop = card.top + metrics.padding + (innerHeight - mapSize) / 2f
            val mapRect = RectF(contentLeft, mapTop, contentLeft + mapSize, mapTop + mapSize)
            if (content.mapPanel == MapPanel.TILE && map != null) {
                drawMap(canvas, map, mapRect, metrics)
            } else {
                drawCoordinatePanel(canvas, content.panelCoordinates, mapRect, style, metrics)
            }
            contentLeft += mapSize + metrics.columnGap
        }
        var rightEdge = card.right - metrics.padding
        if (qr != null) {
            // The QR's white square already contains its quiet zone, and text never enters this column.
            val qrTop = card.top + metrics.padding + (innerHeight - qrSize) / 2f
            QrDrawing.draw(canvas, qr, RectF(rightEdge - qrSize, qrTop, rightEdge, qrTop + qrSize))
            rightEdge -= qrSize + metrics.columnGap
        }
        if (showLogo) {
            val logoLeft = rightEdge - logoSize
            drawLogo(canvas, logo!!, RectF(logoLeft, card.top + metrics.padding, logoLeft + logoSize, card.top + metrics.padding + logoSize))
        }

        var y = card.top + metrics.padding + (innerHeight - textHeight) / 2f
        rows.forEachIndexed { index, row ->
            drawRow(canvas, row, contentLeft, y, metrics)
            y += row.height + metrics.rowGap
            if (index == 0 && row.isDate && rows.size > 1) {
                val lineY = y - metrics.rowGap / 2f + metrics.separatorSpace / 2f
                canvas.drawLine(
                    contentLeft,
                    lineY,
                    contentLeft + min(textMaxWidth, card.right - metrics.padding - contentLeft),
                    lineY,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = withAlpha(style.textColor, SEPARATOR_ALPHA)
                        strokeWidth = metrics.hairline
                    }
                )
                y += metrics.separatorSpace
            }
        }
    }

    /** Convenience for still images: draws onto a mutable bitmap in place. */
    fun renderOnto(bitmap: Bitmap, content: StampContent, style: StampStyle, map: Bitmap? = null, logo: Bitmap? = null) {
        require(bitmap.isMutable) { "Stamp target must be mutable" }
        render(Canvas(bitmap), bitmap.width, bitmap.height, content, style, map, logo)
    }

    private data class Row(
        val icon: StampIcon?,
        val layout: StaticLayout,
        val isDate: Boolean
    ) {
        val height: Float get() = layout.height.toFloat()
        val contentWidth: Float
            get() = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
    }

    private fun buildRows(content: StampContent, style: StampStyle, metrics: Metrics, maxWidth: Int): List<Row> {
        val iconSpace = (metrics.iconSize + metrics.iconGap).roundToInt()
        val textWidth = (maxWidth - iconSpace).coerceAtLeast(1)
        return buildList {
            content.dateTime?.let {
                add(Row(StampIcon.TIME, layout(it, paint(metrics.body, style.textColor, 1f, bold = true), textWidth, 1), isDate = true))
            }
            content.headline?.let {
                add(Row(StampIcon.PLACE, layout(it, paint(metrics.headline, style.textColor, 1f, bold = true), textWidth, 2), isDate = false))
            }
            content.details.forEach { line ->
                val alpha = if (line.emphasis == StampLine.Emphasis.PRIMARY) 0.95f else SECONDARY_ALPHA
                add(Row(line.icon, layout(line.text, paint(metrics.detail, style.textColor, alpha, bold = false), textWidth, 2), isDate = false))
            }
        }
    }

    private fun separatorSpace(rows: List<Row>, metrics: Metrics): Float =
        if (rows.size > 1 && rows.first().isDate) metrics.separatorSpace else 0f

    private fun drawRow(canvas: Canvas, row: Row, left: Float, top: Float, metrics: Metrics) {
        val icon = row.icon?.let(iconProvider)
        val firstLineHeight = row.layout.getLineBottom(0) - row.layout.getLineTop(0)
        if (icon != null) {
            val size = metrics.iconSize.roundToInt()
            val iconTop = (top + (firstLineHeight - size) / 2f).roundToInt()
            icon.mutate().setBounds(left.roundToInt(), iconTop, left.roundToInt() + size, iconTop + size)
            icon.alpha = (255 * ICON_ALPHA).roundToInt()
            icon.setTint(row.layout.paint.color or 0xFF000000.toInt())
            icon.draw(canvas)
        }
        canvas.save()
        canvas.translate(left + metrics.iconSize + metrics.iconGap, top)
        row.layout.draw(canvas)
        canvas.restore()
    }

    private fun drawCard(canvas: Canvas, card: RectF, style: StampStyle, metrics: Metrics) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((style.backgroundOpacity * 255).roundToInt(), 20, 20, 22)
        }
        canvas.drawRoundRect(card, metrics.corner, metrics.corner, fill)
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = metrics.hairline
            color = Color.argb(OUTLINE_ALPHA, 255, 255, 255)
        }
        canvas.drawRoundRect(card, metrics.corner, metrics.corner, outline)
    }

    private fun drawMap(canvas: Canvas, map: Bitmap, rect: RectF, metrics: Metrics) {
        val radius = metrics.corner * 0.7f
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        // Tile thumbnails carry their provider's attribution; see MapTileRenderer.
        canvas.drawBitmap(map, null, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()

        drawMarker(canvas, rect.centerX(), rect.centerY(), rect.width() * 0.07f)
    }

    /**
     * Honest placeholder for the map slot: a framed crosshair and the coordinates as text. It deliberately
     * contains no streets or terrain, so it cannot be mistaken for a real map.
     */
    private fun drawCoordinatePanel(canvas: Canvas, lines: List<String>, rect: RectF, style: StampStyle, metrics: Metrics) {
        val radius = metrics.corner * 0.7f
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) })
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = metrics.hairline * 1.5f
            color = withAlpha(style.textColor, 0.55f)
        }
        canvas.drawRoundRect(rect, radius, radius, stroke)
        val cx = rect.centerX()
        val cy = rect.top + rect.height() * 0.36f
        val ring = rect.width() * 0.13f
        canvas.drawCircle(cx, cy, ring, stroke)
        canvas.drawLine(cx - ring * 1.8f, cy, cx + ring * 1.8f, cy, stroke)
        canvas.drawLine(cx, cy - ring * 1.8f, cx, cy + ring * 1.8f, stroke)
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = rect.width() * 0.105f
            color = withAlpha(style.textColor, 0.95f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        lines.take(2).forEachIndexed { index, line ->
            val y = rect.top + rect.height() * (0.72f + index * 0.16f)
            canvas.drawText(line, cx, y, text)
        }
    }

    private fun drawMarker(canvas: Canvas, x: Float, y: Float, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.argb(70, 26, 115, 232)
        canvas.drawCircle(x, y, radius * 2.2f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(x, y, radius * 1.25f, paint)
        paint.color = MARKER_COLOR
        canvas.drawCircle(x, y, radius, paint)
    }

    private fun drawLogo(canvas: Canvas, logo: Bitmap, box: RectF) {
        val scale = min(box.width() / logo.width, box.height() / logo.height)
        val w = logo.width * scale
        val h = logo.height * scale
        val target = RectF(box.right - w, box.top, box.right, box.top + h)
        canvas.drawBitmap(logo, null, target, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    private fun paint(size: Float, color: Int, alpha: Float, bold: Boolean) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = withAlpha(color, alpha)
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.SANS_SERIF
        }

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.05f)
            .build()

    private fun Float.withGap(metrics: Metrics): Float = if (this > 0f) this + metrics.columnGap else 0f

    private fun withAlpha(color: Int, alpha: Float): Int =
        Color.argb((Color.alpha(color) * alpha).roundToInt(), Color.red(color), Color.green(color), Color.blue(color))

    /** All sizes in pixels, proportional to the image's short side. */
    private class Metrics(shortSide: Int, fontScale: Float, compact: Boolean) {
        private val unit = shortSide / 100f
        val body = unit * (if (compact) 2.7f else 2.9f) * fontScale
        val headline = body * 1.12f
        val detail = body * 0.92f
        val margin = unit * 3f
        val padding = body * 0.85f
        val corner = body * 0.9f
        val rowGap = body * 0.28f
        val columnGap = body * 0.8f
        val iconSize = body * 0.95f
        val iconGap = body * 0.45f
        val separatorSpace = body * 0.35f
        val hairline = max(1f, unit * 0.12f)
        val mapSize = body * 6.2f
        val logoSize = body * 2.6f

        /** Never below [QR_MIN_FRACTION] of the short side, so the code still scans after messaging apps shrink the photo. */
        val qrSize = max(mapSize, shortSide * QR_MIN_FRACTION)
    }

    private companion object {
        const val SECONDARY_ALPHA = 0.74f
        const val ICON_ALPHA = 0.8f
        const val SEPARATOR_ALPHA = 0.18f
        const val OUTLINE_ALPHA = 28
        const val QR_MIN_FRACTION = 0.18f
        val MARKER_COLOR = Color.rgb(26, 115, 232)
    }
}
