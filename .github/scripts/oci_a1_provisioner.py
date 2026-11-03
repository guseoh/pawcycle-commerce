#!/usr/bin/env python3
"""One fail-closed tick; no OCI dependency in the state machine or persistence."""
import argparse
from contextlib import contextmanager
import hashlib
import json
import math
import os
from pathlib import Path
import sys
import time
import uuid

from oci_a1_capacity_monitor import REGION, AD, SHAPE


class GateError(Exception):
    """Only fixed, non-sensitive reason codes may cross the logging boundary."""


class ApiError(Exception):
    def __init__(self, kind):
        self.kind = kind


def require(condition, reason):
    if not condition:
        raise GateError(reason)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def validate_config(c, now, host):
    require(c.get("mode") in ("DISABLED", "dry-run", "enabled"), "invalid_mode")
    require((c.get("region"), c.get("availability_domain"), c.get("shape"),
             c.get("ocpus"), c.get("memory_gb"), c.get("boot_volume_gb")) ==
            (REGION, AD, SHAPE, 2, 12, 50), "target_not_approved")
    require(c.get("assign_public_ip") is False, "public_ip_forbidden")
    require(c.get("executor_machine_sha256") == host, "wrong_executor")
    require(c.get("single_executor_confirmed") is True, "executor_not_fenced")
    for key, kind in (("tenancy_id", "tenancy"), ("compartment_id", "compartment"),
                      ("image_id", "image"), ("subnet_id", "subnet"), ("vcn_id", "vcn")):
        require(str(c.get(key, "")).startswith("ocid1." + kind + "."), "invalid_resource_config")
    require(str(c.get("ssh_public_key", "")).startswith(("ssh-ed25519 ", "ssh-rsa "))
            and "\n" not in c["ssh_public_key"], "invalid_ssh_key")
    require(len(str(c.get("network_sha256", ""))) == 64, "network_not_approved")
    require(bool(c.get("security_list_ids")) and isinstance(c.get("nsg_ids"), list), "security_not_approved")
    require(str(c.get("image_architecture")).lower() == "aarch64", "image_not_arm64")
    require(c.get("free_image_confirmed") is True, "image_cost_not_approved")
    require(c.get("always_free_confirmed") is True, "free_tier_not_approved")
    require(c.get("free_ocpus") == 2 and c.get("free_memory_gb") == 12
            and c.get("free_storage_gb") == 200, "free_budget_not_approved")
    expires = c.get("approval_expires_at", 0)
    require(isinstance(expires, (int, float)) and math.isfinite(expires)
            and now < expires <= now + 86400, "approval_expired_or_too_long")
    require(bool(c.get("deployment_id")) and len(c["deployment_id"]) <= 64, "invalid_deployment_id")


def config_digest(c):
    # Mode and renewed owner attestation do not change the immutable launch plan.
    return digest({k: v for k, v in c.items() if k not in ("mode", "approval_expires_at")})


class Store:
    def __init__(self, directory):
        self.directory = Path(directory)
        self.path = self.directory / "state.json"

    @contextmanager
    def lock(self):
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        with (self.directory / "worker.lock").open("a+b") as handle:
            handle.seek(0)
            if os.name == "nt":
                import msvcrt
                if os.fstat(handle.fileno()).st_size == 0:
                    handle.write(b"0")
                    handle.flush()
                handle.seek(0)
                try:
                    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                except OSError:
                    yield False
                    return
            else:
                import fcntl
                try:
                    fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
                except BlockingIOError:
                    yield False
                    return
            try:
                yield True
            finally:
                if os.name == "nt":
                    handle.seek(0)
                    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    fcntl.flock(handle, fcntl.LOCK_UN)

    def load(self):
        # Missing/corrupt state is NEVER interpreted as permission to initialize.
        s = json.loads(self.path.read_text(encoding="utf-8"))
        require(s.get("version") == 1 and s.get("mode") in
                ("DISABLED", "dry-run", "enabled", "approval_expired", "provisioned", "halted"), "invalid_state")
        require(s.get("phase") in ("ready", "pending", "tracking", "done"), "invalid_state")
        require(bool(s.get("token")) and bool(s.get("config_sha256")), "invalid_state")
        require((s["phase"] != "done" or s["mode"] == "provisioned") and
                (s["mode"] != "provisioned" or s["phase"] == "done"), "invalid_state")
        if s["phase"] in ("pending", "tracking"):
            require(isinstance(s.get("requested_at"), (int, float)), "invalid_state")
        if s["phase"] == "tracking":
            require(bool(s.get("instance_id")), "invalid_state")
        return s

    def save(self, s):
        temp = self.directory / "state.json.tmp"
        with temp.open("w", encoding="utf-8") as out:
            json.dump(s, out, sort_keys=True)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temp, self.path)
        if s["mode"] in ("provisioned", "halted"):
            with (self.directory / "STOP").open("w") as out:
                out.write(s["mode"] + "\n")
                out.flush()
                os.fsync(out.fileno())
        if os.name != "nt":
            fd = os.open(self.directory, os.O_DIRECTORY)
            try:
                os.fsync(fd)
            finally:
                os.close(fd)

    def initialize(self, c):
        require(not self.path.exists() and not (self.directory / "STOP").exists(), "state_already_exists")
        self.save(dict(version=1, mode="DISABLED", phase="ready", token=str(uuid.uuid4()),
                       config_sha256=config_digest(c), failures=0, next_at=0, last_ok=0,
                       initialized_at=time.time(), approved_expiry=c.get("approval_expires_at", 0)))


