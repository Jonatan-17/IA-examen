// Incidents view (RF01): register, list, filter and change the status of incidents.
// Uses GET /api/categories, GET /api/users, GET/POST /api/incidents and PATCH /api/incidents/{id}/status.
import { api, apiUrl } from '../api.js';
import { showMessage, showError, clearMessage } from '../ui/messages.js';
import { readForm, showFieldErrors, clearFieldErrors } from '../ui/forms.js';
import { renderTable } from '../ui/table.js';
import {
    PRIORITY_LABELS, STATUS_LABELS, optionsFrom, badge, priorityLabel, statusLabel, slaLabel, formatDate,
} from '../ui/format.js';
import { showIncidentDetail, renderSimilar } from './incident-detail.js';

let users = [];
let categories = [];
const PAGE_SIZE = 20;
const paging = { page: 0 };

/** Fired on the section when an incident is created or changed, so the list can reload. */
export const INCIDENTS_CHANGED = 'incidents:changed';

// Static markup only (no API data), so innerHTML is safe here. Names match IncidentRequest.
const FORM_HTML = `
    <h3>Registrar incidente</h3>
    <form class="form-grid" novalidate>
        <div class="field field-wide">
            <label for="incident-title">Título</label>
            <input id="incident-title" name="title" maxlength="200" required>
        </div>
        <div class="field field-wide">
            <label for="incident-description">Descripción</label>
            <textarea id="incident-description" name="description" maxlength="5000" required></textarea>
        </div>
        <div class="field">
            <label for="incident-category">Categoría</label>
            <select id="incident-category" name="categoryId" data-type="number" required></select>
        </div>
        <div class="field">
            <label for="incident-user">Usuario responsable</label>
            <select id="incident-user" name="userId" data-type="number" required></select>
        </div>
        <div class="field">
            <label for="incident-priority">Prioridad</label>
            <select id="incident-priority" name="priority" required></select>
        </div>
        <div class="field">
            <label for="incident-status">Estado</label>
            <select id="incident-status" name="status"></select>
        </div>
        <div class="field">
            <label for="incident-location">Ubicación</label>
            <input id="incident-location" name="location" maxlength="100" placeholder="Ej. Piso 3, sala B">
        </div>
        <input type="hidden" name="suggestedCategoryId" data-type="number">
        <input type="hidden" name="suggestedPriority">
        <div class="form-actions field-wide">
            <button type="submit" class="btn">Registrar</button>
            <button type="reset" class="btn btn-secondary">Limpiar</button>
            <button type="button" class="btn btn-secondary" data-action="classify">Sugerir categoría y prioridad</button>
            <button type="button" class="btn btn-secondary" data-action="similar">Buscar similares</button>
        </div>
        <div class="field-wide ai-panel" data-role="ai-panel"></div>
    </form>`;

// Names match IncidentFilter (query parameters of GET /api/incidents).
const FILTERS_HTML = `
    <h3>Incidentes registrados</h3>
    <form class="form-grid" novalidate>
        <div class="field">
            <label for="filter-category">Categoría</label>
            <select id="filter-category" name="categoryId" data-type="number"></select>
        </div>
        <div class="field">
            <label for="filter-priority">Prioridad</label>
            <select id="filter-priority" name="priority"></select>
        </div>
        <div class="field">
            <label for="filter-status">Estado</label>
            <select id="filter-status" name="status"></select>
        </div>
        <div class="field">
            <label for="filter-from">Desde</label>
            <input id="filter-from" name="from" type="date">
        </div>
        <div class="field">
            <label for="filter-to">Hasta</label>
            <input id="filter-to" name="to" type="date">
        </div>
        <div class="field">
            <label for="filter-user">Responsable</label>
            <select id="filter-user" name="userId" data-type="number"></select>
        </div>
        <div class="field">
            <label for="filter-q">Texto</label>
            <input id="filter-q" name="q" maxlength="100" placeholder="Buscar en título o descripción">
        </div>
        <div class="form-actions field-wide">
            <button type="submit" class="btn">Filtrar</button>
            <button type="reset" class="btn btn-secondary">Limpiar</button>
            <button type="button" class="btn btn-secondary" data-action="export">Exportar CSV</button>
        </div>
    </form>
    <div class="table-container"></div>
    <div class="pager"></div>`;

/** Status transitions offered in the table, per current status. */
const STATUS_ACTIONS = [
    { label: 'Iniciar', status: 'IN_PROGRESS', from: ['OPEN'] },
    { label: 'Resolver', status: 'RESOLVED', from: ['OPEN', 'IN_PROGRESS'] },
    { label: 'Cerrar', status: 'CLOSED', from: ['RESOLVED'] },
    { label: 'Reabrir', status: 'OPEN', from: ['RESOLVED', 'CLOSED'] },
];

