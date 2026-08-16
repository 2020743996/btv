package com.example.iptvplayer

import com.github.promeg.pinyinhelper.Pinyin
import com.github.promeg.pinyinhelper.PinyinMapDict

/**
 * 拼音检索：让 TV 端的字母键盘能搜到中文频道名。
 *
 * 电视遥控器没法输入汉字，搜索页键盘只有字母和数字。
 * 这里给每个频道名预生成三组检索键：
 * - normalized：归一化名（原有规则，小写去符号去画质后缀）
 * - pinyinFull：全拼小写，"湖南卫视" → "hunanweishi"
 * - pinyinInitials：拼音首字母，"湖南卫视" → "hnws"；
 *   非汉字字符原样保留，"CCTV-1 综合" → "cctv1zh"
 *
 * 用户输入同样归一化后，三组键任一 contains 即命中，
 * 这样输入 hnws / hunanweishi / 湖南卫视 / cctv 都能搜到对应频道。
 *
 * 拼音转换用的是内嵌的 TinyPinyin（Apache 2.0，见
 * app/src/main/java/com/github/promeg/pinyinhelper/README.md）。
 */

data class ChannelSearchKeys(
    val normalized: String,
    val pinyinFull: String,
    val pinyinInitials: String
)

/** 命中等级：数字越小排越前（前缀命中 > 包含命中 > 仅拼音命中） */
enum class MatchTier(val rank: Int) {
    PREFIX(0),
    CONTAINS(1),
    PINYIN(2)
}

/**
 * 常见地名的多音字词典（频道名高频词）。
 * 单字转换取最常见读音，"重庆"会读成 zhong，这里按词修正。
 */
private object ChannelPolyphoneDict : PinyinMapDict() {
    override fun mapping(): Map<String, Array<String>> = mapOf(
        "重庆" to arrayOf("CHONG", "QING"),
        "长沙" to arrayOf("CHANG", "SHA"),
        "长春" to arrayOf("CHANG", "CHUN"),
        "厦门" to arrayOf("XIA", "MEN"),
        "成都" to arrayOf("CHENG", "DU"),
        "蚌埠" to arrayOf("BENG", "BU"),
        "乐山" to arrayOf("LE", "SHAN"),
        "六安" to arrayOf("LU", "AN"),
        "朝阳" to arrayOf("CHAO", "YANG")
    )
}

private var pinyinReady = false

/** 初始化拼音库，整个 App 只需一次；重复调用无副作用 */
@Synchronized
fun ensurePinyin() {
    if (pinyinReady) return
    Pinyin.init(Pinyin.newConfig().with(ChannelPolyphoneDict))
    pinyinReady = true
}

/** 生成一个频道名的全部检索键 */
fun channelSearchKeys(name: String): ChannelSearchKeys {
    ensurePinyin()
    // toPinyin 按空格分隔出逐个音节/字符，词典会修正多音字（重庆 → chong qing）
    val tokens = Pinyin.toPinyin(name, " ")
        .lowercase()
        .split(' ')
        .filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
    return ChannelSearchKeys(
        normalized = normalizeChannelName(name),
        pinyinFull = tokens.joinToString(""),
        pinyinInitials = tokens.joinToString("") { it.first().toString() }
    )
}

/** 查询词归一化：小写 + 去分隔符。不去画质后缀（用户可能就想搜"高清"） */
private val QUERY_SEPARATORS = Regex("[\\s\\-_.·、()（）]")

fun normalizeQuery(raw: String): String =
    QUERY_SEPARATORS.replace(raw.lowercase(), "").trim()

/** 查询是否命中；不命中返回 null。 */
fun matchTier(query: String, keys: ChannelSearchKeys): MatchTier? {
    val q = normalizeQuery(query)
    if (q.isEmpty()) return null
    return when {
        keys.normalized.startsWith(q) -> MatchTier.PREFIX
        keys.normalized.contains(q) -> MatchTier.CONTAINS
        keys.pinyinFull.contains(q) || keys.pinyinInitials.contains(q) -> MatchTier.PINYIN
        else -> null
    }
}
