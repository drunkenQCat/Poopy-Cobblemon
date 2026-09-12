"""Portable build entry point: Python 3.11+, JDK 21 and Node.js 22 on PATH."""
from __future__ import annotations

import argparse
import os
import subprocess
import sys

from prepare_dependencies import ROOT, prepare
from release import package, validate_version


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', help='Require this release tag to equal v<VERSION>')
    args = parser.parse_args()
    validate_version(args.tag)
    prepare()
    subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s', 'scripts', '-p', 'test_*.py'],
                   cwd=ROOT, check=True)
    for directory in (ROOT / 'cobblemon-ext', ROOT):
        wrapper = [str(directory / 'gradlew.bat')] if os.name == 'nt' else ['bash', str(directory / 'gradlew')]
        subprocess.run([*wrapper, '--no-daemon', '--console=plain', 'clean', 'build'],
                       cwd=directory, check=True)
    package()


if __name__ == '__main__':
    main()
