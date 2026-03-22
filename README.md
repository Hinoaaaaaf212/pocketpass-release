# PocketPass

A StreetPass-inspired social app for Android. Meet people nearby over Bluetooth, collect Mii inspired avatars called Piips, play mini-games, and build your plaza, just like how it was on the 3DS but on your phone!

Built for both phones and dual-screen devices (Ayn Thor), with more dual screen devices getting support soon.

## Features

**Nearby Encounters**
- BLE-based proximity detection, no need to exchange info
- Encrypted handshake ensures data stays private
- Encounter history with meet counts and timestamps

**Plaza**
- Profile cards with Mii inspired avatars called Piips, greetings, moods, and hobbies
- Swipeable card view for browsing encounters
- 3D animated plaza with walking Miis

**Mini-Games**
- **Puzzle Swap**: collect pieces from encounters, complete themed panels
- **Piip Bingo**: bingo card with encounter-based challenges
- **Shop**: spend tokens on card borders, hats, and costumes

**WaveLink (SpotPass)**
- Server-pushed content: new puzzle panels, timed events, special greetings
- Event effects: token multipliers, drop rate boosts, shop discounts
- New events sync in the background and you get notified with your joystick LEDs! (AYN Thor only)

**Social**
- Friends list with chat messaging and streak tracking
- Global leaderboard
- Achievements and statistics with world tour map

**Customization**
- Mii creator (WebView-based editor)
- Hat options for your Mii
- Card border themes from the shop
- Dark mode with Aero glass UI

## Dual-Screen Support

On dual-screen devices like the Ayn Thor, PocketPass uses both displays. The top screen shows the main content while the bottom screen shows contextual info, nav controls, and the 3D plaza companion view.

## Installation

1. Download the latest APK from the [Releases](https://github.com/Hinoaaaaaf212/pocketpass-release/releases) page
2. Enable "Install from unknown sources" for your browser
3. Install and open, the app will guide you through permissions and profile setup

## Building from Source

```
git clone https://github.com/Hinoaaaaaf212/pocketpass-release.git
cd pocketpass-release
```

Requires Android Studio with AGP 9.1+ and Kotlin 2.2+. Native keys are injected at build time via Gradle. You'll need to configure your own Supabase instance and update the key generation in `build.gradle.kts`.

## Stack

- Kotlin / Jetpack Compose
- Supabase (auth, database, storage, realtime)
- SceneView 2.2.1 (Filament) for 3D rendering
- Room for local persistence
- DataStore for preferences
- Coil for image loading

## Credits

- **datkat21**: Mii creator and lead developer
- **ariankordi**: Mii rendering API

## License

This project is licensed under [AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html). You can view, use, and modify the source code, but any distribution or deployment must also be open-sourced under the same license.
