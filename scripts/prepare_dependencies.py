"""Fetch pinned build-only inputs; never read a Prism instance or redistribute mods."""
from __future__ import annotations

import hashlib
import io
import json
import shutil
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEPS = ROOT / '.deps'


def digest(path: Path, algorithm: str = 'sha256') -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, algorithm).hexdigest()


def download(name: str, spec: dict) -> Path:
    target = DEPS / f'{name}.jar'
    if target.is_file() and digest(target, spec['algorithm']) == spec['hash']:
        print(f'Verified cache: {name} {spec["version"]}', flush=True)
        return target
    temporary = target.with_suffix('.download')
    for attempt in range(3):
        try:
            print(f'Downloading {name} (attempt {attempt + 1}/3)', flush=True)
            request = urllib.request.Request(spec['url'], headers={
                'User-Agent': 'poketoilet-build/1 (github.com/drunkenQCat/poketoilet)'})
            with urllib.request.urlopen(request, timeout=90) as response, temporary.open('wb') as out:
                shutil.copyfileobj(response, out)
            if digest(temporary, spec['algorithm']) != spec['hash']:
                raise ValueError(f'{name}: checksum mismatch; refusing unverified dependency')
            temporary.replace(target)
            return target
        except (OSError, ValueError) as error:
            temporary.unlink(missing_ok=True)
            if attempt == 2:
                raise RuntimeError(f'Unable to prepare {name}: {error}') from error
            time.sleep(2 ** attempt)
    raise AssertionError('unreachable')


def extract_safe(archive: zipfile.ZipFile, destination: Path) -> None:
    destination = destination.resolve()
    for entry in archive.infolist():
        target = (destination / entry.filename).resolve()
        if not target.is_relative_to(destination):
            raise ValueError(f'Unsafe archive path: {entry.filename}')
        if entry.is_dir():
            target.mkdir(parents=True, exist_ok=True)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.open(entry) as source, target.open('wb') as out:
                shutil.copyfileobj(source, out)


def prepare() -> None:
    DEPS.mkdir(exist_ok=True)
    lock = json.loads((ROOT / 'scripts/dependencies.json').read_text('utf-8'))
    jars = {name: download(name, spec) for name, spec in lock.items()}
    # Registrate is already bundled by PoopSky; its bytes are covered by the parent hash.
    with zipfile.ZipFile(jars['poopsky']) as archive:
        metadata = json.loads(archive.read('META-INF/jarjar/metadata.json'))
        matches = [jar['path'] for jar in metadata['jars']
                   if jar['identifier']['group'] == 'com.tterrag.registrate'
                   and jar['identifier']['artifact'] == 'Registrate']
        if len(matches) != 1:
            raise ValueError('Expected exactly one bundled Registrate dependency')
        (DEPS / 'registrate.jar').write_bytes(archive.read(matches[0]))
    # Use the exact simulator shipped with our pinned Cobblemon, including its Node modules.
    with zipfile.ZipFile(jars['cobblemon']) as archive:
        with zipfile.ZipFile(io.BytesIO(archive.read('data/cobblemon/showdown.zip'))) as simulator:
            destination = DEPS / 'showdown'
            if destination.exists():
                shutil.rmtree(destination)  # fixed generated directory beneath this checkout
            extract_safe(simulator, destination)
    print('Build inputs and Cobblemon Showdown simulator are ready.', flush=True)


if __name__ == '__main__':
    prepare()
