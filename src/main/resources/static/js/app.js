// AgentRunr Web UI

const API_BASE = '/api';
let settings = {
    agentName: 'Assistant',
    model: 'gpt-4.1',
    fallbackModel: '',
    instructions: 'You are a helpful AI assistant powered by AgentRunr.',
    maxTurns: 10
};
let sessionId = localStorage.getItem('sessionId') || crypto.randomUUID();
let chatHistory = JSON.parse(localStorage.getItem('chatHistory') || '[]');
let streamingEnabled = true;

localStorage.setItem('sessionId', sessionId);

function persistChat() {
    localStorage.setItem('chatHistory', JSON.stringify(chatHistory));
    localStorage.setItem('sessionId', sessionId);
}

// DOM Elements
const chatMessages = document.getElementById('chatMessages');
const chatInput = document.getElementById('chatInput');
const sendBtn = document.getElementById('sendBtn');
const modelSelect = document.getElementById('modelSelect');
const agentNameEl = document.getElementById('agentName');
const modelBadge = document.getElementById('modelBadge');
const statusDot = document.getElementById('statusDot');
const statusText = document.getElementById('statusText');

// Navigation
document.querySelectorAll('.nav-link[data-view]').forEach(link => {
    link.addEventListener('click', (e) => {
        e.preventDefault();
        const view = link.dataset.view;

        // Update nav
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        link.classList.add('active');

        // Update views
        document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
        document.getElementById(view + 'View').classList.add('active');

        if (view === 'settings') {
            loadSettings();
            loadProviders();
            loadToolSettings();
            loadTelegramSettings();
            loadMcpServers();
            loadSessions();
        } else if (view === 'jobs') {
            loadJobs();
        }
    });
});

// Chat
sendBtn.addEventListener('click', sendMessage);
chatInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
    }
});

// Auto-resize textarea
chatInput.addEventListener('input', () => {
    chatInput.style.height = 'auto';
    chatInput.style.height = Math.min(chatInput.scrollHeight, 120) + 'px';
});

// Model select updates badge
modelSelect.addEventListener('change', () => {
    modelBadge.textContent = modelSelect.options[modelSelect.selectedIndex].text;
});

// Streaming toggle
document.getElementById('streamToggle').addEventListener('change', (e) => {
    streamingEnabled = e.target.checked;
});

async function sendMessage() {
    const text = chatInput.value.trim();
    if (!text) return;

    // Add user message to UI
    addMessage('user', text);
    chatInput.value = '';
    chatInput.style.height = 'auto';

    // Add to history and persist
    chatHistory.push({ role: 'user', content: text });
    persistChat();

    // Disable input
    sendBtn.disabled = true;
    chatInput.disabled = true;

    if (streamingEnabled) {
        await sendMessageStreaming();
    } else {
        await sendMessageNonStreaming();
    }

    sendBtn.disabled = false;
    chatInput.disabled = false;
    chatInput.focus();
}

async function sendMessageStreaming() {
    // Create assistant message element for streaming
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = '';
    msgDiv.appendChild(contentDiv);
    chatMessages.appendChild(msgDiv);

    let fullContent = '';

    try {
        const response = await fetch(`${API_BASE}/chat/stream`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: chatHistory,
                model: modelSelect.value,
                maxTurns: settings.maxTurns,
                contextVariables: {},
                sessionId: sessionId
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });

            // SSE events are separated by double newlines
            const parts = buffer.split('\n\n');
            // Keep the last (possibly incomplete) part in the buffer
            buffer = parts.pop() || '';

            for (const part of parts) {
                if (!part.trim()) continue;

                let eventType = null;
                const dataLines = [];

                for (const line of part.split('\n')) {
                    if (line.startsWith('event:')) {
                        eventType = line.substring(6).trim();
                    } else if (line.startsWith('data:')) {
                        dataLines.push(line.substring(5));
                    }
                }

                const data = dataLines.join('\n');
                if (!data) continue;

                if (eventType === 'session') {
                    sessionId = data;
                    persistChat();
                    continue;
                }

                fullContent += data;
                contentDiv.textContent = fullContent;
                chatMessages.scrollTop = chatMessages.scrollHeight;
            }
        }

        chatHistory.push({ role: 'assistant', content: fullContent });
        persistChat();

    } catch (error) {
        if (!fullContent) {
            msgDiv.remove();
        }
        addMessage('system', 'Error: ' + error.message);
    }
}

