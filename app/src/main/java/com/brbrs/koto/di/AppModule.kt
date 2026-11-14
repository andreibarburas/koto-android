package com.brbrs.koto.di

import android.content.Context
import androidx.room.Room
import com.brbrs.koto.data.KotoDatabase
import com.brbrs.koto.data.KotoMigrations
import com.brbrs.koto.data.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** Keep in sync with versionName in app/build.gradle.kts and the Settings footer. */
const val APP_VERSION = "1.0.0"

// Application-scoped coroutine scope, for writes that must survive a screen being popped
// (e.g. saving a task's edits right as the user navigates back).
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KotoDatabase =
        Room.databaseBuilder(context, KotoDatabase::class.java, "koto.db")
            // No destructive fallback: offline edits waiting in `pending` exist nowhere else,
            // and a schema bump must never be allowed to discard them. See KotoMigrations.
            .addMigrations(*KotoMigrations.ALL)
            .build()

    @Provides @Singleton
    fun provideTaskDao(db: KotoDatabase): TaskDao = db.taskDao()

    // Separate, persistent DB for task <-> Vinci-contact links -- same reasoning as the
    // main database's own no-destructive-fallback rule: these links have no other home.
    @Provides @Singleton
    fun provideContactLinksDatabase(
        @ApplicationContext context: Context,
    ): com.brbrs.koto.contactlinks.data.ContactLinksDatabase =
        Room.databaseBuilder(
            context,
            com.brbrs.koto.contactlinks.data.ContactLinksDatabase::class.java,
            "contact-links.db",
        ).build()

    @Provides @Singleton
    fun provideContactLinksDao(
        db: com.brbrs.koto.contactlinks.data.ContactLinksDatabase,
    ): com.brbrs.koto.contactlinks.data.ContactLinksDao = db.contactLinksDao()

    // Separate, persistent DB for a task's cross-app "Linked items" -- own database so its
    // schema never has to touch koto.db's or contact-links.db's migrations.
    @Provides @Singleton
    fun provideTaskLinksDatabase(
        @ApplicationContext context: Context,
    ): com.brbrs.koto.links.data.TaskLinksDatabase =
        Room.databaseBuilder(
            context,
            com.brbrs.koto.links.data.TaskLinksDatabase::class.java,
            "task-links.db",
        ).build()

    @Provides @Singleton
    fun provideTaskLinksDao(
        db: com.brbrs.koto.links.data.TaskLinksDatabase,
    ): com.brbrs.koto.links.data.TaskLinksDao = db.taskLinksDao()

    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Koto/1.0.0 (Android)")
                        .build()
                )
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
}
