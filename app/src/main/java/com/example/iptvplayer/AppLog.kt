package com.example.iptvplayer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量日志：记录软件运行的关键事件，供"管理员模式"查看。
 *
 * 为什么不用安卓自带的 Logcat：
 * Logcat 需要连电脑用 adb 才能看，普通用户（老人）看不到。
 * 这里把最近 100 条日志存在内存里，界面里直接展示，
 * 出问题时打开管理员模式就能看到"刚才发生了什么"。
 * 注意：App 重启后日志清空（内存日志，不落盘——保持简单）。
 */
object AppLog {

    private val entries = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    /** 记一条日志。@Synchronized 防止多线程同时写导致混乱 */
    @Synchronized
    fun log(message: String) {
        entries.addLast("${timeFormat.format(Date())}  $message")
        while (entries.size > 100) {
            entries.removeFirst() // 只保留最近 100 条
        }
    }

    /** 读取全部日志（最新的在最后） */
    @Synchronized
    fun all(): List<String> = entries.toList()
}
