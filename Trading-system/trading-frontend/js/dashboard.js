// 仪表板逻辑

// 检查服务状态
async function checkServiceStatus() {
    const statusEl = document.getElementById('service-status');
    if (!statusEl) return;

    try {
        const [adminHealth, coreHealth] = await Promise.all([
            AdminAPI.healthCheck(),
            CoreAPI.healthCheck()
        ]);

        const adminOk = adminHealth.success && adminHealth.data && adminHealth.data.status === 'UP';
        const coreOk = coreHealth.success && coreHealth.data && coreHealth.data.status === 'UP';

        if (adminOk && coreOk) {
            statusEl.innerHTML = '<span style="color: #48bb78;">✓ 所有服务正常</span>';
        } else {
            const issues = [];
            if (!adminOk) {
                const error = adminHealth.error || '未知错误';
                issues.push(`Admin (${error})`);
            }
            if (!coreOk) {
                const error = coreHealth.error || '未知错误';
                issues.push(`Core (${error})`);
            }
            statusEl.innerHTML = `<span style="color: #ed8936;">⚠ ${issues.join(', ')}</span>`;
        }
    } catch (error) {
        console.error('Service status check error:', error);
        statusEl.innerHTML = '<span style="color: #f56565;">✗ 无法连接服务: ' + error.message + '</span>';
    }
}

// 加载用户总数
async function loadUserCount() {
    const countEl = document.getElementById('user-count');
    if (!countEl) return;

    try {
        const result = await AdminAPI.getAllUsers();
        if (result.success && result.data && result.data.data) {
            countEl.textContent = result.data.data.length;
        } else {
            console.error('Failed to load users:', result.error);
            countEl.textContent = '-';
        }
    } catch (error) {
        console.error('Error loading user count:', error);
        countEl.textContent = '-';
    }
}

// 加载今日订单数（简化版，实际可以从订单历史统计）
async function loadTodayOrders() {
    const ordersEl = document.getElementById('today-orders');
    if (!ordersEl) return;

    try {
        // 简化实现：查询所有订单
        const result = await AdminAPI.getOrderHistory({ size: 1000 });
        if (result.success && result.data && Array.isArray(result.data)) {
            const today = new Date().toISOString().split('T')[0];
            const todayOrders = result.data.filter(order => {
                if (!order.createdAt) return false;
                return order.createdAt.startsWith(today);
            });
            ordersEl.textContent = todayOrders.length;
        } else {
            console.error('Failed to load orders:', result.error);
            ordersEl.textContent = '-';
        }
    } catch (error) {
        console.error('Error loading today orders:', error);
        ordersEl.textContent = '-';
    }
}

// 页面加载时初始化
document.addEventListener('DOMContentLoaded', () => {
    console.log('Dashboard page loaded');
    
    // 延迟执行，确保所有脚本都已加载
    setTimeout(() => {
        console.log('Starting dashboard initialization...');
        checkServiceStatus();
        loadUserCount();
        loadTodayOrders();

        // 每30秒刷新一次状态
        setInterval(() => {
            checkServiceStatus();
            loadUserCount();
            loadTodayOrders();
        }, 30000);
    }, 100);
});
