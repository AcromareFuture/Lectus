"""Run on the server as root. Never emits account values or credentials."""
import base64
import json
import os
import shlex
import subprocess
import time

BASE = '/opt/lectus-saas'

def sql(query):
    p = subprocess.run(['runuser', '-u', 'postgres', '--', 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-d', 'lectus_saas_member'], input=query, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
    if p.returncode:
        raise RuntimeError('Database operation failed; no changes committed')
    return p.stdout.strip()

def quote(value):
    return 'NULL' if value is None else "'" + str(value).replace("'", "''") + "'"

tables = {}
lines = open(BASE + '/config/legacy-members.tsv').read().splitlines()
i = 0
while i < len(lines):
    name = lines[i].split('\t')[1]
    columns = lines[i + 1].split('\t')
    i += 2
    rows = []
    while i < len(lines) and not lines[i].startswith('TABLE\t'):
        rows.append(dict(zip(columns, [None if v == '~' else base64.b64decode(v).decode('utf-8') for v in lines[i].split('\t')])))
        i += 1
    tables[name] = rows

backup = BASE + '/backups/member-before-legacy-' + time.strftime('%Y%m%dT%H%M%SZ', time.gmtime()) + '.sql'
with open(backup, 'w') as target:
    os.chmod(backup, 0o600)
    subprocess.check_call(['runuser', '-u', 'postgres', '--', 'pg_dump', 'lectus_saas_member'], stdout=target)

before = json.loads(sql("SELECT COALESCE(json_agg(t),'[]') FROM (SELECT * FROM v_member) t;"))
identities = json.loads(sql("SELECT COALESCE(json_agg(t),'[]') FROM v_member_phone_identity t;"))
next_id = max([int(v['member_id']) for v in before] + [0]) + 1
mapping = {}
statements = ['BEGIN;', 'LOCK TABLE v_member, v_member_phone_identity IN EXCLUSIVE MODE;']
inserted = 0
renamed = {}
for row in tables['v_member']:
    matches = [v for v in before if v['username'] == row['username']]
    if matches:
        if len(matches) != 1:
            raise RuntimeError('Ambiguous account identity')
        if matches[0]['password'] == row['password']:
            new_id = matches[0]['member_id']
        else:
            # Keep the original login name on the older account. The new account
            # and every order retain their IDs, password hashes and ownership.
            existing = matches[0]
            assert str(existing['create_time'])[:10] > str(row['create_time'])[:10]
            alias = row['username'][:38] + '_new_' + str(existing['member_id'])
            assert not any(v['username'] == alias for v in before)
            statements.append('UPDATE v_member SET username=' + quote(alias) + ' WHERE member_id=' + str(existing['member_id']) + ';')
            renamed[existing['member_id']] = alias
            new_id = next_id
            next_id += 1
            values = dict(row, member_id=str(new_id))
            statements.append('INSERT INTO v_member (' + ','.join(values) + ') VALUES (' + ','.join(quote(v) for v in values.values()) + ');')
            inserted += 1
    else:
        new_id = next_id
        next_id += 1
        values = dict(row, member_id=str(new_id))
        statements.append('INSERT INTO v_member (' + ','.join(values) + ') VALUES (' + ','.join(quote(v) for v in values.values()) + ');')
        inserted += 1
    mapping[row['member_id']] = str(new_id)

for table, rows in tables.items():
    if table == 'v_member':
        continue
    for row in rows:
        row = dict(row, member_id=mapping[row['member_id']])
        if table == 'v_member_phone_identity':
            conflicts = [v for v in identities if v['phone'] == row['phone'] or str(v['member_id']) == row['member_id']]
            if conflicts:
                if any(v['phone'] != row['phone'] or str(v['member_id']) != row['member_id'] for v in conflicts):
                    raise RuntimeError('Verified phone identity conflict; restore stopped')
                continue
        else:
            # Child data requires a separate idempotent migration when present.
            raise RuntimeError('Nonempty legacy child table requires explicit merge: ' + table)
        statements.append('INSERT INTO ' + table + ' (' + ','.join(row) + ') VALUES (' + ','.join(quote(v) for v in row.values()) + ');')
statements += ["SELECT setval(pg_get_serial_sequence('v_member','member_id'),GREATEST((SELECT MAX(member_id) FROM v_member),1));", 'COMMIT;']
sql('\n'.join(statements))
after = json.loads(sql("SELECT COALESCE(json_agg(t),'[]') FROM v_member t;"))
for row in tables['v_member']:
    restored = next(v for v in after if str(v['member_id']) == mapping[row['member_id']])
    assert restored['password'] == row['password']
    assert restored['username'] == row['username']
for existing in before:
    expected = dict(existing)
    if existing['member_id'] in renamed:
        expected['username'] = renamed[existing['member_id']]
    assert next(v for v in after if v['member_id'] == existing['member_id']) == expected
with open(BASE + '/config/legacy-member-mapping.json', 'w') as target:
    os.chmod(target.name, 0o600)
    json.dump({'ids': mapping, 'renamedNewAccounts': renamed}, target)
print('Legacy accounts verified:', len(mapping), 'inserted:', inserted, 'existing accounts preserved:', len(before))

def envfile(path):
    result = {}
    for line in open(path):
        if '=' in line and not line.startswith('#'):
            k, v = line.strip().split('=', 1)
            parsed = shlex.split(v)
            result[k] = parsed[0] if parsed else ''
    return result

old = envfile('/opt/lectus/config/member.env')
path = BASE + '/config/member.env'
current = envfile(path)
raw = [s.strip() for s in open(BASE + '/config/ram-source.txt') if s.strip()]
assert len(raw) == 2 and raw[0].startswith('LTAI')
assert old['VIRIDAC_SMS_TEMPLATE_CODE'] == '100001'
current.update({
    'VIRIDAC_SMS_ENABLED': 'true', 'VIRIDAC_SMS_PROVIDER': 'pnvs',
    'VIRIDAC_SMS_SIGN_NAME': '北京恒创联众', 'VIRIDAC_SMS_TEMPLATE_CODE': old['VIRIDAC_SMS_TEMPLATE_CODE'],
    'VIRIDAC_SMS_CODE_SECRET': old['VIRIDAC_SMS_CODE_SECRET'],
    'VIRIDAC_SMS_SCHEME_PREFIX': 'Lectus',
    'ALIBABA_CLOUD_ACCESS_KEY_ID': raw[0], 'ALIBABA_CLOUD_ACCESS_KEY_SECRET': raw[1]
})
import shutil
shutil.copy2(path, BASE + '/backups/member-before-sms.env')
with open(path + '.new', 'w') as target:
    os.chmod(path + '.new', 0o600)
    for k, v in current.items():
        assert '\n' not in v and '\r' not in v
        target.write(k + '=' + '"' + v.replace('\\','\\\\').replace('"','\\"') + '"\n')
os.replace(path + '.new', path)
print('SMS configuration installed; provider delivery still to be tested')
