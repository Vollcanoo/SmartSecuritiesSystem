// market-data.js — 实时行情面板（数据源: 新浪财经 via 后端代理）

let currentMarketFilter = '';
let allQuotes = [];
let refreshTimer = null;

// ─── 加载行情数据 ───
async function loadMarketData() {
    try {
        const url = currentMarketFilter
            ? `${CONFIG.CORE_BASE_URL}/api/market-data?market=${currentMarketFilter}`
            : `${CONFIG.CORE_BASE_URL}/api/market-data`;

        const resp = await fetch(url);
        const result = await resp.json();

        if (result.code === 0 && result.data) {
            allQuotes = Object.values(result.data);
            updateStats(allQuotes);
            filterTable();
            document.getElementById('last-update').textContent =
                new Date().toLocaleTimeString('zh-CN', { hour12: false });
        }
    } catch (err) {
        document.getElementById('market-tbody').innerHTML =
            `<tr><td colspan="13" style="text-align:center;color:var(--accent-red);padding:30px;">CONNECTION ERROR: ${err.message}</td></tr>`;
    }
}

// ─── 更新统计 ───
function updateStats(quotes) {
    document.getElementById('stat-total').textContent = quotes.length;
    document.getElementById('stat-a').textContent = quotes.filter(q => q.market === 'SH' || q.market === 'SZ').length;
    document.getElementById('stat-hk').textContent = quotes.filter(q => q.market === 'HK').length;
    document.getElementById('stat-us').textContent = quotes.filter(q => q.market === 'US').length;

    let up = 0, down = 0;
    quotes.forEach(q => {
        if (q.prevClose > 0) {
            if (q.lastPrice > q.prevClose) up++;
            else if (q.lastPrice < q.prevClose) down++;
        }
    });
    document.getElementById('stat-up').textContent = up;
    document.getElementById('stat-down').textContent = down;
}

// ─── 过滤和渲染表格 ───
function filterTable() {
    const keyword = (document.getElementById('search-input').value || '').toLowerCase();
    let filtered = allQuotes;

    if (keyword) {
        filtered = filtered.filter(q =>
            (q.securityId && q.securityId.toLowerCase().includes(keyword)) ||
            (q.name && q.name.toLowerCase().includes(keyword)) ||
            (q.sinaSymbol && q.sinaSymbol.includes(keyword))
        );
    }

    // 按涨跌幅降序排序
    filtered.sort((a, b) => {
        const chgA = a.prevClose > 0 ? (a.lastPrice - a.prevClose) / a.prevClose : 0;
        const chgB = b.prevClose > 0 ? (b.lastPrice - b.prevClose) / b.prevClose : 0;
        return chgB - chgA;
    });

    renderTable(filtered);
}

function renderTable(quotes) {
    const tbody = document.getElementById('market-tbody');

    if (quotes.length === 0) {
        tbody.innerHTML = '<tr><td colspan="13" style="text-align:center;color:var(--text-muted);padding:40px;">暂无行情数据 — 添加关注后自动拉取</td></tr>';
        return;
    }

    tbody.innerHTML = quotes.map(q => {
        const change = q.prevClose > 0 ? q.lastPrice - q.prevClose : 0;
        const changePct = q.prevClose > 0 ? (change / q.prevClose * 100) : 0;
        const dir = change > 0 ? 'up' : change < 0 ? 'down' : 'flat';
        const arrow = change > 0 ? '+' : change < 0 ? '' : ''; // Symbol handled by value sign
        const marketBadge = getMarketBadge(q.market);

        return `<tr>
            <td>
                <span class="stock-code">${marketBadge} ${q.securityId || '--'}</span>
            </td>
            <td><span class="stock-name">${q.name || '--'}</span></td>
            <td class="${dir}" style="font-weight:600;">${fp(q.lastPrice)}</td>
            <td class="${dir}">${change > 0 ? '+' : ''}${change.toFixed(2)}</td>
            <td class="${dir}" style="font-weight:600;">${changePct >= 0 ? '+' : ''}${changePct.toFixed(2)}%</td>
            <td>${fp(q.openPrice)}</td>
            <td>${fp(q.highPrice)}</td>
            <td>${fp(q.lowPrice)}</td>
            <td class="up">${fp(q.bidPrice)}</td>
            <td class="down">${fp(q.askPrice)}</td>
            <td>${fv(q.volume)}</td>
            <td>${famt(q.turnover)}</td>
            <td class="action-cell">
                <button class="unwatch-btn" onclick="unwatchSymbol('${q.sinaSymbol}')">✕</button>
            </td>
        </tr>`;
    }).join('');
}

