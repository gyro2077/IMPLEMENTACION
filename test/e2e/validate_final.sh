#!/bin/bash
echo "=== Validación final Dashboard ==="
echo "--- Health ---"
curl -s -o /dev/null -w "target-app: %{http_code}\n" http://localhost:8081/actuator/health
curl -s -o /dev/null -w "evidence-server: %{http_code}\n" http://localhost:8082/actuator/health

echo "--- Dashboard HTML ---"
curl -s -o /dev/null -w "GET /: %{http_code}\n" http://localhost:8082/
curl -s -o /dev/null -w "GET /dashboard: %{http_code}\n" http://localhost:8082/dashboard

echo "--- Dashboard API ---"
curl -s http://localhost:8082/api/dashboard/data | python3 -c "
import json, sys
d = json.load(sys.stdin)
print('generated_at:', d.get('generated_at','MISSING'))
s = d.get('summary',{})
print('summary: hosts=' + str(s.get('total_hosts',0)), 'ready=' + str(s.get('ready',0)), 'partial=' + str(s.get('partial',0)))
ph = d.get('primary_host',{})
print('primary_host:', ph.get('host','?'), '-', ph.get('evidence_status','?'), '-', ph.get('evidence_status_label','?'))
print('scans:', len(d.get('scans',[])))
print('backups:', len(d.get('backups',[])))
print('restores:', len(d.get('restores',[])))
print('reports:', len(d.get('reports',[])))
print('timeline:', len(d.get('timeline',[])))
a = d.get('actions',{})
for k,v in a.items():
    print('  action:', k, 'enabled=' + str(v.get('enabled',False)))
"

echo "--- Legacy APIs ---"
curl -s -o /dev/null -w "GET /api/hosts: %{http_code}\n" http://localhost:8082/api/hosts
curl -s -o /dev/null -w "GET /api/evidence/overview: %{http_code}\n" http://localhost:8082/api/evidence/overview

echo "--- Static assets ---"
curl -s -o /dev/null -w "GET /dashboard.css: %{http_code}\n" http://localhost:8082/dashboard.css
curl -s -o /dev/null -w "GET /dashboard.js: %{http_code}\n" http://localhost:8082/dashboard.js

echo "=== Fin ==="
