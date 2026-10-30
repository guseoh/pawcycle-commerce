"""Private runtime parsing/projections. Never include input in diagnostics."""
import os
import re
import stat
from urllib.parse import urlsplit, parse_qs

AUTOMATION = (
    'PAWCYCLE_SUBSCRIPTION_AUTOMATION_ENABLED',
    'PAWCYCLE_SUBSCRIPTION_AUTOMATION_BATCH_SIZE',
    'PAWCYCLE_SUBSCRIPTION_AUTOMATION_FIXED_DELAY_MS',
)
RELEASE_KEYS = ('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME',
                'SPRING_DATASOURCE_PASSWORD', *AUTOMATION)
CATALOG_KEYS = ('PAWCYCLE_DATASOURCE_HOST', 'PAWCYCLE_DATASOURCE_PORT',
                'PAWCYCLE_DATASOURCE_SSL_MODE', *RELEASE_KEYS)


class Refused(Exception):
    """Messages are fixed reason codes, never data or subprocess exceptions."""


def require(condition, code):
    if not condition:
        raise Refused(code)


def parse(data, keys=CATALOG_KEYS):
    """Canonical subset: exactly one single-quoted value per allowlisted key.

    Only escaped apostrophe is accepted; no interpolation, double quotes,
    backslash ambiguity, comments, whitespace trimming, or multiline values.
    Compose interpretation is independently compared, not assumed.
    """
    try:
        text = data.decode('utf-8', errors='strict')
    except UnicodeError:
        raise Refused('ENV_ENCODING') from None
    require(not any(c in text for c in ('\x00', '\r', '\ufeff')), 'ENV_ENCODING')
    result = {}
    for line in text.splitlines():
        key, sep, raw = line.partition('=')
        require(sep and key in keys and key not in result, 'ENV_KEYS')
        require(len(raw) > 2 and raw[0] == raw[-1] == "'", 'ENV_QUOTE')
        inner = raw[1:-1]
        require(re.fullmatch(r"(?:[^'\\\x00-\x1f\x7f]|\\')+", inner) is not None,
                'ENV_ESCAPE')
        result[key] = inner.replace("\\'", "'")
    require(set(result) == set(keys), 'ENV_KEYS')
    require(result[AUTOMATION[0]] in ('true', 'false'), 'ENV_AUTOMATION')
    require(all(re.fullmatch('[1-9][0-9]*', result[k]) for k in AUTOMATION[1:]),
            'ENV_AUTOMATION')
    if set(keys) == set(CATALOG_KEYS):
        try:
            url = result['SPRING_DATASOURCE_URL']
            require(url.startswith('jdbc:mysql://'), 'ENV_DATABASE')
            parsed = urlsplit(url[5:])
            host = result['PAWCYCLE_DATASOURCE_HOST']
            port = result['PAWCYCLE_DATASOURCE_PORT']
            ssl = result['PAWCYCLE_DATASOURCE_SSL_MODE']
            require(re.fullmatch('[1-9][0-9]*', port) and int(port) <= 65535,
                    'ENV_DATABASE')
            require(parsed.hostname == host and parsed.port == int(port)
                    and parsed.username is None and parsed.password is None
                    and parsed.path not in ('', '/'), 'ENV_DATABASE')
            require(ssl in ('DISABLED', 'PREFERRED', 'REQUIRED', 'VERIFY_CA', 'VERIFY_IDENTITY'),
                    'ENV_DATABASE')
            modes = parse_qs(parsed.query).get('sslMode')
            require(modes is None or modes == [ssl], 'ENV_DATABASE')
        except (ValueError, KeyError):
            raise Refused('ENV_DATABASE') from None
    return result


def projection(entries, consumer):
    require(set(entries) == set(CATALOG_KEYS), 'ENV_KEYS')
    require(consumer in ('release', 'catalog', 'auth'), 'ENV_CONSUMER')
    keys = CATALOG_KEYS if consumer == 'catalog' else RELEASE_KEYS
    # Preserve input automation; existing Catalog/Auth streamers force false.
    return {key: entries[key] for key in keys}


def serialize(entries):
    return ''.join(k + "='" + v.replace("'", "\\'") + "'\n"
                   for k, v in entries.items()).encode('utf-8')


def signature(info):
    return (info.st_dev, info.st_ino, info.st_uid, info.st_gid,
            info.st_mode, info.st_nlink, info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def stable_read(fd, path, uid=0):
    before = os.fstat(fd)
    require(stat.S_ISREG(before.st_mode) and before.st_nlink == 1
            and before.st_uid == uid and stat.S_IMODE(before.st_mode) == 0o600,
            'ENV_IDENTITY')
    require(signature(before) == signature(os.lstat(path)), 'ENV_IDENTITY')
    require(before.st_size <= 65536, 'ENV_SIZE')
    data = os.pread(fd, 65537, 0)
    require(len(data) == before.st_size and data == os.pread(fd, 65537, 0)
            and signature(before) == signature(os.fstat(fd))
            and signature(before) == signature(os.lstat(path)), 'ENV_DRIFT')
    return data


def env_map(lines):
    result = {}
    require(isinstance(lines, list), 'ENV_EFFECTIVE')
    for line in lines:
        require(isinstance(line, str) and '=' in line, 'ENV_EFFECTIVE')
        key, value = line.split('=', 1)
        require(key and key not in result, 'ENV_EFFECTIVE')
        result[key] = value
    return result


def verify_effective(canonical, resolved, image_lines, running_lines, exclusions=()):
    require(set(exclusions) <= {'HOSTNAME'}, 'ENV_EXCLUSION')
    expected = env_map(image_lines)
    require(isinstance(resolved, dict), 'ENV_COMPOSE')
    # Resolved config includes env_file then environment, by Compose itself.
    require(all(isinstance(k, str) and isinstance(v, str) for k, v in resolved.items()),
            'ENV_COMPOSE')
    require(all(resolved.get(k) == v for k, v in canonical.items()), 'ENV_OVERRIDE')
    expected.update(resolved)
    running = env_map(running_lines)
    for key in exclusions:
        expected.pop(key, None)
        running.pop(key, None)
    require(expected == running, 'ENV_EFFECTIVE')
