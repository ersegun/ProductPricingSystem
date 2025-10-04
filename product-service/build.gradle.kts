plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

application {
    // 🔧 This must point to the product service entrypoint (top-level `fun main()`)
    mainClass.set("productservice.ProductServiceApp")
}

val ktor = "2.3.11"

dependencies {
    implementation(project(":shared"))

    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:$ktor")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor")
    implementation("io.ktor:ktor-server-auth-jvm:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor")
    implementation("io.ktor:ktor-server-call-logging-jvm:${ktor}")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor")

    implementation("io.ktor:ktor-client-core-jvm:$ktor")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktor")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")

    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}
