# Portal Launcher – Native Android App – Project Context

## 1. Project overview

**Name:** Portal Launcher  
**Platform:** Native Android (Kotlin preferred)  
**Concept:** A profile-based launcher that changes home screen layout, app visibility, and device behaviour per “mode” (Study, Social, Productivity, Gaming, etc.). Each profile specializes the experience (e.g. Study = greyscale + study apps + block social), while reusing a shared set of automations (filters, blocking, scheduling, notifications).

**Design principle:** Profiles are configurations over a common engine. Most behaviour is implemented as reusable automations that multiple profiles can enable/configure differently.

---

## 2. Profile types and specializations

Define and implement these profile types with the following specializations. Each profile can enable a subset of reusable automations and define profile-specific app categories and rules.

### Study profile
- **Purpose:** Reduce distraction and support focused learning.
- **Specializations:**
  - **Display:** Greyscale (or reduced colour) filter on screen.
  - **App organization:** Show only “study” apps (e.g. Adobe, Google Drive, Duolingo, notes, PDF readers, calendar). Other apps hidden from main launcher view but still installable.
  - **Blocking:** Social media and entertainment apps treated as “blocked” (launch blocked or delayed via blocker app/overlay).
  - **Optional:** Do Not Disturb or notification rules (e.g. only allow study-related apps to notify).
- **Reusable automations used:** Greyscale filter, App visibility by category, App blocker, Optional notification filter.

### Social profile
- **Purpose:** Prioritize communication and social apps without fully hiding the rest of the device.
- **Specializations:**
  - **App organization:** Social apps (Messenger, WhatsApp, Instagram, Twitter/X, etc.) prominently shown; other apps still accessible (e.g. in drawer or secondary list) but not emphasized.
  - **No blocking:** Other apps remain launchable; layout and prominence are profile-specific only.
- **Reusable automations used:** App visibility / prominence by category (no blocker).

### Productivity profile
- **Purpose:** Work-focused: email, calendar, tasks, documents, meetings.
- **Specializations:**
  - **App organization:** Productivity apps (email, calendar, task apps, Drive, Office, Slack/Teams, etc.) on main view; others in drawer or secondary.
  - **Optional:** Greyscale or “focus” theme to reduce visual distraction.
  - **Optional:** Limit or block social/entertainment during work hours if user configures it.
- **Reusable automations used:** App visibility by category, Optional greyscale, Optional app blocker / notification rules.

### Gaming profile
- **Purpose:** Quick access to games and game-related apps (stores, Discord, etc.) with minimal friction.
- **Specializations:**
  - **App organization:** Games and game-related apps (stores, Discord, game launchers) on main view; optional “performance” or “do not disturb” style behaviour.
  - **Optional:** Disable or reduce non-essential notifications while in Gaming profile.
- **Reusable automations used:** App visibility by category, Optional notification filter.

### Additional profiles (implement as needed)
- **Focus / Deep work:** Similar to Study but configurable (user-defined app set + optional greyscale + blocker).
- **Wellness / Wind-down:** E.g. evening mode: dim/greyscale, limit social and games, allow only calls/messages from selected contacts.
- **Driving / Minimal:** Very few apps on screen, large targets, optional “drive mode” behaviour (e.g. auto-respond or read-only).

Each of these can be implemented by composing the same reusable automations with different config (which apps to show, block, or notify).

---

## 3. Reusable automations (shared engine)

Implement these once and let each profile enable/configure them. This keeps the codebase modular and avoids duplicating logic.

| Automation | Description | Used by (example) |
|------------|-------------|-------------------|
| **Greyscale / colour filter** | System overlay or accessibility/display filter to reduce or remove colour. | Study, Productivity, Wellness |
| **App visibility by category** | Tag apps with categories (study, social, productivity, gaming, etc.); per profile, define which categories are “primary” (on home) vs “secondary” (drawer) vs hidden. | All profiles |
| **App blocker** | When user tries to open a blocked app, show overlay / delay / block (configurable per profile). Block list per profile. | Study, Productivity, Wellness |
| **Notification filter** | Per profile: allow/block/silence notifications by app or category. | Study, Productivity, Gaming, Wellness |
| **Profile scheduler** | Time-based or calendar-based profile switch (e.g. 9–17 = Productivity, 20–22 = Social, 22–7 = Wellness). | All (optional) |
| **Quick profile switch** | UI (tile, widget, or launcher gesture) to change active profile without opening settings. | All |

