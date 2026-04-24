/* === Evidence as Code — Dashboard Admin JS === */

let D = null; // dashboard data

document.addEventListener('DOMContentLoaded', () => {
    initTabs();
    loadData();
});

/* ── Tab Management ── */
function initTabs() {
    document.querySelectorAll('#tab-nav .tab').forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });
    const hash = location.hash.replace('#', '');
    if (hash && document.getElementById('tab-' + hash)) {
        switchTab(hash);
    }
}

function switchTab(name) {
    document.querySelectorAll('.tab-content').forEach(s => s.style.display = 'none');
    document.querySelectorAll('#tab-nav .tab').forEach(b => b.classList.remove('active'));
    const section = document.getElementById('tab-' + name);
    const btn = document.querySelector(`[data-tab="${name}"]`);
    if (section) section.style.display = 'block';
    if (btn) btn.classList.add('active');
    history.replaceState(null, '', '#' + name);
}

/* ── Data Loading ── */
async function loadData() {
    const loading = document.getElementById('loading');
    const errorBox = document.getElementById('error-box');
    loading.style.display = 'block';
    errorBox.style.display = 'none';

    try {
        const resp = await fetch('/api/dashboard/data');
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        D = await resp.json();
        loading.style.display = 'none';
        render();
    } catch (err) {
        loading.style.display = 'none';
        document.getElementById('error-msg').textContent = err.message;
        errorBox.style.display = 'block';
    }
}

/* ── Master Render ── */
function render() {
    renderHeader();
    renderKPIs();
    renderPrimaryHost();
    renderTimeline();
    renderScans();
    renderBackups();
    renderRestores();
    renderReports();
    renderActionBar();
    // Show first tab
    const hash = location.hash.replace('#', '');
    switchTab(hash || 'resumen');
}

/* ── Header ── */
function renderHeader() {
    const ph = D.primary_host || {};
    const status = ph.evidence_status || 'empty';
    const label = ph.evidence_status_label || 'Cargando';
    document.getElementById('env-badge').className = 'badge badge-' + status;
    document.getElementById('env-badge').textContent = label;
    document.getElementById('header-time').textContent = fmtDate(D.generated_at);
}

/* ── KPIs ── */
function renderKPIs() {
    const s = D.summary || {};
    const ph = D.primary_host || {};
    const score = ph.latest_compliance_score || '-';
    const rto = ph.latest_restore_rto_seconds || '-';
    const bkLabel = ph.latest_backup_label || '-';

    document.getElementById('kpi-grid').innerHTML = [
        kpi('Hosts Auditados', s.total_hosts, 'Registrados en el sistema', 'kpi-neutral'),
        kpi('Score Seguridad', score !== '-' ? score + '%' : '-', 'Cumplimiento CIS', parseFloat(score) >= 70 ? 'kpi-ready' : 'kpi-partial'),
        kpi('Último Backup', bkLabel !== '-' ? '✓' : '-', bkLabel !== '-' ? shortLabel(bkLabel) : 'Sin respaldo', 'kpi-ready'),
        kpi('Tiempo RTO', rto !== '-' ? rto + 's' : '-', 'Recovery Time Objective', rto !== '-' && parseInt(rto) <= 60 ? 'kpi-ready' : 'kpi-partial'),
        kpi('Reportes', (D.reports || []).length, 'Generados con Jasper', 'kpi-neutral'),
    ].join('');
}

function kpi(label, value, detail, cls) {
    return `<div class="kpi-card ${cls}">
        <div class="kpi-label">${esc(label)}</div>
        <div class="kpi-value">${esc(String(value))}</div>
        <div class="kpi-detail">${esc(detail)}</div>
    </div>`;
}

