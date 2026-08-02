# Portal Launcher — Production Readiness Plan

Written 2026-08-02. Supersedes the "Next steps" section of [plan.md](plan.md) (which stays as the product spec).

**Status: phases 0, 1 and 2 are done.** See §8 and §9 for what actually landed and
what the work changed about the plan. Phases 3–9 below are still to do.

---

## 0. Verified baseline

Everything below was checked against the actual tree, not inferred from the old plan.

| Thing | State |
|---|---|
| Build | `:app:assembleDebug` **succeeds** (2m33s cold) after fixing `local.properties` |
| Source size | 1,648 lines of Kotlin across 27 files |
| Biggest file | `ui/launcher/LauncherHomeScreen.kt` — 523 lines (does UI **and** package querying **and** bitmap rendering) |
| Tests | Zero real tests. Only the two Android Studio templates (`ExampleUnitTest`, `ExampleInstrumentedTest`) |
| CI | None. No `.github/` anywhere in the monorepo |
| Release build | Not shippable: `isMinifyEnabled = false`, no signing config, no AAB target |
| DB | Room v1, `exportSchema = false`, `fallbackToDestructiveMigration()` → **every schema change wipes user profiles** |
| Automations | 0 of 6 implemented. Config data classes exist; only `AppVisibilityConfig` can even be serialized |
| Localization | 1 string in `strings.xml` (`app_name`). Every other string is hardcoded in Compose |
| Theme | Compose theme is fine; the XML `themes.xml` is still the untouched `Theme.MaterialComponents.DayNight.DarkActionBar` template with purple/teal defaults |

**Two environment problems, fix first (Phase 0):**

1. `local.properties` pointed at `C:\Users\tan\AppData\Local\Android\Sdk`, which does not exist. Real SDK is `D:\sdk\android` (also in `ANDROID_HOME`). **I already corrected this file** — it's gitignored, so this is a per-machine fix.
2. Git refuses to operate on this repo: it is owned by SID `...-1000` (Windows user `tan`) while you are `tanvo` (`...-1001`). Every git command fails with `detected dubious ownership`. Until you run the command below you cannot commit, branch, or diff:

```bash
git config --global --add safe.directory D:/production/portal/pocket_portal/apps/portal_launcher
```

---

## 1. Three decisions that change the plan

Do not start Phase 3+ until these are settled. Each has a recommendation.

### 1.1 Device-wide greyscale is not implementable on stock Android

`plan.md` lists greyscale as the flagship Study-profile automation. On a non-rooted device, a third-party app **cannot** desaturate the whole screen:

- The system daltonizer (`Settings.Secure.accessibility_display_daltonizer_enabled` / `accessibility_display_daltonizer`) needs `WRITE_SECURE_SETTINGS`, which is `signature|privileged`. Not grantable to normal apps.
- A `SYSTEM_ALERT_WINDOW` overlay composites *on top of* other windows. It can tint, it cannot apply a colour matrix to the pixels underneath. There is no public API for that.
- `AccessibilityService` has no colour-transform capability.

**Recommendation — ship all three tiers, in this order:**

| Tier | Mechanism | Effort | Works for |
|---|---|---|---|
| A | Desaturate **the launcher's own UI** (Compose `ColorFilter` / greyscale `ColorScheme`) | hours | Everyone. Real perceived value — the home screen is the trigger surface |
| B | Deep-link the user to Developer Options → *Simulate colour space → Monochromacy*, with a one-tap "how to" card | hours | Everyone willing to tap 3 times |
| C | One-time ADB grant (`adb shell pm grant com.tanvoid0.portallauncher android.permission.WRITE_SECURE_SETTINGS`) or **Shizuku**, then toggle the daltonizer programmatically | 1–2 days | Power users. Optional module, gracefully absent |

Do **not** promise "device greyscale" in the Play listing unless C is present and the user has completed the grant.

### 1.2 App blocker mechanism decides your Play Store risk

Two ways to know which app came to the foreground:

- **`PACKAGE_USAGE_STATS`** (usage access) + `UsageStatsManager.queryEvents` polled from a foreground service. No accessibility disclosure needed. Detection latency ~1s, and the poll costs battery.
- **`AccessibilityService`** listening for `TYPE_WINDOW_STATE_CHANGED`. Instant, cheap. But Play's Accessibility API policy requires a prominent in-app disclosure + a Play Console declaration, and using it for non-accessibility purposes is a common rejection/suspension cause. Digital-wellbeing blockers are an accepted category, but you must justify it in the form.

**Recommendation:** ship the blocker on **usage access** first. Add AccessibilityService later as an opt-in "instant blocking" upgrade, only if latency actually annoys users. One rejected release costs more than 1s of latency.

### 1.3 Scope: how much launcher do you actually build?

The two apps you referenced sit at opposite ends:

- **Olauncher** — deliberately tiny: text-only app list, ~6 home items, swipe gestures, hidden/renamed apps, double-tap to lock, daily wallpaper. No icons, no widgets, no folders. Ships as a few hundred KB.
- **Smart Launcher 6** — full replacement: auto-categorised app drawer, adaptive icon grid, smart search, ambient theme from wallpaper, widgets, folders, gestures, icon packs, app vault.

Your current code is closer to a Pixel Launcher clone (icon grid, dock, search pill, wallpaper) but the *differentiator* in `plan.md` is the profile engine — which no other launcher does well.

**Recommendation:** **Olauncher's surface area, Smart Launcher's categorisation, your profile engine as the hook.** Concretely — cut widgets, folders, icon packs, and multi-page home from v1.0. Those are each multi-week features that every competitor already does better, and none of them make your profile idea land. Ship: one home page, a categorised drawer, search, gestures, profiles + automations. That is a defensible v1.0.

> **Decided 2026-08-02: this option.** Widgets, folders, icon packs and
> multi-page home are out of v1.0 — see §5. The phase estimates in §7 assume it.
>
> **Reopened 2026-08-02, same day:** widgets and multi-page home are back in —
> see §12. Folders and icon packs stay out. The two that came back were taken
> together because they share one data model; splitting them would have migrated
> the home layout schema twice.

---

## 2. Defects in the code as it stands

Fix these inside the phases noted. All verified by reading the file.

