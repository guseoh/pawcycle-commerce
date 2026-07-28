#!/usr/bin/env python3

from __future__ import annotations

import fcntl
import os
from pathlib import Path
import pty
import select
import shlex
import signal
import stat
import subprocess
import tempfile
import termios
import time


ROOT = Path(__file__).resolve().parents[2]
WRAPPER = ROOT / "infra" / "production" / "create-production-auth-smoke-member.sh"
EMAIL = "ops020-fixture@example.test"
PASSWORD = "fixture-password-not-secret"
REPOSITORY = "ghcr.io/example/pawcycle-commerce-backend"
DIGEST = f"{REPOSITORY}@sha256:{'a' * 64}"
PASS = "PASS: production auth smoke member created"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def read_until(master: int, expected: bytes, transcript: bytearray, timeout: float = 15) -> None:
    deadline = time.monotonic() + timeout
    while expected not in transcript:
        remaining = deadline - time.monotonic()
        require(remaining > 0, f"PTY prompt was not observed: {expected!r}")
        ready, _, _ = select.select([master], [], [], remaining)
        require(bool(ready), f"PTY prompt timed out: {expected!r}")
        transcript.extend(os.read(master, 4096))


def collect(master: int, process: subprocess.Popen[bytes], transcript: bytearray) -> int:
    while process.poll() is None:
        ready, _, _ = select.select([master], [], [], 0.2)
        if ready:
            try:
                transcript.extend(os.read(master, 4096))
            except OSError:
                break
    while True:
        ready, _, _ = select.select([master], [], [], 0)
        if not ready:
            break
        try:
            transcript.extend(os.read(master, 4096))
        except OSError:
            break
    return process.wait(timeout=5)


def echo_enabled(slave: int) -> bool:
    return bool(termios.tcgetattr(slave)[3] & termios.ECHO)


def wait_for_echo(slave: int, expected: bool) -> None:
    deadline = time.monotonic() + 5
    while echo_enabled(slave) != expected:
        require(time.monotonic() < deadline, "terminal echo state did not change as expected")
        time.sleep(0.02)


def write_fake_docker(path: Path) -> None:
    path.write_text(
        """#!/usr/bin/env bash
set -Eeuo pipefail
printf '%q ' "$@" >> "$FAKE_DOCKER_LOG"
printf '\\n' >> "$FAKE_DOCKER_LOG"
case "${1:-}" in
  pull) exit 0 ;;
  image)
    case "${4:-}" in
      *org.opencontainers.image.revision*) printf '%s\\n' "$FAKE_SHA" ;;
      *Config.User*) printf '%s\\n' 'pawcycle' ;;
      *RepoDigests*) printf '%s\\n' "$FAKE_DIGEST" ;;
      *) exit 8 ;;
    esac
    ;;
  ps) printf '%s\\n' 'mysql-fixture-id' ;;
  inspect)
    case "${3:-}" in
      *State.Status*) printf '%s\\n' 'running' ;;
      *State.Health.Status*) printf '%s\\n' 'healthy' ;;
      *NetworkSettings.Networks*) printf '%s\\n' 'attached' ;;
      *com.pawcycle.ops020.scope*) printf '%s\\n' 'auth-smoke-member' ;;
      *) exit 8 ;;
    esac
    ;;
  network) printf '%s\\n' 'true' ;;
  container) exit 1 ;;
  run)
    IFS= read -r email
    IFS= read -r password
    [[ "$email" == 'ops020-fixture@example.test' ]]
    [[ "$password" == 'fixture-password-not-secret' ]]
    printf '%s\\n' 'stdin-contract-ok' >> "$FAKE_DOCKER_MARKER"
    if [[ "${FAKE_DOCKER_MODE:-success}" == failure ]]; then
      printf '%s\\n' 'raw-sensitive-docker-error' >&2
      exit 9
    fi
    printf '%s\\n' 'PASS: production auth smoke member created'
    ;;
  rm) printf '%s\\n' 'cleanup-ok' >> "$FAKE_DOCKER_MARKER" ;;
  *) exit 8 ;;
esac
""",
        encoding="utf-8",
    )
    path.chmod(path.stat().st_mode | stat.S_IXUSR)


def prepare_case(root: Path, mode: str) -> tuple[list[str], dict[str, str], Path, Path]:
    runtime = root / "runtime"
    state = root / "state"
    bundle = runtime / ".bundle.fixture"
    fake_bin = root / "bin"
    for directory in (runtime, state, bundle, fake_bin):
        directory.mkdir(mode=0o700)
    (runtime / "current").symlink_to(bundle, target_is_directory=True)
    backend_env = bundle / "backend.env"
    backend_env.write_text(
        "SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/fixture\n"
        "SPRING_DATASOURCE_USERNAME=fixture_user\n"
        "SPRING_DATASOURCE_PASSWORD=fixture_db_password\n",
        encoding="utf-8",
    )
    complete = bundle / ".complete"
    complete.write_text("fixture complete\n", encoding="utf-8")
    sha = subprocess.check_output(
        ["git", "-c", f"safe.directory={ROOT}", "rev-parse", "HEAD"],
        cwd=ROOT,
        text=True,
    ).strip()
    current_sha = state / "current-sha"
    current_sha.write_text(f"{sha}\n", encoding="utf-8")
    image_state = state / f"{sha}.images"
    image_state.write_text(f"RELEASE_SHA={sha}\nBACKEND_DIGEST={DIGEST}\n", encoding="utf-8")
    for protected in (backend_env, complete, current_sha, image_state):
        protected.chmod(0o600)
    log = root / "docker-arguments"
    marker = root / "docker-marker"
    fake_docker = fake_bin / "docker"
    write_fake_docker(fake_docker)
    environment = os.environ | {
        "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
        "FAKE_SHA": sha,
        "FAKE_DIGEST": DIGEST,
        "FAKE_DOCKER_LOG": str(log),
        "FAKE_DOCKER_MARKER": str(marker),
        "FAKE_DOCKER_MODE": mode,
        "GIT_CONFIG_COUNT": "1",
        "GIT_CONFIG_KEY_0": "safe.directory",
        "GIT_CONFIG_VALUE_0": str(ROOT),
    }
    command = [
        str(WRAPPER),
        "--sha",
        sha,
        "--backend-image",
        REPOSITORY,
        "--runtime-dir",
        str(runtime),
        "--state-dir",
        str(state),
    ]
    return command, environment, log, marker


