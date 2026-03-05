// API 配置
const CONFIG = {
    ADMIN_BASE_URL: 'http://localhost:8080',
    CORE_BASE_URL: 'http://localhost:8081',
    GATEWAY_BASE_URL: 'http://localhost:8082'
};

// 如果部署在不同地址，可以从 URL 参数或 localStorage 读取
if (localStorage.getItem('adminBaseUrl')) {
    CONFIG.ADMIN_BASE_URL = localStorage.getItem('adminBaseUrl');
}
if (localStorage.getItem('coreBaseUrl')) {
    CONFIG.CORE_BASE_URL = localStorage.getItem('coreBaseUrl');
}
