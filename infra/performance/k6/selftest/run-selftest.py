"""Prove the k6 verdict paths with deliberate failures against a loopback stub.

Default (about 20 seconds): lib/verdict.js self-test with three stub modes.
  ok        -> k6 exit 0, verdict passed
  bad-body  -> k6 exit 99, response contract error rate 1, verdict failed
  slow      -> k6 exit 99, dropped/not-completed iterations > 0, verdict failed

--real-cohort api-products|products-page|api-product-detail additionally runs the
real baseline cohort script unchanged (VUS=1, 30s warm-up + 2m) against the stub
in bad-body and ok modes. --real-capacity runs capacity-api-products.js at its
lowest approved stage (250 RPS, 30s + 2m) in bad-body mode. Both are opt-in
because they take minutes.

Only 127.0.0.1 is used. No application, database, or remote target is touched.
"""
import argparse
import json
import socket
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
K6_DIR = HERE.parent
REAL_SCRIPTS = {'api-products': 'api-products.js', 'products-page': 'products-page.js',
                'api-product-detail': 'api-product-detail.js'}


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def wait_ready(port):
    for _ in range(50):
        try:
            with socket.create_connection(('127.0.0.1', port), timeout=0.2):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError('stub did not start')


def run_case(mode, script, env_extra):
    port = free_port()
    stub = subprocess.Popen([sys.executable, '-I', str(HERE / 'stub_server.py'), '--mode', mode, '--port', str(port)])
    try:
        wait_ready(port)
        env = {**__import__('os').environ, 'BASE_URL': f'http://127.0.0.1:{port}', **env_extra}
        started = time.monotonic()
        result = subprocess.run(['k6', 'run', '--quiet', str(script)], env=env,
                                capture_output=True, text=True, encoding='utf-8')
        elapsed = time.monotonic() - started
    finally:
        stub.terminate()
        stub.wait(timeout=10)
    summary = None
    for line in reversed(result.stdout.splitlines()):
        if line.startswith('{'):
            summary = json.loads(line)
            break
    return {'mode': mode, 'script': script.name, 'exit': result.returncode,
            'seconds': round(elapsed, 1), 'summary': summary}


def expect(case, exit_code, predicate, label):
    ok = case['exit'] == exit_code and case['summary'] is not None and predicate(case['summary'])
    print(json.dumps({'case': label, 'pass': ok, **case}, ensure_ascii=False), flush=True)
    return ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--real-cohort', choices=sorted(REAL_SCRIPTS))
    parser.add_argument('--real-capacity', action='store_true')
    args = parser.parse_args()
    selftest = HERE / 'verdict-selftest.js'
    results = [
        expect(run_case('ok', selftest, {}), 0,
               lambda s: s['verdict']['passed'] and s['responseContractErrorRate'] == 0,
               'selftest-ok'),
        expect(run_case('bad-body', selftest, {}), 99,
               lambda s: not s['verdict']['passed'] and s['responseContractErrorRate'] == 1
               and any('response_contract' in item for item in s['verdict']['failedThresholds']),
               'selftest-bad-body'),
        expect(run_case('slow', selftest, {}), 99,
               lambda s: not s['verdict']['passed'] and s['accounting']['droppedIterations'] > 0
               and s['accounting']['notCompletedIterations'] > 0
               and any('dropped_iterations' in item for item in s['verdict']['failedThresholds']),
               'selftest-slow-drops'),
    ]
    if args.real_cohort:
        script = K6_DIR / REAL_SCRIPTS[args.real_cohort]
        results.append(expect(run_case('bad-body', script, {'VUS': '1'}), 99,
                              lambda s: not s['verdict']['passed'] and s['responseContractErrorRate'] == 1
                              and s['expectedStatusErrorRate'] == 0,
                              f'real-{args.real_cohort}-bad-body'))
        results.append(expect(run_case('ok', script, {'VUS': '1'}), 0,
                              lambda s: s['verdict']['passed'] and s['responseContractErrorRate'] == 0,
                              f'real-{args.real_cohort}-ok'))
    if args.real_capacity:
        script = K6_DIR / 'capacity-api-products.js'
        results.append(expect(run_case('bad-body', script, {'TARGET_RPS': '250'}), 99,
                              lambda s: not s['verdict']['passed'] and s['responseContractErrorRate'] == 1
                              and s['accounting']['scheduledIterations'] == 30000,
                              'real-capacity-api-products-bad-body'))
    if not all(results):
        print('selftest verdict=FAIL', flush=True)
        raise SystemExit(1)
    print('selftest verdict=PASS', flush=True)


if __name__ == '__main__':
    main()
