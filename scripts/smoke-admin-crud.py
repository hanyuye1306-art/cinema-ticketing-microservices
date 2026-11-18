"""Opt-in Gateway CRUD regression. Only creates, edits and deletes its own labelled fixtures."""
import argparse
import base64
import datetime
import getpass
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid


class Regression:
    def __init__(self, args):
        self.args = args
        self.root = Path(__file__).resolve().parent.parent
        self.output = self.root / '.runtime' / 'admin-crud'
        self.output.mkdir(parents=True, exist_ok=True)
        self.prefix = 'ct' + uuid.uuid4().hex[:8]
        self.token = None
        self.entries = []
        self.results = []
        self.special_cleanup = []
        self.uploaded = []
        self.backup_path = None

    def call(self, verb, endpoint, payload=None, raw=None, content_type=None):
        headers = {'Content-Type': content_type or 'application/json'}
        if self.token:
            headers['Token'] = self.token
        body = raw if raw is not None else json.dumps(payload).encode('utf8') if payload is not None else None
        request = urllib.request.Request(self.args.base_url.rstrip('/') + '/' + endpoint.lstrip('/'), data=body, headers=headers, method=verb)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                result = json.load(response)
        except urllib.error.HTTPError as error:
            try:
                message = json.load(error).get('msg', 'HTTP ' + str(error.code))
            except (ValueError, AttributeError):
                message = 'HTTP ' + str(error.code)
            raise RuntimeError(endpoint + ': ' + message) from None
        if result.get('code') != 200:
            raise RuntimeError(endpoint + ': ' + str(result.get('msg', 'API rejected request')))
        return result

    def rows(self, endpoint):
        data = self.call('GET', endpoint)['data']
        return data.get('rows', []) if isinstance(data, dict) else data

    def record(self, name, action):
        try:
            result = action()
            self.results.append({'name': name, 'passed': True})
            print('[PASS]', name, flush=True)
            return result
        except Exception as error:
            self.results.append({'name': name, 'passed': False, 'error': str(error)})
            print('[FAIL]', name, str(error), flush=True)
            return None

    def backup(self):
        executable = shutil.which('mysqldump')
        if not executable:
            raise RuntimeError('mysqldump is required before running write tests')
        environment = os.environ.copy()
        environment['MYSQL_PWD'] = os.environ.get('MYSQL_PASSWORD', '')
        target = self.output / (self.prefix + '-before.sql')
        subprocess.run([executable, '--host=127.0.0.1', '--user=' + self.args.mysql_user,
                        '--single-transaction', '--order-by-primary', '--no-tablespaces', '--set-gtid-purged=OFF',
                        '--result-file=' + str(target), self.args.database], env=environment,
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        print('[PASS] Database backup saved locally', flush=True)
        self.backup_path = target

    def verify_original_rows(self):
        environment = os.environ.copy()
        environment['MYSQL_PWD'] = os.environ.get('MYSQL_PASSWORD', '')
        target = self.output / (self.prefix + '-after.sql')
        subprocess.run([shutil.which('mysqldump'), '--host=127.0.0.1', '--user=' + self.args.mysql_user,
                        '--single-transaction', '--order-by-primary', '--no-tablespaces', '--set-gtid-purged=OFF',
                        '--result-file=' + str(target), self.args.database], env=environment,
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        def rows(path):
            return {line for line in path.read_bytes().splitlines() if line.startswith(b'INSERT INTO')}
        if rows(self.backup_path) != rows(target):
            raise AssertionError('Database rows differ from the backup; inspect local snapshots before further writes')

    def create(self, endpoint, id_field, label_field, extra=None, label=None):
        value = label or self.prefix + endpoint[3:11]
        payload = dict(extra or {}, **{label_field: value})
        # Record the identity immediately, even when an API returns an unexpected failure after committing.
        failure = None
        try:
            self.call('POST', endpoint, payload)
        except Exception as error:
            failure = error
        matches = [row for row in self.rows(endpoint) if row.get(label_field) == value]
        if len(matches) != 1:
            raise RuntimeError(str(failure) if failure else endpoint + ': created row not uniquely readable')
        row = matches[0]
        entry = {'endpoint': endpoint, 'id_field': id_field, 'label_field': label_field, 'id': row[id_field], 'label': value, 'row': row}
        self.entries.append(entry)
        if failure:
            raise failure
        return entry

    def edit(self, entry, extra=None):
        field = entry['label_field']
        payload = dict(entry['row'], **{field: entry['label'] + 'E'})
        payload.update(extra or {})
        self.call('PUT', entry['endpoint'], payload)
        current = self.call('GET', entry['endpoint'] + '/' + str(entry['id']))['data']
        if not current or current.get(field) != payload[field]:
            raise RuntimeError(entry['endpoint'] + ': update was not persisted')
        entry['row'], entry['label'] = current, payload[field]
        return entry

    def dictionary(self, endpoint, id_field, label_field, extra=None, label=None):
        return self.record(endpoint + ' create/read/update', lambda: self.edit(self.create(endpoint, id_field, label_field, extra, label)))

    def expect_rejected(self, verb, endpoint, payload=None):
        try:
            self.call(verb, endpoint, payload)
        except RuntimeError:
            return
        raise AssertionError('Protected operation unexpectedly succeeded: ' + endpoint)

    def run(self):
        self.backup()
        password = os.environ.get('GOUYAN_PASSWORD') or getpass.getpass('Administrator password (not saved): ')
        login = self.call('POST', 'sysUser/login', {'userName': self.args.username, 'password': password})
        if login['data']['sysUser'].get('roleId') != 1:
            raise RuntimeError('Write tests require a system administrator')
        self.token = login['data']['token']
        self.record('administrator dashboard uses live totals', self.dashboard)
        fixtures = {}
        specs = [
            ('sysActorRole', 'actorRoleId', 'actorRoleName', {}),
            ('sysCinemaArea', 'cinemaAreaId', 'cinemaAreaName', {'province': 'TestProvince', 'city': 'TestCity'}),
            ('sysCinemaBrand', 'cinemaBrandId', 'cinemaBrandName', {}),
            ('sysHallCategory', 'hallCategoryId', 'hallCategoryName', {}),
            ('sysMovieAge', 'movieAgeId', 'movieAgeName', {}),
            ('sysMovieArea', 'movieAreaId', 'movieAreaName', {}),
            ('sysMovieCategory', 'movieCategoryId', 'movieCategoryName', {}),
            ('sysMovieRuntime', 'movieRuntimeId', 'movieRuntimeName', {'beginTime': '10:00', 'endTime': '12:00'}),
            ('sysRole', 'roleId', 'roleName', {'roleDesc': self.prefix + 'RoleDescription'}),
            ('sysResource', 'id', 'name', {'path': '/test-' + self.prefix, 'level': 1, 'parentId': None}),
            ('sysUserHobby', 'userHobbyId', 'userHobbyName', {}),
        ]
        for endpoint, key, field, extra in specs:
            fixtures[endpoint] = self.dictionary(endpoint, key, field, extra)

        if fixtures['sysResource']:
            child = self.dictionary('sysResource', 'id', 'name', {'path': '/test-child-' + self.prefix, 'parentId': fixtures['sysResource']['id']}, label=self.prefix + 'Child')
            if child:
                fixtures['resourceLeaf'] = self.dictionary('sysResource', 'id', 'name', {'path': '/test-leaf-' + self.prefix, 'parentId': child['id']}, label=self.prefix + 'Leaf')

        if fixtures['sysRole'] and fixtures['sysResource']:
            role, resource = fixtures['sysRole'], fixtures.get('resourceLeaf') or fixtures['sysResource']
            self.special_cleanup.append(lambda: self.call('POST', 'sysRole/' + str(role['id']), []))
            self.record('role permission assignment', lambda: self.call('POST', 'sysRole/' + str(role['id']), [resource['id']]))
            self.record('idempotent role permission assignment', lambda: self.call('POST', 'sysRole/' + str(role['id']), [resource['id']]))

        fixtures['sysUser'] = self.dictionary('sysUser', 'userId', 'userName', {
            'password': 'CrudTest2026!', 'email': self.prefix + '@example.invalid', 'phoneNumber': '',
            'sex': False, 'roleId': 2, 'userPicture': '[]', 'information': '{}'})
        fixtures['sysActor'] = self.dictionary('sysActor', 'actorId', 'actorName', {
            'actorPhoto': '[]', 'actorHeight': 175, 'actorAge': 25, 'actorGender': True,
            'actorSchool': 'TestSchool', 'actorNation': 'TestNation', 'actorBloodType': 'A',
            'actorConstellation': 'Test', 'actorNationality': 'Test', 'actorInformation': self.prefix})

        if all(fixtures.get(k) for k in ['sysUser', 'sysCinemaArea', 'sysCinemaBrand']):
            fixtures['sysCinema'] = self.dictionary('sysCinema', 'cinemaId', 'cinemaName', {
                'cinemaAreaId': fixtures['sysCinemaArea']['id'], 'cinemaBrandId': fixtures['sysCinemaBrand']['id'],
                'userId': fixtures['sysUser']['id'], 'cinemaPicture': '[]', 'isTicketChanged': True,
                'isRefunded': True, 'cinemaAddress': self.prefix, 'entryDate': '2026-11-18'})
            if fixtures['sysCinema']:
                self.record('referenced cinema brand deletion is rejected', lambda: self.expect_rejected('DELETE', 'sysCinemaBrand/' + str(fixtures['sysCinemaBrand']['id'])))
        if all(fixtures.get(k) for k in ['sysMovieAge', 'sysMovieArea']):
            fixtures['sysMovie'] = self.dictionary('sysMovie', 'movieId', 'movieNameCn', {
                'movieNameEn': self.prefix, 'movieAreaId': fixtures['sysMovieArea']['id'],
                'movieAgeId': fixtures['sysMovieAge']['id'], 'movieLength': 100, 'moviePoster': '[]',
                'moviePictures': '[]', 'releaseDate': '2026-11-18', 'movieScore': 0,
                'movieBoxOffice': 0, 'movieRateNum': 0, 'movieIntroduction': self.prefix})
        if all(fixtures.get(k) for k in ['sysCinema', 'sysHallCategory']):
            self.record('hall create/read/update', lambda: self.hall(fixtures))
        if all(fixtures.get(k) for k in ['sysMovie', 'sysActor', 'sysActorRole']):
            self.record('movie actor association create/delete', lambda: self.association(fixtures, 'actor'))
        if all(fixtures.get(k) for k in ['sysMovie', 'sysMovieCategory']):
            self.record('movie category association create/delete', lambda: self.association(fixtures, 'category'))
        if all(fixtures.get(k) for k in ['sysMovie', 'sysUser']):
            self.record('comment create/update/delete and movie rating', lambda: self.comment(fixtures))
        if all(fixtures.get(k) for k in ['sysMovie', 'sysCinema', 'sysHall', 'sysMovieRuntime']):
            self.record('session create/read/update', lambda: self.session(fixtures))
        if all(fixtures.get(k) for k in ['sysUser', 'sysSession']):
            self.record('unpaid bill create/read/update/delete', lambda: self.bill(fixtures))
        if fixtures.get('sysCinema'):
            self.record('cinema manager dashboard is scoped to their cinema', lambda: self.dashboard(fixtures))

    def dashboard(self, fixtures=None):
        administrator = self.token
        try:
            if fixtures:
                login = self.call('POST', 'sysUser/login', {'userName': fixtures['sysUser']['label'], 'password': 'CrudTest2026!'})
                self.token = login['data']['token']
            data = self.call('GET', 'sysUser/dashboard')['data']
            assert data['scope'] == ('cinema' if fixtures else 'all')
            assert data['counts']['movies'] >= 0
            if fixtures:
                assert data['counts']['cinemas'] == 1
                assert data['cinemaName'] == fixtures['sysCinema']['label']
                assert all(row['cinemaName'] == data['cinemaName'] for row in data['upcomingSessions'])
        finally:
            self.token = administrator

    def hall(self, fixtures):
        cinema = fixtures['sysCinema']['id']
        payload = {'cinemaId': cinema, 'hallId': 1, 'hallName': self.prefix + 'Hall',
                   'hallCategoryId': fixtures['sysHallCategory']['id'], 'rowStart': '1',
                   'rowNums': 3, 'seatNumsRow': 3, 'seatNums': 9,
                   'seatState': json.dumps({'1': [0]*3, '2': [0]*3, '3': [0]*3}), 'hallState': True}
        self.call('POST', 'sysHall', payload)
        entry = {'endpoint': 'sysHall', 'id_field': 'hallId', 'label_field': 'hallName', 'id': 1,
                 'label': payload['hallName'], 'cinema_id': cinema, 'row': payload}
        self.entries.append(entry)
        endpoint = 'sysHall/' + str(cinema) + '/1'
        current = self.call('GET', endpoint)['data']
        current['hallName'] += 'E'
        self.call('PUT', 'sysHall', current)
        assert self.call('GET', endpoint)['data']['hallName'] == current['hallName']
        fixtures['sysHall'] = current
        entry['row'], entry['label'] = current, current['hallName']

    def association(self, fixtures, kind):
        movie = fixtures['sysMovie']['id']
        if kind == 'actor':
            payload = {'movieId': movie, 'actorId': fixtures['sysActor']['id'], 'actorRoleId': fixtures['sysActorRole']['id']}
            delete = 'sysActorMovie/' + '/'.join(str(payload[k]) for k in ['movieId', 'actorId', 'actorRoleId'])
            self.call('POST', 'sysActorMovie', payload)
        else:
            delete = 'sysMovieToCategory/' + str(movie) + '/' + str(fixtures['sysMovieCategory']['id'])
            self.call('POST', delete)
        cleanup = lambda: self.call('DELETE', delete)
        self.special_cleanup.append(cleanup)
        cleanup()
        self.special_cleanup.remove(cleanup)

    def comment(self, fixtures):
        payload = {'movieId': fixtures['sysMovie']['id'], 'userId': fixtures['sysUser']['id'],
                   'commentTime': '2026-11-18 12:34:56', 'content': self.prefix, 'score': 4.0}
        self.call('POST', 'sysMovieComment', payload)
        cleanup = lambda: self.call('POST', 'sysMovieComment/delete', [payload])
        self.special_cleanup.append(cleanup)
        payload['content'] += 'E'; payload['score'] = 4.5
        self.call('PUT', 'sysMovieComment', payload)
        rows = self.rows('sysMovieComment/' + str(payload['movieId']))
        assert any(r['content'] == payload['content'] for r in rows)
        cleanup(); self.special_cleanup.remove(cleanup)
        movie = self.call('GET', 'sysMovie/' + str(payload['movieId']))['data']
        assert movie['movieRateNum'] == 0 and movie['movieScore'] == 0

    def session(self, fixtures):
        payload = {'cinemaId': fixtures['sysCinema']['id'], 'hallId': 1, 'movieId': fixtures['sysMovie']['id'],
                   'movieRuntimeId': fixtures['sysMovieRuntime']['id'], 'languageVersion': self.prefix,
                   'sessionDate': '2026-11-18', 'sessionPrice': 35.0, 'sessionState': True,
                   'sessionTips': self.prefix, 'sessionSeats': fixtures['sysHall']['seatState'], 'seatNums': 0}
        entry = self.create('sysSession', 'sessionId', 'languageVersion', payload, self.prefix + 'Lang')
        self.edit(entry); fixtures['sysSession'] = entry

    def bill(self, fixtures):
        payload = {'userId': fixtures['sysUser']['id'], 'sessionId': fixtures['sysSession']['id'],
                   'billState': False, 'seats': '["1排1号"]', 'billDate': '2026-11-18 12:34:56'}
        result = self.call('POST', 'sysBill', payload)
        bill = result['data']
        cleanup = lambda: self.call('DELETE', 'sysBill/' + str(bill['billId']))
        self.special_cleanup.append(cleanup)
        self.call('PUT', 'sysBill', {'sysBill': bill})
        self.record('session with an order cannot be deleted', lambda: self.expect_rejected('DELETE', 'sysSession/' + str(fixtures['sysSession']['id'])))
        assert self.call('GET', 'sysBill/' + str(bill['billId']))['data']['billState'] is False
        cleanup(); self.special_cleanup.remove(cleanup)

    def cleanup(self):
        for cleanup in reversed(self.special_cleanup):
            self.record('cleanup related fixture', cleanup)
        for entry in reversed(self.entries):
            def delete(entry=entry):
                rows = self.rows(entry['endpoint'])
                owned = next((r for r in rows if r[entry['id_field']] == entry['id']
                              and (entry['endpoint'] != 'sysHall' or r['cinemaId'] == entry['cinema_id'])), None)
                if owned is None:
                    return
                if not str(owned.get(entry['label_field'], '')).startswith(self.prefix):
                    raise RuntimeError('Refusing to delete a record without the fixture marker')
                if entry['endpoint'] == 'sysHall':
                    self.call('POST', 'sysHall/delete', [{'cinemaId': entry['cinema_id'], 'hallId': entry['id']}])
                    assert not any(r['hallId'] == entry['id'] and r['cinemaId'] == entry['cinema_id'] for r in self.rows('sysHall'))
                else:
                    self.call('DELETE', entry['endpoint'] + '/' + str(entry['id']))
                    assert not any(r[entry['id_field']] == entry['id'] for r in self.rows(entry['endpoint']))
            self.record(entry['endpoint'] + ' delete/read-back', delete)
        if self.backup_path:
            self.record('original business rows unchanged after cleanup', self.verify_original_rows)
        self.output.joinpath('last-run.json').write_text(json.dumps({
            'prefix': self.prefix, 'results': self.results,
            'created': [{k: e[k] for k in ['endpoint', 'id', 'id_field', 'label_field']} for e in self.entries]
        }, ensure_ascii=False, indent=2), encoding='utf8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write-tests', action='store_true', help='Explicitly allow labelled fixture creation and cleanup')
    parser.add_argument('--base-url', default='http://127.0.0.1:8181')
    parser.add_argument('--username', default='admin')
    parser.add_argument('--mysql-user', default='root')
    parser.add_argument('--database', default='movie')
    args = parser.parse_args()
    if not args.write_tests:
        parser.error('Use --write-tests to explicitly allow isolated fixture writes')
    runner = Regression(args)
    try:
        runner.run()
    except Exception as error:
        runner.results.append({'name': 'test setup', 'passed': False, 'error': str(error)})
        print('[FAIL] Test setup:', error, flush=True)
    finally:
        runner.cleanup()
    passed = all(item['passed'] for item in runner.results)
    print('CRUD regression:', 'PASS' if passed else 'FAIL', flush=True)
    return 0 if passed else 1


if __name__ == '__main__':
    sys.exit(main())
