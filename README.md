# Arcoon

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-8.0+-green.svg)](https://developer.android.com/about/versions/oreo)

**An AI-modified, unofficial merge of [Argosy Launcher](https://github.com/nendotools/argosy-launcher) and [Cocoon Shell](https://github.com/inssekt/CocoonFE): Cocoon's look, feel and sound, on top of Argosy's open-source engine.**

> [!IMPORTANT]
> **What Arcoon is, and is not**
>
> - Arcoon is a **personal fork** of Argosy Launcher, rebuilt to look, move and sound like Cocoon Shell while keeping everything Argosy does.
> - It was **modified with the help of AI** (Anthropic's Claude, working through Claude Code), directed and tested by hand on real hardware. Expect rough edges.
> - It is **not affiliated with, endorsed by, or supported by** the Argosy or Cocoon teams. Please don't report Arcoon bugs to them.
> - If you want the real thing, use the originals: they are actively developed by the people who designed them.

## Credits

Arcoon only exists because of the work of two teams. All credit for the ideas below goes to them.

### Argosy Launcher, by nendotools

[github.com/nendotools/argosy-launcher](https://github.com/nendotools/argosy-launcher) · [Wiki](https://github.com/rommapp/argosy-launcher/wiki)

The foundation of Arcoon: the whole app engine, released under the **GNU GPL v3**. Native [RomM](https://github.com/rommapp/romm) integration, downloads, save sync, RetroAchievements, the built-in emulator, emulator detection, dual-screen support, social features, collections, the settings system and much more are Argosy's work. Arcoon is a derivative of Argosy and keeps its license.

### Cocoon Shell, by inssekt

[cocoon-shell.com](https://cocoon-shell.com) · [github.com/inssekt/CocoonFE](https://github.com/inssekt/CocoonFE) · [Discord](https://discord.gg/cocoon)

The design Arcoon imitates: the home grid and folders, the motion and cursor, the sound and haptic language, the Silk theme store, the hero game pages, the corner pills, the Settings layout and the Steam friends chat. Cocoon's visual design, icons, sounds and names belong to its creators. **Cocoon is not open source**, and nothing in this repository changes or relicenses Cocoon's own work.

### Also built on

- [RomM](https://github.com/rommapp/romm), the self-hosted game library server Argosy talks to.
- [JavaSteam](https://github.com/Longi94/JavaSteam), the open-source Steam client library used for Steam sign-in, friends and chat.
- The libretro, RetroArch and RetroAchievements projects, and every emulator Arcoon can launch.

## Why Arcoon

### What it adds over Argosy

- **Cocoon's interface.** The home screen, folders and smart folders, dock, bracket cursor, page and folder motion, navigation sounds and haptics follow Cocoon 3.11 instead of Argosy's own design.
- **Silk theme store.** Cocoon-style themes, sound packs, music and jingles, applied live.
- **Cocoon hero pages.** The upper screen shows the focused game's art and logo, with a layer bar for info, achievements and journey, and matching corner pills for status and friends.
- **Settings that never swap screens.** On dual-screen devices, Settings opens on the upper screen while the lower screen shows live, moving previews of each section, with no screen swap or flicker.
- **Quick menus that grow out of their pill.** R1 and L1 open the quick settings and friends menus by morphing the status and friends pills in place, the way Cocoon does.
- **Steam friends and chat** *(in progress)*. Sign in with a QR code, see which Steam friends are online and what they are playing, and chat with them, with the chat on the upper screen and the keyboard on the lower one.
- **Dual-screen polish.** Fixes for the upper screen losing the highlighted game after a screen swap, a steadier hand-off between screens, and a faster game details page (fewer duplicate server requests, no needless redraws of the upper screen).

### What it adds over Cocoon

- **Open source.** Arcoon is GPL v3, like Argosy, so its full source is public and can be studied, changed and shared.
- **Native RomM client.** Sync your whole library from your own RomM server, with metadata, cover art and collections.
- **Downloads on demand.** Fetch games and BIOS files from RomM, sorted by platform, with a download queue.
- **Save sync.** Two-way save sync with RomM, with conflict detection, so you can continue on another device.
- **RetroAchievements**, including hardcore rules, from Argosy's integration.
- **Built-in emulation.** Argosy's libretro-based player runs many systems inside the app, with game suspend and resume.
- **Argosy's library tools.** Smart collections, filtering by genre, players, franchise or region, ratings synced to RomM, emulator auto-detection and per-platform RetroArch cores, multi-disc handling, and in-app updates.

## Status

Arcoon is a **pre-release** project developed and tested mainly on the **AYN Thor** (dual screen). Other Android handhelds should work, since the engine is Argosy's, but they get less testing. Some features above are marked in progress; expect bugs.

## Getting started

1. Download the latest APK from [Arcoon releases](https://github.com/manu0623/Arcoon-releases/releases/latest).
2. Install it on your device.
3. Run it and follow the setup wizard.
4. Connect to your RomM server, or skip it for local-only use.
5. Sync your library and start playing.

> New to RomM? Set up your self-hosted game library at [github.com/rommapp/romm](https://github.com/rommapp/romm).

### Requirements

- Android 8.0 (Oreo) or higher
- Emulators installed for the platforms you want, or the built-in player
- A [RomM](https://github.com/rommapp/romm) server (optional, for sync features)

## Feature reference

Most of Arcoon's features come straight from Argosy; the [Argosy wiki](https://github.com/rommapp/argosy-launcher/wiki) is the best reference for setup, emulator configuration and RomM. Where Arcoon differs, it follows Cocoon's design; the [Cocoon wiki](https://cocoon-shell.com/wiki/) describes that design.

## Reporting problems

Please report Arcoon issues here, at [github.com/manu0623/Arcoon](https://github.com/manu0623/Arcoon/issues), **not** to the Argosy or Cocoon teams. If a problem also happens in the original app, report it to that project.

## License

Arcoon is licensed under the **GNU General Public License v3.0**, inherited from Argosy Launcher; see [LICENSE](LICENSE). That license covers this repository's code. It does not grant any rights to Cocoon Shell's own design, artwork, sounds or trademarks, which remain the property of their creators.