/* ── Primary Host ── */
function renderPrimaryHost() {
    const ph = D.primary_host || {};
    const status = ph.evidence_status || 'empty';
    const label = ph.evidence_status_label || 'Sin datos';
    const msg = ph.evidence_status_message || '';
    const score = ph.latest_compliance_score;
    const bkStatus = ph.latest_backup_status || '-';
    const bkLabel = ph.latest_backup_label || '-';
    const rto = ph.latest_restore_rto_seconds;
    const smokePassed = ph.latest_restore_smoke_passed;
    const smokeTotal = ph.latest_restore_smoke_total;

    document.getElementById('primary-host-card').innerHTML = `
        <h2 class="card-title">${esc(ph.host || 'Sin host')}</h2>
        <div class="host-status-row">
            <span class="badge badge-${status}">${esc(label)}</span>
        </div>
        <p class="host-msg">${esc(msg)}</p>
        <div class="host-kpis">
            <div class="host-kpi">
                <div class="host-kpi-label">Puntaje de Seguridad</div>
                <div class="host-kpi-value">${score ? esc(String(score)) + ' / 100' : 'Sin escaneo'}</div>
            </div>
            <div class="host-kpi">
                <div class="host-kpi-label">Último Respaldo</div>
                <div class="host-kpi-value">${bkLabel !== '-' ? esc(bkStatus) + ' (' + esc(shortLabel(bkLabel)) + ')' : 'Sin respaldo'}</div>
            </div>
            <div class="host-kpi">
                <div class="host-kpi-label">Prueba de Recuperación</div>
                <div class="host-kpi-value">${rto ? 'RTO ' + esc(String(rto)) + 's' : 'Sin restore'}${smokePassed != null ? ' — ' + smokePassed + '/' + smokeTotal + ' pruebas' : ''}</div>
            </div>
        </div>
    `;
}

