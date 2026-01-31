export function formatTimeStr(t) {
  if (!t && t !== 0) return '??:??';
  const parts = t.toString().split(',');
  const hours = parts[0].toString().padStart(2, '0');
  const minutes = (parts[1] || '0').toString().padStart(2, '0');
  return `${hours}:${minutes}`;
}

export function setLastUpdated(el) {
  const now = new Date();
  el.textContent = `Last updated: ${now.toLocaleString()}`;
}

export function createDownloadJson(obj, filename) {
  const blob = new Blob([JSON.stringify(obj, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

