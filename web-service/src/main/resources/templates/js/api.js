// Lightweight API wrapper for fetch calls
export async function fetchStage() {
  const res = await fetch('/api/stage');
  if (!res.ok) throw new Error(`fetchStage failed: ${res.status}`);
  return res.json();
}

export async function fetchProvinces() {
  const res = await fetch('/api/provinces');
  if (!res.ok) throw new Error(`fetchProvinces failed: ${res.status}`);
  return res.json();
}

export async function fetchTowns(province) {
  const res = await fetch(`/api/towns/${encodeURIComponent(province)}`);
  if (!res.ok) throw new Error(`fetchTowns failed: ${res.status}`);
  return res.json();
}

export async function fetchSchedule(province, town) {
  const res = await fetch(`/api/schedule/${encodeURIComponent(province)}/${encodeURIComponent(town)}`);
  if (!res.ok) throw new Error(`fetchSchedule failed: ${res.status}`);
  return res.json();
}

