#!/usr/bin/env python3
import argparse
import ipaddress
import json
import re
from pathlib import Path


IPV4_PATTERN = re.compile(r"(?<![\w.-])(?:\d{1,3}\.){3}\d{1,3}(?![\w.-])")
IPV6_PATTERN = re.compile(
    r"(?<![\w:.])(?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4}"
    r"(?:%[A-Za-z0-9_.-]+)?(?![\w:.])"
)
MAC_USER_PATH_PATTERN = re.compile(r"/Users/[^/\"',;:}\s]+")
LINUX_USER_PATH_PATTERN = re.compile(r"/home/[^/\"',;:}\s]+")
WINDOWS_USER_PATH_PATTERN = re.compile(
    r'[A-Za-z]:\\\\Users\\\\[^\\"]+', re.IGNORECASE
)
MAC_TEMP_PATH_PATTERN = re.compile(r"/(?:private/)?var/folders/[^\"',;:}\s}]+")
USER_FIELD_PATTERN = re.compile(r'("(?:User|user\.name)"\s*:\s*)"[^"]*"')
SANITIZED_USERNAME = "sparkdoctor-user"


def sanitize_text(content, repo_root=None, home_directory=None, username=None):
    replacements = (
        (repo_root, "/tmp/sparkdoctor"),
        (home_directory, "/tmp/sparkdoctor-user"),
    )
    for original, replacement in replacements:
        if original:
            content = content.replace(str(original), replacement)

    content = MAC_USER_PATH_PATTERN.sub("/tmp/sparkdoctor-user", content)
    content = LINUX_USER_PATH_PATTERN.sub("/tmp/sparkdoctor-user", content)
    content = WINDOWS_USER_PATH_PATTERN.sub(lambda _: r"C:\\sparkdoctor-user", content)
    content = MAC_TEMP_PATH_PATTERN.sub("/tmp/sparkdoctor-temp", content)
    content = content.replace("/opt/homebrew/Cellar", "/opt/sparkdoctor-fixture")
    content = content.replace("/usr/local/Cellar", "/opt/sparkdoctor-fixture")
    content = re.sub(r"local-\d+", "local-sparkdoctor-fixture", content)
    content = USER_FIELD_PATTERN.sub(
        lambda match: f'{match.group(1)}"{SANITIZED_USERNAME}"', content
    )
    content = IPV4_PATTERN.sub(_sanitize_ip_address, content)
    return IPV6_PATTERN.sub(_sanitize_ip_address, content)


def validate_sanitized_text(content, forbidden_values=(), username=None):
    violations = []
    for value in forbidden_values:
        if value and str(value) in content:
            violations.append(f"contains forbidden value: {value}")

    for label, pattern in (
        ("macOS user path", MAC_USER_PATH_PATTERN),
        ("Linux user path", LINUX_USER_PATH_PATTERN),
        ("Windows user path", WINDOWS_USER_PATH_PATTERN),
    ):
        if pattern.search(content):
            violations.append(f"contains an unsanitized {label}")
    if "/opt/homebrew/Cellar" in content or "/usr/local/Cellar" in content:
        violations.append("contains an unsanitized Homebrew Cellar path")

    for candidate in IPV4_PATTERN.findall(content):
        address = _ip_address_or_none(candidate)
        if address is not None and address.is_private and not address.is_loopback:
            violations.append(f"contains a private IP address: {candidate}")
    for candidate in IPV6_PATTERN.findall(content):
        address = _ip_address_or_none(candidate)
        if address is not None and address.is_private and not address.is_loopback:
            violations.append(f"contains a private IP address: {candidate}")

    application_starts = 0
    for line_number, line in enumerate(content.splitlines(), start=1):
        if not line.strip():
            continue
        try:
            event = json.loads(line)
        except json.JSONDecodeError as exception:
            violations.append(f"line {line_number} is not valid JSON: {exception.msg}")
            continue
        if isinstance(event, dict) and event.get("Event") == "SparkListenerApplicationStart":
            application_starts += 1
        for field, value in _user_field_values(event):
            if value != SANITIZED_USERNAME:
                violations.append(
                    f"line {line_number} contains an unsanitized {field} field"
                )
            if username and value == username:
                violations.append(
                    f"line {line_number} contains the source username in {field}"
                )
    if application_starts != 1:
        violations.append(f"expected exactly one SparkListenerApplicationStart, found {application_starts}")

    if violations:
        raise ValueError("Sanitized fixture validation failed: " + "; ".join(violations))


def sanitize_file(input_path, output_path, repo_root=None, home_directory=None, username=None):
    content = Path(input_path).read_text(encoding="utf-8")
    sanitized = sanitize_text(content, repo_root, home_directory, username)
    validate_sanitized_text(
        sanitized, (repo_root, home_directory), username=username
    )
    Path(output_path).write_text(sanitized, encoding="utf-8")


def _sanitize_ip_address(match):
    candidate = match.group(0)
    address = _ip_address_or_none(candidate)
    if address is not None and address.is_private and not address.is_loopback:
        return "::1" if address.version == 6 else "127.0.0.1"
    return candidate


def _ip_address_or_none(candidate):
    try:
        return ipaddress.ip_address(candidate.split("%", 1)[0])
    except ValueError:
        return None


def _user_field_values(value):
    if isinstance(value, dict):
        for field, child in value.items():
            if field in {"User", "user.name"} and isinstance(child, str):
                yield field, child
            yield from _user_field_values(child)
    elif isinstance(value, list):
        for child in value:
            yield from _user_field_values(child)


def main():
    parser = argparse.ArgumentParser(description="Sanitize a generated Spark event-log fixture.")
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--repo-root", type=Path)
    parser.add_argument("--home-directory")
    parser.add_argument("--username")
    args = parser.parse_args()

    sanitize_file(
        args.input,
        args.output,
        repo_root=args.repo_root,
        home_directory=args.home_directory,
        username=args.username,
    )


if __name__ == "__main__":
    main()
