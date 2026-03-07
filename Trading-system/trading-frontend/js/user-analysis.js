// 用户交易分析逻辑

let ordersPerDayChart = null;
let turnoverPerDayChart = null;

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
        renderAnalysisCharts(null);
        return;
    }

    totalOrdersEl.textContent = data.totalOrders ?? 0;
    filledOrdersEl.textContent = data.filledOrders ?? 0;
    cancelledOrdersEl.textContent = data.cancelledOrders ?? 0;
    totalTurnoverEl.textContent = data.totalTurnover ?? 0;
    avgPriceEl.textContent = data.avgPrice ?? 0;

    // 渲染简单条形图（成交 vs 撤单 占比）
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
    } else {
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

    // 使用后端返回的 dailyStats 渲染折线图、柱状图
    renderAnalysisCharts(data);
}

function renderAnalysisCharts(data) {
    const emptyTip = document.getElementById('analysis-chart-empty');
    const ordersCanvas = document.getElementById('chart-orders-per-day');
    const turnoverCanvas = document.getElementById('chart-turnover-per-day');

    if (!ordersCanvas || !turnoverCanvas || typeof Chart === 'undefined') {
        return;
    }

    if (!data || !Array.isArray(data.dailyStats) || data.dailyStats.length === 0) {
        if (ordersPerDayChart) {
            ordersPerDayChart.destroy();
            ordersPerDayChart = null;
        }
        if (turnoverPerDayChart) {
            turnoverPerDayChart.destroy();
            turnoverPerDayChart = null;
        }
        if (emptyTip) {
            emptyTip.style.display = 'block';
            emptyTip.textContent = '当前时间区间内没有可绘制的时间序列数据。';
        }
        return;
    }

    const labels = data.dailyStats.map(p => p.date);
    const totalOrdersSeries = data.dailyStats.map(p => p.totalOrders ?? 0);
    const filledOrdersSeries = data.dailyStats.map(p => p.filledOrders ?? 0);
    const cancelledOrdersSeries = data.dailyStats.map(p => p.cancelledOrders ?? 0);
    const turnoverSeries = data.dailyStats.map(p => Number(p.totalTurnover || 0));

    if (emptyTip) {
        emptyTip.style.display = 'none';
    }

    if (ordersPerDayChart) {
        ordersPerDayChart.destroy();
    }
    ordersPerDayChart = new Chart(ordersCanvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels,
            datasets: [
                {
                    label: '订单总数',
                    data: totalOrdersSeries,
                    backgroundColor: 'rgba(102, 126, 234, 0.8)'
                },
                {
                    label: '成交订单数',
                    data: filledOrdersSeries,
                    backgroundColor: 'rgba(72, 187, 120, 0.7)'
                },
                {
                    label: '撤单订单数',
                    data: cancelledOrdersSeries,
                    backgroundColor: 'rgba(245, 101, 101, 0.7)'
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: {
                    title: {
                        display: true,
                        text: '日期'
                    }
                },
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: '订单数'
                    }
                }
            },
            plugins: {
                legend: {
                    position: 'top'
                }
            }
        }
    });

    if (turnoverPerDayChart) {
        turnoverPerDayChart.destroy();
    }
    turnoverPerDayChart = new Chart(turnoverCanvas.getContext('2d'), {
        type: 'line',
        data: {
            labels,
            datasets: [
                {
                    label: '每日成交金额',
                    data: turnoverSeries,
                    borderColor: 'rgba(72, 187, 120, 1)',
                    backgroundColor: 'rgba(72, 187, 120, 0.2)',
                    tension: 0.2,
                    fill: true
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                x: {
                    title: {
                        display: true,
                        text: '日期'
                    }
                },
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: '成交金额'
                    }
                }
            },
            plugins: {
                legend: {
                    position: 'top'
                }
            }
        }
    });
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

