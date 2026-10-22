"""Exercise real merges and the conditions which must stop auto publication."""

import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("upstream_sync", Path(__file__).with_name("sync.py"))
sync = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sync)


def command(root, *args):
    result = subprocess.run(["git", "-C", str(root), *args], text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise AssertionError(result.stderr)
    return result.stdout.strip()


class MergeFixture(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.upstream = self.directory / "upstream"
        self.upstream.mkdir()
        command(self.upstream, "init", "-b", "main")
        self.configure(self.upstream)
        self.put(self.upstream, "gradle.properties", "workers=2\nversion = 1.47.0\n")
        self.put(self.upstream, "patches/build.gradle.kts", 'patches {\n about {\n  name = "Hoodles Morphe Patches"\n  source = "git@github.com:hoo-dles/morphe-patches.git"\n }\n}\n')
        self.put(self.upstream, "README.md", "Upstream documentation\n")
        self.put(self.upstream, "code.txt", "original implementation\n")
        self.put(self.upstream, ".releaserc", '{"dryRun":false}\n')
        self.put(self.upstream, ".github/workflows/release.yml", "name: upstream\n")
        self.put(self.upstream, "patches-bundle.json", json.dumps({"version": "1.47.0"}))
        self.put(self.upstream, "patches-list.json", json.dumps({"version": "1.47.0", "patches": []}))
        self.put(self.upstream, sync.COMPAT, 'object Compat {\n val DUOLINGO = Compatibility(\n targets = listOf(AppTarget("6.102.4"))\n )\n}\n')
        self.commit(self.upstream, "upstream base")
        self.baseline = command(self.upstream, "rev-parse", "HEAD")
        self.fork = self.directory / "fork"
        command(self.directory, "clone", str(self.upstream), str(self.fork))
        self.configure(self.fork)
        self.put(self.fork, "gradle.properties", "workers=2\nversion = 1.47.0-mod.2\n")
        build = (self.fork / "patches/build.gradle.kts").read_text()
        self.put(self.fork, "patches/build.gradle.kts", build.replace('name = "Hoodles Morphe Patches"', f'name = "{sync.BUNDLE_NAME}"').replace("git@github.com:hoo-dles/morphe-patches.git", sync.FORK_URL))
        self.put(self.fork, "README.md", "Fork instructions, preserving reminder patch\n")
        self.put(self.fork, ".releaserc", '{"dryRun":true}\n')
        self.put(self.fork, ".github/workflows/release.yml", "name: fork\n")
        self.put(self.fork, "patches-bundle.json", json.dumps({"version": "1.47.0-mod.2"}))
        self.put(self.fork, sync.STATE, json.dumps({"upstream_commit": self.baseline}))
        self.put(self.fork, sync.PIN, json.dumps({"version": "1.47.0", "sha256": "a" * 64}))
        self.put(self.fork, sync.CUSTOM[0] + "src/LearningReminderFallback.java", "custom runtime\n")
        self.put(self.fork, sync.CUSTOM[1] + "RestoreLearningRemindersPatch.kt", "custom patch\n")
        self.commit(self.fork, "fork customization")
        self.head = command(self.fork, "rev-parse", "HEAD")
        self.plan_path = self.directory / "plan.json"

    @staticmethod
    def configure(root):
        command(root, "config", "user.name", "Sync test")
        command(root, "config", "user.email", "sync-test@example.invalid")

    @staticmethod
    def put(root, path, content):
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    @staticmethod
    def commit(root, message):
        command(root, "add", "--all")
        command(root, "commit", "-m", message)

    def upstream_change(self, path="code.txt", text="updated implementation\n"):
        self.put(self.upstream, path, text)
        self.commit(self.upstream, "upstream update")
        return command(self.upstream, "rev-parse", "HEAD")

    def prepare(self, **kwargs):
        return sync.prepare(self.fork, self.plan_path, remote=str(self.upstream),
                            discover_release=False, **kwargs)

    def test_version_rule(self):
        self.assertEqual(sync.next_version("1.47.0", "1.47.0-mod.2"), "1.47.0-mod.3")
        self.assertEqual(sync.next_version("1.48.0", "1.47.0-mod.12"), "1.48.0-mod.1")
        self.assertEqual(sync.base_version("version = 1.48.0-dev.1", {"version": "1.47.0"}), "1.47.0")
        with self.assertRaises(sync.SyncError):
            sync.base_version("version = 1.48.0-dev.1", {"version": "1.48.0-dev.1"})

    def test_unchanged_is_a_clean_noop(self):
        plan = self.prepare()
        self.assertFalse(plan["changed"])
        self.assertEqual(plan["version"], "1.47.0-mod.2")
        self.assertEqual(command(self.fork, "status", "--porcelain"), "")

    def test_merge_preserves_custom_files_and_upstream_build_settings(self):
        before = sync.custom_hashes(self.fork)
        self.put(self.upstream, "gradle.properties", "workers=6\nversion = 1.48.0\n")
        self.put(self.upstream, "patches/build.gradle.kts", (self.upstream / "patches/build.gradle.kts").read_text() + "\n// new upstream compiler setting\n")
        self.put(self.upstream, "README.md", "New upstream docs\n")
        self.put(self.upstream, ".github/workflows/release.yml", "name: new upstream workflow\n")
        self.put(self.upstream, ".github/workflows/new.yml", "name: must not import\n")
        sha = self.upstream_change()
        release = {"version": "1.48.0", "commit": sha, "download_url": "https://example.invalid/patches.mpp", "sha256": "b" * 64}
        plan = self.prepare(release=release)
        self.assertTrue(plan["release_parity"])
        self.assertEqual(plan["version"], "1.48.0-mod.1")
        self.assertIn("workers=6", (self.fork / "gradle.properties").read_text())
        self.assertIn("new upstream compiler", (self.fork / "patches/build.gradle.kts").read_text())
        self.assertIn(sync.BUNDLE_NAME, (self.fork / "patches/build.gradle.kts").read_text())
        self.assertIn(sync.FORK_URL, (self.fork / "patches/build.gradle.kts").read_text())
        self.assertEqual((self.fork / "code.txt").read_text(), "updated implementation\n")
        self.assertEqual((self.fork / "README.md").read_text(), "Fork instructions, preserving reminder patch\n")
        self.assertEqual((self.fork / ".github/workflows/release.yml").read_text(), "name: fork\n")
        self.assertFalse((self.fork / ".github/workflows/new.yml").exists())
        self.assertEqual(sync.custom_hashes(self.fork), before)
        self.assertEqual(sync.read_json(self.fork / sync.PIN)["sha256"], "b" * 64)
        self.assertEqual(command(self.fork, "rev-parse", "MERGE_HEAD"), sha)

    def test_code_conflict_aborts_and_restores_clean_checkout(self):
        self.put(self.fork, "code.txt", "fork implementation\n")
        self.commit(self.fork, "fork code")
        self.upstream_change()
        with self.assertRaisesRegex(sync.SyncError, "code conflicts"):
            self.prepare()
        self.assertEqual(command(self.fork, "status", "--porcelain"), "")
        self.assertEqual((self.fork / "code.txt").read_text(), "fork implementation\n")

    def test_custom_reminder_change_is_blocked(self):
        self.upstream_change(sync.CUSTOM[0] + "src/LearningReminderFallback.java", "different upstream implementation\n")
        with self.assertRaises(sync.SyncError):
            self.prepare()
        self.assertEqual(command(self.fork, "status", "--porcelain"), "")

    def test_duolingo_target_change_is_blocked(self):
        source = (self.upstream / sync.COMPAT).read_text().replace("6.102.4", "6.103.0")
        self.upstream_change(sync.COMPAT, source)
        with self.assertRaisesRegex(sync.SyncError, "Duolingo target"):
            self.prepare()
        self.assertEqual(command(self.fork, "status", "--porcelain"), "")
        self.assertEqual(sync.targets((self.fork / sync.COMPAT).read_text()), ("6.102.4",))

    def test_release_overlay_rejects_unpublished_main_without_publication(self):
        self.upstream_change("gradle.properties", "workers=2\nversion = 1.48.0\n")
        self.prepare()
        pin = sync.read_json(self.fork / sync.PIN)
        self.assertEqual(pin["version"], "1.48.0")
        self.assertEqual(pin["source_commit"], command(self.upstream, "rev-parse", "HEAD"))
        self.assertIsNone(pin["download_url"])
        self.assertIsNone(pin["sha256"])
        original = (self.fork / "patches-bundle.json").read_bytes()
        with self.assertRaisesRegex(sync.SyncError, "unpublished main"):
            sync.finalize(self.fork, self.plan_path, self.directory / "nonexistent.mpp",
                          self.directory / "nonexistent.json", "release-overlay")
        self.assertEqual((self.fork / "patches-bundle.json").read_bytes(), original)
        self.assertFalse((self.fork / "dist").exists())
        self.assertEqual(sync.read_json(self.fork / sync.STATE)["upstream_commit"], self.baseline)

    def test_rebuild_only_bumps_mod_revision(self):
        plan = self.prepare(rebuild=True)
        self.assertEqual(plan["upstream_sha"], self.baseline)
        self.assertEqual(plan["version"], "1.47.0-mod.3")
        self.assertEqual(sync.read_json(self.fork / sync.STATE)["upstream_commit"], self.baseline)

    def test_substantive_build_conflict_requires_review(self):
        self.put(self.fork, "gradle.properties", "workers=9\nversion = 1.47.0-mod.2\n")
        self.commit(self.fork, "fork worker setting")
        self.upstream_change("gradle.properties", "workers=4\nversion = 1.48.0\n")
        with self.assertRaisesRegex(sync.SyncError, "Substantive local changes"):
            self.prepare()
        self.assertEqual(command(self.fork, "status", "--porcelain"), "")

    def test_full_catalog_export_preserves_all_fields(self):
        catalog = {"version": "other", "patches": [{"name": "Some patch", "options": [{"description": "option help"}], "category": "custom", "compatiblePackages": None}]}
        converted = sync.normalize_catalog(catalog, "1.47.0-mod.3")
        self.assertEqual(converted["patches"], catalog["patches"])
        self.assertEqual(converted["version"], "1.47.0-mod.3")

    def test_manifest_normalization_can_replace_same_file_on_windows(self):
        bundle = self.directory / "bundle.mpp"
        attrs = {"Manifest-Version": "1.0", "Name": sync.BUNDLE_NAME, "Version": "1.47.0-mod.3", "Source": "upstream", "Description": "unchanged " * 40}
        with zipfile.ZipFile(bundle, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", sync.manifest_bytes(attrs))
            archive.writestr("classes.dex", b"dex\n035\0payload")
            archive.writestr("extensions/duolingo.mpe", b"dex\n035\0extension")
        sync.normalize_bundle(bundle, bundle, "1.47.0-mod.3")
        with zipfile.ZipFile(bundle) as archive:
            manifest = archive.read("META-INF/MANIFEST.MF")
            self.assertTrue(all(len(line) <= 72 for line in manifest.splitlines()))
            self.assertEqual(sync.manifest_attributes(manifest)["Description"], attrs["Description"])
            self.assertEqual(sync.manifest_attributes(manifest)["Source"], sync.FORK_URL)

    def built_fixture(self, version):
        bundle = self.directory / "built.mpp"
        attrs = {"Manifest-Version": "1.0", "Name": sync.BUNDLE_NAME, "Version": version,
                 "Source": "upstream", "Description": "Generic upstream description"}
        with zipfile.ZipFile(bundle, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", sync.manifest_bytes(attrs))
            archive.writestr("classes.dex", b"dex\n035\0payload")
            archive.writestr("extensions/duolingo.mpe", b"dex\n035\0extension")
        catalog = self.directory / "loaded-catalog.json"
        catalog.write_text(json.dumps({"version": version, "patches": [{"name": "Restore learning reminders", "default": False}]}))
        return bundle, catalog

    def test_successful_full_source_finalization_records_exact_provenance(self):
        # A lightweight stand-in isolates publication orchestration. The real
        # distribution verifier separately checks DEX and complete API metadata.
        self.put(self.fork, "tools/duolingo-reminders/verify_distribution.py", '''import json
def verify(root, loaded_catalog):
    endpoint = json.loads((root / 'patches-bundle.json').read_text())
    published = json.loads((root / 'patches-list.json').read_text())
    actual = json.loads(loaded_catalog.read_text())
    assert endpoint['version'] == published['version'] == actual['version']
    assert published['patches'] == actual['patches']
''')
        self.commit(self.fork, "distribution verifier fixture")
        sha = self.upstream_change("gradle.properties", "workers=2\nversion = 1.48.0\n")
        plan = self.prepare()
        bundle, catalog = self.built_fixture(plan["version"])
        result = sync.finalize(self.fork, self.plan_path, bundle, catalog, "full-source")
        self.assertTrue(result["finalized"])
        state = sync.read_json(self.fork / sync.STATE)
        self.assertEqual(state["upstream_commit"], sha)
        self.assertEqual(state["build_mode"], "full-source")
        self.assertFalse(state["release_parity"])
        endpoint = sync.read_json(self.fork / "patches-bundle.json")
        self.assertRegex(endpoint["created_at"], r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$")
        artifact = self.fork / result["artifact"]
        self.assertIn("1.48.0-mod.1", endpoint["download_url"])
        with zipfile.ZipFile(artifact) as archive:
            attrs = sync.manifest_attributes(archive.read("META-INF/MANIFEST.MF"))
        self.assertEqual(attrs["Source"], sync.FORK_URL)
        self.assertIn("6.102.4", attrs["Description"])
        self.assertEqual(command(self.fork, "rev-parse", "HEAD"), plan["original_head"])

    def test_wrong_built_version_does_not_replace_endpoint(self):
        self.upstream_change()
        self.prepare()
        before = (self.fork / "patches-bundle.json").read_bytes()
        bundle, catalog = self.built_fixture("1.47.0-mod.2")
        with self.assertRaisesRegex(sync.SyncError, "planned mod version"):
            sync.finalize(self.fork, self.plan_path, bundle, catalog, "full-source")
        self.assertEqual((self.fork / "patches-bundle.json").read_bytes(), before)
        self.assertFalse((self.fork / "dist").exists())


if __name__ == "__main__":
    unittest.main()
