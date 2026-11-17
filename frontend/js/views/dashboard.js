// Dashboard view (RF06, RF07): indicators and charts computed with SQL by GET /api/analytics/*.
// Every request sends the same filters (dates, category, priority), so cards and charts always agree.
import { api } from '../api.js';
import { showError, clearMessage } from '../ui/messages.js';
import { readForm, showFieldErrors, clearFieldErrors } from '../ui/forms.js';
import { renderTable } from '../ui/table.js';
import { PRIORITY_LABELS, optionsFrom, priorityLabel } from '../ui/format.js';

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
        </div>
    </form>`;

const BODY_HTML = `
    <div class="kpi-grid">
        <div class="card kpi"><span class="kpi-label">Total de incidentes</span><strong data-kpi="total"></strong></div>
        <div class="card kpi"><span class="kpi-label">Tasa de resolución</span><strong data-kpi="rate"></strong></div>
        <div class="card kpi"><span class="kpi-label">Tiempo promedio de resolución</span><strong data-kpi="hours"></strong></div>
        <div class="card kpi"><span class="kpi-label">Categoría más recurrente</span><strong data-kpi="top"></strong></div>
    </div>
    <div class="chart-grid">
        <div class="card"><h3>Evolución semanal</h3><canvas data-chart="weekly"></canvas></div>
        <div class="card"><h3>Incidentes por categoría</h3><canvas data-chart="category"></canvas></div>
        <div class="card"><h3>Tiempo promedio de resolución por categoría (horas)</h3><canvas data-chart="time"></canvas></div>
        <div class="card"><h3>Distribución por prioridad</h3><canvas data-chart="priority"></canvas></div>
    </div>
    <div class="card"><h3>Detalle por categoría</h3><div class="table-container"></div></div>`;

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

    section.append(messages, filtersCard, body);

    const filters = filtersCard.querySelector('form');
    filters.elements.priority.append(...optionsFrom(PRIORITY_LABELS, 'Todas'));

    const reload = () => loadDashboard(filters, body, messages);
    filters.addEventListener('submit', event => { event.preventDefault(); reload(); });
    filters.addEventListener('reset', () => setTimeout(reload)); // after the browser clears the fields

    await loadCategories(filters, messages);
    await reload();
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

function renderCharts(body, { byCategory, resolutionTime, weekly, byPriority }) {
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

/** "2026-08-10" → "10/08". Parsed by hand: new Date() would read it as UTC and show the previous day. */
function formatWeek(isoDay) {
    const [, month, day] = isoDay.split('-');
    return `${day}/${month}`;
}
