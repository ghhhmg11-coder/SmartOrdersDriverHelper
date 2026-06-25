# Smart Orders Driver Helper

Auto-accept Jeeny Driver trip requests based on your rules.

## Setup

### Option 1 — GitHub Actions (recommended, no Android Studio needed)
1. Create a new GitHub repository
2. Upload this entire folder to it
3. Go to **Actions → Build APK → Run workflow**
4. Download the APK from the **Artifacts** section when the build completes

### Option 2 — Android Studio
1. Open this folder in Android Studio (File → Open)
2. Wait for Gradle sync to finish
3. Click **Build → Build Bundle(s)/APK(s) → Build APK(s)**
4. APK is at: `app/build/outputs/apk/debug/app-debug.apk`

### Option 3 — Command line (Linux/Mac, requires JDK 17+)
```bash
chmod +x gradlew
./gradlew assembleDebug
```

## First-run setup on device
1. Install the APK
2. Open **Smart Orders**
3. Go to **Settings → Accessibility Service** → enable **Smart Orders Auto Accept**
4. Go to **Settings → Overlay Permission** → allow drawing over apps
5. Tap **Start Floating Overlay** to show the floating button
6. Set your rules in the **Rules** tab
7. Tap **START** on the floating button

## How the Accessibility Service works
- Reads **all** screen windows (not just Jeeny) every accessibility event
- Detects Jeeny trip screens using Arabic markers: قبول العرض, يبعد, مشوار داخل المدينة, استريح, ﷼
- Parses price, pickup minutes, and pickup distance from the Arabic text
- Compares values against your saved rules
- If rules pass: clicks "قبول العرض" via node ACTION_CLICK → parent node fallback → gesture tap fallback
- **Force Accept** button in the floating panel tests gesture tapping at screen bottom-center

## Packages monitored
- `com.jeeny.driver`
- `com.jeeny.drivers`

## Debug screen
Shows everything the service sees: last package, event type, detected text, detection reason, and click result. Use this to verify the service is seeing Jeeny events.
