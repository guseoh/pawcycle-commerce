#!/usr/bin/env python3
"""Outbound-only OCI CD controller. Install reviewed code separately; default OFF.

Never imports fetched source, GitHub artifacts, PR code or environment secrets.
The root policy and independently approved compatibility observer are live G0
inputs, not repository approvals. Manual recovery remains oci-release's gate.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import signal
import sys
import time
import urllib.request


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


HERE = Path(__file__).resolve().parent
release = module("oci_release", HERE / "oci-release.py")
# Installed alongside this controller, never loaded from the fetched mirror.
classifier = module("image_classifier", HERE / "classify-production-image-changes.py"
                    if (HERE / "classify-production-image-changes.py").exists()
                    else HERE.parents[1] / "scripts/classify-production-image-changes.py")
require = release.require
REPO = "guseoh/pawcycle-commerce"
ENABLED_PATH = Path("/etc/pawcycle/oci-cd-enabled")
JOBS = {"Classify validation changes", "Harness validation", "Backend and MySQL validation",
        "Frontend validation", "Application validation",
        *{"Production contract validation (" + lane + ")" for lane in ("contracts", "compose", "auth-lifecycle")}}
AUTO_MODE = "frontend-cosmetic-v1"
CSS_PATH = "frontend/src/app/globals.css"


class CDHost(release.Host):
    def api(self, endpoint):
        # Fixed public read-only API. No token, redirect to credential endpoint,
        # shell, artifact download or URL from remote data is accepted.
        request = urllib.request.Request("https://api.github.com/repos/" + REPO + "/" + endpoint,
                                         headers={"Accept": "application/vnd.github+json",
                                                  "User-Agent": "pawcycle-host-pull"})
        with urllib.request.urlopen(request, timeout=30) as response:
            require(response.url.startswith("https://api.github.com/repos/" + REPO + "/"), "API redirect refused")
            data = response.read(4 * 1024 * 1024 + 1)
            require(len(data) <= 4 * 1024 * 1024, "API response too large")
            return json.loads(data)

    def pages(self, endpoint, key):
        result = []
        for page in range(1, 21):
            document = self.api(endpoint + ("&" if "?" in endpoint else "?") + f"per_page=100&page={page}")
            items = document[key]
            result.extend(items)
            if len(items) < 100:
                require(len(result) == document["total_count"], "incomplete API evidence")
                return result
        raise release.Refused("API pagination limit; evidence incomplete")

    def refresh(self):
        require(os.geteuid() == 0 and not os.environ.get("GITHUB_ACTIONS"), "approved host only")
        release.protected(self.repository, directory=True)
        require(self.git("rev-parse", "--is-bare-repository").strip() == b"true", "dedicated bare mirror required")
        for name in ("config", "HEAD", "objects", "refs"):
            release.protected(self.repository / name, directory=name in ("objects", "refs"))
        require(self.git("remote", "get-url", "origin").decode().strip() == release.ORIGIN, "wrong Git origin")
        refs = self.git("for-each-ref", "--format=%(objectname)", "refs/remotes/origin/main").decode().splitlines()
        require(len(refs) <= 1, "ambiguous cached main")
        old = refs[0] if refs else "0" * 40
        require(release.SHA.fullmatch(old), "invalid cached main")
        # No checkout/reset/chown, executable-source update or force ref update.
        # Git's ref lock/atomic object writes reject concurrent/stale updates.
        self.command("git", "-c", "core.hooksPath=/dev/null", "-c", "credential.helper=",
                     "-C", str(self.repository), "fetch", "--refmap=", "--no-tags", "--no-recurse-submodules",
                     "origin", "refs/heads/main:refs/heads/cd-main",
                     env={"GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": "/dev/null"})
        # Fetch enforces fast-forward on refs/heads (remote-tracking namespaces
        # alone do not enforce that). Publish the cached ref with a CAS update.
        new = self.git("rev-parse", "refs/heads/cd-main").decode().strip()
        require(release.SHA.fullmatch(new), "invalid fetched main")
        if refs:
            require(release.SHA.fullmatch(old), "invalid cached main")
            self.git("merge-base", "--is-ancestor", old, new)
        self.git("update-ref", "--no-deref", "refs/remotes/origin/main", new, old)
        self.initial()

    def paths(self, base, head):
        return self.git("diff", "--no-renames", "--name-only", base, head).decode().splitlines()

    def candidate(self, current):
        self.git("merge-base", "--is-ancestor", current, self.main)
        history = self.git("rev-list", "--first-parent", current + ".." + self.main).decode().splitlines()
        # Newest relevant commit is a barrier even if unsafe or CI is pending.
        # Never fall back to an older successful build behind a newer candidate.
        for sha in history:
            require(release.SHA.fullmatch(sha), "invalid history SHA")
            parent = self.git("rev-parse", sha + "^").decode().strip()
            if classifier.classify(self.paths(parent, sha))["deploy"]:
                return sha
        return None

    def ci(self, sha):
        for workflow, jobs in (("validate-conventions.yml", JOBS),
                               ("publish-production-images.yml", {"Publish production images"})):
            runs = self.pages("actions/workflows/" + workflow + "/runs?head_sha=" + sha, "workflow_runs")
            runs = [run for run in runs if run.get("event") == "push" and
                    run.get("head_sha") == sha and run.get("head_branch") == "main" and
                    run.get("path") == ".github/workflows/" + workflow and
                    run.get("head_repository", {}).get("full_name") == REPO]
            require(runs, "exact main SHA CI missing")
            run = max(runs, key=lambda r: (r["run_number"], r["run_attempt"]))
            require(run.get("status") == "completed" and run.get("conclusion") == "success", "main SHA CI not successful")
            require(type(run["id"]) is int and type(run["run_attempt"]) is int, "invalid CI identity")
            checks = self.pages(f"actions/runs/{run['id']}/attempts/{run['run_attempt']}/jobs", "jobs")
            for name in jobs:
                selected = [j for j in checks if j["name"] == name]
                require(len(selected) == 1 and selected[0].get("head_sha") == sha and
                        selected[0].get("status") == "completed" and selected[0].get("conclusion") == "success",
                        "required exact SHA job missing, skipped or failed")

    def manifest(self, sha, policy, current=None):
        result = {"sha": sha, "compose_source_sha": sha, "nginx_source_sha": sha,
                  "compose_sha256": release.digest(self.git("show", sha + ":" + release.COMPOSE)),
                  "nginx_sha256": release.digest(self.git("show", sha + ":" + release.NGINX).replace(
                      b"__PAWCYCLE_DOMAIN__", release.DOMAIN.encode())),
                  "migration_tree": self.git("rev-parse", sha + ":backend/src/main/resources/db/migration").decode().strip()}
        for role in ("backend", "frontend"):
            if role == "backend" and policy.get("compatibility_mode") == AUTO_MODE:
                require(current is not None, "verified Backend baseline required")
                result[role] = {**current[role], "source_sha": release.image_sha(current, role)}
                continue
            repository = "ghcr.io/" + REPO + "-" + role
            raw = self.docker("buildx", "imagetools", "inspect", repository + ":" + sha, "--raw")
            image_digest = release.digest(raw)
            document = json.loads(raw)
            if "manifests" in document:
                matches = [m for m in document["manifests"] if m.get("platform", {}).get("os") == "linux" and
                           m.get("platform", {}).get("architecture") == "amd64"]
                require(len(matches) == 1 and release.DIGEST.fullmatch(matches[0]["digest"]), "ambiguous platform")
                child = self.docker("buildx", "imagetools", "inspect", repository + "@" + matches[0]["digest"], "--raw")
                require(release.digest(child) == matches[0]["digest"], "altered child manifest")
                document = json.loads(child)
            result[role] = {"repository": repository, "digest": image_digest,
                            "config_digest": document["config"]["digest"],
                            "unpacked_bytes": policy["unpacked_bytes"][role]}
        release.validate_manifest(result)
        self.registry(result)  # independently refetch digest/config/platform/revision/source
        return result

    def compatibility(self, current, target, policy, policy_path=None):
        observer = Path(policy["compatibility_observer"])
        release.protected(observer, mode=0o700)
        require(release.digest(observer.read_bytes()) == policy["observer_sha256"], "observer identity mismatch")
        # Operator-installed observer gets non-secret identities over stdin-free
        # argv; its output is never printed. It must independently observe live
        # DB epoch and evaluate application/data compatibility in both directions.
        extra = ("--auto-policy", str(policy_path)) if policy.get("compatibility_mode") == AUTO_MODE else ()
        return json.loads(self.command(str(observer), current, target, *extra, timeout=60))


class Controller:
    def __init__(self, host, policy_path):
        self.host, self.policy_path, self.target = host, Path(policy_path), None
        self.started = int(time.time())

    def policy(self):
        h = self.host
        require(not os.environ.get("GITHUB_ACTIONS"), "CI cannot activate Production")
        release.protected(h.state, directory=True, mode=0o700)
        require(release.read_private(ENABLED_PATH).strip() == b"enabled", "CD enable sentinel absent")
        require(not (h.state / "oci-cd-disabled").exists() and not (h.state / "oci-cd-disabled").is_symlink(), "CD kill switch is set")
        p = release.load(self.policy_path)
        require(p["format"] == 1 and p["enabled"] is True and p["target"] == "oci-app01" and
                p["repository"] == release.ORIGIN and p["approval_evidence"] and
                time.time() < p["expires_unix"], "policy absent, OFF, expired or unapproved")
        require(p["stop_on"] == ["gate-failure", "activation-failure", "transition", "db-runtime-drift"] and
                p["notifications"] == "journal", "stop/notification policy is incomplete")
        for name in ("oci-cd.py", "oci-release.py", "production_env.py", "classify-production-image-changes.py"):
            installed = HERE / name
            release.protected(installed)
            require(release.digest(installed.read_bytes()) == p["controller_sha256"][name], "installed controller drift")
        require(p["allowed_prefixes"] and all(prefix.startswith(("backend/", "frontend/", "infra/production/")) and
                ".." not in prefix and "*" not in prefix for prefix in p["allowed_prefixes"]), "unbounded policy scope")
        require(set(p["allowed_risks"]) <= {"application", "sensitive"}, "unknown/runtime change cannot auto-activate")
        mode = p.get("compatibility_mode", "manual-pair-v1")
        require(mode in ("manual-pair-v1", AUTO_MODE), "unknown compatibility mode")
        if mode == AUTO_MODE:
            require(p["allowed_prefixes"] == [CSS_PATH] and p["allowed_risks"] == ["application"] and
                    release.SHA.fullmatch(p.get("automatic_baseline_sha", "")), "unbounded cosmetic policy")
        require(p["capacity_evidence"] and type(p["reserve_bytes"]) is int and p["reserve_bytes"] >= release.GIB and
                type(p["rollback_extra_bytes"]) is int and p["rollback_extra_bytes"] > 0,
                "approved capacity/rollback write evidence missing")
        return p

    def evidence(self, current, manifest, p):
        h, sha = self.host, manifest["sha"]
        require(current["verified"] is True and current.get("evidence") and current.get("compatibility_evidence") and
                release.DIGEST.fullmatch(current.get("approval_sha256", "")), "previous normal baseline is unverified")
        require(h.env_hash == p["runtime_env_sha256"] == current["runtime_env_sha256"] and
                current["db_epoch"] == p["db_epoch"] and
                manifest["migration_tree"] == p["migration_tree"] == current["manifest"]["migration_tree"], "DB/migration/runtime drift")
        for key in ("compose_sha256", "nginx_sha256"):
            require(manifest[key] == current["manifest"][key] == p[key], "runtime config drift")
        automatic = p.get("compatibility_mode") == AUTO_MODE
        proof = (h.compatibility(current["manifest"]["sha"], sha, p, self.policy_path) if automatic else
                 h.compatibility(current["manifest"]["sha"], sha, p))
        require(proof["current"] == current["manifest"]["sha"] and proof["target"] == sha and
                proof["db_epoch"] == p["db_epoch"] and proof["runtime_env_sha256"] == h.env_hash and
                proof["application_data_compatible"] is True and proof["rollback_safe"] is True and
                proof["evidence"] and proof["observations"] and
                time.time() - 120 <= proof["checked_unix"] <= time.time() < proof["expires_unix"] <= proof["checked_unix"] + 120,
                "live DB/application/data compatibility evidence missing or stale")
        if automatic:
            observed = proof["observations"]
            require(observed["mode"] == AUTO_MODE and observed["main"] == h.main and
                    observed["baseline"] == p["automatic_baseline_sha"] and
                    observed["policy_sha256"] == release.identity(p) and
                    release.DIGEST.fullmatch(observed["history_sha256"]), "automatic source evidence mismatch")
            # Never auto-authorize a rebuilt Backend binary. Keep the exact
            # known healthy digest/config/budget and its original source SHA.
            expected = {**current["manifest"]["backend"],
                        "source_sha": release.image_sha(current["manifest"], "backend")}
            require(manifest["backend"] == expected, "automatic Backend artifact must remain current")
            proof["evidence"] += "/backend/" + release.identity(expected)
        return proof

    def reconcile(self):
        h, p = self.host, self.policy()
        h.refresh()
        state = release.Release(h).read_state()
        require(state, "separate baseline adoption required")
        require(not (h.state / "release-state-transition").exists() and
                not (h.state / "release-state-transition").is_symlink(), "manual transition recovery required")
        current = state["current"]["manifest"]["sha"]
        self.previous = current
        self.target = h.candidate(current)
        if self.target is None:
            return "no-candidate"
        sha, tip = self.target, h.main
        paths = h.paths(current, sha)
        intent = classifier.classify(paths)
        require(set(intent["risks"]) <= set(p["allowed_risks"]), "candidate outside approved risk scope")
        require(all(any(path.startswith(prefix) for prefix in p["allowed_prefixes"])
                    for path in paths if classifier.classify([path])["deploy"]), "candidate outside approved path scope")
        h.ci(sha)
        manifest = (h.manifest(sha, p, state["current"]["manifest"]) if p.get("compatibility_mode") == AUTO_MODE
                    else h.manifest(sha, p))
        proof = self.evidence(state["current"], manifest, p)
        approval = {"action": "preflight", "manifest_sha256": release.identity(manifest), "expected_current": current,
                    "evidence": p["approval_evidence"], "db_compatibility_evidence": proof["evidence"],
                    "db_epoch": p["db_epoch"], "compatible_with": [current], "rollback_safe": True,
                    "expires_unix": min(int(time.time()) + 120, proof["expires_unix"], p["expires_unix"]),
                    **{key: p[key] for key in ("capacity_evidence", "reserve_bytes", "rollback_extra_bytes")}}

        def guard():
            require(self.policy() == p, "policy changed during checks")
            h.initial()
            require(h.main == tip and h.candidate(current) == sha, "main advanced; candidate superseded")
            h.ci(sha)
            self.evidence(state["current"], manifest, p)
            require(self.policy() == p, "policy/kill switch changed during final evidence check")

        def start():
            # Under both Release locks, after guard and final approval, before
            # the first durable transition or application mutation. A failed
            # write is conservative: any surviving attempt still fences retry.
            release.atomic(h.state / "oci-cd-attempt", release.encoded({"reason": "attempt-in-progress", "sha": sha}))

        engine = release.Release(h, activation_guard=guard, activation_start=start)
        engine.run("preflight", manifest, approval)
        approval["action"] = "activate"
        try:
            engine.run("activate", manifest, approval, execute=True)
        except BaseException:
            if (h.state / "oci-cd-attempt").exists() or (h.state / "release-state-transition").exists():
                release.atomic(h.state / "oci-cd-disabled", release.encoded({"reason": "activation-failed", "sha": sha}))
            raise
        (h.state / "oci-cd-attempt").unlink(missing_ok=True)
        release.sync_directory(h.state)
        return "activate-verified"

    def run(self):
        h = self.host
        installed = False
        try:
            # A missing/OFF policy causes zero source, Docker or DB operations.
            self.policy()
            installed = True
            with release.locked(h.state / "oci-cd.lock", create=True):
                require(not (h.state / "oci-cd-attempt").exists() and
                        not (h.state / "oci-cd-attempt").is_symlink(), "prior attempt requires manual recovery")
                result = self.reconcile()
            event = {"result": result, "gate": None}
        except (Exception, KeyboardInterrupt) as error:
            unsafe = any((h.state / name).exists() or (h.state / name).is_symlink()
                         for name in ("oci-cd-attempt", "release-state-transition"))
            event = {"result": "failed-disabled" if unsafe else "blocked",
                     "gate": str(error) if isinstance(error, release.Refused) else "invalid input, unavailable evidence or interrupted"}
        event.update(sha=self.target, previous=getattr(self, "previous", None), started_unix=self.started,
                     ended_unix=int(time.time()), disabled=any(
                         (h.state / name).exists() or (h.state / name).is_symlink()
                         for name in ("oci-cd-disabled", "oci-cd-attempt", "release-state-transition")))
        # stdout is systemd's journal notification channel. No webhook/credential
        # is invented. Suppress unchanged timer blocks while persisting result.
        try:
            path = h.state / "oci-cd-result.json"
            old = release.load(path) if installed and path.exists() else {}
            if any(old.get(key) != event[key] for key in ("result", "gate", "sha", "previous", "disabled")):
                if installed:
                    release.atomic(path, release.encoded(event))
                print(json.dumps(event, sort_keys=True))
        except (OSError, release.Refused, ValueError):
            print(json.dumps(event, sort_keys=True))
        return event


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", type=Path, default=Path("/etc/pawcycle/oci-cd-policy.json"))
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--execute-policy", action="store_true")
    args = parser.parse_args()
    if not args.execute_policy or sys.platform != "linux" or os.geteuid() != 0 or os.environ.get("GITHUB_ACTIONS"):
        parser.error("approved Linux host and explicit --execute-policy required")
    host = CDHost(args.repository, "/opt/pawcycle/state", "/opt/pawcycle/runtime",
                  "/run/lock/pawcycle-production-https-renewal.lock")
    def interrupted(*_):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, interrupted)
    event = Controller(host, args.policy).run()
    # A policy/CI block is a recorded no-op, not an automatically retried failed
    # unit. Activation failure stays nonzero and the durable latch forbids retry.
    return 1 if event["result"] == "failed-disabled" else 0


if __name__ == "__main__":
    raise SystemExit(main())
