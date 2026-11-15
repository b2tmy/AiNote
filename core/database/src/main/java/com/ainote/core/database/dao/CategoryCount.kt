package com.ainote.core.database.dao

/** Room 聚合查询的结果载体：某分类下有多少条截图 */
data class CategoryCount(
    val category: String?,
    val count: Int
)
