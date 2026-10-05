#!/usr/bin/env python3
"""Build a Kiosk Satellite plugin ZIP with SDK 1 and Android D8."""
import argparse, hashlib, json, os, re, subprocess, tempfile, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def android_platform(sdk_root, requested):
    jar = Path(sdk_root) / "platforms" / f"android-{requested}" / "android.jar"
    if not jar.is_file():
        raise SystemExit(f"Install Android platform android-{requested}.")
    return jar

parser = argparse.ArgumentParser()
parser.add_argument("--android-platform", default="35")
parser.add_argument(
    "--project",
    default=".",
    help="Plugin project directory relative to repository root (default: root plugin).",
)
args = parser.parse_args()

project = (ROOT / args.project).resolve()
if ROOT != project and ROOT not in project.parents:
    raise SystemExit("Project must live inside the repository.")
manifest_path = project / "kiosk-satellite-plugin.json"
source_root = project / "src"
if not manifest_path.is_file():
    raise SystemExit(f"Missing plugin manifest: {manifest_path}")
if not source_root.is_dir():
    raise SystemExit(f"Missing plugin source directory: {source_root}")

manifest_bytes = manifest_path.read_bytes()
manifest = json.loads(manifest_bytes)
sdk_root = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "android-sdk"))))
platform = android_platform(sdk_root, args.android_platform)

build_tools = sorted((sdk_root / "build-tools").glob("*/d8"),
                     key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.parent.name)))
if not build_tools:
    raise SystemExit("Install Android build-tools (for example 35.0.0).")
d8 = build_tools[-1]

out = ROOT / "dist"
out.mkdir(exist_ok=True)

with tempfile.TemporaryDirectory(prefix=f'ks-{manifest["id"]}-') as td:
    td = Path(td)
    sdk_classes = td / "sdk"
    classes = td / "classes"
    dex = td / "dex"
    sdk_classes.mkdir(); classes.mkdir(); dex.mkdir()

    subprocess.run(["javac", "--release", "8", "-d", str(sdk_classes),
                    *map(str, sorted((ROOT / "sdk/src").rglob("*.java")))], check=True)
    sdk_jar = td / "sdk.jar"
    subprocess.run(["jar", "cf", str(sdk_jar), "-C", str(sdk_classes), "."], check=True)

    subprocess.run(["javac", "--release", "8", "-cp", os.pathsep.join([str(sdk_jar), str(platform)]),
                    "-d", str(classes),
                    *map(str, sorted(source_root.rglob("*.java")))], check=True)

    subprocess.run([str(d8), "--min-api", str(manifest["minAndroidSdk"]),
                    "--lib", str(platform), "--classpath", str(sdk_jar),
                    "--output", str(dex),
                    *map(str, sorted(classes.rglob("*.class")))], check=True)

    plugin_jar = td / "plugin.jar"
    with zipfile.ZipFile(plugin_jar, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted(dex.glob("*.dex")):
            z.write(f, f.name)

    package = out / f'{manifest["id"]}-{manifest["version"]}.zip'
    with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("kiosk-satellite-plugin.json", manifest_bytes)
        z.write(plugin_jar, "plugin.jar")
        z.write(ROOT / "LICENSE", "LICENSE")

    digest = hashlib.sha256(package.read_bytes()).hexdigest()
    (out / (package.name + ".sha256")).write_text(f"{digest}  {package.name}\n")
    print(package)
    print(digest)
