"""Check the committed Manager source endpoint without private Maven credentials."""

import hashlib
import json
import re
import struct
import zipfile
from argparse import ArgumentParser
from datetime import datetime
from pathlib import Path


DUOLINGO_PATCHES = {
    "Disable dynamic app icon",
    "Enable debug mode",
    "Enable Premium",
    "Restore learning reminders",
    "Unlimited RampUp time",
}
UNIVERSAL_PATCHES = {
    "Change package name",
    "Disable Pairip license check",
    "Enable debug",
    "Hide app icon",
    "MicroG integration",
}


def dex_classes(data: bytes) -> set[str]:
    """Read class descriptors, detecting duplicate definitions in the Android bundle."""
    assert data.startswith(b"dex\n"), "Missing Android DEX header"
    string_count, string_offset = struct.unpack_from("<II", data, 56)
    type_count, type_offset = struct.unpack_from("<II", data, 64)
    class_count, class_offset = struct.unpack_from("<II", data, 96)
    strings = []
    for index in range(string_count):
        offset = struct.unpack_from("<I", data, string_offset + 4 * index)[0]
        while data[offset] & 128:  # Skip the ULEB128 UTF-16 length.
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(b"\0", offset)])
    types = [
        strings[struct.unpack_from("<I", data, type_offset + 4 * index)[0]]
        for index in range(type_count)
    ]
    classes = [
        types[struct.unpack_from("<I", data, class_offset + 32 * index)[0]].decode("ascii")
        for index in range(class_count)
    ]
    assert len(set(classes)) == len(classes), "Duplicate DEX class definitions"
    return set(classes)


