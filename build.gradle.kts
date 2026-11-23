import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.Sync

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "pro.wingerapp"
version = "0.1.75"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Compose Desktop работает в EDT-цикле Swing: без этого моста
    // Dispatchers.Main внутри корутин падает на старте.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // Прослойка LTE4/LTE5 ходит в шлюз по WSS и POST — тот же OkHttp, что на Android.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Android-версия использует встроенный org.json; на JVM он отдельной библиотекой.
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

// Полезная нагрузка portable-сборки: jar приложения и его зависимости рядом,
// чтобы пакет запускался без установщика (см. tools/package_windows.sh).
tasks.register<Sync>("portablePayload") {
    dependsOn(tasks.named("jar"))
    into(layout.buildDirectory.dir("portable"))
    from(tasks.named("jar")) { into("app") }
    from(configurations.runtimeClasspath) { into("app/lib") }
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "pro.wingerapp.vpn.MainKt"

        nativeDistributions {
            // Формат зависит от хоста: jpackage не умеет кросс-сборку. На macOS
            // это dmg для локальной проверки, на Windows — msi (CI).
            val hostOs = System.getProperty("os.name").orEmpty()
            targetFormats(
                *if (hostOs.startsWith("Mac", ignoreCase = true)) {
                    arrayOf(TargetFormat.Dmg)
                } else {
                    arrayOf(TargetFormat.Msi)
                }
            )
            packageName = "WingerVPN"
            // jpackage требует MAJOR > 0, поэтому у пакета своя версия;
            // версия приложения (0.1.75) остаётся в интерфейсе и в логе.
            packageVersion = "1.0.75"
            description = "Winger VPN"
            vendor = "Winger"
            // Ядро и wintun кладутся сюда перед сборкой MSI: в установленной
            // программе они окажутся в resources/compose/core и найдутся
            // TunnelController по системному свойству.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            windows {
                menuGroup = "Winger VPN"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Стабильный UUID: обновление ставится поверх, а не рядом.
                upgradeUuid = "6f1c9a52-4b8e-4b0a-9d7e-2d3f5a1c8e41"
                // Иконка-орёл: тот же растр, что в интерфейсе (tools/make_icons.py).
                iconFile.set(project.file("icons/winger.ico"))
            }
            macOS {
                bundleID = "pro.wingerapp.vpn"
                iconFile.set(project.file("icons/winger.icns"))
                // Для локальной проверки на маке: gradle packageDmg.
                // Приложение подписано ad-hoc, поэтому при первом запуске
                // macOS попросит подтвердить открытие.
            }
        }
    }
}
