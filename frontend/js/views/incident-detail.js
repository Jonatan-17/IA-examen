// Detalle de un incidente: estado y resolución, bitácora, comentarios, similares y borrador de artículo con IA.
import { api } from '../api.js';
import { showMessage, showError, clearMessage } from '../ui/messages.js';
import {
    STATUS_LABELS, PRIORITY_LABELS, optionsFrom, badge, priorityLabel, statusLabel, slaLabel, formatDate,
} from '../ui/format.js';

/** Crea un elemento con texto (nunca innerHTML con datos de la API). */
function el(tag, text, className) {
    const node = document.createElement(tag);
    if (text !== undefined && text !== null) node.textContent = text;
    if (className) node.className = className;
    return node;
}

function userSelect(users, emptyLabel) {
    const select = el('select');
    select.append(new Option(emptyLabel, ''), ...users.map(u => new Option(u.name, u.id)));
    return select;
}

/** Lista de incidentes similares (también la usa el formulario de registro). */
export function renderSimilar(container, list) {
    container.replaceChildren();
    if (list.length === 0) {
        container.append(el('p', 'No se encontraron incidentes resueltos parecidos.', 'muted'));
        return;
    }
    const ul = el('ul', null, 'similar-list');
    list.forEach(s => {
        const li = el('li');
        li.append(
            el('strong', `#${s.id} · ${s.title}`),
            el('span', ` (${s.categoryName}, similitud ${Math.round(s.similarity * 100)} %)`, 'muted'),
            el('p', s.resolution ? `Resolución: ${s.resolution}` : 'Sin notas de resolución.'),
        );
        ul.append(li);
    });
    container.append(ul);
}

