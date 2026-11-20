// Knowledge base view (RF02): article CRUD, activate and deactivate.
// Uses GET/POST /api/knowledge-articles, PUT /{id} and PATCH /{id}/activate | /{id}/deactivate.
// Only active and verified articles are RAG sources; the table shows which ones the AI may cite.
import { api } from '../api.js';
import { showMessage, showError, clearMessage } from '../ui/messages.js';
import { readForm, fillForm, showFieldErrors, clearFieldErrors } from '../ui/forms.js';
import { renderTable } from '../ui/table.js';
import { badge, formatDate } from '../ui/format.js';

/** Fired on the section when an article is created or changed, so the list can reload. */
export const ARTICLES_CHANGED = 'knowledge:changed';

// Static markup only (no API data), so innerHTML is safe here. Names match KnowledgeArticleRequest.
const FORM_HTML = `
    <h3 data-role="form-title">Nuevo artículo</h3>
    <form class="form-grid" novalidate>
        <div class="field field-wide">
            <label for="article-title">Título</label>
            <input id="article-title" name="title" maxlength="200" required>
        </div>
        <div class="field field-wide">
            <label for="article-problem">Descripción del problema</label>
            <textarea id="article-problem" name="problem" maxlength="5000" required></textarea>
        </div>
        <div class="field">
            <label for="article-symptoms">Síntomas</label>
            <textarea id="article-symptoms" name="symptoms" maxlength="5000" required></textarea>
        </div>
        <div class="field">
            <label for="article-cause">Causa</label>
            <textarea id="article-cause" name="cause" maxlength="5000" required></textarea>
        </div>
        <div class="field field-wide">
            <label for="article-solution">Procedimiento de solución</label>
            <textarea id="article-solution" name="solution" maxlength="5000" required></textarea>
        </div>
        <div class="field field-check field-wide">
            <input id="article-verified" name="verified" type="checkbox">
            <label for="article-verified">Verificado (solo los artículos activos y verificados se usan como fuente de la IA)</label>
        </div>
        <div class="form-actions field-wide">
            <button type="submit" class="btn">Guardar</button>
            <button type="reset" class="btn btn-secondary">Cancelar</button>
        </div>
    </form>`;

const SEARCH_HTML = `
    <h3>Búsqueda semántica</h3>
    <form class="form-grid" novalidate>
        <div class="field field-wide">
            <label for="article-search">Describe el problema con tus palabras</label>
            <input id="article-search" name="q" maxlength="500">
        </div>
        <div class="form-actions field-wide">
            <button type="submit" class="btn">Buscar</button>
        </div>
    </form>
    <div class="table-container"></div>`;

/** Called once, the first time the view is opened. */
export async function init(section) {
    const messages = document.createElement('div');
    messages.className = 'messages';

    const formCard = document.createElement('div');
    formCard.className = 'card';
    formCard.innerHTML = FORM_HTML;

    const listCard = document.createElement('div');
    listCard.className = 'card';
    const listTitle = document.createElement('h3');
    listTitle.textContent = 'Artículos';
    const table = document.createElement('div');
    listCard.append(listTitle, table);

    // Búsqueda semántica (sin generar respuesta)
    const searchCard = document.createElement('div');
    searchCard.className = 'card';
    searchCard.innerHTML = SEARCH_HTML; // static markup only
    const searchForm = searchCard.querySelector('form');
    const searchResults = searchCard.querySelector('.table-container');

    section.append(messages, searchCard, formCard, listCard);

    const form = formCard.querySelector('form');
    const formTitle = formCard.querySelector('[data-role="form-title"]');
    const state = { editingId: null };

    const startEdit = article => {
        state.editingId = article.id;
        formTitle.textContent = `Editar artículo #${article.id}`;
        clearFieldErrors(form);
        fillForm(form, article);
        form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    };
    const cancelEdit = () => {
        state.editingId = null;
        formTitle.textContent = 'Nuevo artículo';
        clearFieldErrors(form);
    };

    form.addEventListener('submit', event => submitArticle(event, form, state, messages, section));
    form.addEventListener('reset', cancelEdit);

    searchForm.addEventListener('submit', event => {
        event.preventDefault();
        searchArticles(searchForm, searchResults, messages, startEdit);
    });

    const reload = () => loadArticles(table, messages, section, startEdit);
    section.addEventListener(ARTICLES_CHANGED, reload);
    await reload();
}

/** POST for a new article, PUT when editing. The vector store is synced by the backend after commit. */
async function submitArticle(event, form, state, messages, section) {
    event.preventDefault();
    clearMessage(messages);
    clearFieldErrors(form);

    const button = form.querySelector('button[type="submit"]');
    button.disabled = true; // saving also indexes the article in n8n, which can take a moment
    try {
        const body = readForm(form);
        const saved = state.editingId === null
            ? await api('/knowledge-articles', { method: 'POST', body })
            : await api(`/knowledge-articles/${state.editingId}`, { method: 'PUT', body });
        form.reset(); // also leaves edit mode through the reset listener
        showMessage(messages, `Artículo #${saved.id} guardado. ${sourceNote(saved)}`, 'success');
        section.dispatchEvent(new CustomEvent(ARTICLES_CHANGED, { detail: saved }));
    } catch (error) {
        showFormError(form, messages, error);
    } finally {
        button.disabled = false;
    }
}

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

