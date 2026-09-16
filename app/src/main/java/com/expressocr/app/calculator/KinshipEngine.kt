package com.expressocr.app.calculator

/**
 * Chinese kinship title resolver inspired by mumuy/relationship.
 * Supports chain simplification + multi-generation lookup + reverse titles.
 *
 * Codes: f父 m母 h夫 w妻 s子 d女 ob兄 yb弟 os姐 ys妹
 */
object KinshipEngine {

    data class Result(val callThem: String, val callMe: String)

    private val stepLabel = mapOf(
        "f" to "爸爸", "m" to "妈妈", "h" to "老公", "w" to "老婆",
        "s" to "儿子", "d" to "女儿",
        "ob" to "哥哥", "yb" to "弟弟", "os" to "姐姐", "ys" to "妹妹"
    )

    /**
     * Pair expansions / reductions applied repeatedly until stable.
     * Ported from common relationship-selector rules.
     */
    private val reduceRules: List<Pair<List<String>, List<String>>> = listOf(
        listOf("f", "w") to listOf("m"),
        listOf("m", "h") to listOf("f"),
        listOf("h", "w") to emptyList(),
        listOf("w", "h") to emptyList(),
        listOf("s", "w", "h") to listOf("s"),
        listOf("d", "h", "w") to listOf("d"),
        listOf("ob", "f") to listOf("f"),
        listOf("yb", "f") to listOf("f"),
        listOf("os", "f") to listOf("f"),
        listOf("ys", "f") to listOf("f"),
        listOf("ob", "m") to listOf("m"),
        listOf("yb", "m") to listOf("m"),
        listOf("os", "m") to listOf("m"),
        listOf("ys", "m") to listOf("m")
    )

