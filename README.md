# btv — 安卓 IPTV 直播播放器

为家中长辈做的电视/手机直播播放器：遥控器好用、字体大、搜台快。

## 下载安装

- 最新版 APK 在 [Releases 页面](https://github.com/2020743996/btv/releases) 直接下载（`app-release.apk`）
- 支持安卓 8.0+（API 26+）的手机、平板和电视盒子
- 首次使用：设置 → 管理员模式 → 源地址管理 → 添加你的 M3U 播放列表地址

## 特性

- **手机 / 电视双端适配**：电视端遥控器方向键操作（OK 播放、上下换台、左键呼出选台面板）；手机端触摸 + 系统输入法
- **拼音搜台**：遥控器字母键盘输入拼音即可搜中文频道（`hnws` → 湖南卫视），支持全拼/首字母/中文名混搜，前缀命中优先
- **多源多线路**：支持添加多个 M3U 源地址，同名频道自动合并线路；播放失败自动切换备用线路
- **EPG 节目单**：自动解析 XMLTV 节目单，频道列表和播放页显示"正在播什么"
- **线路测速与失效管理**：一键测速按质量排序线路；连续失败的线路自动隐藏，可在回收站重新检测或恢复
- **老人模式**：精简界面只保留电视、收藏和必要设置；三档字体缩放（标准/大/特大）
- **收藏 / 最近观看**：常用频道一键直达，冷启动自动续播上次频道

## 无内置源声明

**本软件不内置任何频道源、不提供任何频道内容。** 首次使用需自行添加 M3U 播放列表地址：

设置 → 管理员模式 → 源地址管理 → 添加新地址

## 构建

- Android Studio：直接打开项目，Run 即可
- 命令行：`./gradlew assembleDebug`

### Release 签名（可选）

签名信息放在不入库的 `keystore.properties`（参考下方格式），没有该文件时打出未签名包：

```properties
storeFile=keystore/your-release.jks
storePassword=你的密码
keyAlias=你的别名
keyPassword=你的密码
```

生成 keystore：`keytool -genkeypair -keystore keystore/your-release.jks -alias your-alias -keyalg RSA -keysize 2048 -validity 10950`

## 项目结构

```
app/src/main/java/com/example/iptvplayer/
├── MainActivity.kt        # 频道列表主页（分组/收藏/最近观看/测速）
├── PlayerActivity.kt      # 播放页（换台、选台面板、线路切换）
├── SearchActivity.kt      # 搜索页（拼音检索、搜索历史）
├── PinyinSearch.kt        # 拼音检索键生成与匹配
├── M3uParser.kt           # M3U 解析与频道合并
├── EpgParser.kt           # XMLTV 节目单解析
├── LineTester.kt          # 线路测速与失效判定
├── AddressEditor.kt       # 源地址管理
├── SettingsActivity.kt    # 设置（老人模式/字体）
├── AdminActivity.kt       # 管理员模式入口
├── DeadChannelActivity.kt # 失效频道管理
├── LogActivity.kt         # 运行日志
└── Keyboard.kt / Theme.kt # 无输入法设备用的屏上键盘、主题

app/src/main/java/com/github/promeg/pinyinhelper/  # 内嵌 TinyPinyin（Apache 2.0）
```

## 开源协议

- 本项目以 [Apache License 2.0](LICENSE) 发布
- 引用的第三方组件（TinyPinyin、ahocorasick、AndroidX、Media3/ExoPlayer、OkHttp 等）声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，均为 Apache 2.0 兼容

## 免责声明

本软件仅是播放工具，不提供、不存储、不索引任何音视频内容；用户需自行确保所添加播放源的合法性与使用权利。
