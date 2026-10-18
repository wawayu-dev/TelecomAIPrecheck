#!/usr/bin/env python3
"""真实 HTTP 演示；仅使用合成 DEMO-001。配置了真实模型时会产生一次模型调用。"""
import argparse
import base64
import http.cookiejar
import json
import time
import uuid
from pathlib import Path
import urllib.request
import urllib.error

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--verify-run', help='仅验证持久化记录，不触发模型或新增审核')
parser.add_argument('--base-url', help='覆盖后端地址，例如 http://127.0.0.1:18081')
args = parser.parse_args()
settings = {}
for line in (root / 'docs/TelecomAIPrecheck/.env').read_text().splitlines():
    if line.strip() and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        settings[key] = value.strip().strip("\"'")
base = args.base_url or ('http://127.0.0.1:' + settings.get('FRONTEND_PORT', '8088'))

class Client:
    def __init__(self, role):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.auth = 'Basic ' + base64.b64encode((role + ':' + settings['DEMO_' + role.upper() + '_PASSWORD']).encode()).decode()
        self.csrf = None
    def call(self, path, body=None, expected=200):
        headers = {'Authorization': self.auth}
        if body is not None:
            headers['Content-Type'] = 'application/json'
            headers[self.csrf['csrfHeader']] = self.csrf['csrfToken']
        req = urllib.request.Request(base+path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        try:
            response = self.opener.open(req, timeout=130)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            data = json.loads(response.read())
            if response.status != expected:
                raise RuntimeError(f'Unexpected HTTP {response.status}, expected {expected}; code={data.get("code", "unknown")}')
            return data
    def session(self):
        self.csrf = self.call('/api/session')

if args.verify_run:
    run_id = args.verify_run
else:
    operator = Client('operator'); operator.session()
    transition = operator.call('/api/mock-host/tickets/DEMO-001/complaint-queue',
                               {'eventId': 'DEMO-' + str(uuid.uuid4()), 'ticketVersion': int(time.time() * 1000000)})
    assert transition['transitioned'] and transition['precheckAccepted']
    run_id = transition['precheckRunId']
    reviewer = Client('reviewer'); reviewer.session()
    deadline = time.time() + 130
    while True:
        run = reviewer.call(f'/api/ai-prechecks/{run_id}')
        if run['run']['status'] != 'ANALYZING':
            break
        if time.time() >= deadline:
            raise RuntimeError('Timed out waiting for asynchronous precheck')
        time.sleep(1)
    run_id = run['run']['id']
    body = {'expectedVersion': run['run']['version'], 'decision': 'CONFIRMED',
            'summary': '合成演示：已核对账单，授权材料仍需人工复核。',
            'recommendation': 'VERIFY_AUTHORIZATION', 'reason': '仅确认人工处理建议，不执行退费。'}
    review = reviewer.call(f'/api/ai-prechecks/{run_id}/review', body)
    assert review['actorId'] == 'reviewer'
    reviewer.call(f'/api/ai-prechecks/{run_id}/review', body, 409)
    operator.call(f'/api/ai-prechecks/{run_id}/review', body, 403)

auditor = Client('auditor')
run = auditor.call(f'/api/ai-prechecks/{run_id}')
review = auditor.call(f'/api/ai-prechecks/{run_id}/review')
events = auditor.call(f'/api/ai-prechecks/{run_id}/audit')
timeline = auditor.call(f'/api/ai-prechecks/{run_id}/timeline')
assert run['run']['status'] == 'REVIEWED' and run['run']['version'] == 2
assert review['decision'] == 'CONFIRMED'
assert len(events) >= 3 and timeline[-1]['reviewChange']['humanResult']['actorId'] == 'reviewer'
print(json.dumps({'synthetic': True, 'runId': run_id, 'status': run['run']['status'],
                  'callStatus': run['run']['callStatus'], 'failureCode': run['run']['failureCode'],
                  'decision': review['decision'], 'auditActions': [e['action'] for e in events],
                  'verification': 'persisted' if args.verify_run else 'created-and-reviewed'}, ensure_ascii=False, indent=2))
