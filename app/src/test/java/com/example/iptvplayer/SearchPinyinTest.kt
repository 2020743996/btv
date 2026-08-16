package com.example.iptvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 拼音检索的键生成与匹配排序规则。
 * channelSearchKeys 内部会完成拼音库初始化（带多音字词典）。
 */
class SearchPinyinTest {

    @Test
    fun channelSearchKeys_中文频道生成全拼和首字母() {
        val keys = channelSearchKeys("湖南卫视")
        assertEquals("hunanweishi", keys.pinyinFull)
        assertEquals("hnws", keys.pinyinInitials)
    }

    @Test
    fun channelSearchKeys_中英混合保留字母数字() {
        val keys = channelSearchKeys("CCTV-1 综合")
        assertEquals("cctv1zonghe", keys.pinyinFull)
        assertEquals("cctv1zh", keys.pinyinInitials)
        // 归一化名沿用原有规则：去分隔符、去画质后缀
        assertEquals("cctv1综合", keys.normalized)
    }

    @Test
    fun channelSearchKeys_画质后缀从归一化名剥离() {
        val keys = channelSearchKeys("湖南卫视 高清")
        assertEquals("hunanweishigaoqing", keys.pinyinFull)
        assertEquals("hnwsgq", keys.pinyinInitials)
        assertEquals("湖南卫视", keys.normalized)
    }

    @Test
    fun matchTier_拼音全拼与首字母命中() {
        val keys = channelSearchKeys("湖南卫视")
        assertEquals(MatchTier.PINYIN, matchTier("hnws", keys))
        assertEquals(MatchTier.PINYIN, matchTier("hunanweishi", keys))
        assertEquals(MatchTier.PINYIN, matchTier("HUNAN", keys))
        assertNull(matchTier("zhejiang", keys))
    }

    @Test
    fun matchTier_中文名与字母名直接命中() {
        val cctv = channelSearchKeys("CCTV-1")
        assertEquals(MatchTier.PREFIX, matchTier("cctv", cctv))
        assertEquals(MatchTier.CONTAINS, matchTier("ctv", cctv))
        // 查询里的分隔符被忽略：c-c-t-v 等价于 cctv
        assertEquals(MatchTier.PREFIX, matchTier("C.C-T V", cctv))

        val hunan = channelSearchKeys("湖南卫视")
        assertEquals(MatchTier.PREFIX, matchTier("湖南", hunan))
        assertEquals(MatchTier.CONTAINS, matchTier("南卫", hunan))
    }

    @Test
    fun matchTier_空查询与空白查询不命中() {
        val keys = channelSearchKeys("湖南卫视")
        assertNull(matchTier("", keys))
        assertNull(matchTier("   ", keys))
    }
}
