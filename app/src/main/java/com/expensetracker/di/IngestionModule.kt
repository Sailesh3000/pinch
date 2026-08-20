package com.expensetracker.di

import android.content.Context
import com.expensetracker.core.database.dao.TemplateCacheDao
import com.expensetracker.extraction.CapabilityDetector
import com.expensetracker.extraction.DeterministicRegexExtractor
import com.expensetracker.extraction.ExtractorChain
import com.expensetracker.extraction.GeminiNanoExtractor
import com.expensetracker.extraction.MediaPipeExtractor
import com.expensetracker.extraction.ModelDownloader
import com.expensetracker.extraction.TemplateCacheEngine
import com.expensetracker.ingestion.DeduplicationEngine
import com.expensetracker.ingestion.PackageWhitelist
import com.expensetracker.ingestion.PreFilterEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IngestionModule {

    @Provides
    @Singleton
    fun providePackageWhitelist(): PackageWhitelist = PackageWhitelist()

    @Provides
    fun providePreFilterEngine(whitelist: PackageWhitelist): PreFilterEngine =
        PreFilterEngine(whitelist)

    @Provides
    fun provideDeduplicationEngine(): DeduplicationEngine = DeduplicationEngine()

    @Provides
    fun provideRegexExtractor(): DeterministicRegexExtractor = DeterministicRegexExtractor()

    @Provides
    fun provideNanoExtractor(): GeminiNanoExtractor = GeminiNanoExtractor()

    @Provides
    @Singleton
    fun provideModelDownloader(
        @ApplicationContext context: Context,
    ): ModelDownloader = ModelDownloader(context)

    @Provides
    @Singleton
    fun provideCapabilityDetector(
        @ApplicationContext context: Context,
        nanoExtractor: GeminiNanoExtractor,
    ): CapabilityDetector = CapabilityDetector(context, nanoExtractor)

    @Provides
    @Singleton
    fun provideExtractorChain(
        @ApplicationContext context: Context,
        nano: GeminiNanoExtractor,
        regex: DeterministicRegexExtractor,
        capabilityDetector: CapabilityDetector,
        modelDownloader: ModelDownloader,
    ): ExtractorChain {
        // If model is already present, wire MediaPipeExtractor immediately
        val mediaPipe = modelDownloader.modelPath?.let { MediaPipeExtractor(context, it) }
        return ExtractorChain(nano, regex, mediaPipe)
    }

    @Provides
    @Singleton
    fun provideTemplateCacheEngine(templateCacheDao: TemplateCacheDao): TemplateCacheEngine =
        TemplateCacheEngine(templateCacheDao)
}
