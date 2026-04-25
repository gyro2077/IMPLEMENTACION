/* === Evidence as Code — Dashboard Admin JS === */

let D = null;
let toastTimer = null;
let activeTabFrame = null;

const TOOLTIP_TERMS = [
    { term: 'RTO', tooltip: 'Recovery Time Objective: tiempo objetivo para recuperar un servicio tras una falla.' },
    { term: 'OpenSCAP', tooltip: 'Framework de evaluación de cumplimiento y hardening basado en políticas de seguridad.' },
    { term: 'pgBackRest', tooltip: 'Herramienta de respaldo y recuperación para PostgreSQL orientada a confiabilidad operativa.' },
    { term: 'JasperReports', tooltip: 'Motor de reportes usado para generar documentos PDF ejecutivos.' },
    { term: 'OpenTelemetry', tooltip: 'Estándar para capturar trazas, métricas y telemetría distribuida.' },
    { term: 'Prometheus', tooltip: 'Sistema de monitoreo orientado a métricas y series temporales.' },
    { term: 'Jaeger', tooltip: 'Plataforma de trazas distribuidas para seguir solicitudes entre servicios.' },
    { term: 'Loki', tooltip: 'Backend de agregación y consulta de logs centralizados.' }
];

document.addEventListener('DOMContentLoaded', () => {
    initTabs();
    injectStaticTooltips();
    loadData();
});

/* ── Tab Management ── */
function initTabs() {
    document.querySelectorAll('#tab-nav .tab').forEach(btn => {
        btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });

    document.querySelectorAll('.tab-content').forEach(section => {
        section.hidden = true;
        section.style.display = 'none';
        section.classList.remove('is-visible');
    });

    const hash = location.hash.replace('#', '');
    if (hash && document.getElementById('tab-' + hash)) {
        switchTab(hash);
    }
}

function switchTab(name) {
    const sections = document.querySelectorAll('.tab-content');
    const buttons = document.querySelectorAll('#tab-nav .tab');
    const section = document.getElementById('tab-' + name);
    const btn = document.querySelector(`[data-tab="${name}"]`);

    sections.forEach(s => {
        s.classList.remove('is-visible');
        s.hidden = true;
        s.style.display = 'none';
    });
    buttons.forEach(b => b.classList.remove('active'));

    if (section) {
        section.hidden = false;
        section.style.display = 'block';
        section.classList.remove('is-visible');
        void section.offsetWidth;
        if (activeTabFrame) cancelAnimationFrame(activeTabFrame);
        activeTabFrame = requestAnimationFrame(() => {
            section.classList.add('is-visible');
        });
    }
    if (btn) btn.classList.add('active');
    history.replaceState(null, '', '#' + name);
}

/* ── Data Loading ── */
async function loadData() {
    const loading = document.getElementById('loading');
    const errorBox = document.getElementById('error-box');
    loading.style.display = 'grid';
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

    const hash = location.hash.replace('#', '');
    switchTab(hash || 'resumen');
    enhanceDashboardUi();
}

function enhanceDashboardUi() {
    injectStaticTooltips();
    animateKpiMetrics();
}

/* ── Header ── */
function renderHeader() {
    const ph = D.primary_host || {};
    const status = ph.evidence_status || 'empty';
    const label = ph.evidence_status_label || 'Cargando';
    document.body.dataset.envStatus = status;

    const header = document.getElementById('app-header');
    if (header) header.setAttribute('data-env-status', status);

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
    const scoreNum = parseFloat(score);
    const rtoNum = parseInt(rto, 10);

    document.getElementById('kpi-grid').innerHTML = [
        kpiRing(
            'Security Score',
            score !== '-' ? scoreNum : null,
            'Cumplimiento CIS consolidado',
            score !== '-' ? (scoreNum >= 70 ? 'kpi-ready' : 'kpi-partial') : 'kpi-neutral',
            [
                pillIcon('shield', 'Compliance posture'),
                score !== '-' ? `${esc(String(score))}% score` : 'Sin escaneo',
                'CIS baseline'
            ]
        ),
        kpi(
            'Hosts Auditados',
            String(s.total_hosts ?? 0),
            'Registrados en el sistema',
            'kpi-neutral',
            iconSvg('inventory'),
            { countTo: Number(s.total_hosts ?? 0) }
        ),
        kpi(
            'Último Backup',
            bkLabel !== '-' ? 'OK' : '--',
            bkLabel !== '-' ? shortLabel(bkLabel) : 'Sin respaldo',
            bkLabel !== '-' ? 'kpi-ready' : 'kpi-partial',
            iconSvg('backup')
        ),
        kpi(
            'Tiempo RTO',
            rto !== '-' ? rto + 's' : '--',
            'Recovery Time Objective (RTO)',
            rto !== '-' && rtoNum <= 60 ? 'kpi-ready' : rto !== '-' ? 'kpi-partial' : 'kpi-neutral',
            iconSvg('restore'),
            rto !== '-' ? { countTo: rtoNum, suffix: 's' } : null
        ),
        kpi(
            'Reportes',
            String((D.reports || []).length),
            'Generados con JasperReports',
            'kpi-neutral',
            iconSvg('report'),
            { countTo: Number((D.reports || []).length) }
        )
    ].join('');
}

