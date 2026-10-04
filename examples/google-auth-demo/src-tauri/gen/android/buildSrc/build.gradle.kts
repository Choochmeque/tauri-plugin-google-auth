plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("pluginsForCoolKids") {
            id = "rust"
            implementationClass = "RustPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    implementation("com.android.tools.build:gradle:9.3.1")
    // AGP depends on an older Kotlin Gradle plugin. buildSrc's classpath is the parent of every build script's,
    // so without this line that older plugin shadows the version declared in the root build.gradle.kts.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
}

