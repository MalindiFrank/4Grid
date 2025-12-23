import { formatTimeStr, setLastUpdated, createDownloadJson } from './helper.js';
import { fetchProvinces, fetchTowns, fetchSchedule, fetchStage } from './api.js';

export function createUIRefs() {
    return {
        stageEl: document.getElementById('s7j2lmq4'),
        provincesEl: document.getElementById('h2jqxf8w'),
        townsEl: document.getElementById('r9jckx1v'),
        scheduleEl: document.getElementById('w7tkbc3n'),
        selectedProvinceEl: document.getElementById('o7knvb2m'),
        scheduleTitleEl: document.getElementById('d5n2kpx9'),
        lastUpdatedEl: document.getElementById('n6jqdw9e'),
        refreshBtn: document.getElementById('p2m8xkl4'),
        exportBtn: document.getElementById('m8fqwt4z'),
        provincesSection: document.getElementById('v8n3xpr6'),
        townsSection: document.getElementById('b9mxkdw3'),
        scheduleSection: document.getElementById('x6mwqbz5'),
    };
}

export async function loadStageAndRender(element) {
    try {
        const data = await fetchStage();
        element.stageEl.textContent = `Stage ${data.stage ?? 0}`;
        setLastUpdated(element.lastUpdatedEl);
    } catch (err) {
        console.error(err);
        element.stageEl.textContent = 'Error loading stage.';
    }
}

export function connectToStageUpdates(element) {
    const eventSource = new EventSource('/api/stage-updates');
    eventSource.addEventListener('stage-update', (event) => {
        const data = JSON.parse(event.data);
        element.stageEl.textContent = `Stage ${data.stage ?? 0}`;
        setLastUpdated(element.lastUpdatedEl);
    });
    eventSource.onerror = (err) => console.error('SSE Error:', err);
}

export async function loadProvincesAndRender(element, state, force = false) {
    try {
        if (!Array.isArray(state.provinces)) state.provinces = [];
        if (state.provinces.length === 0 || force) {
            const list = await fetchProvinces();
            state.provinces.splice(0, state.provinces.length, ...list);
        }
        renderProvinces(element, state);
        setLastUpdated(element.lastUpdatedEl);
    } catch (err) {
        console.error(err);
        element.provincesEl.innerHTML = '<div class="error">Error loading provinces.</div>';
    }
}

export function renderProvinces(element, state) {
    const list = state.provinces || [];

    element.provincesEl.innerHTML = '';
    if (list.length === 0) {
        element.provincesEl.innerHTML = '<div class="empty">No provinces found.</div>';
        return;
    }

    list.forEach(p => {
        const card = document.createElement('div');
        card.className = 'card clickable';
        card.textContent = p;
        card.onclick = () => loadTownsAndRender(element, state, p);
        element.provincesEl.appendChild(card);
    });
}

export async function loadTownsAndRender(element, state, province) {
    state.currentProvince = province;
    element.provincesSection.classList.add('z4tqhv8n');
    element.townsSection.classList.remove('z4tqhv8n');
    element.selectedProvinceEl.textContent = `Towns in ${province}`;
    element.townsEl.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const list = await fetchTowns(province);
        if (!Array.isArray(state.towns)) state.towns = [];
        state.towns.splice(0, state.towns.length, ...list);
        renderTowns(element, state);
        setLastUpdated(element.lastUpdatedEl);
    } catch (err) {
        console.error(err);
        element.townsEl.innerHTML = '<div class="error">Error loading towns.</div>';
    }
}

export function renderTowns(element, state) {
    const list = state.towns || [];

    element.townsEl.innerHTML = '';
    if (list.length === 0) {
        element.townsEl.innerHTML = '<div class="empty">No towns found.</div>';
        return;
    }

    list.forEach(t => {
        const card = document.createElement('div');
        card.className = 'card clickable';
        card.textContent = t.name;
        card.onclick = () => loadScheduleAndRender(element, state, t.name, state.currentProvince);
        element.townsEl.appendChild(card);
    });
}

export async function loadScheduleAndRender(element, state, town, province) {
    state.currentTown = town;
    state.currentProvince = province || state.currentProvince;
    element.townsSection.classList.add('z4tqhv8n');
    element.scheduleSection.classList.remove('z4tqhv8n');
    element.scheduleTitleEl.textContent = `Schedule for ${town}`;
    element.scheduleEl.innerHTML = '<div class="loading">Loading...</div>';

    try {
        const data = await fetchSchedule(state.currentProvince, town);
        state.currentSchedule = data;
        if (!data || !data.days || data.days.length === 0) {
            element.scheduleEl.innerHTML = '<div class="empty">No schedule available.</div>';
            return;
        }

        element.scheduleEl.innerHTML = '';
        let currentDate = data.startDate ? new Date(data.startDate) : new Date();

        data.days.forEach(day => {
            const dateStr = `${currentDate.getDate()}/${currentDate.getMonth()+1}/${currentDate.getFullYear()}`;
            const li = document.createElement('li');
            li.className = 'schedule-day';
            const slotsHtml = (day.slots && day.slots.length)
                ? `<ul class="slots">${day.slots.map(slot=>`<li>${formatTimeStr(slot.start)} - ${formatTimeStr(slot.end)}</li>`).join('')}</ul>`
                : '<div class="empty">No slots</div>';
            li.innerHTML = `<div class="day-header"><strong>${dateStr}</strong></div>${slotsHtml}`;
            element.scheduleEl.appendChild(li);
            currentDate.setDate(currentDate.getDate() + 1);
        });
        setLastUpdated(element.lastUpdatedEl);
    } catch (err) {
        console.error(err);
        element.scheduleEl.innerHTML = '<div class="error">Error loading schedule.</div>';
    }
}

export function attachUIHandlers(element, state) {
    element.refreshBtn.addEventListener('click', async () => {
        await Promise.all([
            loadProvincesAndRender(element, state, true),
            loadStageAndRender(element)
        ]);
    });

    element.exportBtn.addEventListener('click', () => {
        if (!state.currentSchedule) return;
        createDownloadJson(state.currentSchedule, `${state.currentProvince || 'province'}-${state.currentTown || 'town'}-schedule.json`);
    });

    document.getElementById('l5p3rjx9').onclick = () => {
        element.townsSection.classList.add('z4tqhv8n');
        element.provincesSection.classList.remove('z4tqhv8n');
    };

    document.getElementById('c3v8hjt7').onclick = () => {
        element.scheduleSection.classList.add('z4tqhv8n');
        element.townsSection.classList.remove('z4tqhv8n');
    };
}