function kpiRing(label, value, detail, cls, pills) {
    const normalized = Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : null;
    return `<div class="kpi-ring-card ${cls}">
        <div class="kpi-ring-visual">
            <svg class="kpi-ring" viewBox="0 0 120 120" aria-hidden="true">
                <circle class="kpi-ring-track" cx="60" cy="60" r="46"></circle>
                <circle class="kpi-ring-progress" cx="60" cy="60" r="46"${normalized == null ? '' : ` data-score="${normalized.toFixed(2)}"`}></circle>
            </svg>
            <div class="kpi-ring-center">
                <div class="kpi-ring-value ${normalized == null ? '' : 'kpi-animate-number'}"${normalized == null ? '' : ` data-count-to="${Math.round(normalized)}"`}>${normalized == null ? '--' : '0'}</div>
                <div class="kpi-ring-suffix">${normalized == null ? 'No Data' : 'Score'}</div>
            </div>
        </div>
        <div class="kpi-ring-copy">
            <div class="kpi-label">${esc(label)}</div>
            <div class="kpi-ring-title">${normalized == null ? 'Sin escaneo reciente' : esc(detail)}</div>
            <div class="kpi-ring-desc">${withTooltips(normalized == null ? 'No existe un resultado de cumplimiento disponible para este host.' : 'Postura consolidada del control primario con medición CIS.')}</div>
            <div class="metric-pill-row">${(pills || []).map(p => `<span class="metric-pill ${clsToStatus(cls)}">${p}</span>`).join('')}</div>
        </div>
    </div>`;
}