// ─── 格式化工具 ───
function fp(p) {
    if (!p || p <= 0) return '<span style="color:var(--text-muted)">-.--</span>';
    return p.toFixed(2);
}

function fv(v) {
    if (!v) return '0';
    if (v >= 100000000) return (v / 100000000).toFixed(2) + '亿';
    if (v >= 10000) return (v / 10000).toFixed(1) + '万';
    return v.toLocaleString();
}

function famt(a) {
    if (!a) return '0';
    if (a >= 100000000) return (a / 100000000).toFixed(2) + '亿';
    if (a >= 10000) return (a / 10000).toFixed(1) + '万';
    return a.toFixed(0);
}

function getMarketBadge(market) {
    switch (market) {
        case 'SH': return '<span style="color:var(--accent-red);">沪</span>';
        case 'SZ': return '<span style="color:var(--accent-orange);">深</span>';
        case 'HK': return '<span style="color:var(--accent-purple);">港</span>';
        case 'US': return '<span style="color:var(--accent-cyan);">美</span>';
        default: return '';
    }
}

// ─── 市场过滤 ───
function setMarketFilter(btn, market) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentMarketFilter = market;
    loadMarketData();
}

// ─── 添加关注 ───
async function addSymbols() {
    const input = document.getElementById('add-input');
    const raw = input.value.trim();
    if (!raw) return;

    const symbols = raw.split(/[,，\s]+/).filter(s => s.length > 0);

    try {
        const resp = await fetch(`${CONFIG.CORE_BASE_URL}/api/market-data/watch`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ symbols })
        });
        const data = await resp.json();
        if (data.code === 0) {
            showResult('add-result', `已添加: ${data.added.join(', ')} (共关注 ${data.watchlistSize} 个)`, 'success');
            input.value = '';
            setTimeout(loadMarketData, 1000); // 等后端拉取后再刷新
        } else {
            showResult('add-result', data.message, 'error');
        }
    } catch (err) {
        showResult('add-result', `添加失败: ${err.message}`, 'error');
    }
}

// ─── 取消关注 ───
async function unwatchSymbol(sinaSymbol) {
    try {
        await fetch(`${CONFIG.CORE_BASE_URL}/api/market-data/unwatch`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ symbols: [sinaSymbol] })
        });
        loadMarketData();
    } catch (err) {
        console.error('Unwatch failed:', err);
    }
}

// ─── 预设板块 ───
async function loadPreset(name) {
    try {
        const resp = await fetch(`${CONFIG.CORE_BASE_URL}/api/market-data/preset/${name}`, { method: 'POST' });
        const data = await resp.json();
        showResult('add-result', `已加载预设板块: ${name} (共关注 ${data.watchlistSize} 个)`, 'success');
        setTimeout(loadMarketData, 1500);
    } catch (err) {
        showResult('add-result', `加载预设失败: ${err.message}`, 'error');
    }
}

// ─── 手动刷新 ───
async function manualRefresh() {
    try {
        await fetch(`${CONFIG.CORE_BASE_URL}/api/market-data/refresh`, { method: 'POST' });
        loadMarketData();
    } catch (err) {
        console.error('Refresh failed:', err);
    }
}

// ─── 清空 ───
async function clearAllData() {
    if (!confirm('确定清空所有关注和行情数据？')) return;
    try {
        await fetch(`${CONFIG.CORE_BASE_URL}/api/market-data`, { method: 'DELETE' });
        loadMarketData();
    } catch (err) {
        console.error('Clear failed:', err);
    }
}

// ─── 回车快捷键 ───
document.getElementById('add-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') addSymbols();
});

// ─── 自动刷新 ───
function startAutoRefresh() {
    if (refreshTimer) clearInterval(refreshTimer);
    refreshTimer = setInterval(loadMarketData, 5000);
}

// ─── 初始化 ───
document.addEventListener('DOMContentLoaded', () => {
    loadMarketData();
    startAutoRefresh();
});
