# AntennaPod Personal

Personal build of AntennaPod with episode description previews, maintained on the `personal` branch
on top of the latest official release.

## Identity and signing
- Package name `de.danoeh.antennapod.personal` (debug build type), app name "AntennaPod Personal".
- Signed with the permanent key from the `DEBUG_KEYSTORE_BASE64` repository secret
  (SHA-256 `c931193d7d7bbee380ab616aa768c81256bdaefcc54419fb91b38d2af83a9313`).
  CI fails if the key is missing or different. Never change the key or the package name:
  installed copies could no longer be updated in place.
- Unofficial package names hide the "About" section in settings (upstream license notice), so
  `AboutFragmentTest` is skipped on this build.

## Releases
- Every push to the `personal` pull request runs all checks. Only when all of them pass, the APK is
  published as a GitHub release `personal-<version>-<commit>`. Install from the latest release.
- The upgrade test installs the latest release, then updates it to the new build and checks that the
  app data survives and the app starts. It fails if there is no previous build to upgrade from.

## Updating to a new official release
1. Rebase the personal commits onto the new release tag.
2. Database versions: personal schema steps use 3130000-3130002 in `DBUpgrader`.
   Any new upstream step whose version is lower than or equal to the current personal version
   (`PodDBAdapter.VERSION`) would be skipped for existing personal databases. For each such step,
   add a new personal step with a higher version that performs the same change, guarded so that it
   is safe to run twice (for example `hasColumn` before `ALTER TABLE`). Keep `PodDBAdapter.VERSION`
   at the highest of upstream and personal versions.
3. `feeditemlist_item_preview.xml` is based on `feeditemlist_item.xml`: carry over upstream changes
   to the episode row.
4. Push and wait for all checks, including the upgrade test against the previous release.

## One-time migration from AntennaPod Debug
`personalImportTest.sh` tests moving from the old `de.danoeh.antennapod.debug` build (database
export and import, then automatic re-download of episodes). It uses an old APK from CI run
39549751288, which expires in January 2027. Remove the test and the `import-test` job once the
migration is done.
