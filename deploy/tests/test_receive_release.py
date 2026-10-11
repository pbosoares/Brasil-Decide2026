"""Exercise deployment failure paths with fake Docker/HTTP, never touching a VPS."""
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[2]
RELEASE = "a" * 40
OLD_IMAGE = "sha256:" + "b" * 64


class ReceiveReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.shell_root = self.root.as_posix()
        if os.name == "nt":
            self.shell_root = "/" + self.shell_root[0].lower() + self.shell_root[2:]
        (self.root / "deploy").mkdir()
        (self.root / "deploy/.env.vps").write_text("R2_ENABLED=false\n")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        script = (REPO / "deploy/receive-release.sh").read_text()
        if os.name == "nt":
            # MSYS can prefer curl.exe over an extensionless PATH fixture.
            script = script.replace("umask 077", 'umask 077\ncurl() { bash "$TEST_ROOT/bin/curl" "$@"; }')
        self.script = self.root / "receiver.sh"
        self.script.write_text(script.replace("ROOT=/opt/brasil-decide-2026", f"ROOT='{self.shell_root}'"), newline="\n")
        self.fake("docker", r'''#!/usr/bin/env bash
printf '%s\n' "$*" >> "$TEST_ROOT/commands.log"
case "$1" in
  inspect) printf '%s\n' "$OLD_IMAGE" ;;
  image) printf '%s\n' "${TEST_LABEL:-$TEST_RELEASE}" ;;
  load) cat >/dev/null ;;
  compose)
    if [[ "$*" == *' ps -q '* ]]; then echo old-container; fi
    if [[ "$*" == *' exec -T '* ]]; then
      if [[ ${TEST_FAIL_HEALTH:-0} == 1 ]]; then echo '{"status":"DOWN"}'; else echo '{"status":"UP"}'; fi
    fi
    if [[ ${TEST_FAIL_UP:-0} == 1 && "$*" == *next.yml* && "$*" == *' up '* ]]; then exit 1; fi
    ;;
esac
''')
        self.fake("curl", "#!/usr/bin/env bash\nexit 0\n")
        self.fake("flock", "#!/usr/bin/env bash\nexit 0\n")
        # Git Bash on Windows may expose Python only as python.exe.
        if not shutil.which("python3"):
            import sys
            self.fake("python3", f'#!/usr/bin/env bash\nexec "{Path(sys.executable).as_posix()}" "$@"\n')

    def fake(self, name, content):
        path = self.bin / name
        path.write_text(content, newline="\n")
        path.chmod(0o755)

    def package(self, foreign=False):
        tags = [f"brasil-decide-{service}:{RELEASE}" for service in ("backend", "frontend")]
        if foreign:
            tags[0] = "unrelated-project:latest"
        manifest = json.dumps([{"RepoTags": [tag]} for tag in tags]).encode()
        data = io.BytesIO()
        with tarfile.open(fileobj=data, mode="w:gz") as archive:
            info = tarfile.TarInfo("manifest.json")
            info.size = len(manifest)
            archive.addfile(info, io.BytesIO(manifest))
        return data.getvalue()

    def run_receiver(self, package=None, command=None, **overrides):
        env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                   TEST_ROOT=self.shell_root, TEST_RELEASE=RELEASE, OLD_IMAGE=OLD_IMAGE,
                   SSH_ORIGINAL_COMMAND=command or f"deploy {RELEASE}", **overrides)
        return subprocess.run([os.environ.get("DEPLOY_TEST_BASH", "bash"), str(self.script)],
                              input=self.package() if package is None else package,
                              env=env, capture_output=True, timeout=30)

    def commands(self):
        file = self.root / "commands.log"
        return file.read_text() if file.exists() else ""

    def test_success_persists_release_and_previous_immutable_images(self):
        result = self.run_receiver()
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))
        self.assertEqual(RELEASE, (self.root / "deploy/deployed-release.txt").read_text().strip())
        self.assertIn(RELEASE, (self.root / "deploy/release.override.yml").read_text())
        self.assertIn(OLD_IMAGE, (self.root / "deploy/rollback.override.yml").read_text())

    def test_failed_update_restores_previous_images(self):
        result = self.run_receiver(TEST_FAIL_UP="1")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("previous.yml up", self.commands())
        self.assertIn(OLD_IMAGE, (self.root / "deploy/release.override.yml").read_text())
        self.assertFalse((self.root / "deploy/deployed-release.txt").exists())

    def test_unhealthy_update_also_rolls_back(self):
        result = self.run_receiver(TEST_FAIL_HEALTH="1")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("previous.yml up", self.commands())
        self.assertFalse((self.root / "deploy/deployed-release.txt").exists())

    def test_foreign_images_never_reach_docker_load(self):
        result = self.run_receiver(package=self.package(foreign=True))
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("load", self.commands())
        self.assertNotIn(" up ", self.commands())

    def test_bad_revision_label_never_replaces_running_services(self):
        result = self.run_receiver(TEST_LABEL="other")
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(" up ", self.commands())

    def test_no_shell_or_invalid_revision_is_accepted(self):
        for command in ("bash", "deploy ../other", f"deploy {RELEASE}; whoami"):
            result = self.run_receiver(command=command)
            self.assertEqual(2, result.returncode)
            self.assertEqual("", self.commands())

    def test_corrupt_transfer_preserves_running_services(self):
        result = self.run_receiver(package=b"broken gzip")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", self.commands())


if __name__ == "__main__":
    unittest.main()
