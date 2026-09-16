# btv - Android IPTV 直播播放器

btv 是一款面向手机、平板和 Android TV 的轻量 IPTV 播放器。界面以频道识别和遥控器操作为中心，不内置频道源，也不提供任何音视频内容。

当前 `main` 包含 **1.11.0-preview.1 苹果风格 UI 预览**，尚未替换正式 Release。布局、材质和适配说明见 [UI 设计记录](docs/apple-ui.md)。

## 下载

- 在 [GitHub Releases](https://github.com/2020743996/btv/releases) 下载最新的 `app-release.apk`
- 支持 Android 8.0（API 26）及以上设备
- 支持触摸屏、电视遥控器和无系统输入法的电视盒子

## 首次使用

首次打开且没有频道源时，首页会显示“添加频道源”入口，直接进入 M3U 地址管理。在列表底部点击“添加”，输入地址并提交，回到显示“未保存”的列表后点击“保存”。应用随后返回首页并自动加载频道。

btv 不提供默认播放源。请只添加你有权使用的 M3U 播放列表。

## 主要功能

- **苹果风格白色界面**：合并导航操作组、圆润控件、分隔线频道列表和分区设置；高不透明度轻量玻璃材质用于导航与播放浮层，不对视频做实时模糊
- **轻量线性图标**：内置实际使用的 Lucide 风格矢量路径，统一使用克制的单色操作体系
- **一致的操作层级**：返回固定在左侧，页面操作固定在右侧；表单、弹窗和列表行复用统一的主次与危险操作顺序
- **可靠的源地址编辑**：底部操作栏固定，输入区可滚动，系统键盘完成键也可提交；校验完整地址和重复源，旋转后恢复草稿，放弃未保存修改和删除均需确认
- **手机与电视自适应 UI**：手机使用紧凑顶部工具栏和横向分组；电视、平板使用左侧分组导航与清晰的遥控器焦点
- **横屏防溢出**：手机横屏保持单栏频道列表，标题栏操作空间不足时自动换到下一行并保持右对齐
- **频道台标**：解析 M3U 的 `tvg-logo`，异步加载并缓存；透明台标不会透字，浅色台标会自动切换到深色中性底
- **稳定直播播放**：使用 Media3 1.9.4、10~40 秒弹性缓冲、OkHttp 连接池、分片重试和独立的首连/卡顿恢复超时，弱网下减少反复停顿
- **智能线路恢复**：连续稳定播放后才确认线路可用，长时间卡顿会自动切换备用线路；失效线路复测成功或清除记录后会立即恢复，无需重新下载 M3U
- **拼音搜台**：支持中文名、全拼和首字母搜索，例如 `hnws` 可匹配“湖南卫视”
- **多源多线路**：多个 M3U 源中的同名频道自动合并；测速保留全部可用备用线路，高分辨率优先但不丢弃低清回退线路
- **播放健康记录**：播放成功会清除该线路的失败记录；超时、错误或中断会自动累计并进入失效管理
- **多源 EPG 节目单**：合并全部 M3U 声明的 XMLTV 地址，频道标识不区分大小写；首页及播放页可见期间每分钟检查，缓存过期后重新下载节目单，换源后清除不匹配节目
- **收藏与最近观看**：支持收藏频道、最近观看分组和冷启动续播
- **线路测速与真实分辨率**：测速可取消，刷新或换源不会被旧测速结果覆盖；播放器实测尺寸优先于 URL、频道名和清单提示，可以纠正错误的 4K 标签
- **后台解析与请求取消**：M3U、EPG 解析在后台完成；下载和测速设置总超时，取消任务会中断底层请求，直播连接不受清单下载总超时限制
- **老人模式**：隐藏高级操作，并提供标准、大、特大三档字体

## M3U 台标格式

操作流、页面状态机、组件规则及验证范围见 [交互设计记录](docs/interaction-flow.md)。

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
./gradlew connectedDebugAndroidTest # 需要运行中的模拟器或测试设备
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
|-- AddressEditorState.kt  # 地址编辑状态机、URL 校验与未保存修改
|-- NetworkCall.kt         # 支持取消的异步请求与响应体读取
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
