# Fudge Compose Design System (Codex Execution Guide)

Version: v1.0  
Scope: Jetpack Compose UI code under `frontend/app`  
Audience: Codex agents and engineers modifying UI code

This document defines non-negotiable UI rules so every Codex change stays visually consistent, localizable, and testable.

## 1. Core Principles

1. State-first UI: loading, empty, error, in-progress, and success must be explicit.
2. Card-based composition: prefer `Card`/`Surface` blocks over loose text stacks.
3. Controlled branding: use Fuji red as an accent, not as a full-page fill color.
4. Consistent density: spacing, radius, typography, and control height must feel uniform.
5. Business safety: UI refactors must not change domain logic or event semantics.

## 2. Design Tokens (Single Source of Truth)

Use `dev.danielc.ui.theme` as the only token source:

- Colors: `app/src/main/java/dev/danielc/ui/theme/Color.kt`
- Typography: `app/src/main/java/dev/danielc/ui/theme/Type.kt`
- Shapes/Theme: `app/src/main/java/dev/danielc/ui/theme/Theme.kt`
- App background: `app/src/main/java/dev/danielc/ui/theme/AppBackground.kt`

### 2.1 Color Semantics

1. `primary`: primary actions and high-priority highlights.
2. `primaryContainer`: hero or callout surfaces.
3. `secondary`/`tertiary`: supporting emphasis (progress, secondary status).
4. `surface`: primary card background.
5. `surfaceVariant`: secondary sections and placeholders.
6. `errorContainer`: error-state surfaces.
7. `outline`: borders and separators.

Rules:

1. Never hardcode page-level colors (`Color(0x...)`) in feature UI.
2. Do not use `primary` as default body text color. Use `onSurface`/`onSurfaceVariant`.
3. Dark mode contrast must remain readable without guesswork alpha overlays.

### 2.2 Typography

Use Material3 typography (`headline*`, `title*`, `body*`, `label*`) with clear hierarchy.

Rules:

1. Page title: `titleMedium` or `titleLarge`.
2. Body copy: `bodyMedium`.
3. Supporting copy: `bodySmall` or `labelMedium`.
4. Use `FontWeight.SemiBold` only for key emphasis. Avoid global overuse of bold text.

### 2.3 Shape and Radius

Follow `MaterialTheme.shapes` from `Theme.kt`:

1. `small`: standard cards and chips.
2. `medium`: primary content containers.
3. `large`/`extraLarge`: bottom sheets or special containers.

Rules:

1. Prefer theme shapes over ad-hoc `RoundedCornerShape`.
2. Use custom corner values only when a local design requirement is explicit.

### 2.4 Spacing Scale

Base rhythm: `4/8/10/12/14/16/20.dp`

Rules:

1. Default horizontal page padding: `16.dp`.
2. Card content padding: `12.dp` to `16.dp`.
3. Vertical gaps: prefer `8.dp`, `10.dp`, or `12.dp`.
4. Avoid random spacing values that break rhythm.

## 3. Page Scaffold Standards

### 3.1 Global Shell

1. Wrap root content with `AppBackground`.
2. Set `Scaffold(containerColor = Color.Transparent)` by default.
3. Keep bottom navigation styling aligned with `app/src/main/java/dev/danielc/app/AppNavGraph.kt`.

### 3.2 Top App Bar

1. Use AutoMirrored back icons for back navigation.
2. Keep title to one line with `TextOverflow.Ellipsis`.
3. Put secondary actions in `actions`; avoid conflict with the primary CTA.

### 3.3 Required UI States

Every screen should support:

1. `Loading`: progress indicator plus readable message.
2. `Error`: clear error copy and retry action.
3. `Empty`: empty-state copy and recovery/refresh action.
4. `Success`: main content list or detail view.

## 4. Component Rules

### 4.1 Cards

1. Prefer `Card` for grouped information blocks.
2. Clickable cards must have obvious hit area (usually entire row/card).
3. Use `BorderStroke` with `outline` for subtle hierarchy separation.

### 4.2 Buttons

1. Primary action: `Button`.
2. Secondary actions: `FilledTonalButton`, `OutlinedButton`, or `TextButton`.
3. One visual primary button per local section.

### 4.3 Status Badges

1. Use capsule `Surface(shape = CircleShape)` for queue/download status.
2. Use semantic container color differences for status distinction (for example, ongoing vs completed).

### 4.4 Icons

1. Prefer `Icons.Outlined` for informational context.
2. Interactive icons must define `contentDescription`.
3. Decorative icons should use `contentDescription = null`.

## 5. Interaction and Business Boundaries

1. Do not change use case behavior from UI code.
2. Do not remove existing `testTag` constants used by tests.
3. Keep event dispatch through `onIntent(...)` and existing UI contracts.
4. Keep navigation decisions in Route/NavGraph layers.

## 6. Localization Rules

String resources:

- English: `app/src/main/res/values/strings.xml`
- Simplified Chinese: `app/src/main/res/values-zh-rCN/strings.xml`

Rules:

1. No hardcoded user-facing strings in Compose UI.
2. New strings must be added in both EN and ZH files.
3. Use placeholders (`%1$s`) for dynamic text; avoid manual string concatenation.
4. Pass `:app:verifyNoHardcodedUiStrings` before merge.

## 7. Testability Rules

1. Keep stable node IDs/tags required by UI automation.
2. Preserve identifiable state nodes (permission, connecting, error, history).
3. Layout refactors are allowed, but actionable semantics must stay discoverable.

## 8. Code Organization Rules

1. Screen entry should be `XxxScreen` with state/events injected via parameters.
2. Split complex sections into private composables.
3. Keep business logic out of composables (no direct singleton/domain operations).
4. Avoid unrelated utilities and side effects inside UI files.

## 9. Codex Execution Checklist (Required for Every UI Change)

1. Read theme tokens first and reuse existing values before adding new ones.
2. Implement layout and style updates without breaking intent/contract flow.
3. Add any new copy to both EN and ZH resource files.
4. Run:
   - `./gradlew --console=plain :app:compileDebugKotlin`
   - `./gradlew --console=plain :app:verifyNoHardcodedUiStrings`
   - `./gradlew --console=plain :app:testDebugUnitTest`
5. If semantics or node structure changed, also run related `androidTest`.

## 10. Baseline Reference Screens

Use these implementations as style anchors:

1. Connect: `app/src/main/java/dev/danielc/feature/connect/permission/WifiPermissionContent.kt`
2. Photo list: `app/src/main/java/dev/danielc/feature/photolist/PhotoListScreen.kt`
3. Preview: `app/src/main/java/dev/danielc/feature/preview/PreviewScreen.kt`
4. Settings: `app/src/main/java/dev/danielc/feature/settings/SettingsScreen.kt`

## 11. Common Violations

1. Hardcoded colors like `Color(0xFF...)` in feature UI.
2. Hardcoded strings like `Text("...")`.
3. Inconsistent corner radius and irregular spacing on the same screen.
4. Removing test tags during visual-only refactors.
5. Error states without a retry path.

## 12. Recommended Delivery Sequence

1. Minimal safe refactor (no business behavior changes).
2. Visual enhancement (hierarchy, iconography, information density).
3. Consistency pass (strings, tests, token reuse).

---

When evolving this design system, update the version at the top and include:
`new tokens / deprecated tokens / migration strategy`.
