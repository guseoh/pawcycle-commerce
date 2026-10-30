#!/usr/bin/env python3
"""G0-T2A only. Packet-gated transition; no deployment, DB, or ACME commands."""
import contextlib
from datetime import datetime, timezone
import errno
import fcntl
import hashlib
import json
import os
from pathlib import Path
import resource
import shutil
import signal
import stat
import subprocess
import sys
import time

sys.dont_write_bytecode = True

from production_env import (Refused, require, parse, projection, serialize,
                            signature, stable_read, verify_effective, AUTOMATION)

ORIGIN = 'https://github.com/guseoh/pawcycle-commerce.git'
TIMER = 'pawcycle-production-https-renew.timer'
SERVICE = 'pawcycle-production-https-renew.service'
COMPOSE = 'infra/production/compose.yaml'
RENEW = 'infra/production/renew-production-certificate.sh'
SHA = __import__('re').compile('[0-9a-f]{40}')
HASH = __import__('re').compile('[0-9a-f]{64}')


def acl_free(path):
    for attr in ('system.posix_acl_access', 'system.posix_acl_default'):
        try:
            os.getxattr(path, attr, follow_symlinks=False)
        except OSError as exc:
            require(exc.errno in (errno.ENODATA, errno.ENOTSUP), 'PATH_ACL')
        else:
            raise Refused('PATH_ACL')


def protected(path, mode=None):
    path = Path(path)
    require(path.is_absolute(), 'PATH_ABSOLUTE')
    for item in reversed((path, *path.parents)):
        info = item.lstat()
        require(not stat.S_ISLNK(info.st_mode) and info.st_uid == info.st_gid == 0,
                'PATH_OWNER')
        require(not info.st_mode & 0o022 or
                stat.S_ISDIR(info.st_mode) and info.st_mode & stat.S_ISVTX, 'PATH_MODE')
        acl_free(item)
    if mode is not None:
        require(stat.S_IMODE(path.lstat().st_mode) == mode, 'PATH_MODE')
    info = path.lstat()
    require(stat.S_ISDIR(info.st_mode) or stat.S_ISREG(info.st_mode) and info.st_nlink == 1,
            'PATH_TYPE')
    return path


def sync_dir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | os.O_CLOEXEC)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def write_new(path, data, mode=0o600):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | os.O_CLOEXEC, mode)
    with os.fdopen(fd, 'wb') as stream:
        os.fchmod(stream.fileno(), mode)
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


class Runner:
    def __init__(self, deadline, timeout):
        self.deadline = deadline
        self.timeout = timeout

    def check(self):
        require(time.monotonic() < self.deadline, 'WINDOW_EXPIRED')

    @staticmethod
    def reap(proc):
        """One finite cleanup budget, including pipe drain and parent wait.

        A descendant may escape the group while retaining a pipe. Never wait
        for that pipe forever or kill a guessed PID outside the known group.
        """
        deadline = time.monotonic()+2
        eof = False
        try:
            with contextlib.suppress(ProcessLookupError):
                os.killpg(proc.pid, signal.SIGTERM)
            try:
                proc.communicate(timeout=min(0.5, max(0, deadline-time.monotonic())))
                eof = True
            except subprocess.TimeoutExpired:
                pass
            with contextlib.suppress(ProcessLookupError):
                os.killpg(proc.pid, signal.SIGKILL)
            if not eof:
                try:
                    proc.communicate(timeout=max(0, deadline-time.monotonic()))
                    eof = True
                except subprocess.TimeoutExpired:
                    pass
            if proc.poll() is None:
                proc.wait(timeout=max(0, deadline-time.monotonic()))
            try:
                os.killpg(proc.pid, 0)
            except ProcessLookupError:
                return eof and proc.poll() is not None
            return False
        except BaseException:
            return False
        finally:
            for stream in (proc.stdout, proc.stderr):
                if stream is not None:
                    stream.close()

    def __call__(self, argv, env=None):
        self.check()
        clean = {'PATH': '/usr/bin:/bin', 'LANG': 'C', 'HOME': '/root',
                 'DOCKER_HOST': 'unix:///var/run/docker.sock',
                 'GIT_CONFIG_NOSYSTEM': '1', 'GIT_CONFIG_GLOBAL': '/dev/null'}
        clean.update(env or {})
        proc = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=clean, start_new_session=True,
                                close_fds=True, pass_fds=())
        try:
            out, _ = proc.communicate(timeout=min(self.timeout, self.deadline-time.monotonic()))
            # A completed parent with children still in its session is not success.
            try:
                os.killpg(proc.pid, 0)
            except ProcessLookupError:
                pass
            else:
                raise Refused('CHILD_REMAINS')
            require(proc.returncode == 0, 'COMMAND_FAILED')
            self.check()
            return out
        except BaseException as exc:
            certain = self.reap(proc)
            if not certain:
                # CHILD_REMAINS already denotes this uncertainty; preserve its
                # existing reason code, never turn it into a successful return.
                if isinstance(exc, Refused) and str(exc) == 'CHILD_REMAINS':
                    raise
                raise Refused('PROCESS_UNCERTAIN') from None
            if isinstance(exc, subprocess.TimeoutExpired):
                raise Refused('COMMAND_TIMEOUT') from None
            raise


