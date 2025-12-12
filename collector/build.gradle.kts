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
    // JSON (Gson) cho JSONL writer
    implementation("com.google.code.gson:gson:2.11.0")
    // Jackson cho parsing JSON từ YouTube API
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

application {
    mainClass.set("collector.cli.CollectorCLI")
}