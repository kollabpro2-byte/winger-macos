#!/usr/bin/env python3
"""AppViewModel: убирает AndroidViewModel, viewModelScope и Application."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/AppViewModel.kt"

REPLACEMENTS = [
    ("import android.app.Application\n"
     "import androidx.lifecycle.AndroidViewModel\n"
     "import androidx.lifecycle.viewModelScope\n",
     "import kotlinx.coroutines.CoroutineScope\n"
     "import kotlinx.coroutines.SupervisorJob\n"
     "import kotlinx.coroutines.cancel\n"),
    ("class AppViewModel(app: Application) : AndroidViewModel(app) {\n",
     "class AppViewModel {\n\n"
     "    /**\n"
     "     * Замена `viewModelScope`: на десктопе нет ViewModel и её жизненного\n"
     "     * цикла, а модель живёт столько же, сколько окно приложения.\n"
     "     */\n"
     "    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)\n"),
    ("viewModelScope.launch", "scope.launch"),
    ("            val ctx = getApplication<Application>()\n", ""),
    ("runCatching { Api.init(ctx) }", "runCatching { Api.init() }"),
    ("if (_signedIn.value) loadCache(ctx)", "if (_signedIn.value) loadCache()"),
    ("private suspend fun loadCache(ctx: Application) {", "private suspend fun loadCache() {"),
    ("    override fun onCleared() {\n        Api.onUnauthorized = null\n        super.onCleared()\n    }",
     "    /** Закрывает сетевые подписки при выходе из приложения. */\n"
     "    fun close() {\n        Api.onUnauthorized = null\n        scope.cancel()\n    }"),
    ("runCatching { SubCache.clear(getApplication()) }", "runCatching { SubCache.clear() }"),
    ("private fun str(id: Int): String = getApplication<Application>().getString(id)",
     "private fun str(id: Int): String = pro.wingerapp.vpn.ui.stringsNow()[id]"),
]

text = io.open(PATH, encoding="utf-8").read()
for old, new in REPLACEMENTS:
    if old not in text:
        raise SystemExit("не найдено: " + old[:70])
    text = text.replace(old, new)

io.open(PATH, "w", encoding="utf-8").write(text)
print("AppViewModel обновлён")
