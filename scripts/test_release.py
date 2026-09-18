"""Offline failure-path tests for packaging and retry-safe publishing."""
import json
import shutil
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

import release


def make_jar(path, mod_id, version='1.2', missing_mixin=False):
    dependency = '\n[[dependencies.poopy_cobblemon]]\nmodId="cobblemon_ext"\ntype="required"\nversionRange="[1.3,)"\n' if mod_id == 'poopy_cobblemon' else ''
    with zipfile.ZipFile(path, 'w') as jar:
        jar.writestr('META-INF/MANIFEST.MF', f'Manifest-Version: 1.0\r\nImplementation-Version: {version}\r\n')
        jar.writestr('META-INF/neoforge.mods.toml', f'[[mods]]\nmodId="{mod_id}"\nversion="${{file.jarVersion}}"\n[[mixins]]\nconfig="test.json"\n{dependency}')
        jar.writestr('test.json', json.dumps({'package': 'com.poopycobblemon', 'mixins': ['Example']}))
        if not missing_mixin:
            jar.writestr('com/poopycobblemon/Example.class', b'test')
        if mod_id == 'cobblemon_ext':
            jar.writestr('assets/cobblemon_ext/showdown/cobblemon_ext_patch.js', '// test')


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.root_patch = patch.object(release, 'ROOT', self.root)
        self.root_patch.start()
        (self.root / 'VERSION').write_text('1.2\n')
        (self.root / 'releases').mkdir()
        (self.root / 'releases/1.2.md').write_text('Release notes')
        (self.root / 'cobblemon-ext').mkdir()
        (self.root / 'cobblemon-ext/VERSION').write_text('1.2\n')
        for name, mod_id, directory in [('poopy-cobblemon', 'poopy_cobblemon', 'build/libs'),
                                         ('cobblemon-ext', 'cobblemon_ext', 'cobblemon-ext/build/libs')]:
            folder = self.root / directory
            folder.mkdir(parents=True)
            make_jar(folder / f'{name}-1.2.jar', mod_id)

    def tearDown(self):
        self.root_patch.stop()
        self.temp.cleanup()

    def test_package_contains_only_two_mods_and_checksums(self):
        release.package()
        release.verify_assets(self.root / 'dist', '1.2')

    def test_extension_has_an_independent_version(self):
        (self.root / 'cobblemon-ext/VERSION').write_text('1.3\n')
        make_jar(self.root / 'cobblemon-ext/build/libs/cobblemon-ext-1.3.jar', 'cobblemon_ext', '1.3')
        release.package()
        self.assertEqual(set(p.name for p in (self.root / 'dist').iterdir()),
                         {'poopy-cobblemon-1.2.jar', 'cobblemon-ext-1.3.jar', 'SHA256SUMS.txt'})
        release.verify_assets(self.root / 'dist', '1.2')

    def test_missing_submodule_fails_with_init_instruction(self):
        (self.root / 'cobblemon-ext/VERSION').unlink()
        with self.assertRaisesRegex(ValueError, 'git submodule update'):
            release.package()

    def test_tag_mismatch_stops_before_download_or_publish(self):
        with self.assertRaisesRegex(ValueError, 'does not match'):
            release.validate_version('v1.3')

    def comparison_fixture(self):
        release.package()
        base = self.root / 'verification'
        for platform in ('release-ubuntu-24.04', 'release-windows-2022'):
            shutil.copytree(self.root / 'dist', base / platform)
        return base

    def test_cross_platform_artifacts_match(self):
        release.compare_builds(self.comparison_fixture(), '1.2')

    def test_different_valid_platform_jars_block_release(self):
        base = self.comparison_fixture()
        windows = base / 'release-windows-2022'
        jar = windows / release.asset_names('1.2')[0]
        with zipfile.ZipFile(jar, 'a') as archive:
            archive.writestr('platform-difference.txt', 'windows only')
        checksums = ''.join(f'{release.digest(windows / name)}  {name}\n'
                            for name in release.asset_names('1.2')[:-1])
        (windows / 'SHA256SUMS.txt').write_text(checksums, encoding='utf-8', newline='\n')
        release.verify_assets(windows, '1.2')
        with self.assertRaisesRegex(ValueError, 'artifacts differ'):
            release.compare_builds(base, '1.2')

    def test_missing_platform_artifacts_block_release(self):
        base = self.comparison_fixture()
        (base / 'release-windows-2022' / release.asset_names('1.2')[0]).unlink()
        with self.assertRaisesRegex(ValueError, 'Release must contain'):
            release.compare_builds(base, '1.2')

    def test_wrong_jar_version_fails(self):
        jar = self.root / 'build/libs/poopy-cobblemon-1.2.jar'
        make_jar(jar, 'poopy_cobblemon', '1.1')
        with self.assertRaisesRegex(ValueError, 'manifest version'):
            release.package()

    def test_missing_mixin_fails(self):
        make_jar(self.root / 'build/libs/poopy-cobblemon-1.2.jar', 'poopy_cobblemon', missing_mixin=True)
        with self.assertRaisesRegex(ValueError, 'missing mixin'):
            release.package()

    def test_dependency_classes_are_not_published(self):
        with zipfile.ZipFile(self.root / 'build/libs/poopy-cobblemon-1.2.jar', 'a') as jar:
            jar.writestr('com/cobblemon/Example.class', b'dependency')
        with self.assertRaisesRegex(ValueError, 'dependency classes'):
            release.package()

    def test_missing_model_texture_fails(self):
        with zipfile.ZipFile(self.root / 'build/libs/poopy-cobblemon-1.2.jar', 'a') as jar:
            jar.writestr('assets/poopy_cobblemon/models/item/scale_scanner.json', json.dumps({
                'parent': 'minecraft:item/generated',
                'textures': {'layer0': 'poopy_cobblemon:item/scale_scanner'},
            }))
        with self.assertRaisesRegex(ValueError, 'missing model resource'):
            release.package()

    def test_tampered_asset_fails(self):
        release.package()
        with (self.root / 'dist/poopy-cobblemon-1.2.jar').open('ab') as stream:
            stream.write(b'tampered')
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            release.verify_assets(self.root / 'dist', '1.2')

    def test_stale_release_file_fails(self):
        release.package()
        (self.root / 'dist/old.jar').write_bytes(b'old')
        with self.assertRaisesRegex(ValueError, 'unexpected files'):
            release.package()

    def test_release_publish_and_retry_paths(self):
        release.package()
        sha = 'a' * 40
        assets = [{'name': name} for name in release.asset_names('1.2')]
        for state in ('new', 'draft', 'published'):
            with self.subTest(state=state):
                calls = []
                existing = {'tag_name': 'v1.2', 'draft': state == 'draft', 'assets': assets}

                def fake_gh(*args):
                    calls.append(args)
                    if args[:2] == ('api', '--paginate'):
                        if sum(c[:2] == ('api', '--paginate') for c in calls) > 1:
                            return json.dumps([[dict(existing, draft=True)]])
                        return json.dumps([[] if state == 'new' else [existing]])
                    if args[0] == 'api' and '/git/ref/' in args[1]:
                        return json.dumps({'object': {'type': 'commit', 'sha': sha}})
                    return ''

                with patch.object(release, 'gh', side_effect=fake_gh), patch.object(release, 'verify_remote') as verify:
                    release.publish('v1.2', 'owner/repo', sha)
                verify.assert_called_once()
                verbs = [c[1] for c in calls if c[0] == 'release']
                self.assertEqual(verbs, {'new': ['create', 'upload', 'edit'],
                                         'draft': ['upload', 'edit'], 'published': []}[state])

    def test_network_failure_never_creates_release(self):
        release.package()
        with patch.object(release, 'gh', side_effect=RuntimeError('network failed')) as command:
            with self.assertRaisesRegex(RuntimeError, 'network failed'):
                release.publish('v1.2', 'owner/repo', 'a' * 40)
        self.assertTrue(all(c.args[0] == 'api' for c in command.call_args_list))

    def test_failed_remote_verification_never_publishes(self):
        release.package()
        calls = []
        def fake_gh(*args):
            calls.append(args)
            if args[:2] == ('api', '--paginate'):
                if sum(c[:2] == ('api', '--paginate') for c in calls) > 1:
                    return json.dumps([[{'tag_name': 'v1.2', 'draft': True, 'assets': []}]])
                return '[[]]'
            if args[0] == 'api' and '/git/ref/' in args[1]:
                return json.dumps({'object': {'type': 'commit', 'sha': 'a' * 40}})
            if args[0] == 'api':
                return '{}'
            return ''
        with patch.object(release, 'gh', side_effect=fake_gh), patch.object(release, 'verify_remote', side_effect=ValueError('Remote asset differs')):
            with self.assertRaisesRegex(ValueError, 'Remote asset differs'):
                release.publish('v1.2', 'owner/repo', 'a' * 40)
        self.assertNotIn('edit', [c[1] for c in calls if c[0] == 'release'])

    def test_remote_checksum_difference_is_detected(self):
        release.package()
        assets = [{'id': index, 'name': name} for index, name in enumerate(release.asset_names('1.2'))]
        def fake_download(repository, asset_id, destination):
            destination.write_bytes(b'different remote bytes')
        with patch.object(release, 'download_asset', side_effect=fake_download):
            with self.assertRaisesRegex(ValueError, 'Remote asset differs'):
                release.verify_remote('v1.2', 'owner/repo', self.root / 'dist', '1.2', {'assets': assets})

    def test_wrong_remote_tag_never_creates_release(self):
        release.package()
        with patch.object(release, 'gh', return_value=json.dumps({'object': {'type': 'commit', 'sha': 'b' * 40}})) as command:
            with self.assertRaisesRegex(ValueError, 'tested commit'):
                release.publish('v1.2', 'owner/repo', 'a' * 40)
        command.assert_called_once()


if __name__ == '__main__':
    unittest.main()
