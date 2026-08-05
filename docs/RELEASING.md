# Releasing CARAKA

GitHub Actions builds and signs release APKs with the `release` environment. The signing key and
passwords are stored as GitHub Environment Secrets and are never committed to the repository.

## Required environment secrets

The `release` environment must provide:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The original keystore remains the recovery source of truth. Keep an offline backup because GitHub
Secrets cannot be downloaded after they are saved.

## Verify the workflow without publishing

Run the workflow manually from `main`. This produces a signed APK artifact but does not create a
GitHub Release:

```powershell
gh workflow run release.yml --ref main
gh run watch
```

## Publish a release from any development machine

1. Update `versionCode` and `versionName` in `app/app/build.gradle.kts`.
2. Commit and push the version bump to `main`.
3. Create an annotated tag equal to `v` plus `versionName`.
4. Push the tag.

For example, when `versionName` is `0.2.2-beta`:

```powershell
git tag -a v0.2.2-beta -m "Caraka v0.2.2-beta"
git push origin main
git push origin v0.2.2-beta
```

The tag workflow runs unit tests, builds and verifies the signed APK, stores a short-lived Actions
artifact, and publishes the APK plus its SHA-256 checksum to GitHub Releases.

The release workflow rejects a tag when it does not match the committed `versionName`.
