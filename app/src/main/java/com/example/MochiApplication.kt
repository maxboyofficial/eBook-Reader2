package com.example

import android.app.Application
import com.example.data.reader.PdfPageEngine
import com.example.di.AppContainer
import com.example.theme.BundledThemes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MochiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Warm up container, bundled palettes, sample books, and FTS dictionary off main thread
        appScope.launch {
            BundledThemes.all
            container.libraryRepository.initializeDefaults()
            container.annotationRepository.ensureDefaultTemplates()
            container.dictionaryRepository.ensureBundledDictionary()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        PdfPageEngine.trimMemory(level)
    }
}
