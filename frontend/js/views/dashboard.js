// Dashboard view (RF06, RF07): indicators and charts computed with SQL by GET /api/analytics/*.
// Every request sends the same filters (dates, category, priority), so cards and charts always agree.
import { api } from '../api.js';
import { showError, showMessage, clearMessage } from '../ui/messages.js';
import { readForm, showFieldErrors, clearFieldErrors } from '../ui/forms.js';
import { renderTable } from '../ui/table.js';
import { PRIORITY_LABELS, optionsFrom, priorityLabel, queryStatusLabel } from '../ui/format.js';

// Static markup only (no API data), so innerHTML is safe here. Names match AnalyticsFilter.
const FILTERS_HTML = `
    <h3>Filtros</h3>
    <form class="form-grid" novalidate>
        <div class="field">
            <label for="dash-from">Desde</label>
            <input id="dash-from" name="from" type="date">
        </div>
        <div class="field">
            <label for="dash-to">Hasta</label>
            <input id="dash-to" name="to" type="date">
        </div>
        <div class="field">
            <label for="dash-category">Categoría</label>
            <select id="dash-category" name="categoryId" data-type="number"></select>
        </div>
        <div class="field">
            <label for="dash-priority">Prioridad</label>
            <select id="dash-priority" name="priority"></select>
        </div>
        <div class="form-actions field-wide">
            <button type="submit" class="btn">Aplicar</button>
            <button type="reset" class="btn btn-secondary">Limpiar</button>
            <button type="button" class="btn btn-secondary" data-action="print">Imprimir / PDF</button>
        </div>
    </form>`;

// SLA y acierto de la IA usan los filtros; la calidad del asistente usa todo el histórico
const EXTRA_HTML = `
    <h3 class="section-title">IA y SLA</h3>
    <p class="muted">Las consultas, calificaciones y uso de artículos son de todo el histórico.</p>
    <div class="kpi-grid">
        <div class="card kpi"><span class="kpi-label">Consultas a la IA</span><strong data-kpi="queries"></strong></div>
        <div class="card kpi"><span class="kpi-label">Respondidas</span><strong data-kpi="answered"></strong></div>
        <div class="card kpi"><span class="kpi-label">Sin resultados</span><strong data-kpi="noResults"></strong></div>
        <div class="card kpi"><span class="kpi-label">Calificación promedio</span><strong data-kpi="rating"></strong></div>
        <div class="card kpi"><span class="kpi-label">Acierto IA en categoría</span><strong data-kpi="catAccuracy"></strong></div>
        <div class="card kpi"><span class="kpi-label">Acierto IA en prioridad</span><strong data-kpi="prioAccuracy"></strong></div>
    </div>
    <div class="chart-grid">
        <div class="card"><h3>Cumplimiento de SLA por categoría (%)</h3><canvas data-chart="sla"></canvas></div>
        <div class="card"><h3>Consultas a la IA por estado</h3><canvas data-chart="queries"></canvas></div>
    </div>
    <div class="card"><h3>SLA por categoría</h3><div data-table="sla"></div></div>
    <div class="card no-print"><h3>Políticas de SLA (horas máximas por prioridad)</h3>
        <form class="form-grid" data-role="sla-form" novalidate></form></div>
    <div class="card"><h3>Calidad del asistente por categoría</h3><div data-table="quality"></div></div>
    <div class="card"><h3>Uso de artículos como fuente</h3><div data-table="usage"></div></div>`;

