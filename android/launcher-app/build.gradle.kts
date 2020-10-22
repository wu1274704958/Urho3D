//
// Copyright (c) 2008-2020 the Urho3D project.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
//

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
    urho3d("android")
}

android {
    ndkVersion = ndkSideBySideVersion
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(18)
        targetSdkVersion(30)
        applicationId = "io.urho3d.launcher"
        versionCode = 1
        versionName = project.version.toString()
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments.apply {
                    System.getenv("ANDROID_CCACHE")?.let { add("-D ANDROID_CCACHE=$it") }
                    add("-D GRADLE_BUILD_DIR=${findProject(":android:urho3d-lib")?.buildDir}")
                    // In order to get clean module segregation, only build player/samples here
                    val includes = listOf("URHO3D_PLAYER", "URHO3D_SAMPLES")
                    addAll(includes.map { "-D $it=1" })
                    // Pass along matching Gradle properties (higher precedence) or env-vars as CMake build options
                    val vars = project.file("../../script/.build-options").readLines()
                    addAll(vars
                        .filter { project.hasProperty(it) }
                        .map { "-D $it=${project.property(it)}" }
                    )
                    addAll(vars
                        .filterNot { project.hasProperty(it) }
                        .map { variable -> System.getenv(variable)?.let { "-D $variable=$it" } ?: "" }
                    )
                }
            }
        }
        splits {
            abi {
                isEnable = project.hasProperty("ANDROID_ABI")
                reset()
                include(
                    *(project.findProperty("ANDROID_ABI") as String? ?: "")
                        .split(',')
                        .toTypedArray()
                )
            }
        }
    }
    buildTypes {
        named("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
    lintOptions {
        isAbortOnError = false
    }
    externalNativeBuild {
        cmake {
            version = cmakeVersion
            path = project.file("CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":android:urho3d-lib"))
    implementation(kotlin("stdlib-jdk8", embeddedKotlinVersion))
    testImplementation("junit:junit:4.13")
    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}

// Ensure IDE "gradle sync" evaluate the urho3d-lib module first
evaluationDependsOn(":android:urho3d-lib")

afterEvaluate {
    tasks {
        "clean" {
            doLast {
                android.externalNativeBuild.cmake.path?.touch()
            }
        }
    }
    android.buildTypes.forEach {
        val config = it.name.capitalize()
        tasks {
            "externalNativeBuild$config" {
                mustRunAfter(":android:urho3d-lib:externalNativeBuild$config")
            }
        }
    }
}
