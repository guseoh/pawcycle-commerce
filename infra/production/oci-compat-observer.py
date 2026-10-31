#!/usr/bin/python3 -I
"""Root-installed, fail-closed live MySQL compatibility observer.

No credentials, production rows or raw MySQL output are emitted. This does NOT
create semantic test evidence. Manual pairs require reviewed isolated evidence;
the opt-in cosmetic mode proves a bounded source transformation instead.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time

SHA = re.compile(r"[0-9a-f]{40}\Z")
DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")
VERSION = re.compile(r"V([0-9]+(?:\.[0-9]+)*)__.*\.sql\Z")
ROOT = Path('/opt/pawcycle')
MIRROR = ROOT / 'cd-source.git'
RUNTIME = ROOT / 'runtime/current/backend.env'
MYSQL_OPTIONS = Path('/etc/pawcycle/oci-compat-mysql.cnf')
PROOF = Path('/etc/pawcycle/oci-compat-proof.json')
ARTIFACT_DIR = ROOT / 'compat-evidence'
REQUIRED = ('frontend_api', 'authentication', 'orders', 'billing', 'inventory', 'subscription')
# This narrow observer covers only frontend source/assets. Any extra file
# (including CI, release tools, docs, or build inputs) needs separate review.
FRONTEND_SOURCE_PREFIXES = ('frontend/src/', 'frontend/public/')
AUTO_MODE = 'frontend-cosmetic-v1'
CSS_PATH = 'frontend/src/app/globals.css'
COLOR = re.compile(rb'(?<![\w-])(--accent(?:-soft)?:)(#[0-9a-fA-F]{6})(?=;)')


def frontend_only_scope(changed):
    return bool(changed) and all(
        path.startswith(FRONTEND_SOURCE_PREFIXES) for path in changed
    )


class Rejected(Exception):
    pass


def check(ok, reason='unverified observer input'):
    if not ok:
        raise Rejected(reason)


def digest(data):
    return 'sha256:' + hashlib.sha256(data).hexdigest()


def trusted(path, mode=0o600, directory=False):
    path = Path(path)
    for parent in (path, *path.parents):
        st = parent.lstat()
        check(st.st_uid == 0 and not (st.st_mode & 0o022) and not stat.S_ISLNK(st.st_mode),
              'unprotected observer path')
        check(stat.S_ISDIR(st.st_mode) if (directory or parent != path) else stat.S_ISREG(st.st_mode),
              'unexpected observer file type')
    check(stat.S_IMODE(path.lstat().st_mode) == mode, 'observer file permissions')
    if not directory:
        check(path.lstat().st_nlink == 1, 'hardlinked observer input')
    return path


def run(argv, seconds=25):
    env = {'PATH': '/usr/bin:/bin', 'HOME': '/nonexistent', 'LANG': 'C',
           'GIT_CONFIG_GLOBAL': '/dev/null', 'GIT_CONFIG_NOSYSTEM': '1'}
    try:
        proc = subprocess.run(argv, env=env, stdout=subprocess.PIPE,
                              stderr=subprocess.DEVNULL, timeout=seconds, check=False)
    except (OSError, subprocess.TimeoutExpired):
        raise Rejected('observer dependency unavailable') from None
    check(proc.returncode == 0 and len(proc.stdout) <= 131072,
          'observer dependency failed or oversized')
    return proc.stdout


def git(*arguments):
    return run(['/usr/bin/git', '-C', str(MIRROR), *arguments]).decode('utf-8').strip()


def mysql(statement):
    # The protected options file supplies host, credentials and a trusted CA.
    # Enforce TCP and both CA + server-hostname verification at invocation time.
    # A missing/invalid CA, hostname mismatch or unsupported MySQL client fails
    # closed; never fall back to PREFERRED, a socket or plaintext transport.
    result = run(['/usr/bin/mysql', '--defaults-file=' + str(MYSQL_OPTIONS),
                  '--protocol=TCP', '--ssl-mode=VERIFY_IDENTITY',
                  '--connect-timeout=5', '--batch', '--raw', '--skip-column-names',
                  '--skip-auto-rehash', '--execute=' + statement], seconds=15)
    return result.decode('utf-8').strip().splitlines()


def live_snapshot():
    meta = mysql('SELECT @@server_uuid, DATABASE(), @@version')
    check(len(meta) == 1 and len(meta[0].split('\t')) == 3,
          'DB identity unavailable')
    uuid, schema, engine = meta[0].split('\t')
    check(bool(re.fullmatch(r'[0-9a-fA-F-]{36}', uuid)) and
          bool(re.fullmatch(r'[A-Za-z0-9_]+', schema)) and engine.startswith('8.'),
          'unexpected DB identity or MySQL engine')
    rows = []
    for line in mysql('SELECT version, script, checksum, success, type '
                      'FROM flyway_schema_history ORDER BY installed_rank'):
        fields = line.split('\t')
        check(len(fields) == 5 and fields[3] == '1' and fields[4] == 'SQL' and
              fields[2] != 'NULL' and bool(re.fullmatch(r'-?\d+', fields[2])),
              'failed or unexpected Flyway history')
        m = VERSION.fullmatch(fields[1])
        check(m is not None and m.group(1) == fields[0] and fields[1] not in [r[1] for r in rows],
              'Flyway source/history mismatch')
        rows.append(fields)
    check(rows, 'Flyway history empty')
    # Privacy: only a digest of the identifier/version/history leaves the process.
    snapshot = digest(json.dumps([uuid, schema, engine, rows],
                                 separators=(',', ':'), sort_keys=True).encode())
    return snapshot, {r[1] for r in rows}, len(rows)


def proof_artifact(proof, current, target):
    item = proof['semantic_artifact']
    path = Path(item['path'])
    check(path.parent == ARTIFACT_DIR and DIGEST.fullmatch(item['sha256']),
          'unapproved artifact path or digest')
    data = trusted(path).read_bytes()
    check(len(data) <= 65536 and digest(data) == item['sha256'],
          'semantic artifact hash mismatch')
    report = json.loads(data)
    check(report['format'] == 1 and report['current'] == current and
          report['target'] == target and report['isolation'] == 'disposable-mysql-8.4' and
          report['forward_pass'] is True and report['reverse_pass'] is True and
          report['reviewed'] is True, 'unverified bidirectional pair result')
    checks = report['checks']
    check(isinstance(checks, dict) and set(checks) == set(REQUIRED), 'invariant scope missing')
    for domain in REQUIRED:
        entry = checks[domain]
        check(isinstance(entry, dict) and entry.get('result') in ('pass', 'not-applicable') and
              isinstance(entry.get('reason'), str) and len(entry['reason']) >= 12 and
              isinstance(entry.get('test_reference'), str) and len(entry['test_reference']) >= 8,
              'semantic test scope/evidence incomplete')
    return {key: checks[key]['result'] for key in REQUIRED}


def cosmetic_css(baseline, candidate):
    """Only two existing literal colors may vary; every other byte is fixed.

    No CSS parser, executable source, URL or selector from the mirror is run.
    The initial operator review must confirm these tokens are decorative in
    the approved baseline. The bound is cumulative, never reset per release.
    """
    def colors(data):
        check(len(data) <= 131072, 'CSS exceeds cosmetic limit')
        matches = list(COLOR.finditer(data))
        check(len(matches) == 2 and {m[1] for m in matches} == {b'--accent:', b'--accent-soft:'},
              'cosmetic token shape changed')
        return {m[1]: bytes.fromhex(m[2][1:].decode()) for m in matches}
    before, after = colors(baseline), colors(candidate)
    check(COLOR.sub(rb'\1#COLOR', baseline) == COLOR.sub(rb'\1#COLOR', candidate),
          'non-color CSS change requires supervised baseline alignment')
    check(all(abs(a - b) <= 16 for key in before for a, b in zip(before[key], after[key])),
          'cumulative cosmetic color budget exceeded')


def automatic_source(policy, current, target):
    """Validate every main first-parent edge, including reverted unsafe changes.

    A docs tail may be ignored for deployment, never a CI/release/tools tail.
    Both the full current->target diff and the initial baseline->main history
    are bound into the evidence. More than 256 commits requires policy renewal.
    """
    baseline = policy['automatic_baseline_sha']
    check(SHA.fullmatch(baseline), 'automatic baseline missing')
    check(git('rev-parse', '--is-bare-repository') == 'true', 'automatic source must be bare')
    check(git('remote', 'get-url', 'origin') == 'https://github.com/guseoh/pawcycle-commerce.git',
          'wrong automatic source origin')
    tip = git('rev-parse', 'refs/remotes/origin/main')
    check(SHA.fullmatch(tip), 'automatic main unavailable')
    history = git('rev-list', '--first-parent', '--max-count=258', tip).splitlines()
    check(baseline in history and history.index(baseline) <= 256,
          'supervised baseline alignment or policy renewal required')
    chain = list(reversed(history[:history.index(baseline) + 1]))
    check(current in chain and target in chain and chain.index(current) < chain.index(target),
          'release pair is not forward main history')
    base_css = run(['/usr/bin/git', '-C', str(MIRROR), 'show', baseline + ':' + CSS_PATH])
    cosmetic_css(base_css, base_css)
    transcript, latest = [], None

    def delta(before, after):
        raw = run(['/usr/bin/git', '-C', str(MIRROR), 'diff', '--no-ext-diff', '--no-renames',
                   '--raw', '--no-abbrev', '-z', before, after])
        parts = raw.split(b'\0')
        check(parts[-1] == b'' and (len(parts) - 1) % 2 == 0, 'incomplete source diff')
        css = False
        for index in range(0, len(parts) - 1, 2):
            fields = parts[index].decode('ascii').split()
            path = parts[index + 1].decode('utf-8')
            check(len(fields) == 5 and fields[0] in (':100644', ':000000') and
                  fields[1] == '100644' and fields[4] in ('M', 'A') and
                  all(SHA.fullmatch(oid) for oid in fields[2:4]),
                  'automatic file type/deletion/rename change blocked')
            if path == CSS_PATH:
                check(fields[4] == 'M', 'cosmetic source must already exist')
                cosmetic_css(base_css, run(['/usr/bin/git', '-C', str(MIRROR), 'show', after + ':' + path]))
                css = True
            else:
                check((path == 'README.md' or path.startswith('docs/') and path.endswith('.md')) and
                      all(ord(c) >= 32 and ord(c) != 127 for c in path),
                      'non-cosmetic change requires supervised baseline alignment')
        transcript.append([before, after, digest(raw)])
        return css

    for before, after in zip(chain, chain[1:]):
        if delta(before, after) and chain.index(after) > chain.index(current):
            latest = after
    check(latest == target, 'automatic target superseded or has no cosmetic change')
    check(delta(current, target), 'empty cumulative cosmetic change')
    return {'mode': AUTO_MODE, 'main': tip, 'baseline': baseline,
            'history_sha256': digest(json.dumps(transcript, separators=(',', ':')).encode()),
            'policy_sha256': digest((json.dumps(policy, sort_keys=True, separators=(',', ':')) + '\n').encode()),
            'invariants': 'all non-document bytes unchanged except bounded literal accent colors'}


def observe(current, target, now=None, auto_policy=None):
    now = int(time.time()) if now is None else now
    check(SHA.fullmatch(current) and SHA.fullmatch(target) and current != target,
          'invalid release pair')
    check(os.geteuid() == 0, 'root-only observer')
    trusted(MIRROR, mode=0o700, directory=True)
    trusted(MYSQL_OPTIONS)
    if auto_policy is None:
        trusted(PROOF)
    runtime_root = trusted(ROOT / 'runtime', mode=0o700, directory=True)
    runtime_link = runtime_root / 'current'
    link_stat = runtime_link.lstat()
    check(stat.S_ISLNK(link_stat.st_mode) and link_stat.st_uid == 0,
          'runtime bundle symlink missing')
    bundle = runtime_link.resolve(strict=True)
    check(bundle.parent == runtime_root and bundle.name.startswith('.bundle.'),
          'runtime bundle escapes protected directory')
    trusted(bundle, mode=0o700, directory=True)
    backend_env = trusted(bundle / 'backend.env', mode=0o600)
    check(trusted(bundle / '.complete', mode=0o600).read_bytes() == b'g0-t2a-canonical-v1\n',
          'unverified runtime bundle')
    runtime_hash = digest(backend_env.read_bytes())
    if auto_policy is not None:
        policy = json.loads(trusted(auto_policy).read_bytes())
        check(policy['format'] == 1 and policy['enabled'] is True and policy['target'] == 'oci-app01' and
              policy['repository'] == 'https://github.com/guseoh/pawcycle-commerce.git' and
              policy['approval_evidence'] and policy['compatibility_mode'] == AUTO_MODE and
              type(policy['expires_unix']) is int and now < policy['expires_unix'] and
              policy['allowed_prefixes'] == [CSS_PATH] and policy['allowed_risks'] == ['application'] and
              policy['runtime_env_sha256'] == runtime_hash and DIGEST.fullmatch(policy['db_epoch']),
              'automatic policy OFF, expired or outside cosmetic scope')
        observations = automatic_source(policy, current, target)
        tree = git('rev-parse', current + ':backend/src/main/resources/db/migration')
        check(tree == git('rev-parse', target + ':backend/src/main/resources/db/migration') == policy['migration_tree'],
              'automatic migration tree drift')
        names = git('ls-tree', '-r', '--name-only', current + ':backend/src/main/resources/db/migration').splitlines()
        check(names and all(VERSION.fullmatch(n) for n in names), 'migration file set invalid')
        snapshot, actual, count = live_snapshot()
        check(actual == set(names) and count == len(names) and snapshot == policy['db_epoch'],
              'automatic live DB/Flyway drift')
        check(live_snapshot() == (snapshot, actual, count), 'live DB changed during automatic observation')
        observations['flyway_versions'] = count
        evidence = digest(json.dumps([current, target, runtime_hash, snapshot, observations], sort_keys=True).encode())
        return {'current': current, 'target': target, 'db_epoch': snapshot,
                'runtime_env_sha256': runtime_hash, 'application_data_compatible': True, 'rollback_safe': True,
                'evidence': AUTO_MODE + '/' + evidence, 'observations': observations,
                'checked_unix': now, 'expires_unix': min(now + 90, policy['expires_unix'])}
    attest = json.loads(PROOF.read_bytes())
    check(attest['format'] == 1 and attest['current'] == current and attest['target'] == target and
          DIGEST.fullmatch(attest['db_epoch']) and DIGEST.fullmatch(attest['runtime_env_sha256']) and
          attest['runtime_env_sha256'] == runtime_hash and
          attest['reviewed_by_operator'] is True and isinstance(attest['evidence'], str) and
          len(attest['evidence']) >= 8 and
          type(attest['verified_unix']) is int and 0 <= now - attest['verified_unix'] <= 86400 and
          type(attest['expires_unix']) is int and now < attest['expires_unix'] <= attest['verified_unix'] + 86400,
          'operator attestation missing, stale or mismatched')
    check(git('merge-base', '--is-ancestor', current, target) == '', 'release history invalid')
    tree_current = git('rev-parse', current + ':backend/src/main/resources/db/migration')
    tree_target = git('rev-parse', target + ':backend/src/main/resources/db/migration')
    check(tree_current == tree_target == attest['migration_tree'], 'migration tree drift')
    paths = run(['/usr/bin/git', '-C', str(MIRROR), 'diff', '--no-renames', '--name-only',
                 '-z', current, target]).split(b'\0')
    changed = sorted(x.decode('utf-8') for x in paths if x)
    check(digest(('\n'.join(changed) + '\n').encode()) == attest['changed_paths_sha256'],
          'release pair changed after semantic review')
    check(frontend_only_scope(changed),
          'backend/build/runtime change requires separate observer')
    names = git('ls-tree', '-r', '--name-only', current + ':backend/src/main/resources/db/migration').splitlines()
    expected = set(names)
    check(expected and all(VERSION.fullmatch(n) for n in expected), 'migration file set invalid')
    snapshot, actual, count = live_snapshot()
    check(actual == expected and count == len(expected) and
          attest['db_snapshot_sha256'] == snapshot and attest['db_epoch'] == snapshot,
          'live DB/Flyway baseline changed')
    checks = proof_artifact(attest, current, target)
    # Detect Flyway/DB identity changes while checking the separately reviewed pair.
    final_snapshot, final_scripts, final_count = live_snapshot()
    check((final_snapshot, final_scripts, final_count) == (snapshot, actual, count),
          'live DB changed during compatibility observation')
    return {'current': current, 'target': target, 'db_epoch': snapshot,
            'runtime_env_sha256': runtime_hash,
            'application_data_compatible': True, 'rollback_safe': True,
            'evidence': attest['evidence'],
            'observations': {'flyway_versions': count, 'invariant_results': checks,
                             'semantic_artifact_sha256': attest['semantic_artifact']['sha256']},
            'checked_unix': now, 'expires_unix': now + 90}


def main():
    try:
        check(len(sys.argv) == 3 or len(sys.argv) == 5 and sys.argv[3] == '--auto-policy',
              'expected current and target SHA, optionally --auto-policy protected-path')
        print(json.dumps(observe(sys.argv[1], sys.argv[2],
                                 auto_policy=Path(sys.argv[4]) if len(sys.argv) == 5 else None), sort_keys=True))
        return 0
    except (Rejected, OSError, ValueError, KeyError, TypeError, UnicodeError,
            json.JSONDecodeError):
        # Do not expose DB rows, secrets, local identifiers or subprocess errors.
        print('observer BLOCKED', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