async function sendMessageNonStreaming() {
    const typingEl = showTyping();

    try {
        const response = await fetch(`${API_BASE}/chat`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                messages: chatHistory,
                model: modelSelect.value,
                maxTurns: settings.maxTurns,
                contextVariables: {},
                sessionId: sessionId
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const data = await response.json();
        typingEl.remove();

        if (data.sessionId) {
            sessionId = data.sessionId;
        }

        addMessage('assistant', data.response);
        chatHistory.push({ role: 'assistant', content: data.response });
        persistChat();

        if (data.agent) {
            agentNameEl.textContent = data.agent;
        }

    } catch (error) {
        typingEl.remove();
        addMessage('system', 'Error: ' + error.message);
    }
}

function addMessage(role, content) {
    const msgDiv = document.createElement('div');
    msgDiv.className = `message ${role}`;

    const contentDiv = document.createElement('div');
    contentDiv.className = 'message-content';
    contentDiv.textContent = content;

    msgDiv.appendChild(contentDiv);
    chatMessages.appendChild(msgDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

function showTyping() {
    const msgDiv = document.createElement('div');
    msgDiv.className = 'message assistant';
    msgDiv.innerHTML = `
        <div class="typing-indicator">
            <span></span><span></span><span></span>
        </div>
    `;
    chatMessages.appendChild(msgDiv);
    chatMessages.scrollTop = chatMessages.scrollHeight;
    return msgDiv;
}

// Settings
function loadSettings() {
    document.getElementById('settingAgentName').value = settings.agentName;
    document.getElementById('settingModel').value = settings.model;
    document.getElementById('settingFallbackModel').value = settings.fallbackModel || '';
    document.getElementById('settingInstructions').value = settings.instructions;
    document.getElementById('settingMaxTurns').value = settings.maxTurns;
}

document.getElementById('saveSettingsBtn').addEventListener('click', async () => {
    settings.agentName = document.getElementById('settingAgentName').value;
    settings.model = document.getElementById('settingModel').value;
    settings.fallbackModel = document.getElementById('settingFallbackModel').value;
    settings.instructions = document.getElementById('settingInstructions').value;
    settings.maxTurns = parseInt(document.getElementById('settingMaxTurns').value);

    // Update agent config on server
    try {
        const response = await fetch(`${API_BASE}/settings`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(settings)
        });

        if (response.ok) {
            agentNameEl.textContent = settings.agentName;
            modelBadge.textContent = settings.model;
            // Auto-select the default model in chat
            applyDefaultModel();
            showToast('Settings saved', 'success');
        } else {
            showToast('Failed to save settings', 'error');
        }
    } catch (error) {
        // Settings saved locally even if server is down
        agentNameEl.textContent = settings.agentName;
        showToast('Settings saved locally', 'success');
    }
});

document.getElementById('clearChatBtn').addEventListener('click', () => {
    chatHistory = [];
    sessionId = crypto.randomUUID();
    persistChat();
    chatMessages.innerHTML = `
        <div class="message system">
            <div class="message-content">Chat history cleared. Start a new conversation.</div>
        </div>
    `;
    showToast('Chat cleared', 'success');
});

// API Providers
async function loadProviders() {
    try {
        const response = await fetch(`${API_BASE}/settings/providers`);
        if (!response.ok) return;
        const providers = await response.json();

        for (const [name, info] of Object.entries(providers)) {
            const capName = name.charAt(0).toUpperCase() + name.slice(1);
            const dot = document.getElementById('dot' + capName);
            const masked = document.getElementById('masked' + capName);

            if (dot) {
                dot.classList.toggle('active', !!info.available);
            }
            if (masked) {
                masked.textContent = info.maskedKey || (info.available ? 'Connected' : 'Not configured');
            }
        }
    } catch (error) {
        // Silently fail
    }
}

// Save provider key buttons
document.querySelectorAll('[data-save-provider]').forEach(btn => {
    btn.addEventListener('click', async () => {
        const provider = btn.getAttribute('data-save-provider');
        const capName = provider.charAt(0).toUpperCase() + provider.slice(1);
        const input = document.getElementById('key' + capName);
        const key = input.value.trim();

        if (!key) {
            showToast('Enter an API key to save', 'error');
            return;
        }

        btn.disabled = true;
        btn.textContent = 'Saving...';

        try {
            const response = await fetch(`${API_BASE}/settings/providers`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ [provider]: key })
            });

            if (response.ok) {
                showToast(capName + ' API key saved. Restart to apply.', 'success');
                input.value = '';
                loadProviders();
            } else {
                showToast('Failed to save ' + capName + ' API key', 'error');
            }
        } catch (error) {
            showToast('Failed to save ' + capName + ' API key', 'error');
        }

        btn.disabled = false;
        btn.textContent = 'Save';
    });
});

