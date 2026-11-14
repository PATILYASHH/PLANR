# Release signing

Every PLANR release is signed with the **ExoSynk Labs release key**. Android only
installs an update over an existing PLANR when both are signed with the same key,
so this key must never change or be lost.

## One-time setup

1. On your own computer (needs a JDK and `openssl`):

   ```bash
   ./scripts/create-release-key.sh            # creates planr-release.jks
   ```

2. Add the four values it prints as repository secrets
   (**Settings → Secrets and variables → Actions → New repository secret**):

   | Secret | Value |
   |---|---|
   | `PLANR_KEYSTORE_BASE64` | contents of `planr-release.jks.base64.txt` |
   | `PLANR_KEYSTORE_PASSWORD` | the printed password |
   | `PLANR_KEY_ALIAS` | `exosynk-planr` |
   | `PLANR_KEY_PASSWORD` | the printed password (same as above) |

3. **Back up `planr-release.jks` and the password offline**, e.g. on two USB
   drives and in a password manager. Then delete `planr-release.jks.base64.txt`.
   Never commit either file. `.gitignore` already blocks `*.jks` and `*.base64.txt`.

4. Record the certificate **SHA-256** the script prints. ExoSynk Link
   ([exosynk-link.md](exosynk-link.md)) uses it so XPENC, FITNS and CONECTR can
   recognise PLANR.

## How builds are signed

- **GitHub releases** (`.github/workflows/release.yml`): signed with the key from the
  secrets. If a secret is missing or the password is wrong, the workflow **fails**.
  It never publishes a build signed with anything else.
- **Local `assembleRelease`** without the `PLANR_KEYSTORE_*` environment variables
  produces an **unsigned** APK. Use `assembleDebug` for testing on your phone.
- **F-Droid** builds from source and signs with **its own** key, so it needs the
  unsigned output above.
- **Google Play**: use Play App Signing and upload with this key as the *upload
  key*. Play then re-signs with its own key.

Each channel's APK is therefore signed differently. That's normal: users move
between GitHub, F-Droid and Play with **Backup → Export**, uninstall, reinstall,
**Restore**.

## Moving from the old preview builds

PLANR 0.2.0 – 0.5.2 were signed with throwaway CI debug keys, a different one on
every release. The first build signed with the release key can't update over them,
so ask testers once to export a backup, uninstall, install the new build and restore.
