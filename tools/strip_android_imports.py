#!/usr/bin/env python3
"""Чистит UI от Android-импортов: их место занимает платформенный слой desktop."""
import glob
import io
import re

DROP_IMPORTS = (
    "import android.",
    "import androidx.activity.",
    "import androidx.compose.ui.platform.LocalContext",
    "import androidx.compose.ui.res.painterResource",
    "import androidx.compose.ui.res.stringResource",
)

changed = 0
for path in sorted(glob.glob("src/main/kotlin/pro/wingerapp/vpn/ui/*.kt")):
    lines = io.open(path, encoding="utf-8").read().split("\n")
    kept = [line for line in lines if not line.startswith(DROP_IMPORTS)]
    if kept != lines:
        io.open(path, "w", encoding="utf-8").write("\n".join(kept))
        changed += 1
print("файлов почищено:", changed)