// Tool Settings
async function loadToolSettings() {
    try {
        const response = await fetch(`${API_BASE}/settings/tools`);
        if (response.ok) {
            const data = await response.json();

            // Web Search status
            const braveStatus = document.getElementById('braveToolStatus');
            if (data.webSearch && data.webSearch.configured) {
                braveStatus.textContent = 'Configured';
                braveStatus.className = 'tool-status configured';
            } else {
                braveStatus.textContent = 'Not configured';
                braveStatus.className = 'tool-status';
            }

            // Playwright status
            const pwToggle = document.getElementById('playwrightToggle');
            const pwStatus = document.getElementById('playwrightStatus');
            if (data.browser) {
                pwToggle.checked = data.browser.enabled;
                if (data.browser.enabled) {
                    pwStatus.textContent = 'Connected';
                    pwStatus.className = 'tool-status configured';
                } else if (data.browser.configured) {
                    pwStatus.textContent = 'Disabled';
                    pwStatus.className = 'tool-status';
                } else {
                    pwStatus.textContent = 'Not available';
                    pwStatus.className = 'tool-status';
                }
            }
        }
    } catch (error) {
        // Silently fail
    }
}

document.getElementById('saveBraveKeyBtn').addEventListener('click', async () => {
    const key = document.getElementById('braveApiKeyInput').value.trim();
    if (!key) {
        showToast('Enter a Brave API key to save', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/settings/tools`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ braveApiKey: key })
        });

        if (response.ok) {
            showToast('Brave API key saved', 'success');
            document.getElementById('braveApiKeyInput').value = '';
            loadToolSettings();
        } else {
            showToast('Failed to save Brave API key', 'error');
        }
    } catch (error) {
        showToast('Failed to save Brave API key', 'error');
    }
});

document.getElementById('playwrightToggle').addEventListener('change', async (e) => {
    const enable = e.target.checked;
    const pwStatus = document.getElementById('playwrightStatus');
    pwStatus.textContent = enable ? 'Connecting...' : 'Disconnecting...';
    pwStatus.className = 'tool-status';

    try {
        const response = await fetch(`${API_BASE}/settings/tools`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ browser: enable })
        });

        if (response.ok) {
            showToast(enable ? 'Playwright enabled' : 'Playwright disabled', 'success');
            loadToolSettings();
            loadMcpServers();
        } else {
            showToast('Failed to toggle Playwright', 'error');
            e.target.checked = !enable;
            loadToolSettings();
        }
    } catch (error) {
        showToast('Failed to toggle Playwright', 'error');
        e.target.checked = !enable;
        loadToolSettings();
    }
});

// Telegram Settings
async function loadTelegramSettings() {
    try {
        const response = await fetch(`${API_BASE}/telegram/settings`);
        if (response.ok) {
            const data = await response.json();
            const statusEl = document.getElementById('telegramStatus');
            document.getElementById('telegramToken').value = '';
            document.getElementById('telegramToken').placeholder = data.tokenMasked || '123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11';
            document.getElementById('telegramAllowedUsers').value = data.allowedUsers || '';
            if (data.configured) {
                statusEl.textContent = 'Configured';
                statusEl.className = 'telegram-status configured';
            } else {
                statusEl.textContent = 'Not configured';
                statusEl.className = 'telegram-status';
            }
        }
    } catch (error) {
        // Silently fail
    }
}

document.getElementById('saveTelegramBtn').addEventListener('click', async () => {
    const token = document.getElementById('telegramToken').value.trim();
    const allowedUsers = document.getElementById('telegramAllowedUsers').value.trim();

    if (!token && !allowedUsers) {
        showToast('Enter a bot token to save', 'error');
        return;
    }

    const body = {};
    if (token) body.token = token;
    body.allowedUsers = allowedUsers;

    try {
        const response = await fetch(`${API_BASE}/telegram/settings`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        if (response.ok) {
            showToast('Telegram settings saved', 'success');
            loadTelegramSettings();
        } else {
            showToast('Failed to save Telegram settings', 'error');
        }
    } catch (error) {
        showToast('Failed to save Telegram settings', 'error');
    }
});

// MCP Servers
async function loadMcpServers() {
    const list = document.getElementById('mcpServerList');
    try {
        const response = await fetch(`${API_BASE}/mcp/servers`);
        if (response.ok) {
            const servers = await response.json();
            if (servers.length === 0) {
                list.innerHTML = '<p class="muted">No MCP servers configured</p>';
                return;
            }
            list.innerHTML = servers.map(s => `
                <div class="mcp-server-card">
                    <div class="mcp-server-header">
                        <span class="mcp-server-icon">${s.connected ? '🟢' : '🔴'}</span>
                        <span class="mcp-server-name">${escapeHtml(s.name)}</span>
                        ${s.dynamic ? `<button class="mcp-remove-btn" data-name="${escapeHtml(s.name)}" title="Remove">✕</button>` : ''}
                    </div>
                    <div class="mcp-server-details">
                        ${s.connected ? s.toolCount + ' tool' + (s.toolCount !== 1 ? 's' : '') + ' available' : 'Disconnected'}
                    </div>
                    ${s.url ? `<div class="mcp-server-url">${escapeHtml(s.url)}</div>` : ''}
                </div>
            `).join('');

            // Attach remove handlers
            list.querySelectorAll('.mcp-remove-btn').forEach(btn => {
                btn.addEventListener('click', async () => {
                    const name = btn.dataset.name;
                    try {
                        const resp = await fetch(`${API_BASE}/mcp/servers/${encodeURIComponent(name)}`, { method: 'DELETE' });
                        if (resp.ok) {
                            showToast('MCP server removed', 'success');
                            loadMcpServers();
                        } else {
                            showToast('Failed to remove MCP server', 'error');
                        }
                    } catch (error) {
                        showToast('Failed to remove MCP server', 'error');
                    }
                });
            });
        }
    } catch (error) {
        list.innerHTML = '<p class="muted">Failed to load MCP servers</p>';
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

document.getElementById('addMcpServerBtn').addEventListener('click', async () => {
    const name = document.getElementById('mcpName').value.trim();
    const url = document.getElementById('mcpUrl').value.trim();
    const authHeader = document.getElementById('mcpAuthHeader').value.trim();
    const authValue = document.getElementById('mcpAuthValue').value.trim();

    if (!name || !url) {
        showToast('Name and URL are required', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/mcp/servers`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, url, authHeader: authHeader || null, authValue: authValue || null })
        });

        if (response.ok) {
            showToast('MCP server saved', 'success');
            document.getElementById('mcpName').value = '';
            document.getElementById('mcpUrl').value = '';
            document.getElementById('mcpAuthHeader').value = '';
            document.getElementById('mcpAuthValue').value = '';
            loadMcpServers();
        } else {
            const data = await response.json();
            showToast(data.error || 'Failed to save MCP server', 'error');
        }
    } catch (error) {
        showToast('Failed to save MCP server', 'error');
    }
});

