plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.9.24"
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
