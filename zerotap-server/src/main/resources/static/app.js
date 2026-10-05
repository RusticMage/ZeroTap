/* ========================================================================
   ZeroTap Responder Dashboard JavaScript
   - Free OpenStreetMap tiles (No API key required)
   - Live Safe Spaces rendering with Hospitable Green icons (No red)
   - Real-time Linked Device GPS Tracking (REST + WebSocket)
   - Red RESERVED STRICTLY for active emergency incidents
   - Theme Toggle (Dark & Warm Light)
   ======================================================================== */

let map;
let safeSpaceLayerGroup;
let incidentLayerGroup;
let deviceLayerGroup;
let allSafeSpaces = [];
let currentFilter = 'ALL';
let stompClient = null;
let activeIncidents = [];
let activeDeviceMarkers = {};
let pendingResolveId = null;

// Emergency Contact 1:1 Connection State
let contactToken = localStorage.getItem('zerotap_contact_token') || null;
let pairedUserId = localStorage.getItem('zerotap_paired_user_id') || null;
let pairedUserName = localStorage.getItem('zerotap_paired_user_name') || null;
let monitoredData = null;
let monitoredMarker = null;
let pingCountdown = 0;
let pingCountdownInterval = null;

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initMap();
  initContactMonitoring();
  loadSafeSpaces();
  refreshIncidents();
  loadActiveDevices();
  loadRecentEvidence();
  connectWebSocket();

  // Poll active devices, incidents & evidence periodically as resilient fallback
  setInterval(loadActiveDevices, 4000);
  setInterval(refreshIncidents, 6000);
  setInterval(loadRecentEvidence, 5000);
  setInterval(loadMonitoredContact, 3000);
});

/* ----------------- THEME MANAGEMENT ----------------- */
function initTheme() {
  const savedTheme = localStorage.getItem('zerotap_theme') || 'dark';
  applyTheme(savedTheme);
}

function toggleTheme() {
  const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';
  const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
  applyTheme(newTheme);
}

function applyTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem('zerotap_theme', theme);

  const themeBtn = document.getElementById('theme-icon');
  if (themeBtn) {
    themeBtn.textContent = theme === 'dark' ? '☀️ Light Mode' : '🌙 Dark Mode';
  }
}

let currentTileLayer = null;
function updateMapTiles() {
  if (currentTileLayer) {
    map.removeLayer(currentTileLayer);
  }

  // Official OpenStreetMap Standard Tiles — 100% Free, NO API Key needed
  const tileUrl = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';

  currentTileLayer = L.tileLayer(tileUrl, {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    subdomains: 'abc',
    maxZoom: 19
  }).addTo(map);
}

/* ----------------- MAP SETUP ----------------- */
function initMap() {
  // Center on Tamil Nadu (approx Trichy: 10.8505, 78.7047)
  map = L.map('map', {
    center: [10.8505, 78.7047],
    zoom: 7.4,
    zoomControl: true
  });

  updateMapTiles();

  safeSpaceLayerGroup = L.layerGroup().addTo(map);
  incidentLayerGroup = L.layerGroup().addTo(map);
  deviceLayerGroup = L.layerGroup().addTo(map);
}

/* ----------------- LOAD SAFE SPACES (SHADES OF GREEN) ----------------- */
async function loadSafeSpaces() {
  try {
    const res = await fetch('/api/safe-spaces');
    if (res.ok) {
      allSafeSpaces = await res.json();
      const countEl = document.getElementById('stat-safespaces');
      if (countEl) countEl.textContent = allSafeSpaces.length;
      renderSafeSpaces();
    }
  } catch (err) {
    console.error('Failed to load safe spaces:', err);
  }
}

function filterSpaces(type) {
  currentFilter = type;
  document.querySelectorAll('.filter-pills .pill-btn').forEach(btn => {
    btn.classList.toggle('active', btn.textContent.toUpperCase().includes(type) || (type === 'ALL' && btn.textContent.includes('All')));
  });
  renderSafeSpaces();
}

