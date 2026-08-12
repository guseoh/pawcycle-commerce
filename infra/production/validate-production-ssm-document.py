#!/usr/bin/env python3

from __future__ import annotations

import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[2]
DOCUMENT = ROOT / "infra" / "production" / "pawcycle-production-deploy-ssm-document.json"
PARAMETERS = (
    "Operation",
    "TargetSha",
    "ApprovedContractFromSha",
    "ApprovedControlSha",
    "ApprovedMigrationTargetSha",
)
TRUSTED_DIRECTORY = "/opt/pawcycle/control"
TRUSTED_CONFIG_LINES = (
    "export GIT_CONFIG_COUNT=1",
    "export GIT_CONFIG_KEY_0=safe.directory",
    f"export GIT_CONFIG_VALUE_0={TRUSTED_DIRECTORY}",
)
RAW_TEMPLATE_RE = re.compile(r"\{\{\s*([A-Za-z][A-Za-z0-9]*)\s*\}\}")
VALID_VALUES = {
    "Operation": "deploy",
    "TargetSha": "6" * 40,
    "ApprovedContractFromSha": "7" * 40,
    "ApprovedControlSha": "8" * 40,
    "ApprovedMigrationTargetSha": "9" * 40,
}
EMPTY_APPROVAL_VALUES = {
    "Operation": "preflight",
    "TargetSha": "a" * 40,
    "ApprovedContractFromSha": "",
    "ApprovedControlSha": "",
    "ApprovedMigrationTargetSha": "",
}
INJECTION_VALUES = {
    "Operation": "preflight; touch /tmp/ssm-pwned",
    "TargetSha": "a" * 39 + ";id",
    "ApprovedContractFromSha": "$(id)",
    "ApprovedControlSha": "`id`",
    "ApprovedMigrationTargetSha": "a" * 40 + "\nwhoami",
}
OBSERVED_V3_V4_MATERIALIZED_COMMAND = r"""exec /usr/bin/env bash <<'PAWCYCLE_PRODUCTION_SSM_SCRIPT'
#!/usr/bin/env bash
set -Eeuo pipefail
if [[ -z "${SSM_Operation+x}" ]]; then export SSM_Operation="$SSM_Operation"; fi
if [[ -z "${SSM_TargetSha+x}" ]]; then export SSM_TargetSha="$SSM_TargetSha"; fi
if [[ -z "${SSM_ApprovedContractFromSha+x}" ]]; then export SSM_ApprovedContractFromSha="$SSM_ApprovedContractFromSha"; fi
if [[ -z "${SSM_ApprovedControlSha+x}" ]]; then export SSM_ApprovedControlSha="$SSM_ApprovedControlSha"; fi
if [[ -z "${SSM_ApprovedMigrationTargetSha+x}" ]]; then export SSM_ApprovedMigrationTargetSha="$SSM_ApprovedMigrationTargetSha"; fi
printf '%s\n' reached
PAWCYCLE_PRODUCTION_SSM_SCRIPT"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def posix_sh_command(force_wsl: bool = False) -> list[str]:
    if force_wsl and os.name == "nt":
        wsl = shutil.which("wsl.exe")
        if wsl is None:
            candidate = Path(os.environ.get("SYSTEMROOT", r"C:\Windows")) / "System32" / "wsl.exe"
            if candidate.is_file():
                wsl = str(candidate)
        if wsl is None:
            raise SystemExit("ERROR: wsl.exe is required for the Windows SSM trusted-directory validation")
        return [wsl, "--exec", "/bin/sh", "-s"]
    shell = shutil.which("sh")
    if shell is not None:
        return [shell]
    if os.name == "nt":
        wsl_bash = Path(os.environ.get("SYSTEMROOT", r"C:\Windows")) / "System32" / "bash.exe"
        if wsl_bash.is_file():
            return [str(wsl_bash), "-c", "exec /bin/sh -s"]
    raise SystemExit("ERROR: a POSIX sh is required to validate SSM runShellScript materialization")


def run_shell(
    script: str,
    values: dict[str, str] | None = None,
    force_wsl: bool = False,
) -> subprocess.CompletedProcess[bytes]:
    environment = os.environ.copy()
    for name in PARAMETERS:
        environment.pop(f"SSM_{name}", None)
    if values is not None:
        for name, value in values.items():
            environment[f"SSM_{name}"] = value
        script = "\n".join(
            f"export SSM_{name}={shlex.quote(value)}" for name, value in values.items()
        ) + "\n" + script
    return subprocess.run(
        posix_sh_command(force_wsl),
        input=script.encode("utf-8"),
        env=environment,
        capture_output=True,
    )


def materialize_raw_parameters(command: str, values: dict[str, str]) -> str:
    generated = command
    for name in PARAMETERS:
        generated = generated.replace(f"{{{{{name}}}}}", values[name])
    return generated


def run_document_parameter_probe(command: str, values: dict[str, str]) -> list[str]:
    generated = materialize_raw_parameters(command, values)
    marker = 'approved_migration_target_sha="${5:-}"'
    require(generated.count(marker) == 1, "SSM command positional parameter probe is ambiguous")
    probe = "\n".join(f'printf "%s\\n" "${{{index}}}"' for index in range(1, 6)) + "\nexit 0"
    generated = generated.replace(marker, marker + "\n" + probe, 1)
    completed = run_shell(generated)
    stdout = completed.stdout.decode("utf-8")
    stderr = completed.stderr.decode("utf-8")
    require(
        completed.returncode == 0,
        f"raw SSM parameter command exited {completed.returncode}: {stderr.strip() or stdout.strip()}",
    )
    return stdout.splitlines()


def validate_observed_v4_failure() -> None:
    completed = run_shell(OBSERVED_V3_V4_MATERIALIZED_COMMAND)
    require(
        completed.returncode == 1 and completed.stdout == b"",
        "the captured v3/v4 ENV_VAR fallback materialization no longer reproduces the observed exit 1",
    )
    direct = run_shell(OBSERVED_V3_V4_MATERIALIZED_COMMAND, VALID_VALUES)
    require(
        direct.returncode == 0 and direct.stdout == b"reached\n",
        "the captured v3/v4 materialization did not preserve the observed direct-env success contrast",
    )


def validate_trusted_directory_inheritance(command: str) -> None:
    generated = materialize_raw_parameters(command, VALID_VALUES)
    require("GIT_CONFIG_VALUE_0=/opt/pawcycle/control" in generated, "SSM trusted-directory value is not fixed to the control worktree")
    trusted_config = "\n".join(TRUSTED_CONFIG_LINES).replace(TRUSTED_DIRECTORY, "$fixture_repository")
    probe = f'''fixture_repository="$(mktemp -d)"
trap 'rm -rf "$fixture_repository"' EXIT
git init -q "$fixture_repository"
git -C "$fixture_repository" remote add origin https://github.com/example/repo.git
if env GIT_TEST_ASSUME_DIFFERENT_OWNER=1 GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 \\
  git -C "$fixture_repository" config --get remote.origin.url >/dev/null 2>&1; then
  exit 20
fi
{trusted_config}
trusted_origin="$(env GIT_TEST_ASSUME_DIFFERENT_OWNER=1 GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 \\
  git -C "$fixture_repository" config --get remote.origin.url)" || exit 21
printf '%s\n' "$trusted_origin"
'''
    trusted = run_shell(probe, force_wsl=True)
    require(
        trusted.returncode == 0 and trusted.stdout == b"https://github.com/example/repo.git\n",
        "process-scoped safe.directory did not convert the isolated child Git probe from rejected to trusted",
    )


def validate_parameter_contract(parameters: dict[str, object]) -> None:
    require(set(parameters) == set(PARAMETERS), "SSM document must accept only the five bounded parameters")
    expected = {
        "Operation": ("^(preflight|deploy)$", 6, 9),
        "TargetSha": ("^[0-9a-f]{40}$", 40, 40),
        "ApprovedContractFromSha": ("^$|^[0-9a-f]{40}$", 0, 40),
        "ApprovedControlSha": ("^$|^[0-9a-f]{40}$", 0, 40),
        "ApprovedMigrationTargetSha": ("^$|^[0-9a-f]{40}$", 0, 40),
    }
    for name, (pattern, minimum, maximum) in expected.items():
        parameter = parameters[name]
        require(
            parameter.get("type") == "String"
            and "interpolationType" not in parameter
            and parameter.get("allowedPattern") == pattern
            and parameter.get("minChars") == minimum
            and parameter.get("maxChars") == maximum,
            f"SSM {name} must use the exact bounded raw document parameter contract",
        )
        require(parameter.get("default", "") == "" if minimum == 0 else "default" not in parameter, f"SSM {name} default contract is invalid")


def validate_command_contract(command: str) -> None:
    first_line = command.splitlines()[0]
    expected_first_line = (
        "exec /usr/bin/env bash -s -- "
        '"{{Operation}}" "{{TargetSha}}" "{{ApprovedContractFromSha}}" '
        '"{{ApprovedControlSha}}" "{{ApprovedMigrationTargetSha}}" '
        "<<'PAWCYCLE_PRODUCTION_SSM_SCRIPT'"
    )
    require(first_line == expected_first_line, "SSM document must pass only the five exact bounded parameters as Bash arguments")
    require(command.startswith(expected_first_line + "\n#!/usr/bin/env bash\nset -Eeuo pipefail\n"), "SSM document must enter the bounded Bash body from sh")
    require(command.endswith("\nPAWCYCLE_PRODUCTION_SSM_SCRIPT"), "SSM document Bash heredoc must be closed")
    templates = RAW_TEMPLATE_RE.findall(first_line)
    require(templates == list(PARAMETERS), "SSM Bash argv must contain the five parameters in the approved order")
    require(RAW_TEMPLATE_RE.search("\n".join(command.splitlines()[1:])) is None, "raw document templates are forbidden inside the Bash body")
    require(not any(f"SSM_{name}" in command for name in PARAMETERS) and "interpolationType" not in command, "SSM command must not depend on ENV_VAR materialization")
    for line in TRUSTED_CONFIG_LINES:
        require(command.count(line) == 1, f"SSM trusted-directory contract must contain exactly one line: {line}")
    require("safe.directory=*" not in command, "SSM trusted-directory contract must reject wildcard safe.directory")
    require("GIT_CONFIG_GLOBAL" not in command and "GIT_CONFIG_SYSTEM" not in command, "SSM trusted-directory contract must not change persistent Git config paths")
    require("git config --global" not in command and "git config --system" not in command, "SSM trusted-directory contract must not invoke persistent Git config")
    require("GIT_CONFIG_KEY_1" not in command and "GIT_CONFIG_VALUE_1" not in command, "SSM trusted-directory contract must contain only one process config entry")
    for index, name in enumerate(PARAMETERS, start=1):
        require(f'"${{{index}:-}}"' in command, f"SSM {name} must be read from its bounded Bash positional argument")
    require("--adopt-contract-sha" not in command and "aws ssm" not in command and "ssh" not in command.lower(), "SSM document must not bypass the deploy boundary")
    require("${SSM_Operation:-}" not in command and "{{ TargetSha }}" not in command, "SSM document contains a legacy ENV_VAR or spaced interpolation path")


def main() -> None:
    document = json.loads(DOCUMENT.read_text(encoding="utf-8"))
    parameters = document.get("parameters")
    require(isinstance(parameters, dict), "SSM document parameters must be an object")
    validate_parameter_contract(parameters)

    steps = document.get("mainSteps")
    require(isinstance(steps, list) and len(steps) == 1, "SSM document must contain exactly one main step")
    require(steps[0].get("action") == "aws:runShellScript", "SSM document must use the Linux shell plugin")
    command = "\n".join(steps[0].get("inputs", {}).get("runCommand", []))
    validate_command_contract(command)

    for name, value in INJECTION_VALUES.items():
        pattern = parameters[name]["allowedPattern"]
        require(re.fullmatch(pattern, value) is None, f"SSM {name} allowedPattern accepted an injection-shaped value")

    validate_observed_v4_failure()
    for values in (VALID_VALUES, EMPTY_APPROVAL_VALUES):
        require(
            run_document_parameter_probe(command, values) == [values[name] for name in PARAMETERS],
            "raw bounded SSM document parameters did not reach the Bash positional inputs",
        )
    validate_trusted_directory_inheritance(command)

    print("OPS-AUTO-008 delta SSM raw parameter materialization contracts validated")


if __name__ == "__main__":
    main()
