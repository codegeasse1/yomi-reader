# Extension ABI. These classes are called from separately compiled plugin APKs,
# often through reflection/ClassLoader seams. Keep them non-optimized in the host
# app so release builds expose the same ABI behaviour as debug builds.
-keep class eu.kanade.tachiyomi.source.** { public protected *; }
-keep class eu.kanade.tachiyomi.animesource.** { public protected *; }
-keep class eu.kanade.tachiyomi.novelsource.** { public protected *; }

-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }
