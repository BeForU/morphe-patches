"""Merge upstream safely, then publish one verified modded Manager bundle.

The prepare/finalize split deliberately leaves committing and pushing to the
workflow: a failed build must never advance the public source endpoint.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
import zipfile


UPSTREAM = "https://github.com/hoo-dles/morphe-patches.git"
UPSTREAM_API = "https://api.github.com/repos/hoo-dles/morphe-patches"
BUNDLE_NAME = "Hoodles Morphe Patches (Modded)"
FORK_URL = "https://github.com/BeForU/morphe-patches"
STATE = "tools/upstream-sync/state.json"
PIN = "tools/duolingo-reminders/upstream.json"
COMPAT = "patches/src/main/kotlin/hoodles/morphe/compatibility/Constants.kt"
PROTECTED = (
    ".github/workflows/", ".releaserc", "README.md", "patches-bundle.json",
    "patches-list.json", "tools/duolingo-reminders/", "tools/upstream-sync/", "dist/",
)
CUSTOM = (
    "extensions/duolingo/",
    "patches/src/main/kotlin/hoodles/morphe/patches/duolingo/misc/notifications/",
    "patches/src/main/kotlin/hoodles/morphe/patches/all/microg/MicroGSupportPatch.kt",
    "patches/src/main/kotlin/hoodles/morphe/patches/all/microg/FirebaseInstallationsSupportPatch.kt",
)


class SyncError(RuntimeError):
    pass


def git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args], text=True, encoding="utf-8",
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    if check and result.returncode:
        raise SyncError(f"git {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout.strip()


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def tree_file(root: Path, ref: str, path: str) -> str:
    return git(root, "show", f"{ref}:{path}")


def base_version(gradle: str, metadata: dict) -> str:
    match = re.search(r"(?m)^version\s*=\s*(\S+)\s*$", gradle)
    for candidate in (match.group(1) if match else None, metadata.get("version")):
        if candidate and re.fullmatch(r"\d+\.\d+\.\d+", candidate):
            return candidate
    raise SyncError("Upstream main has no stable three-component base version; manual review is required.")


def next_version(base: str, current: str) -> str:
    match = re.fullmatch(re.escape(base) + r"-mod\.([1-9]\d*)", current)
    return f"{base}-mod.{int(match.group(1)) + 1 if match else 1}"


def targets(source: str) -> tuple[str, ...]:
    match = re.search(r"\bval DUOLINGO\s*=\s*Compatibility\((.*?)(?=\n\s*val |\n\})", source, re.S)
    versions = tuple(re.findall(r'AppTarget\("([^"\r\n]+)"', match.group(1))) if match else ()
    if not versions:
        raise SyncError("Cannot determine the validated Duolingo target versions.")
    return versions


def custom_hashes(root: Path) -> dict[str, str]:
    result = {}
    for prefix in CUSTOM:
        for name in git(root, "ls-files", "--", prefix).splitlines():
            path = root / name
            if not path.is_file():
                raise SyncError(f"Required custom patch file is missing: {name}")
            result[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    if not any("RestoreLearningRemindersPatch.kt" in name for name in result):
        raise SyncError("The custom learning reminder patch is missing.")
    if not any("LearningReminderFallback.java" in name for name in result):
        raise SyncError("The custom reminder runtime extension is missing.")
    for required in (path for path in CUSTOM if not path.endswith("/")):
        if required not in result:
            raise SyncError(f"Required custom MicroG support file is missing: {required}")
    return result


def is_protected(path: str) -> bool:
    return any(path.startswith(item) if item.endswith("/") else path == item for item in PROTECTED)


def request_json(url: str) -> dict | None:
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "BeForU-upstream-sync"}
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=90) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise SyncError(f"GitHub metadata request failed ({error.code}): {url}") from error


def stable_release(root: Path, remote: str = UPSTREAM) -> dict | None:
    release = request_json(f"{UPSTREAM_API}/releases/latest")
    if not release or release.get("draft") or release.get("prerelease"):
        return None
    match = re.fullmatch(r"v(\d+\.\d+\.\d+)", release["tag_name"])
    if not match:
        raise SyncError("Latest stable upstream release has an unsupported tag.")
    version = match.group(1)
    asset = next((item for item in release["assets"] if item["name"] == f"patches-{version}.mpp"), None)
    if not asset:
        raise SyncError("Latest stable upstream release has no Manager bundle.")
    digest = asset.get("digest", "")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise SyncError("Upstream release asset has no SHA-256 digest; refusing an unverified dependency.")
    git(root, "fetch", "--no-tags", remote, f"refs/tags/v{version}")
    commit = git(root, "rev-parse", "FETCH_HEAD^{commit}")
    return {"version": version, "download_url": asset["browser_download_url"],
            "sha256": digest.removeprefix("sha256:"), "commit": commit}


def replace_version(text: str, version: str) -> str:
    result, count = re.subn(r"(?m)^(version\s*=\s*)\S+\s*$", lambda m: m[1] + version, text)
    if count != 1:
        raise SyncError("Expected exactly one Gradle version property.")
    return result + ("\n" if not result.endswith("\n") else "")


def strip_fork_setting(path: str, text: str) -> str:
    if path == "gradle.properties":
        return re.sub(r"(?m)^version\s*=\s*\S+\s*$", "version = <version>", text).strip()
    return about_metadata(text, "<fork-name>", "<fork-source>").strip()


def about_metadata(text: str, name: str, source: str) -> str:
    block = re.search(r"\babout\s*\{([^{}]*)\}", text, re.S)
    if not block:
        raise SyncError("Cannot identify the bundle about metadata in the Gradle build.")
    content = block[1]
    for key, value in (("name", name), ("source", source)):
        content, count = re.subn(r'(?m)^([ \t]*' + key + r'\s*=\s*)"[^"\r\n]+"',
                                  lambda m: m[1] + f'"{value}"', content)
        if count != 1:
            raise SyncError(f"Cannot preserve the bundle {key} in the Gradle about metadata.")
    return text[:block.start(1)] + content + text[block.end(1):]


def reconcile_setting(root: Path, path: str, head: str, baseline: str, upstream: str, version: str) -> None:
    original = tree_file(root, head, path)
    previous = tree_file(root, baseline, path)
    if strip_fork_setting(path, original) != strip_fork_setting(path, previous):
        # Never replace substantive fork build changes with upstream's file.
        if path in git(root, "diff", "--name-only", "--diff-filter=U").splitlines():
            raise SyncError(f"Substantive local changes conflict with upstream in {path}; review manually.")
        text = (root / path).read_text(encoding="utf-8")
    else:
        text = tree_file(root, upstream, path)
    if path == "gradle.properties":
        text = replace_version(text, version)
    else:
        text = about_metadata(text, BUNDLE_NAME, FORK_URL)
        text += "\n" if not text.endswith("\n") else ""
    (root / path).write_text(text, encoding="utf-8")
    git(root, "add", "--", path)


def github_outputs(plan: dict) -> None:
    destination = os.environ.get("GITHUB_OUTPUT")
    if destination:
        with open(destination, "a", encoding="utf-8") as output:
            for key in ("changed", "version", "upstream_sha", "release_parity"):
                value = plan.get(key, "")
                output.write(f"{key}={str(value).lower() if isinstance(value, bool) else value}\n")


def prepare(root: Path, plan_path: Path, rebuild: bool = False, remote: str = UPSTREAM,
            release: dict | None = None, discover_release: bool = True) -> dict:
    if git(root, "status", "--porcelain"):
        raise SyncError("Prepare requires a clean checkout, including no untracked files.")
    head = git(root, "rev-parse", "HEAD")
    state = read_json(root / STATE)
    baseline = state.get("upstream_commit") or state.get("last_upstream_sha")
    if not baseline or not re.fullmatch(r"[0-9a-f]{40}", baseline):
        raise SyncError("The sync state must record the previously incorporated upstream commit.")
    git(root, "fetch", "--no-tags", remote, "main")
    upstream = git(root, "rev-parse", "FETCH_HEAD^{commit}")
    # merge-base --is-ancestor has no output on either result; use its status.
    ancestor = subprocess.run(["git", "-C", str(root), "merge-base", "--is-ancestor", baseline, upstream],
                              stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    if ancestor.returncode:
        raise SyncError("Upstream main no longer descends from the pinned sync commit; refusing a history rewrite.")
    metadata = read_json(root / "patches-bundle.json")
    changed = upstream != baseline or rebuild
    if not changed:
        plan = {"changed": False, "version": metadata["version"], "upstream_sha": upstream,
                "release_parity": False, "original_head": head}
        write_json(plan_path, plan)
        github_outputs(plan)
        print("Upstream is unchanged; retaining the published mod bundle.")
        return plan
    upstream_metadata = json.loads(tree_file(root, upstream, "patches-bundle.json"))
    base = base_version(tree_file(root, upstream, "gradle.properties"), upstream_metadata)
    version = next_version(base, metadata["version"])
    if discover_release:
        release = stable_release(root, remote)
    parity = bool(release and release["commit"] == upstream and release["version"] == base)
    original_hashes = custom_hashes(root)
    original_targets = targets((root / COMPAT).read_text(encoding="utf-8"))
    try:
        # A real merge retains both histories and gives unrelated code normal
        # three-way treatment. Only fork-owned publication files are restored.
        result = subprocess.run(["git", "-C", str(root), "merge", "--no-ff", "--no-commit", upstream],
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        conflicts = git(root, "diff", "--name-only", "--diff-filter=U").splitlines()
        allowed = {"gradle.properties", "patches/build.gradle.kts"}
        unresolved = [path for path in conflicts if not is_protected(path) and path not in allowed]
        if unresolved:
            raise SyncError("Upstream code conflicts require review: " + ", ".join(unresolved))
        if result.returncode and not conflicts:
            raise SyncError("Upstream merge failed: " + result.stderr.strip())
        for path in PROTECTED:
            # Prefixes may contain upstream additions; restore removes those,
            # too, instead of allowing imported workflow files to run.
            if git(root, "ls-files", "--", path):
                git(root, "restore", f"--source={head}", "--staged", "--worktree", "--", path)
        reconcile_setting(root, "gradle.properties", head, baseline, upstream, version)
        reconcile_setting(root, "patches/build.gradle.kts", head, baseline, upstream, version)
        if git(root, "diff", "--name-only", "--diff-filter=U"):
            raise SyncError("Unresolved upstream merge conflicts remain.")
        if custom_hashes(root) != original_hashes:
            raise SyncError("Upstream modified the custom patch implementation; manual review is required.")
        if targets((root / COMPAT).read_text(encoding="utf-8")) != original_targets:
            raise SyncError("Upstream changed the validated Duolingo target; validate the new APK manually first.")
        if parity:
            write_json(root / PIN, {key: release[key] for key in ("version", "download_url", "sha256")})
        else:
            # A source-only main revision has no corresponding public binary.
            # Do not retain an older release pin which would silently omit it.
            write_json(root / PIN, {"version": base, "source_commit": upstream,
                                    "download_url": None, "sha256": None})
        plan = {
            "changed": True, "version": version, "upstream_version": base,
            "upstream_sha": upstream, "original_head": head, "previous_upstream_sha": baseline,
            "release_parity": parity, "release": release, "prepared_at": now(),
            "custom_hashes": original_hashes, "duolingo_targets": list(original_targets),
        }
        write_json(plan_path, plan)
        github_outputs(plan)
        print(f"Prepared {version} from upstream {upstream}; release parity: {parity}.")
        return plan
    except Exception:
        merge_head = Path(git(root, "rev-parse", "--git-path", "MERGE_HEAD"))
        if not merge_head.is_absolute():
            merge_head = root / merge_head
        if merge_head.exists():
            git(root, "merge", "--abort")
        else:
            # No merge is started for a rebuild of an already merged commit.
            # Initial cleanliness makes restoring tracked files safe.
            git(root, "restore", f"--source={head}", "--staged", "--worktree", "--", ".")
        raise


def manifest_attributes(content: bytes) -> dict[str, str]:
    text = content.decode("utf-8").replace("\r\n", "\n").replace("\n ", "")
    return dict(line.split(": ", 1) for line in text.split("\n\n", 1)[0].splitlines() if ": " in line)


def manifest_bytes(attributes: dict[str, str]) -> bytes:
    lines = []
    for key, value in attributes.items():
        raw = f"{key}: {value}".encode("utf-8")
        # Fold by bytes (manifest limit), retaining UTF-8 codepoint boundaries.
        while len(raw) > 70:
            end = 70
            while raw[end] & 0xC0 == 0x80:
                end -= 1
            lines.append(raw[:end])
            raw = b" " + raw[end:]
        lines.append(raw)
    return b"\r\n".join(lines) + b"\r\n\r\n"


def normalize_bundle(source: Path, target: Path, version: str, description: str | None = None) -> None:
    temporary = target.with_suffix(".tmp")
    with zipfile.ZipFile(source) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names) or archive.testzip() is not None:
            raise SyncError("Built bundle contains duplicate or corrupt archive entries.")
        attrs = manifest_attributes(archive.read("META-INF/MANIFEST.MF"))
        if attrs.get("Version") != version or attrs.get("Name") != BUNDLE_NAME:
            raise SyncError("Built manifest must contain the planned mod version and modded display name.")
        for required in ("classes.dex", "extensions/duolingo.mpe"):
            if required not in names or not archive.read(required).startswith(b"dex\n"):
                raise SyncError(f"Built bundle is missing its Android payload: {required}")
        attrs["Source"] = FORK_URL
        attrs["Contact"] = FORK_URL + "/issues"
        attrs["Website"] = FORK_URL
        if description is not None:
            attrs["Description"] = description
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
                for entry in archive.infolist():
                    payload = manifest_bytes(attrs) if entry.filename == "META-INF/MANIFEST.MF" else archive.read(entry)
                    output.writestr(entry, payload)
        except Exception:
            temporary.unlink(missing_ok=True)
            raise
    # Close the input archive before replacing it, including when source and
    # target are the same path (Windows disallows replacing an open file).
    try:
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def normalize_catalog(value: dict | list, version: str) -> dict:
    if isinstance(value, dict):
        result = dict(value)
        if not isinstance(result.get("patches"), list):
            raise SyncError("Exported catalog has no patches array.")
    else:
        patches = []
        for patch in value:
            patch = dict(patch)
            packages = patch.pop("compatibility", patch.get("compatiblePackages"))
            if packages is not None:
                packages = [dict(package, targets=[{"version": target} if isinstance(target, str) else target
                                                   for target in package["targets"]]) for package in packages]
            patch["compatiblePackages"] = packages
            patches.append(patch)
        result = {"patches": patches}
    result["NOTE"] = "Generated from the actual modded bundle; do not edit patch entries manually."
    result["version"] = version
    return result


def finalize(root: Path, plan_path: Path, bundle: Path, catalog: Path, build_mode: str) -> dict:
    plan = read_json(plan_path)
    if not plan.get("changed"):
        raise SyncError("There is no prepared upstream update to finalize.")
    if git(root, "rev-parse", "HEAD") != plan["original_head"]:
        raise SyncError("Checkout HEAD changed after prepare; refuse publishing a stale plan.")
    if build_mode == "release-overlay" and not plan["release_parity"]:
        raise SyncError("Release-overlay cannot publish unpublished main changes; full-source build is required.")
    if custom_hashes(root) != plan["custom_hashes"]:
        raise SyncError("Custom patch files changed after prepare.")
    if list(targets((root / COMPAT).read_text(encoding="utf-8"))) != plan["duolingo_targets"]:
        raise SyncError("The validated Duolingo target changed after prepare.")
    exported = normalize_catalog(json.loads(catalog.read_text(encoding="utf-8-sig")), plan["version"])
    reminder = [patch for patch in exported["patches"] if patch["name"] == "Restore learning reminders"]
    if len(reminder) != 1:
        raise SyncError("The loaded bundle must contain exactly one Restore learning reminders patch.")
    version = plan["version"]
    relative = f"dist/patches-{version}.mpp"
    artifact = root / relative
    normalize_bundle(bundle, artifact, version,
                     f"Hoodles {plan['upstream_version']} patches with Duolingo "
                     f"{', '.join(plan['duolingo_targets'])} and local reminder fallback")
    timestamp = now()
    sha256 = hashlib.sha256(artifact.read_bytes()).hexdigest()
    write_json(root / "patches-list.json", exported)
    write_json(root / "patches-bundle.json", {
        # Manager's MorpheAsset uses kotlinx.datetime.LocalDateTime, which
        # rejects an offset or Z suffix. Store UTC wall time without the offset.
        "created_at": timestamp.removesuffix("+00:00"),
        "description": f"# {version}\n\n* Automatically incorporate hoo-dles/morphe-patches main "
                       f"at {plan['upstream_sha']}.\n* Preserve Restore learning reminders and the validated "
                       f"Duolingo targets: {', '.join(plan['duolingo_targets'])}.\n"
                       f"* Bundle: {BUNDLE_NAME}. Build mode: {build_mode}.",
        "download_url": f"https://raw.githubusercontent.com/BeForU/morphe-patches/main/{relative}",
        "page_url": FORK_URL, "version": version,
    })
    gradle = root / "gradle.properties"
    gradle.write_text(replace_version(gradle.read_text(encoding="utf-8"), version), encoding="utf-8")
    write_json(root / STATE, {
        "upstream_commit": plan["upstream_sha"], "upstream_version": plan["upstream_version"],
        "version": version, "build_mode": build_mode,
        "release_commit": (plan.get("release") or {}).get("commit"),
        "release_parity": plan["release_parity"], "updated_at": timestamp, "bundle_sha256": sha256,
    })
    verifier_path = root / "tools/duolingo-reminders/verify_distribution.py"
    specification = importlib.util.spec_from_file_location("mod_distribution_verifier", verifier_path)
    verifier = importlib.util.module_from_spec(specification)
    previous_bytecode_setting = sys.dont_write_bytecode
    try:
        sys.dont_write_bytecode = True
        specification.loader.exec_module(verifier)
    finally:
        sys.dont_write_bytecode = previous_bytecode_setting
    verifier.verify(root, catalog)
    plan.update({"finalized": True, "build_mode": build_mode, "bundle_sha256": sha256,
                 "artifact": relative, "finalized_at": timestamp})
    write_json(plan_path, plan)
    print(f"Verified {version}; ready to commit and push the merged source and bundle together.")
    return plan


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    commands = parser.add_subparsers(dest="command", required=True)
    preparation = commands.add_parser("prepare")
    preparation.add_argument("--plan", type=Path, required=True)
    preparation.add_argument("--rebuild", action="store_true")
    status = commands.add_parser("status")
    status.add_argument("--plan", type=Path, required=True)
    finalization = commands.add_parser("finalize")
    finalization.add_argument("--plan", type=Path, required=True)
    finalization.add_argument("--bundle", type=Path, required=True)
    finalization.add_argument("--catalog", type=Path, required=True)
    finalization.add_argument("--build-mode", choices=("full-source", "release-overlay"), required=True)
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            prepare(args.root.resolve(), args.plan.resolve(), args.rebuild)
        elif args.command == "status":
            plan = read_json(args.plan)
            github_outputs(plan)
            print(json.dumps(plan, ensure_ascii=False, indent=2))
        else:
            finalize(args.root.resolve(), args.plan.resolve(), args.bundle.resolve(), args.catalog.resolve(), args.build_mode)
    except (SyncError, OSError, ValueError, zipfile.BadZipFile, AssertionError) as error:
        print(f"Upstream sync stopped: {error}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
