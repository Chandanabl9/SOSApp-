// Top-level build file
plugins {
    id("com.android.application") version "8.2.1" apply false
    id("com.android.library") version "8.2.1" apply false
    kotlin("android") version "1.9.10" apply false
    kotlin("plugin.serialization") version "1.9.10" apply false
    kotlin("multiplatform") version "1.9.10" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