const BODY_HTML = `
    <div class="kpi-grid">
        <div class="card kpi"><span class="kpi-label">Total de incidentes</span><strong data-kpi="total"></strong></div>
        <div class="card kpi"><span class="kpi-label">Tasa de resolución</span><strong data-kpi="rate"></strong></div>
        <div class="card kpi"><span class="kpi-label">Tiempo promedio de resolución</span><strong data-kpi="hours"></strong></div>
        <div class="card kpi"><span class="kpi-label">Categoría más recurrente</span><strong data-kpi="top"></strong></div>
        <div class="card kpi"><span class="kpi-label">Tasa de reapertura</span><strong data-kpi="reopen"></strong></div>
        <div class="card kpi"><span class="kpi-label">Vs. periodo anterior</span><strong data-kpi="compare"></strong></div>
    </div>
    <div class="chart-grid">
        <div class="card"><h3>Evolución semanal</h3><canvas data-chart="weekly"></canvas></div>
        <div class="card"><h3>Incidentes por categoría</h3><canvas data-chart="category"></canvas></div>
        <div class="card"><h3>Tiempo promedio de resolución por categoría (horas)</h3><canvas data-chart="time"></canvas></div>
        <div class="card"><h3>Distribución por prioridad</h3><canvas data-chart="priority"></canvas></div>
        <div class="card"><h3>Incidentes por área</h3><canvas data-chart="area"></canvas></div>
        <div class="card"><h3>Incidentes por día de la semana</h3><canvas data-chart="weekday"></canvas></div>
        <div class="card"><h3>Tiempo promedio de resolución por prioridad (horas)</h3><canvas data-chart="priorityTime"></canvas></div>
    </div>
    <div class="card"><h3>Detalle por categoría</h3><div class="table-container"></div></div>
    <div class="card"><h3>Ranking de responsables</h3><div data-table="technicians"></div></div>
    <div class="card"><h3>Tiempo de resolución: mediana y p90 (horas)</h3>
        <p class="muted">El promedio se infla con pocos casos muy lentos; la mediana (p50) muestra el caso típico y el p90 el peor 10 %.</p>
        <div data-table="percentiles"></div></div>`;

const COLORS = ['#2457c5', '#1f8a4c', '#a84300', '#7b3fb5', '#c0392b', '#5f6b7a'];

const charts = {};

/** Called once, the first time the view is opened. */
export async function init(section) {
    const messages = document.createElement('div');
    messages.className = 'messages';

    const filtersCard = document.createElement('div');
    filtersCard.className = 'card';
    filtersCard.innerHTML = FILTERS_HTML;

    const body = document.createElement('div');
    body.innerHTML = BODY_HTML;

    const extra = document.createElement('div');
    extra.innerHTML = EXTRA_HTML;

    section.append(messages, filtersCard, body, extra);

    const filters = filtersCard.querySelector('form');
    filters.elements.priority.append(...optionsFrom(PRIORITY_LABELS, 'Todas'));

    const reload = async () => {
        await loadDashboard(filters, body, messages);
        await loadExtra(extra, messages, readForm(filters));
    };
    filters.addEventListener('submit', event => { event.preventDefault(); reload(); });
    filters.addEventListener('reset', () => setTimeout(reload)); // after the browser clears the fields
    filters.querySelector('[data-action="print"]').addEventListener('click', () => window.print());

    await loadCategories(filters, messages);
    await reload();
}

async function loadExtra(extra, messages, params = extra._params) {
    extra._params = params;
    try {
        const [assistant, sla, classification, policies] = await Promise.all([
            api('/analytics/assistant'),
            api('/analytics/sla', { params }),
            api('/analytics/classification', { params }),
            api('/sla-policies'),
        ]);
        renderExtra(extra, assistant, sla, classification);
        renderSlaForm(extra, policies, messages);
    } catch (error) {
        showError(messages, error);
    }
}