function kpi(label, value, detail, cls, icon, animation) {
    const animated = animation && Number.isFinite(animation.countTo);
    return `<div class="kpi-card ${cls}">
        <div class="kpi-card-head">
            <div class="kpi-label">${esc(label)}</div>
            <div class="kpi-card-accent">${icon || ''}</div>
        </div>
        <div class="kpi-value ${animated ? 'kpi-animate-number' : ''}"${animated ? counterAttrs(animation) : ''}>${animated ? formatCounter(0, animation) : esc(String(value))}</div>
        <div class="kpi-card-foot">
            <div class="kpi-detail">${withTooltips(detail)}</div>
            <div class="kpi-meta">${kpiMeta(label, value, detail)}</div>
        </div>
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
    const complianceState = score != null ? (parseFloat(score) >= 70 ? 'ready' : 'partial') : 'problem';
    const backupState = bkLabel !== '-' ? (String(bkStatus).includes('fail') ? 'problem' : 'ready') : 'partial';
    const restoreState = rto ? (parseInt(rto, 10) <= 60 ? 'ready' : 'partial') : 'problem';
    const restoreDetail = `${rto ? 'RTO ' + String(rto) + 's' : 'Sin restore'}${smokePassed != null ? ' / ' + smokePassed + '/' + smokeTotal + ' pruebas' : ''}`;

    document.getElementById('primary-host-card').innerHTML = `
        <div class="host-card-shell">
            <div class="host-card-top">
                <div class="host-identity">
                    <div class="host-label">Primary asset</div>
                    <h2 class="host-name">${esc(ph.host || 'Sin host')}</h2>
                    <div class="host-meta-row">
                        <span class="host-tech">${esc(ph.host || 'sin-host')}</span>
                        <span class="metric-pill status-${clsToStatus('kpi-' + status)}">${pillIcon('shield', label)}</span>
                    </div>
                </div>
                <div class="host-status-row">
                    <span class="badge badge-${status}">${esc(label)}</span>
                </div>
            </div>
            <p class="host-msg">${withTooltips(msg)}</p>
            <div class="host-kpis">
                ${hostMetric(
                    'Puntaje de Seguridad',
                    score ? `${score} / 100` : 'Sin escaneo',
                    score ? 'Última evaluación CIS consolidada' : 'Esperando evidencia de cumplimiento',
                    complianceState,
                    'shield'
                )}
                ${hostMetric(
                    'Último Respaldo',
                    bkLabel !== '-' ? `${bkStatus} (${shortLabel(bkLabel)})` : 'Sin respaldo',
                    bkLabel !== '-' ? bkLabel : 'Aún no existe respaldo validado',
                    backupState,
                    'backup'
                )}
                ${hostMetric(
                    'Prueba de Recuperación',
                    restoreDetail,
                    smokePassed != null ? `${smokePassed}/${smokeTotal} smoke tests validados` : 'Sin simulacro reciente',
                    restoreState,
                    'restore'
                )}
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

    const classMap = { scan: 'tl-scan', backup: 'tl-backup', restore: 'tl-restore', report: 'tl-report' };

    el.innerHTML = tl.map(e => `<li>
        <div class="timeline-marker ${classMap[e.type] || ''}">${timelineIcon(e.type)}</div>
        <div class="timeline-event">
            <div class="tl-body">
                <div class="tl-title">${esc(e.title)}</div>
                <div class="tl-detail">${withTooltips(`${e.host} — ${e.details}`)}</div>
            </div>
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
            const sizeMB = b.size_bytes ? (parseInt(b.size_bytes, 10) / 1048576).toFixed(1) + ' MB' : '-';
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
    if (btn) {
        btn.disabled = true;
        btn.textContent = 'Generando…';
    }

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
        if (btn) {
            btn.disabled = false;
            btn.textContent = 'Generar Nuevo Reporte';
        }
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

    showProgressModal({
        title: `Ejecutando ${capitalizeLabel(label)}…`,
        detail: 'Solicitando ejecución al plano de control del sistema.',
        state: 'pending'
    });

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
        showToast(`Acción completada: ${label} (${shortJobId(body.id)})`, 'success');
        await hideProgressModal(900);
    } catch (err) {
        updateProgressModal({
            title: `${capitalizeLabel(label)} falló`,
            detail: err.message,
            state: 'error'
        });
        showToast(`Falló ${label}: ${err.message}`, 'error');
        await hideProgressModal(1400);
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
        const shortId = shortJobId(jobId);

        if (status === 'PENDING') {
            updateProgressModal({
                title: `${capitalizeLabel(label)} en cola`,
                detail: body.message || 'El job fue aceptado y está pendiente de ejecución.',
                state: 'pending',
                jobId: shortId
            });
            continue;
        }

        if (status === 'RUNNING') {
            updateProgressModal({
                title: `${capitalizeLabel(label)} en ejecución`,
                detail: body.message || `Procesando ${label} sobre la infraestructura objetivo.`,
                state: 'running',
                jobId: shortId
            });
            continue;
        }

        if (status === 'COMPLETED') {
            updateProgressModal({
                title: `${capitalizeLabel(label)} completado`,
                detail: body.message || 'La operación terminó correctamente y la evidencia ya está disponible.',
                state: 'success',
                jobId: shortId
            });
            return body;
        }

        if (status === 'FAILED') {
            updateProgressModal({
                title: `${capitalizeLabel(label)} falló`,
                detail: body.error_message || `El job ${shortId} reportó un fallo.`,
                state: 'error',
                jobId: shortId
            });
            throw new Error(body.error_message || `job ${shortId} falló`);
        }

        throw new Error(`Estado no soportado para job ${shortId}: ${status || '-'}`);
    }

    throw new Error(`Timeout esperando job ${shortJobId(jobId)}`);
}

/* ── Async Overlay ── */
function showProgressModal({ title, detail, state, jobId }) {
    const modal = document.getElementById('progress-modal');
    if (!modal) return;

    setUiBusy(true);
    modal.classList.add('show');
    modal.setAttribute('aria-hidden', 'false');
    updateProgressModal({ title, detail, state, jobId });
}

function updateProgressModal({ title, detail, state, jobId }) {
    const modal = document.getElementById('progress-modal');
    const panel = modal ? modal.querySelector('.progress-panel') : null;
    const titleEl = document.getElementById('progress-title');
    const detailEl = document.getElementById('progress-detail');
    const chipEl = document.getElementById('progress-state-chip');
    const jobEl = document.getElementById('progress-job');

    if (!modal || !panel || !titleEl || !detailEl || !chipEl || !jobEl) return;

    const normalizedState = state || 'pending';
    panel.className = `progress-panel progress-state-${normalizedState}`;
    titleEl.textContent = title || 'Ejecutando operación…';
    detailEl.innerHTML = withTooltips(detail || 'Procesando solicitud.');
    chipEl.className = `metric-pill ${progressChipClass(normalizedState)}`;
    chipEl.textContent = progressStateLabel(normalizedState);
    jobEl.textContent = jobId ? `Job ${jobId}` : '';
    jobEl.style.display = jobId ? 'inline-flex' : 'none';
}

async function hideProgressModal(delayMs) {
    if (delayMs) await sleep(delayMs);
    const modal = document.getElementById('progress-modal');
    if (modal) {
        modal.classList.remove('show');
        modal.setAttribute('aria-hidden', 'true');
    }
    setUiBusy(false);
}

function setUiBusy(isBusy) {
    document.body.classList.toggle('progress-active', isBusy);

    document.querySelectorAll('#action-buttons button, #btn-gen-report, #tab-nav .tab').forEach(control => {
        if (isBusy) {
            if (!control.dataset.busyOriginalDisabled) {
                control.dataset.busyOriginalDisabled = control.disabled ? 'true' : 'false';
            }
            control.disabled = true;
            control.classList.add('is-busy-locked');
        } else {
            const original = control.dataset.busyOriginalDisabled;
            if (original) {
                control.disabled = original === 'true';
                delete control.dataset.busyOriginalDisabled;
            } else {
                control.disabled = false;
            }
            control.classList.remove('is-busy-locked');
        }
    });
}

/* ── Motion and Enhancement ── */
function animateKpiMetrics() {
    document.querySelectorAll('.kpi-ring-progress[data-score]').forEach(circle => {
        animateRingProgress(circle, parseFloat(circle.dataset.score));
    });

    document.querySelectorAll('.kpi-animate-number[data-count-to]').forEach(el => {
        animateCountUp(el);
    });
}

function animateRingProgress(circle, score) {
    if (!Number.isFinite(score)) return;
    const radius = parseFloat(circle.getAttribute('r'));
    const circumference = 2 * Math.PI * radius;
    const targetOffset = circumference * (1 - score / 100);

    circle.style.strokeDasharray = circumference.toFixed(2);
    circle.style.strokeDashoffset = circumference.toFixed(2);

    animateValue(1100, eased => {
        const currentOffset = circumference - (circumference - targetOffset) * eased;
        circle.style.strokeDashoffset = currentOffset.toFixed(2);
    });
}

function animateCountUp(el) {
    const target = parseFloat(el.dataset.countTo);
    if (!Number.isFinite(target)) return;

    const suffix = el.dataset.suffix || '';
    const decimals = parseInt(el.dataset.decimals || '0', 10);

    el.textContent = formatCounter(0, { suffix, decimals });
    animateValue(900, eased => {
        el.textContent = formatCounter(target * eased, { suffix, decimals });
    }, () => {
        el.textContent = formatCounter(target, { suffix, decimals });
    });
}

function animateValue(duration, onFrame, onComplete) {
    const start = performance.now();

    function frame(now) {
        const progress = Math.min(1, (now - start) / duration);
        const eased = 1 - Math.pow(1 - progress, 3);
        onFrame(eased);
        if (progress < 1) {
            requestAnimationFrame(frame);
        } else if (onComplete) {
            onComplete();
        }
    }

    requestAnimationFrame(frame);
}

/* ── Tooltips ── */
function injectStaticTooltips() {
    const selectors = [
        '.card-desc',
        '.kpi-detail',
        '.kpi-ring-desc',
        '.host-kpi-note',
        '.data-table th',
        '#loading .state-copy p:last-child'
    ];

    document.querySelectorAll(selectors.join(',')).forEach(el => {
        if (el.dataset.tooltipReady === 'true') return;
        el.innerHTML = withTooltips(el.textContent.trim());
        el.dataset.tooltipReady = 'true';
    });
}

function withTooltips(text) {
    let safe = esc(text || '');
    for (const def of TOOLTIP_TERMS) {
        const re = new RegExp(`\\b${escapeRegExp(def.term)}\\b`, 'g');
        safe = safe.replace(re, `<span class="term-tooltip" data-tooltip="${esc(def.tooltip)}" tabindex="0">${def.term}</span>`);
    }
    return safe;
}

/* ── Toast ── */
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
    } catch {
        return iso;
    }
}

function fmtDateShort(iso) {
    if (!iso || iso === '-') return '-';
    try {
        const s = String(iso);
        if (s.length >= 16) return s.substring(0, 16).replace('T', ' ');
        return s;
    } catch {
        return iso;
    }
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

function clsToStatus(cls) {
    if (String(cls).includes('ready')) return 'ready';
    if (String(cls).includes('partial')) return 'partial';
    if (String(cls).includes('problem')) return 'problem';
    return 'neutral';
}

function kpiMeta(label, value, detail) {
    const safeLabel = String(label || '').toUpperCase().slice(0, 8);
    return `${safeLabel} :: ${String(value)} :: ${String(detail).slice(0, 18)}`;
}

function hostMetric(label, value, note, state, icon) {
    return `<div class="host-kpi">
        <div class="host-metric ${esc(state)}">
            <div class="metric-icon">${metricIcon(icon)}</div>
            <div class="host-metric-body">
                <div class="host-kpi-label">${esc(label)}</div>
                <div class="host-kpi-value">${withTooltips(value)}</div>
                <div class="host-kpi-note">${withTooltips(note)}</div>
            </div>
        </div>
    </div>`;
}

function timelineIcon(type) {
    switch (type) {
        case 'scan':
            return iconSvg('scan');
        case 'backup':
            return iconSvg('backup');
        case 'restore':
            return iconSvg('restore');
        case 'report':
            return iconSvg('report');
        default:
            return iconSvg('dot');
    }
}

function metricIcon(type) {
    return iconSvg(type || 'dot');
}

function pillIcon(type, label) {
    return `<span class="metric-pill-icon" aria-hidden="true">${iconSvg(type)}</span>${esc(label)}`;
}

function iconSvg(type) {
    switch (type) {
        case 'shield':
            return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 3v5c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V6l7-3z"></path><path d="m9 12 2 2 4-4"></path></svg>`;
        case 'backup':
            return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="7" ry="3"></ellipse><path d="M5 5v6c0 1.66 3.13 3 7 3s7-1.34 7-3V5"></path><path d="M5 11v6c0 1.66 3.13 3 7 3"></path><path d="m16 19 2 2 4-4"></path></svg>`;
        case 'restore':
            return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7"></path><path d="M3 4v5h5"></path><path d="M12 8v4l3 2"></path></svg>`;
        case 'scan':
            return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="6"></circle><path d="m20 20-4.2-4.2"></path></svg>`;
        case 'report':
            return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16l4-2 4 2 4-2 4 2V8z"></path><path d="M14 2v6h6"></path></svg>`;
        case 'inventory':
            return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="7" height="7" rx="1.5"></rect><rect x="14" y="4" width="7" height="7" rx="1.5"></rect><rect x="3" y="15" width="7" height="7" rx="1.5"></rect><rect x="14" y="15" width="7" height="7" rx="1.5"></rect></svg>`;
        default:
            return `<svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="12" r="4"></circle></svg>`;
    }
}

function counterAttrs(animation) {
    const suffix = animation && animation.suffix ? animation.suffix : '';
    const decimals = animation && Number.isFinite(animation.decimals) ? animation.decimals : 0;
    return ` data-count-to="${animation.countTo}" data-suffix="${esc(suffix)}" data-decimals="${decimals}"`;
}

function formatCounter(value, animation) {
    const suffix = animation && animation.suffix ? animation.suffix : '';
    const decimals = animation && Number.isFinite(animation.decimals) ? animation.decimals : 0;
    const formatted = decimals > 0 ? Number(value).toFixed(decimals) : String(Math.round(value));
    return `${formatted}${suffix}`;
}

function progressStateLabel(state) {
    switch (state) {
        case 'running': return 'Ejecutando';
        case 'success': return 'Completado';
        case 'error': return 'Fallido';
        default: return 'Pendiente';
    }
}

function progressChipClass(state) {
    switch (state) {
        case 'running': return 'status-neutral';
        case 'success': return 'status-ready';
        case 'error': return 'status-problem';
        default: return 'status-partial';
    }
}

function capitalizeLabel(label) {
    const normalized = String(label || '').trim();
    if (!normalized) return 'Operación';
    return normalized.charAt(0).toUpperCase() + normalized.slice(1);
}

function escapeRegExp(str) {
    return String(str).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
