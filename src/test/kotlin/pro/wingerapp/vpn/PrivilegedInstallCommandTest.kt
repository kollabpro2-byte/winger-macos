package pro.wingerapp.vpn

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Команда установки уходит в `osascript … with administrator privileges`, где
 * кавычки экранируются для AppleScript и должны остаться кавычками для shell.
 * Ошибка здесь = «пароль ввели, а установка не прошла».
 */
class PrivilegedInstallCommandTest {

    @Test
    fun `путь с пробелами экранируется одинарными кавычками`() {
        val command = PrivilegedTunnel.macAdminCommand(
            "/var/folders/ab cd/winger-install-1.sh",
            "deadbeef",
        )
        assertTrue(command.startsWith("H=$(shasum -a 256 '/var/folders/ab cd/winger-install-1.sh'"))
        assertTrue(command.contains("/bin/sh '/var/folders/ab cd/winger-install-1.sh'"))
    }

    @Test
    fun `хеш сверяется до запуска и отказ виден`() {
        val command = PrivilegedTunnel.macAdminCommand("/tmp/x.sh", "abc123")
        assertTrue(command.contains("[ \"\$H\" = \"abc123\" ] && /bin/sh '/tmp/x.sh'"))
        assertTrue(command.endsWith("|| echo 'hash mismatch'"))
    }
}