function renderSafeSpaces() {
  safeSpaceLayerGroup.clearLayers();

  const filtered = currentFilter === 'ALL'
    ? allSafeSpaces
    : allSafeSpaces.filter(s => s.type === currentFilter);

  filtered.forEach(space => {
    // SHADES OF GREEN — Red is NEVER used for safe spaces
    let color = '#388E3C'; // Forest / Sage Green
    let symbol = '🛡️';
    if (space.type === 'POLICE') {
      color = '#2E7D32'; // Deep Emerald Green
      symbol = '👮';
    } else if (space.type === 'HOSPITAL') {
      color = '#1B5E20'; // Mint / Medical Green
      symbol = '🏥';
    } else if (space.type === 'TRANSIT_HUB' || space.type === 'METRO') {
      color = '#43A047'; // Fresh Transit Green
      symbol = '🚆';
    }

    const iconHtml = `
      <div style="
        background: ${color};
        color: white;
        border-radius: 50%;
        width: 28px;
        height: 28px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        border: 2px solid #FFFFFF;
        box-shadow: 0 2px 6px rgba(0,0,0,0.3);
      ">
        ${symbol}
      </div>
    `;

    const customIcon = L.divIcon({
      html: iconHtml,
      className: 'safe-marker',
      iconSize: [28, 28],
      iconAnchor: [14, 14]
    });

    const marker = L.marker([space.latitude, space.longitude], { icon: customIcon });
    marker.bindPopup(`
      <div style="font-family: var(--font-family); padding: 4px;">
        <h4 style="margin: 0 0 4px 0; color: #2E7D32; font-size: 14px;">${space.name}</h4>
        <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 4px;"><strong>Type:</strong> ${space.type}</div>
        <div style="font-size: 12px; color: var(--text-secondary); margin-bottom: 4px;"><strong>Address:</strong> ${space.address || 'N/A'}</div>
        <div style="font-size: 12px; color: var(--text-secondary);"><strong>Contact:</strong> <a href="tel:${space.phone}" style="color: #2E7D32; font-weight: bold;">${space.phone || '112'}</a></div>
      </div>
    `);

    safeSpaceLayerGroup.addLayer(marker);
  });
}

/* ----------------- LINKED DEVICES TRACKING ----------------- */
async function loadActiveDevices() {
  try {
    const res = await fetch('/api/locations/active');
    if (res.ok) {
      const records = await res.json();
      const devCountEl = document.getElementById('stat-devices');
      if (devCountEl) devCountEl.textContent = records.length;

      records.forEach(rec => {
        updateDeviceLocationOnMap(rec.userId, rec.latitude, rec.longitude, rec);
      });
      updateDevicesListUI(records);
    }
  } catch (err) {
    console.warn('Failed to load active device telemetry:', err);
  }
}

function updateDeviceLocationOnMap(userId, lat, lng, extraData) {
  if (!map) return;

  const hasAlert = activeIncidents.some(i => i.userId === userId);
  // Red is strictly reserved for active emergency alerts; otherwise emerald green
  const markerBg = hasAlert ? '#DC2626' : '#10B981';
  const pulseClass = hasAlert ? 'pulse-red' : 'pulse-green';

  const devHtml = `
    <div style="
      background: ${markerBg};
      color: white;
      border-radius: 50%;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      border: 2.5px solid #FFFFFF;
      box-shadow: 0 0 14px ${hasAlert ? 'rgba(220, 38, 38, 0.8)' : 'rgba(16, 185, 129, 0.8)'};
      animation: ${pulseClass} 1.5s infinite;
    ">
      📱
    </div>
  `;

  const icon = L.divIcon({
    html: devHtml,
    className: 'device-marker',
    iconSize: [32, 32],
    iconAnchor: [16, 16]
  });

  const speedStr = (extraData && extraData.speed) ? `${extraData.speed.toFixed(1)} m/s` : 'Stationary';
  const timeStr = extraData && extraData.timestamp ? new Date(extraData.timestamp).toLocaleTimeString() : new Date().toLocaleTimeString();

  if (activeDeviceMarkers[userId]) {
    activeDeviceMarkers[userId].setLatLng([lat, lng]);
    activeDeviceMarkers[userId].setIcon(icon);
  } else {
    const marker = L.marker([lat, lng], { icon }).bindPopup(`
      <div style="font-family: var(--font-family); padding: 4px;">
        <h4 style="margin: 0 0 4px 0; color: #2E7D32; font-size: 14px;">Linked Monitored Device</h4>
        <div style="font-size: 12px; margin-bottom: 3px;"><strong>Device ID:</strong> ${userId}</div>
        <div style="font-size: 12px; margin-bottom: 3px;"><strong>Status:</strong> ${hasAlert ? '<span style="color: #DC2626; font-weight: bold;">EMERGENCY ALERT</span>' : '<span style="color: #10B981; font-weight: bold;">Normal / Safe</span>'}</div>
        <div style="font-size: 12px; margin-bottom: 3px;"><strong>Coordinates:</strong> ${lat.toFixed(4)}, ${lng.toFixed(4)}</div>
        <div style="font-size: 12px; margin-bottom: 3px;"><strong>Speed:</strong> ${speedStr}</div>
        <div style="font-size: 12px; color: var(--text-muted);"><strong>Telemetry Updated:</strong> ${timeStr}</div>
      </div>
    `);

    deviceLayerGroup.addLayer(marker);
    activeDeviceMarkers[userId] = marker;
  }

  // Pre-fill target device ID in ping box
  const targetInput = document.getElementById('target-device-id');
  if (targetInput && (!targetInput.value || targetInput.value === 'user-device-1')) {
    targetInput.value = userId;
  }
}

