# 第三方组件声明（Third Party Notices）

本项目使用了以下开源组件。按各组件许可证要求，在此声明致谢与版权信息。

## 随源码分发的组件

### TinyPinyin
- 来源：https://github.com/promeG/TinyPinyin （v2.0.3 lib 模块）
- 用途：频道名汉字转拼音（搜索"hnws"命中"湖南卫视"）
- 许可：Apache License 2.0
- 内嵌位置：`app/src/main/java/com/github/promeg/pinyinhelper/`
- 原库发布在已关闭的 jcenter，JitPack 构建不完整，故直接内嵌源码，未做修改

## 依赖库

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| org.ahocorasick:ahocorasick 0.3.0 | TinyPinyin 词典分词 | Apache License 2.0 |
| AndroidX / Jetpack Compose | UI 框架 | Apache License 2.0 |
| androidx.media3（ExoPlayer） | 视频播放核心 | Apache License 2.0 |
| OkHttp | 网络请求（下载 M3U/EPG、测速） | Apache License 2.0 |
| Kotlin / kotlinx-coroutines | 语言与协程 | Apache License 2.0 |

本项目整体以 Apache License 2.0 发布，详见根目录 [LICENSE](LICENSE)。
