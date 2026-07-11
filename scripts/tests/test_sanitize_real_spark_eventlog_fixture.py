import sys
import tempfile
import unittest
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sanitize_real_spark_eventlog_fixture import sanitize_file, sanitize_text, validate_sanitized_text


class SanitizeRealSparkEventLogFixtureTest(unittest.TestCase):
    def test_sanitizes_machine_paths_user_and_private_addresses(self):
        content = "\n".join(
            (
                '{"Event":"SparkListenerApplicationStart","App ID":"local-12345",'
                '"User":"alice","Path":"/Users/alice/work/sparkDoctor",'
                '"Java Home":"/opt/homebrew/Cellar/openjdk@21/21.0.1",'
                '"Driver":"10.1.2.3","IPv6":"[fe80::1234%en0]"}',
                '{"Event":"SparkListenerEnvironmentUpdate",'
                '"LinuxPath":"/home/alice/spark",'
                '"WindowsPath":"C:\\\\Users\\\\Alice Smith\\\\work\\\\sparkDoctor",'
                '"LowerWindowsPath":"c:\\\\users\\\\bob.jones\\\\work",'
                '"TempPath":"/private/var/folders/aa/bb/T/work",'
                '"Dependency":"derbytools-10.16.1.1.jar",'
                '"Sql":"select ts::date"}',
            )
        )

        sanitized = sanitize_text(content, "/Users/alice/work/sparkDoctor", "/Users/alice", "alice")

        self.assertIn('"App ID":"local-sparkdoctor-fixture"', sanitized)
        self.assertIn('"User":"sparkdoctor-user"', sanitized)
        self.assertIn("/tmp/sparkdoctor", sanitized)
        self.assertIn("/opt/sparkdoctor-fixture/openjdk@21/21.0.1", sanitized)
        self.assertIn('"Driver":"127.0.0.1"', sanitized)
        self.assertIn('"IPv6":"[::1]"', sanitized)
        self.assertIn('"WindowsPath":"C:\\\\sparkdoctor-user\\\\work\\\\sparkDoctor"', sanitized)
        self.assertIn('"LowerWindowsPath":"C:\\\\sparkdoctor-user\\\\work"', sanitized)
        self.assertNotIn("Alice Smith", sanitized)
        self.assertNotIn("bob.jones", sanitized)
        self.assertIn("derbytools-10.16.1.1.jar", sanitized)
        self.assertIn("select ts::date", sanitized)
        validate_sanitized_text(sanitized)

    def test_username_validation_does_not_match_ordinary_spark_content(self):
        content = "\n".join(
            (
                '{"Event":"SparkListenerApplicationStart","User":"spark"}',
                '{"Event":"SparkListenerEnvironmentUpdate",'
                '"Spark Properties":{"spark.executor.id":"driver",'
                '"spark.app.name":"spark"}}',
            )
        )

        sanitized = sanitize_text(content, username="spark")

        self.assertIn('"User":"sparkdoctor-user"', sanitized)
        self.assertIn('"spark.app.name":"spark"', sanitized)
        validate_sanitized_text(sanitized, username="spark")

    def test_rejects_invalid_json_or_missing_application_start(self):
        with self.assertRaisesRegex(ValueError, "valid JSON"):
            validate_sanitized_text("not-json\n")

        with self.assertRaisesRegex(ValueError, "exactly one SparkListenerApplicationStart"):
            validate_sanitized_text('{"Event":"SparkListenerJobStart","Job ID":1}\n')

    def test_writes_only_validated_sanitized_output(self):
        with tempfile.TemporaryDirectory() as temp_directory:
            source = Path(temp_directory) / "raw.json"
            output = Path(temp_directory) / "sanitized.json"
            source.write_text(
                '{"Event":"SparkListenerApplicationStart","App ID":"local-999",'
                '"User":"alice","Home":"/home/alice","Host":"192.168.1.10"}\n',
                encoding="utf-8",
            )

            sanitize_file(source, output, home_directory="/home/alice", username="alice")

            sanitized = output.read_text(encoding="utf-8")
            self.assertNotIn("alice", sanitized)
            self.assertNotIn("192.168.1.10", sanitized)
            self.assertIn("sparkdoctor-user", sanitized)


if __name__ == "__main__":
    unittest.main()
