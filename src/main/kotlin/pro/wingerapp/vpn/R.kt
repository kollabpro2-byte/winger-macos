package pro.wingerapp.vpn

/**
 * Идентификаторы ресурсов вместо android.R.
 *
 * Сгенерировано tools/gen_resources.py из app/src/main/res Android-версии.
 * Значения — произвольные стабильные Int: UI использует их только как ключи.
 */
internal object R {
    object string {
        const val acc_account = 1
        const val acc_collapse = 2
        const val acc_days = 3
        const val acc_device_active = 4
        const val acc_devices = 5
        const val acc_devices_other = 6
        const val acc_devices_this = 7
        const val acc_devices_value = 8
        const val acc_error = 9
        const val acc_expand = 10
        const val acc_go_pay = 11
        const val acc_logout = 12
        const val acc_logout_confirm = 13
        const val acc_logout_hint = 14
        const val acc_logout_yes = 15
        const val acc_opening = 16
        const val acc_pay_method = 17
        const val acc_payment = 18
        const val acc_price_sign = 19
        const val acc_remove = 20
        const val acc_remove_failed = 21
        const val acc_remove_no = 22
        const val acc_remove_title = 23
        const val acc_remove_yes = 24
        const val acc_renew = 25
        const val acc_renewal = 26
        const val acc_session = 27
        const val acc_support = 28
        const val acc_support_open = 29
        const val acc_telegram = 30
        const val acc_telegram_bind = 31
        const val acc_telegram_bonus = 32
        const val acc_telegram_failed = 33
        const val acc_telegram_hint = 34
        const val acc_telegram_linked = 35
        const val chat_close = 36
        const val chat_hint = 37
        const val chat_placeholder = 38
        const val chat_title = 39
        const val comp_bird = 40
        const val comp_mir = 41
        const val comp_sbp = 42
        const val fmt_gb = 43
        const val fmt_gb_of = 44
        const val fmt_gb_unit = 45
        const val fmt_none = 46
        const val group_bypass = 47
        const val group_countries = 48
        const val group_recommended = 49
        const val group_youtube = 50
        const val home_days_left = 51
        const val home_devices_label = 52
        const val home_devices_value = 53
        const val home_loading = 54
        const val home_location = 55
        const val home_no_subscription = 56
        const val home_no_subscription_hint = 57
        const val home_not_selected = 58
        const val home_orb_hint = 59
        const val home_ping_none = 60
        const val home_ping_unknown = 61
        const val home_select_server_first = 62
        const val home_server_proto = 63
        const val home_subscription_failed = 64
        const val home_subscription_updated = 65
        const val home_traffic_label = 66
        const val login_action = 67
        const val login_busy = 68
        const val login_disable_vpn = 69
        const val login_label = 70
        const val login_no_network = 71
        const val login_password = 72
        const val login_register_prompt = 73
        const val login_title = 74
        const val nav_account = 75
        const val nav_home = 76
        const val nav_settings = 77
        const val pay_autopay_tag = 78
        const val reg_action = 79
        const val reg_back = 80
        const val reg_busy = 81
        const val reg_subtitle = 82
        const val reg_title = 83
        const val set_add = 84
        const val set_bg = 85
        const val set_bg_action = 86
        const val set_bg_done = 87
        const val set_bg_hint = 88
        const val set_bypass = 89
        const val set_bypass_hint = 90
        const val set_clear = 91
        const val set_connection = 92
        const val set_copy = 93
        const val set_core = 94
        const val set_core_log = 95
        const val set_core_log_hint = 96
        const val set_display = 97
        const val set_dns = 98
        const val set_dns_auto = 99
        const val set_dns_hint = 100
        const val set_dns_own = 101
        const val set_hour_1 = 102
        const val set_hour_2 = 103
        const val set_ipv6 = 104
        const val set_ipv6_hint = 105
        const val set_log = 106
        const val set_log_clear = 107
        const val set_log_clear_hint = 108
        const val set_log_empty = 109
        const val set_min_30 = 110
        const val set_mux = 111
        const val set_refresh = 112
        const val set_remove = 113
        const val set_sec = 114
        const val set_theme = 115
        const val set_theme_dark = 116
        const val set_theme_hint = 117
        const val set_theme_light = 118
        const val set_theme_mid = 119
        const val set_title = 120
        const val set_traffic_rate = 121
        const val set_traffic_rate_hint = 122
        const val sub_active = 123
        const val sub_inactive = 124
        const val sub_left = 125
        const val sub_loading = 126
        const val sub_plan = 127
        const val sub_traffic = 128
    }