/* ── Timeline ── */
function renderTimeline() {
    const tl = D.timeline || [];
    const el = document.getElementById('timeline');
    const empty = document.getElementById('timeline-empty');

    if (tl.length === 0) {
        el.innerHTML = '';
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';

    const iconMap = { scan: '🔍', backup: '💾', restore: '🔄', report: '📄' };
    const classMap = { scan: 'tl-scan', backup: 'tl-backup', restore: 'tl-restore', report: 'tl-report' };

    el.innerHTML = tl.map(e => `<li>
        <div class="tl-icon ${classMap[e.type] || ''}">${iconMap[e.type] || '●'}</div>
        <div class="tl-body">
            <div class="tl-title">${esc(e.title)}</div>
            <div class="tl-detail">${esc(e.host)} — ${esc(e.details)}</div>
        </div>
        <div class="tl-time">${fmtDateShort(e.time)}</div>
    </li>`).join('');
}

/* ── Scans ── */
function renderScans() {
    const scans = D.scans || [];
    const el = document.getElementById('scans-table');
    if (scans.length === 0) {
        el.innerHTML = '<p class="empty-msg">No hay escaneos registrados.</p>';
        return;
    }
    el.innerHTML = `<table class="data-table">
        <thead><tr><th>Host</th><th>Puntaje</th><th>Perfil</th><th>Hallazgos</th><th>Estado</th><th>Fecha</th></tr></thead>
        <tbody>${scans.map(s => {
            const topFailed = s.top_failed_rules || [];
            const statusCls = s.status === 'completed' ? 'badge-ready' : 'badge-problem';
            return `<tr>
                <td><strong>${esc(s.host)}</strong></td>
                <td><strong>${esc(String(s.score))}</strong></td>
                <td class="text-mono">${esc(shortProfile(s.profile_id))}</td>
                <td>${topFailed.length} reglas fallidas</td>
                <td><span class="badge ${statusCls}">${esc(s.status)}</span></td>
                <td class="text-mono">${fmtDateShort(s.created_at)}</td>
            </tr>`;
        }).join('')}</tbody></table>`;
}

/* ── Backups ── */
function renderBackups() {
    const bks = D.backups || [];
    const el = document.getElementById('backups-table');
    if (bks.length === 0) {
        el.innerHTML = '<p class="empty-msg">No hay respaldos registrados.</p>';
        return;
    }
    el.innerHTML = `<table class="data-table">
        <thead><tr><th>Host</th><th>Etiqueta</th><th>Estado</th><th>Tamaño</th><th>Duración</th></tr></thead>
        <tbody>${bks.map(b => {
            const cls = String(b.status).includes('fail') ? 'badge-problem' : 'badge-ready';
            const sizeMB = b.size_bytes ? (parseInt(b.size_bytes) / 1048576).toFixed(1) + ' MB' : '-';
            return `<tr>
                <td>${esc(b.host)}</td>
                <td class="text-mono">${esc(b.backup_label)}</td>
                <td><span class="badge ${cls}">${esc(b.status)}</span></td>
                <td>${sizeMB}</td>
                <td>${b.duration_seconds ? b.duration_seconds + 's' : '-'}</td>
            </tr>`;
        }).join('')}</tbody></table>`;
}

/* ── Restores ── */
function renderRestores() {
    const rs = D.restores || [];
    const el = document.getElementById('restores-table');
    if (rs.length === 0) {
        el.innerHTML = '<p class="empty-msg">No hay restauraciones registradas.</p>';
        return;
    }
    el.innerHTML = `<table class="data-table">
        <thead><tr><th>Host</th><th>Backup</th><th>Estado</th><th>RTO</th><th>Pruebas</th><th>Filas</th></tr></thead>
        <tbody>${rs.map(r => {
            const cls = String(r.status).includes('fail') ? 'badge-problem' : 'badge-ready';
            return `<tr>
                <td>${esc(r.host)}</td>
                <td class="text-mono">${esc(r.backup_label)}</td>
                <td><span class="badge ${cls}">${esc(r.status)}</span></td>
                <td><strong>${r.rto_seconds || '-'}s</strong></td>
                <td>${r.smoke_passed ?? '-'} / ${r.smoke_total ?? '-'}</td>
                <td>${r.rows_restored ?? '-'} / ${r.rows_expected ?? '-'}</td>
            </tr>`;
        }).join('')}</tbody></table>`;
}

/* ── Reports ── */
function renderReports() {
    const reps = D.reports || [];
    const el = document.getElementById('reports-table');
    const empty = document.getElementById('reports-empty');

    if (reps.length === 0) {
        el.innerHTML = '';
        empty.style.display = 'block';
        return;
    }
    empty.style.display = 'none';

    el.innerHTML = `<table class="data-table">
        <thead><tr><th>Fecha</th><th>Host</th><th>Estado</th><th>Acción</th></tr></thead>
        <tbody>${reps.map(r => {
            const done = r.status === 'COMPLETED';
            const cls = done ? 'badge-ready' : r.status === 'FAILED' ? 'badge-problem' : 'badge-partial';
            const action = done
                ? `<a href="/api/reports/${r.id}/download" class="btn btn-primary btn-sm">Descargar PDF</a>`
                : `<span class="badge ${cls}">${esc(r.status)}</span>`;
            return `<tr>
                <td>${fmtDateShort(r.created_at)}</td>
                <td>${esc(r.host_name)}</td>
                <td><span class="badge ${cls}">${esc(r.status)}</span></td>
                <td>${action}</td>
            </tr>`;
        }).join('')}</tbody></table>`;
}

/* ── Action Bar ── */
const ACTION_HANDLERS = {
    generate_report: 'generateReport()',
    reload_dataset: 'reloadDataset()',
    run_scan: 'runScan()',
    run_backup: 'runBackup()',
    run_restore: 'runRestore()'
};

function renderActionBar() {
    const actions = D.actions || {};
    const el = document.getElementById('action-buttons');
    let html = '';

    for (const [key, action] of Object.entries(actions)) {
        if (action.url) {
            html += `<a href="${esc(action.url)}" target="_blank" class="btn ${action.enabled ? 'btn-secondary' : 'btn-disabled'}">${esc(action.label)}</a>`;
        } else if (action.enabled && ACTION_HANDLERS[key]) {
            html += `<button class="btn btn-primary" data-action="${esc(key)}" onclick="${ACTION_HANDLERS[key]}">${esc(action.label)}</button>`;
        } else if (action.enabled) {
            html += `<button class="btn btn-primary" disabled>${esc(action.label)}</button>`;
        } else {
            html += `<button class="btn btn-disabled" disabled title="${esc(action.reason || '')}">${esc(action.label)}</button>`;
        }
    }
    el.innerHTML = html;
}

/* ── Actions ── */
async function generateReport() {
    const ph = D.primary_host || {};
    const host = ph.host;
    if (!host || host === 'sin-host') {
        showToast('No hay host disponible para generar reporte.', 'error');
        return;
    }

    showToast('Generando reporte PDF para ' + host + '…', 'info');
    const btn = document.getElementById('btn-gen-report');
    if (btn) { btn.disabled = true; btn.textContent = 'Generando…'; }

    try {
        const resp = await fetch('/api/reports/evidence/' + encodeURIComponent(host), { method: 'POST' });
        if (resp.ok) {
            showToast('¡Reporte generado exitosamente!', 'success');
            setTimeout(() => loadData(), 1500);
        } else {
            const body = await resp.text();
            showToast('Error al generar reporte: ' + (body || resp.status), 'error');
        }
    } catch (err) {
        showToast('Error de conexión: ' + err.message, 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'Generar Nuevo Reporte'; }
    }
}

async function reloadDataset() {
    showToast('Recargando dataset de demostración…', 'info');
    try {
        const resp = await fetch('/api/actions/reload-dataset', { method: 'POST' });
        const body = await resp.json();
        if (resp.ok && body.status === 'COMPLETED') {
            showToast('Dataset recargado exitosamente. Actualizando panel…', 'success');
            setTimeout(() => loadData(), 1500);
        } else {
            showToast('Error al recargar: ' + (body.message || resp.status), 'error');
        }
    } catch (err) {
        showToast('Error de conexión: ' + err.message, 'error');
    }
}

function runScan() {
    return runAction('run_scan', 'scan de cumplimiento');
}

function runBackup() {
    return runAction('run_backup', 'backup');
}

function runRestore() {
    return runAction('run_restore', 'restore');
}

async function runAction(actionKey, label) {
    const actions = (D && D.actions) || {};
    const action = actions[actionKey];
    if (!action || !action.endpoint) {
        showToast('Acción no disponible en este entorno.', 'error');
        return;
    }

    const btn = document.querySelector(`[data-action="${actionKey}"]`);
    const originalText = btn ? btn.textContent : '';
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Ejecutando…';
    }

    showToast(`Ejecutando ${label}…`, 'info');
    try {
        const resp = await fetch(action.endpoint, { method: action.method || 'POST' });
        if (!resp.ok) {
            const text = await resp.text();
            throw new Error(text || `HTTP ${resp.status}`);
        }
        const body = await resp.json();
        if (!body.id) {
            throw new Error('La acción no devolvió job id.');
        }
        showToast(`Job ${shortJobId(body.id)} en cola para ${label}…`, 'info');
        await pollActionStatus(body.id, label);
        await loadData();
    } catch (err) {
        showToast(`Falló ${label}: ${err.message}`, 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = originalText || 'Ejecutar';
        }
    }
}