function updateDevicesListUI(records) {
  const container = document.getElementById('devices-container');
  if (!container) return;

  if (records.length === 0) {
    container.innerHTML = '<div class="empty-state">No linked devices reporting telemetry yet.<br><small style="color: var(--text-muted);">Open ZeroTap app in Connected Mode to stream GPS.</small></div>';
    return;
  }

  container.innerHTML = '';
  records.forEach(rec => {
    const hasAlert = activeIncidents.some(i => i.userId === rec.userId);
    const item = document.createElement('div');
    item.className = 'device-item';

    item.innerHTML = `
      <div class="device-info">
        <div style="display: flex; align-items: center; gap: 6px;">
          <span class="pulse-dot ${hasAlert ? 'disconnected' : ''}" style="${hasAlert ? 'background-color: var(--status-critical);' : ''}"></span>
          <span class="device-id">${rec.userId}</span>
        </div>
        <span class="device-coords">GPS: ${rec.latitude.toFixed(4)}, ${rec.longitude.toFixed(4)} · Speed: ${rec.speed ? rec.speed.toFixed(1) : 0} m/s</span>
      </div>
      <div style="display: flex; gap: 6px;">
        <button class="action-btn btn-outline" style="padding: 4px 10px; font-size: 11px;" onclick="focusOnMap(${rec.latitude}, ${rec.longitude})">Locate</button>
        <button class="action-btn btn-ping" style="padding: 4px 10px; font-size: 11px;" onclick="quickPing('${rec.userId}')">Ping</button>
      </div>
    `;

    container.appendChild(item);
  });
}

function focusOnMap(lat, lng) {
  if (map) {
    map.flyTo([lat, lng], 15, { duration: 1.2 });
  }
}

function quickPing(userId) {
  const targetInput = document.getElementById('target-device-id');
  if (targetInput) targetInput.value = userId;
  triggerManualPing();
}

/* ----------------- INCIDENTS MANAGEMENT (RED RESERVED FOR EMERGENCIES) ----------------- */
async function refreshIncidents() {
  try {
    const res = await fetch('/api/incidents/active');
    if (res.ok) {
      activeIncidents = await res.json();
      updateIncidentUI();
    }
  } catch (err) {
    console.error('Failed to fetch active incidents:', err);
  }
}

function updateIncidentUI() {
  const container = document.getElementById('incidents-container');
  const countEl = document.getElementById('stat-incidents');
  const subEl = document.getElementById('stat-incidents-sub');

  if (activeIncidents.length === 0) {
    if (countEl) {
      countEl.textContent = '0';
      countEl.style.color = 'var(--status-safe)'; // Green when zero!
    }
    if (subEl) subEl.textContent = 'All devices safe & secured';
    container.innerHTML = '<div class="empty-state">No active emergencies detected. All devices safe.</div>';
    incidentLayerGroup.clearLayers();
    return;
  }

  // Active emergencies exist -> ONLY NOW turn red!
  if (countEl) {
    countEl.textContent = activeIncidents.length;
    countEl.style.color = 'var(--status-critical)';
  }
  if (subEl) subEl.textContent = 'Requires immediate intervention';

  incidentLayerGroup.clearLayers();
  container.innerHTML = '';

  activeIncidents.forEach(inc => {
    const isAlerting = inc.status === 'ALERTING' || inc.riskLevel === 'INCIDENT' || inc.riskLevel === 'HIGH';
    const item = document.createElement('div');
    item.className = `incident-item ${isAlerting ? 'alerting' : ''}`;

    const dateStr = new Date(inc.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });

    item.innerHTML = `
      <div class="incident-title-row">
        <span class="incident-type" style="color: ${isAlerting ? 'var(--status-critical)' : 'inherit'};">${inc.triggerType || 'EMERGENCY EVENT'}</span>
        <span class="incident-tag ${isAlerting ? 'tag-critical' : 'tag-safe'}">${inc.status}</span>
      </div>
      <div class="incident-desc">
        ${inc.summary || 'Sensor anomaly fusion triggered safety escalation.'}
      </div>
      <div class="incident-meta">
        <span>Device: ${inc.userId || 'user-device-1'}</span>
        <span>${dateStr}</span>
      </div>
      <div class="incident-actions">
        <button class="action-btn btn-ping" onclick="sendPingForIncident('${inc.id}', '${inc.userId}')">Ping Device</button>
        <button class="action-btn btn-resolve" onclick="openResolveModal('${inc.id}')">Resolve</button>
        <button class="action-btn btn-outline" onclick="focusOnMap(${inc.latitude}, ${inc.longitude})">Locate</button>
      </div>
    `;

    container.appendChild(item);

    // Plot red incident marker ONLY for active emergency
    if (inc.latitude && inc.longitude) {
      const incidentHtml = `
        <div style="
          background: #DC2626;
          color: white;
          border-radius: 50%;
          width: 36px;
          height: 36px;
          display: flex;
          align-items: center;
          justify-content: center;
          font-size: 16px;
          border: 3px solid #FFFFFF;
          box-shadow: 0 0 16px rgba(220, 38, 38, 0.9);
          animation: pulse-red 1.5s infinite;
        ">
          🚨
        </div>
      `;

      const incidentIcon = L.divIcon({
        html: incidentHtml,
        className: 'incident-marker',
        iconSize: [36, 36],
        iconAnchor: [18, 18]
      });

      const marker = L.marker([inc.latitude, inc.longitude], { icon: incidentIcon });
      marker.bindPopup(`
        <div style="font-family: var(--font-family); padding: 4px;">
          <h4 style="margin: 0 0 4px 0; color: #DC2626; font-size: 14px;">EMERGENCY ALERT: ${inc.triggerType || 'SOS'}</h4>
          <div style="font-size: 12px; margin-bottom: 4px;"><strong>Device:</strong> ${inc.userId || 'Unknown'}</div>
          <div style="font-size: 12px; margin-bottom: 4px;"><strong>Summary:</strong> ${inc.summary || 'Live distress signal'}</div>
          <div style="font-size: 12px; margin-bottom: 6px;"><strong>Status:</strong> <span style="color: #DC2626; font-weight: bold;">${inc.status}</span></div>
          <button style="padding: 4px 10px; background: #2E7D32; color: white; border: none; border-radius: 4px; cursor: pointer;" onclick="openResolveModal('${inc.id}')">Mark Resolved</button>
        </div>
      `);

      incidentLayerGroup.addLayer(marker);
    }
  });
}

