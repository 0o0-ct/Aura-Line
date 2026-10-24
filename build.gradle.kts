plugins {
    id("com.android.application") version "8.5.0" apply false
    id("com.android.library") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
}

extra["kotlin_version"] = "2.0.20"
extra["lifecycle_version"] = "2.8.4"
extra["room_version"] = "2.6.1"
extra["datastore_version"] = "1.1.1"

tasks.register("clean") {
    delete(rootProject.buildDir)
}
