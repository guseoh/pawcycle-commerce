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
RAW_TEMPLATE_RE = re.compile(r"\{\{\s*([A-Za-z][A-Za-z0-9]*)\s*\}\}")
FALLBACK_VALUES = {
    "Operation": "preflight",
    "TargetSha": "1" * 40,
    "ApprovedContractFromSha": "2" * 40,
    "ApprovedControlSha": "3" * 40,
    "ApprovedMigrationTargetSha": "4" * 40,
}
PRESENT_VALUES = {
    "Operation": "deploy",
    "TargetSha": "6" * 40,
    "ApprovedContractFromSha": "7" * 40,
    "ApprovedControlSha": "8" * 40,
    "ApprovedMigrationTargetSha": "9" * 40,
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"ERROR: {message}")


def fallback_line(name: str) -> str:
    return (
        f'if [[ -z "${{SSM_{name}+x}}" ]]; then '
        f'export SSM_{name}="{{{{{name}}}}}"; fi'
    )


def raw_template_contract_ok(command: str) -> bool:
    fallback_lines = [fallback_line(name) for name in PARAMETERS]
    expected_prefix = (
        "exec /usr/bin/env bash <<'PAWCYCLE_PRODUCTION_SSM_SCRIPT'\n"
        "#!/usr/bin/env bash\nset -Eeuo pipefail\n"
        + "\n".join(fallback_lines)
        + "\n"
    )
    if not command.startswith(expected_prefix):
        return False
    if any(command.count(line) != 1 for line in fallback_lines):
        return False

    templates = RAW_TEMPLATE_RE.findall(command)
    if len(templates) != len(PARAMETERS) or set(templates) != set(PARAMETERS):
        return False

    outside_fallbacks = command
    for line in fallback_lines:
        outside_fallbacks = outside_fallbacks.replace(line, "", 1)
    return RAW_TEMPLATE_RE.search(outside_fallbacks) is None


def posix_sh_command(values: dict[str, str] | None) -> list[str]:
    shell = shutil.which("sh")
    if shell is not None:
        return [shell]
    if os.name == "nt":
        wsl_bash = Path(os.environ.get("SystemRoot", r"C:\\Windows")) / "System32" / "bash.exe"
        if wsl_bash.is_file():
            assignments = ""
            if values is not None:
                assignments = " ".join(f"SSM_{name}={shlex.quote(value)}" for name, value in values.items()) + " "
            return [str(wsl_bash), "-c", f"exec env {assignments}/bin/sh -s"]
    raise SystemExit("ERROR: a POSIX sh is required to validate SSM runShellScript materialization")


def run_materialized_shell(
    command: str,
    *,
    values: dict[str, str] | None,
    substitute_legacy_templates: bool,
) -> list[str]:
    require(command.count('operation="${SSM_Operation:-}"') == 1, "SSM command parameter probe is ambiguous")
    probe = "\n".join(f'printf "%s\\n" "$SSM_{name}"' for name in PARAMETERS) + "\nexit 0"
    generated_shell = command.replace('operation="${SSM_Operation:-}"', probe, 1)
    if substitute_legacy_templates:
        for name, value in FALLBACK_VALUES.items():
            generated_shell = generated_shell.replace(f"{{{{{name}}}}}", value)

    environment = os.environ.copy()
    for name in PARAMETERS:
        environment.pop(f"SSM_{name}", None)
    if values is not None:
        for name, value in values.items():
            environment[f"SSM_{name}"] = value

    completed = subprocess.run(
        posix_sh_command(values),
        input=generated_shell.encode("utf-8"),
        env=environment,
        capture_output=True,
    )
    stdout = completed.stdout.decode("utf-8")
    stderr = completed.stderr.decode("utf-8")
    require(
        completed.returncode == 0,
        f"SSM generated shell exited {completed.returncode}: {stderr.strip() or stdout.strip()}",
    )
    return stdout.splitlines()


def main() -> None:
    document = json.loads(DOCUMENT.read_text(encoding="utf-8"))
    parameters = document.get("parameters")
    require(isinstance(parameters, dict) and set(parameters) == set(PARAMETERS), "unexpected SSM document parameters")
    for name in PARAMETERS:
        parameter = parameters[name]
        require(
            parameter.get("type") == "String" and parameter.get("interpolationType") == "ENV_VAR",
            f"SSM {name} must use String ENV_VAR interpolation",
        )

    steps = document.get("mainSteps")
    require(isinstance(steps, list) and len(steps) == 1, "SSM document must contain exactly one main step")
    command = "\n".join(steps[0].get("inputs", {}).get("runCommand", []))
    require(raw_template_contract_ok(command), "only the five exact ENV_VAR fallback assignments may use raw SSM templates")

    require(
        not raw_template_contract_ok(command + '\necho "{{TargetSha}}"'),
        "raw-template guard accepted compact interpolation outside an approved fallback",
    )
    require(
        not raw_template_contract_ok(command + '\necho "{{ TargetSha }}"'),
        "raw-template guard accepted spaced interpolation outside an approved fallback",
    )
    require(
        not raw_template_contract_ok(command + '\necho "{{UnexpectedParameter}}"'),
        "raw-template guard accepted an unexpected interpolation parameter",
    )

    require(
        command.startswith("exec /usr/bin/env bash <<'PAWCYCLE_PRODUCTION_SSM_SCRIPT'\n#!/usr/bin/env bash\nset -Eeuo pipefail"),
        "SSM runShellScript must enter Bash before its Bash-only command body",
    )
    require(
        command.endswith("\nPAWCYCLE_PRODUCTION_SSM_SCRIPT"),
        "SSM runShellScript Bash wrapper must contain a closed heredoc body",
    )

    fallback_output = run_materialized_shell(
        command,
        values=None,
        substitute_legacy_templates=True,
    )
    require(
        fallback_output == [FALLBACK_VALUES[name] for name in PARAMETERS],
        "legacy SSM template materialization did not reach the Bash command body",
    )
    present_output = run_materialized_shell(
        command,
        values=PRESENT_VALUES,
        substitute_legacy_templates=False,
    )
    require(
        present_output == [PRESENT_VALUES[name] for name in PARAMETERS],
        "SSM ENV_VAR materialization did not reach the Bash command body",
    )

    print("OPS-AUTO-008 SSM Bash materialization contracts validated")


if __name__ == "__main__":
    main()
