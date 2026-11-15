package com.ainote.service

import android.service.quicksettings.TileService

/**
 * 快捷设置磁贴：下拉通知栏点一下，立刻扫一次相册里的新截图。
 * 用于「刚截完图想立即入库」或者怀疑监听掉线时手动补救。
 */
class ScanTileService : TileService() {

    override fun onClick() {
        super.onClick()
        WatcherScheduler.requestScanNow(this)
    }
}
