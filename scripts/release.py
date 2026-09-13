"""Validate release packages; publish only when explicitly invoked with `publish`."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import tempfile
import tomllib
import zipfile
from pathlib import Path

from build_support import ROOT, digest


def validate_version(tag: str | None = None) -> str:
    version = (ROOT / 'VERSION').read_text('utf-8').strip()
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?', version):
        raise ValueError(f'Invalid VERSION: {version!r}')
    if tag is not None and tag != f'v{version}':
        raise ValueError(f'Tag {tag!r} does not match v{version}')
    notes = ROOT / 'releases' / f'{version}.md'
    if not notes.is_file() or not notes.read_text('utf-8').strip():
        raise ValueError(f'Missing release notes: {notes}')
    return version


def validate_jar(path: Path, mod_id: str, version: str) -> None:
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise ValueError(f'Corrupt JAR: {path}')
        manifest = archive.read('META-INF/MANIFEST.MF').decode('utf-8').replace('\r\n', '\n')
        if f'Implementation-Version: {version}' not in manifest.splitlines():
            raise ValueError(f'{path}: incorrect manifest version')
        metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        if metadata['mods'][0]['modId'] != mod_id or metadata['mods'][0]['version'] != '${file.jarVersion}':
            raise ValueError(f'{path}: incorrect mod metadata')
        if mod_id == 'poopy_cobblemon':
            dependency = next(d for d in metadata['dependencies'][mod_id] if d['modId'] == 'cobblemon_ext')
            if dependency['versionRange'] != '[1.2,)':
                raise ValueError('Poopy Cobblemon must require cobblemon-ext API 1.2 or later when installed')
        names = set(archive.namelist())
        # Follow local model references so namespace/path refactors cannot ship missing textures.
        for model_path in sorted(n for n in names if n.startswith(f'assets/{mod_id}/models/') and n.endswith('.json')):
            model = json.loads(archive.read(model_path))
            references = [(model.get('parent', ''), 'models', '.json')]
            references += [(texture, 'textures', '.png') for texture in model.get('textures', {}).values()]
            for reference, folder, suffix in references:
                if not reference or reference.startswith('#'):
                    continue
                namespace, _, resource = reference.partition(':')
                if namespace == mod_id:
                    target = f'assets/{namespace}/{folder}/{resource}{suffix}'
                    if target not in names:
                        raise ValueError(f'{path}: missing model resource {target}')
        if any(n.startswith(('com/cobblemon/', 'com/altnoir/', 'kotlin/', 'META-INF/jarjar/')) for n in names):
            raise ValueError(f'{path}: dependency classes or nested dependency JARs must not be published')
        for mixin in metadata.get('mixins', []):
            config = json.loads(archive.read(mixin['config']))
            for side in ('mixins', 'client', 'server'):
                for name in config.get(side, []):
                    class_path = (config['package'] + '.' + name).replace('.', '/') + '.class'
                    if class_path not in names:
                        raise ValueError(f'{path}: missing mixin {class_path}')
        if mod_id == 'cobblemon_ext':
            archive.read('assets/cobblemon_ext/showdown/cobblemon_ext_patch.js')


def extension_version() -> str:
    path = ROOT / 'cobblemon-ext/VERSION'
    if not path.is_file():
        raise ValueError('Initialize the extension with: git submodule update --init --recursive')
    version = path.read_text('utf-8').strip()
    if not re.fullmatch(r'\d+\.\d+(?:\.\d+)?(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?', version):
        raise ValueError(f'Invalid cobblemon-ext VERSION: {version!r}')
    return version


def mod_artifacts(version: str) -> list[tuple[str, str, str]]:
    ext_version = extension_version()
    return [(f'poopy-cobblemon-{version}.jar', 'poopy_cobblemon', version),
            (f'cobblemon-ext-{ext_version}.jar', 'cobblemon_ext', ext_version)]


def asset_names(version: str) -> list[str]:
    return [name for name, _, _ in mod_artifacts(version)] + ['SHA256SUMS.txt']


def package() -> None:
    version = validate_version()
    artifacts = mod_artifacts(version)
    sources = [ROOT / directory / name for directory, (name, _, _) in
               zip(('build/libs', 'cobblemon-ext/build/libs'), artifacts)]
    for path, (_, mod_id, mod_version) in zip(sources, artifacts):
        validate_jar(path, mod_id, mod_version)
    output = ROOT / 'dist'
    output.mkdir(exist_ok=True)
    # Refuse stale output instead of accidentally uploading an older version.
    if set(p.name for p in output.iterdir()) - set(asset_names(version)):
        raise ValueError('dist contains unexpected files; use a fresh output directory')
    for path in sources:
        shutil.copy2(path, output / path.name)
    sums = ''.join(f'{digest(path)}  {path.name}\n' for path in sources)
    (output / 'SHA256SUMS.txt').write_text(sums, encoding='utf-8', newline='\n')
    verify_assets(output, version)
    print(f'Validated release assets: {output}', flush=True)


def verify_assets(directory: Path, version: str) -> None:
    if set(p.name for p in directory.iterdir()) != set(asset_names(version)):
        raise ValueError('Release must contain exactly both mod JARs and SHA256SUMS.txt')
    expected = ''.join(f'{digest(directory / name)}  {name}\n' for name in asset_names(version)[:2])
    if (directory / 'SHA256SUMS.txt').read_text('utf-8') != expected:
        raise ValueError('Release checksum mismatch')
    for name, mod_id, mod_version in mod_artifacts(version):
        validate_jar(directory / name, mod_id, mod_version)


def compare_builds(directory: Path, version: str) -> None:
    platforms = ('release-ubuntu-24.04', 'release-windows-2022')
    for platform in platforms:
        verify_assets(directory / platform, version)
    for name in asset_names(version):
        if digest(directory / platforms[0] / name) != digest(directory / platforms[1] / name):
            raise ValueError(f'Ubuntu and Windows artifacts differ: {name}')
    print('Ubuntu and Windows release artifacts are identical.', flush=True)


def gh(*arguments: str) -> str:
    return subprocess.check_output(['gh', *arguments], text=True, encoding='utf-8').strip()


def find_release(repository: str, tag: str) -> dict | None:
    # The list API includes drafts for push-capable tokens; the tag endpoint is for published releases.
    pages = json.loads(gh('api', '--paginate', '--slurp', f'repos/{repository}/releases?per_page=100'))
    return next((r for page in pages for r in page if r['tag_name'] == tag), None)


def publish(tag: str, repository: str, commit: str) -> None:
    version = validate_version(tag)
    if not re.fullmatch(r'[\w.-]+/[\w.-]+', repository) or not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise ValueError('Expected owner/repository and a full commit SHA')
    directory = ROOT / 'dist'
    verify_assets(directory, version)
    # Dereference lightweight or annotated tags and refuse publishing the wrong checkout.
    ref = json.loads(gh('api', f'repos/{repository}/git/ref/tags/{tag}'))['object']
    for _ in range(8):
        if ref['type'] != 'tag':
            break
        ref = json.loads(gh('api', f'repos/{repository}/git/tags/{ref["sha"]}'))['object']
    if ref['type'] != 'commit' or ref['sha'] != commit:
        raise ValueError('Remote release tag does not point to the tested commit')
    # Listing distinguishes a missing release from authentication/network failures.
    existing = find_release(repository, tag)
    if existing is not None and not existing['draft']:
        verify_remote(tag, repository, directory, version, existing)
        print('Release already published with identical assets; nothing to change.')
        return
    if existing is None:
        flags = ['--prerelease'] if '-' in version else []
        gh('release', 'create', tag, '--repo', repository, '--verify-tag', '--target', commit,
           '--draft', '--title', f'Poopy Cobblemon {version}', '--notes-file', str(ROOT / 'releases' / f'{version}.md'), *flags)
    elif set(a['name'] for a in existing['assets']) - set(asset_names(version)):
        raise ValueError('Existing draft has unexpected assets; inspect it before retrying')
    gh('release', 'upload', tag, '--repo', repository, '--clobber',
       *(str(directory / name) for name in asset_names(version)))
    # Download and hash all assets before making the release public.
    details = find_release(repository, tag)
    if details is None or not details['draft']:
        raise ValueError('Expected an unpublished draft before asset verification')
    verify_remote(tag, repository, directory, version, details)
    gh('release', 'edit', tag, '--repo', repository, '--draft=false',
       '--prerelease=' + str('-' in version).lower(), '--title', f'Poopy Cobblemon {version}',
       '--notes-file', str(ROOT / 'releases' / f'{version}.md'))
    print(f'Published {repository} {tag}.')


def verify_remote(tag: str, repository: str, directory: Path, version: str, details: dict) -> None:
    if set(a['name'] for a in details['assets']) != set(asset_names(version)):
        raise ValueError('Remote release asset set does not match this build')
    with tempfile.TemporaryDirectory(prefix='poopy-cobblemon-release-') as temporary:
        for asset in details['assets']:
            name = asset['name']
            download_asset(repository, asset['id'], Path(temporary) / name)
            if digest(Path(temporary) / name) != digest(directory / name):
                raise ValueError(f'Remote asset differs: {name}; published assets are never overwritten')


def download_asset(repository: str, asset_id: int, destination: Path) -> None:
    # Authenticated asset API works for private repositories and unpublished draft assets.
    with destination.open('wb') as output:
        subprocess.run(['gh', 'api', f'repos/{repository}/releases/assets/{asset_id}',
                        '-H', 'Accept: application/octet-stream'], stdout=output, check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('package', 'verify', 'compare', 'publish'))
    parser.add_argument('--tag')
    parser.add_argument('--artifacts-dir', type=Path, default=ROOT / 'verification')
    args = parser.parse_args()
    if args.command == 'publish':
        if not args.tag:
            parser.error('publish requires --tag')
        publish(args.tag, os.environ['GITHUB_REPOSITORY'], os.environ['GITHUB_SHA'])
    elif args.command == 'compare':
        compare_builds(args.artifacts_dir, validate_version(args.tag))
    elif args.command == 'package':
        validate_version(args.tag)
        package()
    else:
        verify_assets(ROOT / 'dist', validate_version(args.tag))
