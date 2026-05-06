package com.liaobusi.stockman

import com.liaobusi.stockman.db.HistoryStock
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 两只股票在给定区间内日收盘对齐后的走势拟合度：对数收益 Pearson / Spearman、涨跌符号一致率、OLS β 与 R²。
 */
object StockReturnCorrelation {

    const val MIN_RETURNS_FOR_METRICS = 10

    data class AlignedClose(val date: Int, val closeA: Float, val closeB: Float)

    data class FitResult(
        val tradingDaysAligned: Int,
        val returnSamples: Int,
        val pearson: Double?,
        val spearman: Double?,
        val signAgreement: Double?,
        val betaYOnX: Double?,
        val rSquared: Double?,
        val totalReturnA: Double?,
        val totalReturnB: Double?,
        val fitLabel: String,
    )

    fun alignCloses(a: List<HistoryStock>, b: List<HistoryStock>): List<AlignedClose> {
        val mapA = a.associateBy { it.date }
        val mapB = b.associateBy { it.date }
        val dates = mapA.keys.intersect(mapB.keys).sorted()
        return dates.map { d ->
            AlignedClose(d, mapA.getValue(d).closePrice, mapB.getValue(d).closePrice)
        }
    }

    /**
     * 对数收益 ln(C_t/C_{t-1})，长度 = aligned.size - 1。
     */
    fun logReturnsA(aligned: List<AlignedClose>): DoubleArray {
        if (aligned.size < 2) return DoubleArray(0)
        val out = DoubleArray(aligned.size - 1)
        for (i in 1 until aligned.size) {
            out[i - 1] = ln(aligned[i].closeA.toDouble() / aligned[i - 1].closeA.toDouble())
        }
        return out
    }

    fun logReturnsB(aligned: List<AlignedClose>): DoubleArray {
        if (aligned.size < 2) return DoubleArray(0)
        val out = DoubleArray(aligned.size - 1)
        for (i in 1 until aligned.size) {
            out[i - 1] = ln(aligned[i].closeB.toDouble() / aligned[i - 1].closeB.toDouble())
        }
        return out
    }

    /**
     * 同时对 A/B 算对数收益，跳过收盘价非正的区间，避免 ln 产生 NaN/∞ 导致下游死循环或脏指标。
     */
    fun alignedLogReturnsPaired(aligned: List<AlignedClose>): Pair<DoubleArray, DoubleArray> {
        if (aligned.size < 2) return DoubleArray(0) to DoubleArray(0)
        val ra = ArrayList<Double>(aligned.size - 1)
        val rb = ArrayList<Double>(aligned.size - 1)
        for (i in 1 until aligned.size) {
            val p0a = aligned[i - 1].closeA.toDouble()
            val p1a = aligned[i].closeA.toDouble()
            val p0b = aligned[i - 1].closeB.toDouble()
            val p1b = aligned[i].closeB.toDouble()
            if (p0a <= 0.0 || p1a <= 0.0 || p0b <= 0.0 || p1b <= 0.0) continue
            if (!p0a.isFinite() || !p1a.isFinite() || !p0b.isFinite() || !p1b.isFinite()) continue
            ra.add(ln(p1a / p0a))
            rb.add(ln(p1b / p0b))
        }
        return ra.toDoubleArray() to rb.toDoubleArray()
    }