Data model suggestion: **Profile** = id, name, icon, list of **enabled automations** + their **config** (e.g. which categories are primary, which apps are blocked, greyscale on/off, schedule).

---

## 4. Technical direction (native Android)

- **Language:** Kotlin.
- **Min SDK:** Align with need for overlay/accessibility/launcher APIs (e.g. 24+ or 26+; confirm for launcher and overlay).
- **Architecture:** Single-activity or few activities; launcher home as default. Use ViewModel + repository for profile and automation state; consider Room for profile/config persistence.
- **Key Android pieces:**
  - Custom launcher (default home) or “launcher-like” overlay that lists apps by profile and category.
  - App categorization: either manual tagging, or rules (package names / system categories) with optional user overrides.
  - Greyscale: `WindowManager` overlay with filter, or `ColorMatrix`/`setColorFilter`, or accessibility/display API depending on OS version and policy.
  - App blocking: intercept launch (e.g. via launcher as sole entry or usage access) and show block overlay or delay.
  - Notifications: `NotificationListenerService` (with user permission) to filter by profile.
  - Profile scheduler: `WorkManager` or `AlarmManager` for time-based profile switches; store schedule in profile config.

---

## 5. Suggested implementation steps (for planning)

1. **Scaffold:** Create native Android project (Kotlin), add modules/structure (app, domain profiles, automations).
2. **Data layer:** Define Profile and AutomationConfig models; Room DB or DataStore for profiles and per-profile settings.
3. **Reusable automations:** Implement each automation as a separate module or component (greyscale, app visibility, blocker, notification filter, scheduler, profile switcher) with a common interface (enable/disable, apply config).
4. **App categorization:** Define categories and mapping from packages to categories; launcher UI to show apps by category per profile.
5. **Launcher UI:** Home screen that shows apps based on active profile and visibility rules; profile switcher (tile/widget/settings).
6. **Profiles:** Implement each profile type (Study, Social, Productivity, Gaming, etc.) as configs that wire up the right automations with the right parameters.
7. **Scheduler:** Add time-based profile switching using stored profile configs.
8. **Polish:** Onboarding, profile creation/editing UI, backup/restore of profile configs.

---

## 6. What to output in this session

- Detailed project plan (phases and tasks).
- Native Android project structure (packages, modules).
- Data models for Profile and automation configs.
- List of screens and key user flows.
- API/OS considerations (permissions, launcher default, overlay, notification listener).
- Optional: high-level class/component diagram for launcher, profile engine, and automations.

---

## 7. Implementation status (scaffold)

**Package:** `com.tanvoid0.portallauncher`

**Done:**
- **Build:** compileSdk/targetSdk 35, minSdk 26, Kotlin 2.0, JDK 17. Compose BOM, Room (KSP), material3-adaptive, navigation-compose.
- **Manifest:** Launcher Activity (MAIN + HOME + DEFAULT), `PortalLauncherApplication`, `PortalNotificationListenerService` placeholder, permissions (SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS).
- **Data layer:** `ProfileEntity`, `ProfileType`, `AutomationConfigEntity`, config DTOs (GreyscaleConfig, AppVisibilityConfig, etc.), `ProfileDao`, `AutomationConfigDao`, `AppDatabase`, `Converters` (List&lt;String&gt;).
- **UI:** Single-activity `MainActivity` with Compose; custom `AdaptiveLauncherScaffold` and `WindowSizeClass` (Compact &lt; 600dp, Medium, Expanded)—bottom nav on compact, navigation rail on medium/expanded; NavHost with routes: launcher home, profile list, profile edit, onboarding, settings.
- **Screens (placeholders):** LauncherHomeScreen, ProfileListScreen, ProfileEditScreen, OnboardingScreen, SettingsScreen.
- **Resources:** `values`, `values-sw600dp`, `values-w840dp` (dimens); theme and strings unchanged.

**Next (from §5 steps):** Data & engine (profile CRUD, default profile); automations (greyscale, visibility, blocker, notification filter, scheduler, quick switch); launcher UI (apps by category); profile configs; onboarding flows; backup/restore.