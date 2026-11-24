package com.dfi.droneflightintelligence

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File

class DfiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val osmConf = Configuration.getInstance()
        osmConf.userAgentValue = "$packageName/1.0"
        val basePath = File(cacheDir, "osmdroid").apply { mkdirs() }
        val tileCache = File(basePath, "tiles").apply { mkdirs() }
        osmConf.osmdroidBasePath = basePath
        osmConf.osmdroidTileCache = tileCache
    }
}
