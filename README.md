# Steadfast: screen time, content blocking & accountability for Android

Free, no ads, no subscriptions, no account. Everything is enforced on the phone itself.
Your accountability partner learns **that** something happened, never **what** it was.

---

## What it does

| Feature | How it works |
|---|---|
| **Block apps** | Pick any app and block it completely |
| **Daily time limits** | e.g. YouTube 30 min/day. Measured from Android's own usage data |
| **Allowed hours** | e.g. Instagram only 18:00–20:00 |
| **Downtime** | e.g. 22:00–07:00 only "essential" apps open (phone, keyboard and home screen are never blocked) |
| **Block websites** | Add `example.com` and every subdomain is blocked too (`www.`, `m.`, `anything.`). Add `news.example.com` to block just that subdomain |
| **Always-allowed sites** | Exceptions for things a keyword wrongly catches |
| **Adult-content filter** | Free family DNS (Cloudflare for Families or CleanBrowsing) blocks adult & malicious sites by category |
| **SafeSearch** | Forced on Google, Bing, DuckDuckGo, plus YouTube Restricted Mode (filters explicit images in search) |
| **Keyword filter** | Blocks web addresses, pages and app screens that show your keywords, including image descriptions/alt text. Whole-word matching, so "sex" won't block "Essex" |
| **Search filter** | Blocks searches and typing containing your terms in **any** app (Google, YouTube, TikTok, Instagram…) and clears the search box |
| **Master PIN** | Needed to change any setting. Ideally your partner chooses it |
| **NFC lock** | Register a cheap NFC sticker. Tap it to lock (settings frozen, optional "essential apps only" lockdown) and tap again to unlock |
| **Accountability partner** | Instant alerts when a block happens or protection is tampered with or loosened. Daily report of screen time and counts. Remote pause / resume / unlock. Optional "partner must approve" for loosening anything |
| **Tamper protection** | Can't be uninstalled without alerting your partner (device admin). Steadfast's own system-settings pages are blocked. VPN/accessibility being switched off triggers an alert. Browser "secure DNS" bypasses are blocked |
| **Block screen** | Shows a short Bible verse (World English Bible, public domain). Can be turned off |

### Privacy: what the partner sees
- ✅ "Tried to open a website on the blocked list", "A search matching the search filter was blocked", "The web filter was switched off", "Restrictions loosened: removed a keyword"
- ✅ Daily: total screen time, number of blocks by type, tamper alerts. App names only if you switch that on
- ❌ Never the website, the search words, the keyword that matched, or what you were watching. Those stay in the private log on your phone (behind your PIN)

Messages travel through **ntfy.sh**, a free open-source push service, on random private channel names only you and your partner know. Remote commands are cryptographically signed, so nobody else can send them.

---

## Get the app onto your phone (about 15 minutes, all free)

You can't download a finished APK from me directly, so GitHub's free build servers turn this code into one for you.

### 1. Put the code on GitHub
1. Go to **github.com** on your PC and create a free account (or sign in).
2. Click **+** (top right) → **New repository**. Name it `steadfast`, choose **Private**, and click **Create repository**.
3. On the next page click **"uploading an existing file"**.
4. Unzip `steadfast-android.zip` on your PC. Open the `steadfast-android` folder, select **everything inside it** and drag it into the GitHub page. Wait for the upload, then click **Commit changes**.

### 2. Make sure the build recipe is there
Some browsers skip folders whose names start with a dot. On your repository page, check whether a `.github` folder appears.
- **If it doesn't:** click **Add file → Create new file**, type the name `.github/workflows/build.yml` (the slashes create the folders), open `build.yml` from the unzipped folder in Notepad, copy everything into the GitHub editor, and click **Commit changes**.

### 3. Let GitHub build it
1. Click the **Actions** tab. If asked, click **"I understand my workflows, go ahead and enable them"**.
2. You'll see **Build Steadfast APK** running (if not: click it on the left → **Run workflow**). It takes about 5 minutes and should end with a green tick ✔.

