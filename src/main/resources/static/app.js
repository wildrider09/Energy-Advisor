const API = '';
const chatMessages = document.getElementById('chat-messages');

async function sendMessage() {
    const input = document.getElementById('user-input');
    const msg = input.value.trim();
    if (!msg) return;
    const sendBtn = document.querySelector('.chat-input button');
    input.value = '';
    input.disabled = true;
    sendBtn.disabled = true;
    sendBtn.style.opacity = '0.5';
    input.placeholder = '⏳ Waiting for response...';
    document.querySelectorAll('.suggestions button').forEach(b => b.style.opacity = '0.5');
    appendMsg('user', msg);
    appendTyping();
    try {
        const res = await fetch(API + '/api/chat', {
            method: 'POST', headers: {'Content-Type':'application/json'},
            body: JSON.stringify({message: msg, deviceId: 'snyder-demo-001'})
        });
        const data = await res.json();
        removeTyping();
        appendMsg('advisor', data.response);
    } catch(e) {
        removeTyping();
        appendMsg('advisor', 'Sorry, couldn\'t process that. Check the server.');
    }
    input.disabled = false;
    sendBtn.disabled = false;
    sendBtn.style.opacity = '1';
    input.placeholder = 'Ask about energy, HVAC health, or demand response...';
    document.querySelectorAll('.suggestions button').forEach(b => b.style.opacity = '1');
    input.focus();
}

function askQuestion(q) {
    if (document.getElementById('user-input').disabled) return;
    document.getElementById('user-input').value = q;
    sendMessage();
}

