# GreatcatNote Mobile

GreatcatNote Mobile is the personal Android companion to the GreatcatNote desktop fork. It intentionally focuses on a small offline-first loop:

- read Markdown and PDF files from Android's document picker;
- clone an HTTPS Git repository into the app-private vault;
- fetch and rebase only Git changes after the first clone;
- import Markdown/PDF files into `imports/`, then commit and push them on the next sync;
- keep the Git token encrypted by Android Keystore.

## Use

Install the debug APK, open **仓库设置**, and enter an HTTPS repository URL, branch, username, and personal access token. For GitHub, use a fine-grained token limited to the single notes repository with Contents read/write permission.

The mobile vault is isolated from the desktop vault. Git is the synchronization boundary. If a rebase conflict occurs, the app aborts the rebase and asks you to resolve it on the desktop instead of guessing and risking note loss.

## Build

The repository workflow `greatcatnote-android.yml` runs unit tests and produces `GreatcatNote-Mobile-debug.apk`. Android Studio can also open this directory directly; it requires JDK 17 and Android SDK 35.

This project is part of the AGPL-3.0-or-later GreatcatNote source tree. See `THIRD_PARTY_NOTICES.md` for bundled library notices.
