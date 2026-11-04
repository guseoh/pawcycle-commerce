#!/usr/bin/env python3
"""Notify an external dead-man check only after a real A1 monitor job succeeds.

The URL is a credential. Never log or store its value outside Actions Secrets.
No network call occurs when the optional secret is absent.
"""
from __future__ import annotations

import os
import re
from urllib.request import HTTPRedirectHandler, Request, build_opener

PING_URL_RE = re.compile(
    r"https://hc-ping\.com/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}",
    re.IGNORECASE,
)


class BlockRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def report_success(url: str, opener=None) -> None:
    if not PING_URL_RE.fullmatch(url):
        raise RuntimeError("Invalid OCI heartbeat URL format")
    opener = opener if opener is not None else build_opener(BlockRedirect)
    request = Request(url, method="POST", headers={"User-Agent": "pawcycle-oci-a1-capacity-watch"})
    try:
        with opener.open(request, timeout=8) as response:
            if response.status != 200 or response.read(64).strip() != b"OK":
                raise RuntimeError("OCI heartbeat endpoint rejected success")
    except Exception as exc:
        # Never forward a URL or exception text into Actions logs, even for
        # unexpected opener/runtime exceptions outside urllib's normal types.
        if isinstance(exc, RuntimeError) and str(exc) == "OCI heartbeat endpoint rejected success":
            raise
        raise RuntimeError("OCI heartbeat transport failed") from None


def main() -> None:
    url = os.environ.get("HEALTHCHECKS_OCI_PING_URL", "").strip()
    if not url:
        # Backwards compatible while the external account and Secrets await approval.
        print("Independent OCI heartbeat not configured; continuous coverage not verified.")
        return
    report_success(url)
    print("Independent OCI check heartbeat accepted after successful monitor completion.")


if __name__ == "__main__":
    main()
