plugins {
  application
  id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } // nếu bạn chỉ có 17, đổi 21 -> 17
}

application { 
  mainClass.set("app.MainApp") 
  applicationDefaultJvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
  applicationDefaultJvmArgs =
      listOf("-Dapp.data.dir=${rootProject.rootDir}/data")
}

javafx {
  version = "21"
  modules = listOf("javafx.controls", "javafx.fxml") // dùng dấu chấm, không phải dấu gạch
}

dependencies {
  implementation("org.xerial:sqlite-jdbc:3.45.3.0")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
  implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")
  implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")
  implementation("com.typesafe:config:1.4.3")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
  implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
  implementation(project(":collector"))
}

tasks.test { useJUnitPlatform() }