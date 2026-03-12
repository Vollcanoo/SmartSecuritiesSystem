// 用户交易分析逻辑

let ordersSummaryChart = null;
let incomeExpenseChart = null;

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

function toDatetimeLocalValue(date) {
    const pad = (n) => n.toString().padStart(2, '0');
    const year = date.getFullYear();
    const month = pad(date.getMonth() + 1);
    const day = pad(date.getDate());
    const hour = pad(date.getHours());
    const minute = pad(date.getMinutes());
    return `${year}-${month}-${day}T${hour}:${minute}`;
}

function setAnalysisLoading(loading) {
    const msgEl = document.getElementById('analysis-message');
    if (!msgEl) return;
    if (loading) {
        msgEl.textContent = '分析中，请稍候...';
        msgEl.className = 'result-message info';
        msgEl.style.display = 'block';
    } else {
        msgEl.style.display = 'none';
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
    const ordersCanvas = document.getElementById('chart-orders-summary');
    const incomeExpenseCanvas = document.getElementById('chart-income-expense');

    if (!ordersCanvas || !incomeExpenseCanvas || typeof Chart === 'undefined') {
        return;
    }

    if (!data) {
        if (ordersSummaryChart) {
            ordersSummaryChart.destroy();
            ordersSummaryChart = null;
        }
        if (incomeExpenseChart) {
            incomeExpenseChart.destroy();
            incomeExpenseChart = null;
        }
        if (emptyTip) {
            emptyTip.style.display = 'block';
            emptyTip.textContent = '当前时间区间内没有可绘制的可视化数据。';
        }
        return;
    }

    const totalOrders = Number(data.totalOrders || 0);
    const filledOrders = Number(data.filledOrders || 0);
    const cancelledOrders = Number(data.cancelledOrders || 0);

    // 若三个值全为 0，则认为没有可视化意义
    if (totalOrders === 0 && filledOrders === 0 && cancelledOrders === 0) {
        if (ordersSummaryChart) {
            ordersSummaryChart.destroy();
            ordersSummaryChart = null;
        }
        if (incomeExpenseChart) {
            incomeExpenseChart.destroy();
            incomeExpenseChart = null;
        }
        if (emptyTip) {
            emptyTip.style.display = 'block';
            emptyTip.textContent = '当前时间区间内没有可绘制的可视化数据。';
        }
        return;
    }

    const labels = ['订单总数', '成交订单数', '撤单订单数'];
    const values = [totalOrders, filledOrders, cancelledOrders];

    if (emptyTip) {
        emptyTip.style.display = 'none';
    }

    if (ordersSummaryChart) {
        ordersSummaryChart.destroy();
    }
    ordersSummaryChart = new Chart(ordersCanvas.getContext('2d'), {
        type: 'bar',
        data: {
            labels,
            datasets: [
                {
                    label: '订单数量',
                    data: values,
                    backgroundColor: [
                        'rgba(102, 126, 234, 0.8)',
                        'rgba(72, 187, 120, 0.8)',
                        'rgba(245, 101, 101, 0.8)'
                    ]
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
                        text: '指标'
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

    // 绘制“总支出 / 总收入”随时间的折线图（使用 dailyStats）
    if (!Array.isArray(data.dailyStats) || data.dailyStats.length === 0) {
        if (incomeExpenseChart) {
            incomeExpenseChart.destroy();
            incomeExpenseChart = null;
        }
        return;
    }

    const dateLabels = data.dailyStats.map(p => p.date);
    const incomeSeries = data.dailyStats.map(p => Number(p.totalIncome || 0));
    const expenseSeries = data.dailyStats.map(p => Number(p.totalExpense || 0));

    const hasIncome = incomeSeries.some(v => v !== 0);
    const hasExpense = expenseSeries.some(v => v !== 0);

    if (!hasIncome && !hasExpense) {
        if (incomeExpenseChart) {
            incomeExpenseChart.destroy();
            incomeExpenseChart = null;
        }
        return;
    }

    if (incomeExpenseChart) {
        incomeExpenseChart.destroy();
    }
    incomeExpenseChart = new Chart(incomeExpenseCanvas.getContext('2d'), {
        type: 'line',
        data: {
            labels: dateLabels,
            datasets: [
                {
                    label: '总收入（卖出）',
                    data: incomeSeries,
                    borderColor: 'rgba(72, 187, 120, 1)',
                    backgroundColor: 'rgba(72, 187, 120, 0.15)',
                    tension: 0.2,
                    fill: true
                },
                {
                    label: '总支出（买入）',
                    data: expenseSeries,
                    borderColor: 'rgba(245, 101, 101, 1)',
                    backgroundColor: 'rgba(245, 101, 101, 0.15)',
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
                        text: '金额'
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

    const rangeSelect = document.getElementById('analysis-range');
    const startInput = document.getElementById('analysis-start');
    const endInput = document.getElementById('analysis-end');

    function applyRange(value) {
        if (!value) {
            startInput.value = '';
            endInput.value = '';
            return;
        }
        const now = new Date();
        const end = new Date(now);
        const start = new Date(now);

        switch (value) {
            case '7d':
                start.setDate(start.getDate() - 7);
                break;
            case '1m':
                start.setMonth(start.getMonth() - 1);
                break;
            case '3m':
                start.setMonth(start.getMonth() - 3);
                break;
            case '6m':
                start.setMonth(start.getMonth() - 6);
                break;
            case '1y':
                start.setFullYear(start.getFullYear() - 1);
                break;
            default:
                break;
        }

        startInput.value = toDatetimeLocalValue(start);
        endInput.value = toDatetimeLocalValue(end);
    }

    if (rangeSelect) {
        rangeSelect.addEventListener('change', (e) => {
            applyRange(e.target.value);
        });
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        const uidInput = document.getElementById('analysis-uid');
        const selectedRange = rangeSelect ? rangeSelect.value : '';

        const uid = uidInput.value.trim();
        const start = formatToBackendDateTimeLocal(startInput.value);
        const end = formatToBackendDateTimeLocal(endInput.value);

        if (!uid || !selectedRange || !start || !end) {
            showAnalysisMessage('请填写用户ID并选择时间范围。', 'error');
            return;
        }

        setAnalysisLoading(true);
        console.log('[UserAnalysis] Start request', { uid, start, end });

        try {
            const timeoutMs = 10000; // 10 秒超时给出明确提示
            const timeoutPromise = new Promise((_, reject) => {
                setTimeout(() => {
                    reject(new Error('分析请求超时，请检查 Admin 服务是否启动 (8080) 以及网络连接。'));
                }, timeoutMs);
            });

            // 直接构造 URL 调用通用 apiRequest，避免依赖 AdminAPI 是否扩展了 getUserAnalysis
            const params = new URLSearchParams();
            if (start) params.append('start', start);
            if (end) params.append('end', end);
            const query = params.toString();
            const url = `${CONFIG.ADMIN_BASE_URL}/api/users/${uid}/analysis${query ? '?' + query : ''}`;

            const result = await Promise.race([
                apiRequest(url),
                timeoutPromise
            ]);

            console.log('[UserAnalysis] Raw result', result);

            if (!result || typeof result !== 'object') {
                renderAnalysisStats(null);
                showAnalysisMessage('分析失败: 未收到任何返回，请检查 Admin 服务是否已启动。', 'error');
                return;
            }

            if (result.success && result.data) {
                // 后端的统一返回有可能是 { code, data, message } 结构
                const payload = result.data.data ?? result.data;
                if (!payload) {
                    renderAnalysisStats(null);
                    showAnalysisMessage('分析完成：当前时间区间内没有订单数据。', 'info');
                    return;
                }

                renderAnalysisStats(payload);

                const total = Number(payload.totalOrders || 0);
                if (total === 0) {
                    showAnalysisMessage('分析完成：当前时间区间内没有订单数据。', 'info');
                } else {
                    showAnalysisMessage(`分析完成：共统计到 ${total} 条订单。`, 'success');
                }
            } else {
                const errorMsg = result.error || (result.data && result.data.message) || '未知错误';
                renderAnalysisStats(null);
                showAnalysisMessage(`分析失败: ${errorMsg}`, 'error');
            }
        } catch (err) {
            console.error('User analysis request failed:', err);
            renderAnalysisStats(null);
            showAnalysisMessage(`分析失败: ${err.message || '未知错误'}`, 'error');
        } finally {
            setAnalysisLoading(false);
        }
    });
});

