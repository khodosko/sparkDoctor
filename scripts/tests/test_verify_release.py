import io
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from typing import Optional

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import verify_release


class VerifyReleaseTest(unittest.TestCase):
    def test_project_version_must_be_defined_exactly_once(self):
        with tempfile.TemporaryDirectory() as directory:
            properties = Path(directory) / "gradle.properties"
            properties.write_text("sparkDoctorVersion=0.1.5\n", encoding="utf-8")

            self.assertEqual("0.1.5", verify_release.read_project_version(properties))

            properties.write_text(
                "sparkDoctorVersion=0.1.5\nsparkDoctorVersion=0.1.5\n",
                encoding="utf-8",
            )
            with self.assertRaises(verify_release.VerificationError):
                verify_release.read_project_version(properties)

    def test_release_tag_must_match_non_snapshot_version(self):
        verify_release.verify_release_tag("0.1.5", "v0.1.5")

        with self.assertRaises(verify_release.VerificationError):
            verify_release.verify_release_tag("0.1.5-SNAPSHOT", "v0.1.5")
        with self.assertRaises(verify_release.VerificationError):
            verify_release.verify_release_tag("0.1.5", "v0.1.4")

    def test_archive_contains_public_distribution_and_matching_version(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive)

            verify_release.verify_release_archive(archive, "0.1.5")

    def test_archive_only_cli_accepts_snapshot_version(self):
        with tempfile.TemporaryDirectory() as directory:
            properties = Path(directory) / "gradle.properties"
            properties.write_text(
                "sparkDoctorVersion=0.1.5-SNAPSHOT\n", encoding="utf-8"
            )
            archive = Path(directory) / "sparkdoctor-0.1.5-SNAPSHOT.zip"
            self._write_archive(
                archive,
                version="0.1.5-SNAPSHOT",
                embedded_version="0.1.5-SNAPSHOT",
            )

            exit_code = verify_release.main(
                ["--properties", str(properties), "--archive", str(archive)]
            )

            self.assertEqual(0, exit_code)

    def test_archive_rejects_missing_required_content(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, omitted="README.md")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_archive_rejects_internal_content(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, extra="guidance/owner-notes.md")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_archive_rejects_mismatched_embedded_version(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, embedded_version="0.1.4")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_release_archive_rejects_stale_readme_version(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, readme_version="0.1.4")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_release_archive_rejects_missing_changelog_heading(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, changelog_version="0.1.4")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_release_archive_rejects_nondated_changelog_heading(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, changelog_date="TBD")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_archive_rejects_wrong_file_name(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-release.zip"
            self._write_archive(archive)

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_archive_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, extra="../owner-notes.md")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_archive_rejects_duplicate_entries(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive)
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(archive, "a") as distribution:
                    distribution.writestr("sparkdoctor-0.1.5/README.md", "duplicate\n")

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    def test_archive_rejects_unreadable_application_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / "sparkdoctor-0.1.5.zip"
            self._write_archive(archive, readable_jar=False)

            with self.assertRaises(verify_release.VerificationError):
                verify_release.verify_release_archive(archive, "0.1.5")

    @staticmethod
    def _write_archive(
        archive_path: Path,
        omitted: Optional[str] = None,
        extra: Optional[str] = None,
        embedded_version: str = "0.1.5",
        readable_jar: bool = True,
        version: str = "0.1.5",
        readme_version: Optional[str] = None,
        changelog_version: Optional[str] = None,
        changelog_date: str = "2026-01-01",
    ) -> None:
        root = f"sparkdoctor-{version}"
        jar_bytes = io.BytesIO()
        with zipfile.ZipFile(jar_bytes, "w") as application:
            application.writestr(
                "sparkdoctor-version.properties", f"version={embedded_version}\n"
            )

        with zipfile.ZipFile(archive_path, "w") as archive:
            for relative in verify_release.REQUIRED_ARCHIVE_PATHS:
                if relative != omitted:
                    content = "test\n"
                    if relative == "README.md":
                        content = f"SPARKDOCTOR_VERSION={readme_version or version}\n"
                    elif relative == "CHANGELOG.md":
                        content = f"## {changelog_version or version} - {changelog_date}\n"
                    archive.writestr(f"{root}/{relative}", content)
            archive.writestr(
                f"{root}/lib/sparkDoctor-{version}.jar",
                jar_bytes.getvalue() if readable_jar else b"not-a-zip",
            )
            if extra is not None:
                archive.writestr(f"{root}/{extra}", "internal\n")


if __name__ == "__main__":
    unittest.main()
