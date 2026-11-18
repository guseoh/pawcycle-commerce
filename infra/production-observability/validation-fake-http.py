"""Validation-only HTTP fakes for validate-observability.sh (ARCH-016). Never used in Production.

discord: accepts Alertmanager Discord webhook POSTs on :8080 and prints one "DELIVERED <title>" line per embed.
metrics: answers GET /actuator/prometheus on :9464 like metrics-proxy, 404 for any other path.
"""

import http.server
import json
import sys

MODE = sys.argv[1]


class DiscordHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        for embed in json.loads(body).get("embeds", []):
            print("DELIVERED " + embed.get("title", ""), flush=True)
        self.send_response(204)
        self.end_headers()

    def log_message(self, *args):
        pass


class MetricsHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/actuator/prometheus":
            self.send_response(404)
            self.end_headers()
            return
        body = b"# TYPE pawcycle_validation_up gauge\npawcycle_validation_up 1\n"
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if MODE == "discord":
    http.server.ThreadingHTTPServer(("0.0.0.0", 8080), DiscordHandler).serve_forever()
elif MODE == "metrics":
    http.server.ThreadingHTTPServer(("0.0.0.0", 9464), MetricsHandler).serve_forever()
else:
    raise SystemExit(f"unknown mode: {MODE}")
