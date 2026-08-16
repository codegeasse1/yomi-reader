import mihon.buildlogic.configureTest

plugins {
    id("com.android.kotlin.multiplatform.library")

    id("mihon.code.lint")
}

// KMP modules were missing the shared test setup, so their JUnit 5 host tests were never
// discovered by Gradle (test task ran with the default JUnit 4 runner).
configureTest()
