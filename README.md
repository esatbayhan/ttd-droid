# Tasks

An Android task manager for the [todo.txt.d](https://github.com/esatbayhan/ttd-spec) plain-text format. Tasks are individual `.txt` files in a directory you control — no cloud accounts, no proprietary databases.

Built with Kotlin and Jetpack Compose. Targets Android 14+ (minSdk 34).

> **Note:** This project is developed primarily through agentic coding (AI-assisted development). While functional, it carries the usual risks of AI-generated code. Review before relying on it.

## What it does

- Browse, create, edit, and complete tasks stored as plain `.txt` files via SAF (Storage Access Framework)
- Full todo.txt syntax support: priorities `(A)`, projects `+project`, contexts `@context`, due/scheduled/starting/updated dates
- [Smart lists](https://github.com/esatbayhan/ttd-spec/blob/main/LISTS.md) — user-defined filter views with `AND`/`OR` logic, `sort by`, and `group by` directives
- Directory-based list groups for organizing smart lists in the sidebar
- Auto-generated project and context views
- Per-task swipe actions: complete (right), delete with undo (left), update timestamp
- Task editor with project/context autocomplete and smart list prefill support
- Due date and scheduled date notifications
- Home screen widget showing today's tasks

## What it doesn't do

- No cloud sync built in — use Syncthing (see below)
- No collaboration features
- No recurring/repeating tasks
- No calendar integration
- No task prioritization beyond the standard `(A)`-`(Z)` priority syntax

## Recommended sync setup

todo.txt.d is designed for conflict-free file sync. The recommended setup:

1. **[Syncthing](https://syncthing.org)** — Sync your `todo.txt.d/` directory across devices. Each task is a separate file, so independent changes on different devices never conflict.
2. **[Tailscale](https://tailscale.com)** (optional) — Secure mesh VPN for Syncthing when devices aren't on the same network. Install Tailscale on each device and point Syncthing at the Tailscale IPs.

Set your Syncthing folder path, then point Tasks to that directory on first launch via the SAF directory picker.

Tasks is not affiliated with Syncthing or Tailscale.

## Related projects

- [todo.txt.d specification](https://github.com/esatbayhan/ttd-spec) — The format spec this app implements (v3.0.0)
- [ttd-rs](https://github.com/esatbayhan/ttd-rs) — Terminal UI for desktops, same format
- [Syncthing](https://syncthing.net/) — Continuous file synchronization
- [Tailscale](https://tailscale.com/) — Zero-config VPN for secure device connectivity

## Build from source

### Prerequisites

- Android Studio (Hedgehog or later)
- JDK 17+

### Command line

```bash
# Set JDK path if needed
export JAVA_HOME=/opt/android-studio/jbr

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run lint
./gradlew lint
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Android Studio

Open this directory as a project, wait for Gradle sync, then **Run > Run 'app'**.

## License

[GPL-3.0](LICENSE) — same as the specification this project implements.

---

**Disclaimer:** This software is provided "as is", without warranty of any kind. The author is not responsible for lost tasks, missed deadlines, or any other consequences of using this software. Always back up your `todo.txt.d/` directory.
