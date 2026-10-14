# Releases

1. Update versionCode/versionName in app/build.gradle, diagnostic version text,
   README validation notes, and fastlane/metadata/android/en-US/changelogs/.
2. Run ./gradlew lintDebug lintRelease assembleDebug assembleRelease using JDK 17.
3. Configure the ignored keystore.properties for signed maintainer releases.
   Back up the keystore and credentials securely. Future GitHub updates must use
   the same key. Never publish signing material.
4. Verify the APK with Android SDK apksigner verify --verbose --print-certs.
   Check that the release manifest excludes the debug instrumentation probe.
5. Commit and tag source as v<versionName>. Publish the signed APK built from that
   commit and its SHA-256 checksum. Include the certificate SHA-256 fingerprint,
   device validation, and outstanding limitations in the release notes.

CI builds unsigned releases without signing secrets. F-Droid submission and build
metadata are a separate follow-up. Store descriptions are in fastlane/metadata/android/en-US/.
