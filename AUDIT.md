# Audit projektu Hunt (Cobblemon addon)

Datum: 2026-11-01 · Commit: `f7af8f0` (master) · Auditor: externí, kód jsem nepsal a nic jsem neměnil (kromě tohoto souboru).

Verze z build souborů: Minecraft 1.21.1, Fabric Loader 0.16.0, Fabric API 0.107.0+1.21.1, Cobblemon **1.8.0+1.21.1** (compile), GooeyLibs 3.1.0-1.21.1-SNAPSHOT (jar-in-jar), Impactor API 5.3.0-SNAPSHOT, NeoForge 21.1.75, Gradle 8.14.2, Java 21. `fabric.mod.json`: `cobblemon >=1.8.0`, `minecraft =1.21.1`, `fabricloader >=0.14.22`.

Značení: **[ověřeno]** = viděno v kódu Hunt nebo ve zdrojácích/bytecode Cobblemonu, MC nebo GooeyLibs. **[podezření]** = plyne z kódu, ale dopad je potřeba ověřit na serveru. **[otázka]** = z kódu nejde zjistit.

**Revize 1 (2026-11-01), odpovědi provozovatele:** `individualHunts` se nepoužívá, server běží za **Velocity**, měna jde **převádět mezi hráči** a reward příkazy **obsahují `{player}`**. Podle toho jsou upravené závažnosti (HUNT-005 a HUNT-006 jsou teď latentní), shrnutí, kapitola 7 (doporučení se změnilo) a kapitola 8. Podrobnosti jsou v kapitole 4.0.

**Stav oprav (2026-11-02):**
| Krok (7.4) | Stav | Poznámka |
|---|---|---|
| 0 – hotfix bezpečnosti (kapitola 9) | ✅ **Hotovo a otestováno (1.3.2)** | Body A–I z 9.1 hotové. Ověření 9.2/1 (build, `-Xlint`), 9.2/2 (`PlayerNames` + `String.replace`) a 9.2/3 (testovací server) prošlo (2026-11-02). Na produkci nasazeno 2026-11-02 jako součást 1.3.3. |
| 1 – log výplat (HUNT-014) | ✅ **Hotovo a otestováno (1.3.2)** | Každé splnění zapíše řádek do `config/hunt/payouts.log` (JSON Lines) a INFO do `latest.log`. Nově se loguje i `success=false` od ekonomiky (WARN, `DECLINED`). Vyšlo s krokem 0, na produkci nasazeno 2026-11-02 jako součást 1.3.3. Build zelený, test na serveru (9.2/3 g–l) prošel (2026-11-02). |
| 2 – config se nesmí přepsat (HUNT-004, 012, 016) | ✅ **Hotovo a otestováno (1.3.3)** | Chybný `config.json` na startu Hunt pozastaví (žádné hunty ani odměny, ERROR „PAUSED“). Reload s chybou ponechá starý config a ohlásí to adminovi. Config, lang, hunts i stats se zapisují atomicky přes `FileStore` s rotujícími zálohami `.bak.1–3`. `FileStore.read` rozlišuje „neexistuje“ a „nejde přečíst“. Migrace langu bere texty ze souboru. Build zelený (`-Xlint:all` bez nových varování), offline kontrola parserů a `FileStore` prošla (2026-11-02). Test na testovacím serveru (10.2/2, scénáře a–t) proběhl a prošel (2026-11-02), rotace záloh ověřena i na souborech po restartu. **Nasazeno na produkci (1.3.3) 2026-11-02.** Viz kapitola 10. |
| 3 – testy (HUNT-015, část) | ✅ **Hotovo, nasazeno s 1.3.4** | JUnit 5 v `common`, 114 testů v 7 třídách: substituce placeholderů a blokování příkazů (`Placeholders`, nově vyčleněno z `Utils`), `PlayerNames`, `RarityConfig.getRandomRarity` (injektovaný `RandomGenerator`), `Config.parse`, `Lang.parse` a `FileStore`. `bash ./gradlew build` testy spouští a při selhání spadne. Mutační kontrola: 9 z 10 mutací zachyceno, 1 je ekvivalentní (2026-11-02). Runtime chování se nemění, vyšlo s krokem 4 v 1.3.4 (na produkci 2026-11-02). Nové nálezy HUNT-030, HUNT-031 a HUNT-032. Viz kapitola 11. |
| 4 – `CompletionService` (HUNT-007) | ✅ **Hotovo, otestováno a nasazeno (1.3.4)** | Výplatní tok je vyčleněný do `CompletionService` s injektovanými závislostmi. Pořadí: claim → okamžité uložení `hunts.json` → PENDING v `payouts.log` (fsync, synchronně) → výplata → příkazy jen při úspěchu → stats → DONE. Při selhání ekonomiky se příkazy přeskočí a hráč dostane `payFailedMessage`. Nedokončené výplaty se při startu jen nahlásí. 138 testů, mutační kontrola 10 z 10, test na serveru (12.4/3 a–g) prošel. **Na produkci nasazeno 2026-11-02.** Viz kapitola 12. |
| 5 – oprávnění (HUNT-009, 017) | ✅ **Hotovo, otestováno a nasazeno (1.3.5)** | `reload`, `refresh`, `stats` a `top` kontrolují oprávnění u zdroje příkazu (`CommandSourceStack`), takže `execute as <mob>` už nic neobejde. `/hunts` je čistý alias, `hunt.base` řídí jen GUI. README uvádí skutečné uzly s prefixem `cobblemon.` (nový nález HUNT-033). 161 testů, mutační kontrola 6 z 6, test na serveru (13.4 a–f) prošel. **Na produkci nasazeno 2026-11-02.** Viz kapitola 13. |
| HUNT-033 – uzly oprávnění | 🟡 **Implementováno (1.3.6), čeká na test na serveru** | Uzly jsou zase `hunt.*` (bez prefixu `cobblemon.`), jak uvádělo původní README. Nová třída `HuntPermission` předává uzel Cobblemon validatoru beze změny. V LuckPerms provozovatele žádné uzly Hunt nastavené nejsou, takže se nic nerozbije. 161 testů (2026-11-02). Viz kapitola 14. |
| 6–9 | ⬜ Neprovedeno | |

---

## 1. Shrnutí

Mód je malý (~4,2k řádků Javy, z toho ~3,9k logika). Po posledních commitech je slušně čitelný: IO běží mimo hlavní vlákno, zápisy jsou atomické a hunty i statistiky přežijí restart. Hra a odměny jsou ale chráněné slabě.

**Top 3 problémy:**
1. **HUNT-001/002:** Jméno hráče se vkládá do reward příkazů a broadcastů přes `String.replaceAll` bez escapování. Jméno `@a` rozdá odměnu všem hráčům na serveru. Jméno s `$` nebo `\` vyhodí výjimku uvnitř ticku entity Pokéballu a **shodí server**. Vanilla 1.21.1 ani Velocity znaky ve jméně nekontrolují (ověřeno). Jediná ochrana je AuthMe na lobby. To má `allowedNicknameCharacters: '[a-zA-Z0-9_]*'` (potvrzeno provozovatelem), takže taková jména blokuje, ale jen pokud na Cobblemon server nevede jiná cesta než přes přihlášení na lobby (zatím neověřeno). Na serveru se `{player}` používá a měna je převoditelná.
2. **HUNT-003:** Capture handler ani tick handler nemají žádný `try/catch`. Jakákoli výjimka (chybný config, listener jiného módu) = `ReportedException` → pád serveru.
3. **HUNT-004:** Překlep v `config.json` vede k tichému přepsání configu výchozími hodnotami. Ty obsahují výplatu 100–1000 v primární měně, diamanty a Magikarpa za 500.

**Doporučení (revize 1): ponechat a opravit (možnost 1)** s jedním cíleným vyčleněním výplatní logiky do testovatelné třídy. Všechny kritické a vysoké nálezy, které se globálního režimu týkají, jdou opravit lokálně. Velký refaktor modelu huntu dával smysl hlavně kvůli individuálním huntům, které se nepoužívají. Kompletní rewrite se nevyplatí.

---

## 2. Mapa módu

### 2.1 Z pohledu hráče
- `/hunt` (alias `/hunts`) otevře GUI (GooeyLibs chest, 3–6 řad). V něm je až 28 cílů: druh, forma, případně pohlaví, abilita a nature (podle `matchProperties`), odměna, zbývající čas a lore rarity. GUI nemá žádné klikací akce.
- **Splnění huntu:** hráč chytí Pokéballem **divokého** Pokémona, který odpovídá huntu (druh + forma a volitelně abilita, pohlaví, nature, shiny). Hunt se okamžitě nahradí novým. Hráč dostane peníze přes Impactor, Pebbles nebo Blanket, provedou se reward příkazy jako konzole a zapíše se statistika.
- **Globální režim** (`individualHunts=false`): jedna sada huntů pro celý server. Kdo chytí první, dostane odměnu. Broadcasty o začátku, konci a chycení jdou všem.
- **Individuální režim:** každý hráč má vlastní sadu. Vzniká při joinu nebo při prvním `/hunt`. Expirace se kontroluje jen u online hráčů.
- `/hunt stats [player]`, `/hunt top [completions|earned]`.
- **Žádné denní ani týdenní resety neexistují.** Každý hunt má vlastní `endtime = now + huntDuration` (epoch ms) a po vypršení se nahradí. Časová pásma ani letní čas proto nehrají roli **[ověřeno]**.

### 2.2 Z pohledu admina
- Config: `config/hunt/config.json` (economy, měna, režim, broadcasty, buffer, `huntDuration` v minutách, `huntAmount` max 28, váhy rarit, odměny po raritách, `matchProperties`, `customPrices`, `blacklist` včetně `Species-Form`, ikony z resource packu) a `config/hunt/lang.json`.
- Data: `config/hunt/hunts.json` (verze 1, globální hunty + hunty všech hráčů) a `config/hunt/stats.json` (verze 1). Starý `logs.json` se jednorázově importuje.
- Příkazy: `/hunt reload` (`hunt.reload`, fallback OP2), `/hunt refresh` (přelosuje globální hunty), `/hunt refresh <online hráč>` (`hunt.refresh`, OP2). `DebugCommand` existuje, ale není registrovaný.
- Oprávnění jdou přes `Cobblemon.permissionValidator`. Na Fabricu je to `FabricPermissionValidator` → `me.lucko.fabric.api.permissions.v0.Permissions.check(entity, node, level)`, takže LuckPerms funguje a bez něj se použije OP level **[ověřeno v cobblemon-fabric 1.8.4 bytecode]**.
- Ceny se odvozují z rarity: bucket `common/uncommon/rare/ultra-rare` z `BestSpawner.config.worldBuckets` a `CobblemonSpawnPools.WORLD_SPAWN_POOL`. Pokud druh nebo forma spawnuje ve více bucketech, platí nejběžnější. Legendárky (label `legendary`) a blacklist jsou vyřazené.

### 2.3 Třídy (common, `org.pokesplash.hunt`)
| Balíček / třída | Role |
|---|---|
| `Hunt` | Statický „god object“: `config`, `language`, `hunts`, `manager`, `spawnRates`, `broadcaster`, `storage`, `stats`, `server`. Životní cyklus `init/load/reload/stop/check`. |
| `event.CaptureEvent` | Jediný herní event. Matching + výplata + broadcast + stats + příkazy v jedné lambdě. |
| `hunts.CurrentHunts` | Kolekce huntů jednoho vlastníka (nebo globální). Generování, nahrazování, mazání, matching **a zároveň** broadcasty a `markDirty`. |
| `hunts.SingleHunt` | Hunt = plný Cobblemon `Pokemon` + cena + příkazy + `endtime` + bucket. Serializace v1. Expirace v `check()`. |
| `hunts.HuntManager` | `HashMap<UUID, CurrentHunts>` pro individuální režim. Nikdy se nečistí. |
| `hunts.SpawnRates` | Katalog cílů podle bucketu z Cobblemon spawn poolu. |
| `hunts.HuntStorage`, `util.FileStore` | Ukládání `hunts.json`: dirty flag, nejvýš jednou za 30 s, serializace na hlavním vlákně, zápis na vlákně `Hunt-Save` přes tmp + atomic move. |
| `stats.HuntStats/PlayerStats` | Statistiky, top a lookup podle jména. |
| `broadcast.Broadcaster` | Zprávy a broadcasty s časovým bufferem (zprávy v okně bufferu se zahazují). |
| `config.*`, `old.*` | Gson POJO, migrace ze starých verzí. |
| `economy.*`, `api.economy.*` | Bridge Impactor (přímo), Pebbles a Blanket (reflexe). API pro externí providery s prioritou. |
| `api.event.*` | Vlastní event `HuntEvents.COMPLETED` pro jiné módy. |
| `command.*`, `util.BaseCommand/Subcommand/CommandsRegistry` | Brigadier příkazy a GUI. |
| `util.Utils` | Placeholdery, spouštění příkazů, synchronní IO pro config/lang, parsování itemů. |

### 2.4 Eventy a toky
| Zdroj | Event | Volá | Zapisuje |
|---|---|---|---|
| Fabric | `ServerLifecycleEvents.SERVER_STARTED` | `Hunt.load()`: config, lang, broadcaster, spawnRates → `storage.load()` → `stats.init()` → `fillHunts()` | paměť, dirty |
| Fabric | `SERVER_STOPPING` | `Hunt.stop()`: `storage.saveNow()`, `stats.saveNow()` (synchronně přes writer thread) | `hunts.json`, `stats.json` |
| Fabric | `ServerWorldEvents.LOAD` | `Hunt.server = server` | – |
| Fabric | `ServerPlayConnectionEvents.JOIN` | `manager.addPlayer(uuid)` (jen v individuálním režimu): vytvoření, oříznutí nebo doplnění huntů | paměť, dirty, zpráva hráči |
| Fabric | `ServerTickEvents.END_SERVER_TICK` (každý 20. tick) | `Hunt.check()`: expirace → `replaceHunt` → `addHunt` + broadcasty; každých 30 s `saveIfDirty()` | paměť, async zápis |
| Fabric | `CommandRegistrationCallback` | registrace `/hunt` a `/hunts` | – |
| Cobblemon | `POKEMON_CAPTURED` (Priority.NORMAL) | `CaptureEvent`: `getHuntsFor` → `matches` → `replaceHunt` → broadcast → `HuntEvents.COMPLETED` → ekonomika → `stats.recordCompletion` → `Utils.runCommands` | paměť, ekonomika, příkazy |
| NeoForge | ekvivalenty v `HuntForge` | totéž | totéž |

**Ověřené chování `POKEMON_CAPTURED` v Cobblemonu 1.8.0** (`EmptyPokeBallEntity.kt:346-372`):
- Event se postuje **jen** z hozeného Pokéballu, po `party.add(pokemon)`, a jen když `pokemon.isWild() && pokemon.isAlive && !captureFuture.isDone`. Jde tedy pouze o divoké Pokémony (`isWild() = storeCoordinates == null`, `Pokemon.kt:1571`), jednou za chycení. Trade, GTS, WonderTrade, daycare, vejce, `/pokegive` ani release ho **nespouští**.
- Běží uvnitř `after(seconds=…)` → `schedulingTracker.update()` → `EmptyPokeBallEntity.tick()`. `SimpleObservable.emit` výjimky **nechytá**. `Level.guardEntityTick` (MC 1.21.1 bytecode) je zabalí do `CrashReport("Ticking entity")` a hodí `ReportedException` → **pád serveru**.
- Ostatní odběratelé: `StatHandler` (NORMAL), `CallbackHandler`, `AdvancementHandler` (LOWEST). Při výjimce v Hunt se už nespustí.

Ověřeno také: `forcedAspects` setter → `updateAspects()` → `aspects` setter → `updateForm()`, takže regionální formy se u huntů nastaví správně. `rollAbility()` vybírá z nejnižší priority, ne skrytou abilitu (stejně jako wild spawn). API `getWorldBuckets()`, `WORLD_SPAWN_POOL` a `getHerdablePokemon()` existuje v 1.8.0 i 1.8.4.

---

## 3. Build a testy

- `./gradlew build` nejde spustit, protože `gradlew` nemá executable bit (`operace zamítnuta`). Spuštěno přes `bash ./gradlew build`: **BUILD SUCCESSFUL**.
- Rebuild `--rerun-tasks` s `-Xlint:all` (přes init skript v /tmp, projekt beze změny): **0 varování v `common` a `fabric`**, 1 varování ve `forge` (`HuntForge.java:19` `[this-escape]`). Žádné deprecated Java API.
- **Testy: žádné.** `compileTestJava NO-SOURCE` / `test NO-SOURCE` ve všech modulech. V `test-data/stats.json` (untracked) jsou jen ukázková data. *Krok 3: `common` má 114 JUnit 5 testů (kapitola 11), `fabric` a `forge` zůstávají `NO-SOURCE`.*
- Gradle deprecation (rozbije přechod na Gradle 9/10): `archivesBaseName` (`build.gradle:33`), `BasePluginConvention`, `JavaPluginConvention` a `ConfigureUtil` (shadow plugin 7.1.2 ve `fabric/build.gradle:2`), Groovy „space assignment“ (`settings.gradle:3-5`, `build.gradle:47,58`, `fabric/build.gradle:22-23`, `forge/build.gradle:20`).
- Závislosti: `gooeylibs 3.1.0-SNAPSHOT` a `impactor 5.3.0-SNAPSHOT` jsou SNAPSHOT, takže build není reprodukovatelný. Shadow plugin `com.github.johnrengelman.shadow 7.1.2` je zastaralý (nástupce `com.gradleup.shadow`). Kotlin plugin 2.2.0 se aplikuje, ale v projektu není žádný Kotlin.
- V gradle cache je i `cobblemon-1.8.4+1.21.1`. Pokud běží produkce na 1.8.4, kompilace proti 1.8.0 projde (API ověřeno výše), ale je vhodné bumpnout. **Odpověď provozovatele (2026-11-02): produkce běží na Cobblemonu 1.8.4.** Bump `cobblemon_version` na `1.8.4+1.21.1` patří do kroku 8 (spolu s horní hranicí `<1.9` v HUNT-023).

---

## 4. Nálezy

Seřazeno podle závažnosti. Řádky odpovídají commitu `f7af8f0`.

### 4.0 Revize 1: odpovědi provozovatele a dohledané údaje
| Údaj | Dopad na nálezy |
|---|---|
| `individualHunts` se nepoužívá | Ověřeno, že `HuntManager.addPlayer` (`HuntManager.java:79`) i `Hunt.getHuntsFor` (`Hunt.java:133`) v globálním režimu nic nevytváří. **HUNT-005 a HUNT-006 jsou teď latentní** (sníženo na Nízká) a projeví se až po zapnutí režimu. HUNT-019 se netýká provozu. |
| Proxy je **Velocity** | Ve zdrojácích Velocity (`dev/3.0.0`) kontroluje `ServerLoginPacket.decode` jen délku (`readString(buf, 16)`) a prázdné jméno. `InitialLoginSessionHandler` v offline mode jméno předá beze změny do `GameProfile.forOfflinePlayer(...)`. **Velocity znaky ve jméně nekontroluje [ověřeno].** |
| AuthMe na lobby | Provozovatel potvrdil `allowedNicknameCharacters: '[a-zA-Z0-9_]*'`. AuthMe tedy hráče se jménem `@a`, `Ash$1` nebo `a\` **na lobby vykopne**. Chrání to ovšem jen tehdy, když se hráč na Cobblemon server nedostane jinou cestou. To jsou podmínky 2–4 v HUNT-001 a zatím jsou **neověřené**. |
| Reward příkazy používají `{player}` | HUNT-001 a HUNT-002 jsou na tomto serveru reálně dosažitelné, pokud jméno projde přes AuthMe. |
| Měna jde převádět mezi hráči | Peníze získané exploitem (HUNT-001, HUNT-007) jdou okamžitě přelít na jiné účty, takže je obtížné je dohledat a vrátit. To zvyšuje význam logu výplat (HUNT-014). |
| Režim je globální | Hráčské jméno se dosazuje i do broadcastu `captureHuntBroadcast` (výchozí text obsahuje `{player}`). Pád (HUNT-002) proto nastane už v kroku broadcastu (`CaptureEvent.java:41`), **ještě před výplatou**, a to i bez reward příkazů. |

### Kritická

#### ✅ HUNT-001 · [opraveno v 1.3.2, krok 0] · Command injection přes jméno hráče v reward příkazech · A · [ověřeno v kódu, vanilla i Velocity; zneužitelnost závisí na tom, jestli jde obejít AuthMe → otázka]
- **Kde:** `util/Utils.java:262` (`replaceAll("\\{player\\}", player.getName().getString())`), `util/Utils.java:289-291` (příkaz se spouští jako `createCommandSourceStack()`, tedy permission level 4).
- **Popis:** Jméno se do příkazu vkládá bez validace a bez quotingu. Vanilla `StringUtil.isValidPlayerName` (1.21.1 bytecode) odmítá jen délku nad 16 a znaky `<= 32` nebo `>= 127`. Povolené jsou tedy `@ [ ] = , " ; $ \` atd. V offline režimu za proxy rozhoduje o povolených znacích proxy a AuthMe na lobby.
- **Scénář:** Hráč se připojí jako `@a` (2 znaky, projde vanilla validací). Chytí hunt Pokémona. Výchozí příkaz `give {player} minecraft:diamond 1` se provede jako `give @a minecraft:diamond 1` a odměnu dostanou všichni online. Ověřeno na samotné substituci: výstup `give @a minecraft:diamond 1`. S příkazem typu `eco give {player} 500` vznikne peněžní inflace. S `@e[type=item]` a podobnými jde mířit i na entity.
- **Dopad:** Exploit odměn, který jde škálovat (všichni online × každý hunt).
- **Oprava:** Před spuštěním příkazů ověřit jméno regexem `^[A-Za-z0-9_]{1,16}$`. Při neshodě příkazy nespouštět a zalogovat WARN. Přidat placeholder `{uuid}`, protože vanilla entity argument přijímá UUID, a doporučit ho v configu. Substituci dělat přes `String.replace` (literál). **S**.
- **Revize 1:** Velocity znaky nekontroluje [ověřeno ve zdrojácích]. V offline mode si hráč jméno volí sám, stačí upravený launcher nebo bot. Jméno zastaví jen AuthMe na lobby. Aby mod byl chráněný, musí platit **všechny** tyto podmínky:
  1. `settings.restrictions.allowedNicknameCharacters` v AuthMe na lobby je `[a-zA-Z0-9_]*` nebo přísnější.
  2. Ve `velocity.toml` není Cobblemon server v seznamu `[servers] try` ani ve `[forced-hosts]`. Jinak hráče, kterého lobby vykopne nebo které je nedostupné, pošle Velocity rovnou na Cobblemon server. Pozor na `failover-on-unexpected-server-disconnect`.
  3. Cobblemon server přijímá spojení jen z Velocity (modern forwarding secret a port nedostupný zvenku).
  4. Hráč nemůže přejít na Cobblemon server (`/server`) dřív, než se v AuthMe přihlásí, například díky pluginu AuthMeVelocity nebo podobnému. Jinak bod 1 platí jen pro lobby.

  I když všechny podmínky platí, jde o jedinou vrstvu ochrany v cizím pluginu. Oprava v Hunt (S) je proto doporučená tak jako tak.