// Sessions
async function loadSessions() {
    try {
        const response = await fetch(`${API_BASE}/sessions`);
        if (response.ok) {
            const sessions = await response.json();
            const list = document.getElementById('sessionList');
            if (sessions.length === 0) {
                list.innerHTML = '<p class="muted">No sessions yet</p>';
                return;
            }
            list.innerHTML = sessions.map(s => `
                <div class="session-item">
                    <span>${s}</span>
                </div>
            `).join('');
        }
    } catch (error) {
        // Silently fail
    }
}

// Health check
async function checkHealth() {
    try {
        const response = await fetch(`${API_BASE}/health`);
        if (response.ok) {
            statusDot.className = 'status-dot connected';
            statusText.textContent = 'Connected';
        } else {
            statusDot.className = 'status-dot error';
            statusText.textContent = 'Error';
        }
    } catch (error) {
        statusDot.className = 'status-dot error';
        statusText.textContent = 'Disconnected';
    }
}

// Toast
function showToast(message, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}

// Apply default model to the chat model selector
function applyDefaultModel() {
    if (settings.model) {
        modelSelect.value = settings.model;
        modelBadge.textContent = modelSelect.options[modelSelect.selectedIndex]?.text || settings.model;
    }
}

// Fetch settings from server on startup
async function fetchSettings() {
    try {
        const response = await fetch(`${API_BASE}/settings`);
        if (response.ok) {
            const data = await response.json();
            settings.agentName = data.agentName || settings.agentName;
            settings.model = data.model || settings.model;
            settings.fallbackModel = data.fallbackModel || '';
            settings.instructions = data.instructions || settings.instructions;
            settings.maxTurns = data.maxTurns || settings.maxTurns;
            agentNameEl.textContent = settings.agentName;
            applyDefaultModel();
        }
    } catch (error) {
        // Use local defaults
    }
}

