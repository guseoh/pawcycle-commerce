"""Loopback-only stub target for k6 harness self-tests. Not a product server.

Modes:
  ok        valid product list / detail / HTML responses
  bad-body  HTTP 200 with a body that violates the response contract
  bad-detail  valid product list (setup still works), contract-violating detail body
  slow      valid responses after a fixed delay (forces arrival-rate drops)
"""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LIST = {'items': [{'productId': 1, 'name': 'stub'}], 'page': 0, 'size': 20, 'totalElements': 1, 'totalPages': 1}
DETAIL = {'productId': 1, 'name': 'stub', 'skus': []}
HTML = '<!doctype html><html><body>stub</body></html>'


def handler(mode, delay):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'

        def log_message(self, *_):
            pass

        def send(self, status, body, content_type):
            payload = body.encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def do_GET(self):
            if mode == 'slow':
                time.sleep(delay)
            if mode == 'bad-body' or (mode == 'bad-detail' and self.path.startswith('/api/products/')):
                self.send(200, '{"unexpected":true}', 'application/json')
            elif self.path.startswith('/api/products/'):
                self.send(200, json.dumps(DETAIL), 'application/json')
            elif self.path.startswith('/api/products'):
                self.send(200, json.dumps(LIST), 'application/json')
            elif self.path.startswith('/products'):
                self.send(200, HTML, 'text/html; charset=utf-8')
            else:
                self.send(404, '{}', 'application/json')

    return Handler


class QuietServer(ThreadingHTTPServer):
    def handle_error(self, request, client_address):
        # k6 closes keep-alive sockets abruptly at gracefulStop; that is expected.
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mode', choices=['ok', 'bad-body', 'bad-detail', 'slow'], required=True)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--delay', type=float, default=2.0)
    args = parser.parse_args()
    server = QuietServer(('127.0.0.1', args.port), handler(args.mode, args.delay))
    server.daemon_threads = True
    server.serve_forever()


if __name__ == '__main__':
    main()
