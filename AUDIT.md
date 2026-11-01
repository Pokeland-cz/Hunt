# Audit projektu Hunt (Cobblemon addon)

Datum: 2026-11-01 · Commit: `f7af8f0` (master) · Auditor: externí, kód jsem nepsal a nic jsem neměnil (kromě tohoto souboru).

Verze z build souborů: Minecraft 1.21.1, Fabric Loader 0.16.0, Fabric API 0.107.0+1.21.1, Cobblemon **1.8.0+1.21.1** (compile), GooeyLibs 3.1.0-1.21.1-SNAPSHOT (jar-in-jar), Impactor API 5.3.0-SNAPSHOT, NeoForge 21.1.75, Gradle 8.14.2, Java 21. `fabric.mod.json`: `cobblemon >=1.8.0`, `minecraft =1.21.1`, `fabricloader >=0.14.22`.

Značení: **[ověřeno]** = viděno v kódu Hunt nebo ve zdrojácích/bytecode Cobblemonu, MC nebo GooeyLibs. **[podezření]** = plyne z kódu, ale dopad je potřeba ověřit na serveru. **[otázka]** = z kódu nejde zjistit.

**Revize 1 (2026-11-01), odpovědi provozovatele:** `individualHunts` se nepoužívá, server běží za **Velocity**, měna jde **převádět mezi hráči** a reward příkazy **obsahují `{player}`**. Podle toho jsou upravené závažnosti (HUNT-005 a HUNT-006 jsou teď latentní), shrnutí, kapitola 7 (doporučení se změnilo) a kapitola 8. Podrobnosti jsou v kapitole 4.0.

**Stav oprav (2026-11-01):**
| Krok (7.4) | Stav | Poznámka |
|---|---|---|
| 0 – hotfix bezpečnosti (kapitola 9) | ✅ **Hotovo v kódu (1.3.2)**, ⏳ čeká test na serveru | Body A–I z 9.1 hotové. Ověření 9.2/1 (build, `-Xlint`) a 9.2/2 (`PlayerNames` + `String.replace`) prošlo. 9.2/3 (testovací server, scénáře a–f) **zatím neproběhlo**. |
| 1–9 | ⬜ Neprovedeno | |

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
- **Testy: žádné.** `compileTestJava NO-SOURCE` / `test NO-SOURCE` ve všech modulech. V `test-data/stats.json` (untracked) jsou jen ukázková data.
- Gradle deprecation (rozbije přechod na Gradle 9/10): `archivesBaseName` (`build.gradle:33`), `BasePluginConvention`, `JavaPluginConvention` a `ConfigureUtil` (shadow plugin 7.1.2 ve `fabric/build.gradle:2`), Groovy „space assignment“ (`settings.gradle:3-5`, `build.gradle:47,58`, `fabric/build.gradle:22-23`, `forge/build.gradle:20`).
- Závislosti: `gooeylibs 3.1.0-SNAPSHOT` a `impactor 5.3.0-SNAPSHOT` jsou SNAPSHOT, takže build není reprodukovatelný. Shadow plugin `com.github.johnrengelman.shadow 7.1.2` je zastaralý (nástupce `com.gradleup.shadow`). Kotlin plugin 2.2.0 se aplikuje, ale v projektu není žádný Kotlin.
- V gradle cache je i `cobblemon-1.8.4+1.21.1`. Pokud běží produkce na 1.8.4, kompilace proti 1.8.0 projde (API ověřeno výše), ale je vhodné bumpnout.

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

#### HUNT-004 · Chyba v configu přepíše config výchozími hodnotami · C/E · [ověřeno]
- **Kde:** `config/Config.java:60-119`, `util/Utils.java:125-181`, `config/Lang.java:119-178`.
- **Popis:** Gson výjimka v callbacku → `readFileAsync` ji chytí → `readFileSync` zavolá callback znovu → `false` → `Config.init` vypíše *„No config.json file found“* a `write()` zapíše **aktuální objekt v paměti** přes soubor admina (`Config.java:108-110`).
  - Při **startu** je v paměti `new Config()`, tedy výchozí hodnoty: 100/500/700/1000 v primární měně Impactoru, `give {player} minecraft:diamond 1–4`, Magikarp za 500 (`RewardsConfig.java:15-18`, `CustomPrice.java:9-12`). Server tak běží s výchozí ekonomikou a adminův config je pryč.
  - Při `/hunt reload` se přepíše adminova nová verze starou verzí z paměti a admin přijde o úpravy.
  - Zápis configu a langu (`Utils.writeFileAsync`) **není atomický** (`TRUNCATE_EXISTING` a jeden `write` bez kontroly počtu zapsaných bajtů). Pád během zápisu nechá prázdný nebo useknutý soubor a při dalším startu nastane výše popsaný scénář.