class Worker:
    def __init__(self, api, store, notify, now=time.time, host=None):
        self.api, self.store, self.notify, self.now = api, store, notify, now
        self.host = host

    def reapprove(self, c):
        with self.store.lock() as acquired:
            require(acquired, "locked")
            s = self.store.load()
            require(s["mode"] not in ("halted", "provisioned"), "terminal_state")
            require(not (self.store.directory / "STOP").exists(), "stop_latch_present")
            validate_config(c, self.now(), self.host)
            require(s["config_sha256"] == config_digest(c), "config_changed")
            require(c["approval_expires_at"] > s.get("approved_expiry", 0), "approval_not_extended")
            s["approved_expiry"] = c["approval_expires_at"]
            s.pop("notified_approval_expiring", None)
            s["mode"] = "DISABLED"
            self.store.save(s)

    def alert(self, s, event):
        key = "notified_" + event
        if s.get(key):
            return
        try:
            self.notify(event)
        except Exception:
            # Never propagate webhook URLs or exception details. No launch rollback.
            print("notification_delivery_failed", file=sys.stderr)
            return
        s[key] = True
        self.store.save(s)

    def halt(self, s, reason):
        s.update(mode="halted", reason=reason)
        self.store.save(s)
        self.alert(s, "halted")

    def backoff(self, s, now):
        s["failures"] += 1
        s["next_at"] = now + min(60 * 2 ** (s["failures"] - 1), 900)
        self.store.save(s)
        if s["failures"] >= 5:
            self.halt(s, "transient_retry_budget_exhausted")

    def tick(self, c):
        with self.store.lock() as acquired:
            if not acquired:
                return "locked"
            s = self.store.load()
            if s["mode"] in ("provisioned", "halted"):
                self.alert(s, s["mode"])
                return s["mode"]
            if s["mode"] == "approval_expired":
                return "approval_expired"
            if c.get("mode", "DISABLED") == "DISABLED":
                s["mode"] = "DISABLED"
                self.store.save(s)
                return "DISABLED"
            now = self.now()
            try:
                expires = c.get("approval_expires_at", 0)
                require(isinstance(expires, (int, float)) and math.isfinite(expires), "invalid_approval")
                # A legacy state has no durable approval binding: never infer one.
                if "approved_expiry" not in s:
                    s["mode"] = "approval_expired"
                    self.store.save(s)
                    return "approval_expired"
                if expires != s["approved_expiry"]:
                    s["mode"] = "approval_expired"
                    self.store.save(s)
                    return "approval_expired"
                if now >= expires:
                    s["mode"] = "approval_expired"
                    self.store.save(s)
                    return "approval_expired"
                if expires - now <= 3600:
                    self.alert(s, "approval_expiring")
                validate_config(c, now, self.host)
                require(s["config_sha256"] == config_digest(c), "config_changed")
                require(not (self.store.directory / "STOP").exists(), "stop_latch_present")
                # A dry-run must never resume an earlier real launch transaction.
                if c["mode"] == "dry-run" and s["phase"] != "ready":
                    return "pending_requires_reconciliation"
                s["mode"] = c["mode"]
                if now < s["next_at"]:
                    return "backoff"
                if s["phase"] in ("pending", "tracking"):
                    self.reconcile(c, s, now)
                else:
                    status = self.api.capacity(c)
                    require(status in ("AVAILABLE", "OUT_OF_HOST_CAPACITY"), "invalid_capacity")
                    if status == "AVAILABLE" or c["mode"] == "dry-run":
                        self.api.preflight(c)
                        if c["mode"] == "enabled":
                            validate_config(c, self.now(), self.host)
                            # fsync intent before the only potentially ambiguous call.
                            s.update(phase="pending", requested_at=now)
                            self.store.save(s)
                            try:
                                validate_config(c, self.now(), self.host)
                                instance_id = self.api.launch(c, s["token"])
                            except ApiError as exc:
                                # A 429 after LaunchInstance may be ambiguous: retain
                                # the durable intent and reconcile instead of relaunching.
                                # Only a known capacity rejection returns to polling.
                                if exc.kind == "capacity":
                                    s["phase"] = "ready"
                                raise
                            require(bool(instance_id), "missing_launch_identity")
                            s.update(phase="tracking", instance_id=instance_id)
                            self.store.save(s)
                            self.reconcile(c, s, now)
                s["last_ok"] = now
                if s["phase"] != "pending":
                    s.update(failures=0, next_at=0)
                self.store.save(s)
            except ApiError as exc:
                if exc.kind == "capacity":
                    s.update(failures=0, next_at=now + 60)
                    self.store.save(s)
                elif exc.kind in ("throttled", "transient", "uncertain"):
                    self.backoff(s, now)
                else:
                    self.halt(s, "oci_access_quota_or_configuration")
            except GateError as exc:
                if str(exc) == "approval_expired_or_too_long" and self.now() >= c.get("approval_expires_at", 0):
                    s["mode"] = "approval_expired"
                    self.store.save(s)
                else:
                    self.halt(s, str(exc))
            except Exception:
                self.halt(s, "unexpected_failure")
            return s["mode"]

    def reconcile(self, c, s, now):
        matches = self.api.reconcile(c, s["token"], s.get("instance_id"))
        require(len(matches) <= 1, "duplicate_instances")
        if not matches:
            require(now - s["requested_at"] < 900, "unresolved_launch_manual_recovery")
            s["next_at"] = now + 60
            return
        instance_id, status = matches[0]
        s.update(phase="tracking", instance_id=instance_id)
        require(status in ("PROVISIONING", "STARTING", "RUNNING"), "unexpected_instance_state")
        require(now - s["requested_at"] < 1800 or status == "RUNNING", "instance_start_timeout")
        if status == "RUNNING":
            s.update(mode="provisioned", phase="done")
            self.store.save(s)  # success is durable BEFORE Discord
            self.alert(s, "provisioned")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--state-dir", required=True)
    parser.add_argument("--initialize", action="store_true")
    parser.add_argument("--reapprove", action="store_true", help="Explicitly approve an unchanged plan")
    parser.add_argument("--health", action="store_true", help="Local stale-run watchdog; no OCI calls")
    parser.add_argument("--network-fingerprint", action="store_true", help="Read-only network approval hash")
    args = parser.parse_args()
    store = Store(args.state_dir)
    try:
        c = json.loads(Path(args.config).read_text(encoding="utf-8"))
        if args.health:
            from oci_a1_provisioner_api import notify_discord
            with store.lock() as acquired:
                if not acquired:
                    return 0
                s = store.load()
                if c.get("mode") == "enabled" and s["mode"] not in ("provisioned", "halted", "approval_expired"):
                    stale = time.time() - (s.get("last_ok") or s["initialized_at"]) > 180
                    if stale and not s.get("notified_stale"):
                        notify_discord("stale")
                        s["notified_stale"] = True
                        store.save(s)
                    elif not stale and s.pop("notified_stale", None):
                        store.save(s)
            return 0
        if args.initialize:
            require(c.get("mode") == "DISABLED", "initialize_disabled_only")
            with store.lock() as acquired:
                require(acquired, "locked")
                store.initialize(c)
            print("DISABLED: initialized; no OCI calls")
            return 0
        if args.reapprove:
            host = hashlib.sha256(Path("/etc/machine-id").read_bytes().strip()).hexdigest()
            Worker(None, store, None, host=host).reapprove(c)
            print("approval recorded; next tick reconciles pending requests first")
            return 0
        # Disabled/terminal ticks do not need OCI credentials or an SDK.
        from oci_a1_provisioner_api import OciApi, notify_discord
        if args.network_fingerprint:
            require(c.get("mode") == "dry-run", "fingerprint_dry_run_only")
            api = OciApi()
            api.connect(c)
            print("network_sha256=" + api.network_fingerprint(c))
            return 0
        host = hashlib.sha256(Path("/etc/machine-id").read_bytes().strip()).hexdigest()
        result = Worker(OciApi(), store, notify_discord, host=host).tick(c)
        print("oci_a1_worker: " + result)
        return 0
    except Exception:
        # Missing state/config, disk full, SDK import failure etc. latch STOP too.
        if not (args.health or args.reapprove or args.network_fingerprint):
            try:
                store.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
                (store.directory / "STOP").touch()
            except OSError:
                pass
            try:
                from oci_a1_provisioner_api import notify_discord
                notify_discord("halted")
            except Exception:
                pass
        print("oci_a1_worker: fatal_local_failure; operator recovery required", file=sys.stderr)
        return 1


if __name__ == "__main__":
    # The adapter must share exception identities when this file is the CLI.
    sys.modules["oci_a1_provisioner"] = sys.modules[__name__]
    sys.exit(main())
