#!/usr/bin/env python3

from __future__ import annotations

import json
import os
from pathlib import Path
import re
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
    expected_prefix = "#!/usr/bin/env bash\nset -Eeuo pipefail\n" + "\n".join(fallback_lines) + "\n"
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


def run_fallback_probe(command: str, values: dict[str, str] | None) -> list[str]:
    fallback_block = "\n".join(fallback_line(name) for name in PARAMETERS)
    for name, value in FALLBACK_VALUES.items():
        fallback_block = fallback_block.replace(f"{{{{{name}}}}}", value)

    probe = fallback_block + "\n" + "\n".join(
        f'printf "%s\\n" "$SSM_{name}"' for name in PARAMETERS
    )
    environment = os.environ.copy()
    for name in PARAMETERS:
        environment.pop(f"SSM_{name}", None)
    if values is not None:
        for name, value in values.items():
            environment[f"SSM_{name}"] = value

    completed = subprocess.run(
        ["bash", "-c", probe],
        cwd=ROOT,
        env=environment,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    return completed.stdout.splitlines()


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

    fallback_output = run_fallback_probe(command, None)
    require(
        fallback_output == [FALLBACK_VALUES[name] for name in PARAMETERS],
        "unset SSM ENV_VAR values did not use the bounded document fallbacks",
    )
    present_output = run_fallback_probe(command, PRESENT_VALUES)
    require(
        present_output == [PRESENT_VALUES[name] for name in PARAMETERS],
        "existing SSM ENV_VAR values did not retain precedence over document fallbacks",
    )

    print("OPS-AUTO-007 SSM fallback interpolation contracts validated")


if __name__ == "__main__":
    main()