- **Scénář:** Admin přidá do `customPrices` položku a zapomene čárku, pak restartuje. Hunt běží s výchozími cenami, hráči dostávají diamanty a peníze podle defaultů a v logu je jen INFO „No config.json file found“.
- **Oprava:** Rozlišit „soubor neexistuje“ od „soubor je nečitelný“. Při chybě parsování **nikdy nepřepisovat**: na startu odmítnout start Hunt (nebo běžet bez odměn) s ERROR, při reloadu ponechat starý config. Config a lang zapisovat přes `FileStore` (tmp + atomic move). **M**.

#### HUNT-007 · Výplata není transakční vůči stavu huntu a selhání není vidět · A/C · [ověřeno v kódu; scénář pádu je podezření]
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

#### HUNT-009 · Oprávnění se obchází ze zdroje, který není hráč · E · [ověřeno]
- **Kde:** `command/subcommand/RefreshCommand.java:32-39`, `ReloadCommand.java:26-33`, `StatsCommand.java:33`, `TopCommand.java:29`, `util/BaseCommand.java:69-76`: `if (ctx.isPlayer()) {check} else return true`.
- **Popis:** `isPlayer()` je false pro konzoli, command blocky a také pro `/execute as <ne-hráč> run hunt refresh` a pro módy, které spouštějí příkazy jménem entity nebo NPC.
- **Scénář:** Hráč, který má přes LuckPerms `minecraft.command.execute` (některé servery ho dávají kvůli `/execute` v minihrách), napíše `/execute as @e[type=cow,limit=1] run hunt refresh` a přelosuje globální hunty („reroll“ na lukrativní cíle). Stejně tak `hunt reload`.
- **Oprava:** Kontrolovat `source.hasPermission(level)` nebo `Permissions.check(source, node, level)` nad `CommandSourceStack`. Cobblemon má `hasPermission(CommandSourceStack, Permission)`. **S**.

#### HUNT-010 · Hunt se splní jakýmkoli divokým Pokémonem bez ohledu na původ · A · [ověřeno: matching; podezření: konkrétní zdroje]
- **Kde:** `event/CaptureEvent.java:24-30`, `hunts/SpawnRates.java:53,60` (rarita a cena se počítají jen z `worldBuckets`).
- **Popis:** Cobblemon postuje event jen pro divoké Pokémony chycené ballem (dobře). Nezáleží ale na tom, odkud Pokémon pochází: PokeSnack (1.8, bucket váhy `rare 4.125 / ultra-rare 1.375` proti world `0.5 / 0.2`), rybaření, admin `/spawnpokemon`, spawner bloky, raid nebo lure módy. Cena „ultra-rare“ se ale počítá z vzácnosti ve světě.
- **Scénář:** Hráč zjistí, že ultra-rare cíl jde přilákat PokeSnackem nebo že ho spawnuje farma nebo spawner. Opakovaně farmí ultra-rare odměny.
- **Oprava:** Volitelný filtr původu. Cobblemon ukládá spawn kontext a data (`pokemon.persistentData` / `PokemonEntity` spawn cause); je potřeba ověřit, co je v 1.8 dostupné. Nebo aspoň počítat raritu ze všech bucket map. **M**.
- **Otázka:** Jaké módy na serveru spawnují chytatelné divoké Pokémony (raidy, lures, spawnery)?

#### HUNT-011 · Blokující `CompletableFuture.join()` ekonomiky na hlavním vlákně · B/D · [podezření]
- **Kde:** `economy/ImpactorService.java:57` (`service.account(currency, uuid).join()`), `api/economy/HuntEconomyProvider.java:56-69` (při **každé** výplatě nová instance služby a u Pebbles/Blanket reflexe s `Class.forName`).
- **Popis:** Pokud Impactor používá SQL úložiště nebo účet není v cache (alt účet, viz HUNT-005), tick stojí na IO.
- **Oprava:** Účet předem načíst při joinu, případně platit asynchronně a výsledek vrátit na hlavní vlákno (`server.execute`). Instanci služby cachovat. **S–M**.
- **Otázka:** Jaký storage backend používá Impactor na serveru?