function renderExtra(extra, assistant, sla, classification) {
    const set = (key, text) => { extra.querySelector(`[data-kpi="${key}"]`).textContent = text; };
    const count = status => assistant.byStatus.find(s => s.status === status)?.queries ?? 0;
    const share = status => assistant.totalQueries === 0 ? '—'
        : `${formatNumber(100 * count(status) / assistant.totalQueries)} %`;
    const percent = value => value == null ? '—' : `${formatNumber(value)} %`;

    set('queries', assistant.totalQueries);
    set('answered', share('ANSWERED'));
    set('noResults', share('NO_RESULTS'));
    set('rating', assistant.avgRating == null ? '—' : `${formatNumber(assistant.avgRating)} / 5 (${assistant.ratedResponses})`);
    set('catAccuracy', percent(classification.categoryAccuracy));
    set('prioAccuracy', percent(classification.priorityAccuracy));

    drawChart(extra, 'sla', 'bar', sla.map(s => s.category), sla.map(s => s.compliance ?? 0), '% cumplido');
    drawChart(extra, 'queries', 'doughnut', assistant.byStatus.map(s => queryStatusLabel(s.status)),
        assistant.byStatus.map(s => s.queries), 'Consultas');

    renderTable(extra.querySelector('[data-table="sla"]'), [
        { header: 'Categoría', value: s => s.category },
        { header: 'Resueltos', value: s => s.resolved },
        { header: 'Dentro del SLA', value: s => s.met },
        { header: '% cumplimiento', value: s => percent(s.compliance) },
        { header: 'Abiertos vencidos', value: s => s.openOverdue },
    ], sla);

    renderTable(extra.querySelector('[data-table="quality"]'), [
        { header: 'Categoría', value: c => c.category },
        { header: 'Consultas', value: c => c.queries },
        { header: 'Sin resultados (falta documentación)', value: c => c.noResults },
        { header: 'Calificadas', value: c => c.rated },
        { header: 'Calificación promedio', value: c => c.avgRating == null ? null : formatNumber(c.avgRating) },
    ], assistant.byCategory);

    renderTable(extra.querySelector('[data-table="usage"]'), [
        { header: '#', value: a => a.articleId },
        { header: 'Artículo', value: a => a.title },
        { header: 'Veces usado', value: a => a.uses },
        { header: 'Similitud promedio', value: a => a.avgSimilarity == null ? null : formatNumber(a.avgSimilarity) },
        { header: 'Fuente IA', value: a => (a.active && a.verified ? 'Sí' : 'No') },
    ], assistant.articleUsage);
}

/** Un campo de horas por prioridad; guarda cada una con PUT /api/sla-policies/{priority}. */
function renderSlaForm(extra, policies, messages) {
    const form = extra.querySelector('[data-role="sla-form"]');
    form.replaceChildren();
    policies.forEach(p => {
        const wrap = document.createElement('div');
        wrap.className = 'field';
        const label = document.createElement('label');
        label.textContent = `${priorityLabel(p.priority)} (${p.priority})`;
        const input = document.createElement('input');
        input.type = 'number';
        input.min = '1';
        input.name = p.priority;
        input.value = p.maxHours;
        wrap.append(label, input);
        form.append(wrap);
    });
    const actions = document.createElement('div');
    actions.className = 'form-actions field-wide';
    const save = document.createElement('button');
    save.type = 'submit';
    save.className = 'btn';
    save.textContent = 'Guardar SLA';
    actions.append(save);
    form.append(actions);

    form.onsubmit = async event => {
        event.preventDefault();
        clearMessage(messages);
        save.disabled = true;
        try {
            await Promise.all(policies.map(p => api(`/sla-policies/${p.priority}`, {
                method: 'PUT',
                body: { priority: p.priority, maxHours: Number(form.elements[p.priority].value) },
            })));
            showMessage(messages, 'SLA actualizado.', 'success');
            await loadExtra(extra, messages);
        } catch (error) {
            showError(messages, error);
            save.disabled = false;
        }
    };
}

async function loadCategories(filters, messages) {
    const select = filters.elements.categoryId;
    select.append(new Option('Todas', ''));
    try {
        const categories = await api('/categories');
        categories.forEach(c => select.append(new Option(c.name, c.id)));
    } catch (error) {
        showError(messages, error);
    }
}