class Transition:
    """Fixed paths; root prefix/runner injection is library-only for fixtures."""
    def __init__(self, packet, root=Path('/'), runner=None):
        self.p = packet
        self.root = Path(root)
        self.base = self.path('/opt/pawcycle')
        self.runtime = self.base / 'runtime'
        self.state = self.base / 'state'
        self.deploy = self.state / 'deploy.lock'
        self.renewal = self.path('/run/lock/pawcycle-production-https-renewal.lock')
        self.installed = self.path('/usr/local/libexec/pawcycle-production-https-renew')
        self.control = self.path('/var/lib/pawcycle-transition')
        self.journal = self.control / 'ops-g0-t2a-002.journal'
        self.source = self.base / 'root-source' / str(packet.get('source_sha', 'invalid'))
        self.staging = self.control / 'source' / str(packet.get('source_sha', 'invalid'))
        self.bundle = self.runtime / ('.bundle.g0-t2a-' + str(packet.get('id', 'invalid')))
        self.fds = []
        self.lock_bytes = []
        self.switched = False
        self.phase = 'preflight'
        self.timer_stopped = False
        self.timer_restore_attempted = False
        self.original = None
        self.invariant_before = None
        remaining = max(0, packet.get('expires_unix', 0)-time.time())
        self.run = runner or Runner(time.monotonic()+remaining-packet.get('recovery_seconds', 0),
                                    packet.get('command_timeout_seconds', 1))

    def path(self, absolute):
        return self.root / absolute.lstrip('/')

    def docker(self, *args, env=None):
        return self.run(['/usr/bin/docker', *args], env)

    def system(self, *args):
        return self.run(['/usr/bin/systemctl', *args])

    def git(self, *args):
        return self.run(['/usr/bin/git', '-c', 'core.hooksPath=/dev/null',
                         '-c', 'core.fsmonitor=false', '-c', 'credential.helper=',
                         '-c', 'protocol.file.allow=never',
                         '-C', str(self.source), *args])

    def allowed_changes(self):
        return {str(self.base): 0o755, str(self.runtime): 0o700,
                **{str(self.base / p): 0o755 for p in
                   ('source', 'source/repo', 'source/repo/infra', 'source/repo/infra/production')},
                str(self.base / 'source/repo' / COMPOSE): 0o644,
                str(self.runtime / 'nginx-https.conf'): 0o600}

    def snapshot(self):
        ids = self.docker('ps', '-aq').decode().split()
        require(ids, 'RUNTIME_IDENTITY')
        documents = json.loads(self.docker('inspect', *ids))
        result = []
        for c in documents:
            labels = c['Config'].get('Labels') or {}
            result.append({'id': c['Id'], 'image': c['Image'], 'ref': c['Config']['Image'],
                           'started': c['State']['StartedAt'], 'status': c['State']['Status'],
                           'health': c['State'].get('Health', {}).get('Status'),
                           'restarts': c['RestartCount'], 'mounts': c['Mounts'],
                           'project': labels.get('com.docker.compose.project'),
                           'service': labels.get('com.docker.compose.service'),
                           'files': labels.get('com.docker.compose.project.config_files'),
                           'networks': {k: {f: v.get(f) for f in ('NetworkID', 'EndpointID')}
                                        for k, v in c['NetworkSettings']['Networks'].items()}})
        return sorted(result, key=lambda c: c['id'])

    def unit_state(self):
        raw = self.system('show', TIMER, '-pActiveState', '-pUnitFileState').decode()
        return dict(line.split('=', 1) for line in raw.splitlines() if '=' in line)

    def idle(self):
        raw = self.system('show', SERVICE, '-pActiveState', '-pMainPID').decode()
        require('ActiveState=inactive' in raw.splitlines() and 'MainPID=0' in raw.splitlines(),
                'RENEWAL_BUSY')
        require(SERVICE.encode() not in self.system('list-jobs', '--no-legend', '--no-pager'),
                'RENEWAL_BUSY')

    def unit_contract(self):
        import re
        manager_keys = {line.partition(b'=')[0] for line in self.system('show-environment').splitlines()}
        require(not manager_keys & {b'PAWCYCLE_STATE_DIR', b'PAWCYCLE_HTTPS_RENEWAL_LOCK_FILE',
                                    b'BASH_ENV', b'ENV', b'LD_PRELOAD', b'LD_LIBRARY_PATH'},
                'UNIT_OVERRIDE')
        for unit in (SERVICE, TIMER):
            installed = protected(self.path('/etc/systemd/system/'+unit), 0o644)
            staged = protected(self.staging/'infra/production/systemd'/unit)
            require(installed.read_bytes() == staged.read_bytes(), 'UNIT_BYTES')
            properties = ['DropInPaths', 'FragmentPath']
            if unit == SERVICE:
                properties += ['ExecStart', 'User', 'Group', 'Type']
            raw = self.system('show', unit, '--all',
                              *['-p'+key for key in properties]).decode()
            fields = {}
            for line in raw.splitlines():
                key, separator, value = line.partition('=')
                require(separator and key in properties and key not in fields,
                        'UNIT_OBSERVATION')
                fields[key] = value
            require(set(fields) == set(properties), 'UNIT_OBSERVATION')
            require(fields.get('DropInPaths') == '' and
                    fields.get('FragmentPath') == str(installed), 'UNIT_OVERRIDE')
            if unit == SERVICE:
                require(fields.get('User') == fields.get('Group') == 'root'
                        and fields.get('Type') == 'oneshot',
                        'UNIT_OVERRIDE')
                # systemctl's EnvironmentFiles array printer emits no line for
                # an empty array, even with --all (systemd 255). Absence is not
                # evidence of emptiness. Require both typed D-Bus properties.
                object_path = '/org/freedesktop/systemd1/unit/' + ''.join(
                    char if char.isascii() and char.isalnum() else '_'+format(ord(char), '02x')
                    for char in SERVICE)
                empty = self.run(['/usr/bin/busctl', '--system', 'get-property',
                                  'org.freedesktop.systemd1', object_path,
                                  'org.freedesktop.systemd1.Service',
                                  'Environment', 'EnvironmentFiles'])
                require(empty == b'as 0\na(sb) 0\n', 'UNIT_OVERRIDE')
                execute = fields.get('ExecStart', '')
                require(re.findall(r'\bpath=([^ ;}]+)', execute) == [str(self.installed)]
                        and [s.strip() for s in re.findall(r'argv\[\]=([^;}]+)', execute)]
                        == [str(self.installed)], 'UNIT_OVERRIDE')

    def no_writer(self):
        """No command lines or process environments are read or reported."""
        targets = [self.runtime/'backend.env', self.runtime/'nginx-https.conf',
                   self.base/'source/repo'/COMPOSE]
        wanted = {(p.stat().st_dev, p.stat().st_ino) for p in targets}
        for process in Path('/proc').iterdir():
            if not process.name.isdigit():
                continue
            try:
                for fd in (process/'fd').iterdir():
                    try:
                        info = fd.stat()
                        if (info.st_dev, info.st_ino) not in wanted:
                            continue
                        flags = next(line.split()[1] for line in
                                     (process/'fdinfo'/fd.name).read_text().splitlines()
                                     if line.startswith('flags:'))
                        require(int(flags, 8) & os.O_ACCMODE == os.O_RDONLY, 'ENV_WRITER')
                    except FileNotFoundError:
                        continue
                for line in (process/'maps').read_text().splitlines():
                    fields = line.split()
                    require(not (len(fields) >= 6 and fields[-1] in set(map(str, targets))
                                 and 'w' in fields[1]), 'ENV_WRITER')
            except (FileNotFoundError, ProcessLookupError):
                continue

    def invariants(self, before_protection=False):
        # Certificate is PUBLIC; never read privkey.pem or any DB/Secret volume.
        cert = self.path('/var/lib/docker/volumes/pawcycle-production-letsencrypt/_data/live/pawcycle.duckdns.org/cert.pem')
        resolved = protected(cert.resolve(strict=True))
        not_after = self.run(['/usr/bin/openssl', 'x509', '-in', str(resolved),
                              '-noout', '-enddate']).decode().strip()
        expiry = datetime.strptime(not_after, 'notAfter=%b %d %H:%M:%S %Y %Z').replace(
            tzinfo=timezone.utc).timestamp()
        require(expiry == self.p['certificate_not_after_unix'], 'CERTIFICATE_DRIFT')
        adoption = {}
        for name, expected in (('https-domain', b'pawcycle.duckdns.org'), ('https-enabled', b'enabled')):
            path = self.state/name
            if not before_protection:
                protected(path, 0o600)
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
            try:
                adoption[name] = stable_read(fd, path)
            finally:
                os.close(fd)
            require(adoption[name].strip() == expected, 'HTTPS_ADOPTION')
        names = ('pawcycle-production-letsencrypt', 'pawcycle-production-certbot-webroot')
        volumes = json.loads(self.docker('volume', 'inspect', *names))
        volumes = sorted(({k: v[k] for k in ('Name', 'Driver', 'Mountpoint')} for v in volumes),
                         key=lambda v: v['Name'])
        return resolved.read_bytes(), adoption, volumes

    def validate_packet(self):
        keys = {'schema', 'id', 'source_sha', 'main_sha', 'compose_sha256', 'renewal_sha256',
                'installed_sha256', 'nginx_sha256', 'not_before_unix', 'expires_unix',
                'recovery_seconds', 'command_timeout_seconds', 'certificate_not_after_unix',
                'minimum_certificate_seconds', 'quiescence_confirmed', 'changes',
                'env_identity', 'deploy_identity', 'renewal_identity', 'installed_identity',
                'snapshot', 'timer', 'release_sha', 'backend_image', 'frontend_image',
                'compose_version', 'exclusions', 'approval_reference', 'tool_hashes'}
        require(set(self.p) == keys and self.p['schema'] == 1, 'PACKET_SCHEMA')
        import re
        require(re.fullmatch('[a-z0-9-]{1,48}', self.p['id'])
                and isinstance(self.p['approval_reference'], str) and self.p['approval_reference'],
                'PACKET_SCHEMA')
        require(all(SHA.fullmatch(self.p[k]) for k in ('source_sha', 'main_sha', 'release_sha')),
                'PACKET_SOURCE')
        require(self.p['source_sha'] == self.p['main_sha'], 'SOURCE_MAIN')
        require(set(self.p['tool_hashes']) == {'trust_transition.py', 'production_env.py'},
                'TOOL_HASH')
        for name, expected in self.p['tool_hashes'].items():
            require(HASH.fullmatch(expected) and hashlib.sha256(
                Path(__file__).with_name(name).read_bytes()).hexdigest() == expected, 'TOOL_HASH')
        require(all(HASH.fullmatch(self.p[k]) for k in
                    ('compose_sha256', 'renewal_sha256', 'installed_sha256', 'nginx_sha256')),
                'PACKET_SOURCE')
        now = time.time()
        require(self.p['not_before_unix'] <= now < self.p['expires_unix']
                and self.p['expires_unix']-self.p['not_before_unix'] <= 3600
                and 1 <= self.p['command_timeout_seconds'] <= 120
                and 30 <= self.p['recovery_seconds'] < self.p['expires_unix']-now,
                'PACKET_WINDOW')
        require(self.p['certificate_not_after_unix']-self.p['expires_unix'] >=
                self.p['minimum_certificate_seconds'] >= 86400, 'CERTIFICATE_WINDOW')
        require(self.p['quiescence_confirmed'] is True, 'QUIESCENCE')
        require(self.p['exclusions'] in ([], ['HOSTNAME']), 'ENV_EXCLUSION')
        require(self.p['timer'] == {'ActiveState': 'active', 'UnitFileState': 'enabled'},
                'TIMER_CONTRACT')
        require(set(self.p['changes']) == set(self.allowed_changes()), 'PATH_ALLOWLIST')
        for record in self.p['changes'].values():
            require(isinstance(record, list) and len(record) == 9, 'PACKET_IDENTITY')
        protected(self.control, 0o700)
        protected(self.base.parent)
        state_info = self.state.lstat()
        require(stat.S_ISDIR(state_info.st_mode) and state_info.st_uid == state_info.st_gid == 0
                and stat.S_IMODE(state_info.st_mode) == 0o700, 'STATE_IDENTITY')
        acl_free(self.state)
        for binary in ('docker', 'git', 'systemctl', 'busctl', 'openssl'):
            protected(self.path('/usr/bin/'+binary).resolve(strict=True))
        require(not os.path.lexists(self.journal) and not os.path.lexists(self.journal.with_suffix('.new'))
                and not os.path.lexists(self.runtime/'current')
                and not os.path.lexists(self.bundle) and not os.path.lexists(self.source),
                'TRANSITION_EXISTS')
        protected(self.staging)
        self.idle()
        self.unit_contract()
        self.no_writer()
        self.invariant_before = self.invariants(before_protection=True)
        require(self.unit_state() == self.p['timer'], 'TIMER_DRIFT')
        for path, expected in self.p['changes'].items():
            require(list(signature(os.lstat(path))) == expected, 'PATH_DRIFT')
            require(stat.S_ISDIR(expected[4]) or stat.S_ISREG(expected[4])
                    and expected[5] == 1, 'PATH_TYPE')
            acl_free(path)
        for path, key in ((self.deploy, 'deploy_identity'), (self.renewal, 'renewal_identity'),
                          (self.installed, 'installed_identity'),
                          (self.runtime/'backend.env', 'env_identity')):
            require(list(signature(path.lstat())) == self.p[key], 'PATH_DRIFT')
            require(stat.S_ISREG(path.lstat().st_mode) and path.lstat().st_nlink == 1,
                    'PATH_TYPE')
            acl_free(path)
        for path, mode in ((self.deploy, 0o600), (self.renewal, 0o644), (self.installed, 0o755)):
            info = path.lstat()
            require(info.st_uid == info.st_gid == 0 and stat.S_IMODE(info.st_mode) == mode,
                    'PATH_MODE')
        require(not os.path.lexists(self.path('/etc/pawcycle/oci-cd-enabled')), 'CD_NOT_OFF')
        require(self.snapshot() == self.p['snapshot'], 'RUNTIME_DRIFT')
        production = [c for c in self.p['snapshot'] if c['project'] == 'pawcycle-production']
        require(sorted(c['service'] for c in production) == ['backend', 'frontend', 'proxy']
                and all(c['health'] == 'healthy' and c['status'] == 'running' for c in production),
                'RUNTIME_HEALTH')
        require(all(c['files'] == str(self.base/'source/repo'/COMPOSE) for c in production),
                'LABEL_PATH')
        protected(self.installed, 0o755)
        require(hashlib.sha256(self.installed.read_bytes()).hexdigest() == self.p['installed_sha256'],
                'SCRIPT_DRIFT')
        require(hashlib.sha256((self.runtime/'nginx-https.conf').read_bytes()).hexdigest()
                == self.p['nginx_sha256'], 'NGINX_DRIFT')
        require(not (self.state/'oci-release-state.json').exists()
                and not (self.state/'release-state-transition').exists()
                and not (self.state/'https-reload-pending').exists(), 'RELEASE_STATE')
        # Fixed bytes already adoption-required, but ancestor transition has not
        # occurred yet. Only controlled read-only observations run in preflight.
        original = self.runtime/'backend.env'
        fd = os.open(original, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
        try:
            parse(stable_read(fd, original, uid=self.p['env_identity'][2]))
        finally:
            os.close(fd)

    def journal_phase(self, phase, code=None, timer_handoff=None):
        self.phase = phase
        temporary = self.journal.with_suffix('.new')
        record = {'id': self.p['id'], 'phase': phase, 'code': code, 'bundle': str(self.bundle)}
        if timer_handoff is not None:
            record['timer_handoff'] = timer_handoff
        write_new(temporary, json.dumps(record).encode())
        os.replace(temporary, self.journal)
        sync_dir(self.control)

    def protect_paths(self):
        for path, mode in self.allowed_changes().items():
            self.run.check()
            protected(Path(path).parent)
            expected = self.p['changes'][path]
            require(list(signature(os.lstat(path))) == expected, 'PATH_DRIFT')
            flags = os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC
            if stat.S_ISDIR(expected[4]):
                flags |= os.O_DIRECTORY
            fd = os.open(path, flags)
            try:
                require(list(signature(os.fstat(fd))) == expected, 'PATH_DRIFT')
                os.fchown(fd, 0, 0)
                os.fchmod(fd, mode)
                os.fsync(fd)
            finally:
                os.close(fd)
            protected(path, mode)

    def lock_check(self, index, mode):
        path = (self.deploy, self.renewal)[index]
        expected = self.p[('deploy_identity', 'renewal_identity')[index]]
        fd = self.fds[index]
        for info in (path.lstat(), os.fstat(fd)):
            require((info.st_dev, info.st_ino) == tuple(expected[:2]) and info.st_uid == 0
                    and info.st_gid == 0 and stat.S_ISREG(info.st_mode) and info.st_nlink == 1
                    and stat.S_IMODE(info.st_mode) == mode, 'LOCK_IDENTITY')
        if len(self.lock_bytes) > index:
            require(os.pread(fd, 65537, 0) == self.lock_bytes[index], 'LOCK_BYTES')

    def acquire(self):
        for index, path in enumerate((self.deploy, self.renewal)):
            self.run.check()
            protected(path.parent)
            fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW | os.O_CLOEXEC)
            self.fds.append(fd)
            self.lock_check(index, (0o600, 0o644)[index])
            require(os.fstat(fd).st_size <= 65536, 'LOCK_BYTES')
            self.lock_bytes.append(os.pread(fd, 65537, 0))
            try:
                fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise Refused('LOCK_BUSY') from None
            self.lock_check(index, (0o600, 0o644)[index])

    def validate_source(self):
        protected(self.source)
        require((self.source/'.git').is_dir(), 'SOURCE_GIT')
        require(not any(os.path.lexists(self.source/'.git'/name) for name in
                        ('objects/info/alternates', 'objects/info/http-alternates',
                         'info/grafts', 'shallow')), 'SOURCE_GIT')
        # All source bytes and Git metadata must be independently root protected.
        for directory, dirs, files in os.walk(self.source, followlinks=False):
            self.run.check()
            for name in dirs + files:
                protected(Path(directory)/name)
        require(self.git('remote', 'get-url', 'origin').decode().strip() == ORIGIN, 'SOURCE_ORIGIN')
        require(self.git('rev-parse', 'HEAD').decode().strip() == self.p['source_sha'], 'SOURCE_SHA')
        require(not self.git('status', '--porcelain', '--untracked-files=all').strip(), 'SOURCE_DIRTY')
        self.git('fsck', '--full')
        tree = self.git('ls-tree', '-r', self.p['source_sha']).splitlines()
        require(tree and all(line.startswith((b'100644 blob ', b'100755 blob ')) for line in tree),
                'SOURCE_TREE')
        self.git('merge-base', '--is-ancestor', self.p['source_sha'], self.p['main_sha'])
        remote = self.git('ls-remote', 'origin', 'refs/heads/main').decode().split()
        require(remote == [self.p['main_sha'], 'refs/heads/main'], 'SOURCE_MAIN')
        for rel, field in ((COMPOSE, 'compose_sha256'), (RENEW, 'renewal_sha256')):
            data = (self.source/rel).read_bytes()
            require(data == self.git('show', self.p['source_sha']+':'+rel)
                    and hashlib.sha256(data).hexdigest() == self.p[field], 'SOURCE_BYTES')
        for unit in (SERVICE, TIMER):
            rel = 'infra/production/systemd/'+unit
            require((self.source/rel).read_bytes() == self.git('show', self.p['source_sha']+':'+rel),
                    'SOURCE_BYTES')
        require(b'exec 9>' not in (self.source/RENEW).read_bytes(), 'LEGACY_RENEWAL')
        require((self.base/'source/repo'/COMPOSE).read_bytes() == (self.source/COMPOSE).read_bytes(),
                'LABEL_BYTES')

    def prepare_source(self):
        # Local approved artifact only. Never clone from/promote the opc checkout.
        # The live source is installed ONLY while both shared locks are held.
        for directory, dirs, files in os.walk(self.staging, followlinks=False):
            self.run.check()
            for name in dirs + files:
                protected(Path(directory)/name)
        if not self.source.parent.exists():
            self.source.parent.mkdir(mode=0o700)
            sync_dir(self.base)
        protected(self.source.parent, 0o700)
        shutil.copytree(self.staging, self.source, symlinks=True)
        for directory, _, files in os.walk(self.source, topdown=False):
            self.run.check()
            for name in files:
                path = protected(Path(directory)/name)
                fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
                try:
                    os.fsync(fd)
                finally:
                    os.close(fd)
            sync_dir(directory)
        sync_dir(self.source.parent)
        self.validate_source()

    def install_script(self):
        self.run.check()
        self.lock_check(0, 0o600)
        self.lock_check(1, 0o644)
        os.fchmod(self.fds[1], 0o600)
        os.fsync(self.fds[1])
        self.lock_check(1, 0o600)
        protected(self.installed, 0o755)
        backup = self.control/'renewal.old'
        write_new(backup, self.installed.read_bytes())
        sync_dir(self.control)
        tmp = self.installed.with_suffix('.g0-t2a-new')
        data = (self.source/RENEW).read_bytes()
        self.run.check()
        write_new(tmp, data, 0o755)
        require(tmp.read_bytes() == data, 'SCRIPT_COPY')
        os.replace(tmp, self.installed)
        sync_dir(self.installed.parent)

    def verify_bundle(self, original_fd, original):
        path = self.bundle/'backend.env'
        protected(path, 0o600)
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
        try:
            data = stable_read(fd, path)
            require(data == original == stable_read(original_fd, self.runtime/'backend.env',
                    uid=self.p['env_identity'][2]), 'ENV_DRIFT')
            canonical = parse(data)
            for consumer in ('release', 'catalog', 'auth'):
                candidate = self.bundle/(consumer+'.env')
                protected(candidate, 0o600)
                require(candidate.read_bytes() == serialize(projection(canonical, consumer)),
                        'ENV_PROJECTION')
            require(self.docker('compose', 'version', '--short').decode().strip()
                    == self.p['compose_version'], 'COMPOSE_VERSION')
            documents = json.loads(self.docker('inspect', *[c['id'] for c in self.p['snapshot']]))
            backend = next(c for c in documents if c['Id'] == next(
                c['id'] for c in self.p['snapshot'] if c['project'] == 'pawcycle-production'
                and c['service'] == 'backend'))
            env = {'RELEASE_SHA': self.p['release_sha'], 'BACKEND_IMAGE': self.p['backend_image'],
                   'FRONTEND_IMAGE': self.p['frontend_image'],
                   'PAWCYCLE_BACKEND_ENV_FILE': str(path),
                   'PAWCYCLE_NGINX_CONFIG': str(self.runtime/'nginx-https.conf'),
                   **{k: canonical[k] for k in AUTOMATION}}
            config = json.loads(self.docker('compose', '--env-file', '/dev/null', '--project-name',
                              'pawcycle-production', '--file', str(self.source/COMPOSE),
                              'config', '--format', 'json', env=env))
            resolved = config['services']['backend']
            require(resolved['image'] == backend['Config']['Image'], 'ENV_IMAGE')
            image = json.loads(self.docker('image', 'inspect', backend['Image']))[0]
            require(image['Id'] == backend['Image'], 'ENV_IMAGE')
            verify_effective(canonical, resolved['environment'], image['Config'].get('Env') or [],
                             backend['Config']['Env'], self.p['exclusions'])
            require(self.snapshot() == self.p['snapshot'], 'RUNTIME_DRIFT')
            require(data == stable_read(fd, path) == stable_read(original_fd,
                    self.runtime/'backend.env', uid=self.p['env_identity'][2]), 'ENV_DRIFT')
        finally:
            os.close(fd)

    def make_bundle(self):
        self.run.check()
        original = self.runtime/'backend.env'
        protected(original.parent, 0o700)
        require(list(signature(original.lstat())) == self.p['env_identity'], 'ENV_IDENTITY')
        fd = os.open(original, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
        try:
            data = stable_read(fd, original, uid=self.p['env_identity'][2])
            self.original = data
            entries = parse(data)  # Do not create a bundle for invalid canonical input.
            self.bundle.mkdir(mode=0o700)
            sync_dir(self.runtime)
            write_new(self.bundle/'backend.env', data)
            for consumer in ('release', 'catalog', 'auth'):
                self.run.check()
                write_new(self.bundle/(consumer+'.env'), serialize(projection(entries, consumer)))
            self.verify_bundle(fd, data)
            self.run.check()
            self.lock_check(0, 0o600)
            self.lock_check(1, 0o600)
            write_new(self.bundle/'.complete', b'g0-t2a-canonical-v1\n')
            sync_dir(self.bundle)
            self.journal_phase('switching')
            self.run.check()
            require(not os.path.lexists(self.runtime/'current'), 'CURRENT_DRIFT')
            temporary = self.runtime/'.current.g0-t2a-new'
            os.symlink(self.bundle.name, temporary)
            os.replace(temporary, self.runtime/'current')
            self.switched = True
            sync_dir(self.runtime)
        finally:
            os.close(fd)

    def postflight(self):
        self.run.check()
        for path, mode in self.allowed_changes().items():
            protected(path, mode)
            require(tuple(signature(os.lstat(path))[:2]) == tuple(self.p['changes'][path][:2]),
                    'PATH_DRIFT')
        self.lock_check(0, 0o600)
        self.lock_check(1, 0o600)
        require(self.snapshot() == self.p['snapshot'], 'RUNTIME_DRIFT')
        require(self.installed.read_bytes() == (self.source/RENEW).read_bytes(), 'SCRIPT_DRIFT')
        require((self.base/'source/repo'/COMPOSE).read_bytes() == (self.source/COMPOSE).read_bytes(),
                'LABEL_BYTES')
        require(hashlib.sha256((self.runtime/'nginx-https.conf').read_bytes()).hexdigest()
                == self.p['nginx_sha256'], 'NGINX_DRIFT')
        require((self.runtime/'current').resolve(strict=True) == self.bundle
                and (self.bundle/'.complete').read_bytes() == b'g0-t2a-canonical-v1\n',
                'BUNDLE_DRIFT')
        fd = os.open(self.runtime/'backend.env', os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
        try:
            require(self.original == stable_read(fd, self.runtime/'backend.env',
                    uid=self.p['env_identity'][2]), 'ENV_DRIFT')
        finally:
            os.close(fd)
        self.no_writer()
        self.unit_contract()
        require(self.invariants() == self.invariant_before, 'RESOURCE_DRIFT')

    def release(self):
        while self.fds:
            os.close(self.fds.pop())

    def recovery_runner(self):
        seconds = min(self.p['recovery_seconds'], self.p['expires_unix']-time.time())
        require(seconds > 0, 'WINDOW_EXPIRED')
        return Runner(time.monotonic()+seconds, self.p['command_timeout_seconds'])

    def timer_incident_handoff(self):
        """Contain future triggers, observe; never kill an already running renew.

        start may have taken effect even when its acknowledgement was lost.
        Failed observations stay unknown, never fabricate an inactive timer.
        """
        result = {'stop': 'unknown', 'timer': 'unknown', 'enabled': 'unknown',
                  'renewal': 'unknown', 'queued': 'unknown'}
        try:
            run = self.recovery_runner()
        except BaseException:
            return result
        try:
            run(['/usr/bin/systemctl', 'stop', TIMER])
            result['stop'] = 'acknowledged'
        except BaseException:
            pass
        # Each observation shares the same finite recovery Runner deadline.
        try:
            raw = run(['/usr/bin/systemctl', 'show', TIMER, '-pActiveState', '-pUnitFileState'])
            fields = dict(line.split('=', 1) for line in raw.decode().splitlines() if '=' in line)
            if fields.get('ActiveState') in ('active', 'inactive', 'failed', 'activating', 'deactivating'):
                result['timer'] = fields['ActiveState']
            if fields.get('UnitFileState') in ('enabled', 'disabled', 'masked', 'static'):
                result['enabled'] = fields['UnitFileState']
        except BaseException:
            pass
        try:
            raw = run(['/usr/bin/systemctl', 'show', SERVICE, '-pActiveState', '-pMainPID'])
            fields = dict(line.split('=', 1) for line in raw.decode().splitlines() if '=' in line)
            if fields.get('ActiveState') == 'inactive' and fields.get('MainPID') == '0':
                result['renewal'] = 'idle'
            elif fields.get('ActiveState') in ('active', 'activating', 'deactivating'):
                result['renewal'] = 'possibly-running'
        except BaseException:
            pass
        try:
            jobs = run(['/usr/bin/systemctl', 'list-jobs', '--no-legend', '--no-pager'])
            result['queued'] = 'yes' if SERVICE.encode() in jobs else 'no'
        except BaseException:
            pass
        return result

    def apply(self):
        self.validate_packet()
        self.journal_phase('quiescence')
        try:
            self.system('stop', TIMER)
            self.timer_stopped = True
            self.idle()
            self.protect_paths()
            self.journal_phase('protected')
            self.acquire()
            require(self.invariants() == self.invariant_before, 'RESOURCE_DRIFT')
            self.journal_phase('locked')
            self.prepare_source()
            self.journal_phase('source-verified')
            self.install_script()
            self.journal_phase('script-installed')
            self.make_bundle()
            self.postflight()
            self.journal_phase('postflight')
            self.release()
            # Restoring timer may run renewal immediately; never restore old script.
            self.journal_phase('timer-restoring')
            self.timer_restore_attempted = True
            self.system('start', TIMER)
            require(self.unit_state() == self.p['timer'], 'TIMER_RESTORE')
            self.journal_phase('complete')
            self.timer_stopped = False
        except BaseException as exc:
            handoff = self.timer_incident_handoff() if self.timer_restore_attempted else None
            # A known in-process initial-current transition can be disconnected.
            # On crash, recover is read-only and requires incident authorization.
            if self.switched and len(self.fds) == 2:
                try:
                    require((self.runtime/'current').is_symlink() and
                            (self.runtime/'current').resolve() == self.bundle, 'CURRENT_DRIFT')
                    (self.runtime/'current').unlink()
                    sync_dir(self.runtime)
                except BaseException:
                    pass  # Journal remains incident; never claim restoration.
            with contextlib.suppress(BaseException):
                self.journal_phase('incident', str(exc) if isinstance(exc, Refused) else 'INTERNAL',
                                   timer_handoff=handoff)
            raise Refused('INCIDENT') from None
        finally:
            self.release()


def load_packet(path):
    protected(path, 0o600)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_CLOEXEC)
    try:
        def unique(pairs):
            result = {}
            for key, value in pairs:
                require(key not in result, 'PACKET_SCHEMA')
                result[key] = value
            return result
        return json.loads(stable_read(fd, path), object_pairs_hook=unique)
    finally:
        os.close(fd)


def main():
    # Fixed output only, including argparse errors and unexpected exceptions.
    try:
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
        require(os.geteuid() == os.getegid() == 0, 'ROOT_REQUIRED')
        require(len(sys.argv) == 4 and sys.argv[1] in ('preflight', 'apply', 'recover')
                and sys.argv[2] == '--packet', 'CLI')
        for rel in ('trust_transition.py', 'production_env.py'):
            protected(Path(__file__).absolute().with_name(rel))
        packet_path = Path(sys.argv[3])
        require(packet_path.parent == Path('/var/lib/pawcycle-transition'), 'PACKET_PATH')
        p = load_packet(packet_path)
        operation = Transition(p)
        cancelled = False
        def interrupted(*_):
            nonlocal cancelled
            if cancelled:
                return  # Do not interrupt child reaping / FD cleanup twice.
            cancelled = True
            raise Refused('INTERRUPTED')
        for sig in (signal.SIGINT, signal.SIGTERM):
            signal.signal(sig, interrupted)
        if sys.argv[1] == 'recover':
            protected(operation.journal, 0o600)
            state = json.loads(operation.journal.read_bytes())
            require(state['phase'] == 'complete' and state['id'] == p['id']
                    and not os.path.lexists(operation.journal.with_suffix('.new')), 'INCIDENT')
            try:
                require(operation.unit_state() == p['timer'], 'INCIDENT')
            except BaseException:
                raise Refused('INCIDENT') from None
            print('PASS:RECOVERY_RECORD_ONLY')
        elif sys.argv[1] == 'preflight':
            operation.validate_packet()
            print('PASS:PREFLIGHT')
        else:
            operation.apply()
            print('PASS:TRANSITION')
        return 0
    except Refused as exc:
        code = str(exc)
        print('FAIL:'+code)
        return 4 if code in ('INCIDENT', 'PROCESS_UNCERTAIN', 'CHILD_REMAINS') else 3 if code in (
            'INTERRUPTED', 'COMMAND_TIMEOUT', 'WINDOW_EXPIRED') else 2
    except BaseException:
        print('FAIL:INTERNAL')
        return 4


if __name__ == '__main__':
    sys.exit(main())