#### HUNT-012 · Chyba IO při čtení dat = „soubor neexistuje“ → přepsání · C · [ověřeno]
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

#### HUNT-016 · Nesoulad verze lang.json zapíše výchozí texty · C · [ověřeno]
- **Kde:** `config/Lang.java:125-139`. Když `version` chybí nebo nesedí, nastaví se pole z `new LangOld()` (výchozí, **ne ze souboru**) a zavolá se `write()`. V paměti se pak použijí hodnoty ze souboru, ale na disku zůstanou výchozí texty a po dalším restartu je překlad pryč.
- **Oprava:** Migrovat z načteného obsahu. **S**.

### Nízká

| ID | Oblast | Kde | Popis a scénář | Oprava | Náročnost |
|---|---|---|---|---|---|
| HUNT-017 | E | `util/BaseCommand.java:59-62` vs README | `/hunt` (root) **nemá** `requires`, alias `/hunts` vyžaduje `hunt.base`. README tvrdí, že `/hunt` vyžaduje `hunt.base`. Odebrání `hunt.base` v LuckPerms tedy nic nezakáže. [ověřeno] | Sjednotit a opravit README. | S |
| HUNT-018 | A | `broadcast/Broadcaster.java:131-135` | Zprávy v okně bufferu se **zahazují**, nefrontují. Všechny globální hunty vznikají najednou, takže i expirují najednou a oznámí se jen 1 ze 7 nových huntů. Druhé chycení do 5 s se neoznámí. [ověřeno] | Agregovat zprávy („Nové hunty: A, B, C“) nebo rozprostřít `endtime`. | S |
| HUNT-019 | A | `hunts/CurrentHunts.java:169-175` | Individuální režim ignoruje flag `broadcast`: hráč po vlastním splnění i po `/hunt refresh` dostane „hunt skončil“. [ověřeno] | Respektovat flag. | S |
| HUNT-020 | A | `util/Utils.java:266` | `{pokemon}` v příkazech = přeložené jméno, případně s titulem marku (`getDisplayName(true)`): „Mr. Mime“, „Farfetch'd“, „Vulpix the Peppy“. Mezery posunou argumenty příkazu. [ověřeno] | Přidat `{species_id}` a `{form}`. | S |
| HUNT-021 | A | `hunts/SingleHunt.java:285-287` | Hunt Pokémon nikdy není shiny, takže `matchProperties.shiny=true` znamená „shiny se NEpočítá“. Matoucí. [ověřeno] | Přejmenovat nebo zdokumentovat, případně možnost „shiny bonus“. | S |
| HUNT-022 | A | `hunts/SpawnRates.java:159-161` | Vyřazuje se jen label `legendary`. `mythical`, `ultra_beast` a `paradox` projdou, pokud je nějaký datapack nebo addon dá do world poolu. [ověřeno, výskyt je otázka] | Konfigurovatelný seznam vyřazených labelů. | S |
| HUNT-023 | F | `fabric.mod.json`, `build.gradle` | `cobblemon >=1.8.0` bez horní hranice (1.8 už jednou rozbila spawning API, viz komentář `SpawnRates.java:47-51`). Chybí `fabric-api` v `depends`, `icon.png` neexistuje, `environment: "*"` u server-only módu. SNAPSHOT závislosti, Gradle deprecations (kap. 3), `gradlew` bez +x. [ověřeno] | `cobblemon: ">=1.8.0 <1.9"`, doplnit depends, pinout verze. | S |
| HUNT-024 | F | `command/subcommand/DebugCommand.java`, `old/*`, `Settings.java`, `forge/*`, `util/Utils.java:44-181` | Mrtvý nebo matoucí kód: neregistrovaný Debug (navíc hlásí slot −1), prázdný `Settings.java` v rootu. `readFileAsync`/`writeFileAsync` jsou ve skutečnosti synchronní (`join`, `get`) a `writeFileAsync` nekontroluje částečný zápis. NeoForge modul na Fabric serveru nikdo netestuje. [ověřeno] | Smazat, sjednotit IO na `FileStore`. | S |
| HUNT-025 | B/D | `broadcast/Broadcaster.java:20`, `hunts/HuntManager.java:66-71` | `playerBuffers` roste do reloadu (malý leak, 1 záznam na hráče). `checkOnlinePlayers` každou sekundu kopíruje množinu **všech** známých UUID a hledá je mezi online hráči. Iterovat stačí online hráče. [ověřeno] | Iterovat `getPlayerList().getPlayers()`, čistit při disconnectu. | S |
| HUNT-026 | A | `hunts/CurrentHunts.java:66, 192-197` | `taken` se počítá až po odebrání splněného huntu, takže stejný druh může být hned znovu vylosován. Hráč na spawnu daného druhu dostane šanci 1/|bucket| na okamžité další splnění. [ověřeno, malý dopad] | Vyloučit právě splněný cíl. | S |
| HUNT-027 | A/F | `command/basecommand/HuntCommand.java:127,161-163` | GUI je statické (čas se neaktualizuje). Hráči se zobrazuje `e.getMessage()` (interní detaily). GooeyLibs `GooeyContainer` přepisuje `clicked()` i `quickMoveStack()` a volá `patchDesyncs` [ověřeno v bytecode], takže vytažení itemů je nepravděpodobné a GUI nemá akce. **Doporučuji ruční test** (shift-click, čísla 1–9, drag, double-click, Q, zavření při lagu). | Generická hláška hráči. | S |
| HUNT-028 | E | `stats/HuntStats.java:99-103` | `/hunt stats <jméno>` vrací první shodu bez ohledu na velikost písmen. Offline mode umožní `Steve` i `steve` jako dva účty se dvěma UUID. [ověřeno] | Preferovat přesnou shodu, umožnit zadat UUID. | S |
| HUNT-029 | B | `Hunt.java:86-91` | Vanilla `/reload` (datapacky) neobnoví `SpawnRates`. Hunty drží staré `Species` objekty (matching jede přes jména, takže funkčně OK). [ověřeno] | Hook na `END_DATA_PACK_RELOAD`. | S |

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
2. ~~Proxy a validace jmen?~~ **Odpověď:** Velocity (znaky nekontroluje, ověřeno), AuthMe `[a-zA-Z0-9_]*`. **Zbývá ověřit:** podmínky 2–4 z HUNT-001 (Velocity `try` a forced-hosts, přímý přístup na backend, přepnutí serveru před přihlášením).
3. ~~Reward příkazy a převoditelnost měny?~~ **Odpověď:** `{player}` se používá, měna je převoditelná.
4. Jaký storage backend používá Impactor (HUNT-011)?
5. Které módy spawnují chytatelné divoké Pokémony (HUNT-010)?
6. Přesná verze Cobblemonu v produkci (v cache je 1.8.4)? Běží nějaký mód, který chytá výjimky z ticku entit (např. Neruina)? Ten by změnil HUNT-002 a HUNT-003 z „pád serveru“ na „smazání entity Pokéballu“.
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
| 1 | **Log výplat:** jeden INFO řádek na completion (`uuid, jméno, huntId, druh/forma, bucket, cena, výsledek ekonomiky, provedené příkazy`), ideálně do `config/hunt/payouts.log` | 014 | S |
| 2 | **Config se nesmí přepsat:** rozlišit „neexistuje“ a „nečitelný“; při chybě parsování na startu běžet bez odměn + ERROR, při `/hunt reload` ponechat starý config; config a lang zapisovat přes `FileStore` (atomicky); opravit migraci langu; `FileStore.read` při IOException neházet „soubor neexistuje“ | 004, 012, 016 | M |
| 3 | **Testy:** JUnit 5 v `common`; testy substituce (`@a`, `Ash$1`, `a\`, `x;y`, `"`), `RarityConfig.getRandomRarity`, načítání rozbitého configu | 015 (část) | S |
| 4 | **`CompletionService`:** vyčlenit výplatní tok z `CaptureEvent` (claim → log PENDING → ekonomika → příkazy jen při úspěchu → log DONE → stats), po completion vynutit uložení `hunts.json`; testy (selhání ekonomiky, dvojí completion) | 007 | M |
| 5 | **Oprávnění:** `requires` přes `CommandSourceStack` místo `isPlayer() ? check : true`; sjednotit `/hunt` a `/hunts` a opravit README | 009, 017 | S |
| 6 | **Validace configu** s jasnými hláškami (`huntDuration > 0`, `huntAmount 1–28`, váhy, `economy`, ceny, existence druhů v `customPrices` a `blacklist`) | 008 | M |
| 7 | **Admin příkazy:** reset stats, `save`, zobrazení stats podle UUID | 013, 028 | S |
| 8 | **Úklid:** `old/` (po ověření, že nikdo nemá config < 1.2.3), `DebugCommand`, `Settings.java`, Utils IO, `fabric.mod.json` (`cobblemon <1.9`, `fabric-api`), Gradle deprecations, `gradlew +x`; rozhodnout o `forge/` | 023, 024 | S |
| 9 | **Kosmetika a drobnosti:** agregované broadcasty, `{species_id}`, labely mythical/UB/paradox, datapack reload hook, GUI hláška, vyloučení právě splněného cíle | 018, 020, 021, 022, 026, 027, 029 | S |
| — | **Odloženo, dokud se nezapne `individualHunts`:** HUNT-005 (policy výplat), HUNT-006 (lehký model, prune, serializace mimo tick), HUNT-019, HUNT-025 | 005, 006, 019, 025 | M–L |
| — | **Podle výsledku testu na serveru:** HUNT-010 (původ Pokémona), HUNT-011 (Impactor `join()`) | 010, 011 | M |

**Migrace dat:** žádná. Formáty `hunts.json`, `stats.json`, `config.json` a `lang.json` zůstávají a nové klíče configu jsou volitelné s defaulty (Gson pro chybějící pole zachová hodnoty z no-arg konstruktoru).

---

## 8. Doporučené pořadí dalších kroků

1. **Hned: ověřit podmínky 2–4 z HUNT-001** (`velocity.toml` `try`/forced-hosts, přímý přístup na port backendu, přepnutí serveru před přihlášením) a udělat test na testovací instanci se jménem `Test$1`. AuthMe má regex `[a-zA-Z0-9_]*`, takže pokud tyto tři podmínky platí, je server dnes chráněný a stačí bod 3.
2. **Jen pokud ověření selže** (hráč se jménem mimo `[A-Za-z0-9_]` se dostane na Cobblemon server): okamžitě odstranit `{player}` z `captureHuntBroadcast` v `lang.json` a z reward příkazů, pak `/hunt reload`. Odměny přes příkazy tím do hotfixu přestanou fungovat. Placeholder `{uuid}` (vanilla `give` UUID přijme) přidá až krok 0.
3. **Kroky 0 a 1 jako jeden hotfix release.** Po nasazení 1–2 dny sledovat `payouts.log`. Měna je převoditelná, takže log je jediný způsob, jak zpětně najít případné zneužití.
4. Krok 2 (config) před další úpravou `config.json` na produkci.
5. Kroky 3–4 (testy a `CompletionService`), pak 5–6.
6. Kroky 7–9 průběžně.
7. Ruční test GUI (HUNT-027) a test spawn zdrojů (HUNT-010) na testovacím serveru se stejným modpackem.

---

## 9. Plán kroku 0: hotfix bezpečnosti (revize 2) · ✅ HOTOVO v kódu (1.3.2)

**Stav:** Změny A–I z 9.1 jsou implementované. Z 9.2 prošly body 1 (build zelený, `-Xlint:all` bez nových varování, zůstává jen známé `[this-escape]` v `HuntForge`) a 2 (skript proti `PlayerNames` a `String.replace`: bezpečné je jen `Normal_1`, žádná výjimka, jméno se dosadí doslova). **Bod 3 (testovací server, scénáře a–f) a nasazení podle 9.3 zatím neproběhly.** Odchylka od plánu: v individuálním režimu je `manager.checkOnlinePlayers()` chráněné jako celek, ne po jednotlivých huntech (režim se nepoužívá, `HuntManager` jsem neměnil). Build vypisuje „Could not remap …-sources.jar fully“ (NPE v Mercury). Ten se objevuje i na 1.3.1 a týká se jen jaru se zdrojáky.

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
