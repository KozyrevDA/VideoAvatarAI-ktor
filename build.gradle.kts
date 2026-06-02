plugins {
    kotlin("jvm") version "2.2.0"
    id("io.ktor.plugin") version "3.1.3"
    kotlin("plugin.serialization") version "2.2.0"
}

group = "org.nla.videoavataraii"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

ktor {
    fatJar {
        archiveFileName.set("videoavataraii-ktor.jar")
    }
}

repositories { mavenCentral() }

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation:3.1.3")
    implementation("io.ktor:ktor-server-core:3.1.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.3")
    implementation("io.ktor:ktor-server-cors:3.1.3")
    implementation("io.ktor:ktor-server-default-headers:3.1.3")
    implementation("io.ktor:ktor-server-auth:3.1.3")
    implementation("io.ktor:ktor-server-auth-jwt:3.1.3")
    implementation("io.ktor:ktor-server-netty:3.1.3")
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-cio:3.1.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.3")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.50.1")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("com.auth0:java-jwt:4.4.0")
    testImplementation("io.ktor:ktor-server-test-host:3.1.3")
    testImplementation(kotlin("test"))
}
