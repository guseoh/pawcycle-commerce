"""Prove scale-mixed.js write-path verdicts against a loopback stub (about 40 seconds, k6 required).

  python infra/performance/k6/selftest/scale-mixed-selftest.py

Modes of the stub's POST /api/checkout:
  ok         -> valid order JSON: k6 exit 0, write error rate 0, confirm called
  bad-json   -> HTTP 200 with a body that is not JSON: k6 exit 99, the failure is recorded as a
                POST /api/checkout status error, confirm is never called, and no iteration ends
                by exception (started == finished)

Only 127.0.0.1 is used. No application, database, or remote target is touched.
"""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading

HERE = Path(__file__).resolve().parent
SCRIPT = HERE.parent / 'scale-mixed.js'
VUS = {'browse': 2, 'member': 2, 'write': 4, 'login': 2}
PRODUCTS = list(range(1, 101))
SKUS = list(range(1, 21))


class Stub(BaseHTTPRequestHandler):
    mode = 'ok'
    confirms = 0
    lock = threading.Lock()

    def log_message(self, *args):
        pass

    def reply(self, status, body=None, raw=None):
        data = raw if raw is not None else (b'' if body is None else json.dumps(body).encode())
        self.send_response(status)
        if data:
            self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = self.path.split('?')[0]
        if path == '/api/products':
            self.reply(200, {'items': [{'productId': p} for p in PRODUCTS[:20]], 'page': 0, 'size': 20,
                             'totalElements': 100, 'totalPages': 5})
        elif path.startswith('/api/products/'):
            self.reply(200, {'productId': int(path.rsplit('/', 1)[1]), 'skus': []})
        elif path == '/api/cart':
            self.reply(200, {'items': [], 'version': 1})
        elif path == '/api/auth/csrf':
            self.reply(200, {'token': 'stub-token'})
        else:   # /api/orders, /api/subscriptions
            self.reply(200, [])

    def do_POST(self):
        self.rfile.read(int(self.headers.get('Content-Length') or 0))
        path = self.path.split('?')[0]
        if path == '/api/cart/items':
            self.reply(204)
        elif path == '/api/checkout':
            if Stub.mode == 'bad-json':
                self.reply(200, raw=b'<html>not json</html>')
            else:
                self.reply(200, {'orderId': 1, 'providerOrderId': 'stub-order', 'amount': 1000})
        elif path == '/api/payments/toss/confirm':
            with Stub.lock:
                Stub.confirms += 1
            self.reply(200, {'paymentId': 1, 'orderId': 1, 'status': 'SUCCEEDED'})
        elif path == '/api/auth/login':
            self.reply(200, {})
        else:
            self.reply(404)


def run(mode, port, files):
    Stub.mode, Stub.confirms = mode, 0
    summary = Path(files['dir']) / f'{mode}.json'
    env = dict(os.environ, SCALE_BASE_URL=f'http://127.0.0.1:{port}', SCALE_PHASE='steady', SCALE_RPS='4',
               SCALE_DURATION_SECONDS='15', SCALE_COHORT='100', SCALE_COHORT_PASSWORD='stub',
               SCALE_SESSIONS_FILE=files['sessions'], SCALE_CATALOG_FILE=files['catalog'],
               SCALE_VUS=json.dumps(VUS), SCALE_SUMMARY=str(summary))
    result = subprocess.run(['k6', 'run', '--quiet', '--no-color', str(SCRIPT)], env=env, capture_output=True,
                            text=True, encoding='utf-8')
    return result.returncode, json.loads(summary.read_text(encoding='utf-8')), Stub.confirms


def main():
    server = ThreadingHTTPServer(('127.0.0.1', 0), Stub)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    port = server.server_address[1]
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        files = {'dir': tmp, 'sessions': str(Path(tmp) / 'sessions.json'), 'catalog': str(Path(tmp) / 'catalog.json')}
        sessions = [{'memberId': i, 'sessionId': f'stub-{i}', 'csrf': 'stub-token'} for i in range(1, sum(VUS.values()) + 1)]
        Path(files['sessions']).write_text(json.dumps(sessions), encoding='utf-8')
        Path(files['catalog']).write_text(json.dumps({'productIds': PRODUCTS, 'skuIds': SKUS}), encoding='utf-8')

        code, summary, confirms = run('ok', port, files)
        write = summary['bundles']['write']
        print(json.dumps({'mode': 'ok', 'exit': code, 'write_error_rate': write['error_rate'], 'confirms': confirms}))
        if code != 0 or write['error_rate'] != 0 or confirms == 0:
            failures.append('ok mode must pass and call confirm')

        code, summary, confirms = run('bad-json', port, files)
        write = summary['bundles']['write']
        checkout_errors = summary['status_errors_by_step']['POST /api/checkout']
        print(json.dumps({'mode': 'bad-json', 'exit': code, 'write_error_rate': write['error_rate'],
                          'checkout_errors': checkout_errors, 'confirms': confirms,
                          'interrupted': summary['interrupted_iterations']}))
        if code != 99:
            failures.append('bad JSON must fail the run (k6 exit 99)')
        if not write['error_rate'] or checkout_errors == 0:
            failures.append('bad JSON must be recorded as a POST /api/checkout contract failure')
        if confirms != 0:
            failures.append('confirm must not be called after an unreadable checkout response')
        if summary['interrupted_iterations'] != 0:
            failures.append('bad JSON must not end the iteration by exception')
    server.shutdown()
    print(json.dumps({'selftest': 'scale-mixed', 'failed': failures, 'verdict': 'FAIL' if failures else 'PASS'}))
    sys.exit(1 if failures else 0)


if __name__ == '__main__':
    main()
