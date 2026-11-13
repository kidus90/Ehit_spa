# Ehit Spa – Android APK

Wraps the Ehit Spa web app (`index.html`) with Capacitor and builds an installable APK using GitHub Actions.

## Project structure

```
ehit-spa-apk/
├── .github/
│   └── workflows/
│       └── build-apk.yml      # GitHub Actions workflow that builds the APK
├── .gitignore
├── capacitor.config.json      # App id, app name, web folder
├── package.json               # Capacitor dependencies
├── index.html                 # The Ehit Spa app (unchanged)
├── assets/
│   └── icon.png               # App icon (replace this to change the icon)
├── scripts/
│   └── make_icons.py          # Builds all Android icon sizes from assets/icon.png
└── README.md
```

Optional: put `sw.js` next to `index.html` and the workflow will include it.

## How to build the APK

1. Create a new GitHub repository.
2. Upload everything in this folder, keeping the same layout
   (`build-apk.yml` must stay inside `.github/workflows/`).
3. Push to the `main` branch. The workflow starts automatically.
   You can also run it manually: Actions tab > Build APK > Run workflow.
4. When it finishes (about 5-8 minutes), open the run and download the
   **ehit-spa-apk** artifact. It is a zip containing `app-debug.apk`.
5. Copy the APK to your phone and install it
   (Android will ask you to allow installs from unknown sources).

## Changing the app icon

Replace `assets/icon.png` with your own square PNG (1024x1024 is ideal) and push.
The workflow generates every Android icon size from it automatically.
Keep the important part of the design near the center, because Android
may crop the edges into a circle or rounded square.

## Notes

- This builds a debug APK, which installs on any phone.
  Publishing to the Play Store needs a signed release build.
- The app still needs internet to load Supabase and fonts.
