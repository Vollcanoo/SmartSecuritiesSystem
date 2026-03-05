// 订单历史逻辑

let currentPage = 0;
let pageSize = 50;
let searchParams = {};

// 搜索表单提交
document.getElementById('search-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    currentPage = 0;
    searchParams = {
        shareholderId: document.getElementById('search-shareholderId').value.trim(),
        status: document.getElementById('search-status').value,
        size: parseInt(document.getElementById('page-size').value)
    };
    pageSize = searchParams.size;
    await loadOrders();
});

// 重置搜索
function resetSearch() {
    document.getElementById('search-form').reset();
    currentPage = 0;
    searchParams = {};
    pageSize = 50;
    loadOrders();
}

// 切换页码
function changePage(delta) {
    currentPage = Math.max(0, currentPage + delta);
    loadOrders();
}

// 加载订单历史
async function loadOrders() {
    const tbody = document.getElementById('orders-table-body');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="12" class="loading">加载中...</td></tr>';

    const params = {
        ...searchParams,
        page: currentPage,
        size: pageSize
    };

    const result = await AdminAPI.getOrderHistory(params);
    
    if (result.success && result.data) {
        const orders = Array.isArray(result.data) ? result.data : [];
        renderOrders(orders);
        updatePagination(orders.length);
    } else {
        tbody.innerHTML = '<tr><td colspan="12" class="loading">加载失败</td></tr>';
    }
}

// 渲染订单列表
function renderOrders(orders) {
    const tbody = document.getElementById('orders-table-body');
    if (!tbody) return;

    if (orders.length === 0) {
        tbody.innerHTML = '<tr><td colspan="12" class="loading">暂无订单</td></tr>';
        return;
    }

    tbody.innerHTML = orders.map(order => `
        <tr>
            <td>${order.clOrderId || '-'}</td>
            <td>${order.shareholderId || '-'}</td>
            <td>${order.market || '-'}</td>
            <td>${order.securityId || '-'}</td>
            <td>${order.side === 'B' ? '买' : order.side === 'S' ? '卖' : order.side || '-'}</td>
            <td>${order.price || '-'}</td>
            <td>${order.orderQty || '-'}</td>
            <td>${order.filledQty || 0}</td>
            <td>${order.canceledQty || 0}</td>
            <td>${formatStatus(order.status)}</td>
            <td>${formatDateTime(order.createdAt)}</td>
            <td><button type="button" class="btn btn-sm btn-danger btn-delete-order" data-order-id="${order.id}" title="删除此条">删除</button></td>
        </tr>
    `).join('');
}

// 删除单条历史订单
async function deleteOrder(id) {
    if (!confirm('确定要删除这条订单记录吗？')) return;
    const result = await AdminAPI.deleteOrderHistory(id);
    if (result.success) {
        loadOrders();
    } else {
        alert('删除失败：' + (result.error || '未知错误'));
    }
}

// 删除全部历史订单
async function deleteAllOrders() {
    if (!confirm('确定要删除全部历史订单吗？此操作不可恢复。')) return;
    const result = await AdminAPI.deleteAllOrderHistory();
    if (result.success) {
        const count = result.data && result.data.deletedCount !== undefined ? result.data.deletedCount : 0;
        if (count > 0) alert('已删除 ' + count + ' 条记录');
        loadOrders();
    } else {
        alert('删除失败：' + (result.error || '未知错误'));
    }
}

// 更新分页信息
function updatePagination(count) {
    const infoEl = document.getElementById('pagination-info');
    const currentPageEl = document.getElementById('current-page');
    const prevBtn = document.getElementById('prev-page');
    const nextBtn = document.getElementById('next-page');

    if (infoEl) {
        infoEl.textContent = `第 ${currentPage + 1} 页，本页 ${count} 条`;
    }
    if (currentPageEl) {
        currentPageEl.textContent = currentPage + 1;
    }
    if (prevBtn) {
        prevBtn.disabled = currentPage === 0;
    }
    if (nextBtn) {
        nextBtn.disabled = count < pageSize;
    }
}

// 页面加载时初始化：绑定删除按钮事件
document.addEventListener('DOMContentLoaded', () => {
    // 删除全部
    const btnDeleteAll = document.getElementById('btn-delete-all-orders');
    if (btnDeleteAll) {
        btnDeleteAll.addEventListener('click', function () {
            deleteAllOrders();
        });
    }
    // 表格内删除单条（事件委托，动态生成的按钮也能响应）
    const tbody = document.getElementById('orders-table-body');
    if (tbody) {
        tbody.addEventListener('click', function (e) {
            const btn = e.target.closest('.btn-delete-order');
            if (!btn) return;
            const id = btn.getAttribute('data-order-id');
            if (id) deleteOrder(Number(id));
        });
    }
});
