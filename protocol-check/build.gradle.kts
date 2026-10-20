// Runs the tag's pure-Kotlin protocol code on the JVM, without Android, against a scripted reader.
plugins {
  kotlin("jvm") version "2.2.0"
  application
}

repositories { mavenCentral() }

kotlin {
  sourceSets.main {
    kotlin.srcDir("../modules/tag-emulator/android/src/main/java/sg/edu/sutd/attend/tagemulator/nfc/protocol")
  }
}

application { mainClass.set("ProtocolCheckKt") }

tasks.named<JavaExec>("run") {
  workingDir = layout.buildDirectory.get().asFile
}
