"""CI-only real systemd lifecycle, with fake OCI and isolated unit names."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import unittest


@unittest.skipUnless(sys.platform == "linux" and os.geteuid() == 0, "Linux root CI fixture only")
class SystemdTests(unittest.TestCase):
    def test_running_success_stops_real_timer(self):
        root = Path(__file__).resolve().parents[2]
        prefix = "pawcycle-a1-offline-test-" + str(os.getpid())
        unit_dir = Path("/run/systemd/system")
        with tempfile.TemporaryDirectory(prefix=prefix, dir="/run") as temp:
            directory = Path(temp)
            modules = directory / "scripts"
            modules.mkdir()
            for name in ("oci_a1_provisioner.py", "oci_a1_provisioner_api.py",
                         "oci_a1_capacity_monitor.py", "test_oci_a1_provisioner.py"):
                shutil.copyfile(root / ".github/scripts" / name, modules / name)
            (directory / "worker.env").write_text("")
            (directory / "tick.py").write_text(
                "import sys\nsys.path.insert(0, " + repr(str(modules)) + ")\n"
                "from test_oci_a1_provisioner import config, FakeApi\n"
                "from oci_a1_provisioner import Store, Worker\n"
                "s=Store(" + repr(temp) + ")\ns.initialize(config())\n"
                "assert Worker(FakeApi(),s,lambda event: None,now=lambda:1000,host='host').tick(config()) == 'provisioned'\n")
            service = (root / "infra/oci-a1/pawcycle-a1.service").read_text()
            service = re.sub(r"^(User|Group|StateDirectory|StateDirectoryMode)=.*\n", "", service, flags=re.M)
            service = service.replace("/var/lib/pawcycle-a1", temp).replace("/etc/pawcycle-a1/worker.env", str(directory / "worker.env"))
            service = service.replace("WorkingDirectory=/opt/pawcycle-a1", "WorkingDirectory=" + temp)
            service = re.sub(r"^ExecStart=.*$", "ExecStart=" + sys.executable + " " + str(directory / "tick.py"), service, flags=re.M)
            service = service.replace("pawcycle-a1.timer", prefix + ".timer")
            service = service.replace("pawcycle-a1-health.timer", prefix + "-health.timer")
            timer = (root / "infra/oci-a1/pawcycle-a1.timer").read_text().replace("pawcycle-a1.service", prefix + ".service")
            # CI hosts have already passed OnBootSec; avoid racing the explicit
            # manual service start/assertion with an immediate boot activation.
            timer = timer.replace("OnBootSec=60s", "OnActiveSec=1h")
            service_path, timer_path = (unit_dir / (prefix + suffix) for suffix in (".service", ".timer"))
            health_service = unit_dir / (prefix + "-health.service")
            health_timer = unit_dir / (prefix + "-health.timer")
            def run(*args, check=True):
                return subprocess.run(args, check=check, capture_output=True, text=True, timeout=20)
            try:
                service_path.write_text(service)
                timer_path.write_text(timer)
                health_service.write_text("[Service]\\nType=oneshot\\nExecStart=/usr/bin/true\\n".replace("\\n", "\n"))
                health_timer.write_text(
                    "[Timer]\\nOnActiveSec=1h\\nUnit=" + prefix + "-health.service\\n".replace("\\n", "\n"))
                run("systemctl", "daemon-reload")
                run("systemd-analyze", "verify", str(service_path), str(timer_path), str(health_service), str(health_timer))
                run("systemctl", "start", prefix + ".timer", prefix + "-health.timer")
                self.assertEqual(run("systemctl", "is-active", prefix + ".timer").stdout.strip(), "active")
                self.assertEqual(run("systemctl", "is-active", prefix + "-health.timer").stdout.strip(), "active")
                result = run("systemctl", "start", prefix + ".service", check=False)
                if result.returncode:
                    self.fail(run("journalctl", "-u", prefix + ".service", "--no-pager", "-n", "25").stdout)
                for _ in range(20):
                    if run("systemctl", "is-active", prefix + ".timer", check=False).stdout.strip() == "inactive":
                        break
                    time.sleep(0.1)
                self.assertEqual(run("systemctl", "is-active", prefix + ".timer", check=False).stdout.strip(), "inactive")
                self.assertEqual(run("systemctl", "is-active", prefix + "-health.timer", check=False).stdout.strip(), "inactive")
                self.assertEqual((directory / "STOP").read_text().strip(), "provisioned")
            finally:
                run("systemctl", "stop", prefix + ".timer", prefix + ".service", prefix + "-health.timer", check=False)
                service_path.unlink(missing_ok=True)
                timer_path.unlink(missing_ok=True)
                health_service.unlink(missing_ok=True)
                health_timer.unlink(missing_ok=True)
                run("systemctl", "daemon-reload")


if __name__ == "__main__":
    unittest.main()
