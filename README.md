# SenseSwap Mod v3.0.0

> **GitHub:** https://github.com/ChaldS-mods/SenseSwap
**Author: ChaldS**

---

## Что нового в v3.0 / What's new in v3.0

### ✅ Серверная авторизация (Server-authoritative roles)
В v2.0 роли хранились только на клиенте — сервер лишь рассылал пакет. Теперь **сервер (или хост) является единственным источником правды**. Клиент не может самостоятельно выставить или изменить роль — он только получает её с сервера.

### ✅ Сохранение ролей при переподключении
Если игрок выходит и заходит снова — его роль **автоматически восстанавливается**. При заходе он получает уведомление в чат.

### ✅ Сохранение ролей в папку мира
Файл `senseswap_roles.json` теперь хранится **внутри папки мира/сохранения**, а не в общей папке конфигов. Каждый мир/сервер хранит свои роли независимо.
- Dedicated server: `<server_dir>/<world_name>/senseswap_roles.json`
- Singleplayer: `<saves>/<world_name>/senseswap_roles.json`

### ✅ Новые команды (бонус)
| Команда | Описание |
|---------|----------|
| `/ss list` | Алиас для `/ss status` |
| `/ss reload` | Перезагрузить роли с диска и разослать всем онлайн-игрокам |

### ✅ Таймер сессии (бонус)
`/ss status` теперь показывает, сколько времени идёт текущая игровая сессия.

### ✅ Серверная проверка микрофона (бонус)
MUTE-проверка в VoicechatPlugin теперь происходит **на сервере** при получении микрофонного пакета. Это означает, что модифицированный клиент не может обойти заглушение микрофона.

### ✅ Пакет сброса роли
Добавлен отдельный сетевой пакет `role_clear` — при `/ss stop` или `/ss clearrole` клиент явно получает команду сбросить свою роль, а не просто перестаёт её иметь.

---

## Структура проекта

```
src/main/
├── java/com/chalds/senseswap/
│   ├── SenseSwapMod.java              — сервер: команды, хранение ролей, события
│   ├── SenseSwapClientMod.java        — клиент: HUD, кнопка, приём пакетов
│   ├── SenseSwapVoicechatPlugin.java  — голосовой чат (MUTE сервер-side!)
│   ├── SenseSwapModMenuIntegration.java
│   ├── config/
│   │   └── ModConfig.java                 — клиентские настройки (JSON в .minecraft/config/)
│   ├── gui/
│   │   ├── SettingsScreen.java
│   │   ├── RolePopupScreen.java
│   │   └── RoleHudRenderer.java
│   ├── mixin/
│   │   ├── ChatHudMixin.java
│   │   ├── InGameHudMixin.java
│   │   ├── SubtitlesHudMixin.java
│   │   └── HotbarHudMixin.java
│   ├── network/
│   │   └── RoleNetworking.java            — два пакета: role + role_clear
│   └── server/
│       └── RoleManager.java               — NEW: сохранение/загрузка ролей в папку мира
└── resources/
    ├── fabric.mod.json
    ├── senseswap.mixins.json
    └── assets/senseswap/
        ├── lang/
        │   ├── en_us.json
        │   ├── ru_ru.json
        │   └── kk_kz.json
        └── textures/
            └── logo.png
```

---

## Команды (требует OP уровень 2)

| Команда | Описание |
|---------|----------|
| `/ss start` | Запустить игру, случайно распределить роли (нужно ≥3 игроков) |
| `/ss stop` | Остановить игру, убрать все роли |
| `/ss setrole <игрок> <BLIND\|DEAF\|MUTE>` | Назначить роль вручную |
| `/ss clearrole <игрок>` | Убрать роль у игрока |
| `/ss status` | Показать текущие роли + время сессии |
| `/ss list` | То же, что status |
| `/ss reload` | Перезагрузить роли с диска |

## Горячие клавиши

| Клавиша | Действие |
|---------|----------|
| `K` | Открыть настройки мода |

## Сборка

```bash
./gradlew build
```

Файл мода: `build/libs/senseswap-3.0.0.jar`

## Зависимости

- Fabric Loader ≥ 0.15.0
- Fabric API
- Simple Voice Chat
- ModMenu (опционально)

## Два конфиг-файла

| Файл | Где | Что хранит |
|------|-----|------------|
| `.minecraft/config/senseswap.json` | Клиент | HUD настройки, попап, визуальные эффекты |
| `<world>/senseswap_roles.json` | Сервер/мир | UUID → роль (авторитативный источник) |
