"""Check the committed Manager source endpoint without private Maven credentials."""

import hashlib
import json
import re
import zipfile
from datetime import datetime
from pathlib import Path


def verify(root: Path) -> None:
    metadata = json.loads((root / "patches-bundle.json").read_text(encoding="utf-8-sig"))
    catalog = json.loads((root / "patches-list.json").read_text(encoding="utf-8-sig"))
    version = metadata["version"]
    assert re.fullmatch(r"\d+\.\d+\.\d+", version), "Expected a stable bundle version"
    datetime.fromisoformat(metadata["created_at"])
    relative = f"dist/duolingo-reminders-{version}.mpp"
    assert metadata["download_url"] == (
        f"https://raw.githubusercontent.com/BeForU/morphe-patches/main/{relative}"
    ), "Repository source must serve this fork's committed bundle"
    assert not metadata.get("signature_download_url"), "No detached signature is published"
    assert metadata["description"], "Manager changelog must describe this update"
    assert catalog["version"] == version, "Catalog and remote metadata versions differ"
    assert len(catalog["patches"]) == 1, "This distribution is a single-patch supplement"
    patch = catalog["patches"][0]
    assert patch["name"] == "Restore learning reminders"
    assert patch["default"] is False
    package = patch["compatiblePackages"][0]
    assert package["packageName"] == "com.duolingo"
    source = (root / "patches/src/main/kotlin/hoodles/morphe/compatibility/Constants.kt").read_text()
    app_version = re.search(
        r'val DUOLINGO = Compatibility\(.*?AppTarget\("([^"\r\n]+)"\)', source, re.S
    ).group(1)
    assert package["targets"][0]["version"] == app_version
    bundle = root / relative
    with zipfile.ZipFile(bundle) as archive:
        assert archive.testzip() is None, "Corrupt Manager bundle"
        manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
        # JAR manifests fold lines at 72 bytes; continuation lines start with a space.
        manifest = manifest.replace("\r\n ", "").replace("\n ", "")
        attributes = dict(line.split(": ", 1) for line in manifest.splitlines() if ": " in line)
        assert attributes["Version"] == version, "Manifest and endpoint versions differ"
        assert attributes["Name"] == "Duolingo Learning Reminders"
        assert app_version in attributes["Description"]
        assert attributes["Source"] == "https://github.com/BeForU/morphe-patches"
        assert attributes["Patcher-Version"] == "1.14.1"
        assert archive.read("classes.dex").startswith(b"dex\n")
        assert archive.read("extensions/duolingo.mpe").startswith(b"dex\n")
        assert any(name.endswith("RestoreLearningRemindersPatchKt.class") for name in archive.namelist())
    print(f"Manager source OK: {version}, Duolingo {app_version}, {bundle.stat().st_size} bytes")
    print(f"SHA-256: {hashlib.sha256(bundle.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    verify(Path(__file__).resolve().parents[2])