async function loadArticles(table, messages, section, onEdit) {
    try {
        const articles = await api('/knowledge-articles');
        renderArticles(table, articles, messages, section, onEdit);
    } catch (error) {
        showError(messages, error);
    }
}

function renderArticles(table, articles, messages, section, onEdit) {
    renderTable(table, [
        { header: '#', value: a => a.id },
        { header: 'Título', value: a => a.title },
        { header: 'Estado', value: a => badge(a.active ? 'Activo' : 'Inactivo', a.active ? 'status-open' : 'query-no_results') },
        { header: 'Verificado', value: a => a.verified ? 'Sí' : 'No' },
        { header: 'Fuente IA', value: a => badge(a.usableAsSource ? 'Sí' : 'No', a.usableAsSource ? 'status-resolved' : 'query-no_results') },
        // Marcado por calificaciones bajas (1-2) de respuestas que lo usaron
        { header: 'Revisión', value: a => a.needsReview ? badge('Revisar', 'query-error') : null },
        { header: 'Actualizado', value: a => formatDate(a.updatedAt) },
        {
            header: 'Acciones',
            actions: [
                { label: 'Editar', onClick: onEdit },
                { label: 'Desactivar', visible: a => a.active, onClick: a => setActive(a, false, messages, section) },
                { label: 'Activar', visible: a => !a.active, onClick: a => setActive(a, true, messages, section) },
                { label: 'Marcar revisado', visible: a => a.needsReview, onClick: a => markReviewed(a, messages, section) },
                { label: 'Eliminar', onClick: a => removeArticle(a, messages, section) },
            ],
        },
    ], articles, { emptyText: 'No hay artículos registrados.' });
}

/** PATCH /{id}/activate or /{id}/deactivate; a deactivated article is removed from the vector store. */
async function setActive(article, active, messages, section) {
    clearMessage(messages);
    try {
        const updated = await api(`/knowledge-articles/${article.id}/${active ? 'activate' : 'deactivate'}`, { method: 'PATCH' });
        showMessage(messages, `Artículo #${updated.id} ${active ? 'activado' : 'desactivado'}. ${sourceNote(updated)}`, 'success');
        section.dispatchEvent(new CustomEvent(ARTICLES_CHANGED, { detail: updated }));
    } catch (error) {
        showError(messages, error);
    }
}

/** Tells the user whether the AI can cite the article now (test 6). */
function sourceNote(article) {
    return article.usableAsSource
        ? 'La IA puede usarlo como fuente.'
        : 'La IA no lo usará como fuente.';
}

/** GET /api/knowledge-articles/search: artículos ordenados por similitud. */
async function searchArticles(form, container, messages, onEdit) {
    clearMessage(messages);
    const q = form.elements.q.value.trim();
    if (!q) {
        showMessage(messages, 'Escribe qué quieres buscar.', 'error');
        return;
    }
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
        const results = await api('/knowledge-articles/search', { params: { q } });
        renderTable(container, [
            { header: 'Similitud', value: r => `${Math.round(r.similarity * 100)} %` },
            { header: '#', value: r => r.id },
            { header: 'Título', value: r => r.title },
            { header: 'Fuente IA', value: r => badge(r.usableAsSource ? 'Sí' : 'No', r.usableAsSource ? 'status-resolved' : 'query-no_results') },
            {
                header: 'Acciones',
                actions: [{
                    label: 'Editar',
                    onClick: async r => {
                        try {
                            onEdit(await api(`/knowledge-articles/${r.id}`));
                        } catch (error) {
                            showError(messages, error);
                        }
                    },
                }],
            },
        ], results, { emptyText: 'No hay artículos parecidos.' });
    } catch (error) {
        showError(messages, error);
    } finally {
        button.disabled = false;
    }
}

/** DELETE /{id}: el backend responde 409 si el artículo ya fue fuente de la IA. */
async function removeArticle(article, messages, section) {
    if (!window.confirm(`¿Eliminar el artículo #${article.id}?`)) return;
    clearMessage(messages);
    try {
        await api(`/knowledge-articles/${article.id}`, { method: 'DELETE' });
        showMessage(messages, `Artículo #${article.id} eliminado.`, 'success');
        section.dispatchEvent(new CustomEvent(ARTICLES_CHANGED));
    } catch (error) {
        showError(messages, error);
    }
}

async function markReviewed(article, messages, section) {
    clearMessage(messages);
    try {
        await api(`/knowledge-articles/${article.id}/reviewed`, { method: 'PATCH' });
        showMessage(messages, `Artículo #${article.id} marcado como revisado.`, 'success');
        section.dispatchEvent(new CustomEvent(ARTICLES_CHANGED));
    } catch (error) {
        showError(messages, error);
    }
}