async function pollActionStatus(jobId, label) {
    const maxAttempts = 180;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
        await sleep(attempt === 0 ? 600 : 1500);
        const resp = await fetch(`/api/actions/${encodeURIComponent(jobId)}/status`);
        if (!resp.ok) {
            throw new Error(`No se pudo consultar job ${shortJobId(jobId)} (HTTP ${resp.status})`);
        }

        const body = await resp.json();
        const status = String(body.status || '').toUpperCase();
        if (status === 'PENDING') {
            showToast(`Job ${shortJobId(jobId)} pendiente para ${label}…`, 'info');
            continue;
        }
        if (status === 'RUNNING') {
            showToast(`Ejecutando ${label}…`, 'info');
            continue;
        }
        if (status === 'COMPLETED') {
            showToast(`Acción completada: ${label} (${shortJobId(jobId)})`, 'success');
            return body;
        }
        if (status === 'FAILED') {
            throw new Error(body.error_message || `job ${shortJobId(jobId)} falló`);
        }
        throw new Error(`Estado no soportado para job ${shortJobId(jobId)}: ${status || '-'}`);
    }

    throw new Error(`Timeout esperando job ${shortJobId(jobId)}`);
}

/* ── Toast ── */
let toastTimer = null;
function showToast(message, type) {
    const t = document.getElementById('toast');
    t.textContent = message;
    t.className = 'toast toast-' + (type || 'info') + ' show';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { t.classList.remove('show'); }, 4000);
}

/* ── Utilities ── */
function esc(s) {
    if (s == null) return '';
    const d = document.createElement('div');
    d.textContent = String(s);
    return d.innerHTML;
}

function fmtDate(iso) {
    if (!iso) return '-';
    try {
        return new Date(iso).toLocaleString('es-CL', { dateStyle: 'medium', timeStyle: 'short' });
    } catch { return iso; }
}

function fmtDateShort(iso) {
    if (!iso || iso === '-') return '-';
    try {
        const s = String(iso);
        if (s.length >= 16) return s.substring(0, 16).replace('T', ' ');
        return s;
    } catch { return iso; }
}

function shortLabel(label) {
    if (!label || label === '-') return '-';
    return label.length > 20 ? label.substring(0, 20) + '…' : label;
}

function shortJobId(id) {
    if (!id) return '-';
    return String(id).slice(0, 8);
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

function shortProfile(profileId) {
    if (!profileId || profileId === '-') return '-';
    const parts = profileId.split('_');
    return parts.length > 3 ? parts.slice(-3).join('_') : profileId;
}
