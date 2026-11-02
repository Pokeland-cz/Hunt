# Audit projektu Hunt (Cobblemon addon)

Audit: 2026-11-01 na commitu `f7af8f0` (master), externí auditor. Závěrečný stav: 2026-11-03, na produkci běží **1.3.10**.

Značení: **[ověřeno]** = viděno v kódu Hunt nebo ve zdrojácích/bytecode Cobblemonu, MC nebo GooeyLibs. **[podezření]** = plyne z kódu, dopad je potřeba ověřit na serveru.

Podrobné plány jednotlivých kroků (změny po souborech, testovací scénáře, mutační kontroly) jsou v historii gitu, naposledy v commitu `ef56855`.

---

## 1. Shrnutí

Mód je malý (~6k řádků Javy v `common` a `fabric`). Audit našel tři zásadní problémy:
1. **HUNT-001/002:** jméno hráče se do reward příkazů a broadcastů dosazovalo přes `String.replaceAll`. Jméno `@a` rozdalo odměnu všem a jméno s `$` nebo `\` shodilo server.
2. **HUNT-003:** capture ani tick handler neměly `try/catch`, takže jakákoli výjimka shodila server.
3. **HUNT-004:** překlep v `config.json` vedl k tichému přepsání configu výchozími odměnami.

**Stav:** kroky 0–9 opravného plánu jsou hotové, otestované na testovacím serveru a nasazené (1.3.2–1.3.10, 2026-11-02 až 2026-11-03). Všechny kritické, vysoké a střední nálezy jsou opravené nebo snížené na Nízkou. Otevřené zůstávají jen HUNT-010 a HUNT-011 (Nízká), zbytek HUNT-013 a HUNT-015 a latentní nálezy individuálního režimu (HUNT-005, 006, 019, 025), viz kapitola 5.

**Hodnocení:** doporučení „ponechat a opravit“ se potvrdilo. Všechny nálezy pro globální režim šly opravit lokálně, jediný architektonický zásah bylo vyčlenění `CompletionService`. Datové formáty se nezměnily a žádná migrace dat nebyla potřeba. Kompletní rewrite by se nevyplatil. **Přehodnotit**, pokud se má zapnout `individualHunts` (nejdřív opravit latentní nálezy, 5.5) nebo pokud přibudou nové typy huntů („poraz N“, biom, ball), na které `SingleHunt.matches` a `CompletionService` nejsou stavěné.

---

## 2. Provozní kontext (odpovědi provozovatele)

| Údaj | Dopad |
|---|---|
| `individualHunts` se nepoužívá (globální režim) | HUNT-005, 006, 019 a 025 jsou latentní. |
| Proxy **Velocity**, offline mode | Velocity ani vanilla znaky ve jméně nekontrolují (`ServerLoginPacket.decode` jen délku, `GameProfile.forOfflinePlayer` jméno předá beze změny) [ověřeno]. |
| AuthMe na lobby s `allowedNicknameCharacters: '[a-zA-Z0-9_]*'` | Jména mimo `[a-zA-Z0-9_]` lobby vykopne. Podmínky níže provozovatel ověřil, všechny platí. |
| Reward příkazy používají `{player}`, měna jde převádět mezi hráči | HUNT-001/002 byly dosažitelné při průniku jména. Peníze z exploitu jdou okamžitě přelít, proto `payouts.log`. |
| Impactor ukládá do JSON | HUNT-011 snížen na Nízká. |
| Divoké Pokémony spawnuje jen Cobblemon (žádné raidy, lures, spawnery) | HUNT-010 snížen na Nízká. |
| Produkce na Cobblemonu 1.8.4 | Build kompiluje proti 1.8.4, `fabric.mod.json` vyžaduje `>=1.8.4 <1.9.0-`. |

**Podmínky, na kterých stojí ochrana jmen mimo mód** (platí dál, mód je ale od 1.3.2 bezpečný i bez nich):
1. AuthMe na lobby má `allowedNicknameCharacters` `[a-zA-Z0-9_]*` nebo přísnější.
2. Ve `velocity.toml` není Cobblemon server v `[servers] try` ani ve `[forced-hosts]` (pozor na `failover-on-unexpected-server-disconnect`).
3. Cobblemon server přijímá spojení jen z Velocity (modern forwarding secret, port nedostupný zvenku).
4. Hráč nemůže přejít na Cobblemon server (`/server`) dřív, než se v AuthMe přihlásí.

Při změně infrastruktury (nový vstupní server, jiný auth plugin) je potřeba podmínky ověřit znovu.

---

## 3. Mapa módu (stav 1.3.10)

### 3.1 Z pohledu hráče
- `/hunt` (alias `/hunts`) otevře GUI (GooeyLibs chest, 3–6 řad) s až 28 cíli: druh, forma, případně pohlaví, abilita, nature a shiny (podle `matchProperties`), odměna, zbývající čas (statický) a rarita. GUI nemá klikací akce.
- **Splnění huntu:** hráč chytí Pokéballem **divokého** Pokémona, který odpovídá huntu. Druh a forma musí sedět vždy, ostatní vlastnosti jen podle `matchProperties`. `shiny=true` znamená, že hunt splní jen shiny úlovek. Hunt se hned nahradí jiným cílem, dostane peníze (Impactor, Pebbles nebo Blanket), provedou se reward příkazy a zapíše se statistika.
- **Globální režim:** jedna sada huntů pro celý server, odměnu dostane, kdo chytí první. Broadcasty „začal/skončil“ se v okně `bufferDuration` slučují, „chycen“ jde hned.
- `/hunt stats [jméno|uuid]`, `/hunt top [completions|earned]`.
- Denní ani týdenní resety nejsou. Každý hunt má vlastní `endtime` (epoch ms), časová pásma ani letní čas nehrají roli.

### 3.2 Z pohledu admina
- Config `config/hunt/config.json` a `config/hunt/lang.json`. Chybný config na startu Hunt pozastaví, chybný config při `/hunt reload` nechá v platnosti starý. Validátor rozlišuje chyby a varování (README, *Config checks*).
- Data: `hunts.json` (v1), `stats.json` (v1), append-only `payouts.log` (PENDING/DONE) a zálohy `.bak.1–3`. Zápisy jsou atomické.
- Příkazy: `reload`, `refresh [hráč]`, `admin save`, `admin stats reset <jméno|uuid> [confirm]`, `admin stats resetall [confirm]`. Uzly `hunt.*` přes Cobblemon permission validator (fabric-permissions-api, LuckPerms, jinak OP level), kontrola nad zdrojem příkazu.
- Ceny podle rarity: bucket `common/uncommon/rare/ultra-rare` z `BestSpawner.config.worldBuckets` a `CobblemonSpawnPools.WORLD_SPAWN_POOL`, u více bucketů platí nejběžnější. Vyřazené jsou labely z `excludedLabels` a blacklist (`Druh` nebo `Druh-Forma`). `customPrices` mají přednost.

### 3.3 Třídy (`common`, `org.pokesplash.hunt`)
| Balíček / třída | Role |
|---|---|
| `Hunt` | Statický stav (`config`, `language`, `hunts`, `manager`, `spawnRates`, `broadcaster`, `storage`, `stats`, `server`) a životní cyklus `load/start/reload/stop/check`, pauza při chybném configu, `onPlayerJoin`, `onDataPackReload`. |
| `event.CaptureEvent` | Listener `POKEMON_CAPTURED` v `try/catch (Throwable)`, adaptéry pro `CompletionService`. |
| `event.CompletionService` | Výplatní tok bez MC importů: claim → uložení `hunts.json` → PENDING → výplata → příkazy (jen při úspěchu nebo nulové ceně) → stats → DONE. |
| `event.PayoutJournal` | `payouts.log`: `pending` (synchronně, fsync), `done`, `scan()` při startu (nedokončené výplaty, splněné `huntId`). |
| `hunts.CurrentHunts`, `SingleHunt`, `HuntManager` | Sada huntů (globální nebo hráčova), jeden hunt (plný Cobblemon `Pokemon` + cena + příkazy + `endtime`), mapa hráčů pro individuální režim. |
| `hunts.SpawnRates`, `TargetChooser`, `HuntRules`, `HuntTarget`, `Bucket` | Katalog cílů z world spawn poolu, výběr cíle bez obsazených a právě splněného, pravidla vyřazení a shiny. |
| `hunts.HuntStorage` | `hunts.json`: dirty flag, nejvýš jednou za 30 s, po splnění hned. |
| `stats.HuntStats`, `PlayerStats`, `StatsLookup` | Statistiky, žebříček, dohledání podle UUID a jména (přesná shoda, nejednoznačnost). |
| `broadcast.Broadcaster`, `BroadcastQueue` | Zprávy a broadcasty, slučování „začal/skončil“ v okně bufferu. |
| `config.Config`, `ConfigValidator`, `Lang`, … | Gson POJO, `parse` s výjimkou `ConfigLoadException`, validace (chyby a varování). |
| `economy.*`, `api.economy.*` | Impactor přímo, Pebbles a Blanket reflexí, API pro externí providery. |
| `api.event.*` | Event `HuntEvents.COMPLETED` pro jiné módy, každý listener chráněný. |
| `command.*`, `util.BaseCommand`, `Subcommand`, `CommandsRegistry` | Brigadier příkazy, GUI, `AdminCommand`, `PendingConfirmations` (potvrzení do 30 s). |
| `util.Placeholders`, `PlayerNames` | Literální substituce `{player}`, `{uuid}`, `{pokemon}`, `{species_id}`, `{form}`, `{price}` (`Locale.ROOT`), blokování příkazů s nebezpečným jménem. |
| `util.Permissions`, `HuntPermission` | Uzly `hunt.*` bez prefixu `cobblemon.`, `requires` nad `CommandSourceStack`. |
| `util.FileStore` | Čtení (`Missing`/`Ok`/`Failed`), atomický zápis s rotací `.bak.1–3`, append s fsync, jeden writer thread. |

### 3.4 Eventy (`fabric/.../HuntFabric.java`)
| Event | Volá |
|---|---|
| `SERVER_STARTED` | `Hunt.load()`: config, lang, stats; při platném configu `start()` (sken `payouts.log`, obnova huntů, `fillHunts`), jinak pauza. |
| `SERVER_STOPPING` | `Hunt.stop()`: synchronní uložení `hunts.json` a `stats.json`. |
| `END_DATA_PACK_RELOAD` | `Hunt.onDataPackReload`: nový katalog cílů, doplnění huntů. |
| `ServerWorldEvents.LOAD` | `Hunt.server = server` |
| `ServerPlayConnectionEvents.JOIN` | `Hunt.onPlayerJoin` (jen individuální režim). |
| `END_SERVER_TICK` (každý 20. tick) | `Hunt.check()`: expirace, flush broadcastů, uložení každých 30 s, vše v `try/catch`. |
| `CommandRegistrationCallback` | `/hunt` a alias `/hunts`. |
| Cobblemon `POKEMON_CAPTURED` (NORMAL) | `CaptureEvent` → `CompletionService`. |

### 3.5 Ověřené chování Cobblemonu a MC
- `POKEMON_CAPTURED` se postuje **jen** z hozeného Pokéballu po `party.add`, a jen když `pokemon.isWild() && isAlive && !captureFuture.isDone`. Trade, GTS, daycare, vejce, `/pokegive` ani release ho nespouští a dvojí post nehrozí (`EmptyPokeBallEntity`, 1.8.0 i 1.8.4).
- Handler běží uvnitř ticku entity Pokéballu. `SimpleObservable.emit` výjimky nechytá a `Level.guardEntityTick` z nich udělá `ReportedException`, tedy pád serveru. Proto `try/catch (Throwable)` na hranici.
- Všechna herní logika běží na server threadu, matching i odebrání huntu proběhne v jednom handleru. Dva hráči nemohou splnit stejný globální hunt dvakrát.
- `forcedAspects` → `updateAspects()` → `updateForm()`, regionální formy se nastaví správně. `rollAbility()` nevybírá skrytou abilitu.
- `WORLD_SPAWN_POOL` je `reloadable = true` (vanilla `/reload` ho obnoví před `END_DATA_PACK_RELOAD`). `PokemonSpecies` je `reloadable = false`, objekty `Species` v huntech nezastarají. Výchozí world pool 1.8.4 nemá žádný druh s labelem `legendary`, `mythical`, `ultra_beast` ani `paradox`.
- `FabricPermissionValidator.hasPermission(CommandSourceStack, …)` → `Permissions.check(source, node, level)`. U zdroje bez hráče platí OP level zdroje, `execute as <mob>` ho nezvýší.

---

## 4. Build a testy (stav 1.3.10)

- Minecraft 1.21.1, Fabric Loader 0.20.1, Fabric API 0.116.17+1.21.1, Cobblemon 1.8.4+1.21.1 (compile), Impactor API 5.3.0, GooeyLibs 3.1.0-1.21.1-SNAPSHOT (jar-in-jar), Java 21, Gradle 8.14.2. Loom 1.10.455, architectury-plugin 3.4.164, `com.gradleup.shadow` 8.3.11.
- `./gradlew build` (nebo `bash ./gradlew build`) spouští testy a při selhání spadne. `common`: **301 JUnit 5 testů** (placeholdery, jména, rarita, parsování a validace configu a langu, `FileStore`, `CompletionService`, `PayoutJournal`, oprávnění příkazů, dohledání a reset stats, broadcast fronta, výběr cíle, shiny a labely). `fabric`: `NO-SOURCE`.
- Každý krok prošel mutační kontrolou (dočasná chyba v kódu → testy musí spadnout).
- `--warning-mode all` i `-Xlint:all` bez varování. Sources jar je úplný (`modCompileOnly` Cobblemonu ve `fabric/build.gradle`, opraveno v 1.3.7).
- Zbývá: GooeyLibs je SNAPSHOT (jediný release `3.1.1-1.21.x` není ověřený na 1.21.1 a obsahuje mixiny), Kotlin plugin se aplikuje bez Kotlin kódu.

---

## 5. Otevřené nálezy

### 5.1 HUNT-010 · Hunt se splní jakýmkoli divokým Pokémonem bez ohledu na původ · A · Nízká (původně Střední) · [ověřeno: matching; podezření: zdroje]
- **Kde:** `CaptureEvent`, `SpawnRates` (rarita a cena jen z `worldBuckets`).
- **Popis:** Nezáleží na tom, odkud divoký Pokémon pochází: PokeSnack (bucket váhy `rare 4.125 / ultra-rare 1.375` proti world `0.5 / 0.2`), rybaření, admin `/spawnpokemon`. Cena „ultra-rare“ se ale počítá ze vzácnosti ve světě, takže jde opakovaně farmit dražší cíle.
- **Stav:** podle provozovatele spawnuje divoké Pokémony jen Cobblemon, cizí módy odpadají. Pokud se farmení přes PokeSnack ukáže v `payouts.log`, řešit raritou ze všech bucket map nebo filtrem původu (spawn kontext Cobblemonu je potřeba ověřit). **M**.

### 5.2 HUNT-011 · Blokující `join()` ekonomiky na hlavním vlákně · B/D · Nízká (původně Střední) · [podezření]
- **Kde:** `ImpactorService` (`service.account(currency, uuid).join()`), `HuntEconomyProvider` (nová instance služby při každé výplatě, u Pebbles a Blanket reflexe).
- **Stav:** Impactor používá JSON, `join()` čeká nejvýš na čtení malého lokálního souboru. Cache služby provozovatel v kroku 8 odmítl jako zanedbatelnou. **Přehodnotit při přechodu Impactoru na SQL:** předehřát účet při joinu, platit asynchronně a výsledek vrátit přes `server.execute`, cachovat instanci. **S–M**.

### 5.3 HUNT-013 (zbytek) · Admin nástroje pro offline hráče · C/E
- Reset stats, `resetall` a `save` jsou hotové (1.3.8). Chybí `/hunt admin refresh <hráč|uuid>` a `/hunt admin view <hráč>` pro offline hráče. Týkají se jen individuálního režimu, odloženo s HUNT-005/006.
- Ruční editace `stats.json`/`hunts.json` za běhu se dál přepíše daty z paměti (README to uvádí).

### 5.4 HUNT-015 (zbytek) · Netestované jádro a statický stav · F
- Testy pokrývají substituci, config, IO, výplatní tok, oprávnění, stats a broadcasty. **Matching (`SingleHunt.matches`) a generování huntů (`CurrentHunts`) testy nemají**, protože pracují s Cobblemon `Pokemon` a statickým `Hunt.*`. Riziko je nízké, dokud se tato logika nemění. Při změně matchingu nebo zavedení nových typů huntů nejdřív vyčlenit lehký model (5.5).

### 5.5 Latentní: jen při `individualHunts=true`
Kód dnes tyto cesty nepoužívá. **Před zapnutím režimu je nutné je opravit**, jinak se HUNT-005 a 006 vrací na Vysokou.

#### HUNT-005 · Offline mode = neomezené alt účty na farmení · A · [ověřeno v kódu]
- **Kde:** `HuntManager.addPlayer`, `CaptureEvent`, `ImpactorService` (účet pro nové UUID vznikne automaticky).
- **Popis:** Každé UUID má vlastních až 28 huntů a vlastní výplaty. V offline mode stojí nový účet jen nové jméno. Mód nemá strop výplat na hráče nebo IP, cooldown ani podmínku odehraného času. Hráč s 5 alty vydělá zhruba 6× víc a peníze pošle `/pay` na hlavní účet.
- **Oprava:** policy vrstva výplat: denní strop na UUID (volitelně na IP), minimální odehraný čas (`Stats.PLAY_TIME`), vše v configu. Detekce přes `payouts.log`. **M**.

#### HUNT-006 · Neomezený růst dat a paměti, serializace na hlavním vlákně · B/D · [ověřeno v kódu; velikost je odhad]
- **Kde:** `HuntManager` (`playerHunts` se nikdy nečistí), `SingleHunt` (drží plný `Pokemon`, ~77 property, odhad 2–5 KB), `HuntStorage.serialize()` (všechny hunty všech hráčů na hlavním vlákně), načtení (`new Pokemon()` pro každý uložený hunt).
- **Scénář (odhad):** 2 000 UUID × 7 huntů = 14 000 objektů `Pokemon` ≈ 30–70 MB heapu. Každých 30 s serializace ~14 000 huntů na hlavním vlákně (odhad 10–30 ms lag spike), pomalý start. V globálním režimu je to 7–28 huntů, zanedbatelné.
- **Oprava:** lehký `record HuntSpec(speciesId, form, ability, gender, nature, shiny)`, `Pokemon` jen pro GUI. Hunty offline hráčů lazy load při joinu nebo prune po X dnech. Na hlavním vlákně jen snapshot immutable recordů, JSON na writer threadu. **M–L**.
- **Ověření:** `jcmd <pid> GC.class_histogram | grep 'cobblemon.mod.common.pokemon.Pokemon '`, velikost `hunts.json`, `/spark profiler --thread "Server thread"` během uložení.

#### HUNT-019 · Individuální režim ignoruje flag `broadcast` · A · [ověřeno]
- **Kde:** `CurrentHunts.removeHunt` posílá `sendPlayerMessage(ENDED)` bez ohledu na parametr `broadcast`. Hráč dostane „hunt skončil“ i po vlastním splnění a po `/hunt refresh`. **Oprava:** respektovat flag. **S**.

#### HUNT-025 · Zbytečná práce a malý leak v individuálním režimu · B/D · [ověřeno]
- **Kde:** `Broadcaster.playerBuffers` roste do reloadu (1 záznam na hráče). `HuntManager.checkOnlinePlayers` každou sekundu kopíruje množinu všech známých UUID a hledá je mezi online hráči.
- **Oprava:** iterovat `getPlayerList().getPlayers()`, buffery čistit při disconnectu. **S**.

#### Cílová architektura pro případný refaktor
```
org.pokesplash.hunt
├─ core/        čistá Java bez MC a Cobblemon importů (unit testy)
│  ├─ HuntSpec, HuntInstance, Reward, CaughtPokemon   recordy
│  ├─ HuntBoard      kolekce jednoho scope; generate/expire/claim(id)
│  ├─ HuntGenerator  (TargetCatalog, RandomSource, Clock, HuntConfig)
│  ├─ Matcher        (HuntSpec, MatchRules, CaughtPokemon) → boolean
│  └─ PayoutPolicy   denní strop / min. playtime / per-IP (HUNT-005)
├─ app/         CompletionService, PayoutJournal (existují), HuntRegistry (global + online boards, prune, HUNT-006)
├─ platform/    CobblemonCaptureListener, CobblemonTargetCatalog (SpawnRates), EconomyGateway, gui/, command/
└─ persistence/ FileStore (existuje), HuntRepository (v1 read, v2 write), StatsRepository
```
Testy navíc: matching (formy, pohlaví u genderless, nature, abilita, shiny), přepínání global ↔ individual, prune neaktivních, strop výplat a alt scénář.

---

## 6. Opravené nálezy

| ID | Původně | Problém | Oprava | Verze |
|---|---|---|---|---|
| HUNT-001 | Kritická | Jméno hráče (`@a`) v reward příkazech mění cíl příkazu, příkazy běží s OP 4. | Příkaz s `{player}` se pro jméno mimo `[A-Za-z0-9_]{1,16}` přeskočí s WARN. Nový `{uuid}`. | 1.3.2 |
| HUNT-002 | Kritická | `$` nebo `\` ve jméně → výjimka z `replaceAll` → pád serveru (i v broadcastu). | Literální `String.replace` všude (`Placeholders`). | 1.3.2 |
| HUNT-003 | Kritická | Výjimka v capture nebo tick handleru (chybný config, cizí listener) shodí server. | `try/catch (Throwable)` kolem handleru, `Hunt.check()`, joinu a každého `HuntEvents` listeneru. | 1.3.2 |
| HUNT-004 | Vysoká | Chyba v `config.json` vede k přepisu výchozími odměnami, zápis nebyl atomický. | Chybný config nikdy nepřepisovat: start → pauza, reload → starý config. Atomické zápisy přes `FileStore`. | 1.3.3 |
| HUNT-007 | Vysoká | Výplata není transakční: po pádu jde hunt splnit znovu, selhání ekonomiky přesto spustí příkazy a není vidět. | `CompletionService` a žurnál PENDING/DONE, `hunts.json` hned po claimu, příkazy jen při úspěšné platbě, `payFailedMessage`. | 1.3.4 |
| HUNT-008 | Střední | Chybí validace configu (`huntDuration 0`, `huntAmount 0`, váhy, `economy`, NaN ceny, neznámé druhy). | `ConfigValidator`: chyby a varování, všechny problémy najednou, žlutě při reloadu. | 1.3.7 |
| HUNT-009 | Střední | Oprávnění se obchází přes `execute as <mob>` (`isPlayer() ? check : true`). | `requires` nad `CommandSourceStack`. | 1.3.5 |
| HUNT-012 | Střední | IO chyba při čtení dat = „neexistuje“ → přepsání prázdnými daty. | `Missing`/`Failed` v `FileStore.read`, nečitelný soubor se v běhu neukládá, rotace `.bak.1–3`. | 1.3.3 |
| HUNT-014 | Střední | Výplaty se nelogují. | `payouts.log` (JSON Lines) a INFO v `latest.log`, od 1.3.4 fáze PENDING/DONE. | 1.3.2 |
| HUNT-016 | Střední | Nesoulad verze `lang.json` zapíše výchozí texty. | Migrace z načteného obsahu. | 1.3.3 |
| HUNT-017 | Nízká | `/hunt` nemá `requires`, alias ano, README nesedí. | `hunt.base` řídí jen GUI, alias je čistý `redirect`, README opraveno. | 1.3.5 |
| HUNT-018 | Nízká | Broadcasty v okně bufferu se zahazují (oznámí se 1 ze 7 huntů). | `BroadcastQueue` slučuje jména do stávajícího textu, `CAPTURED` jde hned. | 1.3.10 |
| HUNT-020 | Nízká | `{pokemon}` v příkazech obsahuje mezery a titul marku. | Nové `{species_id}` a `{form}`. | 1.3.10 |
| HUNT-021 | Nízká | `matchProperties.shiny=true` znamenalo „shiny se nepočítá“. | `true` = splní jen shiny úlovek, GUI ukazuje shiny (rozhodnutí provozovatele). | 1.3.10 |
| HUNT-022 | Nízká | Vyřazuje se jen `legendary`. | `excludedLabels` (výchozí legendary, mythical, ultra_beast, paradox). | 1.3.10 |
| HUNT-023 | Nízká | Kompilace proti 1.8.0, `cobblemon` bez horní hranice, chybí `fabric-api`, `environment: "*"`, SNAPSHOT závislosti, Gradle deprecations, `gradlew` bez +x. | `>=1.8.4 <1.9.0-`, `fabric-api`, `environment: server`, pinnuté pluginy, Impactor 5.3.0. **GooeyLibs zůstává SNAPSHOT.** | 1.3.7, 1.3.9 |
| HUNT-024 | Nízká | Mrtvý kód (`DebugCommand`, `Settings.java`, `old/`, pseudo-async IO v `Utils`) a netestovaný NeoForge modul. | Smazáno, IO jen přes `FileStore`. | 1.3.3, 1.3.9 |
| HUNT-026 | Nízká | Splněný druh se může hned znovu vylosovat. | `TargetChooser` vyloučí splněný i vypršelý cíl, pokud zbývá jiný. | 1.3.10 |
| HUNT-027 | Nízká | GUI ukazuje hráči text výjimky. | Obecná hláška, detail do logu. Ruční test vytažení itemů prošel. Čas v GUI zůstává statický. | 1.3.10 |
| HUNT-028 | Nízká | `/hunt stats <jméno>` vrací první shodu bez ohledu na velikost písmen. | `StatsLookup`: UUID, přesná shoda, seznam nejednoznačných shod. | 1.3.8 |
| HUNT-029 | Nízká | Vanilla `/reload` neobnoví katalog cílů. | Hook `END_DATA_PACK_RELOAD`. | 1.3.10 |
| HUNT-030 | Nízká | `{price}` podle locale JVM (`1,5`). | `Locale.ROOT` všude (příkazy, chat, GUI). | 1.3.7 |
| HUNT-031 | Nízká | Chybějící sekce configu → potichu výchozí odměny. | Chybějící `economy`/`rarity`/`rewards`/`customPrices` je chyba. | 1.3.7 |
| HUNT-032 | Střední | Config bez `version` přepne ekonomiku na Impactor a měnu na výchozí. | Migrace jen pro skutečný formát 1.2.x (od 1.3.9 se 1.2.x odmítá). | 1.3.7 |
| HUNT-033 | Nízká | Uzly měly prefix `cobblemon.` (README uvádělo `hunt.*`). | `HuntPermission` předává uzel beze změny, uzly jsou `hunt.*`. | 1.3.6 |

---

## 7. Historie verzí

Všechny verze otestoval provozovatel na testovacím serveru a nasadil na produkci. Datové formáty (`hunts.json` v1, `stats.json` v1, `config.json`, `lang.json`) zůstávají kompatibilní, návrat na předchozí verzi = výměna jaru.

- **1.3.2 (krok 0 a 1):** literální substituce, blokování příkazů s nebezpečným jménem, `{uuid}`, ochrana handlerů (HUNT-001, 002, 003). `payouts.log` (HUNT-014). Vyšlo na produkci jako součást 1.3.3.
- **1.3.3 (krok 2), nasazeno 2026-11-02:** config se nikdy nepřepíše (HUNT-004, 012, 016). Rozhodnutí: chybný config na startu = **pauza** (žádné hunty ani odměny, `/hunt stats` funguje), rotace `.bak.1–3` jednou za běh (`.bak.1` = stav při posledním startu).
- **1.3.4 (kroky 3 a 4), nasazeno 2026-11-02:** JUnit 5 v `common`, `Placeholders` vyčleněné z `Utils`. `CompletionService` a žurnál (HUNT-007). Rozhodnutí: nedokončené výplaty se při startu **jen nahlásí** (nevyplácí se automaticky), při selhání ekonomiky se příkazy přeskočí, hunt s cenou 0 příkazy spouští. 138 testů.
- **1.3.5 (krok 5), nasazeno 2026-11-02:** oprávnění nad zdrojem příkazu (HUNT-009, 017). Rozhodnutí: `hunt.base` řídí jen GUI. 161 testů.
- **1.3.6, nasazeno 2026-11-02:** uzly `hunt.*` bez prefixu (HUNT-033). V LuckPerms nebyly nastavené žádné uzly Hunt.
- **1.3.7 (krok 6), nasazeno 2026-11-02:** `ConfigValidator` (HUNT-008, 030, 031, 032). Rozhodnutí: neznámý druh nebo forma = varování, chybějící sekce = chyba, `{price}` vždy s tečkou. Bump na Cobblemon 1.8.4, Loader 0.20.1, Fabric API 0.116.17, oprava sources jaru a Gradle syntaxe. 226 testů.
- **1.3.8 (krok 7), nasazeno 2026-11-02:** `/hunt admin save`, `stats reset`, `stats resetall`, `/hunt stats <uuid>` (HUNT-013, 028). Rozhodnutí: reset jednoho i všech, vždy s potvrzením do 30 s, před `resetall` snapshot `stats-reset-<čas>.json`. 271 testů.
- **1.3.9 (krok 8), nasazeno 2026-11-02:** smazané `old/` včetně migrace configu 1.2.x, `DebugCommand`, `Settings.java` a modul `forge/` (HUNT-023, 024). Metadata `fabric.mod.json`, pinnuté pluginy, shadow 8.3.11. Rozhodnutí: cache ekonomiky (HUNT-011) vynechat. 270 testů.
- **1.3.10 (krok 9), nasazeno 2026-11-03:** slučované broadcasty, `{species_id}` a `{form}`, `shiny=true` = jen shiny, `excludedLabels`, vyloučení splněného cíle, obecná hláška v GUI, hook na `/reload` (HUNT-018, 020, 021, 022, 026, 027, 029). Rozhodnutí: jména se slučují do stávajícího textu z `lang.json`. 301 testů.

---

## 8. Provozní poznámky a trvalá rizika

- **`matchProperties.shiny: true`** znamená, že hunty splní jen shiny úlovek (~1/8192). Před změnou configu na to pamatovat.
- **`payouts.log`** se při každém startu čte celý (100k splnění ≈ 40–80 MB, řádově 1–2 s). Při velkém logu ho archivovat, ale až po vyřešení všech nedokončených výplat (ERROR `Unfinished hunt payouts`).
- PENDING řádek se zapisuje synchronně s fsync na hlavním vlákně (jednotky ms při každém splnění). `admin save` a uložení po resetu jsou také synchronní.
- Reset stats nejde vrátit ve hře, jen ručně při vypnutém serveru (WARN s JSON nebo snapshot). Snapshoty `stats-reset-*.json` se samy nemažou.
- Config z Hunt 1.2.x se nenačte (pauza s hláškou). Cobblemon mimo `>=1.8.4 <1.9.0-` a chybějící Fabric API Fabric odmítne při startu. `environment: server`: Hunt se nenačte v singleplayeru ani na LAN.
- Hráč s `minecraft.command.execute` může přes `execute as <admin>` použít oprávnění admina. Jde o vlastnost fabric-permissions-api, opatření je nedávat běžným hráčům `minecraft.command.execute`.
- `{earned}` v `/hunt stats` se formátuje podle locale JVM (jen zobrazení).
- GooeyLibs je SNAPSHOT: pokud ho repo smaže, build přestane jít. Řešení je přechod na release po ověření na 1.21.1.
- Broadcasty „začal/skončil“ dorazí se zpožděním až `bufferDuration` + 1 s, `CAPTURED` před „začal“ náhradního huntu.

**Regresní test na testovacím serveru** (před každým scénářem zálohovat `config/hunt/`, `matchProperties` false, hunt Pokémona spawnovat přes `/spawnpokemon`):
- Běžné chycení: broadcast, peníze, příkazy, stats, v `payouts.log` PENDING a DONE `PAID` se stejným `completionId`, `hunts.json` hned s náhradním huntem.
- Hráč `Test$1`: server nespadne, příkaz s `{player}` má `SKIPPED_UNSAFE_NAME`, příkaz s `{uuid}` `RAN`.
- Rozbitý `config.json` na startu → `Hunt is PAUSED`, soubor beze změny (`sha256sum`). Oprava + `/hunt reload` → hunty běží. Rozbitý config při reloadu → platí starý.
- `/hunt admin save`, `/hunt top`, `/hunt stats <uuid>`, vanilla `/reload` (`hunt targets updated`).
- LuckPerms: `hunt.refresh` povolí `/hunt refresh` hráči bez OP. `execute as @e[type=cow,limit=1] run hunt refresh` od hráče bez OP neprojde.
- Po startu: `grep -E "Hunt.*(PAUSED|ERROR|Unfinished)" logs/latest.log` je prázdné.