| # | Location | Problem | Phase |
|---|---|---|---|
| D1 | `AdaptiveLauncherScaffold.kt:51` | `Scaffold(bottomBar = …) { content() }` discards the `PaddingValues`. Content draws **behind** the bottom nav bar | 1 |
| D2 | `AndroidManifest.xml` | No `<queries>` element. On API 30+ `queryIntentActivities` is filtered by package visibility — you will silently see fewer apps | 1 |
| D3 | `LauncherHomeScreen.kt:478` | `PackageManager.queryIntentActivities` instead of `LauncherApps.getActivityList()`. Misses work-profile apps and activity aliases; no install/uninstall/update callbacks, so the list goes stale until process death | 1 |
| D4 | `LauncherHomeScreen.kt:474-491` | Loads **every** app icon eagerly at 96×96 ARGB_8888 on first composition. ~150 apps ≈ 5.5 MB of bitmaps, all held in a `MutableStateFlow` inside the ViewModel across config changes | 3 |
| D5 | `LauncherHomeScreen.kt:495-515` | Renders the wallpaper into your own bitmap. Breaks live wallpapers, no parallax, wastes memory, and `WallpaperManager.getDrawable()` is increasingly restricted. Delete ~25 lines; set `android:windowShowWallpaper=true` + transparent `windowBackground` instead | 1 |
| D6 | `MainActivity.kt` | No `onNewIntent` override. As default HOME with `launchMode="singleTask"`, pressing the home button while on Settings does nothing — the user is stuck off-home | 1 |
| D7 | `MainActivity.kt` | No `enableEdgeToEdge()`, no `android:enableOnBackInvokedCallback`. targetSdk 36 means edge-to-edge is **mandatory and not opt-out-able** — the dock will sit under the navigation bar | 1 |
| D8 | `AppDatabase.kt:11,24` | `exportSchema = false` + `fallbackToDestructiveMigration()`. Users lose all profiles on any schema bump | 2 |
| D9 | `ConfigJson.kt` | Hand-rolled `"primary:a,b;secondary:c"` format, and only for `AppVisibilityConfig`. Greyscale/Blocker/NotificationFilter/Scheduler configs **cannot be persisted at all**. Breaks if any id contains `,` or `;` | 2 |
| D10 | `PortalLauncherApplication.kt:24` | Seeds the default profile in a detached `CoroutineScope(Dispatchers.IO)`. UI can read before the seed lands → active profile null → filter silently bypassed. Also opens Room on the app-start path | 2 |
| D11 | `PreferencesRepository.kt:18` | `defaultProfileId` is actually the **active** profile id (mutated by tapping a chip). Two different concepts under one name | 2 |
| D12 | `LauncherHomeScreen.kt:136` | "Pinned apps" = `apps.take(8)` — the first 8 alphabetically. No pinning, no persistence | 3 |
| D13 | `LauncherHomeScreen.kt:161-190` | The search pill is a `readOnly` `OutlinedTextField` with no click handler. Screen readers announce an editable text field that does nothing. Search is entirely unimplemented | 4 |
| D14 | `AndroidManifest.xml` | `SYSTEM_ALERT_WINDOW` and `FOREGROUND_SERVICE_SPECIAL_USE` declared but nothing uses them. `SPECIAL_USE` also needs a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property + Play justification. Declared-unused permissions add review friction | 6 / 9 |
| D15 | `LauncherViewModel.kt:35` | `flatMapLatest` used without `@OptIn(ExperimentalCoroutinesApi::class)` — compiler warning today, breakage on a future Kotlin bump | 1 |
| D16 | `libs.versions.toml` | Compose BOM `2024.06.00` against `activity-compose 1.12.4` / `navigation 2.9.7` / `lifecycle 2.8.6`. ~18 months of version skew | 0 |
| D17 | all Compose files | Every user-visible string hardcoded. `supportsRtl="true"` is a claim you can't currently honour | 7 |
| D18 | `res/values/themes.xml` | Still the template `Theme.MaterialComponents.DayNight.DarkActionBar` with purple/teal. A launcher wants no action bar and a transparent window | 1 |

---

## 3. Phases

Effort is solo-dev working days. Each phase ends with a green build and a commit.

### Phase 0 — Make the repo governable (0.5 day)

1. `git config --global --add safe.directory …` (see §0).
2. Commit the current tree so you have a rollback point.
3. Bump Compose BOM to current stable (check the [BOM→library mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)); align Kotlin/KSP to a current pair. **D16**
4. Add to `app/build.gradle.kts`:
   ```kotlin
   buildTypes {
       debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
       release { isMinifyEnabled = true; isShrinkResources = true; /* signing in Phase 9 */ }
   }
   lint { warningsAsErrors = true; abortOnError = true; baseline = file("lint-baseline.xml") }
   ```
   Generate the baseline once so existing warnings don't block you, then never let it grow.
5. Fix the two compiler warnings (**D15**, and the deprecated `fallbackToDestructiveMigration` — removed properly in Phase 2).
6. `.gitignore`: it currently ignores `.cursor`, which is why `.cursor/plans/*.plan.md` isn't tracked. Decide if you want those in history.

**Done when:** `./gradlew build lint` is green from a clean clone on your machine, and `git status` works.

### Phase 1 — Behave like a launcher (2–3 days)

This is the phase that makes the app usable as an actual home screen. Everything here is a correctness fix, not a feature.

1. **Package visibility** — add to the manifest, above `<application>`: **D2**
   ```xml
   <queries>
       <intent>
           <action android:name="android.intent.action.MAIN" />
           <category android:name="android.intent.category.LAUNCHER" />
       </intent>
   </queries>
   ```
   This is enough to enumerate launchable apps and needs **no** `QUERY_ALL_PACKAGES`. Only add `QUERY_ALL_PACKAGES` if you must see non-launchable packages — it triggers a Play declaration form and is a frequent rejection source.
2. **Switch to `LauncherApps`** — new `data/AppRepository.kt`: `LauncherApps.getActivityList(null, user)` for each `UserHandle` from `UserManager.getUserProfiles()` (this is how you get work-profile apps for free), plus `LauncherApps.registerCallback()` so install/remove/update push a new list. Expose `Flow<List<LauncherAppInfo>>`. Move `loadLaunchableApps` out of the screen file. **D3**
3. **Wallpaper via the window, not a bitmap** — delete `loadWallpaperBitmap` and the `Image` that draws it. Replace `res/values/themes.xml`: **D5, D18**
   ```xml
   <style name="Theme.PortalLauncher" parent="Theme.Material3.DayNight.NoActionBar">
       <item name="android:windowShowWallpaper">true</item>
       <item name="android:windowBackground">@android:color/transparent</item>
       <item name="android:windowTranslucentStatus">false</item>
   </style>
   ```
   Net: ~30 fewer lines, live wallpapers and parallax start working.
4. **Edge-to-edge + predictive back** — `enableEdgeToEdge()` in `MainActivity.onCreate`, `android:enableOnBackInvokedCallback="true"` on `<application>`, and audit every screen for `WindowInsets` handling (the dock especially needs `navigationBars` padding). **D7**
5. **Fix the scaffold padding bug** — thread `PaddingValues` into `content` in the Compact branch. **D1**
6. **Handle re-entry as HOME** — `onNewIntent` in `MainActivity`: if the intent has `CATEGORY_HOME`, pop the nav stack to `LAUNCHER_HOME` and close the drawer sheet. **D6**
7. **"Set as default launcher" flow** — `OnboardingScreen` needs this or the app is unreachable:
   - API 29+: `RoleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)`
   - API 26–28: `Intent(Settings.ACTION_HOME_SETTINGS)` + instructions
   Detect current state by resolving `CATEGORY_HOME` and comparing the package.

**Done when:** you can set it as the default home on a real device, press home from anywhere and land on the home screen, the wallpaper (including a live one) shows through, nothing draws under the system bars, and installing an app makes it appear without restarting the launcher.

### Phase 2 — A data layer that survives an update (1–2 days)