    /** Forward titles: chain key → (maleSelf title, femaleSelf title) — usually same */
    private val forward: Map<String, String> = buildMap {
        fun put(key: String, name: String) { this[key] = name }

        put("", "自己")
        put("f", "爸爸"); put("m", "妈妈"); put("h", "老公"); put("w", "老婆")
        put("s", "儿子"); put("d", "女儿")
        put("ob", "哥哥"); put("yb", "弟弟"); put("os", "姐姐"); put("ys", "妹妹")

        // 祖辈
        put("f,f", "爷爷"); put("f,m", "奶奶"); put("m,f", "外公"); put("m,m", "外婆")
        put("f,f,f", "曾祖父"); put("f,f,m", "曾祖母")
        put("f,m,f", "曾外祖父"); put("f,m,m", "曾外祖母")
        put("m,f,f", "外曾祖父"); put("m,f,m", "外曾祖母")
        put("m,m,f", "外曾外祖父"); put("m,m,m", "外曾外祖母")
        put("f,f,f,f", "高祖父"); put("f,f,f,m", "高祖母")

        // 父母的兄弟姐妹
        put("f,ob", "伯父"); put("f,yb", "叔叔"); put("f,os", "姑妈"); put("f,ys", "姑妈")
        put("f,ob,w", "伯母"); put("f,yb,w", "婶婶")
        put("f,os,h", "姑父"); put("f,ys,h", "姑父")
        put("m,ob", "舅舅"); put("m,yb", "舅舅"); put("m,os", "姨妈"); put("m,ys", "姨妈")
        put("m,ob,w", "舅妈"); put("m,yb,w", "舅妈")
        put("m,os,h", "姨父"); put("m,ys,h", "姨父")

        // 堂表
        put("f,ob,s", "堂兄弟"); put("f,ob,d", "堂姐妹")
        put("f,yb,s", "堂兄弟"); put("f,yb,d", "堂姐妹")
        put("f,os,s", "表兄弟"); put("f,os,d", "表姐妹")
        put("f,ys,s", "表兄弟"); put("f,ys,d", "表姐妹")
        put("m,ob,s", "表兄弟"); put("m,ob,d", "表姐妹")
        put("m,yb,s", "表兄弟"); put("m,yb,d", "表姐妹")
        put("m,os,s", "表兄弟"); put("m,os,d", "表姐妹")
        put("m,ys,s", "表兄弟"); put("m,ys,d", "表姐妹")
        put("f,ob,s,w", "堂嫂/堂弟媳"); put("f,yb,s,w", "堂嫂/堂弟媳")
        put("f,ob,d,h", "堂姐夫/堂妹夫"); put("f,yb,d,h", "堂姐夫/堂妹夫")
        put("f,os,s,w", "表嫂/表弟媳"); put("f,ys,s,w", "表嫂/表弟媳")
        put("m,ob,s,w", "表嫂/表弟媳"); put("m,os,s,w", "表嫂/表弟媳")

        // 兄弟姐妹的子女 / 配偶
        put("ob,s", "侄子"); put("ob,d", "侄女"); put("yb,s", "侄子"); put("yb,d", "侄女")
        put("os,s", "外甥"); put("os,d", "外甥女"); put("ys,s", "外甥"); put("ys,d", "外甥女")
        put("ob,w", "嫂子"); put("yb,w", "弟妹"); put("os,h", "姐夫"); put("ys,h", "妹夫")
        put("ob,s,w", "侄媳"); put("ob,d,h", "侄女婿")
        put("yb,s,w", "侄媳"); put("yb,d,h", "侄女婿")
        put("os,s,w", "外甥媳"); put("os,d,h", "外甥女婿")
        put("ys,s,w", "外甥媳"); put("ys,d,h", "外甥女婿")
        put("ob,s,s", "侄孙"); put("ob,s,d", "侄孙女")
        put("yb,s,s", "侄孙"); put("yb,s,d", "侄孙女")
        put("os,s,s", "外甥孙"); put("os,s,d", "外甥孙女")

        // 子女 / 孙
        put("s,w", "儿媳"); put("d,h", "女婿")
        put("s,s", "孙子"); put("s,d", "孙女"); put("d,s", "外孙"); put("d,d", "外孙女")
        put("s,s,w", "孙媳"); put("s,d,h", "孙女婿")
        put("d,s,w", "外孙媳"); put("d,d,h", "外孙女婿")
        put("s,s,s", "曾孙"); put("s,s,d", "曾孙女")
        put("s,d,s", "曾外孙"); put("s,d,d", "曾外孙女")
        put("d,s,s", "外曾孙"); put("d,s,d", "外曾孙女")
        put("d,d,s", "外曾外孙"); put("d,d,d", "外曾外孙女")

        // 配偶父母 / 兄弟姐妹
        put("h,f", "公公"); put("h,m", "婆婆"); put("w,f", "岳父"); put("w,m", "岳母")
        put("h,ob", "大伯子"); put("h,yb", "小叔子"); put("h,os", "大姑子"); put("h,ys", "小姑子")
        put("w,ob", "大舅子"); put("w,yb", "小舅子"); put("w,os", "大姨子"); put("w,ys", "小姨子")
        put("h,ob,w", "妯娌"); put("h,yb,w", "妯娌")
        put("w,os,h", "连襟"); put("w,ys,h", "连襟")
        put("h,f,f", "祖公公"); put("h,f,m", "祖婆婆")
        put("w,f,f", "太岳父"); put("w,f,m", "太岳母")
        put("h,ob,s", "侄子"); put("h,ob,d", "侄女") // 从夫侧
        put("w,ob,s", "外甥"); put("w,ob,d", "外甥女")

        // 父母的堂表 → 堂伯/表舅等
        put("f,f,ob", "伯祖父"); put("f,f,yb", "叔祖父")
        put("f,f,os", "姑奶奶"); put("f,f,ys", "姑奶奶")
        put("f,m,ob", "舅公"); put("f,m,yb", "舅公")
        put("f,m,os", "姨奶奶"); put("f,m,ys", "姨奶奶")
        put("m,f,ob", "伯外祖父"); put("m,f,yb", "叔外祖父")
        put("m,f,os", "姑外婆"); put("m,f,ys", "姑外婆")
        put("m,m,ob", "舅外公"); put("m,m,yb", "舅外公")
        put("m,m,os", "姨外婆"); put("m,m,ys", "姨外婆")
        put("f,f,ob,s", "堂伯/堂叔"); put("f,f,yb,s", "堂伯/堂叔")
        put("f,f,os,s", "表伯/表叔"); put("f,f,ys,s", "表伯/表叔")
        put("m,m,ob,s", "表舅"); put("m,m,os,s", "表姨")

        // 配偶的祖父母
        put("h,f,ob", "伯公公"); put("h,f,yb", "叔公公")
        put("w,f,ob", "伯岳父"); put("w,f,yb", "叔岳父")
    }

