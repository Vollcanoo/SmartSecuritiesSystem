// API 配置
const SERVER_IP = '129.211.187.179';
const CONFIG = {
    ADMIN_BASE_URL: 'http://' + SERVER_IP + ':8082',
    CORE_BASE_URL: 'http://' + SERVER_IP + ':8083',
    GATEWAY_BASE_URL: 'http://' + SERVER_IP + ':9001'
};

// 如果部署在不同地址，可以从 URL 参数或 localStorage 读取
if (localStorage.getItem('adminBaseUrl')) {
    CONFIG.ADMIN_BASE_URL = localStorage.getItem('adminBaseUrl');
}
if (localStorage.getItem('coreBaseUrl')) {
    CONFIG.CORE_BASE_URL = localStorage.getItem('coreBaseUrl');
}
