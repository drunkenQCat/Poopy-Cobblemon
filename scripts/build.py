"""Portable build entry point: Python 3.11+, JDK 21 and Node.js 22 on PATH."""
from __future__ import annotations

import argparse
import os
import subprocess

from build_support import ROOT
from release import extension_version, package, validate_version


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', help='Require this release tag to equal v<VERSION>')
    args = parser.parse_args()
    validate_version(args.tag)
    extension_version()  # Fail before downloads when the submodule was not initialized.
    wrapper = [str(ROOT / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(ROOT / 'gradlew')]
    subprocess.run([*wrapper, '--no-daemon', '--console=plain', 'clean', 'build'], cwd=ROOT, check=True)
    package()
    subprocess.run([*wrapper, '-p', 'publishing', '--no-daemon', '--console=plain',
                    'curseforgePreview'], cwd=ROOT, check=True)


if __name__ == '__main__':
    main()
