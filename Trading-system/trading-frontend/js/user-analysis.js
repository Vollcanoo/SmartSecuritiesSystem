// 用户交易分析逻辑

function formatToBackendDateTimeLocal(value) {
    // datetime-local: "2026-03-02T12:34"
    // 后端 LocalDateTime.parse 期望 "2026-03-02T12:34" 或带秒
    if (!value) return '';
    // 补秒
    if (value.length === 16) {
        return value + ':00';
    }
    return value;
}

function setAnalysisLoading(loading) {
    const msgEl = document.getElementById('analysis-message');
    if (!msgEl) return;
    if (loading) {
        msgEl.textContent = '分析中，请稍候...';
        msgEl.className = 'result-message info';
        msgEl.style.display = 'block';
    }
}

function showAnalysisMessage(text, type = 'info') {
    const msgEl = document.getElementById('analysis-message');
    if (!msgEl) return;
    msgEl.textContent = text;
    msgEl.className = `result-message ${type}`;
    msgEl.style.display = 'block';
}

function renderAnalysisStats(data) {
    const totalOrdersEl = document.getElementById('stat-total-orders');
    const filledOrdersEl = document.getElementById('stat-filled-orders');
    const cancelledOrdersEl = document.getElementById('stat-cancelled-orders');
    const totalTurnoverEl = document.getElementById('stat-total-turnover');
    const avgPriceEl = document.getElementById('stat-avg-price');

    if (!data) {
        totalOrdersEl.textContent = '-';
        filledOrdersEl.textContent = '-';
        cancelledOrdersEl.textContent = '-';
        totalTurnoverEl.textContent = '-';
        avgPriceEl.textContent = '-';
        return;
    }

    totalOrdersEl.textContent = data.totalOrders ?? 0;
    filledOrdersEl.textContent = data.filledOrders ?? 0;
    cancelledOrdersEl.textContent = data.cancelledOrders ?? 0;
    totalTurnoverEl.textContent = data.totalTurnover ?? 0;
    avgPriceEl.textContent = data.avgPrice ?? 0;

    // 渲染简单条形图
    const barFilled = document.getElementById('bar-filled');
    const barCancelled = document.getElementById('bar-cancelled');
    const barsWrapper = document.getElementById('analysis-bars');
    const emptyTip = document.getElementById('analysis-chart-empty');

    const filled = Number(data.filledOrders || 0);
    const cancelled = Number(data.cancelledOrders || 0);
    const total = filled + cancelled;

    if (total === 0) {
        barsWrapper.style.display = 'none';
        if (emptyTip) {
            emptyTip.style.display = 'block';
            emptyTip.textContent = '当前时间区间内无成交或撤单数据。';
        }
        return;
    }

    const filledPercent = Math.round((filled / total) * 100);
    const cancelledPercent = 100 - filledPercent;

    barFilled.style.width = filledPercent + '%';
    barCancelled.style.width = cancelledPercent + '%';

    barFilled.textContent = filled > 0 ? `成交 ${filledPercent}%` : '';
    barCancelled.textContent = cancelled > 0 ? `撤单 ${cancelledPercent}%` : '';

    if (emptyTip) {
        emptyTip.style.display = 'none';
    }
    barsWrapper.style.display = 'block';
}

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('analysis-form');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const uidInput = document.getElementById('analysis-uid');
        const startInput = document.getElementById('analysis-start');
        const endInput = document.getElementById('analysis-end');

        const uid = uidInput.value.trim();
        const start = formatToBackendDateTimeLocal(startInput.value);
        const end = formatToBackendDateTimeLocal(endInput.value);

        if (!uid || !start || !end) {
            showAnalysisMessage('请完整填写用户ID和时间范围。', 'error');
            return;
        }

        setAnalysisLoading(true);

        const result = await AdminAPI.getUserAnalysis(uid, start, end);

        if (result.success && result.data) {
            // 后端的统一返回有可能是 { data: { ... } } 结构
            const payload = result.data.data ?? result.data;
            renderAnalysisStats(payload);
            showAnalysisMessage('分析完成。', 'success');
        } else {
            renderAnalysisStats(null);
            showAnalysisMessage(`分析失败: ${result.error || '未知错误'}`, 'error');
        }
    });
});

