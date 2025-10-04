plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

kotlin { jvmToolchain(21) }

application {
    mainClass.set("discountservice.DiscountServiceApp")
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

    implementation("ch.qos.logback:logback-classic:1.5.6")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}