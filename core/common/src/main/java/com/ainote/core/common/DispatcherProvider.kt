package com.ainote.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一调度器入口。
 *
 * 刻意做成具体类而不是「接口 + @Binds 实现」：那需要给 core:common 也挂上
 * Hilt 注解处理器，而这里换不来任何收益 —— 测试里直接构造同名对象替换即可。
 */
@Singleton
class DispatcherProvider @Inject constructor() {
    val main: CoroutineDispatcher = Dispatchers.Main
    val io: CoroutineDispatcher = Dispatchers.IO
    val default: CoroutineDispatcher = Dispatchers.Default
}

/** 文本截断，用于列表摘要展示。 */
fun String.ellipsize(max: Int = 80): String =
    if (length <= max) this else take(max).trimEnd() + "…"