- **Stav po revizi 1:** Podmínka 1 je splněná (provozovatel potvrdil `[a-zA-Z0-9_]*`). Podmínky 2–4 zbývá ověřit. **Závažnost zůstává Kritická**, protože se tím nemění, co se stane po průniku jména (inflace převoditelné měny, pád serveru). **Pravděpodobnost zneužití je ale nižší:** pokud platí i podmínky 2–4, útočník potřebuje obejít AuthMe nebo Velocity. Stejné podmínky platí pro HUNT-002. Pozor, AuthMe chrání jen jméno. Nechrání před výjimkami z jiných zdrojů (HUNT-003) ani před budoucím přechodem na jiný auth plugin nebo změnou jeho configu.
- **Stav po revizi 2:** Provozovatel ověřil, že platí i podmínky 2–4. Jména mimo `[a-zA-Z0-9_]` se tedy na Cobblemon server **dnes nedostanou** a HUNT-001 i HUNT-002 jsou aktuálně zmírněné konfigurací AuthMe a Velocity. Vada v módu ale trvá a ochrana stojí na jediné vrstvě mimo mód (změna AuthMe configu, výměna auth pluginu nebo nový vstupní server ji zruší). Proto se opravuje v kroku 0 (kapitola 9). Pro provoz to znamená, že `{player}` z configu odstraňovat **není nutné**.
- **Jak ověřit podmínky 2–4:** Ve `velocity.toml` zkontrolovat `[servers] try`, `[forced-hosts]` a `failover-on-unexpected-server-disconnect`. Z vnějšku zkusit spojení přímo na port Cobblemon serveru (má selhat, protože chybí forwarding). Zkontrolovat, jestli Velocity plugin (např. AuthMeVelocity) blokuje `/server` a přepnutí serveru před přihlášením. Pro praktický test se na testovací instanci připojit offline klientem se jménem `Test$1`. Lobby by mělo hráče vykopnout a Velocity ho nesmí poslat na Cobblemon server.