    /** Reverse titles keyed by chain; value = maleSelf to femaleSelf */
    private val reverse: Map<String, Pair<String, String>> = buildMap {
        fun put(key: String, male: String, female: String = male) {
            this[key] = male to female
        }
        put("f", "儿子", "女儿"); put("m", "儿子", "女儿")
        put("h", "老婆"); put("w", "老公")
        put("s", "爸爸", "妈妈"); put("d", "爸爸", "妈妈")
        put("ob", "弟弟", "妹妹"); put("yb", "哥哥", "姐姐")
        put("os", "弟弟", "妹妹"); put("ys", "哥哥", "姐姐")

        put("f,f", "孙子", "孙女"); put("f,m", "孙子", "孙女")
        put("m,f", "外孙", "外孙女"); put("m,m", "外孙", "外孙女")
        put("f,f,f", "曾孙", "曾孙女"); put("f,f,m", "曾孙", "曾孙女")

        put("f,ob", "侄子", "侄女"); put("f,yb", "侄子", "侄女")
        put("f,os", "外甥", "外甥女"); put("f,ys", "外甥", "外甥女")
        put("m,ob", "外甥", "外甥女"); put("m,yb", "外甥", "外甥女")
        put("m,os", "外甥", "外甥女"); put("m,ys", "外甥", "外甥女")
        put("f,ob,w", "侄子", "侄女"); put("f,yb,w", "侄子", "侄女")
        put("f,os,h", "外甥", "外甥女"); put("m,ob,w", "外甥", "外甥女")

        put("f,ob,s", "堂兄弟", "堂姐妹"); put("f,ob,d", "堂兄弟", "堂姐妹")
        put("f,yb,s", "堂兄弟", "堂姐妹"); put("f,yb,d", "堂兄弟", "堂姐妹")
        put("f,os,s", "表兄弟", "表姐妹"); put("f,os,d", "表兄弟", "表姐妹")
        put("f,ys,s", "表兄弟", "表姐妹"); put("f,ys,d", "表兄弟", "表姐妹")
        put("m,ob,s", "表兄弟", "表姐妹"); put("m,ob,d", "表兄弟", "表姐妹")
        put("m,yb,s", "表兄弟", "表姐妹"); put("m,yb,d", "表兄弟", "表姐妹")
        put("m,os,s", "表兄弟", "表姐妹"); put("m,os,d", "表兄弟", "表姐妹")
        put("m,ys,s", "表兄弟", "表姐妹"); put("m,ys,d", "表兄弟", "表姐妹")

        put("ob,s", "叔叔", "姑姑"); put("ob,d", "叔叔", "姑姑")
        put("yb,s", "伯父", "姑妈"); put("yb,d", "伯父", "姑妈")
        put("os,s", "舅舅", "姨妈"); put("os,d", "舅舅", "姨妈")
        put("ys,s", "舅舅", "姨妈"); put("ys,d", "舅舅", "姨妈")
        put("ob,w", "小叔子", "小姑子"); put("yb,w", "大伯子", "大姑子")
        this["os,h"] = "内弟" to "姨妹"
        this["ys,h"] = "内兄" to "姨姐"
        put("ob,s,w", "叔公", "姑奶"); put("ob,d,h", "叔公", "姑奶")

        put("s,s", "爷爷", "奶奶"); put("s,d", "爷爷", "奶奶")
        put("d,s", "外公", "外婆"); put("d,d", "外公", "外婆")
        put("s,w", "公公", "婆婆"); put("d,h", "岳父", "岳母")
        put("s,s,s", "曾祖父", "曾祖母"); put("s,s,d", "曾祖父", "曾祖母")
        put("s,s,w", "祖公公", "祖婆婆")

        put("h,f", "儿媳"); put("h,m", "儿媳")
        put("w,f", "女婿"); put("w,m", "女婿")
        this["h,ob"] = "弟妹" to "弟妹"
        this["h,yb"] = "嫂子" to "嫂子"
        this["h,os"] = "弟妇" to "弟妇"
        this["h,ys"] = "嫂" to "嫂"
        this["w,ob"] = "姐夫" to "姐夫"
        this["w,yb"] = "姐夫" to "姐夫"
        this["w,os"] = "妹夫" to "妹夫"
        this["w,ys"] = "妹夫" to "妹夫"
        put("h,ob,w", "妯娌"); put("w,os,h", "连襟")

        put("f,f,ob", "侄孙", "侄孙女"); put("f,f,yb", "侄孙", "侄孙女")
        put("f,f,os", "侄孙", "侄孙女"); put("m,m,ob", "外甥孙", "外甥孙女")
    }

