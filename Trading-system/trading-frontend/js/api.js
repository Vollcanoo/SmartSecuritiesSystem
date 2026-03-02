// API 调用封装

// 通用请求函数
async function apiRequest(url, options = {}) {
    try {
        console.log('API Request:', url, options);
        const response = await fetch(url, {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        });

        console.log('API Response status:', response.status, response.statusText);

        // 204 No Content 无响应体
        if (response.status === 204) {
            if (response.ok) return { success: true, data: null };
        }

        // 检查响应内容类型
        const contentType = response.headers.get('content-type');
        let data;
        
        if (contentType && contentType.includes('application/json')) {
            const text = await response.text();
            data = text ? JSON.parse(text) : null;
        } else {
            const text = await response.text();
            try {
                data = text ? JSON.parse(text) : null;
            } catch (e) {
                data = text ? { raw: text } : null;
            }
        }
        
        console.log('API Response data:', data);
        
        if (response.ok) {
            return { success: true, data };
        }
        
        // 其他接口检查是否有错误信息
        throw new Error(data.message || data.error || `HTTP ${response.status}`);
    } catch (error) {
        console.error('API request failed:', url, error);
        return { success: false, error: error.message };
    }
}

// Admin API
const AdminAPI = {
    // 创建用户
    async createUser(userData) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users`, {
            method: 'POST',
            body: JSON.stringify(userData)
        });
    },

    // 查询所有用户
    async getAllUsers() {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users`);
    },

    // 按 uid 查询用户
    async getUserByUid(uid) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users/${uid}`);
    },

    // 按股东号查询用户
    async getUserByShareholderId(shareholderId) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users/shareholder/${shareholderId}`);
    },

    // 禁用用户
    async suspendUser(uid) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users/${uid}/suspend`, {
            method: 'POST'
        });
    },

    // 启用用户
    async resumeUser(uid) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users/${uid}/resume`, {
            method: 'POST'
        });
    },

    // 修改用户名
    async updateUsername(uid, newUsername) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users/${uid}/username`, {
            method: 'PUT',
            body: JSON.stringify({ username: newUsername })
        });
    },

    // 查询用户余额
    async getUserBalance(uid) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/users/${uid}/balance`);
    },

    // 查询订单历史
    async getOrderHistory(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.shareholderId) queryParams.append('shareholderId', params.shareholderId);
        if (params.status) queryParams.append('status', params.status);
        if (params.page !== undefined) queryParams.append('page', params.page);
        if (params.size) queryParams.append('size', params.size);
        
        const url = `${CONFIG.ADMIN_BASE_URL}/api/orders/history${queryParams.toString() ? '?' + queryParams.toString() : ''}`;
        return await apiRequest(url);
    },

    // 用户交易分析
    async getUserAnalysis(uid, start, end) {
        const queryParams = new URLSearchParams();
        if (start) queryParams.append('start', start);
        if (end) queryParams.append('end', end);
        const url = `${CONFIG.ADMIN_BASE_URL}/analysis/user/${uid}?${queryParams.toString()}`;
        return await apiRequest(url);
    },

    // 删除单条历史订单
    async deleteOrderHistory(id) {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/orders/history/${id}`, { method: 'DELETE' });
    },

    // 删除全部历史订单
    async deleteAllOrderHistory() {
        return await apiRequest(`${CONFIG.ADMIN_BASE_URL}/api/orders/history`, { method: 'DELETE' });
    },

    // 健康检查
    async healthCheck() {
        try {
            const response = await fetch(`${CONFIG.ADMIN_BASE_URL}/actuator/health`);
            if (!response.ok) {
                return { success: false, error: `HTTP ${response.status}` };
            }
            const data = await response.json();
            // 健康检查返回 {"status":"UP"} 或类似格式
            return { success: true, data };
        } catch (error) {
            console.error('Health check failed:', error);
            return { success: false, error: error.message };
        }
    }
};

// Core API
const CoreAPI = {
    // 下单
    async placeOrder(orderData) {
        return await apiRequest(`${CONFIG.CORE_BASE_URL}/api/order`, {
            method: 'POST',
            body: JSON.stringify(orderData)
        });
    },

    // 撤单
    async cancelOrder(cancelData) {
        return await apiRequest(`${CONFIG.CORE_BASE_URL}/api/cancel`, {
            method: 'POST',
            body: JSON.stringify(cancelData)
        });
    },

    // 查询挂单
    async getOpenOrders(shareholderId) {
        return await apiRequest(`${CONFIG.CORE_BASE_URL}/api/orders?shareholderId=${shareholderId}`);
    },

    // 健康检查
    async healthCheck() {
        try {
            const response = await fetch(`${CONFIG.CORE_BASE_URL}/actuator/health`);
            if (!response.ok) {
                return { success: false, error: `HTTP ${response.status}` };
            }
            const data = await response.json();
            // 健康检查返回 {"status":"UP"} 或类似格式
            return { success: true, data };
        } catch (error) {
            console.error('Health check failed:', error);
            return { success: false, error: error.message };
        }
    }
};

// 工具函数：显示结果消息
function showResult(elementId, message, type = 'info') {
    const element = document.getElementById(elementId);
    if (!element) return;
    
    element.textContent = message;
    element.className = `result-message ${type}`;
    element.style.display = 'block';
    
    // 3秒后自动隐藏成功/信息消息
    if (type === 'success' || type === 'info') {
        setTimeout(() => {
            element.style.display = 'none';
        }, 3000);
    }
}

// 工具函数：格式化日期时间
function formatDateTime(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

// 工具函数：格式化状态
function formatStatus(status) {
    const statusMap = {
        '1': { text: '启用', class: 'active' },
        '0': { text: '禁用', class: 'suspended' },
        'LIVE': { text: '挂单中', class: 'live' },
        'PARTIALLY_FILLED': { text: '部分成交', class: 'partially-filled' },
        'FILLED': { text: '全部成交', class: 'filled' },
        'CANCELLED': { text: '已撤单', class: 'cancelled' }
    };
    const info = statusMap[status] || { text: status, class: '' };
    return `<span class="status-badge ${info.class}">${info.text}</span>`;
}
