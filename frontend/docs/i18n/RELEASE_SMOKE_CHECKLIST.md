# I18N Release Smoke Checklist

## Functional checks
- Launch app in default environment and verify UI language is English.
- Open Settings -> Language, switch to Simplified Chinese, and verify text updates immediately.
- Kill and relaunch the app; verify selected language persists.
- Set language to Follow system; verify app follows device language.

## Key page checks
- Connect page: scan states, permission errors, retry/error messages.
- Photo list page: loading, empty state, queue badges, item metadata labels.
- Preview page: loading, error state, detail sheet labels, download button states.
- Settings page: language/help/about/feedback/export labels.
- Notification text: download channel/title/progress content.

## Regression checks
- Run full unit tests with `./gradlew --console=plain test`.
- Run hardcoded text guard `./gradlew --console=plain :app:verifyNoHardcodedUiStrings`.
- Verify EN/ZH key parity for `app` and `core` string files.
