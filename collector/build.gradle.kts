plugins {
    application
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

application {
    mainClass.set("app.collector.Main")
}

repositories { mavenCentral() }

dependencies {
    // Không bắt buộc thêm lib JSON để giữ gọn nhẹ; dùng HttpClient + parser tối giản.
    // Nếu muốn chắc chắn hơn, thêm Jackson:
    // implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
}