/** Called once, the first time the view is opened. */
export async function init(section) {
    const messages = document.createElement('div');
    messages.className = 'messages';

    const formCard = document.createElement('div');
    formCard.className = 'card';
    formCard.innerHTML = FORM_HTML;

    const listCard = document.createElement('div');
    listCard.className = 'card';
    listCard.innerHTML = FILTERS_HTML;

    // Panel de detalle, oculto hasta pulsar "Ver"
    const detailCard = document.createElement('div');
    detailCard.className = 'card incident-detail';
    detailCard.hidden = true;

    section.append(messages, formCard, detailCard, listCard);

    const form = formCard.querySelector('form');
    const filters = listCard.querySelector('form');
    const table = listCard.querySelector('.table-container');
    const aiPanel = form.querySelector('[data-role="ai-panel"]');

    fillStaticOptions(form, filters);
    form.addEventListener('submit', event => submitIncident(event, form, messages, section));
    form.addEventListener('reset', () => {
        clearFieldErrors(form);
        clearSuggestion(form);
        aiPanel.replaceChildren();
    });
    form.querySelector('[data-action="classify"]').addEventListener('click', e => classify(e.target, form, aiPanel, messages));
    form.querySelector('[data-action="similar"]').addEventListener('click', e => findSimilar(e.target, form, aiPanel, messages));

    const pager = listCard.querySelector('.pager');
    const reload = () => loadIncidents(filters, table, pager, messages, section);
    const openDetail = incident => showIncidentDetail(detailCard, incident.id, { users, categories, onChanged: reload });
    section._openDetail = openDetail;
    pager.addEventListener('click', event => {
        const step = Number(event.target.dataset.step);
        if (!step) return;
        paging.page += step;
        reload();
    });
    filters.addEventListener('submit', event => { event.preventDefault(); paging.page = 0; reload(); });
    filters.addEventListener('reset', () => { paging.page = 0; setTimeout(reload); }); // after the browser clears the fields
    // Descarga el CSV con los filtros actuales
    filters.querySelector('[data-action="export"]').addEventListener('click', () => {
        window.location.href = apiUrl('/incidents/export', readForm(filters)).toString();
    });
    section.addEventListener(INCIDENTS_CHANGED, reload);

    await loadCatalogs(form, filters, messages);
    await reload();
}

/** Priority and status come from the backend enums, shown with Spanish labels. */
function fillStaticOptions(form, filters) {
    form.elements.priority.append(...optionsFrom(PRIORITY_LABELS, 'Selecciona…'));
    form.elements.status.append(...optionsFrom(STATUS_LABELS));
    form.elements.status.value = 'OPEN';
    filters.elements.priority.append(...optionsFrom(PRIORITY_LABELS, 'Todas'));
    filters.elements.status.append(...optionsFrom(STATUS_LABELS, 'Todos'));
}

/** Categories and users are loaded from the API; an empty choice makes the backend answer 400. */
async function loadCatalogs(form, filters, messages) {
    try {
        [categories, users] = await Promise.all([api('/categories'), api('/users')]);
        fillSelect(filters.elements.userId, users, u => u.name, 'Todos');
        fillSelect(form.elements.categoryId, categories, c => c.name, 'Selecciona…');
        fillSelect(form.elements.userId, users, u => `${u.name} · ${u.area}`, 'Selecciona…');
        fillSelect(filters.elements.categoryId, categories, c => c.name, 'Todas');
    } catch (error) {
        showError(messages, error);
    }
}

function fillSelect(select, items, label, emptyLabel) {
    select.replaceChildren(new Option(emptyLabel, ''), ...items.map(item => new Option(label(item), item.id)));
}

async function submitIncident(event, form, messages, section) {
    event.preventDefault();
    clearMessage(messages);
    clearFieldErrors(form);

    const button = form.querySelector('button[type="submit"]');
    button.disabled = true; // avoids registering the same incident twice
    try {
        const created = await api('/incidents', { method: 'POST', body: readForm(form) });
        form.reset(); // the reset listener also clears the AI suggestion
        form.elements.status.value = 'OPEN';
        showMessage(messages, `Incidente #${created.id} registrado`, 'success');
        section.dispatchEvent(new CustomEvent(INCIDENTS_CHANGED, { detail: created }));
    } catch (error) {
        showFormError(form, messages, error);
    } finally {
        button.disabled = false;
    }
}

/**
 * 400: each validation message goes under its field, plus a short summary on top.
 * Anything else (404 unknown category or user, network...) is shown as a general message.
 */
function showFormError(form, messages, error) {
    const unplaced = showFieldErrors(form, error);
    if (unplaced.length > 0) {
        showMessage(messages, unplaced.join(' '), 'error');
    } else if (error?.status === 400) {
        showMessage(messages, 'Revisa los campos marcados.', 'error');
    } else {
        showError(messages, error);
    }
}