### 4. Install on your Android phone
1. On your phone, open **github.com** in Chrome, sign in, open your `steadfast` repository and tap **Releases** (or go to `github.com/YOUR-NAME/steadfast/releases`).
2. Tap **Steadfast.apk** to download it, then open it.
3. If asked, allow **"Install unknown apps"** for Chrome. If Play Protect warns about an unknown app, tap **More details → Install anyway** (the warning appears because you built the app yourself and it isn't from the Play Store).

> **Updates:** whenever the code changes, GitHub builds a new release. Install it over the old one and your settings are kept.
> **2027 note:** Google plans to require "verified developers" for sideloaded apps worldwide in 2027. It's already in force in Brazil, Indonesia, Singapore and Thailand. Google says power users will keep an "advanced flow" for installing their own apps, and there's a free limited-distribution account for hobbyists.

---

## Set up Steadfast (protected phone)

Open Steadfast → **Protect this phone** and follow the checklist:

1. **Master PIN.** Best if your accountability partner types it and keeps it.
2. **Usage access** → turn on for Steadfast.
3. **Accessibility** → Steadfast → On.
   *Android 13+ may say "Restricted setting".* Tap **Open App info** in Steadfast → **⋮** (top right) → **Allow restricted settings** → go back and turn it on.
4. **Start web filter** → OK (a key 🔑 icon appears; it's an on-phone filter, not a real VPN to a server).
5. **Uninstall protection** → Activate.
6. **Notifications** → Allow.
7. **Background running** → Allow.
8. *(Recommended)* **VPN settings** → ⚙ next to Steadfast → **Always-on VPN: ON**. Leave **"Block connections without VPN" OFF**.

Then **Finish setup**. A starter keyword list and search list are added. Review them under *Keyword filter* / *Search filter*.

### Connect your accountability partner
Steadfast → unlock → **Accountability partner** → **Set up accountability** → **Share pairing code** and send it to your partner privately.

**Partner with Android:** install Steadfast the same way → **I'm an accountability partner** → **Add device** → paste the code. They get notifications, reports, the approval code, and remote buttons (pause, resume, unlock NFC, lock settings, require approval).

**Partner with an iPhone:**
1. Install the free **ntfy** app → **+** → subscribe to the **topic** shown on your Accountability screen (server `ntfy.sh`). They now get alerts and daily reports instantly.
2. In **Google Authenticator** or **Microsoft Authenticator**: **+** → **Enter a setup key** → name "Steadfast", paste the **key**, time-based. The 6-digit code there is the **partner code** you'll need to loosen settings or unlock.
   (Remote pause/unlock buttons need the Android partner app. An iPhone partner gives you the 6-digit code instead.)

**Recommended:** turn on **Partner approval to loosen settings**. Then removing a rule, pausing, lowering the filter, etc. needs your partner's live code, not just the PIN.

### NFC lock
Buy any NTAG213/215 NFC stickers or cards (a few pounds for a pack). Steadfast → **NFC lock** → **Register a new tag** → hold it to the back of the phone.
- Tap the tag (even with Steadfast closed) → **Locked**: nothing can be loosened. Turn on **Lockdown** to also limit you to essential apps.
- Tap again → **Unlocked**.
- Lost the tag? Your partner can unlock you remotely or give you their code.

Ideas: stick the tag somewhere inconvenient (the garage, your car), or give it to your partner or spouse.

---

## Honest limits

- **Pictures without text can't be "read".** Explicit images are handled by the family DNS filter and SafeSearch, and pages are caught by keywords in their text, descriptions and addresses. No filter is perfect, which is why the accountability part matters.
- Someone determined with full control of the phone can get around any app, for example with a factory reset or safe mode. Steadfast makes that **noticeable**: the partner sees missing daily reports ("no report for 36h+: check in") and tamper alerts.
- **Private DNS:** Android's Settings → Private DNS page is blocked while protected, and common secure-DNS providers are blocked so browsers can't bypass the filter.
- Free ntfy.sh keeps undelivered messages for about 12 hours. Alerts queue on the phone while it's offline and send when it reconnects.
- Steadfast's keyword scanning runs entirely on the phone. Nothing it reads is stored or sent, apart from the type of each block.

## Building it yourself (optional)
Open the folder in **Android Studio** → Run. Or from a terminal: `./gradlew assembleRelease`. The APK is written to `app/build/outputs/apk/release/`.

The signing key in `keystore/` was generated just for you. Keep your repository **private**, and keep the same key so updates install over the top of each other.

## How it's built
- 100% Java on Android's built-in libraries (no third-party code), minSdk 26 (Android 8.0+), targetSdk 34.
- `FilterVpnService`: local DNS-only VPN that blocks domains, subdomains, keyword domains and DoH resolvers, rewrites SafeSearch, and forwards everything else to the chosen family resolver.
- `GuardAccessibilityService`: app rules, limits, downtime, lockdown, keyword/search filtering, browser URL checks, settings protection.
- `Partner` / `PartnerStore` / `Reports`: ntfy alerts, HMAC-signed commands, TOTP partner codes (RFC 6238), daily reports.
- `NfcActivity`: tag registration (hashed UID + NDEF link), lock and unlock.