async function loadDashboard(filters, body, messages) {
    clearMessage(messages);
    clearFieldErrors(filters);
    const params = readForm(filters);
    try {
        const [summary, byCategory, resolutionTime, weekly, byPriority] = await Promise.all([
            api('/analytics/summary', { params }),
            api('/analytics/by-category', { params }),
            api('/analytics/resolution-time', { params }),
            api('/analytics/weekly-trend', { params }),
            api('/analytics/by-priority', { params }),
        ]);
        renderKpis(body, summary);
        renderCharts(body, { byCategory, resolutionTime, weekly, byPriority });
        await loadMore(body, params);
        renderCategoryTable(body, byCategory, resolutionTime);
    } catch (error) {
        showFieldErrors(filters, error);
        showError(messages, error);
    }
}

function renderKpis(body, summary) {
    const set = (key, text) => { body.querySelector(`[data-kpi="${key}"]`).textContent = text; };
    set('total', summary.total);
    set('rate', summary.resolutionRate == null ? '—' : `${formatNumber(summary.resolutionRate)} %`);
    set('hours', summary.avgResolutionHours == null ? '—' : `${formatNumber(summary.avgResolutionHours)} h`);
    set('top', summary.topCategory
        ? `${summary.topCategory.category} (${summary.topCategory.incidents})`
        : '—');
}

function renderCharts(body, { byCategory, resolutionTime, weekly: rows, byPriority }) {
    const weekly = fillEmptyWeeks(rows);
    drawChart(body, 'weekly', 'line', weekly.map(w => formatWeek(w.periodStart)), weekly.map(w => w.incidents),
        'Incidentes');
    drawChart(body, 'category', 'bar', byCategory.map(c => c.category), byCategory.map(c => c.incidents),
        'Incidentes');
    drawChart(body, 'time', 'bar', resolutionTime.map(c => c.category), resolutionTime.map(c => c.avgHours),
        'Horas');
    drawChart(body, 'priority', 'doughnut', byPriority.map(p => priorityLabel(p.priority)),
        byPriority.map(p => p.incidents), 'Incidentes');
}

/** Chart.js keeps one chart per canvas: the previous one is destroyed before drawing with new filters. */
function drawChart(body, key, type, labels, data, label) {
    if (typeof Chart === 'undefined') return; // CDN not reachable: the cards and the table still work
    charts[key]?.destroy();
    const canvas = body.querySelector(`[data-chart="${key}"]`);
    charts[key] = new Chart(canvas, {
        type,
        data: {
            labels,
            datasets: [{
                label,
                data,
                backgroundColor: type === 'doughnut' ? COLORS : COLORS[0],
                borderColor: COLORS[0],
                tension: 0.25,
            }],
        },
        options: {
            responsive: true,
            plugins: { legend: { display: type === 'doughnut' } },
            scales: type === 'doughnut' ? {} : { y: { beginAtZero: true, ticks: { precision: 0 } } },
        },
    });
}

function renderCategoryTable(body, byCategory, resolutionTime) {
    const hoursById = new Map(resolutionTime.map(r => [r.categoryId, r]));
    renderTable(body.querySelector('.table-container'), [
        { header: 'Categoría', value: c => c.category },
        { header: 'Incidentes', value: c => c.incidents },
        { header: '% del total', value: c => `${formatNumber(c.percentage)} %` },
        { header: 'Resueltos medidos', value: c => hoursById.get(c.categoryId)?.resolvedIncidents ?? 0 },
        {
            header: 'Promedio de resolución (h)',
            value: c => {
                const hours = hoursById.get(c.categoryId)?.avgHours;
                return hours == null ? null : formatNumber(hours);
            },
        },
    ], byCategory, { emptyText: 'No hay incidentes con estos filtros' });
}

const NUMBER = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });

function formatNumber(value) {
    return NUMBER.format(value);
}

/**
 * SQL only returns weeks that have incidents. With filters some weeks are empty, and skipping them
 * would join two distant weeks in the line chart, so the missing weeks are added with 0.
 */
