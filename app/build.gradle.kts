plugins {
  application
  id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

application {
  mainClass.set("app.MainApp")
}

javafx {
  version = "21"
  // Plugin JavaFX dùng tên module dạng dấu chấm (.) chứ không phải dấu gạch (-)
  modules = listOf("javafx.controls", "javafx.fxml")
}

dependencies {
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.test {
  useJUnitPlatform()
}