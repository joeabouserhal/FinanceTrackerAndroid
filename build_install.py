#!/usr/bin/env python3
"""Quick build + adb install helper for FinanceTrackerAndroid.

Usage:
  python build_install.py             # assembleRelease, install, relaunch
  python build_install.py --tests     # run the unit tests first
  python build_install.py --debug     # build/install the debug APK instead
"""

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PACKAGE = "com.joeabouserhal.financetracker"


def run(cmd, **kwargs):
    return subprocess.run(cmd, cwd=ROOT, **kwargs)


def find_adb() -> str:
    # 1. sdk.dir from local.properties (survives Windows path escaping).
    lp = ROOT / "local.properties"
    if lp.exists():
        for line in lp.read_text(encoding="utf-8").splitlines():
            stripped = line.strip()
            if stripped.startswith("sdk.dir"):
                sdk = stripped.split("=", 1)[1].strip().replace("\\\\", "\\")
                for candidate in (
                    Path(sdk) / "platform-tools" / "adb.exe",
                    Path(sdk) / "platform-tools" / "adb",
                ):
                    if candidate.exists():
                        return str(candidate)
    # 2. adb on PATH.
    on_path = shutil.which("adb")
    if on_path:
        return on_path
    sys.exit("adb not found: set sdk.dir in local.properties or add adb to PATH")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tests", action="store_true", help="run unit tests before building")
    parser.add_argument("--debug", action="store_true", help="build and install the debug APK")
    args = parser.parse_args()

    gradlew = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
    variant = "debug" if args.debug else "release"
    apk = ROOT / "app" / "build" / "outputs" / "apk" / variant / f"app-{variant}.apk"

    tasks = [f":app:assemble{variant.capitalize()}"]
    if args.tests:
        tasks.insert(0, ":app:testDebugUnitTest")

    print(f"> building: {' '.join(tasks)}")
    build = run([gradlew, *tasks, "--console=plain"])
    if build.returncode != 0:
        sys.exit(build.returncode)

    if not apk.exists():
        sys.exit(f"APK not found after build: {apk}")

    adb = find_adb()
    print(f"> installing: {apk.name}")
    install = run([adb, "install", "-r", str(apk)])
    if install.returncode != 0:
        sys.exit(install.returncode)

    run([adb, "shell", "am", "force-stop", PACKAGE])
    run(
        [
            adb,
            "shell",
            "monkey",
            "-p",
            PACKAGE,
            "-c",
            "android.intent.category.LAUNCHER",
            "1",
        ],
        stdout=subprocess.DEVNULL,
    )
    print("done — installed and relaunched")
    return 0


if __name__ == "__main__":
    sys.exit(main())
