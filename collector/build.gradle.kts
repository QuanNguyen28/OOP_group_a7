plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Selenium core + ChromeDriver via Selenium Manager
    implementation("org.seleniumhq.selenium:selenium-java:4.26.0")

    // HTTP + XML/HTML helpers đã dùng trong code news/youtube
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    // KHÔNG thêm selenium-devtools-* để tránh lỗi
}

application {
    mainClass.set("collector.cli.CollectorCLI")
}