    object plurals {
        const val days = 129
    }

    object drawable {
        const val eagle = 130
        const val sbp = 131
    }

    object font {
        const val inter_bold = 132
        const val inter_extrabold = 133
        const val inter_medium = 134
        const val inter_regular = 135
        const val inter_semibold = 136
    }

}

internal val RU_STRINGS: Map<Int, String> = mapOf(
    R.string.acc_account to "АККАУНТ",
    R.string.acc_collapse to "свернуть",
    R.string.acc_days to "дни",
    R.string.acc_device_active to "активно",
    R.string.acc_devices to "УСТРОЙСТВА",
    R.string.acc_devices_other to "ДРУГИЕ УСТРОЙСТВА · %1\$d",
    R.string.acc_devices_this to "ЭТОТ ТЕЛЕФОН",
    R.string.acc_devices_value to "%1\$d из %2\$d",
    R.string.acc_error to "ошибка",
    R.string.acc_expand to "развернуть",
    R.string.acc_go_pay to "Перейти к оплате",
    R.string.acc_logout to "выйти",
    R.string.acc_logout_confirm to "Выйти из аккаунта?",
    R.string.acc_logout_hint to "VPN продолжит работать, пока вы его не отключите. Чтобы вернуться, понадобится логин и пароль.",
    R.string.acc_logout_yes to "Да, выйти",
    R.string.acc_opening to "Открываем…",
    R.string.acc_pay_method to "СПОСОБ ОПЛАТЫ",
    R.string.acc_payment to "ОПЛАТА",
    R.string.acc_price_sign to "₽",
    R.string.acc_remove to "удалить",
    R.string.acc_remove_failed to "не удалось удалить устройство",
    R.string.acc_remove_no to "Нет",
    R.string.acc_remove_title to "Удалить устройство?",
    R.string.acc_remove_yes to "Да",
    R.string.acc_renew to "Продлить",
    R.string.acc_renewal to "ПРОДЛЕНИЕ",
    R.string.acc_session to "СЕССИЯ",
    R.string.acc_support to "ПОДДЕРЖКА",
    R.string.acc_support_open to "открыть",
    R.string.acc_telegram to "TELEGRAM",
    R.string.acc_telegram_bind to "Привязать Telegram +3 дня",
    R.string.acc_telegram_bonus to "Бонус +3 дня начислен",
    R.string.acc_telegram_failed to "Telegram не открылся",
    R.string.acc_telegram_hint to "Привяжите Telegram и заберите бонус",
    R.string.acc_telegram_linked to "привязан",
    R.string.chat_close to "Закрыть",
    R.string.chat_hint to "Напишите сообщение, и откроется новый тикет.",
    R.string.chat_placeholder to "Сообщение",
    R.string.chat_title to "Поддержка",
    R.string.comp_bird to "птица",
    R.string.comp_mir to "МИР",
    R.string.comp_sbp to "СБП",
    R.string.fmt_gb to "%1\$.0f ГБ",
    R.string.fmt_gb_of to "%1\$s ГБ из %2\$s",
    R.string.fmt_gb_unit to "ГБ",
    R.string.fmt_none to "—",
    R.string.group_bypass to "Обходы",
    R.string.group_countries to "Страны",
    R.string.group_recommended to "Рекомендуемые",
    R.string.group_youtube to "YouTube",
    R.string.home_days_left to "осталось",
    R.string.home_devices_label to "устройства",
    R.string.home_devices_value to "%1\$d из %2\$s",
    R.string.home_loading to "Загружаем подписку…",
    R.string.home_location to "Текущее местоположение",
    R.string.home_no_subscription to "Нужно приобрести подписку",
    R.string.home_no_subscription_hint to "Перейдите во вкладку Аккаунт",
    R.string.home_not_selected to "Не выбрано",
    R.string.home_orb_hint to "включить",
    R.string.home_ping_none to "нет",
    R.string.home_ping_unknown to "—",
    R.string.home_select_server_first to "Сначала выберите сервер",
    R.string.home_server_proto to "VLESS · %1\$d",
    R.string.home_subscription_failed to "не удалось обновить подписку",
    R.string.home_subscription_updated to "Подписка обновлена",
    R.string.home_traffic_label to "трафик",
    R.string.login_action to "Войти",
    R.string.login_busy to "Входим…",
    R.string.login_disable_vpn to "Выключить VPN",
    R.string.login_label to "Логин",
    R.string.login_no_network to "нет сети",
    R.string.login_password to "Пароль",
    R.string.login_register_prompt to "Нет аккаунта? Зарегистрироваться",
    R.string.login_title to "Вход в аккаунт",
    R.string.nav_account to "Аккаунт",
    R.string.nav_home to "Главная",
    R.string.nav_settings to "Настройки",
    R.string.pay_autopay_tag to "подписка",
    R.string.reg_action to "Создать аккаунт",
    R.string.reg_back to "← Назад",
    R.string.reg_busy to "Создаю…",
    R.string.reg_subtitle to "Аккаунт создаётся сразу, без браузера",
    R.string.reg_title to "Регистрация",
    R.string.set_add to "добавить",
    R.string.set_bg to "Фон и батарея",
    R.string.set_bg_action to "Разрешить",
    R.string.set_bg_done to "Разрешено: система не будет выгружать VPN",
    R.string.set_bg_hint to "Прошивки вроде HyperOS убивают VPN при выключенном экране. Разрешение на работу в фоне сохраняет туннель.",
    R.string.set_bypass to "БЕЗ ТУННЕЛЯ",
    R.string.set_bypass_hint to "Эти домены идут напрямую, мимо VPN",
    R.string.set_clear to "Очистить",
    R.string.set_connection to "СОЕДИНЕНИЕ",
    R.string.set_copy to "Скопировать",
    R.string.set_core to "ЯДРО",
    R.string.set_core_log to "Подробный лог ядра",
    R.string.set_core_log_hint to "Нужен, чтобы разобрать, почему нет соединения",
    R.string.set_display to "ОТОБРАЖЕНИЕ",
    R.string.set_dns to "DNS",
    R.string.set_dns_auto to "Авто",
    R.string.set_dns_hint to "Запросы уходят через туннель",
    R.string.set_dns_own to "Свой",
    R.string.set_hour_1 to "1 час",
    R.string.set_hour_2 to "2 часа",
    R.string.set_ipv6 to "IPv6 для адреса прокси",
    R.string.set_ipv6_hint to "Выключено: адрес сервера берётся только по A-записи (IPv4)",
    R.string.set_log to "ЛОГ",
    R.string.set_log_clear to "Очистка лога",
    R.string.set_log_clear_hint to "Лог старше выбранного времени удаляется автоматически",
    R.string.set_log_empty to "пока пусто",
    R.string.set_min_30 to "30 мин",
    R.string.set_mux to "Mux (не для vision)",
    R.string.set_refresh to "Обновить",
    R.string.set_remove to "убрать",
    R.string.set_sec to "%1\$d с",
    R.string.set_theme to "Тема оформления",
    R.string.set_theme_dark to "Тёмная",
    R.string.set_theme_hint to "Средняя — между тёмной и светлой: не чёрный фон, но и не серый",
    R.string.set_theme_light to "Светлая",
    R.string.set_theme_mid to "Средняя",
    R.string.set_title to "НАСТРОЙКИ",
    R.string.set_traffic_rate to "Обновление трафика",
    R.string.set_traffic_rate_hint to "Как часто обновляются счётчики ↑ и ↓ на главной",
    R.string.sub_active to "АКТИВНА",
    R.string.sub_inactive to "НЕАКТИВНА",
    R.string.sub_left to "ОСТАЛОСЬ",
    R.string.sub_loading to "Подписка в пути",
    R.string.sub_plan to "Тариф",
    R.string.sub_traffic to "ТРАФИК",
)

