package com.example.iptvplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * AddressActivity：源地址管理（管理员模式子页面）。
 * 内容就是 AddressEditor：查看/添加/修改/删除 M3U 地址。
 */
class AddressActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IptvPlayerTheme(fontScale = fontScaleFor(getFontSize(this))) {
                AddressEditor(
                    initialUrls = getM3uUrls(this),
                    onSave = { urls ->
                        saveM3uUrls(this, urls)
                        SourceStatuses.channels = SourceStatuses.channels.filter { it.url in urls }
                        AppLog.log("保存源地址：${urls.size} 个")
                    },
                    onSourceLoad = { url -> loadChannelSource(this, url).status },
                    onBack = { finish() }
                )
            }
        }
    }
}