function fillEmptyWeeks(rows) {
    if (rows.length === 0) return rows;
    const counts = new Map(rows.map(r => [r.periodStart, r.incidents]));
    const filled = [];
    // Week starts are plain dates: UTC arithmetic avoids any daylight saving shift
    const day = new Date(`${rows[0].periodStart}T00:00:00Z`);
    const last = rows[rows.length - 1].periodStart;
    for (let iso = rows[0].periodStart; iso <= last; iso = day.toISOString().slice(0, 10)) {
        filled.push({ periodStart: iso, incidents: counts.get(iso) ?? 0 });
        day.setUTCDate(day.getUTCDate() + 7);
    }
    return filled;
}

/** "2026-08-10" → "10/08". Parsed by hand: new Date() would read it as UTC and show the previous day. */
function formatWeek(isoDay) {
    const [, month, day] = isoDay.split('-');
    return `${day}/${month}`;
}

const WEEKDAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

/** Área, día de la semana, tiempo por prioridad, reapertura y comparación de periodos. */
async function loadMore(body, params) {
    const [byArea, byWeekday, priorityTime, reopen, technicians, percentiles] = await Promise.all([
        api('/analytics/by-area', { params }),
        api('/analytics/by-weekday', { params }),
        api('/analytics/resolution-time-by-priority', { params }),
        api('/analytics/reopen-rate', { params }),
        api('/analytics/technicians', { params }),
        api('/analytics/resolution-percentiles', { params }),
    ]);
    const hours = v => v == null ? null : formatNumber(v);
    renderTable(body.querySelector('[data-table="technicians"]'), [
        { header: '#', value: t => t.position },
        { header: 'Responsable', value: t => t.name },
        { header: 'Área', value: t => t.area },
        { header: 'Asignados', value: t => t.assigned },
        { header: 'Resueltos', value: t => t.resolved },
        { header: '% resolución', value: t => t.resolutionRate == null ? null : `${formatNumber(t.resolutionRate)} %` },
        { header: 'Promedio (h)', value: t => hours(t.avgHours) },
        { header: 'SLA incumplidos', value: t => t.slaMissed },
    ], technicians.map((t, i) => ({ ...t, position: i + 1 })), { emptyText: 'Sin datos con estos filtros' });
    renderTable(body.querySelector('[data-table="percentiles"]'), [
        { header: 'Categoría', value: p => p.category ?? 'Total' },
        { header: 'Resueltos', value: p => p.resolved },
        { header: 'Promedio', value: p => hours(p.avgHours) },
        { header: 'Mediana (p50)', value: p => hours(p.p50) },
        { header: 'p90', value: p => hours(p.p90) },
        { header: 'Máximo', value: p => hours(p.maxHours) },
    ], percentiles, { emptyText: 'Sin incidentes resueltos con estos filtros' });
    drawChart(body, 'area', 'bar', byArea.map(a => a.area), byArea.map(a => a.incidents), 'Incidentes');
    const counts = new Map(byWeekday.map(d => [d.day, d.incidents]));
    drawChart(body, 'weekday', 'bar', WEEKDAYS, WEEKDAYS.map((_, i) => counts.get(i + 1) ?? 0), 'Incidentes');
    drawChart(body, 'priorityTime', 'bar', priorityTime.map(p => priorityLabel(p.priority)),
        priorityTime.map(p => p.avgHours), 'Horas');

    const set = (key, text) => { body.querySelector(`[data-kpi="${key}"]`).textContent = text; };
    set('reopen', reopen.rate == null ? '—' : `${formatNumber(reopen.rate)} % (${reopen.reopened})`);

    // Solo con rango de fechas completo
    if (params.from && params.to) {
        const c = await api('/analytics/compare', { params });
        const change = c.changePercent == null ? 'sin datos previos'
            : `${c.changePercent > 0 ? '+' : ''}${formatNumber(c.changePercent)} %`;
        set('compare', `${c.current} vs ${c.previous} (${change})`);
    } else {
        set('compare', 'Elige desde y hasta');
    }
}