    val keypad: List<Pair<String, String>> = listOf(
        "f" to "爸", "m" to "妈", "h" to "夫", "w" to "妻",
        "ob" to "兄", "yb" to "弟", "os" to "姐", "ys" to "妹",
        "s" to "子", "d" to "女"
    )

    fun labelOf(step: String): String = stepLabel[step] ?: step

    fun resolve(chain: List<String>, selfIsMale: Boolean): Result {
        if (chain.isEmpty()) return Result("自己", "自己")
        // Gender-inconsistent spouse shortcuts
        if (!selfIsMale && chain.first() == "w") {
            return Result("暂无标准称呼", "暂无标准称呼")
        }
        if (selfIsMale && chain.first() == "h") {
            return Result("暂无标准称呼", "暂无标准称呼")
        }

        val normalized = normalize(chain)
        val key = normalized.joinToString(",")
        val them = forward[key]
            ?: synthesize(normalized)
            ?: "暂无标准称呼"

        val mePair = reverse[key]
        val me = when {
            mePair != null -> if (selfIsMale) mePair.first else mePair.second
            them == "暂无标准称呼" -> "暂无标准称呼"
            else -> reverseSynthesize(normalized, selfIsMale) ?: "暂无标准称呼"
        }
        return Result(them, me)
    }

    private fun normalize(chain: List<String>): List<String> {
        var cur = chain.toMutableList()
        var changed = true
        var guard = 0
        while (changed && guard++ < 20) {
            changed = false
            for ((from, to) in reduceRules) {
                val idx = indexOfSublist(cur, from)
                if (idx >= 0) {
                    repeat(from.size) { cur.removeAt(idx) }
                    cur.addAll(idx, to)
                    changed = true
                    break
                }
            }
        }
        return cur
    }

    private fun indexOfSublist(list: List<String>, sub: List<String>): Int {
        if (sub.isEmpty() || sub.size > list.size) return -1
        for (i in 0..list.size - sub.size) {
            if (list.subList(i, i + sub.size) == sub) return i
        }
        return -1
    }

    private fun synthesize(chain: List<String>): String? {
        if (chain.isEmpty()) return "自己"
        if (chain.size == 1) return stepLabel[chain[0]]
        for (len in chain.size - 1 downTo 1) {
            val prefix = chain.take(len).joinToString(",")
            val prefixName = forward[prefix] ?: continue
            val last = labelOf(chain.last())
            return "${prefixName}的$last"
        }
        return null
    }

    private fun reverseSynthesize(chain: List<String>, selfIsMale: Boolean): String? {
        if (chain.size == 1) return invertStep(chain[0], selfIsMale)
        return null
    }

    private fun invertStep(step: String, selfIsMale: Boolean): String? = when (step) {
        "f", "m" -> if (selfIsMale) "儿子" else "女儿"
        "h" -> "老婆"
        "w" -> "老公"
        "s", "d" -> if (selfIsMale) "爸爸" else "妈妈"
        "ob", "os" -> if (selfIsMale) "弟弟" else "妹妹"
        "yb", "ys" -> if (selfIsMale) "哥哥" else "姐姐"
        else -> null
    }
}