def start_pty(command: list[str], environment: dict[str, str]) -> tuple[subprocess.Popen[bytes], int, int]:
    master, slave = pty.openpty()

    def establish_terminal() -> None:
        os.setsid()
        fcntl.ioctl(slave, termios.TIOCSCTTY, 0)

    process = subprocess.Popen(
        command,
        cwd=ROOT,
        env=environment,
        stdin=slave,
        stdout=slave,
        stderr=slave,
        preexec_fn=establish_terminal,
    )
    return process, master, slave


def run_case(mode: str, signal_during_password: bool = False) -> tuple[int, str, str, str]:
    with tempfile.TemporaryDirectory(prefix="ops020-pty-") as temporary:
        command, environment, log, marker = prepare_case(Path(temporary), mode)
        process, master, slave = start_pty(command, environment)
        transcript = bytearray()
        try:
            read_until(master, b"Email: ", transcript)
            require(echo_enabled(slave), "email prompt unexpectedly disabled terminal echo")
            os.write(master, f"{EMAIL}\n".encode())
            read_until(master, b"Password: ", transcript)
            wait_for_echo(slave, False)
            if signal_during_password:
                process.send_signal(signal.SIGTERM)
            else:
                os.write(master, f"{PASSWORD}\n".encode())
            status = collect(master, process, transcript)
            wait_for_echo(slave, True)
            decoded = transcript.decode("utf-8", errors="replace")
            require(PASSWORD not in decoded, "password was echoed to the terminal")
            return (
                status,
                decoded,
                log.read_text(encoding="utf-8") if log.exists() else "",
                marker.read_text(encoding="utf-8") if marker.exists() else "",
            )
        finally:
            os.close(master)
            os.close(slave)


def assert_run_contract(arguments: str) -> None:
    run_line = next(line for line in arguments.splitlines() if line.startswith("run "))
    run_arguments = shlex.split(run_line)
    for required_flag in (
        "--rm",
        "--interactive",
        "--read-only",
        "--spring.main.web-application-type=none",
        "--pawcycle.maintenance.create-auth-smoke-member.enabled=true",
        "--spring.flyway.enabled=false",
        DIGEST,
    ):
        require(
            required_flag in run_arguments,
            f"Docker run contract is missing: {required_flag}",
        )
    for option, value in (
        ("--name", "pawcycle-ops020-auth-smoke-member"),
        ("--network", "pawcycle-production-data"),
        ("--tmpfs", "/tmp:size=64m,mode=1777"),
        ("--user", "pawcycle"),
        ("--security-opt", "no-new-privileges:true"),
        ("--cap-drop", "ALL"),
        ("--memory", "640m"),
        ("--cpus", "0.75"),
        ("--pids-limit", "256"),
        ("--log-driver", "none"),
    ):
        option_indexes = [
            index for index, argument in enumerate(run_arguments) if argument == option
        ]
        require(
            len(option_indexes) == 1
            and option_indexes[0] + 1 < len(run_arguments)
            and run_arguments[option_indexes[0] + 1] == value,
            f"Docker run contract is missing or duplicated: {option} {value}",
        )
    for forbidden in (EMAIL, PASSWORD, "--publish", "-p", "--restart"):
        require(
            forbidden not in run_arguments,
            f"Docker run contract exposed or enabled: {forbidden}",
        )


def main() -> None:
    require(os.geteuid() == 0, "PTY contract test must run as root")

    status, transcript, arguments, marker = run_case("success")
    require(status == 0, "successful fake Docker execution failed")
    require(transcript.count(PASS) == 1, "success did not emit exactly one PASS")
    require("stdin-contract-ok" in marker, "credentials were not delivered only through stdin")
    assert_run_contract(arguments)

    status, transcript, arguments, marker = run_case("failure")
    require(status != 0, "Docker failure was reported as success")
    require("raw-sensitive-docker-error" not in transcript, "raw Docker stderr was exposed")
    require("member creation Container failed" in transcript, "generic failure stage is missing")
    require("cleanup-ok" in marker, "failed one-shot Container was not cleaned")
    assert_run_contract(arguments)

    status, transcript, _, marker = run_case("success", signal_during_password=True)
    require(status == 143, "TERM did not preserve the expected nonzero status")
    require(PASSWORD not in transcript, "password was exposed during signal cleanup")
    require("stdin-contract-ok" not in marker, "Container ran after signal interruption")

    print("OPS-020 fake Docker and PTY contracts passed")


if __name__ == "__main__":
    main()
