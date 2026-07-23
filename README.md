# GreatcatNote

GreatcatNote is a personal fork of [Tolaria](https://github.com/refactoringhq/tolaria), a files-first desktop knowledge-base app built with Tauri, React, and TypeScript.

This fork keeps Tolaria's core idea: notes stay as ordinary files, Git stays available, and the app should remain offline-first. The reason for the fork is simple: I want one local knowledge app that can treat Markdown, HTML, and Excalidraw drawings as first-class knowledge artifacts.

## Why this fork exists

Tolaria is already a strong base for a Markdown knowledge base. GreatcatNote adds a smaller, personal-product layer on top:

- Native app name, bundle id, and deep link scheme changed to `GreatcatNote`, `com.greatcat.note`, and `greatcatnote://`.
- `.html` files can be opened and viewed inside the app.
- `.excalidraw` and `.excalidraw.json` files can be opened from the vault instead of appearing unavailable.
- Excalidraw editing is embedded with the official `@excalidraw/excalidraw` React package.
- Excalidraw's bundled fonts are shipped locally so drawings keep the handwritten style offline.
- The note list includes a direct New Drawing action that creates a valid `.excalidraw` file.
- Drawing edits warn before leaving the file or closing the window when unsaved.

## Build locally

```bash
pnpm install
CI=true pnpm tauri build
```

The macOS `.app` is produced at:

```text
src-tauri/target/release/bundle/macos/GreatcatNote.app
```

On this fork, the DMG bundling step can fail while the `.app` has already been built. For local use, zip the `.app` bundle and publish that artifact.

```bash
cd src-tauri/target/release/bundle/macos
ditto -c -k --sequesterRsrc --keepParent GreatcatNote.app GreatcatNote-macOS-aarch64.zip
```

## Keep up with official Tolaria

Keep two remotes:

```bash
git remote add upstream https://github.com/refactoringhq/tolaria.git
git remote set-url origin git@github.com:czy1999/GreatcatNote.git
```

Update from official Tolaria:

```bash
git fetch upstream
git checkout greatcatnote
git merge upstream/main
pnpm install
CI=true pnpm tauri build
```

If conflicts happen, protect the fork-specific seams first:

- `src-tauri/tauri.conf.json` for app identity and bundle settings.
- `src/components/ExcalidrawFilePreview.tsx` for embedded Excalidraw editing.
- `src/utils/filePreview.ts`, `src-tauri/src/vault/mod.rs`, and `src-tauri/src/vault/rename.rs` for file support.
- `index.html` and `public/fonts/` for Excalidraw font loading.
- `src/components/note-list/*` and `src/hooks/useNoteCreation.ts` for New Drawing creation.

After each upstream merge, create a fresh local build and open one Markdown file, one HTML file, and one Excalidraw file before releasing.

## Release checklist

```bash
CI=true pnpm tauri build
cd src-tauri/target/release/bundle/macos
ditto -c -k --sequesterRsrc --keepParent GreatcatNote.app GreatcatNote-macOS-aarch64.zip
gh release create v0.1.0 GreatcatNote-macOS-aarch64.zip --title "GreatcatNote v0.1.0" --notes-file RELEASE_NOTES.md
```

## License and upstream credit

GreatcatNote is based on Tolaria and keeps the original AGPL-3.0-or-later license. Tolaria's name and logo remain covered by the upstream project's trademark policy.