/** GET /api/incidents/paged con los filtros y la página actual. */
async function loadIncidents(filters, table, pager, messages, section) {
    clearFieldErrors(filters);
    try {
        const result = await api('/incidents/paged', {
            params: { ...readForm(filters), page: paging.page, size: PAGE_SIZE },
        });
        renderIncidents(table, result.content, messages, section);
        renderPager(pager, result);
    } catch (error) {
        // e.g. "from" after "to" has no field of its own, so it is shown on top
        showFormError(filters, messages, error);
    }
}

function renderPager(pager, result) {
    pager.replaceChildren();
    if (result.totalPages <= 1) return;
    const button = (label, step, disabled) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.className = 'btn-small';
        b.textContent = label;
        b.dataset.step = step;
        b.disabled = disabled;
        return b;
    };
    const info = document.createElement('span');
    info.className = 'muted';
    info.textContent = ` Página ${result.page + 1} de ${result.totalPages} (${result.totalElements} incidentes) `;
    pager.append(button('Anterior', -1, result.page === 0), info,
        button('Siguiente', 1, result.page + 1 >= result.totalPages));
}

function renderIncidents(table, incidents, messages, section) {
    renderTable(table, [
        { header: '#', value: i => i.id },
        { header: 'Título', value: i => i.title },
        { header: 'Categoría', value: i => i.categoryName },
        { header: 'Ubicación', value: i => i.location },
        { header: 'Prioridad', value: i => badge(priorityLabel(i.priority), i.priority) },
        { header: 'Estado', value: i => badge(statusLabel(i.status), `status-${i.status}`) },
        { header: 'Responsable', value: i => i.userName },
        { header: 'Creado', value: i => formatDate(i.createdAt) },
        { header: 'Resuelto', value: i => formatDate(i.resolvedAt) },
        { header: 'SLA', value: i => i.slaStatus ? badge(slaLabel(i.slaStatus), `sla-${i.slaStatus}`) : null },
        {
            header: 'Acciones',
            actions: [
                { label: 'Ver', onClick: i => section._openDetail(i) },
                ...STATUS_ACTIONS.map(action => ({
                    label: action.label,
                    visible: i => action.from.includes(i.status),
                    onClick: i => changeStatus(i, action.status, messages, section),
                })),
            ],
        },
    ], incidents, { emptyText: 'No hay incidentes que coincidan con los filtros.' });
}

/** PATCH /api/incidents/{id}/status; the list reloads through INCIDENTS_CHANGED. */
async function changeStatus(incident, status, messages, section) {
    clearMessage(messages);
    try {
        const updated = await api(`/incidents/${incident.id}/status`, { method: 'PATCH', body: { status } });
        showMessage(messages, `Incidente #${updated.id} ahora está "${statusLabel(updated.status)}"`, 'success');
        section.dispatchEvent(new CustomEvent(INCIDENTS_CHANGED, { detail: updated }));
    } catch (error) {
        showError(messages, error);
    }
}

/** Título y descripción actuales del formulario; null si falta la descripción. */
function incidentText(form, messages) {
    const description = form.elements.description.value.trim();
    if (!description) {
        showMessage(messages, 'Escribe primero la descripción del incidente.', 'error');
        return null;
    }
    return { title: form.elements.title.value.trim() || null, description };
}

/** La IA sugiere categoría y prioridad; se guardan en campos ocultos para medir su acierto. */
async function classify(button, form, panel, messages) {
    clearMessage(messages);
    const body = incidentText(form, messages);
    if (!body) return;
    button.disabled = true;
    try {
        const s = await api('/incidents/classify', { method: 'POST', body });
        form.elements.categoryId.value = s.categoryId;
        form.elements.priority.value = s.priority;
        form.elements.suggestedCategoryId.value = s.categoryId;
        form.elements.suggestedPriority.value = s.priority;
        const p = document.createElement('p');
        p.className = 'muted';
        p.textContent = `Sugerencia IA: ${s.categoryName} · ${priorityLabel(s.priority)}. ${s.reason ?? ''} Puedes cambiarla antes de registrar.`;
        panel.replaceChildren(p);
    } catch (error) {
        showError(messages, error);
    } finally {
        button.disabled = false;
    }
}

/** Incidentes resueltos parecidos al que se está escribiendo. */
async function findSimilar(button, form, panel, messages) {
    clearMessage(messages);
    const body = incidentText(form, messages);
    if (!body) return;
    button.disabled = true;
    try {
        renderSimilar(panel, await api('/incidents/similar', { method: 'POST', body }));
    } catch (error) {
        showError(messages, error);
    } finally {
        button.disabled = false;
    }
}

// form.reset() no limpia los campos ocultos
function clearSuggestion(form) {
    form.elements.suggestedCategoryId.value = '';
    form.elements.suggestedPriority.value = '';
}