function appendMsg(type, text) {
    const div = document.createElement('div');
    div.className = 'msg ' + type;

    const avatar = document.createElement('div');
    avatar.className = 'avatar';
    avatar.textContent = type === 'user' ? '👤' : type === 'alert' ? '⚠️' : '🤖';

    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';
    const content = document.createElement('div');
    content.className = 'msg-content';
    content.textContent = text;
    bubble.appendChild(content);

    div.appendChild(avatar);
    div.appendChild(bubble);
    chatMessages.appendChild(div);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function appendTyping() {
    const div = document.createElement('div');
    div.className = 'typing';
    div.id = 'typing-indicator';
    div.innerHTML = '<div class="typing-dots"><span></span><span></span><span></span></div> Thinking...';
    chatMessages.appendChild(div);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function removeTyping() {
    const el = document.getElementById('typing-indicator');
    if (el) el.remove();
}

async function startSim() { await fetch(API + '/api/simulation/start', {method:'POST'}); }
async function stopSim() { await fetch(API + '/api/simulation/stop', {method:'POST'}); }
async function resetSim() {
    await fetch(API + '/api/simulation/reset', {method:'POST'});
    chatMessages.innerHTML = '';
    appendMsg('advisor', 'Simulation reset. Click Start to begin a new scenario.');
}

async function pollState() {
    try {
        const res = await fetch(API + '/api/state');
        const s = await res.json();

        // Temperature
        document.getElementById('indoor-temp').textContent = s.indoorTemp.toFixed(1);
        document.getElementById('target-setpoint').textContent = s.targetSetpoint.toFixed(1);
        document.getElementById('outdoor-temp').textContent = s.outdoorTemp.toFixed(0) + '°F';

        // Mode badge with color
        const badge = document.getElementById('mode-badge');
        badge.textContent = s.thermostatMode;
        badge.className = 'mode-badge ' + s.thermostatMode.toLowerCase();

        // DR status with card color
        const drEl = document.getElementById('dr-status');
        drEl.textContent = s.drStatus;
        const drCard = drEl.closest('.stat-card');
        drCard.className = 'stat-card dr' + (s.drStatus === 'ACTIVE' ? ' active' : s.drStatus === 'SCHEDULED' ? ' scheduled' : s.drStatus === 'PRE_COOLING' ? ' pre_cooling' : s.drStatus === 'RECOVERY' ? ' recovery' : '');

        // Next DR event
        const nextDr = document.getElementById('next-dr');
        if (nextDr && s.nextDrEvent) {
            nextDr.textContent = s.drStatus === 'NONE' ? '📅 Next: ' + s.nextDrEvent : s.nextDrEvent;
        }

        // Energy Star score (removed)

        // Filter hours
        const filt = document.getElementById('filter-hours');
        if (filt) {
            filt.textContent = s.totalHvacRuntimeHours + 'h';
            const filtCard = filt.closest('.stat-card');
            if (s.totalHvacRuntimeHours >= 200) {
                filtCard.style.background = 'linear-gradient(135deg,#ffe0e0,#ffc8c8)';
                filt.style.color = '#e07070';
            } else if (s.totalHvacRuntimeHours >= 100) {
                filtCard.style.background = 'linear-gradient(135deg,#fff8e0,#ffeea0)';
                filt.style.color = '#e6a84d';
            } else {
                filtCard.style.background = 'linear-gradient(135deg,#e0ffe8,#c8f0d0)';
                filt.style.color = '#4db89c';
            }
        }

        // Dynamic outdoor temp color
        const outEl = document.getElementById('outdoor-temp');
        const outCard = outEl.closest('.stat-card');
        const outTemp = s.outdoorTemp;
        if (outTemp >= 95) {
            outCard.style.background = 'linear-gradient(135deg,#ffe0e0,#ffc8c8)';
            outEl.style.color = '#e07070';
        } else if (outTemp >= 80) {
            outCard.style.background = 'linear-gradient(135deg,#fff8e0,#ffeea0)';
            outEl.style.color = '#e6a84d';
        } else if (outTemp >= 65) {
            outCard.style.background = 'linear-gradient(135deg,#e0ffe8,#c8f0d0)';
            outEl.style.color = '#4db89c';
        } else {
            outCard.style.background = 'linear-gradient(135deg,#e0f0ff,#c8e0ff)';
            outEl.style.color = '#3b8fd4';
        }

        // Dynamic DR status color
        const drColors = {
            'NONE': {bg:'linear-gradient(135deg,#e8f0ff,#d4e4ff)', color:'#3b8fd4'},
            'SCHEDULED': {bg:'linear-gradient(135deg,#fff8e0,#ffeea0)', color:'#e6a84d'},
            'PRE_COOLING': {bg:'linear-gradient(135deg,#e0f0ff,#b8d8ff)', color:'#3b8fd4'},
            'ACTIVE': {bg:'linear-gradient(135deg,#e0ffe8,#c8f0d0)', color:'#4db89c'},
            'RECOVERY': {bg:'linear-gradient(135deg,#fff0e0,#ffe0c0)', color:'#e6a84d'},
        };
        const drStyle = drColors[s.drStatus] || drColors['NONE'];
        drCard.style.background = drStyle.bg;
        drEl.style.color = drStyle.color;

        // Connection
        const conn = document.getElementById('connectivity');
        conn.className = 'conn-dot ' + (s.online ? 'online' : 'offline');
        conn.title = s.online ? 'Online' : 'Offline';

        // Runtime values and bars
        const maxH = 8;
        const heaterH = s.primaryHeaterRuntimeToday / 3600;
        const auxH = s.auxHeaterRuntimeToday / 3600;
        const coolerH = s.coolerRuntimeToday / 3600;
        document.getElementById('rt-heater').textContent = heaterH.toFixed(1) + 'h';
        document.getElementById('rt-aux').textContent = auxH.toFixed(1) + 'h';
        document.getElementById('rt-cooler').textContent = coolerH.toFixed(1) + 'h';
        document.getElementById('aux-cycles').textContent = s.auxHeaterCyclesToday;
        document.getElementById('bar-heater').style.width = Math.min(heaterH / maxH * 100, 100) + '%';
        document.getElementById('bar-aux').style.width = Math.min(auxH / maxH * 100, 100) + '%';
        document.getElementById('bar-cooler').style.width = Math.min(coolerH / maxH * 100, 100) + '%';
        document.getElementById('bar-cycles').style.width = Math.min(s.auxHeaterCyclesToday / 10 * 100, 100) + '%';

        // Components
        setComponent('heater', s.primaryHeaterRunning);
        setComponent('aux', s.auxHeaterRunning);
        setComponent('cooler', s.coolerRunning);
        setComponent('fan', s.fanRunning);

        // Temperature ring
        const pct = Math.max(0, Math.min(1, (s.indoorTemp - 60) / 40));
        const ring = document.getElementById('temp-ring');
        if (ring) ring.style.strokeDashoffset = 327 - (327 * pct);

    } catch(e) {}
}

function setComponent(name, running) {
    const dot = document.getElementById('dot-' + name);
    const status = document.getElementById('status-' + name);
    const comp = document.getElementById('comp-' + name);
    dot.className = 'dot' + (running ? ' running' : '');
    status.textContent = running ? 'RUNNING' : 'IDLE';
    status.className = 'status' + (running ? ' running' : '');
    if (comp) comp.className = 'component' + (running ? ' active' : '');
}

async function pollAlerts() {
    try {
        const res = await fetch(API + '/api/alerts');
        const alerts = await res.json();
        for (const a of alerts) appendMsg('alert', a.response);
    } catch(e) {}
}

async function runOptimize() {
    appendMsg('user', '🤖 Run AI Optimization');
    appendTyping();
    try {
        const res = await fetch(API + '/api/optimize', {method:'POST'});
        const data = await res.json();
        removeTyping();
        let msg;
        if (data.applied) {
            msg = `✅ Applied: ${data.reason}\n(Mode → ${data.mode}, Setpoint → ${data.setpoint}°F)`;
        } else if (data.reason) {
            msg = `ℹ️ ${data.reason}`;
        } else {
            msg = '⚠️ Could not optimize — check if Bedrock is connected.';
        }
        appendMsg('advisor', msg);
    } catch(e) {
        removeTyping();
        appendMsg('advisor', '⚠️ Optimization failed — credentials may have expired.');
    }
}

let overrideTemp = 74;
let overrideMode = 'COOL';

function updateOverrideDisplay() {
    const icons = {COOL:'❄️', HEAT:'🔥', OFF:'⏸'};
    document.getElementById('override-display').textContent = overrideTemp + '°F';
    document.getElementById('override-status').textContent = 
        `${icons[overrideMode]} Ready: ${overrideMode} at ${overrideTemp}°F — tap Apply to set`;
    document.getElementById('override-status').style.color = '#6366f1';
    document.getElementById('override-status').style.background = 'rgba(99,102,241,.08)';
}

function adjustTemp(delta) {
    overrideTemp = Math.max(60, Math.min(90, overrideTemp + delta));
    updateOverrideDisplay();
}

function setMode(m) {
    overrideMode = m;
    document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
    document.getElementById('mode-' + m.toLowerCase()).classList.add('active');
    updateOverrideDisplay();
}

async function manualOverride() {
    const icons = {COOL:'❄️', HEAT:'🔥', OFF:'⏸'};
    try {
        const res = await fetch(API + '/api/override', {
            method:'POST', headers:{'Content-Type':'application/json'},
            body: JSON.stringify({mode: overrideMode, setpoint: overrideTemp})
        });
        const data = await res.json();
        const st = document.getElementById('override-status');
        st.textContent = `🔒 OVERRIDE ON: ${icons[overrideMode]} ${overrideMode} at ${overrideTemp}°F — AI paused`;
        st.style.color = '#ef4444';
        st.style.background = 'rgba(239,68,68,.1)';
        appendMsg('advisor', `🔒 Got it! I've switched your thermostat to ${icons[overrideMode]} ${overrideMode} mode at ${overrideTemp}°F. AI optimization is paused — I won't change anything until you tap Resume AI. I'll remember you prefer ${overrideTemp}°F when it's ${Math.round(parseFloat(document.getElementById('outdoor-temp').textContent))}°F outside.`);
    } catch(e) { appendMsg('advisor', '❌ Override failed — is the server running?'); }
}

async function resumeAI() {
    await fetch(API + '/api/override/resume', {method:'POST'});
    const st = document.getElementById('override-status');
    st.textContent = '🤖 AI is controlling your thermostat';
    st.style.color = '#10b981';
    st.style.background = 'rgba(16,185,129,.08)';
    appendMsg('advisor', '🤖 AI is back in control! I\'ll optimize your thermostat every 60 seconds based on weather, your preferences, and energy prices. Your past overrides help me make better decisions.');
}

setInterval(pollState, 1500);
setInterval(pollAlerts, 3000);
setInterval(pollWeather, 30000);
pollState();
pollWeather();

function toggle(id) {
    document.getElementById('body-' + id).classList.toggle('open');
    document.getElementById('arrow-' + id).classList.toggle('open');
}

async function pollCost() {
    try {
        const res = await fetch(API + '/api/cost');
        const c = await res.json();
        document.getElementById('cost-total').textContent = c.todayTotal;
        document.getElementById('cost-cool').textContent = c.coolerCost;
        document.getElementById('cost-heat').textContent = c.heaterCost;
        document.getElementById('cost-aux').textContent = c.auxCost;
        document.getElementById('cost-month').textContent = c.projectedMonthly;
    } catch(e) {}
}

async function showSavings() {
    appendMsg('user', '💵 Show monthly savings');
    appendTyping();
    try {
        const res = await fetch(API + '/api/savings');
        const s = await res.json();
        removeTyping();
        appendMsg('advisor',
            `💵 Monthly Savings Report\n\n` +
            `Running cost without AI: ${s.unoptimizedMonthly}/month\n` +
            `Running cost with AI: ${s.optimizedMonthly}/month\n\n` +
            `Savings breakdown:\n` +
            `  🤖 AI optimization: ${s.aiSavings}/month\n` +
            `  🔧 Filter maintenance: ${s.filterSavings}/month\n` +
            `  ⚡ DR participation: ${s.drSavings}/month\n\n` +
            `✅ You save ${s.totalMonthlySavings}/month (${s.savingsPercent}) with AI`
        );
    } catch(e) {
        removeTyping();
        appendMsg('advisor', '⚠️ Could not calculate savings.');
    }
}
setInterval(pollCost, 5000);
pollCost();

async function pollWeather() {
    try {
        const res = await fetch(API + '/api/weather');
        const w = await res.json();
        const el = document.getElementById('weather-trend');
        if (el && w.trend) {
            const icons = {RISING:'↑ Rising',FALLING:'↓ Falling',STABLE:'→ Stable'};
            el.textContent = icons[w.trend] || w.trend;
            el.className = 'stat-trend ' + w.trend.toLowerCase();
        }
    } catch(e) {}
}