1. **`kotlinx.serialization`** — add the plugin (version = your Kotlin version). Make every config a `@Serializable` data class. Delete `ConfigJson.kt` (42 lines) and `Converters.kt` (14 lines) and store configs with `Json.encodeToString`. This is what unblocks the five automations that currently have no persistence path at all. **D9**
2. **Real migrations** — `exportSchema = true`, `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, commit `schemas/` to git, drop `fallbackToDestructiveMigration()`. Add one `MigrationTestHelper` instrumented test now, while there's only one version to protect. **D8**
3. **Seed correctly** — move the default-profile seed out of `Application.onCreate` into a `RoomDatabase.Callback.onCreate` (or a `suspend fun ensureSeeded()` awaited by the first repository read). Kills the race and the app-start DB open. **D10**
4. **Rename `defaultProfileId` → `activeProfileId`**; if you genuinely need "the profile to fall back to", that's a separate key. **D11**
5. **Home layout persistence** — new table: `home_item(profileId, packageName, activityName, userHandle, position)`. This is what makes pinning real. **D12 groundwork**

**Done when:** you can add a column to `ProfileEntity`, bump to v2, and your existing profiles are still there. There is a test proving it.

### Phase 3 — A home screen worth using (3–5 days)

1. **Icon cache, lazily filled** — an LRU cache keyed by `(packageName, activityName, userHandle)`, populated per-item as the grid composes, evicted under memory pressure. Icons stop living in ViewModel state. **D4**
   > `ponytail:` a plain `LruCache` sized off `ActivityManager.memoryClass` is enough; reach for Coil's `AsyncImage` only if you need disk caching or crossfades.
2. **Render immediately** — the home screen must never show a full-screen spinner. Wallpaper + dock chrome paint on frame 1; app cells fill in. Delete the `if (uiState.loading) { … return }` early-return.
3. **Real pinned apps** — backed by the Phase 2 `home_item` table. Long-press to add/remove, drag to reorder.
4. **Long-press context menu** on every icon: App info (`ACTION_APPLICATION_DETAILS_SETTINGS`), Uninstall (`ACTION_DELETE`), Hide, Rename, Pin/Unpin. Also surface `LauncherApps.getShortcuts()` static+dynamic shortcuts here — it's one API call and it's the feature users notice.
5. **Hidden apps + renamed apps** — both are Olauncher staples and both are trivial once (3) exists. Store in DataStore or a small table.
6. **Gestures** — swipe up = drawer, swipe down = notification shade (`StatusBarManager.expandNotificationsPanel` via reflection, or an AccessibilityService action), double-tap = lock screen (`DevicePolicyManager.lockNow` with a `DeviceAdminReceiver`, or `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN` on API 28+). Prefer the accessibility route over device-admin: device-admin makes the app un-uninstallable-looking and scares users.
7. **The clock/date card** — currently `remember { SimpleDateFormat(...).format(Date()) }`, which never updates. Either make it tick or replace it with something useful (next calendar event, battery, unread count).

**Done when:** cold start to interactive is under 500 ms on a mid-range device, pinned apps persist across reboot, and long-press does something on every icon.

### Phase 4 — Drawer and search (2 days)

1. **Search that works** — replace the fake field with a real one: fuzzy match on app label (initials too — "gm" → Gmail), then contacts (needs `READ_CONTACTS`, ask lazily), then a web-search fallback. Keyboard opens automatically; Enter launches the top hit. **D13**
2. **Categorised drawer** — `AppCategoryRules` already exists but is a substring-match list that will mis-file plenty of apps. Layer it: (a) `ApplicationInfo.category` (`CATEGORY_GAME`, `CATEGORY_SOCIAL`, `CATEGORY_PRODUCTIVITY`… available API 26+, which is your minSdk — use it first), (b) your keyword rules as fallback, (c) a per-package user override that always wins. This mirrors Smart Launcher's behaviour, which is the single feature people install that launcher for.
3. **A–Z fast scroll** and section headers in the drawer.

**Done when:** typing 2 characters finds any app, and every installed app lands in a defensible category with a way to correct it.

#### Landed early: layered categoriser + optional on-device AI

Phase 4 §2's layering is built (`data/AppCategorizer.kt`), plus an optional fourth
layer. Resolution order, cheapest first:

1. `ApplicationInfo.category` — developer-declared, free, no permission
2. `AppCategoryRules` — the keyword list, for apps that declare nothing
3. AI cache (`ai_category` table) — only consulted for apps steps 1–2 left in `Other`
4. `Other`

Step 3 is **Gemini Nano via ML Kit's Prompt API** (`com.google.mlkit:genai-prompt`),
which runs inside AICore, a Google system service — not Samsung, and not our process.
That last part is the reason this and not a bundled LiteRT/Gemma model: the launcher
is the home process, and a launcher that balloons past lmkd's threshold leaves the
user with no home screen. AICore owns the weights, so our resident set and cold start
are unchanged. Cost is ~1.6 MB of client AARs before R8, and no model bytes at all.

It is off by default, behind a switch in Settings, and every failure mode — unsupported
chipset, unlocked bootloader, declined download, inference quota, garbled response —
lands on the same path as "the user never turned it on". Turning it off clears the
cache, so categories revert to exactly what the rules alone produce.

Also fixes part of **D8**: `Migration(1,2)` is a real migration, so the new table does
not take the user's profiles with it. `fallbackToDestructiveMigration()` is still
there for every other version bump — Phase 2 still owns removing it.

Remaining: new apps are only classified when Settings is opened (marked `ponytail:`
in `SettingsViewModel`); a per-package user override that beats all four layers is
still Phase 7.

### Phase 5 — The profile engine (3–4 days)

This is your product. Everything before it was table stakes.

1. **Domain layer** — an `Automation` interface: `id`, `isSupported(context)`, `requiredPermissions`, `suspend fun apply(config)`, `suspend fun revert()`. A `ProfileEngine` that diffs the outgoing profile against the incoming one and applies only what changed.
2. **Built-in profile templates** — Study / Social / Productivity / Gaming / Focus / Wellness / Driving as *seeded rows*, not code branches. `ProfileType` should influence defaults and nothing else, or you'll end up with a `when` over the enum in ten places.
3. **Profile editor that isn't a wall of buttons** — `ProfileEditScreen` currently renders one full-width `Button` per `ProfileType`. Rebuild: name, icon, type picker, then a card per automation with an enable switch and inline config.
4. **Quick switch** — long-press on home, a Quick Settings tile (`TileService`), and an app shortcut per profile (`ShortcutManager`). All three are cheap.
5. **Scheduler** — `AlarmManager.setExactAndAllowWhileIdle` for the next transition only (re-armed on fire), not `WorkManager`: WorkManager's minimum periodic interval is 15 minutes and it will not fire on time, which is precisely what a "9am → Productivity" schedule needs. Re-arm on `BOOT_COMPLETED` and on `TIME_SET`.
   > `ponytail:` one alarm for the next slot, re-armed each fire — not N alarms for N slots. Overlapping-slot resolution is "last matching slot wins"; revisit if users want priorities.

**Done when:** switching a profile visibly changes the home screen and drawer, a schedule fires on time after a reboot, and adding a 7th automation touches exactly one new file.

### Phase 6 — Automations, feasible ones first (4–6 days)

Build in this order — each is independently shippable, and the risky ones are last.

1. **App visibility** (0 permissions) — already half-built. Finish it against the Phase 4 categoriser.
2. **Greyscale, tier A** (0 permissions) — desaturate the launcher's own UI. See §1.1.
3. **Notification filter** — `PortalNotificationListenerService` is an empty stub. Implement `onNotificationPosted` → look up the active profile's `NotificationFilterConfig` → `cancelNotification(key)` or `snoozeNotification`. Needs the user to grant notification access (`ACTION_NOTIFICATION_LISTENER_SETTINGS`); handle the not-granted case everywhere. **Note:** cancelling notifications is destructive from the user's point of view — default to *silencing* (`setNotificationsShown` / snooze), and make hard-cancel an explicit opt-in with a clear warning.
4. **App blocker** — usage-access polling from a foreground service (see §1.2). Overlay screen offering "Go back" / "5 more minutes" / "Unlock for the session". This is where `SYSTEM_ALERT_WINDOW` and the foreground service finally get used, which resolves **D14**. Declare `FOREGROUND_SERVICE_SPECIAL_USE` properly with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` property, or pick a better-fitting type.
5. **Greyscale, tiers B and C** — deep link, then optional ADB/Shizuku.

