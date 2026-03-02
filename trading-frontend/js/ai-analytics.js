// ai-analytics.js — AI 智能分析

// 页面加载时恢复保存的 API Key
document.addEventListener('DOMContentLoaded', () => {
    const savedKey = localStorage.getItem('siliconflow_api_key');
    if (savedKey) {
        document.getElementById('ai-apikey').value = savedKey;
        document.getElementById('key-status').textContent = '● KEY LOADED';
        document.getElementById('key-status').style.color = 'var(--accent-green)';
    }
    loadStatsOverview();
});

// 保存 API Key
function saveApiKey() {
    const key = document.getElementById('ai-apikey').value.trim();
    if (key) {
        localStorage.setItem('siliconflow_api_key', key);
        document.getElementById('key-status').textContent = '● KEY SAVED';
        document.getElementById('key-status').style.color = 'var(--accent-green)';
        setTimeout(() => {
            document.getElementById('key-status').textContent = '';
        }, 2000);
    }
}

// 加载数据统计概览
async function loadStatsOverview() {
    const el = document.getElementById('stats-overview');
    try {
        const resp = await fetch(`${CONFIG.ADMIN_BASE_URL}/api/analytics/overview`);
        const result = await resp.json();
        
        if (result.code === 0 && result.data) {
            const d = result.data;
            const sb = d.statusBreakdown || {};
            el.innerHTML = `
                <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:12px;">
                    <div class="stat-card">
                        <h3>总订单数</h3>
                        <div class="stat-value" style="font-size:22px;">${d.totalOrders || 0}</div>
                    </div>
                    <div class="stat-card">
                        <h3>成交率</h3>
                        <div class="stat-value" style="font-size:22px;color:var(--accent-green);">${d.fillRate || 0}%</div>
                    </div>
                    <div class="stat-card">
                        <h3>撤单率</h3>
                        <div class="stat-value" style="font-size:22px;color:var(--accent-red);">${d.cancelRate || 0}%</div>
                    </div>
                    <div class="stat-card">
                        <h3>总成交量</h3>
                        <div class="stat-value" style="font-size:22px;">${(d.totalFilledQty || 0).toLocaleString()}</div>
                    </div>
                    <div class="stat-card">
                        <h3>总成交额</h3>
                        <div class="stat-value" style="font-size:22px;color:var(--accent-yellow);">¥${(d.totalTurnover || 0).toLocaleString()}</div>
                    </div>
                    <div class="stat-card">
                        <h3>活跃交易员</h3>
                        <div class="stat-value" style="font-size:22px;">${d.activeShareholders || 0}</div>
                    </div>
                </div>
                <div style="margin-top:12px;padding:8px 12px;background:var(--bg-input);border-radius:2px;border:1px solid var(--border-color);">
                    <span style="color:var(--accent-cyan);">STATUS:</span>
                    <span style="color:var(--accent-cyan);">LIVE ${sb.LIVE || 0}</span> |
                    <span style="color:var(--accent-yellow);">PARTIAL ${sb.PARTIALLY_FILLED || 0}</span> |
                    <span style="color:var(--accent-green);">FILLED ${sb.FILLED || 0}</span> |
                    <span style="color:var(--accent-red);">CANCELLED ${sb.CANCELLED || 0}</span>
                    &nbsp;&nbsp;|&nbsp;&nbsp;
                    <span>BUY ${d.buyOrders || 0}</span> |
                    <span>SELL ${d.sellOrders || 0}</span>
                </div>
            `;
        } else {
            el.textContent = 'NO DATA AVAILABLE';
        }
    } catch (err) {
        el.textContent = `ERROR: ${err.message}`;
    }
}

// AI 分析
document.getElementById('ai-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    const apiKey = document.getElementById('ai-apikey').value.trim();
    const model = document.getElementById('ai-model').value;
    const question = document.getElementById('ai-question').value.trim();
    
    if (!apiKey) {
        alert('请输入 SiliconFlow API Key');
        return;
    }
    
    const outputEl = document.getElementById('ai-output');
    const usageEl = document.getElementById('token-usage');
    
    outputEl.innerHTML = `
        <div class="ai-loading">
            <div class="spinner"></div>
            <span>AI ANALYZING... 正在调用 ${model} 模型分析交易数据，请稍候...</span>
        </div>
    `;
    usageEl.style.display = 'none';
    
    try {
        const resp = await fetch(`${CONFIG.ADMIN_BASE_URL}/api/ai/analyze`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ apiKey, model, question })
        });
        const result = await resp.json();
        
        if (result.code === 0 && result.data) {
            const analysis = result.data.analysis || '';
            // 简单的 Markdown 渲染
            outputEl.innerHTML = `<div class="md-rendered">${renderMarkdown(analysis)}</div>`;
            
            // 显示 token 使用量
            if (result.data.usage) {
                const u = result.data.usage;
                usageEl.innerHTML = `
                    MODEL: ${result.data.model} | 
                    PROMPT: ${u.promptTokens} tokens | 
                    COMPLETION: ${u.completionTokens} tokens | 
                    TOTAL: ${u.totalTokens} tokens
                `;
                usageEl.style.display = 'block';
            }
        } else {
            outputEl.innerHTML = `<span style="color:var(--accent-red);">ERROR: ${result.message || 'Unknown error'}</span>`;
        }
    } catch (err) {
        outputEl.innerHTML = `<span style="color:var(--accent-red);">REQUEST FAILED: ${err.message}</span>`;
    }
});

// 简单 Markdown → HTML 渲染
function renderMarkdown(md) {
    if (!md) return '';
    let html = md
        // 代码块
        .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre style="background:rgba(0,0,0,0.3);padding:12px;border-radius:4px;overflow-x:auto;"><code>$2</code></pre>')
        // 标题
        .replace(/^### (.+)$/gm, '<h3>$1</h3>')
        .replace(/^## (.+)$/gm, '<h2>$1</h2>')
        .replace(/^# (.+)$/gm, '<h1>$1</h1>')
        // 粗体和斜体
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\*(.+?)\*/g, '<em>$1</em>')
        // 内联代码
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        // 列表
        .replace(/^\- (.+)$/gm, '<li>$1</li>')
        .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
        // 分割线
        .replace(/^---$/gm, '<hr style="border-color:var(--border-color);margin:16px 0;">')
        // 引用
        .replace(/^> (.+)$/gm, '<blockquote>$1</blockquote>')
        // 段落
        .replace(/\n\n/g, '</p><p>')
        .replace(/\n/g, '<br>');
    
    return '<p>' + html + '</p>';
}
