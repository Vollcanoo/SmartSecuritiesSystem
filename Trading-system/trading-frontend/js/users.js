// 用户管理逻辑

let allUsers = [];

// 创建用户（只需用户名）
document.getElementById('create-user-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('create-user-result');
    
    const formData = {
        username: document.getElementById('username').value.trim()
    };

    const result = await AdminAPI.createUser(formData);
    
    if (result.success) {
        const user = result.data.data;
        showResult('create-user-result', 
            `用户创建成功！用户ID: ${user.uid}, 股东号: ${user.shareholderId}`, 
            'success');
        document.getElementById('create-user-form').reset();
        loadUsers();
    } else {
        // 检查是否是 409 冲突
        if (result.error && result.error.includes('409')) {
            showResult('create-user-result', '用户名已存在，请使用其他用户名', 'error');
        } else {
            showResult('create-user-result', `创建失败: ${result.error}`, 'error');
        }
    }
});

// 加载用户列表
async function loadUsers() {
    const tbody = document.getElementById('users-table-body');
    if (!tbody) return;

    tbody.innerHTML = '<tr><td colspan="6" class="loading">加载中...</td></tr>';

    const result = await AdminAPI.getAllUsers();
    
    if (result.success && result.data && result.data.data) {
        allUsers = result.data.data;
        renderUsers(allUsers);
    } else {
        tbody.innerHTML = '<tr><td colspan="6" class="loading">加载失败</td></tr>';
    }
}

// 渲染用户列表
function renderUsers(users) {
    const tbody = document.getElementById('users-table-body');
    if (!tbody) return;

    if (users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" class="loading">暂无用户</td></tr>';
        return;
    }

    tbody.innerHTML = users.map(user => `
        <tr>
            <td>${user.uid}</td>
            <td>${user.shareholderId}</td>
            <td>${user.username}</td>
            <td>${formatStatus(user.status.toString())}</td>
            <td>${formatDateTime(user.createdAt)}</td>
            <td>
                <button onclick="editUsername(${user.uid}, '${(user.username || '').replace(/'/g, "\\'")}')" class="btn btn-sm btn-info">修改用户名</button>
                <button onclick="viewBalance(${user.uid})" class="btn btn-sm btn-success">查询余额</button>
                ${user.status === 1 
                    ? `<button onclick="suspendUser(${user.uid})" class="btn btn-sm btn-warning">禁用</button>`
                    : `<button onclick="resumeUser(${user.uid})" class="btn btn-sm btn-success">启用</button>`
                }
            </td>
        </tr>
    `).join('');
}

// 过滤用户
function filterUsers() {
    const keyword = document.getElementById('search-shareholder').value.trim().toLowerCase();
    if (!keyword) {
        renderUsers(allUsers);
        return;
    }

    const filtered = allUsers.filter(user => 
        user.shareholderId.toLowerCase().includes(keyword)
    );
    renderUsers(filtered);
}

// 禁用用户
async function suspendUser(uid) {
    if (!confirm(`确定要禁用用户 ${uid} 吗？`)) return;

    const result = await AdminAPI.suspendUser(uid);
    if (result.success) {
        showResult('create-user-result', '用户已禁用', 'success');
        loadUsers();
    } else {
        showResult('create-user-result', `操作失败: ${result.error}`, 'error');
    }
}

// 启用用户
async function resumeUser(uid) {
    if (!confirm(`确定要启用用户 ${uid} 吗？`)) return;

    const result = await AdminAPI.resumeUser(uid);
    if (result.success) {
        showResult('create-user-result', '用户已启用', 'success');
        loadUsers();
    } else {
        showResult('create-user-result', `操作失败: ${result.error}`, 'error');
    }
}

// 修改用户名
function editUsername(uid, currentUsername) {
    document.getElementById('edit-uid').value = uid;
    document.getElementById('edit-username').value = currentUsername;
    document.getElementById('edit-username-modal').style.display = 'block';
}

function closeEditModal() {
    document.getElementById('edit-username-modal').style.display = 'none';
    document.getElementById('edit-username-result').style.display = 'none';
}

document.getElementById('edit-username-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const uid = parseInt(document.getElementById('edit-uid').value);
    const newUsername = document.getElementById('edit-username').value.trim();
    
    const result = await AdminAPI.updateUsername(uid, newUsername);
    
    if (result.success) {
        showResult('edit-username-result', '用户名修改成功！', 'success');
        setTimeout(() => {
            closeEditModal();
            loadUsers();
        }, 1500);
    } else {
        if (result.error && result.error.includes('409')) {
            showResult('edit-username-result', '用户名已存在，请使用其他用户名', 'error');
        } else {
            showResult('edit-username-result', `修改失败: ${result.error}`, 'error');
        }
    }
});

// 查询余额
async function viewBalance(uid) {
    document.getElementById('balance-modal').style.display = 'block';
    const balanceInfo = document.getElementById('balance-info');
    balanceInfo.innerHTML = '<p>加载中...</p>';
    
    const result = await AdminAPI.getUserBalance(uid);
    
    if (result.success && result.data && result.data.data) {
        const balance = result.data.data;
        balanceInfo.innerHTML = `
            <p><strong>用户ID:</strong> ${balance.uid}</p>
            <p><strong>资金余额:</strong> ${balance.quoteBalance || 0}</p>
            <p><strong>持仓余额:</strong> ${balance.baseBalance || 0}</p>
            ${balance.note ? `<p style="color: #718096; font-size: 12px;">${balance.note}</p>` : ''}
        `;
    } else {
        balanceInfo.innerHTML = `<p style="color: #f56565;">查询失败: ${result.error || '未知错误'}</p>`;
    }
}

function closeBalanceModal() {
    document.getElementById('balance-modal').style.display = 'none';
}

// 点击模态框外部关闭
window.onclick = function(event) {
    const editModal = document.getElementById('edit-username-modal');
    const balanceModal = document.getElementById('balance-modal');
    if (event.target === editModal) {
        closeEditModal();
    }
    if (event.target === balanceModal) {
        closeBalanceModal();
    }
}

// 页面加载时加载用户列表
document.addEventListener('DOMContentLoaded', () => {
    loadUsers();
});
