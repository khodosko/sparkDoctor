#!/usr/bin/env python3

import argparse
import io
import re
import sys
import zipfile
from datetime import date
from pathlib import Path, PurePosixPath
from typing import Optional


VERSION_KEY = "sparkDoctorVersion"
RELEASE_VERSION_PATTERN = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+")
REQUIRED_ARCHIVE_PATHS = (
    "bin/sparkdoctor",
    "bin/sparkdoctor.bat",
    "README.md",
    "LICENSE",
    "CHANGELOG.md",
    "CONTRIBUTING.md",
    "ROADMAP.md",
    "docs/detections.md",
    "docs/development.md",
    "docs/event-logs.md",
    "docs/output.md",
)
FORBIDDEN_PATH_PARTS = frozenset(
    {
        ".git",
        ".github",
        ".agents",
        ".codex",
        "guidance",
        "scripts",
        "src",
        "superpowers",
    }
)
FORBIDDEN_FILE_NAMES = frozenset(
    {".ds_store", "agents.md", "developmentstandards.md", "statustracker.md"}
)


class VerificationError(RuntimeError):
    pass


def read_project_version(properties_path: Path) -> str:
    values = []
    for raw_line in properties_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() == VERSION_KEY:
            values.append(value.strip())

    if len(values) != 1 or not values[0]:
        raise VerificationError(
            f"{properties_path} must define exactly one non-empty {VERSION_KEY}"
        )
    return values[0]


def verify_release_tag(version: str, tag: str) -> None:
    if not RELEASE_VERSION_PATTERN.fullmatch(version):
        raise VerificationError(
            f"Release version must be MAJOR.MINOR.PATCH without a qualifier; found {version!r}"
        )

    expected_tag = f"v{version}"
    if tag != expected_tag:
        raise VerificationError(
            f"Release tag {tag!r} does not match project version; expected {expected_tag!r}"
        )


def _verify_safe_archive_paths(names: list[str], root: str) -> None:
    if len(names) != len(set(names)):
        raise VerificationError("Release archive contains duplicate entries")

    prefix = f"{root}/"
    for name in names:
        if "\\" in name or name.startswith("/"):
            raise VerificationError(f"Release archive contains an unsafe path: {name!r}")

        path = PurePosixPath(name)
        if ".." in path.parts or (name != root and not name.startswith(prefix)):
            raise VerificationError(f"Release archive contains an unsafe path: {name!r}")

        relative_parts = path.parts[1:]
        lowered_parts = tuple(part.lower() for part in relative_parts)
        if FORBIDDEN_PATH_PARTS.intersection(lowered_parts):
            raise VerificationError(f"Release archive contains forbidden content: {name!r}")
        if lowered_parts and lowered_parts[-1] in FORBIDDEN_FILE_NAMES:
            raise VerificationError(f"Release archive contains forbidden content: {name!r}")
        if "sparkdoctor-pro" in name.lower():
            raise VerificationError(f"Release archive contains forbidden content: {name!r}")


def verify_release_archive(archive_path: Path, version: str) -> None:
    expected_name = f"sparkdoctor-{version}.zip"
    if archive_path.name != expected_name:
        raise VerificationError(
            f"Release archive must be named {expected_name!r}; found {archive_path.name!r}"
        )
    if not archive_path.is_file():
        raise VerificationError(f"Release archive does not exist: {archive_path}")

    root = f"sparkdoctor-{version}"
    with zipfile.ZipFile(archive_path) as archive:
        names = archive.namelist()
        _verify_safe_archive_paths(names, root)

        missing = [
            f"{root}/{relative}"
            for relative in REQUIRED_ARCHIVE_PATHS
            if f"{root}/{relative}" not in names
        ]
        application_jar = f"{root}/lib/sparkDoctor-{version}.jar"
        if application_jar not in names:
            missing.append(application_jar)
        if missing:
            raise VerificationError(
                "Release archive is missing required content: " + ", ".join(missing)
            )

        if RELEASE_VERSION_PATTERN.fullmatch(version):
            try:
                readme = archive.read(f"{root}/README.md").decode("utf-8")
                changelog = archive.read(f"{root}/CHANGELOG.md").decode("utf-8")
            except UnicodeDecodeError as exception:
                raise VerificationError(
                    "README and CHANGELOG must be UTF-8 text"
                ) from exception
            readme_versions = re.findall(
                r"^SPARKDOCTOR_VERSION=([^\s]+)$", readme, re.MULTILINE
            )
            if not readme_versions or any(
                readme_version != version for readme_version in readme_versions
            ):
                raise VerificationError(
                    "README release installation version does not match the archive version"
                )
            changelog_heading = re.search(
                rf"^## {re.escape(version)} - (\d{{4}}-\d{{2}}-\d{{2}})$",
                changelog,
                re.MULTILINE,
            )
            if changelog_heading is None:
                raise VerificationError(
                    "CHANGELOG is missing a dated heading for the release version"
                )
            try:
                date.fromisoformat(changelog_heading.group(1))
            except ValueError as exception:
                raise VerificationError(
                    "CHANGELOG release heading contains an invalid date"
                ) from exception

        try:
            with zipfile.ZipFile(io.BytesIO(archive.read(application_jar))) as application:
                embedded = application.read("sparkdoctor-version.properties").decode("utf-8")
        except (KeyError, zipfile.BadZipFile, UnicodeDecodeError) as exception:
            raise VerificationError(
                "Application JAR is missing a readable sparkdoctor-version.properties"
            ) from exception

    embedded_properties = dict(
        line.split("=", 1)
        for line in embedded.splitlines()
        if line.strip() and not line.lstrip().startswith("#") and "=" in line
    )
    if embedded_properties.get("version", "").strip() != version:
        raise VerificationError(
            "Application JAR version does not match the release archive version"
        )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Verify SparkDoctor release tag, version, and archive contents."
    )
    parser.add_argument("--tag", help="Release tag, for example v0.1.5")
    parser.add_argument("--archive", type=Path, help="Distribution ZIP to inspect")
    parser.add_argument(
        "--properties",
        type=Path,
        default=Path("gradle.properties"),
        help="Path to gradle.properties (default: ./gradle.properties)",
    )
    parser.add_argument(
        "--print-version",
        action="store_true",
        help="Print only the verified project version",
    )
    return parser.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    try:
        version = read_project_version(args.properties)
        if args.tag is None and args.archive is None:
            raise VerificationError("Provide --tag, --archive, or both")
        if args.tag is not None:
            verify_release_tag(version, args.tag)
        if args.archive is not None:
            verify_release_archive(args.archive, version)
    except (OSError, VerificationError, zipfile.BadZipFile) as exception:
        print(f"Release verification failed: {exception}", file=sys.stderr)
        return 1

    if args.print_version:
        print(version)
    else:
        message = f"Verified SparkDoctor {version}"
        if args.tag is not None:
            message += f" release tag {args.tag}"
        if args.archive is not None:
            message += f" and archive {args.archive}"
        print(message)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
