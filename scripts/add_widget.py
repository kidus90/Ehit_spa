"""Adds the Ehit Spa home-screen widget to the generated Capacitor Android project.

Run after `npx cap add android` (the android/ folder is rebuilt on every build, so the
native widget files live in android-widget/ and are copied in here).
"""
import pathlib
import shutil
import sys

root = pathlib.Path(__file__).resolve().parent.parent
src = root / "android-widget"
app = root / "android" / "app" / "src" / "main"
pkg_dir = app / "java" / "com" / "ehit" / "spa"

if not app.exists():
    sys.exit("android project not found - run `npx cap add android` first")

pkg_dir.mkdir(parents=True, exist_ok=True)
for f in (src / "java").glob("*.java"):
    shutil.copy(f, pkg_dir / f.name)

for d in ("layout", "xml", "drawable", "values"):
    (app / "res" / d).mkdir(parents=True, exist_ok=True)
    for f in (src / "res" / d).iterdir():
        shutil.copy(f, app / "res" / d / f.name)

manifest = app / "AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
if "EhitWidgetProvider" not in text:
    receiver = """
        <receiver android:name=".EhitWidgetProvider" android:exported="false" android:label="Ehit Spa">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/ehit_widget_info" />
        </receiver>
"""
    if "</application>" not in text:
        sys.exit("could not find </application> in AndroidManifest.xml")
    text = text.replace("</application>", receiver + "    </application>", 1)
    manifest.write_text(text, encoding="utf-8")

print("Widget files added to the Android project")