internal val EN_STRINGS: Map<Int, String> = mapOf(
    R.string.acc_account to "ACCOUNT",
    R.string.acc_collapse to "collapse",
    R.string.acc_days to "days",
    R.string.acc_device_active to "active",
    R.string.acc_devices to "DEVICES",
    R.string.acc_devices_other to "OTHER DEVICES · %1\$d",
    R.string.acc_devices_this to "THIS PHONE",
    R.string.acc_devices_value to "%1\$d of %2\$d",
    R.string.acc_error to "error",
    R.string.acc_expand to "expand",
    R.string.acc_go_pay to "Go to payment",
    R.string.acc_logout to "sign out",
    R.string.acc_logout_confirm to "Sign out?",
    R.string.acc_logout_hint to "The VPN keeps running until you turn it off. You will need your login and password to come back.",
    R.string.acc_logout_yes to "Yes, sign out",
    R.string.acc_opening to "Opening…",
    R.string.acc_pay_method to "PAYMENT METHOD",
    R.string.acc_payment to "PAYMENT",
    R.string.acc_price_sign to "₽",
    R.string.acc_remove to "remove",
    R.string.acc_remove_failed to "could not remove the device",
    R.string.acc_remove_no to "No",
    R.string.acc_remove_title to "Remove this device?",
    R.string.acc_remove_yes to "Yes",
    R.string.acc_renew to "Renew",
    R.string.acc_renewal to "RENEWAL",
    R.string.acc_session to "SESSION",
    R.string.acc_support to "SUPPORT",
    R.string.acc_support_open to "open",
    R.string.acc_telegram to "TELEGRAM",
    R.string.acc_telegram_bind to "Link Telegram +3 days",
    R.string.acc_telegram_bonus to "+3 day bonus credited",
    R.string.acc_telegram_failed to "Telegram did not open",
    R.string.acc_telegram_hint to "Link Telegram and claim the bonus",
    R.string.acc_telegram_linked to "linked",
    R.string.chat_close to "Close",
    R.string.chat_hint to "Write a message and a new ticket will be opened.",
    R.string.chat_placeholder to "Message",
    R.string.chat_title to "Support",
    R.string.comp_bird to "bird",
    R.string.comp_mir to "MIR",
    R.string.comp_sbp to "SBP",
    R.string.fmt_gb to "%1\$.0f GB",
    R.string.fmt_gb_of to "%1\$s GB of %2\$s",
    R.string.fmt_gb_unit to "GB",
    R.string.fmt_none to "—",
    R.string.group_bypass to "Bypass",
    R.string.group_countries to "Countries",
    R.string.group_recommended to "Recommended",
    R.string.group_youtube to "YouTube",
    R.string.home_days_left to "left",
    R.string.home_devices_label to "devices",
    R.string.home_devices_value to "%1\$d of %2\$s",
    R.string.home_loading to "Loading the subscription…",
    R.string.home_location to "Current location",
    R.string.home_no_subscription to "No subscription yet",
    R.string.home_no_subscription_hint to "Open the Account tab",
    R.string.home_not_selected to "Not selected",
    R.string.home_orb_hint to "turn on",
    R.string.home_ping_none to "none",
    R.string.home_ping_unknown to "—",
    R.string.home_select_server_first to "Choose a server first",
    R.string.home_server_proto to "VLESS · %1\$d",
    R.string.home_subscription_failed to "could not refresh the subscription",
    R.string.home_subscription_updated to "Subscription updated",
    R.string.home_traffic_label to "traffic",
    R.string.login_action to "Sign in",
    R.string.login_busy to "Signing in…",
    R.string.login_disable_vpn to "Turn VPN off",
    R.string.login_label to "Login",
    R.string.login_no_network to "no network",
    R.string.login_password to "Password",
    R.string.login_register_prompt to "No account? Create one",
    R.string.login_title to "Sign in",
    R.string.nav_account to "Account",
    R.string.nav_home to "Home",
    R.string.nav_settings to "Settings",
    R.string.pay_autopay_tag to "subscription",
    R.string.reg_action to "Create account",
    R.string.reg_back to "← Back",
    R.string.reg_busy to "Creating…",
    R.string.reg_subtitle to "The account is created right away, no browser needed",
    R.string.reg_title to "Create account",
    R.string.set_add to "add",
    R.string.set_bg to "Background & battery",
    R.string.set_bg_action to "Allow",
    R.string.set_bg_done to "Allowed: the system will keep the VPN alive",
    R.string.set_bg_hint to "ROMs like HyperOS kill the VPN while the screen is off. Allow background work to keep the tunnel alive.",
    R.string.set_bypass to "NO TUNNEL",
    R.string.set_bypass_hint to "These domains go straight out, around the VPN",
    R.string.set_clear to "Clear",
    R.string.set_connection to "CONNECTION",
    R.string.set_copy to "Copy",
    R.string.set_core to "CORE",
    R.string.set_core_log to "Verbose core log",
    R.string.set_core_log_hint to "Needed to find out why there is no connection",
    R.string.set_display to "DISPLAY",
    R.string.set_dns to "DNS",
    R.string.set_dns_auto to "Auto",
    R.string.set_dns_hint to "Queries go through the tunnel",
    R.string.set_dns_own to "Custom",
    R.string.set_hour_1 to "1 hour",
    R.string.set_hour_2 to "2 hours",
    R.string.set_ipv6 to "IPv6 for the proxy address",
    R.string.set_ipv6_hint to "Off: the server address is resolved over A records (IPv4) only",
    R.string.set_log to "LOG",
    R.string.set_log_clear to "Log cleanup",
    R.string.set_log_clear_hint to "Anything older than the selected period is removed automatically",
    R.string.set_log_empty to "nothing yet",
    R.string.set_min_30 to "30 min",
    R.string.set_mux to "Mux (not for vision)",
    R.string.set_refresh to "Refresh",
    R.string.set_remove to "remove",
    R.string.set_sec to "%1\$d s",
    R.string.set_theme to "Theme",
    R.string.set_theme_dark to "Dark",
    R.string.set_theme_hint to "Middle sits between dark and light: neither black nor grey",
    R.string.set_theme_light to "Light",
    R.string.set_theme_mid to "Middle",
    R.string.set_title to "SETTINGS",
    R.string.set_traffic_rate to "Traffic refresh",
    R.string.set_traffic_rate_hint to "How often the ↑ and ↓ counters on the home screen update",
    R.string.sub_active to "ACTIVE",
    R.string.sub_inactive to "INACTIVE",
    R.string.sub_left to "LEFT",
    R.string.sub_loading to "Subscription on its way",
    R.string.sub_plan to "Plan",
    R.string.sub_traffic to "TRAFFIC",
)

internal val RU_PLURALS: Map<Int, Map<String, String>> = mapOf(
    R.plurals.days to mapOf("one" to "%1\$d день", "few" to "%1\$d дня", "many" to "%1\$d дней", "other" to "%1\$d дня"),
)

internal val EN_PLURALS: Map<Int, Map<String, String>> = mapOf(
    R.plurals.days to mapOf("one" to "%1\$d day", "other" to "%1\$d days"),
)

internal val DRAWABLE_PATHS: Map<Int, String> = mapOf(
    R.drawable.eagle to "drawable/eagle.png",
    R.drawable.sbp to "drawable/sbp.png",
)

internal val FONT_PATHS: Map<Int, String> = mapOf(
    R.font.inter_bold to "font/inter_bold.otf",
    R.font.inter_extrabold to "font/inter_extrabold.otf",
    R.font.inter_medium to "font/inter_medium.otf",
    R.font.inter_regular to "font/inter_regular.otf",
    R.font.inter_semibold to "font/inter_semibold.otf",
)
