"""Offline invariants: fake OCI only, including restart and process overlap."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace as NS
import unittest
from unittest.mock import Mock, patch

import oci_a1_provisioner as worker
from oci_a1_provisioner_api import OciApi, TAG, DEPLOYMENT


def config():
    return dict(mode="enabled", region=worker.REGION, availability_domain=worker.AD,
                shape=worker.SHAPE, ocpus=2, memory_gb=12, boot_volume_gb=50,
                tenancy_id="ocid1.tenancy.test", compartment_id="ocid1.compartment.test",
                image_id="ocid1.image.test", subnet_id="ocid1.subnet.test", vcn_id="ocid1.vcn.test",
                ssh_public_key="ssh-ed25519 TEST-PUBLIC-KEY", executor_machine_sha256="host",
                single_executor_confirmed=True, image_architecture="aarch64", free_image_confirmed=True,
                network_sha256="0" * 64, security_list_ids=["security"], nsg_ids=[],
                always_free_confirmed=True, free_ocpus=2, free_memory_gb=12,
                free_storage_gb=200, approval_expires_at=10000, assign_public_ip=False,
                deployment_id="offline-test")


class FakeApi:
    def __init__(self):
        self.status = "AVAILABLE"
        self.launches = []
        self.matches = [("fake-instance", "RUNNING")]
        self.error = None
        self.preflight_error = None
        self.capacity_calls = 0

    def capacity(self, c):
        self.capacity_calls += 1
        return self.status

    def preflight(self, c):
        if self.preflight_error:
            raise self.preflight_error

    def launch(self, c, token):
        self.launches.append(token)
        if self.error:
            raise self.error
        return "fake-instance"

    def reconcile(self, c, token, instance_id):
        return self.matches


class WorkerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.store = worker.Store(self.temp.name)
        self.c = config()
        self.store.initialize(self.c)
        self.api, self.notify = FakeApi(), Mock()
        self.clock = 1000
        self.w = worker.Worker(self.api, self.store, self.notify, now=lambda: self.clock, host="host")

    def test_available_launch_once_running_durable_before_notification(self):
        def notified(event):
            self.assertEqual(event, "provisioned")
            self.assertEqual(self.store.load()["mode"], "provisioned")
            self.assertTrue((self.store.directory / "STOP").exists())
        self.notify.side_effect = notified
        self.assertEqual(self.w.tick(self.c), "provisioned")
        self.w.tick(self.c)
        self.assertEqual(len(self.api.launches), 1)
        self.notify.assert_called_once()

    def test_unavailable_silent_zero_launch(self):
        self.api.status = "OUT_OF_HOST_CAPACITY"
        for _ in range(3):
            self.w.tick(self.c)
        self.assertEqual(self.api.launches, [])
        self.notify.assert_not_called()
        self.assertEqual(self.store.load()["last_ok"], 1000)

    def test_expiry_warning_once_and_expired_state_preserved(self):
        self.api.status = "OUT_OF_HOST_CAPACITY"
        self.clock = 6400
        self.w.tick(self.c)
        self.w.tick(self.c)
        self.notify.assert_called_once_with("approval_expiring")
        token = self.store.load()["token"]
        self.clock = 10000
        self.assertEqual(self.w.tick(self.c), "approval_expired")
        s = self.store.load()
        self.assertEqual((s["token"], s["phase"]), (token, "ready"))
        self.clock = 9000  # A backwards clock adjustment cannot resume approval.
        self.assertEqual(self.w.tick(self.c), "approval_expired")
        self.assertEqual(self.api.launches, [])

    def test_campaign_cutoff_halts_even_approval_expired_without_oci_calls(self):
        self.clock = worker.CAMPAIGN_END_UTC
        state = self.store.load()
        state["mode"] = "approval_expired"
        self.store.save(state)
        token = state["token"]
        self.assertEqual(self.w.tick(self.c), "halted")
        result = self.store.load()
        self.assertEqual((result["mode"], result["reason"], result["token"], result["phase"]),
                         ("halted", "campaign_ended", token, "ready"))
        self.assertTrue((self.store.directory / "STOP").exists())
        self.assertEqual(self.api.capacity_calls, 0)
        self.assertEqual(self.api.launches, [])

    def test_campaign_cutoff_preserves_pending_request(self):
        state = self.store.load()
        state.update(mode="enabled", phase="pending",
                     requested_at=worker.CAMPAIGN_END_UTC - 100)
        self.store.save(state)
        self.clock = worker.CAMPAIGN_END_UTC
        self.assertEqual(self.w.tick(self.c), "halted")
        result = self.store.load()
        self.assertEqual((result["token"], result["phase"], result["requested_at"]),
                         (state["token"], "pending", state["requested_at"]))
        self.assertEqual(self.api.capacity_calls, 0)
        self.assertEqual(self.api.launches, [])

    def test_campaign_cutoff_crossed_during_preflight_blocks_launch(self):
        self.c["approval_expires_at"] = worker.CAMPAIGN_END_UTC
        state = self.store.load()
        state["approved_expiry"] = worker.CAMPAIGN_END_UTC
        self.store.save(state)
        self.clock = worker.CAMPAIGN_END_UTC - 60
        def cross_cutoff(_):
            self.clock = worker.CAMPAIGN_END_UTC
        self.api.preflight = Mock(side_effect=cross_cutoff)
        self.assertEqual(self.w.tick(self.c), "halted")
        self.assertEqual(self.store.load()["reason"], "campaign_ended")
        self.assertEqual(self.api.launches, [])
        self.assertTrue((self.store.directory / "STOP").exists())

    def test_campaign_cutoff_blocks_manual_reapproval(self):
        self.clock = worker.CAMPAIGN_END_UTC
        self.c["approval_expires_at"] = self.clock + 1200
        with self.assertRaisesRegex(worker.GateError, "campaign_ended"):
            self.w.reapprove(self.c)
        self.assertEqual(self.api.launches, [])

    def test_reapproval_requires_explicit_command_and_preserves_pending(self):
        self.api.error = worker.ApiError("uncertain")
        self.api.matches = []
        self.w.tick(self.c)
        previous = self.store.load()
        self.clock = 10000
        self.assertEqual(self.w.tick(self.c), "approval_expired")
        self.c["approval_expires_at"] = 15000
        self.assertEqual(self.w.tick(self.c), "approval_expired")
        self.w.reapprove(self.c)
        s = self.store.load()
        self.assertEqual((s["token"], s["phase"], s["requested_at"]),
                         (previous["token"], "pending", previous["requested_at"]))
        self.api.matches = [("fake-instance", "RUNNING")]
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "provisioned")
        self.assertEqual(len(self.api.launches), 1)

    def test_reapproval_does_not_accept_changed_gate(self):
        self.c["approval_expires_at"] = 15000
        self.c["free_image_confirmed"] = False
        with self.assertRaises(worker.GateError):
            self.w.reapprove(self.c)
        self.assertEqual(self.store.load()["approved_expiry"], 10000)

    def test_legacy_state_requires_explicit_reapproval(self):
        state = self.store.load()
        del state["approved_expiry"]
        self.store.save(state)
        self.assertEqual(self.w.tick(self.c), "approval_expired")
        self.assertEqual(self.api.launches, [])
        self.w.reapprove(self.c)
        self.assertEqual(self.store.load()["approved_expiry"], 10000)

    def test_tracking_expiry_reapproval_never_relaunches(self):
        self.api.matches = [("fake-instance", "PROVISIONING")]
        self.w.tick(self.c)
        original = self.store.load()
        self.clock = 10000
        self.w.tick(self.c)
        self.c["approval_expires_at"] = 15000
        self.w.reapprove(self.c)
        self.assertEqual(self.store.load()["instance_id"], original["instance_id"])
        self.api.matches = [("fake-instance", "RUNNING")]
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "provisioned")
        self.assertEqual(len(self.api.launches), 1)

    def test_dry_run_does_not_halt_unresolved_request(self):
        self.api.error = worker.ApiError("uncertain")
        self.w.tick(self.c)
        before = self.store.load()
        self.c["mode"] = "dry-run"
        self.assertEqual(self.w.tick(self.c), "pending_requires_reconciliation")
        after = self.store.load()
        self.assertEqual((after["mode"], after["phase"], after["token"]),
                         (before["mode"], before["phase"], before["token"]))
        self.assertFalse((self.store.directory / "STOP").exists())

    def test_disabled_no_api_even_with_placeholder_config(self):
        self.c = {"mode": "DISABLED"}
        self.assertEqual(self.w.tick(self.c), "DISABLED")
        self.assertEqual(self.api.capacity_calls, 0)

    def test_dry_run_validates_even_unavailable_and_never_writes_oci(self):
        self.c["mode"] = "dry-run"
        self.api.status = "OUT_OF_HOST_CAPACITY"
        self.api.preflight = Mock()
        self.assertEqual(self.w.tick(self.c), "dry-run")
        self.api.preflight.assert_called_once()
        self.assertEqual(self.api.launches, [])

    def test_lost_response_restart_reconciles_without_second_launch(self):
        self.api.error = worker.ApiError("uncertain")
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["phase"], "pending")
        self.clock += 60
        restarted = worker.Worker(self.api, worker.Store(self.temp.name), self.notify,
                                  now=lambda: self.clock, host="host")
        self.assertEqual(restarted.tick(self.c), "provisioned")
        self.assertEqual(len(self.api.launches), 1)

    def test_crash_between_remote_acceptance_and_state_commit(self):
        class Crash(BaseException):
            pass
        self.api.error = Crash()
        with self.assertRaises(Crash):
            self.w.tick(self.c)
        self.api.error = None
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "provisioned")
        self.assertEqual(len(self.api.launches), 1)

    def test_timeout_absent_result_never_relaunches_even_after_token_expiry(self):
        self.api.error = worker.ApiError("uncertain")
        self.api.matches = []
        self.w.tick(self.c)
        token = self.store.load()["token"]
        self.clock += 60
        self.w.tick(self.c)
        self.clock += 900
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "halted")
        self.clock += 86400
        self.w.tick(self.c)
        self.assertEqual(self.api.launches, [token])

    def test_429_after_launch_keeps_pending_and_only_reconciles(self):
        self.api.error = worker.ApiError("throttled")
        self.api.matches = []
        self.w.tick(self.c)
        s = self.store.load()
        self.assertEqual(s["phase"], "pending")
        self.assertEqual(s["next_at"], 1060)
        self.assertEqual(self.api.launches, [s["token"]])
        self.assertEqual(self.w.tick(self.c), "backoff")
        self.clock += 60
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["next_at"], 1120)
        self.assertEqual(len(self.api.launches), 1)
        self.clock += 60
        self.api.matches = [("fake-instance", "RUNNING")]
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "provisioned")
        self.assertEqual(len(self.api.launches), 1)

    def test_429_unresolved_never_relaunches_after_timeout(self):
        self.api.error = worker.ApiError("throttled")
        self.api.matches = []
        self.w.tick(self.c)
        token = self.store.load()["token"]
        self.clock += 901
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "halted")
        self.assertEqual(self.api.launches, [token])

    def test_5xx_reconciles_and_retry_budget_is_bounded(self):
        self.api.error = worker.ApiError("transient")
        self.w.tick(self.c)
        self.clock += 60
        self.api.reconcile = Mock(side_effect=worker.ApiError("transient"))
        for _ in range(4):
            self.w.tick(self.c)
            self.clock = self.store.load()["next_at"]
        self.assertEqual(self.store.load()["mode"], "halted")
        self.assertEqual(len(self.api.launches), 1)

    def test_capacity_rejection_returns_to_polling(self):
        self.api.error = worker.ApiError("capacity")
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["phase"], "ready")
        self.clock += 60
        self.api.error = None
        self.w.tick(self.c)
        self.assertEqual(len(set(self.api.launches)), 1)
        self.assertEqual(self.store.load()["mode"], "provisioned")

    def test_permission_preflight_fatal_sticky(self):
        self.api.preflight_error = worker.ApiError("fatal")
        self.w.tick(self.c)
        self.api.preflight_error = None
        self.w.tick(self.c)
        self.assertEqual(self.api.launches, [])
        self.notify.assert_called_once_with("halted")

    def test_wrong_target_host_expiry_and_cost_configuration_block(self):
        for key, value in (("region", "us-ashburn-1"), ("shape", "VM.Standard.E5.Flex"),
                           ("subnet_id", "invalid"), ("assign_public_ip", True),
                           ("free_storage_gb", 300), ("always_free_confirmed", False),
                           ("executor_machine_sha256", "other-host"), ("approval_expires_at", 999),
                           ("free_image_confirmed", False), ("image_architecture", "x86_64")):
            with self.subTest(key=key):
                c = {**config(), key: value}
                with self.assertRaises(worker.GateError):
                    worker.validate_config(c, 1000, "host")

    def test_configuration_change_after_initialization_halts(self):
        self.c["image_id"] = "ocid1.image.changed"
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["reason"], "config_changed")
        self.assertEqual(self.api.launches, [])

    def test_no_state_no_new_identity(self):
        self.store.path.unlink()
        with self.assertRaises(FileNotFoundError):
            self.w.tick(self.c)
        self.assertEqual(self.api.launches, [])

    def test_discord_failure_cannot_launch_another_vm(self):
        self.notify.side_effect = RuntimeError("sensitive webhook not logged")
        with patch("sys.stderr") as err:
            self.w.tick(self.c)
            self.w.tick(self.c)
        self.assertNotIn("sensitive", str(err.write.call_args_list))
        self.assertEqual(self.store.load()["mode"], "provisioned")
        self.assertEqual(len(self.api.launches), 1)

    def test_provisioning_then_running_and_duplicate_result(self):
        self.api.matches = [("fake-instance", "PROVISIONING")]
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "enabled")
        self.api.matches = [("fake-instance", "RUNNING")]
        self.w.tick(self.c)
        self.assertEqual(len(self.api.launches), 1)

    def test_multiple_matches_or_terminal_vm_halts(self):
        self.api.matches = [("fake-instance", "TERMINATED")]
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["mode"], "halted")

    def test_duplicate_matches_halt_without_another_launch(self):
        self.api.matches = [("fake-instance", "RUNNING"), ("duplicate", "RUNNING")]
        self.w.tick(self.c)
        self.assertEqual(self.store.load()["reason"], "duplicate_instances")
        self.w.tick(self.c)
        self.assertEqual(len(self.api.launches), 1)

    def test_health_alert_once_recovery_and_no_oci(self):
        path = self.store.directory / "config.json"
        path.write_text(json.dumps(self.c))
        s = self.store.load()
        s["initialized_at"] = 1
        self.store.save(s)
        args = ["worker", "--config", str(path), "--state-dir", self.temp.name, "--health"]
        with patch.object(sys, "argv", args), patch("oci_a1_provisioner.time.time", return_value=1000), \
                patch("oci_a1_provisioner_api.notify_discord") as notify:
            self.assertEqual(worker.main(), 0)
            self.assertEqual(worker.main(), 0)
            notify.assert_called_once_with("stale")
            s = self.store.load()
            s["last_ok"] = 999
            self.store.save(s)
            worker.main()
            self.assertNotIn("notified_stale", self.store.load())
        self.assertEqual(self.api.capacity_calls, 0)

    def test_overlapping_process_cannot_acquire_lock(self):
        module_dir = str(Path(worker.__file__).parent)
        code = "import sys; sys.path.insert(0, sys.argv[1]); from oci_a1_provisioner import Store\nwith Store(sys.argv[2]).lock() as acquired: print(acquired)"
        with self.store.lock() as acquired:
            self.assertTrue(acquired)
            result = subprocess.run([sys.executable, "-c", code, module_dir, self.temp.name],
                                    capture_output=True, text=True, timeout=10, check=True)
            self.assertEqual(result.stdout.strip(), "False")
            self.assertEqual(self.w.tick(self.c), "locked")


def response(data, next_page=None):
    return NS(data=data, headers={"opc-next-page": next_page} if next_page else {})


class AdapterTests(unittest.TestCase):
    """Real pinned SDK models, with all network clients replaced by mocks."""
    @classmethod
    def setUpClass(cls):
        try:
            import oci
        except ImportError:
            raise unittest.SkipTest("SDK model contracts run in the dedicated pinned-SDK job")
        cls.sdk = oci

    def setUp(self):
        self.c = config()
        self.clients = {name: Mock() for name in ("identity", "compute", "network", "block", "limits")}
        self.api = OciApi(self.clients, self.sdk)
        identity, compute, block, limits = (self.clients[x] for x in ("identity", "compute", "block", "limits"))
        identity.list_region_subscriptions.return_value = response([NS(region_name=worker.REGION, is_home_region=True)])
        identity.list_compartments.return_value = response([NS(id=self.c["compartment_id"], lifecycle_state="ACTIVE")])
        identity.get_compartment.return_value = response(NS(lifecycle_state="ACTIVE"))
        identity.list_availability_domains.return_value = response([NS(name=worker.AD)])
        compute.list_instances.return_value = response([])
        block.list_volumes.return_value = response([])
        block.list_boot_volumes.return_value = response([])
        limits.get_resource_availability.return_value = response(self.sdk.limits.models.ResourceAvailability(available=200, used=0))
        compute.get_image.return_value = response(self.sdk.core.models.Image(
            lifecycle_state="AVAILABLE", operating_system="Canonical Ubuntu", size_in_mbs=47000))
        compute.get_image_shape_compatibility_entry.return_value = response(
            self.sdk.core.models.ImageShapeCompatibilityEntry(shape=worker.SHAPE))
        self.api.network_fingerprint = Mock(return_value=self.c["network_sha256"])

    def test_preflight_enumerates_tenancy_and_all_compartments(self):
        self.api.preflight(self.c)
        self.clients["compute"].list_instances.assert_any_call(compartment_id=self.c["tenancy_id"])
        self.clients["compute"].list_instances.assert_any_call(compartment_id=self.c["compartment_id"])
        self.assertEqual(self.clients["limits"].get_resource_availability.call_count, 7)
        from unittest.mock import call
        self.assertEqual(self.clients["limits"].get_resource_availability.call_args_list, [
            call("compute", "standard-a1-core-count", self.c["compartment_id"], availability_domain=worker.AD),
            call("compute", "standard-a1-core-count", self.c["tenancy_id"], availability_domain=worker.AD),
            call("compute", "standard-a1-core-count", self.c["compartment_id"], availability_domain=worker.AD),
            call("compute", "standard-a1-memory-count", self.c["compartment_id"], availability_domain=worker.AD),
            call("compute", "standard-a1-memory-count", self.c["tenancy_id"], availability_domain=worker.AD),
            call("compute", "standard-a1-memory-count", self.c["compartment_id"], availability_domain=worker.AD),
            call("block-storage", "total-storage-gb", self.c["compartment_id"], availability_domain=worker.AD),
        ])

    def test_existing_stopped_vm_different_name_blocks(self):
        self.clients["compute"].list_instances.return_value = response([
            NS(shape=worker.SHAPE, lifecycle_state="STOPPED", display_name="different", freeform_tags={})])
        with self.assertRaisesRegex(worker.GateError, "existing_instance"):
            self.api.preflight(self.c)

    def test_volume_from_another_compartment_counts(self):
        self.clients["block"].list_boot_volumes.side_effect = [response([NS(size_in_gbs=151, lifecycle_state="AVAILABLE")]), response([])]
        with self.assertRaisesRegex(worker.GateError, "free_storage_exceeded"):
            self.api.preflight(self.c)

    def test_quota_missing_or_insufficient_blocks(self):
        for value in (None, 0):
            with self.subTest(value=value):
                self.clients["limits"].get_resource_availability.return_value = response(
                    self.sdk.limits.models.ResourceAvailability(available=value))
                with self.assertRaisesRegex(worker.GateError, "quota_insufficient"):
                    self.api.preflight(self.c)

    def test_permission_error_and_non_home_region_block(self):
        self.clients["compute"].list_instances.side_effect = self.sdk.exceptions.ServiceError(403, "NotAuthorized", {}, "PRIVATE")
        with self.assertRaises(worker.ApiError) as caught:
            self.api.preflight(self.c)
        self.assertEqual(caught.exception.kind, "fatal")
        self.clients["identity"].list_region_subscriptions.return_value = response([])
        with self.assertRaisesRegex(worker.GateError, "not_home_region"):
            self.api.preflight(self.c)

    def test_limits_unsupported_or_unauthorized_fail_closed(self):
        for status in (403, 404):
            with self.subTest(status=status):
                self.clients["limits"].get_resource_availability.side_effect = self.sdk.exceptions.ServiceError(
                    status, "Unavailable", {}, "hidden")
                with self.assertRaises(worker.ApiError) as caught:
                    self.api.preflight(self.c)
                self.assertEqual(caught.exception.kind, "fatal")
                self.clients["compute"].launch_instance.assert_not_called()

    def test_tenancy_compute_usage_from_non_vm_consumer_blocks(self):
        self.clients["limits"].get_resource_availability.return_value.data.used = 1
        with self.assertRaisesRegex(worker.GateError, "free_compute_exceeded"):
            self.api.preflight(self.c)

    def test_other_ad_compute_usage_blocks_free_budget(self):
        self.clients["identity"].list_availability_domains.return_value = response(
            [NS(name=worker.AD), NS(name="other-ad")])
        def availability(service, name, compartment, **kwargs):
            used = 1 if compartment == self.c["tenancy_id"] and kwargs["availability_domain"] == "other-ad" else 0
            return response(self.sdk.limits.models.ResourceAvailability(available=200, used=used))
        self.clients["limits"].get_resource_availability.side_effect = availability
        with self.assertRaisesRegex(worker.GateError, "free_compute_exceeded"):
            self.api.preflight(self.c)

    def test_child_compartment_compute_usage_blocks_free_budget(self):
        def availability(service, name, compartment, **kwargs):
            used = 1 if compartment == self.c["compartment_id"] else 0
            return response(self.sdk.limits.models.ResourceAvailability(available=200, used=used))
        self.clients["limits"].get_resource_availability.side_effect = availability
        with self.assertRaisesRegex(worker.GateError, "free_compute_exceeded"):
            self.api.preflight(self.c)

    def test_paid_image_network_drift_block(self):
        self.clients["compute"].get_image.return_value.data.operating_system = "Windows"
        with self.assertRaisesRegex(worker.GateError, "image_not_free"):
            self.api.preflight(self.c)
        self.clients["compute"].get_image.return_value.data.operating_system = "Canonical Ubuntu"
        self.api.network_fingerprint.return_value = "f" * 64
        with self.assertRaisesRegex(worker.GateError, "network_drift"):
            self.api.preflight(self.c)

    def test_image_constraints_real_sdk_properties(self):
        m = self.sdk.core.models
        self.clients["compute"].get_image_shape_compatibility_entry.return_value = response(
            m.ImageShapeCompatibilityEntry(shape=worker.SHAPE,
                ocpu_constraints=m.ImageOcpuConstraints(min=1, max=4),
                memory_constraints=m.ImageMemoryConstraints(min_in_gbs=1, max_in_gbs=24)))
        self.api.preflight(self.c)

    def test_network_snapshot_real_sdk_models_and_wrong_vcn(self):
        m = self.sdk.core.models
        network = self.clients["network"]
        network.get_subnet.return_value = response(m.Subnet(
            lifecycle_state="AVAILABLE", vcn_id=self.c["vcn_id"], compartment_id=self.c["compartment_id"],
            security_list_ids=self.c["security_list_ids"], route_table_id="route"))
        network.get_route_table.return_value = response(m.RouteTable(vcn_id=self.c["vcn_id"], route_rules=[]))
        network.get_security_list.return_value = response(m.SecurityList(vcn_id=self.c["vcn_id"], ingress_security_rules=[], egress_security_rules=[]))
        actual = OciApi.network_fingerprint(self.api, self.c)
        self.assertEqual(len(actual), 64)
        network.get_subnet.return_value.data.vcn_id = "other-vcn"
        with self.assertRaisesRegex(worker.GateError, "subnet_not_approved"):
            OciApi.network_fingerprint(self.api, self.c)

    def test_reconcile_uses_request_tags_not_name(self):
        m = self.sdk.core.models
        vm = m.Instance(id="remote", display_name="renamed", lifecycle_state="RUNNING",
            compartment_id=self.c["compartment_id"], availability_domain=worker.AD, shape=worker.SHAPE,
            image_id=self.c["image_id"], shape_config=m.InstanceShapeConfig(ocpus=2, memory_in_gbs=12),
            freeform_tags={TAG: worker.digest("token"), DEPLOYMENT: self.c["deployment_id"]})
        self.api.instances = Mock(return_value=[vm])
        self.assertEqual(self.api.reconcile(self.c, "token", None), [("remote", "RUNNING")])
        vm.shape_config.ocpus = 4
        with self.assertRaisesRegex(worker.GateError, "tracked_instance_mismatch"):
            self.api.reconcile(self.c, "token", None)

    def test_launch_real_sdk_payload_and_no_hidden_retries(self):
        self.clients["compute"].launch_instance.return_value = response(NS(id="fake"))
        self.api.launch(self.c, "dedicated-token")
        call = self.clients["compute"].launch_instance.call_args
        details = call.args[0]
        self.assertEqual(call.kwargs, {"opc_retry_token": "dedicated-token"})
        self.assertEqual(details.shape, worker.SHAPE)
        self.assertEqual(details.source_details.boot_volume_size_in_gbs, 50)
        self.assertEqual(details.source_details.boot_volume_vpus_per_gb, 10)
        self.assertIs(details.create_vnic_details.assign_public_ip, False)
        self.assertIs(details.create_vnic_details.assign_ipv6_ip, False)
        self.c["mode"] = "dry-run"
        with self.assertRaises(worker.GateError):
            self.api.launch(self.c, "dedicated-token")
        self.assertEqual(self.clients["compute"].launch_instance.call_count, 1)

    def test_pagination_and_capacity_report_reuse(self):
        self.clients["compute"].list_instances.side_effect = [response([1], "second"), response([2])]
        self.assertEqual(self.api.listing("compute", "list_instances", compartment_id="fake"), [1, 2])
        m = self.sdk.core.models
        self.clients["compute"].create_compute_capacity_report.return_value = response(
            m.ComputeCapacityReport(shape_availabilities=[m.CapacityReportShapeAvailability(
                instance_shape=worker.SHAPE, availability_status="AVAILABLE", available_count=1,
                instance_shape_config=m.CapacityReportInstanceShapeConfig(ocpus=2, memory_in_gbs=12))]))
        self.assertEqual(self.api.capacity(self.c), "AVAILABLE")
        details = self.clients["compute"].create_compute_capacity_report.call_args.args[0]
        self.assertEqual(details.compartment_id, self.c["tenancy_id"])
        self.assertNotEqual(details.compartment_id, self.c["compartment_id"])
        self.assertEqual(details.availability_domain, worker.AD)
        self.assertEqual(len(details.shape_availabilities), 1)
        self.assertEqual(details.shape_availabilities[0].instance_shape, worker.SHAPE)
        self.assertEqual(details.shape_availabilities[0].instance_shape_config.ocpus, 2)
        self.assertEqual(details.shape_availabilities[0].instance_shape_config.memory_in_gbs, 12)

    def test_exception_classification_does_not_expose_payload(self):
        for status, code, message, expected in ((500, "InternalError", "Out of host capacity.", "capacity"),
                (500, "InternalError", "sensitive", "transient"), (429, "TooManyRequests", "private", "throttled"),
                (401, "NotAuthenticated", "private", "fatal")):
            with self.subTest(status=status, message=message):
                self.clients["compute"].launch_instance.side_effect = self.sdk.exceptions.ServiceError(status, code, {}, message)
                with self.assertRaises(worker.ApiError) as caught:
                    self.api.launch(self.c, "token")
                self.assertEqual(caught.exception.kind, expected)
                self.assertNotIn(message, str(caught.exception))


if __name__ == "__main__":
    unittest.main()
