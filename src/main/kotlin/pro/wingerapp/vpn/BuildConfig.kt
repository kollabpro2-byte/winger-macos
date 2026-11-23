package pro.wingerapp.vpn

/**
 * Замена android BuildConfig: на десктопе значения приходят из Gradle и констант.
 * `CORE_LOG` включает подробный лог ядра — на Windows он нужен для диагностики
 * так же, как в debug-сборке Android.
 */
object BuildConfig {
    const val CORE_LOG = true
    const val DEBUG = true
    const val APPLICATION_ID = "pro.wingerapp.vpn"
    const val VERSION_NAME = "0.1.75"
    const val VERSION_CODE = 152
}
