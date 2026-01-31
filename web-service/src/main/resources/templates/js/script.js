import { createUIRefs, attachUIHandlers, loadStageAndRender, loadProvincesAndRender, connectToStageUpdates } from './document.js';

const state = {
  provinces: [],
  towns: [],
  currentProvince: null,
  currentTown: null,
  currentSchedule: null,
};

const el = createUIRefs();
attachUIHandlers(el, state);

// Home button → back to provinces
const homeBtn = document.getElementById('q8f3vnx1');
if (homeBtn) {
  homeBtn.addEventListener('click', () => {
    el.townsSection.classList.add('z4tqhv8n');
    el.scheduleSection.classList.add('z4tqhv8n');
    el.provincesSection.classList.remove('z4tqhv8n');
    if (state.provinces.length) {
      loadProvincesAndRender(el, state, false);
    }
  });

  homeBtn.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      homeBtn.click();
    }
  });
}

// Start the app
loadStageAndRender(el);
loadProvincesAndRender(el, state);
connectToStageUpdates(el);