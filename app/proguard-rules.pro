# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep class * extends com.squareup.moshi.JsonAdapter
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}
-keep class **JsonAdapter {
    <init>(...);
}
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers enum * { <fields>; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# JavaSteam - prevent R8 from reordering/stripping lateinit field initialization
-keep class in.dragonbra.javasteam.** { *; }
-keepclassmembers class in.dragonbra.javasteam.** { *; }

# zstd-jni - native code accesses Java fields by name via JNI
-keep class com.github.luben.zstd.** { *; }

# SpongyCastle - loaded via reflection by JavaSteam's CryptoHelper for AES/ECB depot decryption
-keep class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**

# Result types whose class names appear in exported logs
-keepnames class com.nendo.argosy.data.repository.PreLaunchSyncResult$*
-keepnames class com.nendo.argosy.data.repository.SaveSyncResult$*
-keepnames class com.nendo.argosy.data.repository.SaveSyncOrchestrator$DiskCheck$*
-keepnames class com.nendo.argosy.data.repository.SaveCacheManager$CacheResult$*
-keepnames class com.nendo.argosy.data.emulator.SessionEndResult$*
-keepnames class com.nendo.argosy.data.emulator.SessionSaveOutcome$*
-keepnames class com.nendo.argosy.data.emulator.LaunchResult$*
-keepnames class com.nendo.argosy.data.steam.SteamDownloadState$*

# Discord Social SDK (optional, may not be present)
-keep class com.discord.socialsdk.** { *; }
-keep class discordpp.** { *; }
-dontwarn com.discord.socialsdk.**
-dontwarn discordpp.**

# Arcoon optimizer warm-up: classes it loads by name (CocoonOptimizer.WARM_CLASSES).
# Keep this list in step with that one.
-keepnames class com.nendo.argosy.ui.cocoon.CocoonHomeScreenKt
-keepnames class com.nendo.argosy.ui.cocoon.CocoonHomeViewModel
-keepnames class com.nendo.argosy.ui.cocoon.CocoonFolderCoversKt
-keepnames class com.nendo.argosy.ui.cocoon.CocoonWidgetsKt
-keepnames class com.nendo.argosy.ui.screens.library.LibraryScreenKt
-keepnames class com.nendo.argosy.ui.screens.library.LibraryViewModel
-keepnames class com.nendo.argosy.ui.screens.settings.SettingsScreenKt
-keepnames class com.nendo.argosy.ui.screens.settings.SettingsViewModel
-keepnames class com.nendo.argosy.ui.screens.settings.SettingsInitRouterKt
-keepnames class com.nendo.argosy.ui.screens.gamedetail.GameDetailScreenKt
-keepnames class com.nendo.argosy.ui.screens.gamedetail.GameDetailViewModel
-keepnames class com.nendo.argosy.ui.screens.apps.AppsScreenKt
-keepnames class com.nendo.argosy.ui.screens.search.SearchScreenKt
-keepnames class com.nendo.argosy.ui.components.QuickSettingsPanelKt
-keepnames class com.nendo.argosy.ui.components.GameCardKt
-keepnames class com.nendo.argosy.ui.cocoon.theme.CocoonPersonalizationKt
-keepnames class com.nendo.argosy.ui.cocoon.theme.CocoonThemePickerKt
-keepnames class com.nendo.argosy.ui.dualscreen.GameShowcaseKt
-keepnames class com.nendo.argosy.ui.dualscreen.PresentationSlotContentKt
-keepnames class com.nendo.argosy.ui.cocoon.CocoonGameHeroKt
-keepnames class com.nendo.argosy.ui.cocoon.CocoonHeroKt
-keepnames class com.nendo.argosy.ui.quickmenu.QuickMenuOverlayKt
-keepnames class com.nendo.argosy.ui.primitives.CocoonBracketsKt
-keepnames class com.nendo.argosy.ui.components.BoxArtFrameKt
-keepnames class com.nendo.argosy.ui.cocoon.CocoonFolderKt
-keepnames class com.nendo.argosy.ui.cocoon.CocoonFolderMenuKt
-keepnames class com.nendo.argosy.ui.cocoon.CocoonFoldersKt
-keepnames class com.nendo.argosy.ui.screens.gamedetail.components.GameDetailTabsKt
-keepnames class com.nendo.argosy.ui.screens.gamedetail.components.GameDetailSectionsKt
-keepnames class com.nendo.argosy.ui.common.DeferredBlurKt
-keepnames class com.nendo.argosy.ui.navigation.NavGraphKt
-keepnames class com.nendo.argosy.ui.ArgosyAppKt
-keepnames class com.nendo.argosy.ui.components.SystemStatusBarKt
-keepnames class coil.compose.AsyncImageKt
-keepnames class coil.compose.AsyncImagePainter
-keepnames class com.nendo.argosy.ui.quickpanel.QuickPanelOverlay