// Restore chat history from localStorage on page load
if (chatHistory.length > 0) {
    for (const msg of chatHistory) {
        addMessage(msg.role, msg.content);
    }
}

// Restart
document.getElementById('restartBtn').addEventListener('click', async () => {
    if (!confirm('Are you sure you want to restart AgentRunr?')) return;

    const btn = document.getElementById('restartBtn');
    btn.disabled = true;
    btn.textContent = 'Restarting...';

    try {
        await fetch(`${API_BASE}/restart`, { method: 'POST' });
        showToast('Restarting AgentRunr...', 'success');

        // Poll health endpoint until the app comes back
        let attempts = 0;
        const maxAttempts = 20;
        const poll = setInterval(async () => {
            attempts++;
            try {
                const resp = await fetch(`${API_BASE}/health`);
                if (resp.ok) {
                    clearInterval(poll);
                    showToast('AgentRunr is back online', 'success');
                    location.reload();
                }
            } catch (e) {
                // Still restarting
            }
            if (attempts >= maxAttempts) {
                clearInterval(poll);
                btn.disabled = false;
                btn.textContent = '\u27F3 Restart AgentRunr';
                showToast('Restart may have failed. Check if the process manager restarted the app.', 'error');
            }
        }, 2000);
    } catch (error) {
        btn.disabled = false;
        btn.textContent = '\u27F3 Restart AgentRunr';
        showToast('Failed to restart: ' + error.message, 'error');
    }
});

// Jobs View
async function loadJobs() {
    try {
        const response = await fetch(`${API_BASE}/jobs/stats`);
        if (!response.ok) return;
        const data = await response.json();

        // Stats cards
        const stats = data.stats || {};
        document.getElementById('statTotal').textContent = (stats.allTimeSucceeded || 0) + (stats.failed || 0);
        document.getElementById('statSucceeded').textContent = stats.allTimeSucceeded || 0;
        document.getElementById('statFailed').textContent = stats.failed || 0;

        // Recurring jobs table
        const container = document.getElementById('recurringJobsList');
        const jobs = data.recurringJobs || [];

        if (jobs.length === 0) {
            container.innerHTML = '<p class="muted">No recurring jobs configured.</p>';
            return;
        }

        let html = '<table class="jobs-table"><thead><tr>';
        html += '<th>Name</th><th>Schedule</th><th>Next Run</th>';
        html += '</tr></thead><tbody>';

        for (const job of jobs) {
            const nextRun = job.nextRun ? formatRelativeTime(job.nextRun) : '-';
            html += `<tr>`;
            html += `<td class="job-name">${escapeHtml(job.name)}</td>`;
            html += `<td class="job-schedule">${escapeHtml(job.schedule)}</td>`;
            html += `<td class="job-next-run">${nextRun}</td>`;
            html += `</tr>`;
        }

        html += '</tbody></table>';
        container.innerHTML = html;
    } catch (error) {
        // Silently fail
    }
}

function formatRelativeTime(isoString) {
    const target = new Date(isoString);
    const now = new Date();
    const diffMs = target - now;

    if (diffMs < 0) return 'now';

    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'in < 1 min';
    if (diffMin < 60) return `in ${diffMin} min`;

    const diffHours = Math.floor(diffMin / 60);
    if (diffHours < 24) return `in ${diffHours}h ${diffMin % 60}m`;

    const diffDays = Math.floor(diffHours / 24);
    return `in ${diffDays}d ${diffHours % 24}h`;
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Init
fetchSettings();
checkHealth();
setInterval(checkHealth, 30000);
chatInput.focus();