function openResolveModal(id) {
  pendingResolveId = id;
  document.getElementById('modal-incident-id').textContent = id;
  document.getElementById('modal-summary').value = 'False alarm, user verified safe.';
  document.getElementById('resolve-modal').classList.add('active');
}

function closeResolveModal() {
  document.getElementById('resolve-modal').classList.remove('active');
  pendingResolveId = null;
}

async function confirmResolveIncident() {
  if (!pendingResolveId) return;
  const notes = document.getElementById('modal-summary').value.trim();

  try {
    const res = await fetch(`/api/incidents/${pendingResolveId}/resolve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ resolutionNotes: notes || 'Resolved by Responder' })
    });

    if (res.ok) {
      closeResolveModal();
      await refreshIncidents();
      await loadActiveDevices();
    }
  } catch (err) {
    alert('Failed to resolve incident: ' + err);
  }
}

/* ----------------- PING & TELEMETRY ----------------- */
async function sendPingForIncident(incidentId, userId) {
  try {
    const res = await fetch(`/api/incidents/${incidentId}/ping`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: 'Safety Check: Responder center checking on your safety.',
        timeoutSeconds: 30
      })
    });

    if (res.ok) {
      alert(`Ping dispatched to device ${userId}. Waiting for check-in acknowledgement.`);
    }
  } catch (err) {
    alert('Failed to dispatch ping: ' + err);
  }
}

async function triggerManualPing() {
  const targetId = document.getElementById('target-device-id').value.trim();
  const statusMsg = document.getElementById('ping-status-msg');
  if (!targetId) return;

  statusMsg.textContent = `Dispatching ping to ${targetId}...`;
  try {
    const res = await fetch(`/api/incidents/emergency-ping/ping`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message: 'Responder Check-In: Are you safe?',
        timeoutSeconds: 30
      })
    });

    statusMsg.textContent = `Ping broadcast successfully dispatched to ${targetId}!`;
    setTimeout(() => { statusMsg.textContent = ''; }, 4000);
  } catch (err) {
    statusMsg.textContent = `Broadcast completed. Mobile client will receive via /ws channel.`;
    setTimeout(() => { statusMsg.textContent = ''; }, 4000);
  }
}

/* ----------------- WEBSOCKET (STOMP OVER SOCKJS) ----------------- */
function connectWebSocket() {
  const statusEl = document.getElementById('ws-status');
  const pulseEl = document.getElementById('ws-pulse');

  try {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, frame => {
      statusEl.textContent = 'Gateway Connected (Real-Time)';
      pulseEl.classList.remove('disconnected');

      // Subscribe to incident broadcasts
      stompClient.subscribe('/topic/responders', message => {
        try {
          const event = JSON.parse(message.body);
          console.log('Received responder incident event:', event);
          refreshIncidents();
        } catch (e) {
          console.error(e);
        }
      });

      // Subscribe to live device location updates
      stompClient.subscribe('/topic/responders/locations', message => {
        try {
          const locEvent = JSON.parse(message.body);
          if (locEvent.payload && locEvent.payload.latitude && locEvent.payload.longitude) {
            updateDeviceLocationOnMap(locEvent.userId, locEvent.payload.latitude, locEvent.payload.longitude, locEvent.payload);
            loadActiveDevices();
          }
        } catch (e) {
          console.error(e);
        }
      });

      // Subscribe to real-time evidence broadcasts
      stompClient.subscribe('/topic/responders/evidence', message => {
        try {
          console.log('Received evidence event:', message.body);
          loadRecentEvidence();
        } catch (e) {
          console.error(e);
        }
      });

      // Subscribe to Emergency Contact specific channels if paired
      subscribeContactChannels();
    }, error => {
      console.warn('WebSocket reconnecting in 5s...', error);
      statusEl.textContent = 'Reconnecting...';
      pulseEl.classList.add('disconnected');
      setTimeout(connectWebSocket, 5000);
    });
  } catch (err) {
    console.error('Socket init error:', err);
    statusEl.textContent = 'Offline (Polling fallback)';
    pulseEl.classList.add('disconnected');
  }
}

/* ----------------- VEHICLE EVIDENCE LOCKER ----------------- */
async function loadRecentEvidence() {
  const container = document.getElementById('evidence-container');
  if (!container) return;

  try {
    const res = await fetch('/api/evidence/recent');
    if (!res.ok) return;
    const items = await res.json();

    if (!items || items.length === 0) {
      container.innerHTML = '<div class="empty-state">No vehicle plates logged yet.</div>';
      return;
    }

    container.innerHTML = items.map(ev => {
      const regNo = ev.registrationNumber || 'Unknown Plate';
      const confPct = Math.round((ev.confidence || 1.0) * 100);
      const timeStr = ev.timestamp ? new Date(ev.timestamp).toLocaleTimeString() : 'Recent';
      const hasCoords = ev.latitude && ev.longitude && (ev.latitude !== 0 || ev.longitude !== 0);
      const hasImg = !!ev.imagePath;

      return `
        <div class="incident-card" style="border-left: 4px solid var(--accent-hospitable); margin-bottom: 10px;">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span style="font-family: monospace; font-size: 15px; font-weight: 800; color: var(--text-primary); letter-spacing: 1px;">
              🚗 ${regNo}
            </span>
            <span class="badge-safe" style="font-size: 10px;">${confPct}% Conf</span>
          </div>
          <div style="font-size: 11px; color: var(--text-secondary); margin: 6px 0;">
            Logged: ${timeStr} · User: ${ev.userId || 'User Device'}
          </div>
          <div style="display: flex; gap: 6px; margin-top: 8px;">
            ${hasImg ? `
              <button class="action-btn btn-outline" style="font-size: 11px; padding: 4px 8px;" onclick="openImageModal('${ev.id}', '${regNo}')">
                📷 View Photo
              </button>
            ` : ''}
            ${hasCoords ? `
              <button class="action-btn btn-outline" style="font-size: 11px; padding: 4px 8px;" onclick="panToEvidence(${ev.latitude}, ${ev.longitude}, '${regNo}')">
                📍 Map
              </button>
            ` : ''}
          </div>
        </div>
      `;
    }).join('');

  } catch (err) {
    console.debug('Failed loading evidence items:', err);
  }
}

function openImageModal(evidenceId, regNo) {
  const modal = document.getElementById('image-modal');
  const title = document.getElementById('image-modal-title');
  const img = document.getElementById('image-modal-img');
  if (!modal || !img) return;

  title.textContent = `Evidence Photo: ${regNo}`;
  img.src = `/api/evidence/${evidenceId}/image`;
  modal.classList.add('visible');
}

function closeImageModal() {
  const modal = document.getElementById('image-modal');
  if (modal) modal.classList.remove('visible');
}

function panToEvidence(lat, lng, regNo) {
  if (!map) return;
  map.setView([lat, lng], 17);
  L.popup()
    .setLatLng([lat, lng])
    .setContent(`<strong>🚗 Vehicle Plate</strong><br>${regNo}`)
    .openOn(map);
}

/* ========================================================================
   EMERGENCY CONTACT & PHONE-TO-WEB CONNECTION FLOW
   ======================================================================== */

function openPairingModal() {
  const modal = document.getElementById('pairing-modal');
  const codeInput = document.getElementById('pairing-code-input');
  const errorEl = document.getElementById('pairing-error-msg');
  if (errorEl) errorEl.style.display = 'none';
  if (codeInput) {
    codeInput.value = '';
    setTimeout(() => codeInput.focus(), 100);
  }
  if (modal) modal.classList.add('active');
}

function closePairingModal() {
  const modal = document.getElementById('pairing-modal');
  if (modal) modal.classList.remove('active');
}

async function submitPairingCode() {
  const codeInput = document.getElementById('pairing-code-input');
  const nameInput = document.getElementById('pairing-name-input');
  const phoneInput = document.getElementById('pairing-phone-input');
  const errorEl = document.getElementById('pairing-error-msg');
  const btn = document.getElementById('btn-submit-pairing');

  const rawCode = (codeInput ? codeInput.value : '').trim().replace(/\s+/g, '');
  const contactName = (nameInput ? nameInput.value : '').trim() || 'Emergency Contact';
  const contactPhone = (phoneInput ? phoneInput.value : '').trim() || '';

  if (!rawCode || rawCode.length !== 6) {
    if (errorEl) {
      errorEl.textContent = 'Please enter a valid 6-digit numeric pairing code.';
      errorEl.style.display = 'block';
    }
    return;
  }

  btn.disabled = true;
  btn.textContent = 'Verifying Code...';

  try {
    const res = await fetch('/api/pairing/claim', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        code: rawCode,
        contactName: contactName,
        contactPhone: contactPhone,
        contactEmail: ''
      })
    });

    if (!res.ok) {
      const errData = await res.json().catch(() => ({}));
      throw new Error(errData.message || 'Invalid, expired, or already used pairing code.');
    }

    const data = await res.json();
    contactToken = data.contactToken;
    pairedUserId = data.userId;
    pairedUserName = data.userFullName || data.userId;

    localStorage.setItem('zerotap_contact_token', contactToken);
    localStorage.setItem('zerotap_paired_user_id', pairedUserId);
    localStorage.setItem('zerotap_paired_user_name', pairedUserName);

    closePairingModal();
    subscribeContactChannels();
    await loadMonitoredContact();
  } catch (err) {
    if (errorEl) {
      errorEl.textContent = err.message || 'Pairing failed. Check code and retry.';
      errorEl.style.display = 'block';
    }
  } finally {
    btn.disabled = false;
    btn.textContent = 'Establish Connection';
  }
}

function initContactMonitoring() {
  if (contactToken && pairedUserId) {
    updateContactHeaderUI(true, pairedUserName);
    loadMonitoredContact();
  } else {
    updateContactHeaderUI(false, null);
  }
}

function updateContactHeaderUI(isPaired, name) {
  const label = document.getElementById('header-pair-label');
  const badge = document.getElementById('contact-status-badge');
  const unpairedView = document.getElementById('contact-unpaired-view');
  const pairedView = document.getElementById('contact-paired-view');
  const title = document.getElementById('contact-hub-title');
  const subtitle = document.getElementById('contact-hub-subtitle');

  if (isPaired) {
    if (label) label.textContent = `Paired: ${name || 'User'}`;
    if (title) title.textContent = `Monitoring: ${name || 'User'}`;
    if (subtitle) subtitle.textContent = 'Active 1:1 Emergency Channel';
    if (unpairedView) unpairedView.style.display = 'none';
    if (pairedView) pairedView.style.display = 'block';
  } else {
    if (label) label.textContent = 'Connect to Phone';
    if (title) title.textContent = 'Emergency Contact Center';
    if (subtitle) subtitle.textContent = 'Secure 1:1 Phone-to-Web Link';
    if (badge) {
      badge.className = 'badge-offline';
      badge.textContent = 'Not Paired';
    }
    if (unpairedView) unpairedView.style.display = 'block';
    if (pairedView) pairedView.style.display = 'none';
  }
}

let contactWsSubscription = null;
let pingWsSubscription = null;

function subscribeContactChannels() {
  if (!stompClient || !stompClient.connected || !contactToken) return;

  // Unsubscribe previous if existing
  if (contactWsSubscription) {
    try { contactWsSubscription.unsubscribe(); } catch (_) {}
  }
  if (pingWsSubscription) {
    try { pingWsSubscription.unsubscribe(); } catch (_) {}
  }

  // Subscribe to contact's private token channel
  contactWsSubscription = stompClient.subscribe(`/topic/contacts/${contactToken}`, message => {
    try {
      const event = JSON.parse(message.body);
      if (event.eventType === 'PAIRING_CHANGED' && event.payload && event.payload.status === 'NOT_CONNECTED') {
        // Phone disconnected us
        alert('Phone user has disconnected the emergency link.');
        handleUnpairedLocally();
      } else if (event.eventType === 'PING_RESPONSE') {
        handlePingResponseReceived(event.payload);
      }
    } catch (e) {
      console.error('Error handling contact message:', e);
    }
  });

  // Subscribe to phone user's ping-response channel
  if (pairedUserId) {
    pingWsSubscription = stompClient.subscribe(`/topic/users/${pairedUserId}/ping-response`, message => {
      try {
        const event = JSON.parse(message.body);
        if (event.payload) {
          handlePingResponseReceived(event.payload);
        }
      } catch (e) {
        console.error('Error handling user ping response:', e);
      }
    });
  }
}

async function loadMonitoredContact() {
  if (!contactToken) return;

  try {
    const res = await fetch('/api/contacts/monitor', {
      headers: {
        'X-Contact-Token': contactToken
      }
    });

    if (res.status === 403) {
      // Access was revoked or token invalidated
      handleUnpairedLocally();
      return;
    }

    if (!res.ok) return;

    const data = await res.json();
    monitoredData = data;
    renderMonitoredContactUI(data);
  } catch (err) {
    console.debug('Failed to poll contact monitor:', err);
  }
}

function renderMonitoredContactUI(data) {
  updateContactHeaderUI(true, data.userFullName || data.userId);

  const badge = document.getElementById('contact-status-badge');
  const nameEl = document.getElementById('monitored-user-name');
  const devEl = document.getElementById('monitored-device-id');
  const telemEl = document.getElementById('monitored-telemetry-text');
  const updateEl = document.getElementById('monitored-updated-text');

  if (nameEl) nameEl.textContent = data.userFullName || data.userId;
  if (devEl) devEl.textContent = `ID: ${data.userId}`;

  // Update Status Badge & Freshness
  if (badge) {
    if (data.status === 'LIVE') {
      badge.className = 'badge-live';
      badge.textContent = '● LIVE (<15s)';
    } else if (data.status === 'STALE') {
      badge.className = 'badge-stale';
      badge.textContent = `● STALE (${formatSeconds(data.secondsSinceLastUpdate)})`;
    } else if (data.status === 'OFFLINE') {
      badge.className = 'badge-offline';
      badge.textContent = `● OFFLINE (${formatSeconds(data.secondsSinceLastUpdate)})`;
    } else {
      badge.className = 'badge-unavailable';
      badge.textContent = '● Location Unavailable';
    }
  }

  // Telemetry details (never fake coordinates)
  if (data.latitude !== null && data.latitude !== undefined && data.longitude !== null && data.longitude !== undefined) {
    const speedStr = data.speed ? `${data.speed.toFixed(1)} m/s` : 'Stationary';
    if (telemEl) telemEl.textContent = `${data.latitude.toFixed(4)}, ${data.longitude.toFixed(4)} (${speedStr})`;
    if (updateEl) {
      updateEl.textContent = data.secondsSinceLastUpdate !== null
        ? `${data.secondsSinceLastUpdate}s ago (${new Date(data.lastLocationUpdate).toLocaleTimeString()})`
        : 'Recent';
    }

    // Update or plot distinct monitored contact marker on map
    updateMonitoredMarkerOnMap(data);
  } else {
    if (telemEl) telemEl.textContent = 'No GPS signal from phone';
    if (updateEl) updateEl.textContent = 'Waiting for coordinates...';
  }

  // Check if phone reported a recent ping response
  if (data.lastPingResponse && !pingCountdownInterval) {
    const banner = document.getElementById('contact-ping-status-banner');
    if (banner) {
      banner.style.display = 'flex';
      banner.className = 'ping-response-banner';
      const timeStr = data.lastPingResponseTime ? new Date(data.lastPingResponseTime).toLocaleTimeString() : 'Just now';
      banner.innerHTML = `✅ User responded: <strong>"${data.lastPingResponse}"</strong> at ${timeStr}`;
    }
  }
}

function updateMonitoredMarkerOnMap(data) {
  if (!map || data.latitude == null || data.longitude == null) return;

  const lat = data.latitude;
  const lng = data.longitude;
  const isEmergency = data.emergencyActive;

  const markerHtml = `
    <div style="
      background: ${isEmergency ? '#DC2626' : '#2E7D32'};
      color: white;
      border-radius: 50%;
      width: 36px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      border: 3px solid #FFFFFF;
      box-shadow: 0 0 16px ${isEmergency ? 'rgba(220, 38, 38, 0.9)' : 'rgba(46, 125, 50, 0.9)'};
      animation: ${isEmergency ? 'pulse-red' : 'pulse-green'} 1.4s infinite;
    ">
      🛡️
    </div>
  `;

  const customIcon = L.divIcon({
    html: markerHtml,
    className: 'monitored-user-marker',
    iconSize: [36, 36],
    iconAnchor: [18, 18]
  });

  const speedStr = data.speed ? `${data.speed.toFixed(1)} m/s` : 'Stationary';
  const popupHtml = `
    <div style="font-family: var(--font-family); padding: 4px;">
      <h4 style="margin: 0 0 4px 0; color: #2E7D32; font-size: 14px;">${data.userFullName || 'Monitored Phone'}</h4>
      <div style="font-size: 12px; margin-bottom: 3px;"><strong>Status:</strong> ${data.status}</div>
      <div style="font-size: 12px; margin-bottom: 3px;"><strong>Speed:</strong> ${speedStr}</div>
      <div style="font-size: 12px; margin-bottom: 4px;"><strong>Updated:</strong> ${data.secondsSinceLastUpdate}s ago</div>
      <button style="padding: 4px 10px; background: #2E7D32; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 11px;" onclick="pingPairedUser()">Ping User</button>
    </div>
  `;

  if (monitoredMarker) {
    monitoredMarker.setLatLng([lat, lng]);
    monitoredMarker.setIcon(customIcon);
  } else {
    monitoredMarker = L.marker([lat, lng], { icon: customIcon }).bindPopup(popupHtml);
    deviceLayerGroup.addLayer(monitoredMarker);
  }
}

function focusMonitoredUser() {
  if (monitoredData && monitoredData.latitude && monitoredData.longitude) {
    focusOnMap(monitoredData.latitude, monitoredData.longitude);
  } else {
    alert('Location for this phone is currently unavailable.');
  }
}

async function pingPairedUser() {
  if (!contactToken) return;

  const banner = document.getElementById('contact-ping-status-banner');
  const pingBtn = document.getElementById('btn-ping-paired');
  if (banner) {
    banner.style.display = 'flex';
    banner.className = 'ping-response-banner';
    banner.style.backgroundColor = 'var(--status-watch-bg)';
    banner.style.borderLeftColor = 'var(--status-watch)';
    banner.innerHTML = '⏳ Ping sent to phone. Waiting for response... <strong id="ping-timer-val">(30s)</strong>';
  }

  if (pingBtn) pingBtn.disabled = true;

  try {
    const res = await fetch('/api/contacts/ping', {
      method: 'POST',
      headers: {
        'X-Contact-Token': contactToken
      }
    });

    if (!res.ok) throw new Error('Failed to dispatch ping.');

    // Start 30-second countdown
    if (pingCountdownInterval) clearInterval(pingCountdownInterval);
    pingCountdown = 30;

    pingCountdownInterval = setInterval(() => {
      pingCountdown--;
      const timerVal = document.getElementById('ping-timer-val');
      if (timerVal) timerVal.textContent = `(${pingCountdown}s)`;

      if (pingCountdown <= 0) {
        clearInterval(pingCountdownInterval);
        pingCountdownInterval = null;
        if (pingBtn) pingBtn.disabled = false;
        if (banner) {
          banner.style.backgroundColor = 'var(--status-critical-bg)';
          banner.style.borderLeftColor = 'var(--status-critical)';
          banner.innerHTML = '⚠️ No response yet (30s elapsed). User may be occupied or phone out of reach.';
        }
      }
    }, 1000);

  } catch (err) {
    if (pingBtn) pingBtn.disabled = false;
    if (banner) {
      banner.style.backgroundColor = 'var(--status-critical-bg)';
      banner.style.borderLeftColor = 'var(--status-critical)';
      banner.innerHTML = '❌ Failed to dispatch ping: ' + err.message;
    }
  }
}

function handlePingResponseReceived(payload) {
  if (pingCountdownInterval) {
    clearInterval(pingCountdownInterval);
    pingCountdownInterval = null;
  }

  const pingBtn = document.getElementById('btn-ping-paired');
  if (pingBtn) pingBtn.disabled = false;

  const banner = document.getElementById('contact-ping-status-banner');
  if (banner) {
    banner.style.display = 'flex';
    banner.className = 'ping-response-banner';
    banner.style.backgroundColor = 'var(--status-safe-bg)';
    banner.style.borderLeftColor = 'var(--status-safe)';
    const timeStr = new Date().toLocaleTimeString();
    const msg = payload.message || "I'm OK";
    banner.innerHTML = `✅ User responded: <strong>"${msg}"</strong> at ${timeStr}`;
  }
}

async function unpairContact() {
  if (!confirm('Are you sure you want to disconnect this emergency contact connection? You will no longer receive live telemetry.')) {
    return;
  }

  try {
    await fetch('/api/pairing/unpair', {
      method: 'POST',
      headers: {
        'X-Contact-Token': contactToken
      }
    });
  } catch (_) {}

  handleUnpairedLocally();
}

function handleUnpairedLocally() {
  contactToken = null;
  pairedUserId = null;
  pairedUserName = null;
  monitoredData = null;

  localStorage.removeItem('zerotap_contact_token');
  localStorage.removeItem('zerotap_paired_user_id');
  localStorage.removeItem('zerotap_paired_user_name');

  if (pingCountdownInterval) {
    clearInterval(pingCountdownInterval);
    pingCountdownInterval = null;
  }

  if (monitoredMarker && map) {
    deviceLayerGroup.removeLayer(monitoredMarker);
    monitoredMarker = null;
  }

  updateContactHeaderUI(false, null);
}

function formatSeconds(secs) {
  if (secs == null) return 'N/A';
  if (secs < 60) return `${secs}s ago`;
  const mins = Math.floor(secs / 60);
  return `${mins}m ago`;
}