#### ✅ HUNT-002 · [opraveno v 1.3.2, krok 0] · Pád serveru přes `$` nebo `\` ve jméně hráče · A/B · [ověřeno: kód + Cobblemon + MC bytecode + test]
- **Kde:** `util/Utils.java:262` (replaceAll s jménem jako *replacement*), volá se z `broadcast/Broadcaster.java:46` (CAPTURED broadcast v globálním režimu, `event/CaptureEvent.java:41`) a z `util/Utils.java:289`, které leží **mimo** `try` (`event/CaptureEvent.java:76`).
- **Popis:** `"…{player}…".replaceAll(regex, "Ash$1")` vyhodí `IndexOutOfBoundsException: No group 1`, `a$b` vyhodí `IllegalArgumentException: Illegal group reference` a `Ash\` vyhodí `character to be escaped is missing` (ověřeno spuštěním). Výjimka probublá do `EmptyPokeBallEntity.tick()` → `Level.guardEntityTick` → `ReportedException` → server spadne.
- **Scénář:** Hráč `Ash$1` chytí Pokémona, který odpovídá huntu. Server spadne. Po restartu může útok libovolně opakovat, pokaždé když najde hunt Pokémona. V globálním režimu je hunt už nahrazený a výplata se neprovede. V individuálním režimu proběhne výplata a pak pád.
- **Dopad:** DoS celého serveru (50 hráčů) z jednoho účtu.
- **Oprava:** Všechny substituce dělat přes `String.replace` (nebo `Matcher.quoteReplacement`) a celý handler zabalit (viz HUNT-003). **S**.

#### ✅ HUNT-003 · [opraveno v 1.3.2, krok 0] · Jakákoli výjimka v capture nebo tick handleru shodí server · B · [ověřeno]
- **Kde:** `event/CaptureEvent.java:24-79` (try/catch je jen kolem ekonomiky, `:52-68`), `Hunt.java:109-124` (`check()` z `END_SERVER_TICK`, `HuntFabric.java:21-25`), `api/event/Event.java:31-33` (listenery jiných módů bez ochrany).
- **Popis:** Výjimku mimo blok ekonomiky nikdo nechytá. Konkrétní spouštěče:
  - V configu `"rewards": {"rare": null}` nebo položka `customPrices` bez `reward`/`species`: NPE v `SingleHunt.java:54` nebo `:94`. Projeví se při `addHunt()`, tedy po chycení (uvnitř ticku entity) nebo po expiraci (uvnitř server ticku). Po `/hunt reload` s takovým configem spadne server při nejbližší expiraci nebo chycení, ne hned.
  - Listener `HuntEvents.COMPLETED` z jiného módu, který vyhodí výjimku.
  - HUNT-002.
- **Dopad:** Pád serveru, crash loop, pokud se stav uloží (při pádu přes `stopServer` se volá `SERVER_STOPPING` → `Hunt.stop()`).
- **Oprava:** `try { … } catch (Throwable t) { LOGGER.error(…) }` kolem celého capture handleru, `Hunt.check()` a každého `HuntEvents` listeneru. Dlouhodobě validovat config (HUNT-008). **S**.

### Vysoká

#### ✅ HUNT-004 · [opraveno v 1.3.3, krok 2] · Chyba v configu přepíše config výchozími hodnotami · C/E · [ověřeno]
- **Kde:** `config/Config.java:60-119`, `util/Utils.java:125-181`, `config/Lang.java:119-178`.
- **Popis:** Gson výjimka v callbacku → `readFileAsync` ji chytí → `readFileSync` zavolá callback znovu → `false` → `Config.init` vypíše *„No config.json file found“* a `write()` zapíše **aktuální objekt v paměti** přes soubor admina (`Config.java:108-110`).
  - Při **startu** je v paměti `new Config()`, tedy výchozí hodnoty: 100/500/700/1000 v primární měně Impactoru, `give {player} minecraft:diamond 1–4`, Magikarp za 500 (`RewardsConfig.java:15-18`, `CustomPrice.java:9-12`). Server tak běží s výchozí ekonomikou a adminův config je pryč.
  - Při `/hunt reload` se přepíše adminova nová verze starou verzí z paměti a admin přijde o úpravy.
  - Zápis configu a langu (`Utils.writeFileAsync`) **není atomický** (`TRUNCATE_EXISTING` a jeden `write` bez kontroly počtu zapsaných bajtů). Pád během zápisu nechá prázdný nebo useknutý soubor a při dalším startu nastane výše popsaný scénář.
- **Scénář:** Admin přidá do `customPrices` položku a zapomene čárku, pak restartuje. Hunt běží s výchozími cenami, hráči dostávají diamanty a peníze podle defaultů a v logu je jen INFO „No config.json file found“.
- **Oprava:** Rozlišit „soubor neexistuje“ od „soubor je nečitelný“. Při chybě parsování **nikdy nepřepisovat**: na startu odmítnout start Hunt (nebo běžet bez odměn) s ERROR, při reloadu ponechat starý config. Config a lang zapisovat přes `FileStore` (tmp + atomic move). **M**.
- **Doplněk (krok 2):** další cesta ke stejnému přepisu: `Versioned` nemá no-arg konstruktor, takže když v `config.json` chybí `version`, `versioned.getVersion().equals(...)` vyhodí NPE a `readFileAsync` vrátí `false` → přepis výchozími hodnotami. **Rozhodnutí provozovatele:** chybný config na startu = Hunt pozastaven (ne „běží bez odměn“). Řešení viz kapitola 10.

#### ✅ HUNT-007 · [opraveno v 1.3.4, krok 4] · Výplata není transakční vůči stavu huntu a selhání není vidět · A/C · [ověřeno v kódu; scénář pádu je podezření]
- **Kde:** `event/CaptureEvent.java:38-76`, `Hunt.java:118-123` (ukládá se nejvýš jednou za 30 s).
- **Popis:** Pořadí je: hunt odstraněn (jen v paměti) → broadcast → event → výplata (Impactor ji ukládá sám) → stats → příkazy (okamžitě). `hunts.json` se zapíše až za ≤30 s.
  1. **Tvrdý pád** (kill -9 při zaseknutém stopu v Pterodactylu, OOM, watchdog) v tomto okně: po restartu je splněný hunt zpět, peníze a itemy z příkazů zůstanou a hunt jde splnit znovu. Cobblemon party se může vrátit do stavu před chycením. **[podezření, závisí na autosave Cobblemonu a ekonomiky]**
  2. **Selhání ekonomiky** (výjimka nebo `success=false`): hunt je spotřebovaný, peníze nepřišly, **příkazy se přesto provedou** (`:75-77`), hráč nedostane žádnou zprávu a nic se nezopakuje.
- **Oprava:** `CompletionService`: claim → zápis do append-only žurnálu (`payouts.log`, fsync) → výplata → příkazy → potvrzení. Při startu dohrát nepotvrzené záznamy. Po completion vynutit uložení `hunts.json`, protože jde o vzácnou událost. **M**.

### Střední

#### HUNT-008 · Chybí validace configu · E · [ověřeno]
- **Kde:** `config/Config.java:66-90` (kontroluje se jen `huntAmount > 28`).
- **Scénáře:**
  - `huntDuration: 0` nebo záporné: každý hunt expiruje hned, takže `check()` každou sekundu nahradí všechny hunty. Do chatu jde spam STARTED/ENDED, `hunts.json` je trvale dirty a hunt prakticky nejde splnit.
  - `huntAmount: 0` nebo záporné: GUI `rows = ceil(n/7)+2 = 2` → `rectangle(1,1,0,7)` (`HuntCommand.java:150-155`), hráč uvidí chybovou hlášku s textem výjimky.
  - Všechny váhy rarit 0: žádné hunty a jen WARN.
  - `economy` s překlepem: Gson dá `null` → NPE v `switch` (`HuntEconomyProvider.java:56`) → výjimku chytí `CaptureEvent`, peníze se nevyplatí a příkazy se provedou.
  - `bufferDuration` záporné, `price` NaN/∞ (Gson je lenient) nebo záporná cena (jen se přeskočí).
  - Neznámý druh v `customPrices` nebo `blacklist` se tiše ignoruje.
- **Oprava:** `ConfigValidator` s jasnými hláškami (`config.json: huntDuration musí být > 0, použito 60`), kontrola druhů proti `PokemonSpecies` a odmítnutí neplatného configu (viz HUNT-004). **M**.

#### ✅ HUNT-009 · [opraveno v 1.3.5, krok 5] · Oprávnění se obchází ze zdroje, který není hráč · E · [ověřeno]
- **Kde:** `command/subcommand/RefreshCommand.java:32-39`, `ReloadCommand.java:26-33`, `StatsCommand.java:33`, `TopCommand.java:29`, `util/BaseCommand.java:69-76`: `if (ctx.isPlayer()) {check} else return true`.
- **Popis:** `isPlayer()` je false pro konzoli, command blocky a také pro `/execute as <ne-hráč> run hunt refresh` a pro módy, které spouštějí příkazy jménem entity nebo NPC.
- **Scénář:** Hráč, který má přes LuckPerms `minecraft.command.execute` (některé servery ho dávají kvůli `/execute` v minihrách), napíše `/execute as @e[type=cow,limit=1] run hunt refresh` a přelosuje globální hunty („reroll“ na lukrativní cíle). Stejně tak `hunt reload`.
- **Oprava:** Kontrolovat `source.hasPermission(level)` nebo `Permissions.check(source, node, level)` nad `CommandSourceStack`. Cobblemon má `hasPermission(CommandSourceStack, Permission)`. **S**.

#### HUNT-010 · Hunt se splní jakýmkoli divokým Pokémonem bez ohledu na původ · A · **Nízká (revize 3, původně Střední)** · [ověřeno: matching; podezření: konkrétní zdroje]
- **Kde:** `event/CaptureEvent.java:24-30`, `hunts/SpawnRates.java:53,60` (rarita a cena se počítají jen z `worldBuckets`).
- **Popis:** Cobblemon postuje event jen pro divoké Pokémony chycené ballem (dobře). Nezáleží ale na tom, odkud Pokémon pochází: PokeSnack (1.8, bucket váhy `rare 4.125 / ultra-rare 1.375` proti world `0.5 / 0.2`), rybaření, admin `/spawnpokemon`, spawner bloky, raid nebo lure módy. Cena „ultra-rare“ se ale počítá z vzácnosti ve světě.
- **Scénář:** Hráč zjistí, že ultra-rare cíl jde přilákat PokeSnackem nebo že ho spawnuje farma nebo spawner. Opakovaně farmí ultra-rare odměny.
- **Oprava:** Volitelný filtr původu. Cobblemon ukládá spawn kontext a data (`pokemon.persistentData` / `PokemonEntity` spawn cause); je potřeba ověřit, co je v 1.8 dostupné. Nebo aspoň počítat raritu ze všech bucket map. **M**.
- **Otázka:** Jaké módy na serveru spawnují chytatelné divoké Pokémony (raidy, lures, spawnery)?
- **Odpověď provozovatele (2026-11-02):** žádné, divoké Pokémony spawnuje jen samotný Cobblemon. Odpadají tedy cizí módy (raidy, lures, spawnery). Zůstávají zdroje přímo v Cobblemonu: PokeSnack (bait s vyššími vahami `rare`/`ultra-rare`), rybaření a admin `/spawnpokemon`. **Závažnost snížena na Nízká.** Pokud by se farmení přes PokeSnack v `payouts.log` ukázalo jako problém, řešit v kroku 9 (rarita ze všech bucket map, nebo filtr původu).

#### HUNT-011 · Blokující `CompletableFuture.join()` ekonomiky na hlavním vlákně · B/D · **Nízká (revize 3, původně Střední)** · [podezření]
- **Kde:** `economy/ImpactorService.java:57` (`service.account(currency, uuid).join()`), `api/economy/HuntEconomyProvider.java:56-69` (při **každé** výplatě nová instance služby a u Pebbles/Blanket reflexe s `Class.forName`).
- **Popis:** Pokud Impactor používá SQL úložiště nebo účet není v cache (alt účet, viz HUNT-005), tick stojí na IO.
- **Oprava:** Účet předem načíst při joinu, případně platit asynchronně a výsledek vrátit na hlavní vlákno (`server.execute`). Instanci služby cachovat. **S–M**.
- **Otázka:** Jaký storage backend používá Impactor na serveru?
- **Odpověď provozovatele (2026-11-02):** JSON (lokální soubory). `join()` tedy nečeká na síť ani databázi, nejvýš na čtení malého lokálního souboru u účtu, který ještě není načtený. **Závažnost snížena na Nízká**, samostatný krok není potřeba. Cache instance ekonomické služby lze přidat v kroku 8. Přehodnotit při přechodu Impactoru na SQL.

#### ✅ HUNT-012 · [opraveno v 1.3.3, krok 2] · Chyba IO při čtení dat = „soubor neexistuje“ → přepsání · C · [ověřeno]
- **Kde:** `util/FileStore.java:49-51` (IOException → `null`), `stats/HuntStats.java:54-69`, `hunts/HuntStorage.java:69-72`.
- **Scénář:** Přechodná chyba práv nebo disku v kontejneru Pterodactyl při startu. Stats začnou prázdné, při první completion se nastaví dirty a za 30 s se `stats.json` **přepíše** téměř prázdným souborem. Žádná záloha nevznikne (`backupBroken` se volá jen při chybě parsování a navíc přepisuje předchozí `.broken`).
- **Oprava:** IOException při čtení = fatální pro daný store (neukládat a hlasitě logovat). Rotující zálohy (`stats.json.bak.N`) před přepisem. **S**.

#### HUNT-013 · Chybí admin nástroje pro správu dat hráče · C/E · [ověřeno]
- Nejde resetovat stats hráče, přelosovat hunty **offline** hráče (`RefreshCommand.java:212-219` hledá jen online) ani zobrazit cizí hunty. Ruční editace `stats.json`/`hunts.json` za běhu se do 30 s přepíše daty z paměti.
- **Oprava:** `/hunt admin stats reset <player|uuid>`, `/hunt admin refresh <player|uuid>`, `/hunt admin view <player>`, `/hunt admin save`. **S–M**.

#### HUNT-014 · Výplaty se nelogují · F · [ověřeno]
- **Kde:** `event/CaptureEvent.java:52-68`. Úspěšná výplata ani provedené příkazy nezanechají v logu žádnou stopu, chyby se logují přes `printStackTrace`.
- **Dopad:** Na serveru, kde hráči hledají exploity, nejde zpětně zjistit, kdo, kdy, kolik a za jaký hunt dostal (HUNT-001, -005, -007).
- **Oprava:** Jeden INFO řádek na completion (`uuid, name, huntId, species/form, bucket, price, success, commands`), ideálně do samostatného `payouts.log`. **S**.

#### HUNT-015 · Žádné testy a všudypřítomný statický stav · F · [ověřeno]
- Projekt nemá ani jeden test. Doménová logika (`CurrentHunts`, `SingleHunt.matches`, výběr rarity) sahá přímo na `Hunt.config`, `Hunt.server`, `Hunt.broadcaster` a `Hunt.storage`, takže ji nejde testovat bez běžícího serveru. Každá oprava proto nese riziko regrese.
- **Oprava:** Viz kapitola 7 (doménové jádro bez MC importů + JUnit 5). **M–L**.
- **Stav po kroku 3:** JUnit 5 v `common` pokrývá substituci, výběr rarity, parsování configu a langu a `FileStore` (kapitola 11). Matching, generování huntů a výplatní tok zatím testy nemají. Výplatní tok přijde s `CompletionService` v kroku 4.
- **Stav po kroku 4:** výplatní tok (`CompletionService`) a žurnál (`PayoutJournal`) mají 23 testů (kapitola 12). Matching a generování huntů testy pořád nemají.

#### HUNT-032 · Config bez `version` přepne ekonomiku na Impactor a měnu na výchozí · C/E · [ověřeno testem, nalezeno v kroku 3]
- **Kde:** `config/Config.java:106-119`, `old/ConfigOld.java:30`.
- **Popis:** Když v jinak aktuálním `config.json` chybí `version` (třeba omylem smazaný řádek), projde soubor migrací ze starého formátu. Ta nastaví `economy = IMPACTOR` a `currencyName` vezme z `ConfigOld.impactorCurrencyName`. Gson ale pole naplní z no-arg konstruktoru `ConfigOld()` (`"impactor:huntcoins"`), takže kontrola `!= null` nikdy neplatí a měna ze souboru se ztratí. Config se označí jako `upgraded` a **přepíše se** (originál zůstane v `.bak.1`). Výplaty pak jdou v jiné měně nebo přes jinou ekonomiku, případně skončí `ERROR`, pokud měna `huntcoins` neexistuje. V `latest.log` ani v chatu se to nijak neohlásí. Před krokem 2 se v tomto případě přepsal celý config, takže jde o zbytek HUNT-004.
- **Doklad:** `ConfigParseTest.missingVersionResetsEconomyAndCurrency` (dokumentuje současné chování, po opravě se test otočí).
- **Oprava:** Migrovat jen tehdy, když soubor opravdu má starý formát (obsahuje `impactorCurrencyName` a nemá `economy` / `currencyName`). Jinak jen doplnit verzi, hodnoty ponechat a zalogovat WARN. Doporučuji v kroku 6 (validace). **S**.

#### ✅ HUNT-016 · [opraveno v 1.3.3, krok 2] · Nesoulad verze lang.json zapíše výchozí texty · C · [ověřeno]
- **Kde:** `config/Lang.java:125-139`. Když `version` chybí nebo nesedí, nastaví se pole z `new LangOld()` (výchozí, **ne ze souboru**) a zavolá se `write()`. V paměti se pak použijí hodnoty ze souboru, ale na disku zůstanou výchozí texty a po dalším restartu je překlad pryč.
- **Oprava:** Migrovat z načteného obsahu. **S**.

### Nízká

| ID | Oblast | Kde | Popis a scénář | Oprava | Náročnost |
|---|---|---|---|---|---|
| ✅ HUNT-017 (opraveno v 1.3.5, krok 5) | E | `util/BaseCommand.java:59-62` vs README | `/hunt` (root) **nemá** `requires`, alias `/hunts` vyžaduje `hunt.base`. README tvrdí, že `/hunt` vyžaduje `hunt.base`. Odebrání `hunt.base` v LuckPerms tedy nic nezakáže. [ověřeno] | Sjednotit a opravit README. | S |
| HUNT-018 | A | `broadcast/Broadcaster.java:131-135` | Zprávy v okně bufferu se **zahazují**, nefrontují. Všechny globální hunty vznikají najednou, takže i expirují najednou a oznámí se jen 1 ze 7 nových huntů. Druhé chycení do 5 s se neoznámí. [ověřeno] | Agregovat zprávy („Nové hunty: A, B, C“) nebo rozprostřít `endtime`. | S |
| HUNT-019 | A | `hunts/CurrentHunts.java:169-175` | Individuální režim ignoruje flag `broadcast`: hráč po vlastním splnění i po `/hunt refresh` dostane „hunt skončil“. [ověřeno] | Respektovat flag. | S |
| HUNT-020 | A | `util/Utils.java:266` | `{pokemon}` v příkazech = přeložené jméno, případně s titulem marku (`getDisplayName(true)`): „Mr. Mime“, „Farfetch'd“, „Vulpix the Peppy“. Mezery posunou argumenty příkazu. [ověřeno] | Přidat `{species_id}` a `{form}`. | S |
| HUNT-021 | A | `hunts/SingleHunt.java:285-287` | Hunt Pokémon nikdy není shiny, takže `matchProperties.shiny=true` znamená „shiny se NEpočítá“. Matoucí. [ověřeno] | Přejmenovat nebo zdokumentovat, případně možnost „shiny bonus“. | S |
| HUNT-022 | A | `hunts/SpawnRates.java:159-161` | Vyřazuje se jen label `legendary`. `mythical`, `ultra_beast` a `paradox` projdou, pokud je nějaký datapack nebo addon dá do world poolu. [ověřeno, výskyt je otázka] | Konfigurovatelný seznam vyřazených labelů. | S |
| HUNT-023 | F | `fabric.mod.json`, `build.gradle` | Produkce běží na 1.8.4, build kompiluje proti 1.8.0. `cobblemon >=1.8.0` bez horní hranice (1.8 už jednou rozbila spawning API, viz komentář `SpawnRates.java:47-51`). Chybí `fabric-api` v `depends`, `icon.png` neexistuje, `environment: "*"` u server-only módu. SNAPSHOT závislosti, Gradle deprecations (kap. 3), `gradlew` bez +x. [ověřeno] | `cobblemon: ">=1.8.0 <1.9"`, doplnit depends, pinout verze. | S |
| HUNT-024 | F | `command/subcommand/DebugCommand.java`, `old/*`, `Settings.java`, `forge/*`, `util/Utils.java:44-181` | Mrtvý nebo matoucí kód: neregistrovaný Debug (navíc hlásí slot −1), prázdný `Settings.java` v rootu. `readFileAsync`/`writeFileAsync` jsou ve skutečnosti synchronní (`join`, `get`) a `writeFileAsync` nekontroluje částečný zápis. NeoForge modul na Fabric serveru nikdo netestuje. [ověřeno] | Smazat, sjednotit IO na `FileStore`. **Krok 2 (1.3.3):** `Utils` IO metody a mrtvé `LangOld.init` / `ConfigOld.write` jsou odstraněné, veškeré IO jde přes `FileStore`. `LangOld` už nikdo nepoužívá. | S |
| HUNT-025 | B/D | `broadcast/Broadcaster.java:20`, `hunts/HuntManager.java:66-71` | `playerBuffers` roste do reloadu (malý leak, 1 záznam na hráče). `checkOnlinePlayers` každou sekundu kopíruje množinu **všech** známých UUID a hledá je mezi online hráči. Iterovat stačí online hráče. [ověřeno] | Iterovat `getPlayerList().getPlayers()`, čistit při disconnectu. | S |
| HUNT-026 | A | `hunts/CurrentHunts.java:66, 192-197` | `taken` se počítá až po odebrání splněného huntu, takže stejný druh může být hned znovu vylosován. Hráč na spawnu daného druhu dostane šanci 1/|bucket| na okamžité další splnění. [ověřeno, malý dopad] | Vyloučit právě splněný cíl. | S |
| HUNT-027 | A/F | `command/basecommand/HuntCommand.java:127,161-163` | GUI je statické (čas se neaktualizuje). Hráči se zobrazuje `e.getMessage()` (interní detaily). GooeyLibs `GooeyContainer` přepisuje `clicked()` i `quickMoveStack()` a volá `patchDesyncs` [ověřeno v bytecode], takže vytažení itemů je nepravděpodobné a GUI nemá akce. **Doporučuji ruční test** (shift-click, čísla 1–9, drag, double-click, Q, zavření při lagu). | Generická hláška hráči. | S |
| HUNT-028 | E | `stats/HuntStats.java:99-103` | `/hunt stats <jméno>` vrací první shodu bez ohledu na velikost písmen. Offline mode umožní `Steve` i `steve` jako dva účty se dvěma UUID. [ověřeno] | Preferovat přesnou shodu, umožnit zadat UUID. | S |
| HUNT-029 | B | `Hunt.java:86-91` | Vanilla `/reload` (datapacky) neobnoví `SpawnRates`. Hunty drží staré `Species` objekty (matching jede přes jména, takže funkčně OK). [ověřeno] | Hook na `END_DATA_PACK_RELOAD`. | S |
| HUNT-030 | A | `util/Placeholders.java:43` (dříve `Utils.formatPlaceholders`) | `{price}` formátuje `DecimalFormat("0.##")` podle locale JVM. Na serveru s `cs_CZ` (nebo jiným locale s desetinnou čárkou) se cena `1.5` vypíše jako `1,5` a reward příkaz typu `eco give {uuid} {price}` ji nemusí přijmout. Celé ceny se to netýká (bez oddělovače tisíců). [ověřeno testem `decimalSeparatorFollowsServerLocale`, nalezeno v kroku 3] | `DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT))`, aspoň pro příkazy. Krok 6 nebo 9. | S |
| 🟡 HUNT-033 (1.3.6) | E/F | `util/Permissions.java`, README | `CobblemonPermission` přidává k uzlu prefix `cobblemon.` (`getLiteral()` = `"cobblemon." + node`, ověřeno v bytecode 1.8.0 i 1.8.4 a testem). LuckPerms tedy vidí `cobblemon.hunt.reload`, ne `hunt.reload`, jak tvrdilo README. Uzly nastavené podle README (`hunt.*`) nic neudělají: povolení se neuplatní (platí jen OP level) a zákaz taky ne. [ověřeno testem `permissionNodesHaveTheCobblemonPrefix`, nalezeno v kroku 5] | **Rozhodnutí provozovatele (2026-11-02):** přejít na `hunt.*` (v LuckPerms nejsou žádné uzly Hunt nastavené). Opraveno v 1.3.6 přes vlastní `HuntPermission` bez prefixu, viz kapitola 14. | S |
| HUNT-031 | E | `config/Config.java:82-87` | `requireSection` chytí jen explicitní `null`. Chybějící klíč sekce (např. smazané celé `"rewards"`) Gson doplní z no-arg konstruktoru, takže platí **výchozí odměny** (100–1000 a diamanty) bez varování. [ověřeno testem `missingSectionGetsDefaults`, nalezeno v kroku 3] | Ve validaci (krok 6) vyžadovat přítomnost klíčů `economy`, `rarity`, `rewards`, `customPrices`, nebo aspoň WARN. | S |

### Latentní (individualHunts=false, revize 1)
Tyto nálezy se projeví až po zapnutí `individualHunts`. Kód je dnes nepoužívá, ale **před zapnutím režimu je nutné je opravit**. Kdyby se zapnul bez oprav, vrací se na Vysokou.

#### HUNT-005 · Individuální hunty + offline mode = neomezené alt účty na farmení · A · **Nízká – latentní (revize 1, původně Vysoká)** · [ověřeno v kódu; dopad závisí na nastavení → otázka]
- **Kde:** `hunts/HuntManager.java:36-41, 78-91`, `event/CaptureEvent.java:29`, `economy/ImpactorService.java:57` (Impactor účet se pro nové UUID **vytvoří automaticky**).
- **Popis:** V individuálním režimu má každý UUID vlastních až 28 souběžných huntů a odměny se vyplácí na jeho účet. V offline mode stojí nový účet jen nové jméno (UUID je odvozené od jména). Mód nemá limit výplat na hráče nebo IP, cooldown, ani podmínku odehraného času.
- **Scénář:** Hráč má 5 altů. Na každém si v GUI najde „levné“ hunty (common v okolí), chytí je a peníze pošle `/pay` na hlavní účet. Výnos je zhruba 6× vyšší než u poctivého hráče.
- **Dopad:** Inflace ekonomiky. V globálním režimu se problém netýká výplat, jen počtu „lovců“.
- **Oprava:** Policy vrstva výplat: denní strop na UUID (a volitelně na IP přes `player.getIpAddress()`), minimální odehraný čas (`Stats.PLAY_TIME`), nastavitelné v configu. Logovat výplaty kvůli detekci (HUNT-014). **M**.
- **Otázka:** Je v produkci `individualHunts=true`? Je měna převoditelná mezi hráči?

#### HUNT-006 · Individuální režim: neomezený růst dat a paměti, serializace na hlavním vlákně · B/D · **Nízká – latentní (revize 1, původně Vysoká)** · [ověřeno v kódu; velikost dopadu je odhad]
- **Kde:** `hunts/HuntManager.java:16` (mapa se nikdy nečistí), `hunts/SingleHunt.java:31` (každý hunt drží **plný** `com.cobblemon.mod.common.pokemon.Pokemon`), `hunts/HuntStorage.java:41-62` (serializace **všech** huntů všech hráčů, kteří kdy byli online, pretty-print, na hlavním vlákně), `hunts/HuntStorage.java:97-117` (při startu se pro každý uložený hunt staví `new Pokemon()`).
- **Popis:** Každý hráč, který se kdy připojil, zůstává navždy v paměti i v `hunts.json` (N × `huntAmount` objektů `Pokemon`). `Pokemon` má ~77 property a podobjekty (IVs, EVs, MoveSet, BenchedMoves, observables, …). Odhad 2–5 KB na instanci.
- **Scénář (odhad):** 2 000 unikátních hráčů (v offline mode s alty a boty realistické), 7 huntů → 14 000 objektů `Pokemon` ≈ 30–70 MB heapu. Každých 30 s, kdy je cokoli dirty (s 50 online hráči prakticky vždy), se na hlavním vlákně postaví JSON strom s ~14 000 huntů a pretty-printuje (~5 MB). Odhaduji 10–30 ms, což je pravidelný lag spike. Start zpomalí tisíce `new Pokemon()`.
- **Oprava:** Hunt modelovat jako lehký `record HuntSpec(speciesId, form, ability, gender, nature, shiny)`; `Pokemon` vyrábět jen pro GUI. Hunty offline hráčů neudržovat v paměti (lazy load při joinu) nebo je po X dnech neaktivity smazat. Na hlavním vlákně brát jen snapshot immutable recordů a JSON kódovat na `Hunt-Save` vlákně. Kompaktní JSON. **M–L**.
- **Ověření:** `jcmd <pid> GC.class_histogram | grep 'cobblemon.mod.common.pokemon.Pokemon '` a velikost `config/hunt/hunts.json`.

### Otevřené otázky (nutné zjistit na serveru)
1. ~~Je zapnuté `individualHunts`?~~ **Odpověď: ne.** HUNT-005 a HUNT-006 jsou latentní.
2. ~~Proxy a validace jmen?~~ **Odpověď:** Velocity (znaky nekontroluje, ověřeno), AuthMe `[a-zA-Z0-9_]*`. Podmínky 2–4 z HUNT-001 (Velocity `try` a forced-hosts, přímý přístup na backend, přepnutí serveru před přihlášením) provozovatel **ověřil, všechny platí**.
3. ~~Reward příkazy a převoditelnost měny?~~ **Odpověď:** `{player}` se používá, měna je převoditelná.
4. ~~Jaký storage backend používá Impactor (HUNT-011)?~~ **Odpověď: JSON.** HUNT-011 snížen na Nízká.
5. ~~Které módy spawnují chytatelné divoké Pokémony (HUNT-010)?~~ **Odpověď: žádné, jen Cobblemon.** HUNT-010 snížen na Nízká (zůstává PokeSnack, rybaření a `/spawnpokemon`).
6. ~~Přesná verze Cobblemonu v produkci?~~ **Odpověď: 1.8.4.** Bump závislosti v kroku 8. Dotaz na Neruinu a podobné módy už není potřeba, protože HUNT-002 a HUNT-003 jsou opravené v kroku 0.
7. ~~Kolik unikátních UUID má server?~~ Pro globální režim nerelevantní, zůstává jen jako podmínka pro případné zapnutí individuálních huntů.

---

## 5. Výkon

### 5.1 Hotspoty (odhad pro 50 online hráčů)
| Cesta | Frekvence | Práce | Verdikt |
|---|---|---|---|
| `Hunt.check()` globální | 1×/s | 7–28 porovnání `long` | zanedbatelné |
| `Hunt.check()` individuální | 1×/s | kopie množiny všech N známých UUID + 50 × 7 `check()` | malé, ale roste s N (HUNT-025) |
| Capture handler | při chycení | ≤28 × `matches` (≤5 string compare), broadcast 50 paketů | zanedbatelné |
| `HuntStorage.serialize()` globální | ≤1×/30 s | 7 huntů | zanedbatelné |
| `HuntStorage.serialize()` individuální | ≤1×/30 s, prakticky vždy | **7·N huntů → Gson strom + pretty-print na hlavním vlákně** | **reálný problém pro N v tisících** (HUNT-006) |
| Paměť individuální | trvale | 7·N × `Pokemon` (~2–5 KB) | **reálný problém pro N v tisících** |
| Výplata Impactor | při completion | `join()` na účet | závisí na backendu (HUNT-011) |
| `/hunt top` | na příkaz | sort všech hráčů ve stats | malé (10k záznamů < 5 ms), příkaz může spamovat každý hráč |
| GUI `/hunt` | na příkaz | ≤28 `PokemonItem.from` / item | malé |

V **globálním režimu nemá mód žádný měřitelný výkonový problém**. Optimalizovat matching nebo tick je zbytečné.

**Revize 1:** Server používá globální režim. Řádky „individuální“ v tabulce a optimalizace 1–4 v 5.2 se proto týkají jen budoucího zapnutí `individualHunts`. Pro současný provoz zůstává relevantní jen bod 5 (Impactor `join()`, HUNT-011), a to jen pokud Impactor používá vzdálené úložiště.

### 5.2 Navržené optimalizace (jen tam, kde je problém reálný)
1. **Lehký model huntu** (`HuntSpec` record místo `Pokemon`). Proč: HUNT-006, objekt drží ~77 property a podobjekty. Přínos: ~95 % paměti na hunt a rychlý start. Zároveň umožní unit testy.
2. **Hunty jen pro online hráče a prune neaktivních.** Proč: mapa a soubor rostou s každým kdy připojeným UUID. Přínos: serializace a paměť O(online) místo O(všichni).
3. **Snapshot na hlavním vlákně, JSON na writer threadu, bez pretty-printu.** Přínos: hlavní vlákno dělá jen kopii referencí immutable recordů (< 1 ms). Kódování (desítky ms při velkém N) se přesune mimo tick.
4. **Iterovat online hráče** v `checkOnlinePlayers`. Přínos: odpadne alokace setu o velikosti N každou sekundu.
5. **Cache ekonomické služby a předehřátí účtu při joinu.** Proč: `join()` na hlavním vlákně (HUNT-011).

Jak ověřit: spark profiler (`/spark profiler --thread "Server thread"` během uložení) a `jcmd GC.class_histogram`.

---

## 6. Co je uděláno dobře

- **Správný zdroj eventu.** `POKEMON_CAPTURED` vzniká jen při chycení divokého Pokémona ballem a Cobblemon hlídá dvojí post (`captureFuture.isDone`). Trade, GTS, WonderTrade, daycare ani release-a-znovu-chytit hunt nesplní **[ověřeno]**.
- **Bez race conditions.** Všechna herní logika běží na server threadu a matching i odebrání huntu proběhne atomicky v jednom handleru. Dva hráči nemohou splnit stejný globální hunt dvakrát.
- **Zápisy dat** (`FileStore`) běží přes tmp + `ATOMIC_MOVE` na jednom writer threadu. Při stopu se ukládá synchronně a fronta se dočká. Nečitelný soubor se zálohuje jako `.broken`. Uložení na vypnutí se nevolá dvakrát (`loaded` flag).
- **Persistence huntů** přežije restart (`endtime` v epoch ms, bez závislosti na časovém pásmu nebo DST). Hunty s neexistujícím druhem se při načtení přeskočí, ne pád.
- **Form-aware hunty** přes `forcedAspects` (ověřeno, že setter přepočítá formu), herdy, blacklist `Species-Form`, rarita podle species ID místo přeloženého jména.
- **Ekonomika** je za interface a volitelné ekonomiky se volají reflexí (žádná tvrdá závislost). `catch (Exception | LinkageError)` přežije chybějící Impactor.
- **Oprávnění** jdou přes Cobblemon validator, tedy fabric-permissions-api a LuckPerms s fallbackem na OP level.
- GUI na GooeyLibs nemá klikací akce, takže v něm není prostor pro double-claim.

---

## 7. Hodnocení: oprava, nebo rewrite?

### 7.1 Kritéria a důkazy
| Kritérium | Zjištění |
|---|---|
| Kritické a vysoké nálezy podle příčiny | **Revize 1 (globální režim): 5 nálezů.** **4 lokální:** 001, 002, 003 (`Utils` + try/catch, S) a 004 (`Config`/`Lang`, M). **1 architektonický:** 007 (výplata je inline lambda bez transakce a žurnálu); řeší ho vyčlenění jedné třídy, ne přestavba. *Původně 7 nálezů; 005 a 006 (chybějící policy vrstva, model s plným `Pokemon`, `HuntManager` bez životního cyklu) jsou po revizi latentní.* |
| Podíl kódu zasaženého opravami | **Revize 1:** opravy pro globální režim zasáhnou `CaptureEvent` 83, `Utils` (~60 z 298), `Config` 205, `Lang` 187, `BaseCommand` + 4 subcommandy (jen `requires`, ~30) a `FileStore` (~20) ≈ **~600 z 4 250 řádků (~14 %)**. Nová `CompletionService` má ~150 řádků. Smazat jde ~440 řádků (`old/`, `DebugCommand`, `forge/`, pokud se NeoForge nepoužívá). `SingleHunt`, `CurrentHunts`, `HuntManager` a `HuntStorage` zůstanou beze změny. *Původní odhad ~39 % zahrnoval přestavbu individuálního režimu.* |
| Rozšiřitelnost (nové typy huntů a odměn) | Špatná. Typ huntu je natvrdo „chyť druh+formu“ v `SingleHunt.matches` a odměna je `price + commands` natvrdo v `CaptureEvent`. Nový typ (např. „poraz N“, „chyť v biomu“) by znamenal zasahovat do 4 tříd a statického `Hunt`. Datový formát (`hunts.json` v1) je ale jednoduchý a dá se rozšířit polem `type`. |
| Testovatelnost a riziko regresí | Nulové pokrytí. Logiku nejde instancovat bez `Hunt.*` singletonů a běžícího Cobblemonu. Pro globální režim ale stačí testovat dvě místa, kde vznikají exploity: substituci placeholderů (čistá funkce, testovatelná hned) a výplatní tok (po vyčlenění `CompletionService` s injektovanými závislostmi). Matching a generování se v revizi 1 nemění, takže riziko regrese je tam nízké. |
| Náklady kompletního rewrite | Odhad **20–30 kroků** Claude Code (každý = commit s procházejícím buildem). Nutná migrace `config.json` (2 historické formáty), `lang.json`, `hunts.json` v1, `stats.json` v1 a `logs.json`. Riziko nových chyb v netriviální logice svázané s Cobblemonem 1.8 (formy, herdy, buckety). Po dobu přechodu by se zmrazily funkce. Při 4 250 řádcích, z nichž ~40 % je v pořádku, se to nevyplatí. |

### 7.2 Doporučení (revize 1): **1. Ponechat a opravit**
*Původně jsem doporučoval možnost 2 (částečný refaktor). Změnil jsem to, protože hlavní argumenty pro refaktor (HUNT-005, HUNT-006, tedy model s plným `Pokemon` a životní cyklus individuálních huntů) se v globálním režimu neuplatní.*

Důvody podle kritérií:
- Všechny 4 kritické nálezy a HUNT-004 jdou opravit lokálně (S/M) bez změny struktury.
- Jediný architektonický nález (HUNT-007) vyřeší vyčlenění **jedné** třídy `CompletionService` z lambdy v `CaptureEvent`. Je to cílený zásah, ne přestavba.
- Opravy zasáhnou ~14 % kódu. Datové formáty (`hunts.json` v1, `stats.json` v1, `config.json`) zůstanou, takže **odpadá migrace dat** a s ní spojené riziko.
- Rewrite ani refaktor modelu nepřinese v globálním režimu žádnou výkonovou ani bezpečnostní výhodu.

**Kdy to přehodnotit na možnost 2:** pokud se má zapnout `individualHunts` (pak nejdřív opravit HUNT-005 a HUNT-006), nebo pokud přibudou nové typy huntů („poraz N“, biom, ball), protože na ty současný `SingleHunt.matches` a `CaptureEvent` nejsou stavěné. Pro takový případ je níže připravená cílová architektura (7.3).

### 7.3 Cílová architektura (jen pro případný budoucí refaktor, viz 7.2)
```
org.pokesplash.hunt
├─ core/            (čistá Java, žádné importy net.minecraft / com.cobblemon → unit testy)
│  ├─ HuntSpec      record(speciesId, form, ability?, gender?, nature?, shiny?)
│  ├─ HuntInstance  record(id, owner?, spec, bucket, Reward, endsAt)
│  ├─ Reward        record(price, List<CommandTemplate>)
│  ├─ CaughtPokemon record(speciesId, form, ability, gender, nature, shiny, origin)  // view
│  ├─ HuntBoard     kolekce jednoho scope; generate/expire/claim(id) → Optional<HuntInstance>
│  ├─ HuntGenerator (TargetCatalog, RandomSource, Clock, HuntConfig)
│  ├─ Matcher       (HuntSpec, MatchRules, CaughtPokemon) → boolean
│  ├─ PayoutPolicy  denní strop / min. playtime / per-IP (HUNT-005)
│  └─ CommandTemplate  bezpečná substituce: {player} jen [A-Za-z0-9_]{1,16}, {uuid}, {species_id}
├─ app/
│  ├─ CompletionService   claim → journal.append(PENDING) → economy.pay → commands → journal.commit → stats
│  ├─ PayoutJournal       append-only payouts.log + replay při startu (HUNT-007, HUNT-014)
│  ├─ HuntRegistry        global board + online-player boards, lazy load/unload, prune (HUNT-006)
│  └─ ConfigService       load → validate → swap atomicky; při chybě ponechá starý (HUNT-004, -008)
├─ platform/  (adaptéry, tenké, try/catch na hranici)
│  ├─ CobblemonCaptureListener  Pokemon → CaughtPokemon, celé v try/catch (HUNT-003)
│  ├─ CobblemonTargetCatalog    (dnešní SpawnRates)
│  ├─ EconomyGateway            (dnešní bridge, cache instance, předehřátí účtu)
│  ├─ CommandExecutor, Notifier (Broadcaster), PermissionService (nad CommandSourceStack)
│  └─ gui/, command/
└─ persistence/  FileStore (beze změny), HuntRepository (v1 read, v2 write), StatsRepository
```
Tok: `POKEMON_CAPTURED` → `CobblemonCaptureListener` (try/catch, převod) → `CompletionService.onCatch(playerRef, caught)` → `HuntRegistry.boardFor(player).claimFirstMatch(caught)` → `PayoutPolicy.check` → žurnál → ekonomika → příkazy → stats → `HuntRegistry.markDirty` + po completion okamžitý snapshot.

**Testování exploitů** (JUnit 5 v `common`, fake `Clock`, `Random`, `Economy` a `CommandSink`):
- jméno `@a`, `$1`, `a\`, `x;y`, `"`: příkaz se nespustí nebo se bezpečně escapuje a nic nevyhodí výjimku,
- dvě completion stejného huntu → druhá vrátí `empty`,
- ekonomika selže → žádné příkazy, záznam PENDING, retry při startu,
- „pád“ mezi `pay` a `commit` → replay nevyplatí dvakrát (idempotence podle `huntId`),
- expirace přesně na hranici `endsAt`, záporný nebo nulový `huntDuration` → validace odmítne,
- přepínání global ↔ individual, prune neaktivních, strop výplat a alt scénář,
- `ConfigService` s rozbitým JSON → starý config zůstane a soubor se nepřepíše,
- matching: formy (Alola), pohlaví u genderless, nature, abilita, shiny pravidlo.

### 7.4 Pořadí oprav (revize 1, globální režim; každý krok = zelený build, samostatně nasaditelný)
| # | Krok | Řeší | Náročnost |
|---|---|---|---|
| 0 | ✅ **HOTOVO (1.3.2)** · **Hotfix bezpečnosti:** substituce placeholderů přes `String.replace` (literál), validace jména `^[A-Za-z0-9_]{1,16}$` před spuštěním příkazů (při neshodě příkazy přeskočit + WARN), nový placeholder `{uuid}`; `try/catch (Throwable)` kolem celého capture handleru, `Hunt.check()` a každého `HuntEvents` listeneru | 001, 002, 003 | S |
| 1 | ✅ **HOTOVO (1.3.2)** · **Log výplat:** jeden INFO řádek na completion (`uuid, jméno, huntId, druh/forma, bucket, cena, výsledek ekonomiky, provedené příkazy`), ideálně do `config/hunt/payouts.log` | 014 | S |
| 2 | ✅ **HOTOVO (1.3.3)** · **Config se nesmí přepsat:** rozlišit „neexistuje“ a „nečitelný“; při chybě parsování na startu běžet bez odměn + ERROR, při `/hunt reload` ponechat starý config; config a lang zapisovat přes `FileStore` (atomicky); opravit migraci langu; `FileStore.read` při IOException neházet „soubor neexistuje“ | 004, 012, 016 | M |
| 3 | ✅ **HOTOVO (1.3.4)** · **Testy:** JUnit 5 v `common`; testy substituce (`@a`, `Ash$1`, `a\`, `x;y`, `"`), `RarityConfig.getRandomRarity`, načítání rozbitého configu | 015 (část) | S |
| 4 | ✅ **HOTOVO a nasazeno (1.3.4)** · **`CompletionService`:** vyčlenit výplatní tok z `CaptureEvent` (claim → log PENDING → ekonomika → příkazy jen při úspěchu → log DONE → stats), po completion vynutit uložení `hunts.json`; testy (selhání ekonomiky, dvojí completion) | 007 | M |
| 5 | ✅ **HOTOVO a nasazeno (1.3.5)** · **Oprávnění:** `requires` přes `CommandSourceStack` místo `isPlayer() ? check : true`; sjednotit `/hunt` a `/hunts` a opravit README | 009, 017 | S |
| 6 | **Validace configu** s jasnými hláškami (`huntDuration > 0`, `huntAmount 1–28`, váhy, `economy`, ceny, existence druhů v `customPrices` a `blacklist`) | 008 | M |
| 7 | **Admin příkazy:** reset stats, `save`, zobrazení stats podle UUID | 013, 028 | S |
| 8 | **Úklid:** `old/` (po ověření, že nikdo nemá config < 1.2.3), `DebugCommand`, `Settings.java`, Utils IO, `fabric.mod.json` (`cobblemon <1.9`, `fabric-api`), Gradle deprecations, `gradlew +x`; rozhodnout o `forge/` | 023, 024 | S |
| 9 | **Kosmetika a drobnosti:** agregované broadcasty, `{species_id}`, labely mythical/UB/paradox, datapack reload hook, GUI hláška, vyloučení právě splněného cíle | 018, 020, 021, 022, 026, 027, 029 | S |
| — | **Odloženo, dokud se nezapne `individualHunts`:** HUNT-005 (policy výplat), HUNT-006 (lehký model, prune, serializace mimo tick), HUNT-019, HUNT-025 | 005, 006, 019, 025 | M–L |
| — | ~~**Podle výsledku testu na serveru:** HUNT-010 (původ Pokémona), HUNT-011 (Impactor `join()`)~~ **Odpovězeno 2026-11-02:** jen Cobblemon spawny a Impactor na JSON, oba nálezy sníženy na Nízká. Případně krok 8 (cache služby) a 9 (PokeSnack) | 010, 011 | S |

**Migrace dat:** žádná. Formáty `hunts.json`, `stats.json`, `config.json` a `lang.json` zůstávají a nové klíče configu jsou volitelné s defaulty (Gson pro chybějící pole zachová hodnoty z no-arg konstruktoru).

---

## 8. Doporučené pořadí dalších kroků

1. ✅ **Hotovo: podmínky 2–4 z HUNT-001 ověřeny, všechny platí** (viz HUNT-001, stav po revizi 2). Původní zadání: **ověřit podmínky 2–4 z HUNT-001** (`velocity.toml` `try`/forced-hosts, přímý přístup na port backendu, přepnutí serveru před přihlášením) a udělat test na testovací instanci se jménem `Test$1`. AuthMe má regex `[a-zA-Z0-9_]*`, takže pokud tyto tři podmínky platí, je server dnes chráněný a stačí bod 3.
2. ~~**Jen pokud ověření selže**~~ *(nebylo potřeba, ověření prošlo)* (hráč se jménem mimo `[A-Za-z0-9_]` se dostane na Cobblemon server): okamžitě odstranit `{player}` z `captureHuntBroadcast` v `lang.json` a z reward příkazů, pak `/hunt reload`. Odměny přes příkazy tím do hotfixu přestanou fungovat. Placeholder `{uuid}` (vanilla `give` UUID přijme) přidá až krok 0.
3. **Kroky 0 a 1 jako jeden hotfix release.** ✅ Nasazeno 2026-11-02 (v 1.3.3 spolu s krokem 2). Po nasazení 1–2 dny sledovat `payouts.log`. Měna je převoditelná, takže log je jediný způsob, jak zpětně najít případné zneužití.
4. Krok 2 (config) před další úpravou `config.json` na produkci. **Hotovo, otestováno a nasazeno v 1.3.3 (2026-11-02)** (kapitola 10).
5. Kroky 3–4 (testy a `CompletionService`). ✅ **Hotovo, otestováno a nasazeno v 1.3.4 (2026-11-02)** (kapitoly 11 a 12). Krok 5 (oprávnění) ✅ **hotovo, otestováno a nasazeno v 1.3.5 (2026-11-02)** (kapitola 13). Dál krok 6.
6. Kroky 7–9 průběžně.
7. Ruční test GUI (HUNT-027). Test spawn zdrojů (HUNT-010) už není potřeba: podle provozovatele spawnuje divoké Pokémony jen Cobblemon (revize 3).

---

## 9. Plán kroku 0: hotfix bezpečnosti (revize 2) · ✅ HOTOVO a otestováno (1.3.2)

**Stav:** Změny A–I z 9.1 jsou implementované. Z 9.2 prošly body 1 (build zelený, `-Xlint:all` bez nových varování, zůstává jen známé `[this-escape]` v `HuntForge`) a 2 (skript proti `PlayerNames` a `String.replace`: bezpečné je jen `Normal_1`, žádná výjimka, jméno se dosadí doslova). **Bod 3 (testovací server, scénáře a–f a pro krok 1 scénáře g–l) prošel 2026-11-02. Na produkci nasazeno 2026-11-02 v rámci 1.3.3 (obsahuje kroky 0, 1 a 2).** Odchylka od plánu: v individuálním režimu je `manager.checkOnlinePlayers()` chráněné jako celek, ne po jednotlivých huntech (režim se nepoužívá, `HuntManager` jsem neměnil). Build vypisuje „Could not remap …-sources.jar fully“ (NPE v Mercury). Ten se objevuje i na 1.3.1 a týká se jen jaru se zdrojáky.

**Cíl:** Odstranit HUNT-001, HUNT-002 a HUNT-003 přímo v módu, aby bezpečnost nestála jen na AuthMe, a aby žádná výjimka z Hunt nemohla shodit server.
**Mimo rozsah** (řeší pozdější kroky): log výplat (krok 1), config a přepisování souborů (krok 2), testy v Gradle (krok 3), transakční výplata (krok 4), `requires` (krok 5).
**Datové formáty se nemění**, takže návrat na 1.3.1 je bezpečný (stačí vyměnit jar).

### 9.1 Změny po souborech
| # | Soubor | Změna | Řeší |
|---|---|---|---|
| A | nový `common/.../util/PlayerNames.java` | Čistá třída bez importů MC: `static boolean isCommandSafe(String name)` → `name != null && name.matches("[A-Za-z0-9_]{1,16}")` (předkompilovaný `Pattern`). Testovatelné bez serveru. | 001 |
| B | `util/Utils.java:255-275` `formatPlaceholders` | `replaceAll(regex, …)` nahradit `String.replace("{player}", …)`, totéž pro `{pokemon}` a `{price}`. Přidat `{uuid}` → `player.getUUID().toString()`. Pro běžná jména je výstup stejný jako dosud. | 002 (všechny zprávy: broadcast, pay message, příkazy) |
| C | `util/Utils.java:284-297` `runCommands` | Pro každý příkaz: pokud šablona obsahuje `{player}` a `!PlayerNames.isCommandSafe(jméno)`, příkaz **přeskočit** a zalogovat WARN (`uuid`, jméno, šablona). Příkazy bez `{player}`, například s `{uuid}`, proběhnou. `formatPlaceholders` přesunout **dovnitř** `try`. Zachytávat `Exception` místo dnešního `catch` jen kolem `execute`. | 001, 002 |
| D | `event/CaptureEvent.java:24-79` | Rozdělit tok do samostatně chráněných fází, aby chyba v jedné fázi nezrušila ostatní ani výplatu:<br>1. `SingleHunt hunt = currentHunts.removeHunt(id, false)`; pokud `null`, konec. Je to totéž, co dělá `replaceHunt`.<br>2. `try { currentHunts.addHunt(); }`, při chybě ERROR „náhradní hunt se nevytvořil, po opravě configu `/hunt reload`“.<br>3. broadcast CAPTURED v `try`.<br>4. `HuntEvents.COMPLETED.trigger(new CompletedEvent(hunt, uuid))` (chráněno v E).<br>5. výplata (stávající `try`, beze změny).<br>6. `stats.recordCompletion` v `try`.<br>7. `runCommands` (chráněno v C).<br>Celé tělo lambdy navíc obalit vnějším `try/catch (Throwable)`. `VirtualMachineError` (OOM, StackOverflow) se znovu vyhodí, vše ostatní se zaloguje jako ERROR s `uuid`, `huntId` a druhem, aby šlo hráče ručně odškodnit. Pořadí fází a výsledné zprávy zůstávají stejné jako dnes. | 002, 003 |
| E | `api/event/Event.java:30-34` `trigger` | Každý `sub.run(t)` ve vlastním `try/catch (Exception)` + ERROR. Iterovat přes kopii seznamu, aby odhlášení během triggeru nevyhodilo `ConcurrentModificationException`. | 003 |
| F | `Hunt.java:109-124` `check()` | Každé `SingleHunt::check` a blok s ukládáním ve vlastním `try/catch (Exception)`. Při chybě ERROR, další hunty se zpracují dál. Automatické doplňování huntů se **nepřidává**: při rozbitém configu by každou sekundu spamovalo log. Doplnění zajistí `/hunt reload`. | 003 |
| G | `Hunt.java` (nová `onPlayerJoin(UUID)`), `fabric/.../HuntFabric.java:20`, `forge/.../HuntForge.java:44` | Join handler volá `Hunt.onPlayerJoin` s `try/catch` místo přímého `manager.addPlayer`. Ochrana je pak v `common`, takže platí pro obě platformy. V globálním režimu se nic nemění (`addPlayer` hned vrací). | 003 |
| H | `README.md` | Zdokumentovat placeholdery `{player}`, `{uuid}`, `{pokemon}` a `{price}`. Doporučit `{uuid}` všude, kde příkaz přijímá entitu (vanilla `give`, `effect`, `tp`…). Popsat, že příkaz s `{player}` se pro jméno mimo `[A-Za-z0-9_]` nespustí. | 001 |
| I | `gradle.properties` | `mod_version=1.3.2` | – |

Odhad: ~7 souborů, ~120–150 změněných řádků, náročnost **S**.
Mimo rozsah zůstávají `Broadcaster`, `CurrentHunts`, `SingleHunt`, config a lang. `StatsCommand` už používá `String.replace` (`StatsCommand.java:73,81`), takže ho není potřeba měnit.

### 9.2 Ověření před nasazením
1. `bash ./gradlew build` musí být zelený, bez nových `-Xlint` varování.
2. **Kontrola `PlayerNames` a substituce bez serveru:** jednorázový Java skript v `/tmp` proti `common/build/classes` pro jména `Normal_1`, `@a`, `Ash$1`, `a$b`, `Ash\`, `x;y`, `a"b`, `@e[type=player]`. Očekávání: nic nevyhodí výjimku, safe je jen `Normal_1` a `String.replace` dosadí jméno doslova. (JUnit přijde až v kroku 3.)
3. **Testovací server** (offline mode, bez proxy, stejný modpack). Pro snadné testování nastavit `matchProperties` na `false` a hunt Pokémona spawnovat přes `/spawnpokemon <druh>`:
   - a) Běžný hráč chytí hunt → broadcast, zpráva o výplatě, peníze, příkazy, stats. Totéž jako na 1.3.1.
   - b) Hráč `Test$1` (vanilla takové jméno pustí) chytí hunt → server **nespadne**, broadcast zobrazí jméno doslova, peníze přijdou, příkaz s `{player}` se přeskočí s WARN a příkaz s `{uuid}` proběhne.
   - c) Hráč `@a` + `give {player} minecraft:diamond 1` → diamant **nedostane nikdo** a v logu je WARN.
   - d) Rozbitý config: do `customPrices` přidat `{"species": null}` a dát `/hunt reload` → chycení hunt Pokémona → **bez pádu**, výplata proběhne, v logu je ERROR o neúspěšném náhradním huntu, počet huntů klesne o 1. Po opravě configu a `/hunt reload` se hunty doplní.
   - e) Totéž s krátkým `huntDuration` (1 minuta), aby se chyba projevila v ticku: bez pádu, ERROR v logu.
   - f) Restart serveru → `hunts.json` a `stats.json` se načtou beze změny (formát je stejný).
   - **Krok 1 (log výplat), stejný release:**
   - g) Ve scénáři a) přibude v `config/hunt/payouts.log` 1 řádek s `outcome` `PAID` a příkazy `RAN`, v `latest.log` je INFO „Hunt payout: …“.
   - h) Ve scénáři b) má příkaz s `{player}` `SKIPPED_UNSAFE_NAME`, příkaz s `{uuid}` má `RAN` a jméno je v JSON správně escapované.
   - i) Rozbitá ekonomika (např. `economy: PEBBLES` bez Pebbles módu) → `outcome` `ERROR` s textem chyby v `error`, `paid` 0.
   - j) Hunt s cenou 0 → `outcome` `NO_PRICE`. Neplatný reward příkaz → `status` `FAILED` a ostatní příkazy proběhnou.
   - k) Restart nebo `/stop` hned po chycení → řádek je v souboru a soubor se nepřepíše, jen se k němu připisuje.
   - l) `jq -c . config/hunt/payouts.log` načte každý řádek.
4. Kontrola logu: žádné ERROR ani WARN ve scénáři a) a žádný stacktrace z Hunt při normálním hraní.

### 9.3 Nasazení a rollback
1. Zálohovat `config/hunt/` a původní jar 1.3.1.
2. Nasadit jar 1.3.2 při plánovaném restartu. Config ani lang se nemění.
3. Volitelně (doporučeno) přepsat v `config.json` reward příkazy z `{player}` na `{uuid}` tam, kde to cílový příkaz podporuje. U příkazů jiných módů (například `eco give`) nejdřív ověřit, jestli UUID přijímají.
4. První den sledovat log: `grep -E "Hunt.*(WARN|ERROR)"`.
5. **Rollback:** vrátit jar 1.3.1. Data jsou kompatibilní oběma směry.

### 9.4 Rizika kroku 0
- **Změna chování:** pro jména mimo `[A-Za-z0-9_]` se příkazy s `{player}` nespustí. Na produkci taková jména AuthMe nepustí, takže se to neprojeví.
- **Rozdělení `replaceHunt`** na `removeHunt` + `addHunt` v `CaptureEvent` musí zachovat pořadí zpráv: náhradní hunt (a jeho STARTED broadcast) před CAPTURED broadcastem, stejně jako dnes. Hlídá to scénář 3a.
- **Výjimka po odebrání huntu** (fáze 2–7) už nespadne server. Hráč ale může přijít o část odměny (například o příkazy), pokud selže právě tato fáze. Proto ERROR s `uuid` a `huntId` pro ruční odškodnění. Plně to řeší krok 4 (transakční `CompletionService`).

---

## 10. Plán kroku 2: config se nesmí přepsat (1.3.3) · ✅ HOTOVO, otestováno a nasazeno

**Stav:** Změny A–M z 10.1 jsou implementované. Z 10.2 prošel bod 1 (build zelený, `-Xlint:all` bez nových varování, zůstává jen známé `[this-escape]` v `HuntForge`, `readFileAsync`/`writeFileAsync` v kódu nejsou) a offline kontrola (10.2/1b). **Bod 2 (testovací server, scénáře a–t) proběhl a prošel 2026-11-02.** Po restartech odpovídaly zálohy očekávání: `hunts.json.bak.1–2` po dvou bězích, `stats.json.bak.1`, u `config.json` a `lang.json` žádné zálohy (nebyl potřeba převod) a žádné `*.tmp` ani `*.broken`. **Nasazeno na produkci podle 10.3 2026-11-02.**

**Cíl:** Hunt nikdy nepřepíše soubor, který nešel přečíst nebo naparsovat (HUNT-004, HUNT-012, HUNT-016). Všechny zápisy jsou atomické, s rotujícími zálohami.
**Rozhodnutí provozovatele (2026-11-02):** (1) chybný `config.json` na startu → **Hunt pozastaven**: hunty se nenačtou ani negenerují, chycení nic nesplní a `hunts.json` se neukládá; po opravě `/hunt reload` dokončí start. (2) Rotace `.bak.1–3` **při prvním přepisu souboru v každém běhu** (`.bak.1` = stav při posledním startu).
**Mimo rozsah:** validace hodnot (krok 6), `PayoutLog` (beze změny), individuální režim.
**Datové formáty se nemění.** Návrat na 1.3.2 = výměna jaru.

### 10.1 Změny po souborech
| # | Soubor | Změna | Řeší |
|---|---|---|---|
| A | `util/FileStore.java` | `read` vrací `sealed ReadResult`: `Missing` (`NoSuchFileException`), `Ok(content)` a `Failed(IOException)`. `Files.exists` odpadá (při chybě práv vrací `false`). | 012, 004 |
| A2 | `util/FileStore.java` | `write` před atomickým `move` rotuje zálohy: při prvním přepisu souboru v běhu `.bak.2 → .bak.3`, `.bak.1 → .bak.2` a kopie cíle → `.bak.1`. Selhání zálohy = WARN, zápis pokračuje. `payouts.log` (append) se nerotuje. | 012 |
| A3 | `util/FileStore.java` | `write` i `writeSync` vrací `boolean`. | 004 |
| B | `config/Versioned.java` | `protected setVersion`. | 004, 016 |
| C | `config/Config.java`, nový `config/ConfigLoadException.java` | `static Config parse(String)` vrací nový objekt nebo vyhodí `ConfigLoadException`. Lenientní `JsonParser.parseString` jako dřív. Root musí být objekt (prázdný soubor, `[]` → chyba). Sekce `economy`, `rarity`, `rewards`, `matchProperties`, `customPrices` a `blacklist` nesmí být `null`. Zachovány úpravy `huntAmount ≤ 28` a `packIconItem`. Verze se čte z rootu JSON. Chybějící nebo jiná verze → migrace `ConfigOld` (`currencyName` jen pokud není `null`) a `upgraded`. Chybí `usePackIcons` → `upgraded`. `init()` a `write()` jsou pryč. | 004 |
| D | `config/Lang.java` | `static Lang parse(String)`: texty ze souboru, chybějící klíče doplní defaulty. Chybějící nebo jiná verze → `setVersion` a `upgraded`. **Migrace z načteného obsahu**, `LangOld` se nepoužívá. | 016 |
| E | `Hunt.java` | `loadConfigs(startup)` → `LoadReport(configOk, started, errors)`. Config: `Missing` na startu → výchozí soubor (první instalace). `Missing`, `Failed` nebo neplatný na startu → **pauza**. Na reloadu → starý config v paměti. Soubor se v žádném z těchto případů nepřepisuje. Lang: chyba na startu → výchozí texty v paměti, na reloadu → staré texty. Platný soubor s `upgraded` → `FileStore.writeSync` (originál zůstane v `.bak.1`). `load()` načte stats vždy, při chybném configu vypíše ERROR banner „Hunt is PAUSED“ a nespustí `start()` (restore huntů, `fillHunts`, `loaded = true`). `reload()` při pauze a platném configu zavolá `start()`. Nové `isActive()`. | 004 |
| F | `command/subcommand/ReloadCommand.java` | Chyby z reportu jdou adminovi červeně do chatu (a jako ERROR do logu v `Hunt`). Při startu z pauzy „Config loaded, Hunt started.“, jinak připomínka, že Hunt je pořád pozastaven. | 004 |
| G | `event/CaptureEvent.java` | Pojistka `if (!Hunt.isActive()) return`. | 004 |
| H | `RefreshCommand.java`, `HuntCommand.java` | Při pauze `refresh` odmítne a GUI ukáže „Hunts are temporarily unavailable.“ | 004 |
| I | `stats/HuntStats.java` | `stats.json` `Failed` (nebo chybějící `stats.json` a `Failed` `logs.json`) → `writable = false`, ERROR. `saveIfDirty` loguje jednou za běh, `saveNow` vždy. Chyba parsování beze změny (`.broken`). | 012 |
| J | `hunts/HuntStorage.java` | `hunts.json` `Failed` → nové hunty se vygenerují, ale soubor se v běhu nepřepíše. | 012 |
| K | `util/Utils.java`, `old/ConfigOld.java`, `old/LangOld.java` | Smazány `readFileAsync`, `readFileSync`, `writeFileAsync`, `writeFileSync` a mrtvé `ConfigOld.write`, `LangOld.init`. | 004, 024 |
| L | `README.md` | Sekce *Broken config files* a *Data safety*, doplněk u `/hunt reload`. | – |
| M | `gradle.properties` | `mod_version=1.3.3` | – |

Změněno 16 souborů a 1 nový (+494 / −402 řádků).

### 10.2 Ověření
1. **Build:** `bash ./gradlew build` a `--rerun-tasks` s `-Xlint:all`: ✅ zelený, žádné nové varování.
   - **1b. Offline kontrola** (jednorázový skript ve scratchpadu proti `common/build/classes`, bez serveru): ✅
     - `Config.parse`: výchozí config OK. Prázdný soubor, `[]`, chybějící čárka (hláška „Unterminated object at line 10 column 23“), `"rewards": null` a `"huntAmount": "abc"` → `ConfigLoadException`. Bez `version` a s verzí `1.2.2` → OK, `upgraded`, měna zachována. `huntAmount` 40 → 28. Bez `usePackIcons` → `upgraded`.
     - `Lang.parse`: rozbitý JSON → výjimka. `"version": "0.9"` i bez `version` → vlastní texty zachované, verze `1.0.0`, `stats*` doplněné a `upgraded`.
     - `FileStore`: neexistující soubor → `Missing`. `chmod 000` → `Failed` (`AccessDeniedException`) + ERROR. Čtyři „běhy“ se třemi zápisy → v každém běhu jediná rotace, maximálně `.bak.3`, žádný `.tmp`.
2. **Testovací server** (offline mode, stejný modpack, `matchProperties` `false`, `/spawnpokemon`; před každým scénářem zálohovat `config/hunt/`, „beze změny“ = `sha256sum` před a po). ✅ **Proběhlo a prošlo (2026-11-02), scénáře a–t bez nálezů.**
   - **Config, startup:**
     - a) Čistá instalace → vznikne `config.json` a `lang.json`, žádný `.bak`, hunty běží.
     - b) Rozbitý JSON (chybí čárka v `customPrices`) → ERROR „PAUSED“ s řádkem a sloupcem. `config.json`, `hunts.json` i `stats.json` beze změny i po `/stop`. `/hunt` hlásí pauzu, `/hunt refresh` odmítne, chycení nic neudělá (žádný řádek v `payouts.log`), `/hunt stats` funguje. Oprava + `/hunt reload` → „Hunt started“, hunty obnovené z `hunts.json`, výplata podle configu.
     - c) Prázdný `config.json` a `[]` → jako b).
     - d) Config bez `version` → migrace, hodnoty zachované, `config.json.bak.1` = originál.
     - e) `chmod 000 config.json` (server ne jako root) → pauza, ERROR `AccessDeniedException`, soubor beze změny.
   - **Config, reload:**
     - f) Rozbitý JSON → červená hláška v chatu a ERROR v logu, soubor beze změny, vyplácí se **stará** cena. Oprava → `reloadMessage`, nové hodnoty platí.
     - g) Platná změna `huntAmount` 7 → 9 → hunty se doplní.
     - h) Chybějící config → „missing, previous config is still in use“, soubor nevznikne.
     - i) `chmod 000` → starý config zůstane, hláška v chatu i logu.
   - **Lang:**
     - j) Rozbitý `lang.json` na startu → výchozí texty, soubor beze změny, Hunt běží **s odměnami**. Na reloadu → staré texty a hláška.
     - k) Starý `lang.json` (vlastní `captureHuntBroadcast`, verze `0.9` nebo bez verze, bez `stats*`) → přepsán s vlastními texty, verzí `1.0.0` a doplněnými `stats*`, `.bak.1` = originál. Po dalším restartu texty pořád vlastní.
     - l) Chybějící `lang.json` na startu → vygeneruje se.
   - **Data:**
     - m) `chmod 000 stats.json` → ERROR, Hunt běží. Po splnění huntu, > 30 s a `/stop` je ERROR „not saved“ a soubor beze změny.
     - n) `chmod 000 hunts.json` → nové hunty, soubor se nepřepíše.
     - o) Chybí `stats.json` a `logs.json` je nečitelný → `stats.json` nevznikne.
     - p) Rozbitý JSON ve `stats.json` → `.broken` jako dřív, `.bak.1` = rozbitý originál.
   - **Zálohy:**
     - q) První uložení `stats.json` v běhu vytvoří `.bak.1`, další uložení ho nemění (mtime). Čtyři restarty se změnou dat → `.bak.1–3`, nikdy `.bak.4`.
     - r) Žádný `*.tmp` po zápisech.
   - **Regrese:**
     - s) 9.2/3 a) a g) (výplata a řádek `PAID`) beze změny.
     - t) Data z 1.3.3 načte 1.3.2.

### 10.3 Nasazení a rollback
1. **Před nasazením ověřit produkční soubory:** `jq . config/hunt/config.json` a `jq . config/hunt/lang.json`. `jq` je přísnější než lenientní Gson, takže když selže, zkontrolovat obsah ručně. Ověřit `"version": "1.2.3"`. Jinak Hunt nastartuje pozastavený.
2. Zálohovat `config/hunt/` a jar 1.3.2. Nasadit 1.3.3 při restartu.
3. Po startu: `grep -E "Hunt.*(PAUSED|ERROR)" logs/latest.log` je prázdné a `/hunt` ukazuje hunty.
4. **Rollback:** vrátit jar 1.3.2. Formáty jsou stejné a `.bak.*` 1.3.2 ignoruje. 1.3.2 ale chybný config znovu přepíše výchozími hodnotami.

### 10.4 Rizika a odchylky od plánu
- **Pauza místo tichého startu:** opravdu rozbitý config dřív vedl k výchozím odměnám, teď vede k pauze. Je to záměr, proto 10.3/1. Config bez `version` (dřív přepis) teď projde migrací.
- **Lenientní parser** je zachovaný (`JsonParser.parseString`), takže configy, které 1.3.2 načetla, načte i 1.3.3. Výjimka jsou configy s `null` v povinné sekci nebo s nečíselnou hodnotou v číselném poli: dřív vedly k pádu nebo k přepisu, teď k pauze nebo ponechání starého configu.
- **Chybějící `config.json` při reloadu** se negeneruje (běžící server se nesmí přepnout na výchozí odměny). Výchozí soubor vzniká jen na startu.
- **Rotace jednou za běh:** v singleplayeru se statická množina rotovaných souborů mezi světy nemaže. Týká se jen počtu záloh.
- **Odchylky:** `HuntStats.init()` se volá v `load()` před kontrolou configu, ne v `start()`, aby `/hunt stats` a `/hunt top` fungovaly i při pauze. Stats se při pauze nemění a neukládají (`stop()` gatuje `loaded`). `RefreshCommand` hlídá pauzu i u individuálního refreshe.


---

## 11. Plán kroku 3: JUnit 5 testy · ✅ HOTOVO (vyšlo s krokem 4 v 1.3.4)

**Stav:** Změny A–K z 11.1 jsou implementované a ověření z 11.2 prošlo (2026-11-02). `mod_version` zůstává `1.3.3`: runtime chování se nemění (refaktor je čistá delegace), takže se krok nasadil až s krokem 4 (1.3.4, na produkci 2026-11-02).

**Cíl:** Opakovatelná síť proti regresím v místech, kde vznikaly exploity a ztráty dat (HUNT-001, 002, 004, 012, 016), ještě před krokem 4, který mění výplatní tok. Jednorázové skripty z 9.2/2 a 10.2/1b jsou teď testy v buildu.
**Mimo rozsah:** matching (`SingleHunt.matches`), generování huntů (`CurrentHunts`), `HuntStats`, `HuntStorage` a výplatní tok. Ty závisí na Cobblemon objektech nebo statickém `Hunt.*` a přijdou s `CompletionService` (krok 4).

### 11.1 Změny po souborech
| # | Soubor | Změna |
|---|---|---|
| A | `common/build.gradle` | JUnit BOM `5.14.4` (poslední 5.x), `junit-jupiter`, `junit-platform-launcher` (`testRuntimeOnly`, Gradle 8.14 ho vyžaduje). `test { useJUnitPlatform() }`, `workingDir = build/test-run` (před během se vyprázdní, `FileStore` zapisuje relativně do `config/hunt`), výpis selhání s plným stacktracem. |
| B | nový `util/Placeholders.java` | Čistá třída bez MC importů: `fill(template, playerName, uuid, pokemonName, price)` se stejnou sémantikou jako dřívější `formatPlaceholders`, a `commandAllowed(template, playerName)`. |
| C | `util/Utils.java` | `formatPlaceholders` jen vytáhne jméno, UUID a display name a deleguje na `Placeholders.fill`. `runCommands` volá `Placeholders.commandAllowed`. Logy, `CommandResult` a pořadí jsou beze změny. |
| D | `config/RarityConfig.java` | Přetížení `getRandomRarity(Predicate<Bucket>, RandomGenerator)`. Původní metody delegují s `ThreadLocalRandom.current()`. |
| E | test `util/PlaceholdersTest` (33) | Jména `@a`, `Ash$1`, `a$b`, `Ash\`, `x;y`, `a"b`, `@e[type=player]`, `$0`, `\$1`… se dosadí doslova a nic nevyhodí. Příkaz s `{player}` je blokovaný pro každé nebezpečné jméno i `null`. Příkaz s `{uuid}` projde. Dál `null` šablona, chybějící hodnoty, opakované placeholdery, formát ceny (`1000` bez oddělovače, `1.257` → `1.26`) a doklad HUNT-030 (`cs-CZ` → `1,5`). |
| F | test `util/PlayerNamesTest` (22) | Hranice 1 a 16 znaků, 17 znaků, prázdné jméno, `null`, mezera, `§`, diakritika, selektory a speciální znaky. |
| G | test `config/RarityConfigTest` (10) | Hranice bucketů pro váhy 60/30/9/1 (roll 0/59/60/89/90/98/99) a `bound == 100`. Nulové a záporné váhy, filtr vylučující vše, filtr bez COMMON (`bound == 40`), jediný kladný bucket. Rozdělení ze 100 000 losů se seedem 42 v toleranci ±1 p. b. Config se staví přes Gson stejně jako při načítání. |
| H | test `config/ConfigParseTest` (29) | Scénáře z 10.2/1b: výchozí config, vlastní hodnoty, prázdný soubor / `[]` / `"text"` / `42` / `null` → výjimka, chybějící čárka (zpráva s „line“), useknutý soubor, `null` v každé ze 6 povinných sekcí, text v číselném poli, neznámá `economy`, sekce špatného typu, `huntAmount` 40 → 28, prázdné `packIconItem`, chybějící ikonové volby → `upgraded`, migrace z 1.2.2 s `impactorCurrencyName`, chybějící `version`, lenientní syntax (komentář, klíč bez uvozovek). Doklady HUNT-031 a HUNT-032. |
| I | test `config/LangParseTest` (10) | Nečitelný soubor → výjimka. Verze `0.9` a soubor bez verze a bez `stats*`/`top*` → vlastní texty zachované, verze `1.0.0`, nové klíče z defaultů, totéž v `toJson()` pro přepis. |
| J | test `util/FileStoreTest` (9) | `Missing`. `Ok` s UTF-8 obsahem (`§`, `é`). `Failed` pro adresář a pro neplatné UTF-8, bez `chmod`, takže funguje i pod rootem. Žádný `.tmp`. Nový soubor bez `.bak`. První přepis → `.bak.1` = originál, další přepis v témže běhu `.bak.1` nemění. Posun `.bak.1–3` bez `.bak.4`. 50 `appendAsync` řádků ve správném pořadí, `writeSync` na ně počká, append se nerotuje. |
| — | test `HuntInitTest` (1) | Statická inicializace `Hunt` (Permissions, Broadcaster, SpawnRates…) projde bez serveru a bez bootstrapu MC. |

Změněny 3 soubory, přibyl 1 produkční a 7 testových souborů (~700 řádků testů).

### 11.2 Ověření
1. ✅ `bash ./gradlew build`: zelený, `:common:test` 114 testů, 0 failed, 0 skipped. `fabric:test` a `forge:test` `NO-SOURCE`.
2. ✅ `--rerun-tasks` s `-Xlint:all` (init skript ve scratchpadu): v produkčním kódu žádné nové varování, zůstává jen známé `[this-escape]` v `HuntForge`. Při kompilaci testů hlásí `-Xlint:processing` „No processor claimed…“ (loom přidává annotation processor i do testů). Neškodí a v běžném buildu se neobjevuje.
3. ✅ **Mutační kontrola** (dočasná změna kódu → testy musí spadnout → vrácení): `replace` → `replaceAll` (5 failed), `commandAllowed` bez kontroly jména (10), `commandAllowed` blokuje i příkazy bez `{player}` (1), off-by-one v `nextInt` (5), záporné váhy se nesčítají jako 0 (1), povolené `"rewards": null` (1), migrace langu z defaultů (2), rotace při každém zápisu (1), `IOException` → `Missing` (2). Přežila jen mutace „vypnout kontrolu `isJsonObject()`“. Je ekvivalentní: `getAsJsonObject()` pak hodí `IllegalStateException`, ze které vznikne stejná `ConfigLoadException`.
4. Testovací server není potřeba: změna `Utils` je jen delegace. Regresi hlídá 9.2/3 a), g) při testu kroku 4.

### 11.3 Rizika a odchylky od plánu
- **Žádný fallback nebyl potřeba:** `Hunt` se v testu inicializuje bez serveru a loom dává MC a Cobblemon na test classpath. `Config`, `Lang` a `FileStore` proto dál používají `Hunt.LOGGER`.
- **Odchylka:** JUnit `5.14.4` místo plánovaného `5.13.4` (novější 5.x na Maven Central). Mutační kontrola má navíc mutace pro `commandAllowed`, záporné váhy, `Lang` a `FileStore`.
- **Nové nálezy:** HUNT-030 (locale `{price}`, Nízká), HUNT-031 (chybějící sekce → výchozí odměny, Nízká), HUNT-032 (config bez `version` → změna ekonomiky a měny, Střední). Testy zatím **dokumentují současné chování** (jsou to doklady, ne požadavky) a při opravě se otočí. Doporučuji opravit v kroku 6.
- **`FileStore.ROTATED` je statická** pro celý test JVM, takže testy rotace používají unikátní názvy souborů a ověřují jen jeden „běh“. Scénář přes 4 restarty zůstává ověřený ručně (10.2/2 q).
- **Síť:** první build stáhne JUnit z Maven Central (v gradle cache nebyl).


---

## 12. Plán kroku 4: `CompletionService` (1.3.4) · ✅ HOTOVO, otestováno a nasazeno

**Stav:** Změny A–K z 12.2 jsou implementované. Z 12.4 prošly body 1 (build zelený, 138 testů, `-Xlint:all` bez nových varování) a 2 (mutační kontrola 10 z 10). **Bod 3 (testovací server, scénáře a–g) proběhl a prošel** (2026-11-02). **Nasazeno na produkci podle 12.5 2026-11-02.**

**Cíl:** Vyřešit HUNT-007. Splněný hunt se po tvrdém pádu nesmí vrátit, selhání ekonomiky nesmí spustit reward příkazy a každá rozběhnutá výplata musí být dohledatelná. Výplatní tok má být testovatelný bez serveru.
**Rozhodnutí provozovatele (2026-11-02):** (1) nedokončené výplaty (PENDING bez DONE) se při startu **jen nahlásí**, nic se nevyplácí automaticky (u pádu mezi depositem a DONE nejde zjistit, jestli peníze odešly). (2) Při selhání ekonomiky se reward příkazy **přeskočí** a hráč dostane zprávu. Hunt s cenou 0 příkazy spouští dál. (3) Žurnál je `payouts.log` s polem `phase` (`PENDING`/`DONE`) a `completionId`.
**Mimo rozsah:** matching, generování huntů, individuální režim, Impactor `join()` (HUNT-011), admin příkaz pro odškodnění (krok 7).
**Datové formáty:** `hunts.json`, `stats.json` a `config.json` beze změny. `payouts.log` má nové řádky PENDING, DONE řádek má navíc `phase` a `completionId`. `lang.json` dostane klíč `payFailedMessage`.

### 12.1 Nový tok
1. **claim:** `removeHunt` (`null` → konec, hunt už splnil někdo jiný), náhradní hunt (`addHunt`, chráněno) a `HuntStorage.saveSoon()`, tedy zápis `hunts.json` hned zařazený na writer thread.
2. Broadcast CAPTURED a `HuntEvents.COMPLETED`, každé zvlášť chráněné.
3. `journal.pending`: synchronní append s fsync přes writer thread. Writer je FIFO, takže po návratu je na disku i `hunts.json` z bodu 1. Selhání zápisu → ERROR, výplata pokračuje.
4. Výplata (jen při `price > 0`): `PAID` / `DECLINED` / `ERROR` / `NO_PRICE`.
5. Příkazy jen při `PAID` nebo `NO_PRICE`. Jinak `SKIPPED_PAYMENT_FAILED` a `payFailedMessage`.
6. Stats (`paid`).
7. `journal.done`: async append s fsync, formát z 1.3.3 + `phase` + `completionId`, INFO do `latest.log`.

Při startu (`Hunt.start`) `PayoutJournal.scan()` proudově přečte `payouts.log`. PENDING bez DONE vypíše ERROR banner. Všechna `huntId` z logu se předají `HuntStorage.load`, takže už splněný hunt se z `hunts.json` neobnoví, ani když se `hunts.json` nestihl nebo nemohl zapsat.

### 12.2 Změny po souborech
| # | Soubor | Změna |
|---|---|---|
| A | nový `event/CompletionService.java` | Orchestrace 12.1 bez MC importů. `record Completion`, `enum Outcome`, `record PayResult`, `record Result`. Porty `HuntClaimer`, `EconomySource` (vrací `HuntEconomy`), `RewardCommands`, `Notifier`, `StatsRecorder`, `Journal`. Každý krok chráněný (`Exception | LinkageError`). |
| B | `event/CaptureEvent.java` | Jen listener a adaptéry nad `CurrentHunts`, `HuntEconomyProvider`, `Utils.runCommands`, `Broadcaster`, `HuntStats` a `PayoutJournal`. Vnější `try/catch (Throwable)` zůstává. |
| C | `event/PayoutLog.java` → `event/PayoutJournal.java` | `pending` (přes `FileStore.appendSync`), `done` (přes `appendAsync`) a `scan()` → `Scan(incomplete, claimedHuntIds, unreadable)`. Nečitelné řádky a neplatné UTF-8 se přeskočí, nezastaví čtení. |
| D | `util/FileStore.java` | `appendSync` (čeká přes writer thread i na dřív zařazené zápisy). `append` píše přes `FileChannel` + `force(true)`, takže fsync platí i pro `appendAsync`. |
| E | `hunts/HuntStorage.java` | `saveSoon()` a `load(Set<UUID> completed)` s WARN o vyřazených huntech. |
| F | `Hunt.java` | `scanPayoutJournal()` v `start()`: banner „Unfinished hunt payouts“ s celými PENDING řádky. Chyba čtení start neshodí. |
| G | `util/CommandResult.java` | Status `SKIPPED_PAYMENT_FAILED`. |
| H | `config/Lang.java` | `payFailedMessage`. Chybějící klíč → `upgraded`, takže se do souboru doplní (originál v `.bak.1`). |
| I | `util/Utils.java` | `runCommands` bere `List<String>` místo `ArrayList<String>`. |
| J | `README.md` | Formát `payouts.log`, filtr `jq 'select(.outcome != null)'`, selhání ekonomiky, nedokončené výplaty a jak je označit jako vyřešené (připsat DONE řádek s `completionId`), uložení `hunts.json` po splnění. |
| K | `gradle.properties` | `mod_version=1.3.4` (obsahuje i krok 3). |

Testy: nové `event/CompletionServiceTest` (14) a `event/PayoutJournalTest` (9), v `LangParseTest` +1 (`payFailedMessage`).

### 12.3 Odchylky od plánu
- `Outcome` je v `CompletionService` (spolu s `PayResult`), ne v `PayoutJournal`. Obě třídy jsou bez MC importů, na chování to nemá vliv.
- Port ekonomiky je `EconomySource → HuntEconomy` místo `Payer`, aby se v logu zachoval název ekonomiky i při výjimce z `add()` (jako v 1.3.3).
- Pořadí stats a příkazů je teď příkazy → stats (dřív stats → příkazy). Zprávy hráči a broadcasty mají pořadí stejné.
- Filtr `HuntStorage.load` podle žurnálu nemá unit test (vyžaduje Cobblemon `Pokemon`). Hlídá ho scénář 12.4/3 d.

### 12.4 Ověření
1. ✅ `bash ./gradlew build`: zelený, `:common:test` 138 testů, 0 failed. `--rerun-tasks` s `-Xlint:all`: žádné nové varování (zůstává `[this-escape]` v `HuntForge` a „No processor claimed“ při kompilaci testů).
2. ✅ **Mutační kontrola:** příkazy i při selhání platby (3 failed), druhý claim se nekontroluje (1), PENDING až po platbě (3), PENDING se nezapisuje (4), stats dostanou cenu i při selhání (2), `scan` ignoruje DONE (2), staré řádky bez `phase` neoznačí hunt (1), chybí zpráva o selhání (2), `payFailedMessage` se nedoplní (1), `appendSync` ignoruje chybu zápisu (1). Nezkoušeno: „PENDING přes `appendAsync`“. Jde o trvanlivost na disku a deterministický test by vyžadoval zablokovat writer thread.
3. ✅ **Testovací server** (prošlo 2026-11-02) (offline, `matchProperties` false, `/spawnpokemon`, před scénáři zálohovat `config/hunt/`):
   - a) Běžné chycení → broadcast, peníze, příkazy, stats. V `payouts.log` PENDING a DONE `PAID` se stejným `completionId`. `hunts.json` má hned po chycení (bez čekání 30 s) náhradní hunt.
   - b) `economy: PEBBLES` bez módu → DONE `ERROR`, příkazy `SKIPPED_PAYMENT_FAILED`, hráč dostal `payFailedMessage`, žádný diamant.
   - c) Cena 0 → `NO_PRICE`, příkazy běží.
   - d) Chytit a do 30 s `kill -9` → po restartu splněný hunt není zpět. Pak z `payouts.log` smazat DONE řádek a restartovat → banner „Unfinished hunt payouts“ s tím řádkem. Připsat DONE podle README → po restartu banner zmizí. Navíc: obnovit `hunts.json` ze zálohy před chycením → hunt se neobnoví a v logu je WARN „Dropped 1 saved hunts…“.
   - e) `lang.json` z 1.3.3 → doplní se `payFailedMessage`, vlastní texty zůstanou, `.bak.1` = originál.
   - f) `jq -c . config/hunt/payouts.log` načte všechny řádky včetně řádků z 1.3.3.
   - g) Regrese 9.2/3 a), b) (`Test$1`: `{player}` přeskočen, `{uuid}` proběhne) a 10.2/2 s).

### 12.5 Nasazení a rollback · ✅ nasazeno 2026-11-02
1. Zálohovat `config/hunt/` a jar 1.3.3. Nasadit 1.3.4 při restartu.
2. Po startu: `grep -E "Hunt.*(PAUSED|ERROR|Unfinished)" logs/latest.log` je prázdné.
3. Skripty nad `payouts.log` upravit na filtr `select(.outcome != null)` (jen DONE a řádky z 1.3.3, bez PENDING a bez ručně připsaných `resolved` řádků).
4. **Rollback:** jar 1.3.3. Formáty `hunts.json` a `stats.json` jsou stejné. 1.3.3 do `payouts.log` jen připisuje a klíč `payFailedMessage` v `lang.json` ignoruje.

### 12.6 Rizika
- Synchronní fsync na hlavním vlákně při každém splnění huntu (jednotky ms, splnění je vzácné). Čeká i na případný zařazený zápis `hunts.json` nebo `stats.json`.
- Při selhání ekonomiky hráč nedostane itemy z příkazů. Je to záměr (rozhodnutí 2).
- `scan()` čte celý `payouts.log` při každém startu. 100k splnění je ≈ 40–80 MB, proudově řádově 1–2 s. Při velkém logu ho archivovat (po vyřešení všech nedokončených výplat).

---

## 13. Plán kroku 5: oprávnění (1.3.5) · ✅ HOTOVO, otestováno a nasazeno

**Stav:** Změny A–F z 13.2 jsou implementované. Z ověření prošel build (161 testů, `-Xlint:all` bez nových varování) a mutační kontrola 6 z 6 (2026-11-02). **Test na serveru (13.4, scénáře a–f) proběhl a prošel a 1.3.5 je nasazená na produkci (2026-11-02).** Odchylky a nový nález HUNT-033 jsou v 13.6.

**Cíl:** Oprávnění se kontrolují u zdroje příkazu (`CommandSourceStack`), ne podle `isPlayer() ? kontrola : povolit` (HUNT-009). `/hunt` a `/hunts` se chovají stejně a README odpovídá kódu (HUNT-017).
**Rozhodnutí provozovatele (2026-11-02):** `hunt.base` řídí **jen GUI**. Bez něj `/hunt` i `/hunts` nezobrazí GUI a hráč dostane hlášku. Podpříkazy (`stats`, `top`, `reload`, `refresh`) se řídí jen svými oprávněními.
**Mimo rozsah:** nové admin příkazy (krok 7), `DebugCommand` (neregistrovaný, smaže krok 8), NeoForge validator.
**Datové formáty se nemění.** Návrat na 1.3.4 = výměna jaru.

### 13.1 Ověřené chování (Cobblemon-fabric 1.8.4 bytecode)
- `PermissionValidator` má `hasPermission(CommandSourceStack, Permission)`. `FabricPermissionValidator` ho implementuje jako `me.lucko.fabric.api.permissions.v0.Permissions.check(source, node, level)`.
- fabric-permissions-api u zdroje příkazu: pokud je entitou zdroje hráč, rozhoduje LuckPerms podle hráče. Jinak (konzole, command block, `execute as <ne-hráč>`) platí fallback `source.hasPermission(level)`, tedy OP level zdroje. `execute as` OP level nemění, takže `execute as @e[type=cow] run hunt refresh` od běžného hráče (level 0) neprojde. Konzole (level 4) a command blocky (level 2) projdou.
- `Cobblemon.setPermissionValidator` existuje, takže testy mohou podstrčit falešný validator.

### 13.2 Změny po souborech
| # | Soubor | Změna | Řeší |
|---|---|---|---|
| A | `util/Permissions.java` | `hasPermission(CommandSourceStack, CobblemonPermission)` přes Cobblemon validator a `Predicate<CommandSourceStack> requires(String name)` pro `.requires(...)`. Neznámý název → `IllegalArgumentException` už při registraci (překlep se neprojeví až za běhu). | 009 |
| B | `command/subcommand/ReloadCommand.java`, `RefreshCommand.java`, `StatsCommand.java`, `TopCommand.java` | `.requires(Hunt.permissions.requires("HuntReload"))` atd. místo `isPlayer() ? … : true`. | 009 |
| C | `util/BaseCommand.java` | Alias `/hunts` bez vlastního `requires`, jen `redirect` na kořen. Kořen ani alias tak nikoho neschovají a podpříkazy fungují i přes `/hunts`. | 017 |
| D | `command/basecommand/HuntCommand.java` | `run()`: hráč bez `hunt.base` (kontrola nad zdrojem) → „§5[Hunt] §cYou don't have permission to open the hunts.“ a návrat. Pořadí: hráč? → oprávnění → pauza → GUI. | 017 |
| E | `README.md` | Tabulka příkazů: `/hunt` (alias `/hunts`) a `hunt.base` jen pro GUI. Výchozí úrovně (`hunt.base`, `stats`, `top` = 0, `reload`, `refresh` = OP 2). Odstavec o konzoli, command blocích a `execute as`. | 017 |
| F | `gradle.properties` | `mod_version=1.3.5` | – |

Odhad: ~8 souborů, ~80 změněných řádků, náročnost **S**.

### 13.3 Testy (JUnit 5)
- **`CommandPermissionTest`:** `HuntCommand` zaregistrovaný do vlastního `CommandDispatcher`, falešný `PermissionValidator` přes `Cobblemon.setPermissionValidator` a `CommandSourceStack` bez entity se zadaným OP levelem. Rozhoduje, jestli `dispatcher.parse(...)` najde příkaz (bez spuštění):
  - level 0 bez entity (= `execute as <kráva>` od hráče): `hunt refresh`, `hunt reload`, `hunts refresh` a `hunts reload` neprojdou. `hunt stats` a `hunt top` projdou (úroveň 0).
  - level 2 (command block) a 4 (konzole): vše projde.
  - `/hunts stats` a `/hunt stats` se chovají stejně (alias už nic neschovává).
  - Že se oprávnění ptá validatoru přes zdroj, ne přes hráče: falešný validator zaznamená volání `hasPermission(CommandSourceStack, …)`.
  - Neznámý název v `requires(...)` → výjimka.
- Pokud by `CommandSourceStack` nešel v testu bez bootstrapu MC sestavit, fallback je test samotného predikátu z `Permissions.requires` s falešným validatorem a stubem zdroje. Rozhodnu při implementaci a zapíšu do odchylek.
- Mutační kontrola: vrátit `isPlayer() ? … : true` u `refresh`, vynechat kontrolu `hunt.base` v GUI, `requires` na aliasu zpět.

### 13.4 Ověření na testovacím serveru · ✅ prošlo 2026-11-02
- a) Běžný hráč bez OP: `/hunt`, `/hunts`, `/hunt stats`, `/hunt top` fungují. `/hunt reload` a `/hunt refresh` nejsou v tab completion a hlásí neznámý příkaz.
- b) Týž hráč s `minecraft.command.execute` (LuckPerms): `/execute as @e[type=cow,limit=1] run hunt refresh` **neprojde** a hunty se nezmění. Totéž s `hunt reload`.
- c) OP 2 nebo LuckPerms `cobblemon.hunt.refresh` / `cobblemon.hunt.reload` (pozor na prefix, HUNT-033): příkazy fungují, i přes `/hunts refresh`.
- d) Konzole: `hunt reload`, `hunt refresh` fungují. Command block s `hunt refresh` funguje.
- e) LuckPerms `cobblemon.hunt.base=false`: `/hunt` i `/hunts` vypíšou hlášku o oprávnění, `/hunt stats` a `/hunt top` fungují.
- f) Regrese: 9.2/3 a) (chycení, výplata) a 12.4/3 a) (PENDING/DONE).

### 13.5 Rizika
- **Změna chování:** non-player zdroje s OP levelem pod 2 (např. NPC nebo jiné módy, které spouštějí `hunt refresh` jako entita bez OP) přestanou mít přístup. To je záměr HUNT-009.
- **`execute as <jiný hráč>`:** když má hráč `minecraft.command.execute` a spustí příkaz jako admin, LuckPerms posuzuje oprávnění admina. To je vlastnost fabric-permissions-api a týká se všech módů na serveru, ne jen Hunt. Opatření je nedávat běžným hráčům `minecraft.command.execute`. Mód to sám vyřešit neumí, protože `CommandSourceStack` původního hráče nepamatuje.

### 13.6 Výsledek implementace a odchylky
- **Nový nález HUNT-033:** uzly oprávnění mají prefix `cobblemon.` (viz tabulka nízkých nálezů). Test ho odhalil, protože falešný validator dostával `cobblemon.hunt.refresh`. README teď uvádí skutečné uzly, výchozí úrovně a příklad pro LuckPerms. Názvy uzlů se v kódu nemění.
- **Testy:** `command/CommandPermissionTest` (22): `CommandSourceStack` jde v testu sestavit bez bootstrapu MC, takže fallback na test samotného predikátu nebyl potřeba. Falešný validator hodí chybu, kdyby se oprávnění kontrolovalo přes hráče místo zdroje.
- **Mutační kontrola:** `refresh` zpět na `isPlayer() ? … : true` (3 failed), `reload` bez `requires` (4), alias zpět s `hunt.base` (1), `stats` s cizím oprávněním (4), `requires` toleruje neznámý název (1), `requires` vždy `true` (8). Kontrola `hunt.base` v GUI (`HuntCommand.run`) test nemá, protože potřebuje `ServerPlayer`. Hlídá ji scénář 13.4 e.
- **Odchylka:** `BaseCommand` dostal getter `getPermission()`, aby `HuntCommand` nepsal název oprávnění podruhé. Kontrola v GUI vrací `0` (neúspěch), ne `1`.

---

## 14. HUNT-033: uzly oprávnění bez prefixu (1.3.6) · 🟡 IMPLEMENTOVÁNO, čeká na test na serveru

**Cíl:** Uzly oprávnění jsou `hunt.base`, `hunt.reload` atd., jak je odjakživa uvádělo README. Dosud je `CobblemonPermission` měnil na `cobblemon.hunt.*` (HUNT-033).
**Rozhodnutí provozovatele (2026-11-02):** přejít na `hunt.*`. V LuckPerms nejsou žádné uzly Hunt nastavené, takže změna nic nerozbije.
**Datové formáty se nemění.** Návrat na 1.3.5 = výměna jaru (uzly pak budou zase `cobblemon.hunt.*`).

### 14.1 Změny
| # | Soubor | Změna |
|---|---|---|
| A | nový `util/HuntPermission.java` | Implementuje Cobblemon `Permission`. `getLiteral()` vrací uzel beze změny, `getIdentifier()` je `hunt:<uzel>`, `getLevel()` výchozí OP level. Fabric validator posílá do fabric-permissions-api právě `getLiteral()` (ověřeno v kroku 5). |
| B | `util/Permissions.java`, `util/BaseCommand.java` | Typ `Permission` místo `CobblemonPermission`, oprávnění se vytvářejí jako `HuntPermission`. Názvy a výchozí úrovně beze změny. |
| C | `command/CommandPermissionTest` | Test `permissionNodesHaveNoPrefix` a uzly bez prefixu ve falešném validatoru. |
| D | `README.md` | Uzly `hunt.*`, příklad pro LuckPerms, poznámka, že 1.3.5 omylem používala `cobblemon.hunt.*`. |
| E | `gradle.properties` | `mod_version=1.3.6` |

### 14.2 Ověření
1. ✅ `bash ./gradlew build`: zelený, 161 testů. Mutace „`getLiteral()` zase s prefixem `cobblemon.`“ shodí 4 testy.
2. ⬜ **Testovací server (LuckPerms):**
   - a) Hráč bez OP s `lp user <hráč> permission set hunt.refresh true` → `/hunt refresh` funguje. S `cobblemon.hunt.refresh` (bez `hunt.refresh`) nefunguje.
   - b) `lp user <hráč> permission set hunt.base false` → `/hunt` i `/hunts` vypíšou hlášku o oprávnění, `/hunt stats` funguje.
   - c) `lp user <hráč> permission set hunt.stats false` → `/hunt stats` není dostupný.
   - d) Bez LuckPerms uzlů: chování jako v 1.3.5 (OP 2 pro `reload`/`refresh`, ostatní pro všechny). Regrese 13.4 b) (`execute as` krávy neprojde).
   - e) `lp verbose on` při `/hunt` ukáže kontrolu `hunt.base` (ne `cobblemon.hunt.base`).

### 14.3 Rizika
- NeoForge validator Cobblemonu může používat `getIdentifier()` místo `getLiteral()`. NeoForge modul se na produkci nepoužívá (viz HUNT-024), proto se tam neověřuje.

