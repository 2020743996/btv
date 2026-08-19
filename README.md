# btv - Android IPTV 直播播放器

btv 是一款面向手机、平板和 Android TV 的轻量 IPTV 播放器。界面以频道识别和遥控器操作为中心，不内置频道源，也不提供任何音视频内容。

## 下载

- 在 [GitHub Releases](https://github.com/2020743996/btv/releases) 下载最新的 `app-release.apk`
- 支持 Android 8.0（API 26）及以上设备
- 支持触摸屏、电视遥控器和无系统输入法的电视盒子

## 首次使用

首次打开且没有频道源时，首页会显示“添加频道源”入口，直接进入 M3U 地址管理。保存地址后返回首页，应用会自动加载频道。

btv 不提供默认播放源。请只添加你有权使用的 M3U 播放列表。

## 主要功能

- **白色液态玻璃界面**：以纯白内容层为主，工具栏、分组和播放浮层使用轻量玻璃质感；蓝色只用于操作和焦点，红色只用于直播与危险状态
- **轻量线性图标**：内置实际使用的 Lucide 风格矢量路径，统一使用克制的单色操作体系
- **一致的操作层级**：返回固定在左侧，页面操作固定在右侧；表单、弹窗和列表行复用统一的主次与危险操作顺序
- **可靠的源地址编辑**：地址列表和输入区域独立滚动，保存/添加固定在底部安全区，系统键盘完成键也可直接提交
- **手机与电视自适应 UI**：手机使用紧凑顶部工具栏和横向分组；电视、平板使用左侧分组导航与清晰的遥控器焦点
- **横屏防溢出**：手机横屏保持单栏频道列表，标题栏操作空间不足时自动换到下一行并保持右对齐
- **频道台标**：解析 M3U 的 `tvg-logo`，异步加载并缓存；透明台标不会透字，浅色台标会自动切换到深色中性底
- **稳定直播播放**：使用 10~40 秒弹性缓冲、OkHttp 连接池、分片重试和独立的首连/卡顿恢复超时，弱网下减少反复停顿
- **智能线路恢复**：连续稳定播放后才确认线路可用，发生长时间卡顿时自动切换备用线路，下次优先历史更稳定的线路
- **拼音搜台**：支持中文名、全拼和首字母搜索，例如 `hnws` 可匹配“湖南卫视”
- **多源多线路**：多个 M3U 源中的同名频道自动合并，播放失败后自动尝试备用线路
- **播放健康记录**：播放成功会清除该线路的失败记录；超时、错误或中断会自动累计并进入失效管理
- **EPG 节目单**：解析 XMLTV，在频道列表与播放浮层显示当前节目，并在播放页显示下一节目及开始时间
- **收藏与最近观看**：支持收藏频道、最近观看分组和冷启动续播
- **线路测速与分辨率**：从 HLS 清单、线路 URL 和 M3U 频道标签识别清晰度；同一频道优先高分辨率线路，同分辨率再优先更快线路
- **老人模式**：隐藏高级操作，并提供标准、大、特大三档字体

## M3U 台标格式

台标地址使用常见的 `tvg-logo` 属性：

```m3u
#EXTM3U x-tvg-url="https://example.com/epg.xml"
#EXTINF:-1 tvg-id="cctv1" tvg-logo="https://example.com/logo/cctv1.png" group-title="央视",CCTV-1
https://example.com/live/cctv1.m3u8
```

建议使用 HTTPS 的 PNG、JPEG 或 WebP 图片。台标仅用于显示，不会影响频道播放；图片由 Coil 自动进行内存与磁盘缓存。

## 操作方式

### Android TV

- 上 / 下：切换频道
- 左：打开播放页频道面板
- OK：播放选中的频道
- 信息键 / 菜单键：重新显示频道与节目浮层
- 返回键：关闭面板或返回频道列表

### 手机和平板

- 点击频道行开始播放
- 点击心形图标收藏或取消收藏
- 顶部图标用于搜索、刷新、测速和设置

## 构建

Android Studio 可直接打开项目运行，也可以使用命令行：

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

### Release 签名

签名配置放在不提交到仓库的 `keystore.properties`：

```properties
storeFile=keystore/your-release.jks
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

没有该文件时仍可执行 `./gradlew assembleRelease`，但生成的是未签名 APK。

## 项目结构

```text
app/src/main/java/com/example/iptvplayer/
|-- MainActivity.kt        # 首页、分组与频道列表
|-- PlayerActivity.kt      # 播放、换台、线路切换与播放浮层
|-- PlaybackTuning.kt      # 直播缓冲、超时、重试与线路优先级
|-- SearchActivity.kt      # 中文与拼音搜索
|-- UiComponents.kt        # 台标、工具栏与页面标题组件
|-- UiActions.kt           # 操作层级、表单底栏与行操作组件
|-- UiIcons.kt             # 轻量线性图标与功能色
|-- Theme.kt               # 自适应主题、颜色、字号与尺寸
|-- M3uParser.kt           # M3U、tvg-logo 与频道合并
|-- EpgParser.kt           # XMLTV 当前/下一节目
|-- LineTester.kt          # 线路测速与失效判定
|-- Settings.kt            # 本地设置、收藏、历史与失败记录
|-- AddressEditor.kt       # M3U 地址管理
|-- SettingsActivity.kt    # 老人模式与字体设置
|-- AdminActivity.kt       # 频道管理入口
|-- DeadChannelActivity.kt # 失效线路管理
`-- LogActivity.kt         # 运行日志
```

## 开源与免责声明

- 项目以 [Apache License 2.0](LICENSE) 发布
- 第三方组件说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 本软件不提供、不存储、不索引频道或节目内容
- 用户需要自行确保播放源的合法性、可用性与使用权限