/** Muestra el detalle en `card`. onChanged se llama cuando cambia el incidente. */
export async function showIncidentDetail(card, incidentId, { users, categories = [], onChanged }) {
    card.hidden = false;
    card.replaceChildren(el('p', 'Cargando…', 'muted'));
    try {
        const [incident, activity] = await Promise.all([
            api(`/incidents/${incidentId}`),
            api(`/incidents/${incidentId}/activity`),
        ]);
        render(card, incident, activity, users, categories, onChanged);
        card.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (error) {
        card.replaceChildren();
        const messages = el('div', null, 'messages');
        card.append(messages);
        showError(messages, error);
    }
}

function render(card, incident, activity, users, categories, onChanged) {
    const reload = () => showIncidentDetail(card, incident.id, { users, categories, onChanged });
    const messages = el('div', null, 'messages');

    // Encabezado
    const header = el('div', null, 'detail-header');
    const close = el('button', 'Cerrar', 'btn-small');
    close.type = 'button';
    close.addEventListener('click', () => { card.hidden = true; });
    header.append(el('h3', `#${incident.id} · ${incident.title}`),
        badge(statusLabel(incident.status), `status-${incident.status}`),
        badge(priorityLabel(incident.priority), incident.priority));
    if (incident.slaStatus) header.append(badge(`SLA: ${slaLabel(incident.slaStatus)}`, `sla-${incident.slaStatus}`));
    header.append(close);

    // Datos
    const info = el('dl', null, 'detail-info');
    [
        ['Categoría', incident.categoryName],
        ['Responsable', incident.userName],
        ['Ubicación', incident.location || '—'],
        ['Creado', formatDate(incident.createdAt)],
        ['Resuelto', formatDate(incident.resolvedAt) || '—'],
        ['Vence SLA', formatDate(incident.slaDueAt) || '—'],
    ].forEach(([k, v]) => info.append(el('dt', k), el('dd', v)));

    card.replaceChildren(header, messages, el('p', incident.description), info);
    if (incident.resolution) card.append(el('h4', 'Resolución'), el('p', incident.resolution));

    card.append(statusForm(incident, users, messages, onChanged, reload));
    card.append(editForm(incident, users, categories, messages, onChanged, reload));
    card.append(aiTools(incident, messages));
    card.append(timeline(incident, activity, users, messages, reload));
}

/** Cambiar estado con nota de resolución opcional. */
function statusForm(incident, users, messages, onChanged, reload) {
    const form = el('form', null, 'form-grid');
    const status = el('select');
    status.append(...optionsFrom(STATUS_LABELS));
    status.value = incident.status;
    const user = userSelect(users, 'Sin indicar');
    const resolution = el('textarea');
    resolution.maxLength = 5000;
    resolution.placeholder = 'Cómo se resolvió (opcional)';
    resolution.value = incident.resolution ?? '';
    const save = el('button', 'Guardar estado', 'btn');

    form.append(field('Estado', status), field('Hecho por', user), field('Resolución', resolution, true),
        actions(save));
    form.addEventListener('submit', async event => {
        event.preventDefault();
        clearMessage(messages);
        save.disabled = true;
        try {
            await api(`/incidents/${incident.id}/status`, {
                method: 'PATCH',
                body: { status: status.value, resolution: resolution.value.trim() || null, userId: user.value ? Number(user.value) : null },
            });
            onChanged?.();
            await reload();
        } catch (error) {
            showError(messages, error);
            save.disabled = false;
        }
    });
    const box = el('div', null, 'detail-section');
    box.append(el('h4', 'Cambiar estado'), form);
    return box;
}

/** Editar datos del incidente (PUT /api/incidents/{id}). */
function editForm(incident, users, categories, messages, onChanged, reload) {
    const details = el('details', null, 'detail-section');
    details.append(el('summary', 'Editar incidente'));

    const form = el('form', null, 'form-grid');
    const title = el('input');
    title.maxLength = 200;
    title.value = incident.title;
    const description = el('textarea');
    description.maxLength = 5000;
    description.value = incident.description;
    const category = el('select');
    category.append(...categories.map(c => new Option(c.name, c.id)));
    category.value = incident.categoryId;
    const priority = el('select');
    priority.append(...optionsFrom(PRIORITY_LABELS));
    priority.value = incident.priority;
    const user = el('select');
    user.append(...users.map(u => new Option(u.name, u.id)));
    user.value = incident.userId;
    const location = el('input');
    location.maxLength = 100;
    location.value = incident.location ?? '';
    const save = el('button', 'Guardar cambios', 'btn');

    form.append(field('Título', title, true), field('Descripción', description, true),
        field('Categoría', category), field('Prioridad', priority), field('Responsable', user),
        field('Ubicación', location), actions(save));
    form.addEventListener('submit', async event => {
        event.preventDefault();
        clearMessage(messages);
        save.disabled = true;
        try {
            await api(`/incidents/${incident.id}`, {
                method: 'PUT',
                body: {
                    title: title.value.trim(),
                    description: description.value.trim(),
                    categoryId: Number(category.value),
                    priority: priority.value,
                    userId: Number(user.value),
                    location: location.value.trim() || null,
                },
            });
            onChanged?.();
            await reload();
        } catch (error) {
            showError(messages, error);
            save.disabled = false;
        }
    });
    details.append(form);
    return details;
}

/** Similares, resumen y borrador de artículo. */
function aiTools(incident, messages) {
    const box = el('div', null, 'detail-section');
    const results = el('div');
    const similar = el('button', 'Buscar incidentes similares', 'btn btn-secondary');
    similar.type = 'button';
    similar.addEventListener('click', async () => {
        clearMessage(messages);
        similar.disabled = true;
        try {
            const list = await api('/incidents/similar', {
                method: 'POST',
                params: { excludeId: incident.id },
                body: { title: incident.title, description: incident.description },
            });
            renderSimilar(results, list);
        } catch (error) {
            showError(messages, error);
        } finally {
            similar.disabled = false;
        }
    });

    const summary = el('button', 'Resumir con IA', 'btn btn-secondary');
    summary.type = 'button';
    summary.addEventListener('click', async () => {
        clearMessage(messages);
        summary.disabled = true;
        try {
            const result = await api(`/incidents/${incident.id}/summary`, { method: 'POST' });
            results.replaceChildren(el('h4', 'Resumen'), el('p', result.summary));
        } catch (error) {
            showError(messages, error);
        } finally {
            summary.disabled = false;
        }
    });

    const buttons = el('div', null, 'form-actions');
    buttons.append(similar, summary);

    // Solo los resueltos se pueden documentar
    if (incident.status === 'RESOLVED' || incident.status === 'CLOSED') {
        const draft = el('button', 'Crear artículo con IA', 'btn btn-secondary');
        draft.type = 'button';
        draft.addEventListener('click', async () => {
            clearMessage(messages);
            draft.disabled = true;
            try {
                const article = await api(`/incidents/${incident.id}/article-draft`, { method: 'POST' });
                showMessage(messages, `Borrador #${article.id} creado sin verificar. Revísalo en "Base de conocimiento".`,
                    'success', { timeout: 0 });
                document.getElementById('knowledge-base')?.dispatchEvent(new CustomEvent('knowledge:changed'));
            } catch (error) {
                showError(messages, error);
            } finally {
                draft.disabled = false;
            }
        });
        buttons.append(draft);
    }

    box.append(el('h4', 'Asistencia IA'), buttons, results);
    return box;
}

/** Bitácora y formulario de comentarios. */
function timeline(incident, activity, users, messages, reload) {
    const box = el('div', null, 'detail-section');
    const list = el('ol', null, 'timeline');
    activity.forEach(a => {
        const li = el('li');
        li.append(el('span', `${formatDate(a.createdAt)}${a.userName ? ' · ' + a.userName : ''}`, 'muted'),
            el('strong', ' ' + activityText(a)));
        if (a.comment) li.append(el('p', a.comment));
        list.append(li);
    });
    if (activity.length === 0) list.append(el('li', 'Sin actividad registrada.', 'muted'));

    const form = el('form', null, 'form-grid');
    const user = userSelect(users, 'Autor…');
    const comment = el('textarea');
    comment.maxLength = 2000;
    comment.placeholder = 'Escribe un comentario';
    const send = el('button', 'Comentar', 'btn');
    form.append(field('Autor', user), field('Comentario', comment, true), actions(send));
    form.addEventListener('submit', async event => {
        event.preventDefault();
        clearMessage(messages);
        if (!user.value || !comment.value.trim()) {
            showMessage(messages, 'Elige el autor y escribe el comentario.', 'error');
            return;
        }
        send.disabled = true;
        try {
            await api(`/incidents/${incident.id}/comments`, {
                method: 'POST',
                body: { userId: Number(user.value), comment: comment.value.trim() },
            });
            await reload();
        } catch (error) {
            showError(messages, error);
            send.disabled = false;
        }
    });

    box.append(el('h4', 'Bitácora'), list, form);
    return box;
}

function activityText(a) {
    if (a.type === 'CREATED') return 'Incidente registrado';
    if (a.type === 'COMMENT') return 'Comentario';
    return a.fromStatus === a.toStatus
        ? 'Nota de resolución actualizada'
        : `Estado: ${statusLabel(a.fromStatus) || '—'} → ${statusLabel(a.toStatus)}`;
}

function field(label, input, wide) {
    const wrap = el('div', null, wide ? 'field field-wide' : 'field');
    wrap.append(el('label', label), input);
    return wrap;
}

function actions(button) {
    const wrap = el('div', null, 'form-actions field-wide');
    button.type = 'submit';
    wrap.append(button);
    return wrap;
}
