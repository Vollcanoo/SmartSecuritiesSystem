// 当前挂单逻辑

let currentShareholderId = '';

// 搜索表单提交
document.getElementById('search-open-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    currentShareholderId = document.getElementById('open-shareholderId').value.trim();
    if (currentShareholderId) {
        await loadOpenOrders();
    }
});

// 加载挂单
async function loadOpenOrders() {
    const tbody = document.getElementById('open-orders-table-body');
    if (!tbody) return;

    if (!currentShareholderId) {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">请输入股东号</td></tr>';
        return;
    }

    tbody.innerHTML = '<tr><td colspan="9" class="loading">加载中...</td></tr>';

    const result = await CoreAPI.getOpenOrders(currentShareholderId);
    
    if (result.success && result.data) {
        const orders = Array.isArray(result.data) ? result.data : [];
        renderOpenOrders(orders);
    } else {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">加载失败</td></tr>';
    }
}

// 渲染挂单列表
function renderOpenOrders(orders) {
    const tbody = document.getElementById('open-orders-table-body');
    if (!tbody) return;

    if (orders.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">暂无挂单</td></tr>';
        return;
    }

    tbody.innerHTML = orders.map(order => {
        const remaining = (order.orderQty || 0) - (order.filledQty || 0);
        return `
        <tr>
            <td>${order.clOrderId || '-'}</td>
            <td>${order.shareholderId || '-'}</td>
            <td>${order.market || '-'}</td>
            <td>${order.securityId || '-'}</td>
            <td>${order.side === 'B' ? '买' : order.side === 'S' ? '卖' : order.side || '-'}</td>
            <td>${order.price || '-'}</td>
            <td>${order.orderQty || '-'}</td>
            <td>${order.filledQty || 0}</td>
            <td>${remaining}</td>
        </tr>
    `;
    }).join('');
}

// 页面加载时，如果有股东号参数则自动加载
document.addEventListener('DOMContentLoaded', () => {
    const urlParams = new URLSearchParams(window.location.search);
    const shareholderId = urlParams.get('shareholderId');
    if (shareholderId) {
        document.getElementById('open-shareholderId').value = shareholderId;
        currentShareholderId = shareholderId;
        loadOpenOrders();
    }
});
