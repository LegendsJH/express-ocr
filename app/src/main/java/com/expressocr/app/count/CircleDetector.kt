package com.expressocr.app.count

import android.graphics.Bitmap
import android.graphics.Rect
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Count packed circular ends.
 *
 * Strategy for chopsticks: isolate bright / low-saturation disks (stick ends),
 * find centers via distance-transform peaks, then keep only the densest compact cluster.
 * This rejects skin pores and wall texture that generic blob detectors pick up.
 */
object CircleDetector {

    fun detect(
        source: Bitmap,
        preset: CountPreset,
        roi: Rect? = null
    ): List<DetectedCircle> {
        var offsetX = 0f
        var offsetY = 0f
        var cropped: Bitmap? = null

        val working = if (roi != null && roi.width() > 8 && roi.height() > 8) {
            val safe = Rect(
                roi.left.coerceIn(0, source.width - 1),
                roi.top.coerceIn(0, source.height - 1),
                roi.right.coerceIn(1, source.width),
                roi.bottom.coerceIn(1, source.height)
            )
            if (safe.width() < 8 || safe.height() < 8) {
                source
            } else {
                offsetX = safe.left.toFloat()
                offsetY = safe.top.toFloat()
                Bitmap.createBitmap(source, safe.left, safe.top, safe.width(), safe.height())
                    .also { cropped = it }
            }
        } else {
            source
        }

        val rgba = Mat()
        val bgr = Mat()
        val gray = Mat()
        val grayWork = Mat()
        val mask = Mat()
        val dist = Mat()
        try {
            Utils.bitmapToMat(working, rgba)
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)

            val longSide = max(bgr.cols(), bgr.rows())
            val scale = if (longSide > 1280) 1280.0 / longSide else 1.0
            if (scale < 1.0) {
                Imgproc.resize(bgr, bgr, Size(), scale, scale, Imgproc.INTER_AREA)
            }
            val invScale = (1.0 / scale).toFloat()

            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(gray, grayWork)
            Imgproc.GaussianBlur(grayWork, grayWork, Size(3.0, 3.0), 0.8)

            buildEndMask(bgr, grayWork, mask, preset)

            val shortSide = min(grayWork.cols(), grayWork.rows())
            val (minR, maxR) = when (preset) {
                CountPreset.CHOPSTICK -> {
                    val r0 = max(3, (shortSide * 0.006).roundToInt())
                    val r1 = max(r0 + 3, (shortSide * 0.045).roundToInt())
                    r0 to r1
                }
                CountPreset.PIPE -> {
                    val r0 = max(6, (shortSide * 0.014).roundToInt())
                    val r1 = max(r0 + 4, (shortSide * 0.10).roundToInt())
                    r0 to r1
                }
            }

            Imgproc.distanceTransform(mask, dist, Imgproc.DIST_L2, Imgproc.DIST_MASK_5)
            var peaks = findPeaks(dist, grayWork, bgr, minR, maxR, preset)
            peaks = nms(peaks, 0.85f)

            // Estimate typical radius from strongest peaks, then re-peak with tighter size
            if (peaks.size >= 3) {
                val med = medianRadius(peaks)
                val tightMin = max(minR, (med * 0.55f).roundToInt())
                val tightMax = min(maxR, (med * 1.55f).roundToInt())
                if (tightMax > tightMin + 1) {
                    peaks = nms(findPeaks(dist, grayWork, bgr, tightMin, tightMax, preset), 0.85f)
                }
            }

            val clustered = keepDenseCluster(peaks)
            val validated = clustered.filter { validateDisk(grayWork, bgr, it, preset) }
            val chosen = if (validated.size >= max(3, clustered.size * 2 / 3)) validated else clustered

            val ox = offsetX
            val oy = offsetY
            return chosen
                .map {
                    DetectedCircle(
                        cx = it.cx * invScale + ox,
                        cy = it.cy * invScale + oy,
                        radius = max(it.radius * invScale, 3f)
                    )
                }
                .sortedWith(compareBy({ it.cy }, { it.cx }))
        } finally {
            rgba.release()
            bgr.release()
            gray.release()
            grayWork.release()
            mask.release()
            dist.release()
            cropped?.recycle()
        }
    }

    /**
     * Stick ends: bright + low saturation. Pipe holes: dark circular openings.
     * Skin and wallpaper are suppressed by HSV / morphology.
     */
    private fun buildEndMask(bgr: Mat, gray: Mat, outMask: Mat, preset: CountPreset) {
        val hsv = Mat()
        val bin = Mat()
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)

        when (preset) {
            CountPreset.CHOPSTICK -> {
                // Bright and not colorful (rejects skin which has higher S)
                Core.inRange(hsv, Scalar(0.0, 0.0, 140.0), Scalar(180.0, 80.0, 255.0), bin)
                // Also keep strong white-hat bright spots
                val hat = Mat()
                val k = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
                Imgproc.morphologyEx(gray, hat, Imgproc.MORPH_TOPHAT, k)
                val hatBin = Mat()
                Imgproc.threshold(hat, hatBin, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                Core.bitwise_or(bin, hatBin, bin)
                hat.release()
                hatBin.release()
                k.release()
            }
            CountPreset.PIPE -> {
                // Dark holes
                Core.inRange(hsv, Scalar(0.0, 0.0, 0.0), Scalar(180.0, 255.0, 90.0), bin)
                val hat = Mat()
                val k = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(21.0, 21.0))
                Imgproc.morphologyEx(gray, hat, Imgproc.MORPH_BLACKHAT, k)
                val hatBin = Mat()
                Imgproc.threshold(hat, hatBin, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
                Core.bitwise_or(bin, hatBin, bin)
                hat.release()
                hatBin.release()
                k.release()
            }
        }

        // Remove thin noise; fill small gaps inside disks
        val openK = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        val closeK = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(bin, outMask, Imgproc.MORPH_OPEN, openK)
        Imgproc.morphologyEx(outMask, outMask, Imgproc.MORPH_CLOSE, closeK)
        openK.release()
        closeK.release()

        hsv.release()
        bin.release()
    }

    private fun findPeaks(
        dist: Mat,
        gray: Mat,
        bgr: Mat,
        minR: Int,
        maxR: Int,
        preset: CountPreset
    ): List<DetectedCircle> {
        val minPeak = minR * 0.65
        val above = Mat()
        Core.compare(dist, Scalar(minPeak), above, Core.CMP_GT)

        val dilated = Mat()
        val kSize = max(3, ((minR * 1.8).roundToInt()) or 1)
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(kSize.toDouble(), kSize.toDouble())
        )
        Imgproc.dilate(dist, dilated, kernel)
        kernel.release()

        val peakMask = Mat()
        Core.compare(dist, dilated, peakMask, Core.CMP_EQ)
        Core.bitwise_and(peakMask, above, peakMask)
        above.release()
        dilated.release()

        if (Core.countNonZero(peakMask) == 0) {
            peakMask.release()
            return emptyList()
        }

        val coords = MatOfPoint()
        Core.findNonZero(peakMask, coords)
        peakMask.release()
        val pts = coords.toArray()
        coords.release()

        val out = mutableListOf<DetectedCircle>()
        for (p in pts) {
            val x = p.x.toInt()
            val y = p.y.toInt()
            val r = dist.get(y, x)[0].toFloat()
            if (r < minR * 0.75f || r > maxR * 1.2f) continue
            val c = DetectedCircle(x.toFloat(), y.toFloat(), r)
            if (!validateDisk(gray, bgr, c, preset)) continue
            out += c
        }
        return out
    }

    /** Reject skin / texture: need circular intensity ring and right brightness. */
    private fun validateDisk(gray: Mat, bgr: Mat, c: DetectedCircle, preset: CountPreset): Boolean {
        val cx = c.cx.roundToInt()
        val cy = c.cy.roundToInt()
        val r = max(2, c.radius.roundToInt())
        if (cx < r || cy < r || cx >= gray.cols() - r || cy >= gray.rows() - r) return false

        val rIn = max(1, (r * 0.4f).roundToInt())
        val rMid = max(rIn + 1, (r * 0.75f).roundToInt())
        var inSum = 0.0
        var inN = 0
        var ringSum = 0.0
        var ringN = 0
        var satSum = 0.0
        var satN = 0

        val x0 = cx - r
        val y0 = cy - r
        val x1 = cx + r
        val y1 = cy + r
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x - cx
                val dy = y - cy
                val d2 = dx * dx + dy * dy
                val g = gray.get(y, x)[0]
                when {
                    d2 <= rIn * rIn -> {
                        inSum += g
                        inN++
                        // BGR saturation proxy: max-min channel
                        val px = bgr.get(y, x)
                        val mx = maxOf(px[0], px[1], px[2])
                        val mn = minOf(px[0], px[1], px[2])
                        satSum += if (mx > 1) (mx - mn) / mx else 0.0
                        satN++
                    }
                    d2 in (rMid * rMid)..(r * r) -> {
                        ringSum += g
                        ringN++
                    }
                }
            }
        }
        if (inN < 3 || ringN < 4) return false
        val inMean = inSum / inN
        val ringMean = ringSum / ringN
        val sat = if (satN == 0) 0.0 else satSum / satN

        return when (preset) {
            CountPreset.CHOPSTICK -> {
                // Bright center, darker ring, low color (not skin)
                inMean >= 130 && (inMean - ringMean) >= 12 && sat < 0.35
            }
            CountPreset.PIPE -> {
                // Dark center relative to ring
                ringMean - inMean >= 15 && inMean < 110
            }
        }
    }

    /** Keep only the densest compact group (true stick bundle). */
    private fun keepDenseCluster(circles: List<DetectedCircle>): List<DetectedCircle> {
        if (circles.size <= 2) return circles
        val sized = run {
            val r = medianRadius(circles)
            circles.filter { it.radius in (r * 0.55f)..(r * 1.6f) }
        }
        val pool = if (sized.size >= 3) sized else circles
        val r = medianRadius(pool)
        // Tight neighborhood: sticks touch; skin/wall blobs are sparse
        val eps = max(r * 3.2f, 10f)

        val n = pool.size
        val parent = IntArray(n) { it }
        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }
        fun union(a: Int, b: Int) {
            val pa = find(a)
            val pb = find(b)
            if (pa != pb) parent[pa] = pb
        }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = pool[i].cx - pool[j].cx
                val dy = pool[i].cy - pool[j].cy
                if (sqrt(dx * dx + dy * dy) <= eps) union(i, j)
            }
        }

        val groups = pool.indices.groupBy { find(it) }.map { (_, idxs) -> idxs.map { pool[it] } }
        // Prefer group with highest density (count / bounding area)
        val best = groups.maxWithOrNull(
            compareBy<List<DetectedCircle>> { it.size }
                .thenByDescending { g ->
                    if (g.size < 2) return@thenByDescending 0.0
                    val w = (g.maxOf { it.cx } - g.minOf { it.cx }).coerceAtLeast(1f)
                    val h = (g.maxOf { it.cy } - g.minOf { it.cy }).coerceAtLeast(1f)
                    g.size / (w * h).toDouble()
                }
        ) ?: return pool

        if (best.size < 3) return best

        // Drop outliers still attached at the edge of the cluster
        val cx = best.map { it.cx }.average().toFloat()
        val cy = best.map { it.cy }.average().toFloat()
        val dists = best.map { sqrt((it.cx - cx) * (it.cx - cx) + (it.cy - cy) * (it.cy - cy)) }.sorted()
        val medDist = dists[dists.size / 2]
        val maxDist = max(medDist * 2.0f, r * 8f)
        return best.filter {
            sqrt((it.cx - cx) * (it.cx - cx) + (it.cy - cy) * (it.cy - cy)) <= maxDist
        }
    }

    private fun medianRadius(circles: List<DetectedCircle>): Float {
        val s = circles.map { it.radius }.sorted()
        return s[s.size / 2]
    }

    private fun nms(circles: List<DetectedCircle>, overlap: Float): List<DetectedCircle> {
        if (circles.isEmpty()) return circles
        val sorted = circles.sortedByDescending { it.radius }
        val kept = mutableListOf<DetectedCircle>()
        for (c in sorted) {
            val hit = kept.any { k ->
                val dx = c.cx - k.cx
                val dy = c.cy - k.cy
                sqrt(dx * dx + dy * dy) < overlap * (c.radius + k.radius)
            }
            if (!hit) kept += c
        }
        return kept
    }
}
