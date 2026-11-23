#!/usr/bin/env python3
"""Убирает android-контекст из вызовов Store/SubCache/VpnLog/Diag/TunnelState.

Android-версия передавала Context в каждый геттер настроек. На десктопе контекста
нет, поэтому аргумент просто выпадает: сигнатуры вызовов сохраняются те же.
"""
import glob
import io
import re

PATTERNS = [
    (r'\bStore\.(\w+)\((?:ctx|this|app),\s*', r'Store.\1('),
    (r'\bStore\.(\w+)\((?:ctx|this|app)\)', r'Store.\1()'),
    (r'\bSubCache\.save\((?:ctx|this|app),\s*', 'SubCache.save('),
    (r'\bSubCache\.(load|clear)\((?:ctx|this|app)\)', r'SubCache.\1()'),
    (r'\bVpnLog\.init\((?:ctx|this|app)\)', 'VpnLog.init()'),
    (r'\bDiag\.(harvestCrash|logExitReasons)\((?:ctx|this|app)\)', r'Diag.\1()'),
    (r'\bDiag\.harvestCoreLog\((?:ctx|this|app),\s*', 'Diag.harvestCoreLog('),
    (r'\bTunnelState\.(sync|looksStale)\((?:ctx|this|app)\)', r'TunnelState.\1()'),
]

changed = 0
for path in glob.glob('**/*.kt', recursive=True):
    text = io.open(path, encoding='utf-8').read()
    updated = text
    for pattern, replacement in PATTERNS:
        updated = re.sub(pattern, replacement, updated)
    if updated != text:
        io.open(path, 'w', encoding='utf-8').write(updated)
        changed += 1
print('файлов изменено:', changed)
