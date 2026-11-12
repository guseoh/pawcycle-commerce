#!/usr/bin/env python3
"""Real Docker regression on a disposable, non-Production Linux/root host.

Requires the pinned image locally. No pull, Production volumes, DB or Release
state; only certificate mount names are redirected to unique fixture volumes.
"""
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import uuid

SPEC = importlib.util.spec_from_file_location("oci_release", Path(__file__).with_name("oci-release.py"))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class NginxIsolationDockerTests(unittest.TestCase):
    def test_real_validate_config_without_capabilities(self):
        self.assertEqual(os.geteuid(), 0, "root-owned disposable fixtures are required")
        self.assertFalse(Path("/opt/pawcycle/state").exists(), "never run on a Production host")
        prefix = "pawcycle-release-nginx-test-" + uuid.uuid4().hex
        volumes = {name: prefix + "-" + name for name in ("letsencrypt", "certbot-webroot")}

        def docker(*args, env=None):
            clean = {"PATH": os.environ["PATH"], "HOME": "/root", **(env or {})}
            return subprocess.run(["docker", "--host", "unix:///var/run/docker.sock", *args],
                                  env=clean, capture_output=True, timeout=60)

        def checked(*args):
            result = docker(*args)
            self.assertEqual(result.returncode, 0, result.stderr.decode(errors="replace"))
            return result.stdout

        checked("image", "inspect", release.PROXY)
        identity = checked("run", "--rm", "--pull", "never", "--network", "none", "--read-only",
                           "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
                           "--entrypoint", "sh", release.PROXY, "-c",
                           "id -u nginx; id -g nginx; nginx -V 2>&1")
        self.assertTrue(identity.startswith(b"101\n101\n"))
        for name in release.NGINX_TEMP_DIRECTORIES:
            self.assertIn(("/var/cache/nginx/" + name).encode(), identity)

        with tempfile.TemporaryDirectory(prefix="release-nginx-test-") as temporary:
            root = Path(temporary)
            certificate = root / "certificates/live" / release.DOMAIN
            certificate.mkdir(parents=True)
            subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                            "-subj", "/CN=" + release.DOMAIN, "-keyout", str(certificate / "privkey.pem"),
                            "-out", str(certificate / "fullchain.pem")], check=True, capture_output=True)
            (certificate / "privkey.pem").chmod(0o600)
            created = []
            seed = prefix + "-seed"
            try:
                for name in volumes.values():
                    checked("volume", "create", name)
                    created.append(name)
                checked("create", "--name", seed, "--pull", "never", "--network", "none", "--read-only",
                        "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
                        "--mount", "type=volume,src=" + volumes["letsencrypt"] + ",dst=/fixture",
                        "--entrypoint", "true", release.PROXY)
                checked("cp", str(root / "certificates") + "/.", seed + ":/fixture/")
                checked("rm", seed)

                host = release.Host(root, root, root, root / "unused.lock")
                host.backend_env = root / "backend.env"
                release.atomic(host.backend_env, b"ISOLATED_FIXTURE=true\n")
                host.automation = {"PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED": "false",
                                   "PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE": "1",
                                   "PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS": "60000"}
                target = {"sha": "a" * 40, **{role: {"repository": "ghcr.io/example/" + role}
                                               for role in ("backend", "frontend")}}
                compose = Path(__file__).with_name("compose.yaml").read_bytes()
                nginx = Path(__file__).with_name("nginx.https.conf").read_bytes().replace(
                    b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())
                legacy = True
                diagnostics = []

                def fixture_docker(*args, env=None, **kwargs):
                    args = list(args)
                    if args[0] == "run":
                        # Retain the real CLI security flags and read-only volume mounts.
                        for index, arg in enumerate(args):
                            for name, value in volumes.items():
                                arg = arg.replace("src=pawcycle-production-" + name + ",", "src=" + value + ",")
                            args[index] = arg
                        if legacy:
                            original = []
                            index = 0
                            while index < len(args):
                                if args[index] == "--tmpfs" and args[index + 1].startswith("/var/cache/nginx"):
                                    if args[index + 1].startswith("/var/cache/nginx:"):
                                        original.extend(("--tmpfs", "/var/cache/nginx:size=16m"))
                                    index += 2
                                else:
                                    original.append(args[index])
                                    index += 1
                            args = original
                    result = docker(*args, env=env)
                    diagnostics.append(result.stderr)
                    release.require(result.returncode == 0, "isolated Docker validation failed")
                    return result.stdout

                with patch.object(host, "sources", return_value=(compose, nginx)), \
                        patch.object(host, "docker", side_effect=fixture_docker):
                    with self.assertRaises(release.Refused):
                        host.validate_config(target)
                    self.assertIn(b'chown("/var/cache/nginx/client_temp", 101) failed', diagnostics[-1])
                    legacy = False
                    host.validate_config(target)
                    self.assertIn(b"test is successful", diagnostics[-1])
                self.assertFalse(list(root.glob(".oci-config-*")))
                self.assertFalse((root / "oci-release-state.json").exists())
            finally:
                docker("rm", "--force", seed)
                for name in reversed(created):
                    checked("volume", "rm", name)


if __name__ == "__main__":
    unittest.main()
