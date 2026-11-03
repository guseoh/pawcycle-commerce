"""OCI adapter. Only launch() can create a VM; all clients disable SDK retries."""
import os
import json
from urllib.request import Request, urlopen

from oci_a1_capacity_monitor import REGION, AD, SHAPE, parse_report
from oci_a1_provisioner import ApiError, require, digest

TAG = "pawcycle-a1-request"
DEPLOYMENT = "pawcycle-a1-deployment"


class OciApi:
    def __init__(self, clients=None, sdk=None):
        self.clients = clients
        self.sdk = sdk

    def connect(self, c):
        if self.clients is not None:
            return
        import oci
        config = oci.config.from_file(os.environ.get("OCI_CONFIG_FILE", "/etc/pawcycle-a1/oci-config"))
        oci.config.validate_config(config)
        require(config["region"] == REGION and config["tenancy"] == c["tenancy_id"], "credential_target_mismatch")
        self.sdk = oci
        kwargs = dict(timeout=(10, 30), retry_strategy=oci.retry.NoneRetryStrategy())
        self.clients = {
            "compute": oci.core.ComputeClient(config, **kwargs),
            "identity": oci.identity.IdentityClient(config, **kwargs),
            "network": oci.core.VirtualNetworkClient(config, **kwargs),
            "block": oci.core.BlockstorageClient(config, **kwargs),
            "limits": oci.limits.LimitsClient(config, **kwargs),
        }

    def call(self, client, method, *args, **kwargs):
        try:
            return getattr(self.clients[client], method)(*args, **kwargs)
        except Exception as exc:
            status, code = getattr(exc, "status", None), getattr(exc, "code", "")
            # OCI uses InternalError/500 with this exact message for capacity.
            message = str(getattr(exc, "message", "")).strip().lower().rstrip(".")
            if code == "OutOfHostCapacity" or (status == 500 and message == "out of host capacity"):
                kind = "capacity"
            elif status == 429:
                kind = "throttled"
            elif isinstance(status, int) and 500 <= status < 600:
                kind = "transient"
            elif isinstance(status, int) and 400 <= status < 500:
                kind = "fatal"
            else:
                kind = "uncertain"
            raise ApiError(kind) from None

    def listing(self, client, method, **kwargs):
        result, page, seen = [], None, set()
        while True:
            response = self.call(client, method, **kwargs, **({"page": page} if page else {}))
            result.extend(response.data)
            page = response.headers.get("opc-next-page")
            if not page:
                return result
            require(page not in seen, "pagination_cycle")
            seen.add(page)

    def compartments(self, c):
        self.connect(c)
        subscriptions = self.listing("identity", "list_region_subscriptions", tenancy_id=c["tenancy_id"])
        require(any(x.region_name == REGION and x.is_home_region for x in subscriptions), "not_home_region")
        entries = self.listing("identity", "list_compartments", compartment_id=c["tenancy_id"],
                               compartment_id_in_subtree=True, access_level="ANY")
        ids = [c["tenancy_id"]] + [x.id for x in entries if x.lifecycle_state != "DELETED"]
        require(c["compartment_id"] in ids, "compartment_outside_tenancy")
        target = self.call("identity", "get_compartment", c["compartment_id"]).data
        require(target.lifecycle_state == "ACTIVE", "compartment_not_active")
        return ids

    def instances(self, c):
        # Never use ACCESSIBLE filtering: every compartment must be readable.
        return [vm for compartment in self.compartments(c)
                for vm in self.listing("compute", "list_instances", compartment_id=compartment)]

    def capacity(self, c):
        self.connect(c)
        models = self.sdk.core.models
        details = models.CreateComputeCapacityReportDetails(
            compartment_id=c["compartment_id"], availability_domain=AD,
            shape_availabilities=[models.CreateCapacityReportShapeAvailabilityDetails(
                instance_shape=SHAPE, instance_shape_config=models.CapacityReportInstanceShapeConfig(
                    ocpus=2, memory_in_gbs=12))])
        return parse_report(self.call("compute", "create_compute_capacity_report", details).data)[0]

    def preflight(self, c):
        self.connect(c)
        compartments = self.compartments(c)
        all_instances = [vm for comp in compartments
                         for vm in self.listing("compute", "list_instances", compartment_id=comp)]
        # Deliberately conservative: any A1, including stopped/terminating, blocks.
        require(not any(vm.lifecycle_state != "TERMINATED" and
                        (vm.shape == SHAPE or vm.display_name == "pawcycle-arm-01" or
                         (vm.freeform_tags or {}).get(DEPLOYMENT) == c["deployment_id"])
                        for vm in all_instances), "existing_instance")
        ads = self.listing("identity", "list_availability_domains", compartment_id=c["tenancy_id"])
        require(AD in [x.name for x in ads], "ad_not_in_tenancy")
        storage = 0
        for comp in compartments:
            volumes = self.listing("block", "list_volumes", compartment_id=comp)
            for ad in ads:
                volumes += self.listing("block", "list_boot_volumes", compartment_id=comp,
                                        availability_domain=ad.name)
            for volume in volumes:
                if volume.lifecycle_state != "TERMINATED":
                    require(isinstance(volume.size_in_gbs, int) and volume.size_in_gbs > 0, "unknown_volume_size")
                    storage += volume.size_in_gbs
        require(storage + 50 <= c["free_storage_gb"], "free_storage_exceeded")
        # Availability includes service limits AND compartment quota hierarchy.
        for service, name, needed, ad in (
                ("compute", "standard-a1-core-count", 2, AD),
                ("compute", "standard-a1-memory-count", 12, AD),
                ("block-storage", "total-storage-gb", 50, AD)):
            available = self.call("limits", "get_resource_availability", service, name,
                                  c["compartment_id"], **({"availability_domain": ad} if ad else {})).data
            values = [available.available]
            if available.fractional_availability is not None:
                values.append(available.fractional_availability)
            require(all(isinstance(x, (int, float)) and x >= needed for x in values), "quota_insufficient")
            if service == "compute":
                budget = c["free_memory_gb"] if "memory" in name else c["free_ocpus"]
                # Also cover A1 consumers such as Container Instances, not just VMs.
                usage = 0
                for domain in ads:
                    total = self.call("limits", "get_resource_availability", service, name,
                                      c["tenancy_id"], availability_domain=domain.name).data
                    used = total.fractional_usage if total.fractional_usage is not None else total.used
                    require(isinstance(used, (int, float)) and used >= 0, "free_compute_exceeded_or_unknown")
                    usage += used
                require(usage + needed <= budget, "free_compute_exceeded_or_unknown")
        image = self.call("compute", "get_image", c["image_id"]).data
        require(image.lifecycle_state == "AVAILABLE" and image.compartment_id is None
                and image.operating_system in ("Canonical Ubuntu", "Oracle Linux")
                and isinstance(image.size_in_mbs, int) and 0 < image.size_in_mbs <= 50 * 1024,
                "image_not_free_platform_or_too_large")
        compat = self.call("compute", "get_image_shape_compatibility_entry", c["image_id"], SHAPE).data
        require(compat.shape == SHAPE, "image_not_arm_compatible")
        for constraints, value, low, high in ((compat.ocpu_constraints, 2, "min", "max"),
                                              (compat.memory_constraints, 12, "min_in_gbs", "max_in_gbs")):
            if constraints is not None:
                require(getattr(constraints, low) <= value <= getattr(constraints, high), "image_shape_size_incompatible")
        network_hash = self.network_fingerprint(c)
        # Dry-run prints only a hash, never raw rules, resource IDs or credentials.
        if c["mode"] == "dry-run":
            print("network_sha256=" + network_hash)
        require(network_hash == c["network_sha256"], "network_drift")

    def network_fingerprint(self, c):
        subnet = self.call("network", "get_subnet", c["subnet_id"]).data
        require(subnet.lifecycle_state == "AVAILABLE" and subnet.vcn_id == c["vcn_id"]
                and subnet.compartment_id == c["compartment_id"]
                and subnet.availability_domain in (None, AD)
                and sorted(subnet.security_list_ids) == sorted(c["security_list_ids"]), "subnet_not_approved")
        snapshot = {"subnet": self.sdk.util.to_dict(subnet), "security_lists": [], "nsgs": []}
        route = self.call("network", "get_route_table", subnet.route_table_id).data
        require(route.vcn_id == c["vcn_id"], "route_not_approved")
        snapshot["route_table"] = self.sdk.util.to_dict(route)
        for identifier in sorted(c["security_list_ids"]):
            security = self.call("network", "get_security_list", identifier).data
            require(security.vcn_id == c["vcn_id"], "security_list_not_approved")
            snapshot["security_lists"].append(self.sdk.util.to_dict(security))
        for identifier in sorted(c["nsg_ids"]):
            group = self.call("network", "get_network_security_group", identifier).data
            require(group.vcn_id == c["vcn_id"] and group.lifecycle_state == "AVAILABLE", "nsg_not_approved")
            rules = self.listing("network", "list_network_security_group_security_rules", network_security_group_id=identifier)
            snapshot["nsgs"].append({"group": self.sdk.util.to_dict(group),
                                     "rules": sorted((self.sdk.util.to_dict(x) for x in rules), key=digest)})
        return digest(snapshot)

    def launch(self, c, token):
        self.connect(c)
        require(c["mode"] == "enabled", "write_not_enabled")
        m = self.sdk.core.models
        details = m.LaunchInstanceDetails(
            compartment_id=c["compartment_id"], availability_domain=AD, shape=SHAPE,
            display_name="pawcycle-arm-01",
            shape_config=m.LaunchInstanceShapeConfigDetails(ocpus=2, memory_in_gbs=12),
            source_details=m.InstanceSourceViaImageDetails(image_id=c["image_id"],
                boot_volume_size_in_gbs=50, boot_volume_vpus_per_gb=10),
            create_vnic_details=m.CreateVnicDetails(subnet_id=c["subnet_id"],
                assign_public_ip=False, assign_ipv6_ip=False, nsg_ids=c["nsg_ids"]),
            metadata={"ssh_authorized_keys": c["ssh_public_key"]},
            freeform_tags={TAG: digest(token), DEPLOYMENT: c["deployment_id"]})
        return self.call("compute", "launch_instance", details, opc_retry_token=token).data.id

    def reconcile(self, c, token, instance_id):
        all_instances = self.instances(c)
        matches = [x for x in all_instances if (x.freeform_tags or {}).get(TAG) == digest(token)]
        if instance_id:
            # Direct GET protects against list eventual consistency after a 2xx.
            known = self.call("compute", "get_instance", instance_id).data
            matches = [x for x in matches if x.id != known.id] + [known]
        for vm in matches:
            require(vm.compartment_id == c["compartment_id"] and vm.availability_domain == AD
                    and vm.shape == SHAPE and vm.shape_config.ocpus == 2
                    and vm.shape_config.memory_in_gbs == 12
                    and (vm.freeform_tags or {}).get(DEPLOYMENT) == c["deployment_id"]
                    and (vm.freeform_tags or {}).get(TAG) == digest(token)
                    and vm.image_id == c["image_id"], "tracked_instance_mismatch")
        return [(x.id, x.lifecycle_state) for x in matches]


def notify_discord(event):
    # An allowlisted event is the entire notification. No instance/SDK payloads.
    require(event in ("provisioned", "halted", "stale", "approval_expiring"), "invalid_notification")
    url = os.environ.get("DISCORD_WEBHOOK_URL", "")
    require(url.startswith("https://discord.com/api/webhooks/"), "invalid_webhook")
    payload = json.dumps({"username": "PawCycle A1 Provisioner", "allowed_mentions": {"parse": []},
                          "content": "OCI Tokyo A1 provisioner: " + event}).encode()
    request = Request(url, data=payload, headers={"Content-Type": "application/json"}, method="POST")
    with urlopen(request, timeout=15) as response:
        require(200 <= response.status < 300, "notification_failed")
