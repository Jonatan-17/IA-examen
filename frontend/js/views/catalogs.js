// Catálogos: CRUD de categorías y usuarios (GET/POST /api/{recurso}, PUT/DELETE /api/{recurso}/{id}).
import { api } from '../api.js';
import { showMessage, showError, clearMessage } from '../ui/messages.js';
import { readForm, fillForm, showFieldErrors, clearFieldErrors } from '../ui/forms.js';
import { renderTable } from '../ui/table.js';

const CATALOGS = [
    {
        title: 'Categorías',
        singular: 'Categoría',
        path: '/categories',
        fields: [
            { name: 'name', label: 'Nombre', max: 80 },
            { name: 'description', label: 'Descripción', max: 255 },
        ],
    },
    {
        title: 'Usuarios',
        singular: 'Usuario',
        path: '/users',
        fields: [
            { name: 'name', label: 'Nombre', max: 100 },
            { name: 'email', label: 'Correo', max: 150, type: 'email' },
            { name: 'area', label: 'Área', max: 100 },
        ],
    },
];

export async function init(section) {
    const messages = document.createElement('div');
    messages.className = 'messages';
    section.append(messages);
    for (const catalog of CATALOGS) {
        await catalogCard(section, catalog, messages);
    }
}

async function catalogCard(section, catalog, messages) {
    const card = document.createElement('div');
    card.className = 'card';
    const heading = document.createElement('h3');
    const form = document.createElement('form');
    form.className = 'form-grid';
    form.noValidate = true;

    catalog.fields.forEach(f => {
        const wrap = document.createElement('div');
        wrap.className = 'field';
        const label = document.createElement('label');
        label.textContent = f.label;
        const input = document.createElement('input');
        input.name = f.name;
        input.maxLength = f.max;
        input.type = f.type ?? 'text';
        wrap.append(label, input);
        form.append(wrap);
    });

    const actions = document.createElement('div');
    actions.className = 'form-actions field-wide';
    const save = document.createElement('button');
    save.type = 'submit';
    save.className = 'btn';
    save.textContent = 'Guardar';
    const cancel = document.createElement('button');
    cancel.type = 'reset';
    cancel.className = 'btn btn-secondary';
    cancel.textContent = 'Cancelar';
    actions.append(save, cancel);
    form.append(actions);

    const table = document.createElement('div');
    card.append(heading, form, table);
    section.append(card);

    const state = { editingId: null };
    const setTitle = () => {
        heading.textContent = state.editingId === null
            ? `${catalog.title} · nuevo registro`
            : `${catalog.title} · editando #${state.editingId}`;
    };
    setTitle();

    const reload = async () => {
        try {
            const rows = await api(catalog.path);
            renderTable(table, [
                { header: '#', value: r => r.id },
                ...catalog.fields.map(f => ({ header: f.label, value: r => r[f.name] })),
                {
                    header: 'Acciones',
                    actions: [
                        { label: 'Editar', onClick: r => { state.editingId = r.id; fillForm(form, r); setTitle(); } },
                        { label: 'Eliminar', onClick: r => remove(r) },
                    ],
                },
            ], rows, { emptyText: 'Sin registros.' });
        } catch (error) {
            showError(messages, error);
        }
    };

    // 409 si tiene incidentes asociados (el mensaje viene del backend)
    const remove = async row => {
        if (!window.confirm(`¿Eliminar ${catalog.singular.toLowerCase()} #${row.id}?`)) return;
        clearMessage(messages);
        try {
            await api(`${catalog.path}/${row.id}`, { method: 'DELETE' });
            showMessage(messages, `${catalog.singular} #${row.id} eliminado.`, 'success');
            await reload();
        } catch (error) {
            showError(messages, error);
        }
    };

    form.addEventListener('reset', () => {
        state.editingId = null;
        clearFieldErrors(form);
        setTitle();
    });
    form.addEventListener('submit', async event => {
        event.preventDefault();
        clearMessage(messages);
        clearFieldErrors(form);
        save.disabled = true;
        try {
            const body = readForm(form);
            const saved = state.editingId === null
                ? await api(catalog.path, { method: 'POST', body })
                : await api(`${catalog.path}/${state.editingId}`, { method: 'PUT', body });
            form.reset();
            showMessage(messages, `${catalog.singular} #${saved.id} guardado.`, 'success');
            await reload();
        } catch (error) {
            const unplaced = showFieldErrors(form, error);
            if (unplaced.length) showMessage(messages, unplaced.join(' '), 'error');
            else if (error?.status !== 400) showError(messages, error);
        } finally {
            save.disabled = false;
        }
    });

    await reload();
}
