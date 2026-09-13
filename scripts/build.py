"""Portable build entry point: Python 3.11+, JDK 21 and Node.js 22 on PATH."""
from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys

from prepare_dependencies import ROOT, digest, prepare
from release import extension_version, package, validate_version


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', help='Require this release tag to equal v<VERSION>')
    args = parser.parse_args()
    validate_version(args.tag)
    extension_version()  # Fail before downloads when the submodule was not initialized.
    prepare()
    subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts', '-p', 'test_*.py'],
                   cwd=ROOT, check=True)
    extension = ROOT / 'cobblemon-ext'
    spec = json.loads((extension / 'scripts/dependencies.json').read_text('utf-8'))['cobblemon']
    cached = ROOT / '.deps/cobblemon.jar'
    target = extension / '.deps/cobblemon.jar'
    if not target.exists() and digest(cached, spec['algorithm']) == spec['hash']:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(cached, target)
    subprocess.run([sys.executable, 'scripts/build.py'], cwd=extension, check=True)
    wrapper = [str(ROOT / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(ROOT / 'gradlew')]
    subprocess.run([*wrapper, '--no-daemon', '--console=plain', 'clean', 'build'], cwd=ROOT, check=True)
    package()


if __name__ == '__main__':
    main()