def verify(root: Path, loaded_catalog: Path | None = None) -> None:
    metadata = json.loads((root / "patches-bundle.json").read_text(encoding="utf-8-sig"))
    catalog = json.loads((root / "patches-list.json").read_text(encoding="utf-8-sig"))
    upstream = json.loads((root / "tools/duolingo-reminders/upstream.json").read_text(encoding="utf-8"))
    version = metadata["version"]
    assert re.fullmatch(r"\d+\.\d+\.\d+", upstream["version"]), "Invalid upstream version"
    assert re.fullmatch(re.escape(upstream["version"]) + r"-mod\.[1-9]\d*", version), (
        "Expected <upstream-version>-mod.<positive-revision>"
    )
    if upstream.get("download_url") is None or upstream.get("sha256") is None:
        state = json.loads((root / "tools/upstream-sync/state.json").read_text())
        assert state["build_mode"] == "full-source" and not state["release_parity"], (
            "Unreleased upstream commits require a full source build"
        )
        assert upstream["source_commit"] == state["upstream_commit"], "Unreleased source pin differs"
        assert upstream.get("download_url") is None and upstream.get("sha256") is None
    else:
        assert upstream["download_url"] == (
            f"https://github.com/hoo-dles/morphe-patches/releases/download/"
            f"v{upstream['version']}/patches-{upstream['version']}.mpp"
        ), "Pinned bundle must match the upstream version"
        assert re.fullmatch(r"[0-9a-f]{64}", upstream["sha256"]), "Missing upstream bundle hash"
    gradle_version = re.search(r"(?m)^version\s*=\s*(\S+)", (root / "gradle.properties").read_text()).group(1)
    assert gradle_version == version, "Gradle and published bundle versions differ"
    datetime.fromisoformat(metadata["created_at"])
    relative = f"dist/patches-{version}.mpp"
    assert metadata["download_url"] == (
        f"https://raw.githubusercontent.com/BeForU/morphe-patches/main/{relative}"
    ), "Repository source must serve this fork's committed bundle"
    assert not metadata.get("signature_download_url"), "No detached signature is published"
    assert metadata["description"], "Manager changelog must describe this update"
    assert catalog["version"] == version, "Catalog and remote metadata versions differ"
    patches = catalog["patches"]
    assert patches, "The distributed catalog must not be empty"
    duolingo = {
        patch["name"]: patch for patch in patches
        if any(package["packageName"] == "com.duolingo"
               for package in patch["compatiblePackages"] or [])
    }
    assert DUOLINGO_PATCHES <= set(duolingo), "Missing required Duolingo patches"
    assert duolingo["Restore learning reminders"]["default"] is False
    universal = {patch["name"] for patch in patches if patch["compatiblePackages"] is None}
    assert UNIVERSAL_PATCHES <= universal, "Required universal patches must be in the same source"
    if loaded_catalog:
        loaded = json.loads(loaded_catalog.read_text(encoding="utf-8-sig"))
        if isinstance(loaded, dict):
            assert loaded["version"] == version, "Loaded catalog and endpoint versions differ"
            # Catalog.kt exports the complete schema rather than a reduced test snapshot.
            assert sorted(loaded["patches"], key=lambda p: (p["name"], str(p["compatiblePackages"]))) == sorted(
                patches, key=lambda p: (p["name"], str(p["compatiblePackages"]))
            ), "Published catalog differs from the finished bundle"
            loaded = [
                dict(p, compatibility=None if p["compatiblePackages"] is None else [
                    dict(c, targets=[t["version"] for t in c["targets"]])
                    for c in p["compatiblePackages"]
                ]) for p in loaded["patches"]
            ]

        def identity(patch: dict) -> tuple:
            packages = patch.get("compatibility", patch.get("compatiblePackages"))
            return patch["name"], tuple(sorted(p["packageName"] for p in packages or []))

        actual = {identity(patch): patch for patch in loaded}
        declared = {identity(patch): patch for patch in patches}
        assert len(actual) == len(declared) == len(patches), "Duplicate or missing catalog entries"
        assert actual.keys() == declared.keys(), "Catalog patch names/packages differ from the loader"
        for key, patch in declared.items():
            runtime = actual[key]
            for field in ("name", "description", "default", "dependencies"):
                assert patch[field] == runtime[field], f"Catalog differs from loader: {key}, {field}"
            expected_packages = patch["compatiblePackages"]
            if expected_packages is None:
                assert runtime["compatibility"] is None
            else:
                assert [
                    (p["packageName"], p["name"], [t["version"] for t in p["targets"]])
                    for p in expected_packages
                ] == [
                    (p["packageName"], p["name"], p["targets"])
                    for p in runtime["compatibility"]
                ], f"Catalog compatibility differs from loader: {key}"
            assert len(patch["options"]) == len(runtime["options"])
            for expected, option in zip(patch["options"], runtime["options"]):
                for field in ("key", "default", "values", "required"):
                    assert expected.get(field) == option.get(field), f"Catalog option differs: {key}, {field}"
    source = (root / "patches/src/main/kotlin/hoodles/morphe/compatibility/Constants.kt").read_text()
    app_version = re.search(
        r'val DUOLINGO = Compatibility\(.*?AppTarget\("([^"\r\n]+)"\)', source, re.S
    ).group(1)
    for patch in (duolingo[name] for name in DUOLINGO_PATCHES):
        package = next(p for p in patch["compatiblePackages"] if p["packageName"] == "com.duolingo")
        assert [target["version"] for target in package["targets"]] == [app_version]
    bundle = root / relative
    with zipfile.ZipFile(bundle) as archive:
        assert archive.testzip() is None, "Corrupt Manager bundle"
        names = archive.namelist()
        assert len(names) == len(set(names)), "Duplicate ZIP entries"
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
        # JAR manifests fold lines at 72 bytes; continuation lines start with a space.
        manifest = manifest.replace("\r\n ", "").replace("\n ", "")
        attributes = dict(line.split(": ", 1) for line in manifest.splitlines() if ": " in line)
        assert attributes["Version"] == version, "Manifest and endpoint versions differ"
        assert attributes["Name"] == "Hoodles Morphe Patches (Modded)"
        assert app_version in attributes["Description"]
        assert attributes["Source"] == "https://github.com/BeForU/morphe-patches"
        patcher_version = re.search(
            r'(?m)^morphe-patcher\s*=\s*"([^"]+)"',
            (root / "gradle/libs.versions.toml").read_text(),
        ).group(1)
        assert attributes["Patcher-Version"] == patcher_version, "Bundle API differs from the source dependency"
        dex_names = [name for name in names if re.fullmatch(r"classes(?:\d+)?\.dex", name)]
        assert "classes.dex" in dex_names
        android_classes: set[str] = set()
        for name in dex_names:
            classes = dex_classes(archive.read(name))
            assert not android_classes.intersection(classes), "Duplicate classes across DEX files"
            android_classes.update(classes)
        jvm_classes = {"L" + name[:-6] + ";" for name in names if name.endswith(".class")}
        assert jvm_classes <= android_classes, "Some JVM classes are missing from Manager's DEX"
        for relative_class in (
            "patches/duolingo/misc/notifications/RestoreLearningRemindersPatchKt",
            "patches/duolingo/shared/integrity/BasicLoginFingerprint",
            "patches/duolingo/premium/EnergyConfigCtorFingerprint",
            "patches/all/microg/MicroGSupportPatchKt",
            "patches/all/microg/FirebaseInstallationsSupportPatchKt",
        ):
            assert f"hoodles/morphe/{relative_class}.class" in names
        for extension in ("duolingo", "all/microg", "shared"):
            assert archive.read(f"extensions/{extension}.mpe").startswith(b"dex\n")
    print(f"Manager source OK: {version}, {len(patches)} patches, Duolingo {app_version}, {bundle.stat().st_size} bytes")
    print(f"SHA-256: {hashlib.sha256(bundle.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--loaded-catalog", type=Path, help="bundle-metadata.json written by Validate.kt")
    verify(Path(__file__).resolve().parents[2], parser.parse_args().loaded_catalog)
