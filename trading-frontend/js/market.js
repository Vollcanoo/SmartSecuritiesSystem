// 行情查询逻辑

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('market-query-form');
    const latestBox = document.getElementById('market-latest-box');
    const latestBody = document.getElementById('market-latest-body');

    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const market = document.getElementById('mq-market').value;
            const securityId = document.getElementById('mq-securityId').value.trim();
            if (!market || !securityId) {
                showResult('market-query-result', '请填写市场与股票代码', 'error');
                return;
            }

            const result = await CoreAPI.getMarketData(market, securityId);
            if (!result.success) {
                showResult('market-query-result', `查询失败: ${result.error}`, 'error');
                latestBox.style.display = 'none';
                latestBody.innerHTML = '';
                return;
            }
            const data = result.data;
            if (!data) {
                showResult('market-query-result', '暂无该股票的行情数据', 'info');
                latestBox.style.display = 'none';
                latestBody.innerHTML = '';
                return;
            }

            latestBody.innerHTML = `
                <tr>
                    <td>${data.market || '-'}</td>
                    <td>${data.securityId || '-'}</td>
                    <td>${data.bidPrice != null ? data.bidPrice : '-'}</td>
                    <td>${data.askPrice != null ? data.askPrice : '-'}</td>
                </tr>
            `;
            latestBox.style.display = 'block';
            showResult('market-query-result', '查询成功', 'success');
        });
    }
});