    fun pearson(x: DoubleArray, y: DoubleArray): Double? {
        if (x.size != y.size || x.size < 2) return null
        val n = x.size
        val mx = x.average()
        val my = y.average()
        var num = 0.0
        var vx = 0.0
        var vy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - mx
            val dy = y[i] - my
            num += dx * dy
            vx += dx * dx
            vy += dy * dy
        }
        if (vx == 0.0 || vy == 0.0) return null
        return num / sqrt(vx * vy)
    }

    /** 平均秩（含并列）后做 Pearson。 */
    fun spearman(x: DoubleArray, y: DoubleArray): Double? {
        if (x.size != y.size || x.size < 2) return null
        return pearson(averageRanks(x), averageRanks(y))
    }

    private fun sameValueForRank(a: Double, b: Double): Boolean {
        if (a.isNaN() && b.isNaN()) return true
        return a == b
    }

    private fun averageRanks(values: DoubleArray): DoubleArray {
        val n = values.size
        val order = values.indices.sortedWith(compareBy({ values[it].isNaN() }, { values[it] }))
        val ranks = DoubleArray(n)
        var i = 0
        while (i < n) {
            var j = i
            val v = values[order[i]]
            while (j < n && sameValueForRank(values[order[j]], v)) j++
            val avg = (i + j + 1) / 2.0
            for (k in i until j) ranks[order[k]] = avg
            i = j
        }
        return ranks
    }

    /** 涨跌符号一致比例（收益为 0 的样本不计入分母）。 */
    fun signAgreement(x: DoubleArray, y: DoubleArray): Double? {
        if (x.size != y.size || x.isEmpty()) return null
        var denom = 0
        var agree = 0
        for (i in x.indices) {
            val sx = x[i].compareTo(0.0)
            val sy = y[i].compareTo(0.0)
            if (sx == 0 || sy == 0) continue
            denom++
            if (sx == sy) agree++
        }
        if (denom == 0) return null
        return agree.toDouble() / denom
    }

    /**
     * y 对 x 回归（带截距）：y = α + β x；返回 β 与 R²。
     */
    fun olsBetaAndRSquared(y: DoubleArray, x: DoubleArray): Pair<Double, Double>? {
        if (x.size != y.size || x.size < 2) return null
        val n = x.size
        val mx = x.average()
        val my = y.average()
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = x[i] - mx
            val dy = y[i] - my
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        if (sxx == 0.0) return null
        val beta = sxy / sxx
        val alpha = my - beta * mx
        var sse = 0.0
        for (i in 0 until n) {
            val e = y[i] - alpha - beta * x[i]
            sse += e * e
        }
        if (syy == 0.0) return null
        val r2 = 1.0 - sse / syy
        return Pair(beta, r2)
    }

    fun totalSimpleReturn(aligned: List<AlignedClose>, useA: Boolean): Double? {
        if (aligned.isEmpty()) return null
        val first = if (useA) aligned.first().closeA else aligned.first().closeB
        val last = if (useA) aligned.last().closeA else aligned.last().closeB
        if (first <= 0f) return null
        return (last - first) / first.toDouble()
    }

    private fun labelForPearson(r: Double?): String {
        if (r == null) return "无法判定（方差过小或无重叠收益）"
        val a = kotlin.math.abs(r)
        return when {
            a >= 0.7 -> "强相关（|ρ|≥0.7）"
            a >= 0.4 -> "中等相关（0.4≤|ρ|<0.7）"
            else -> "弱相关（|ρ|<0.4）"
        }
    }

    private fun buildAlignedFromAnchorMap(
        anchorByDate: Map<Int, HistoryStock>,
        b: List<HistoryStock>,
    ): List<AlignedClose> {
        val bMap = b.associateBy { it.date }
        val dates = anchorByDate.keys.intersect(bMap.keys).sorted()
        return dates.map { d ->
            AlignedClose(d, anchorByDate.getValue(d).closePrice, bMap.getValue(d).closePrice)
        }
    }

    /** 与 [compute] 相同，但 anchor 已按 date 建索引，批量扫描时避免重复 associate。 */
    fun computeWithAnchorByDate(anchorByDate: Map<Int, HistoryStock>, b: List<HistoryStock>): FitResult {
        return computeFromAligned(buildAlignedFromAnchorMap(anchorByDate, b))
    }

    fun compute(a: List<HistoryStock>, b: List<HistoryStock>): FitResult {
        return computeFromAligned(alignCloses(a, b))
    }

    private fun computeFromAligned(aligned: List<AlignedClose>): FitResult {
        val (ra, rb) = alignedLogReturnsPaired(aligned)
        val nRet = ra.size
        if (nRet < MIN_RETURNS_FOR_METRICS) {
            return FitResult(
                tradingDaysAligned = aligned.size,
                returnSamples = nRet,
                pearson = null,
                spearman = null,
                signAgreement = if (nRet > 0) signAgreement(ra, rb) else null,
                betaYOnX = null,
                rSquared = null,
                totalReturnA = totalSimpleReturn(aligned, true),
                totalReturnB = totalSimpleReturn(aligned, false),
                fitLabel = "有效对数收益样本不足（需≥$MIN_RETURNS_FOR_METRICS），仅作参考",
            )
        }
        val p = pearson(ra, rb)
        val s = spearman(ra, rb)
        val agree = signAgreement(ra, rb)
        val ols = olsBetaAndRSquared(ra, rb)
        return FitResult(
            tradingDaysAligned = aligned.size,
            returnSamples = nRet,
            pearson = p,
            spearman = s,
            signAgreement = agree,
            betaYOnX = ols?.first,
            rSquared = ols?.second,
            totalReturnA = totalSimpleReturn(aligned, true),
            totalReturnB = totalSimpleReturn(aligned, false),
            fitLabel = labelForPearson(p),
        )
    }
}