Every automation must degrade visibly: if the permission isn't granted, the profile editor shows *why* and a button to fix it. Silent no-ops are the fastest way to a 1-star review.

**Done when:** each automation has an integration test or a documented manual test script, and every one of them tells the user when it can't run.

### Phase 7 — Settings, onboarding, backup (2–3 days)

1. **Onboarding** — currently a single `Text`. Needs: set-as-default-home, then per-automation permission requests explained in plain language, skippable, resumable.
2. **Settings** — the three `ListItem`s are inert. Wire App categories (per-package overrides), Backup & restore, Scheduler, plus Hidden apps, Icon size/grid, Theme (system/light/dark/black), and About/licences.
3. **Backup & restore** — export profiles + configs + overrides as one JSON file via `ACTION_CREATE_DOCUMENT`, import via `ACTION_OPEN_DOCUMENT`. Include a `schemaVersion` field and validate it on import. No backend, no account, no network permission. (See §4 — this is why you don't need `portal_server`.)
4. **Auto Backup rules** — `backup_rules.xml` and `data_extraction_rules.xml` are both untouched templates. Decide explicitly what syncs to the cloud; a device's app list arguably shouldn't.
5. **Extract all strings** to `strings.xml` with plurals. Then RTL-test with `adb shell settings put global debug.force_rtl 1`. **D17**

**Done when:** a fresh install can be driven to a working configured state without you explaining anything, and a restore on a second device reproduces it.

### Phase 8 — Quality gates (2–3 days)

1. **Unit tests** (JVM, no emulator) for the logic that will actually break:
   - category resolution (`ApplicationInfo.category` → rules → override precedence)
   - config serialization round-trips, including unknown-field tolerance
   - scheduler slot resolution: overlapping slots, midnight wrap (23:00→02:00), DST
   - profile-diff → automation apply/revert set
2. **Instrumented tests**: Room migration v1→v2, one Compose UI test per screen. Add `testTag`s as you go — there are none today.
3. **Static analysis**: lint with `warningsAsErrors`, plus **detekt** or **ktlint**. Pick one, not both.
4. **Crash reporting** — Firebase Crashlytics or Sentry. Non-negotiable: a launcher that crashes leaves the user with no home screen and no obvious way to recover.
5. **Baseline profile** — `androidx.baselineprofile` plugin + `androidx.profileinstaller`. For a launcher, cold start *is* the product. Expect a 20–30% start-up improvement.
6. **CI** — `.github/workflows/android.yml`: on PR run `assembleDebug`, `testDebugUnitTest`, `lint`, detekt. On tag, build a signed AAB. This repo would be the monorepo's first CI, so there's no existing workflow to copy.
7. **Manual device matrix**: one Android 8 device (minSdk 26), one Android 13, one Android 15/16, one tablet, one foldable if you have it. Test with 200+ apps installed and with a work profile present.

**Done when:** CI is green on a PR, a crash on a test device shows up in your dashboard, and you have a start-up number you measured rather than guessed.

### Phase 9 — Release (2–3 days)

1. **Signing** — generate an upload keystore, `signingConfigs` reading from a gitignored `keystore.properties` (locally) and env vars/secrets (in CI). Enrol in Play App Signing. **Back the keystore up somewhere you won't lose it — losing it means you can never update the app.**
2. **R8** — `isMinifyEnabled = true` + `isShrinkResources = true`. Then actually test the release build: reflection-based code breaks under R8, and you have Room + kotlinx.serialization + possibly Shizuku. Add keep rules only for what genuinely breaks. Also check whether `material-icons-extended` is worth its weight after shrinking, or whether you should copy the 6 icons you use.
3. **Versioning** — `versionCode` from CI build number or a `git rev-list --count HEAD`; `versionName` semver. It's `1`/`1.0` right now.
4. **Play Console declarations** — this is where launchers get rejected:
   - Privacy policy URL (**required**, you have notification access and usage stats)
   - Data safety form — declare that app usage data is collected/processed **on-device only** if that's true
   - `QUERY_ALL_PACKAGES` declaration form, *if* you kept it (Phase 1 exists to avoid this)
   - Notification access disclosure + in-app prominent disclosure before the request
   - Foreground service type justification for `SPECIAL_USE`
   - Accessibility disclosure, only if you added the Phase 6 AccessibilityService
5. **Store listing** — screenshots on real devices, a feature graphic, and a description that leads with profiles. Don't claim device-wide greyscale (see §1.1).
6. **Staged rollout** — internal testing → closed → 20% production. A broken launcher update is worse than a broken anything-else update.

**Done when:** an internal-testing AAB installs from Play, is set as default home, and survives a day of your own use.

---

## 4. What the monorepo actually gives you

Blunt answer: **almost nothing reusable, and that's fine.**

`packages/portal_*` are Dart/Flutter. `portal_launcher` is Kotlin/Compose. There is no code path between them. Specifically:

| Candidate | Verdict |
|---|---|
| `packages/portal_ui_kit` design tokens (`design_tokens.dart`: spacing 4/8/12/16/24/32/48, radii 6/10/14/20, type scale 11/12/…) | **Copy the numbers, once, by hand** into a Compose `PortalDimens`/`PortalShapes` object so the launcher looks like the family. ~30 lines. Do not build a generator for two consumers. |
| `packages/portal_core` models | Not applicable. Different language, and the launcher's entities are Android-specific. |
| `server/portal_server` (NestJS + MongoDB) | **Skip.** Adding a network dependency + auth + a privacy-policy surface to get profile sync is a bad trade against Phase 7's JSON export. Revisit only if users ask for multi-device sync. |
| `tools/bricks` (Mason) | Flutter widget templates. Not applicable. |
| Monorepo CI | Doesn't exist. Phase 8 makes this repo the first. |
| `.cursor/` conventions | Worth aligning so both projects get the same assistant behaviour. |

The one genuinely useful inheritance is the monorepo's **README discipline** — `portal_launcher`'s entry there still says "Early scaffold — models and placeholder screens exist, no functional logic yet." Keep that line honest as you go.

---

## 5. Deliberately out of scope for v1.0

Say no to these now, in writing, or they'll eat the schedule:

- ~~Widgets (`AppWidgetHost` is a multi-week feature on its own)~~ **reopened, see §12**
- ~~Multi-page home~~ **reopened, see §12**
- Folders
- Icon packs / custom icon theming
- Cloud sync and accounts
- Lock screen replacement, app vault, custom notification shade
- iOS/Flutter port

---

## 6. Definition of done for v1.0

- [ ] Set as default home on Android 8 through 16; home button always returns home
- [ ] Cold start to interactive < 500 ms, measured with a baseline profile installed
- [ ] Installing/uninstalling an app updates the launcher with no restart
- [ ] Work-profile apps appear with the badge
- [ ] Every string localizable; RTL verified
- [ ] Room migration path tested; no user data loss across versions
- [ ] Every automation either works or explains why it can't
- [ ] Release build: R8 on, signed, tested as an AAB from Play internal testing
- [ ] Crash-free sessions > 99.5% in internal testing
- [ ] CI green: build + unit tests + lint + detekt
- [ ] Play policy declarations complete; privacy policy live
- [ ] Backup export → wipe → import reproduces the full configuration

---

## 7. Rough schedule

| Phase | Days | Cumulative |
|---|---|---|
| 0 Repo governable | 0.5 | 0.5 |
| 1 Behave like a launcher | 3 | 3.5 |
| 2 Data layer | 2 | 5.5 |
| 3 Home screen | 5 | 10.5 |
| 4 Drawer + search | 2 | 12.5 |
| 5 Profile engine | 4 | 16.5 |
| 6 Automations | 6 | 22.5 |
| 7 Settings/onboarding/backup | 3 | 25.5 |
| 8 Quality gates | 3 | 28.5 |
| 9 Release | 3 | 31.5 |

**~6.5 working weeks solo**, excluding Play review turnaround and the beta feedback loop. Phases 1–4 are the ones that turn this from a scaffold into something you'd put on your own phone; if you only have two weeks, do those and ship a launcher with one profile.

---

## 8. What phases 0 and 1 actually did

Done 2026-08-02. Everything here was verified by a green
`assembleDebug + assembleRelease + lintDebug + testDebugUnitTest`.

### Defects closed

D1, D2, D3, D5, D6, D7, D11, D13 (partially — the pill is now an honest button,
real search is still phase 4), D15, D16, D18. Plus D4's worst half: icons are no
longer rasterised eagerly. Still open: D8, D9, D10, D12, D14, D17.

### Toolchain — the plan was wrong about AGP

The plan said "stay on AGP 8, don't do a major migration in phase 0". That
turned out not to be an option worth defending: the current androidx line
(`core-ktx` 1.19, `lifecycle` 2.11, `activity-compose` 1.13, `datastore` 1.2)
all require **AGP 9.1+ and compileSdk 37**, so AGP 8 means permanently trailing.
The project is now on **AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.2.21 / KSP 2.3.2**,
with androidx pinned to the newest AGP-9-compatible-at-compileSdk-36 versions
and **Compose BOM 2024.06.00 → 2026.01.01**.

Two follow-ups this created, both tracked in `gradle.properties`:

- The AGP Upgrade Assistant added ten `android.*` compatibility flags that pin
  AGP 8 behaviour. **All are removed in AGP 10.** Migrating `newDsl` and
  `builtInKotlin` means rewriting the build script against
  `com.android.build.api.dsl.ApplicationExtension` — its own task, deliberately
  not done here. The two R8 flags (`strictFullModeForKeepRules`,
  `optimizedResourceShrinking`) *were* flipped on, because they change the
  release artifact and waiting until phase 9 to find out is the expensive order.
- `compileSdk`/`targetSdk` are still 36 while 37 exists. Lint's `OldTargetApi`
  is disabled with a comment: raising targetSdk is a reviewed, re-tested task,
  not a side effect of turning lint gating on. **Do it as its own change.**

### R8 measured, so two plan assumptions can be dropped

`isMinifyEnabled` + `isShrinkResources` are on and the release build passes
under R8 strict full mode with **zero keep rules added**:

| Build | Size |
|---|---|
| debug | 21.59 MB |
| release (R8 + resource shrinking) | **2.32 MB** |

So phase 9's "check whether `material-icons-extended` is worth its weight" is
answered: R8 strips it fine, leave it alone. Debug builds also now carry
`applicationIdSuffix = ".debug"`, so a debug build installs alongside the
release one — you keep a working home screen while testing.

### Lint gating, and the two checks that had to go

`warningsAsErrors = true`, `abortOnError = true`, **no baseline** — an empty
baseline is only somewhere for debt to hide, so the file was deleted along with
the config line. Disabled, each for a stated reason: `OldTargetApi` (above), and
`GradleDependency` / `AndroidGradlePluginVersion` / `NewerVersionAvailable`,
which hit the network and fail the build whenever anything upstream publishes —
dependency updates are a scheduled job, not a build gate.

Turning lint on immediately found things worth having found:

- **It confirmed D5 independently.** `WallpaperManager.getDrawable()` needs
  `MANAGE_EXTERNAL_STORAGE` or the signature-only `READ_WALLPAPER_INTERNAL`, so
  the wallpaper-into-a-bitmap code was already dead on modern Android. Deleted
  ~30 lines; the wallpaper now comes from `android:windowShowWallpaper` on the
  window, which also means live wallpapers and parallax work.
- `Configuration.screenWidthDp` is deprecated for `LocalWindowInfo.containerSize`
  — and reports the wrong thing in split-screen and on foldables. `WindowSize.kt`
  now reads the window it was actually given.

### Deletions

`values-night/themes.xml`, `values/colors.xml`, `values/dimens.xml`,
`values-sw600dp/`, `values-w840dp/`, both `Example*Test` templates, and the
`appcompat` dependency (nothing referenced it; `material` pulls it transitively).
The dimens were the XML-View way of doing adaptivity, which this app does not
use — `currentWindowSizeClass()` is the real mechanism and nothing read `R.dimen`.
Colours live in Compose, so `themes.xml` now defines none and needs no night
variant.

### New code

- `data/AppRepository.kt` — `LauncherApps` instead of `queryIntentActivities`:
  every user profile (so work-profile apps appear, badged via
  `getUserBadgedIcon`), correct activity aliases, and a registered
  `LauncherApps.Callback` so install/remove/update push a fresh list instead of
  the list going stale until process death. Launches through
  `startMainActivity`, the only way to launch into another profile.
- `data/DefaultHomeStatus.kt` — `RoleManager.ROLE_HOME` on API 29+, falling back
  to `Settings.ACTION_HOME_SETTINGS` on 26–28. Onboarding re-checks on resume,
  because the pre-29 path returns no result and the role dialog can be dismissed.
- `OnboardingScreen` went from one `Text` to the actual set-as-home flow, and is
  now the start destination whenever we are not the home app. "Skip for now" is
  per-process, so it needs no persisted flag.
- One real unit test (`AppCategoryRulesTest`) replacing the templates. It pins
  three current mis-filings on purpose — `com.google.android.calendar` and
  `…apps.docs` resolve to **Study**, and `com.acme.gamesetup` to **Gaming** —
  so phase 4's categoriser rewrite is a deliberate change, not an accident.

### Where the manifest landed

`<queries>` with the launcher intent (enumerates launchable apps with **no**
`QUERY_ALL_PACKAGES`, avoiding the Play declaration form entirely), plus
`enableOnBackInvokedCallback`. `SYSTEM_ALERT_WINDOW`,
`FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS` were **removed** —
nothing implements them yet, and a permission with no code behind it is pure
review liability. Phase 6 adds each one back with the automation that needs it.

### One thing phase 1 introduced that phase 2 must watch

The window is now transparent so the wallpaper shows through. That is right for
the home screen and wrong everywhere else: any destination that is not the home
screen needs an opaque background, or it renders over whatever is behind the
window. `AdaptiveLauncherScaffold` takes `showNav` and sets a transparent
container; if a new full-screen destination looks black, this is why.

---

## 9. What phase 2 actually did

Done 2026-08-02. Verified by a green `assembleDebug + assembleRelease +
lintDebug + testDebugUnitTest` (29 unit tests) and by
`connectedDebugAndroidTest` on a Pixel 9a emulator (2 migration tests).

### Defects closed

D8, D9, D11 (finished), D12 (the table, not the UI). D10 was **not** done as
written — see below. Still open: D14, D17, and D12's home-screen UI.

### Migrations are now real

- `exportSchema = true`, schemas written to `app/schemas` and committed, with
  `ksp { arg("room.schemaLocation", …) }`.
- **`fallbackToDestructiveMigration()` is gone.** It turned a forgotten
  migration from a build failure into silently deleting every profile the user
  made. A missing migration should stop us, not cost them their setup.
- Migrations moved out of the companion into `AppDatabaseMigrations` so a test
  can run one directly against the previous version's exported schema.
- `MigrationTest` (instrumented) asserts a *renamed* profile and its automation
  config both survive 2→3 — a rename is what proves nothing was recreated from
  defaults — and that deleting a profile cascades its home layout away.
  `runMigrationsAndValidate` also diffs the post-migration schema against the
  exported JSON, so migration SQL that drifts from the entity fails in CI.

Sequencing worth remembering: the v2 schema had to be exported **before**
bumping to v3, or there would be no baseline for the test to build v2 from.

### Configs are JSON, and five of them can now be stored at all

`kotlinx.serialization` replaces `ConfigJson.kt`, whose hand-rolled
`"primary:a,b;secondary:c"` format covered exactly one of six config types and
silently corrupted any value containing `,` or `;`. `ConfigCodec` handles all
six. `ignoreUnknownKeys` plus a default on every field means an older build can
read a config a newer one wrote; `decodeOr` falls back rather than throwing,
because a config that fails to parse would otherwise crash the home screen, and
a user with no home screen has no way to recover. `ConfigCodecTest` covers the
round trip, the values that broke the old format, legacy rows still on dev
installs, and an unknown future field.

**The plan was wrong about `Converters.kt`** — it said to delete that too. Room
still needs a `List<String>` converter for `ProfileEntity.enabledAutomationIds`,
so replacing it with a JSON one is the same line count plus a data-compat break
for zero gain. It stays.

### D10: not moved into a RoomDatabase.Callback

`onCreate` hands you a raw `SupportSQLiteDatabase`, so seeding there means
re-expressing every `BuiltInProfiles` entry as `execSQL` and keeping the two in
step by hand. The race it closes is one frame of an unfiltered home screen —
which is exactly what the "All apps" profile shows anyway. Duplicated SQL is the
worse trade. Instead: the application got a named `SupervisorJob` scope, and the
real fix went in at the read side (below).

### A bug found next door: the dangling active profile

Deleting the active profile left `activeProfileId` pointing at nothing. The
active profile resolved to null, which silently disabled **all** filtering, and
the profile list showed nothing selected while the home screen applied nothing.
`resolveActiveProfile` now falls back stored id → default profile → first
profile, and both screens go through it, so what is shown as active and what is
applied cannot disagree. The visibility config is keyed off the *resolved*
profile rather than the raw preference for the same reason.

### Home layout table

`home_item(profileId, packageName, activityName, userSerial, position)`,
per-profile, cascading from `profiles`. Keyed on `userSerial` from
`UserManager.getSerialNumberForUser` rather than a `UserHandle`, because a pinned
work-profile app has to still resolve after a reboot and `UserHandle` is only
meaningful for the current boot. `replaceForProfile` rewrites a reorder in one
transaction instead of N position updates, which would leave duplicate positions
if the process died partway. **Phase 3 still has to build the UI on top of it** —
pinned apps are still the first 8 alphabetically.

### Two findings from running it on a device

- **The Study profile is empty on a stock device.** `AppCategorizer` prefers
  `ApplicationInfo.category`, and Calendar and Drive both declare
  `CATEGORY_PRODUCTIVITY` — so they land in Productivity, and Study matches
  nothing. Productivity showed Calendar/Chrome/Drive/Gmail; Study showed zero.
  `BuiltInProfiles`' comment that Productivity includes Study "because the rules
  file resolves Drive, Docs and Calendar to Study" is therefore stale: the system
  category wins first. **Phase 4 owns this** — it is precisely the coverage gap
  the on-device model is for.
- A profile matching nothing rendered a blank screen, which is the silent no-op
  this plan forbids. Fixed now rather than deferred: `EmptyProfileNotice` names
  the profile responsible and offers the drawer. Doing that also exposed the same
  contrast bug the app labels had — unselected `FilterChip`s default to a
  transparent container, putting their text straight onto the wallpaper — so the
  chips now carry the frosted backing the search pill already used.

---

## 10. What phase 3 actually did

Done 2026-08-02. Verified by a green `assembleDebug + assembleRelease +
lintDebug + testDebugUnitTest` plus 11 instrumented tests, and by driving the
running launcher on a Pixel 9a emulator holding the HOME role.

Release size after R8 is now **2.80 MB** (was 2.32 MB at phase 1).

### Shipped

1. **Icon cache** (`IconCache`) — an `LruCache` sized against
   `ActivityManager.memoryClass / 8`, keyed by app identity *and* pixel size,
   application-scoped so it survives navigation. It clears wholesale whenever the
   app list re-emits, which is exactly when an icon can have changed; crude beats
   tracking which packages a change touched, and it refills from visible cells.
   D4 is now fully closed.
2. **Real pinned apps** on the `home_item` table. Verified on device: pinning
   Calendar seeded the other seven as explicit pins and appended Calendar, so
   nothing vanished.
3. **Long-press context menu** — pin/unpin, rename, hide, app info, uninstall.
   Verified that **Uninstall does not appear for Calendar**, because
   `canUninstall` excludes system and work-profile apps. App info goes through
   `LauncherApps.startAppDetailsActivity`, not the settings intent, because that
   intent cannot target another user and would silently do nothing for a
   work-profile app.
4. **Hidden and renamed apps** — a new `app_override` table (schema **v4**),
   global rather than per-profile: categories already do per-profile visibility,
   and "call this something else" is a statement about the app.
5. **Swipe up to open the drawer.**

### Deferred, with the reason

Drag-to-reorder, `LauncherApps.getShortcuts()`, double-tap to lock and swipe-down
for the notification shade. The last two both need the `AccessibilityService` that
§1.2 says to defer until the blocker needs it, so they belong with phase 6 rather
than being half-built here.

### Three holes found by reviewing and running my own work

- **Swipe-up did nothing.** The gesture was on the outer `Box`, but the home grid
  had `weight(1f)` and so covered the "empty" area; its scroll modifier swallowed
  the drag even with nothing to scroll. The code comment had *rationalised* this
  risk instead of testing it. Fixed by sizing the grid to its content with a
  weighted `Spacer` beneath it, which is also the more honest layout. Caps the
  home screen at one screenful — fine, multi-page home is out of v1.0 (§5).
- **Unpinning everything resurrected everything.** Inferring the fallback from
  `pinned.isEmpty()` conflates "never customised" with "customised, then
  emptied" — and an empty home screen is exactly what a minimal-launcher user is
  after. `resolveHomeApps` now takes an explicit `isCustomised`, backed by a
  DataStore set of profile ids (a preference, not a column, so no migration).
  There is a test named after the bug.
- **Hide was a one-way door.** A hidden app is filtered out of the home screen,
  the drawer and search, so no surface was left that could offer to unhide it —
  while the menu told the user to go to Settings. Added `HiddenAppsScreen`, which
  deliberately reads the *unfiltered* app list. The same reasoning added a
  "Restore defaults" action to the emptied-home state.

### Testing note worth keeping

`HomeResolverTest` is an **instrumented** test despite being pure logic:
`LaunchableApp` holds a real `UserHandle`, which has no constructor available off
device. That is a better trade than making the field nullable or adding
Robolectric for one type.

---

## 11. Phases 4 to 9: what landed, and what is genuinely left

Done 2026-08-02, in one session. Gate at every commit: `assembleDebug`,
`assembleRelease`, `lintDebug` (warningsAsErrors, no baseline),
`testDebugUnitTest` (53 tests) and `connectedDebugAndroidTest` (12 tests on a
Pixel 9a emulator). Release AAB builds at 5.38 MB; the release APK is 2.82 MB.

### Landed

**Phase 4 — drawer and search.** `matchScore` ranks in tiers (exact, prefix,
word-start, initials, contains, subsequence), matches both the rename and the
app's own name, and ignores case and accents. Verified on device: "ca" gives
Camera then Calendar then Contacts. The drawer *is* the search surface, grouped
into category sections when idle and flat when searching. A manual category
override (schema v5) outranks every automatic source — the escape hatch for the
empty-Study-profile problem §9 found.

**Phase 5 — profile engine.** `Automation` with an availability result that
carries the *reason*, an `AutomationRegistry` list rather than a `when` over
profile types, and a `ProfileEngine` that reverts what the outgoing profile had
on. `ActiveProfileSource` is now the single answer to "which profile is in
effect", shared by the UI, engine, listener, scheduler and tile. Plus a
`ProfileScheduler` (one alarm, re-armed on fire/boot/clock-change) and a Quick
Settings tile that cycles profiles.

**Phase 6 — two of the automations.** Greyscale of Portal's own surfaces, and
per-profile notification rules that snooze by default and never touch ongoing or
foreground-service notifications.

**Phase 7 — backup and restore**, through the system document picker, replacing
rather than merging, with a message for every failure mode.

**Phase 8 — CI and crash recording.** GitHub Actions runs lint, unit tests, both
build types and the instrumented tests; tags build a signed bundle. Crashes are
recorded locally.

**Phase 9 — the code side.** Signing from environment variables, `versionCode`
derived from the commit count, AAB verified to build.

### Three bugs the tests and lint caught, worth remembering

- `nextBoundaryMinutes` treated a boundary at exactly the current minute as zero
  minutes away, so a 09:00–17:00 slot would switch on at nine and **never switch
  off** — the alarm went to 09:00 tomorrow.
- `ProfileScheduleReceiver` acted on any intent that reached it while declaring
  filters for protected broadcasts. Lint's `UnsafeProtectedBroadcastReceiver`
  found it.
- Because every backup field had a default, `{"volume":11}` decoded as a valid
  empty backup — restoring another app's settings file would have **silently
  deleted every profile**. The version field is now `@Required`.

### Not done — and none of it is hidden

**The app blocker.** The largest remaining feature: a foreground service,
usage-access polling, an overlay window, and its own Play declarations. §1.2 has
the mechanism decision already made (usage access, not AccessibilityService).

**No UI for automations or schedules.** This is the biggest gap and it is not
obvious from the outside: the engine applies whatever a profile has enabled, the
scheduler honours whatever slots are stored, and `ProfileEditScreen` still only
edits a name and a type. So greyscale, notification rules and schedules are all
*functional and unreachable* — a profile's `enabledAutomationIds` and its configs
can only be set from a seeded default or a restored backup. **Build this next; it
is what makes phases 5 and 6 visible at all.**

**Localization (D17).** Every string is still hardcoded English. Mechanical but
large, and `supportsRtl="true"` remains a claim rather than a fact.

**Crash reporting service.** Crashlytics or Sentry both need an account and a key
that cannot be committed. `CrashRecorder` covers the local half.

**Baseline profile.** Needs a benchmark module and a device to generate on, and
without a measurement there is nothing to show for it. Cold start is *the*
launcher metric, so this is worth real time rather than a guess.

**Compose UI tests**, including the accessibility question §8 left explicitly
unresolved: `uiautomator` cannot see Compose's merged semantics tree, so what
TalkBack actually announces is still unverified.

**Everything in Play Console.** The keystore, the privacy policy, the data safety
form, the notification-access disclosure, the store listing, staged rollout.
These need an account and legal text; the checklist is §7 phase 9.

---

## 12. Widgets and multi-page home

Reopened and built on 2026-08-02, hours after §1 decided to cut them. The ask was
"support widgets and pages management like Smart Launcher"; §5's reasoning still
stands for folders and icon packs, which stayed out.

### Why one change and not two

The old layout table stored a flat `position: Int` per app. A widget occupies a
*rectangle*, so it cannot be expressed as a position, and two widgets on one page
must not be allowed to overlap. Pages and widgets therefore needed the same
change — a coordinate model — and doing them apart would have migrated the home
layout schema twice for one result.

`home_item` is replaced by `home_cell` (schema **v6**): `(profileId, page, cellX,
cellY)` as the primary key, plus `spanX`/`spanY`, a `kind` of `app` or `widget`,
and the host-allocated `appWidgetId`. The composite key makes "one thing starts
in this cell" a database rule; overlap between spans is checked in `HomeGrid.kt`,
which SQLite cannot express. Apps and widgets share the table on purpose: two
tables would be two answers to "which cells are occupied", and a failed drag only
has to happen once for them to disagree permanently.

The 5→6 migration **converts** existing layouts rather than dropping them, through
`LegacyLayout` — frozen constants (4 columns, 20 per page) that a backup written
by an older build is read with too. `home_item` is dropped afterwards.

### What binding a widget actually requires

A third-party launcher **cannot** hold `BIND_APPWIDGET`; it is
signature|privileged. So:

1. `AppWidgetManager.bindAppWidgetIdIfAllowed`. If it returns false,
2. the `ACTION_APPWIDGET_BIND` consent dialog, which grants the permission **per
   app, once for the life of the install** — not per widget, and
3. the provider's configuration activity, launched through
   `AppWidgetHost.startAppWidgetConfigureActivityForResult`. From API 31 that
   activity need not be exported, so launching it by Intent throws; the host
   method runs it through a system-granted IntentSender. Its result arrives at
   `Activity.onActivityResult`, which is why `MainActivity` still overrides a
   deprecated method — no ActivityResultContract can express an IntentSender the
   system grants.

Our own picker, not `ACTION_APPWIDGET_PICK`, for the same permission reason: the
system picker binds on the caller's behalf.

Every failure path deletes the allocated id. The one it cannot cover — the process
dying while a system screen is up — is caught by `sweepOrphans` at start-up, which
also covers the two paths that have no Context to tell the host with: a profile
delete cascading its cells away, and a restore replacing the table.

### Pages have no management screen, on purpose

A page is created by dragging an icon onto the spare page the pager grows during a
drag, and destroyed when the last thing leaves it (`normalisePages`). So there is
no page to add, name, reorder or delete, and therefore no UI for it. Long-pressing
the wallpaper offers widgets and the grid size, nothing about pages.

### Grid size is a setting, which made a repair routine necessary

Columns and rows are adjustable (3–6 × 3–7). Narrowing the grid strands cells
outside it, and a cell at `cellX = 5` in a four-column grid is simply never drawn —
apps would appear to be deleted by a settings change. `reflow` puts them back,
preferring their own page so a resize does not silently re-order a layout the user
built. It is idempotent, which is what makes writing its result straight back
safe: the ViewModel observes the write and finds nothing left to do.

### Deliberately not done

- **Folders and icon packs.** Still out, per §5.
- **Work-profile widgets.** `installedProviders` is the current user only.
- **Drag handles for resizing.** The menu nudges a widget a cell at a time, which
  is the whole of what resize handles get used for.
- **Nested scroll between a widget and the pager.** A horizontally scrolling
  widget will fight the pager for the gesture. Marked `ponytail:` in `WidgetCell`.

### Gate

`assembleDebug`, `assembleRelease` (R8), `lintDebug` (warningsAsErrors, no
baseline), 69 unit tests and 15 instrumented tests on a Galaxy SM-S948B / Android
17 — including `migrate5To6_convertsTheFlatLayoutIntoPagedCells`, which asserts
against the exported schema and that `home_item` is gone.

**Verified on device:** the home pager renders the paged grid, and long-pressing
the wallpaper opens the options sheet with the grid controls (dumped from the
running app, not inferred).

**Not verified on device:** the widget picker and adding a real widget end to end.
The first attempt showed the bug described above — two `ModalBottomSheet`s swapped
in one frame, so tapping "Add widget" returned to an empty home screen. That is
fixed by folding the picker into the sheet already on screen, but the fix has not
been re-run: the test phone became unavailable. This is the one thing in §12 that
still needs a device before it can be called done, and it needs a phone that is
not the tester's daily driver, because the bind-consent dialog is a real system
prompt.

---

## 13. Automations and schedules became reachable

Done 2026-08-02. This closes §11's "biggest gap": greyscale, notification rules
and schedules were functional but could only be set from a seeded default or a
restored backup.

### The profile editor edits profiles now

`ProfileEditScreen` gained what §5.3 asked for: the category set the profile
puts on the home screen (multi-select chips; empty = all apps), and a card per
registered automation driven by `AutomationRegistry` + `AutomationAvailability` —
a switch when the automation can run, the reason and a **Grant** button when a
permission is missing (refreshed on resume, because the grant happens in another
app's UI), and the reason alone when the device cannot do it at all. Greyscale
gets an intensity slider and the developer-options deep link; the notification
filter gets a per-package picker and an explicit snooze-vs-dismiss switch that
spells out that dismiss loses notifications.

Fixed on the way, because the rebuild made it visible:
**`saveProfile` hardcoded `enabledAutomationIds = [app_visibility]`** — saving
any profile from the editor silently wiped every automation it had on. The
editor now round-trips the full set, keeps ids it does not recognise, and writes
configs even for automations currently off so switching one back on finds its
settings where the user left them.

### The schedule is global now, and has a screen

§11 stored `SchedulerConfig` per profile and read it from the **active**
profile. That design self-destructs: when a slot fires and switches to profile
B, B's (empty) config governs the next transition, so every schedule died the
first time it worked. The timetable moved to one `SchedulerConfig` JSON in
DataStore (`PreferencesRepository.scheduleJson`); the scheduler reads only
that. Per-profile scheduler rows are simply never read any more — no migration,
dev installs only.

Settings → Schedule lists the slots (start – end → profile, delete inline) and
adds one through a dialog: two `TimePickerDialog`s, profile chips, an "ends the
next day" note when the range wraps midnight. Every mutation persists and
re-arms the alarm in the same step, so the pending alarm can never describe a
timetable other than the stored one. Backups carry the timetable
(`scheduleJson`, additive so old files still read); restore drops slots naming
profiles the file does not contain, same rule as the dangling active id.

---

## 14. The app blocker

Done 2026-08-02. The last automation §6 listed, on the mechanism §1.2 decided:
usage-access polling, **not** an AccessibilityService — accessibility is instant
but is a routine Play rejection cause, and one rejected release costs more than
a second of detection latency. D14 is now resolved the right way round: the
permissions it flagged as declared-but-unused are declared *and used*.

### Shape

- `AppBlockerAutomation` — the availability gate and the engine's lever. Two
  grants asked for one at a time (usage access to *notice* a blocked app, the
  overlay to *do* something about it), each `NeedsPermission` carrying its own
  settings intent, so the editor card walks the user through both.
- `BlockerService` — FGS `specialUse` with the Play justification in the
  manifest `<property>`. Polls `UsageStatsManager.queryEvents` once a second
  while the screen is interactive, overlapping query windows by one poll so a
  missed clock edge cannot leave a blocked app open. Reads the active profile's
  config from `ActiveProfileSource` itself (same pattern as the notification
  listener), so list edits apply without a restart; a profile switch clears all
  reprieves. `START_STICKY`, and self-stops if a sticky restart lands on a
  profile that no longer blocks.
- The overlay is plain views on purpose — a ComposeView in a service window
  needs a hand-rolled lifecycle owner, and this screen is two lines of text and
  three buttons: **Go back** (home), **5 more minutes** (timed reprieve),
  **Unlock for this session** (until the profile changes or the service dies).
  Back on the overlay means "leave the blocked app".
- `BlockerPolicy` — the block/allow decision kept pure and unit-tested,
  including the two never-block cases: the launcher itself (an overlay over
  home is a lockout with no surface left to undo it) and the default dialer
  (a pause screen over an emergency call is indefensible).

### Not verified on device

The full grant flow and the overlay need a phone: usage access and the overlay
grant are real system settings screens, and emulator `queryEvents` timing is
not the field. Same device session as §12's widget picker check.

---

## 15. Localization (D17 closed)

Done 2026-08-02. Every user-visible string now lives in `strings.xml` — ~150
strings across every screen, sheet, dialog, the blocker overlay, the FGS
notification and the backup messages. `supportsRtl="true"` is now backed by
something translatable; layouts were already start/end-based Compose, so RTL
needs only the manual `debug.force_rtl 1` pass on a device.

Design decisions that were not mechanical:

- **`Automation.title/summary` became `titleRes`/`summaryRes`.** The interface
  had no Context to resolve against; availability explanations stay `String`
  because `availability(context)` already has one.
- **Built-in profile names resolve exactly once, at seed time.**
  `BuiltInProfile.nameRes` + `seedBuiltInProfiles(resolveName)` — after
  seeding, the name is the user's data, the same as a rename. Changing the
  device language does not rename existing profiles, which is the same promise
  every launcher makes about its stock items.
- **Category and profile-type display names moved to `ui/kit/Labels.kt`** —
  the enum names are storage keys and must never change; what the user reads
  now localizes independently. A stored type that matches no enum (hand-edited
  backup) falls back to the raw text instead of crashing.
- **Plurals** where a count is user-facing (`%d chosen`, `Restored %d
  profiles`); the grid subtitle is symbolic (`4 × 5`) because the counts are
  clamped to 3–7 and a plural would translate a case that cannot occur —
  lint's `PluralsCandidate` agreed about the rest.

Still manual: an actual translation (`values-xx/`), and the on-device
`debug.force_rtl` sweep.
