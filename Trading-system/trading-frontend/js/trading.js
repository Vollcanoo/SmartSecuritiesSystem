// 交易下单逻辑

// 下单表单提交（系统自动生成订单号）
document.getElementById('place-order-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('place-order-result');
    
    // 不传 clOrderId，由系统自动生成
    const orderData = {
        market: document.getElementById('market').value,
        securityId: document.getElementById('securityId').value.trim(),
        side: document.getElementById('side').value,
        qty: parseInt(document.getElementById('qty').value),
        price: parseFloat(document.getElementById('price').value),
        shareholderId: document.getElementById('shareholderId').value.trim()
    };

    const result = await CoreAPI.placeOrder(orderData);
    
    if (result.success) {
        const data = result.data;
        if (data.rejectCode) {
            // 订单被拒绝
            showResult('place-order-result', 
                `订单被拒绝: ${data.rejectText || '未知原因'} (错误码: ${data.rejectCode})`, 
                'error');
        } else {
            // 订单成功，显示系统生成的订单号
            showResult('place-order-result', 
                `订单提交成功！订单编号: ${data.clOrderId || 'N/A'}, 引擎订单ID: ${data.orderId || 'N/A'}`, 
                'success');
            document.getElementById('place-order-form').reset();
        }
    } else {
        showResult('place-order-result', `下单失败: ${result.error}`, 'error');
    }
});

// 撤单表单提交（只需原订单号，系统自动生成撤单号并填充其他字段）
document.getElementById('cancel-order-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const resultEl = document.getElementById('cancel-order-result');
    
    // 只需提供原订单号，其他字段由系统从挂单记录自动获取
    const cancelData = {
        origClOrderId: document.getElementById('origClOrderId').value.trim()
    };

    const result = await CoreAPI.cancelOrder(cancelData);
    
    if (result.success) {
        const data = result.data;
        if (data.rejectCode) {
            // 撤单被拒绝
            showResult('cancel-order-result', 
                `撤单被拒绝: ${data.rejectText || '未知原因'} (错误码: ${data.rejectCode})`, 
                'error');
        } else {
            // 撤单成功，显示系统生成的撤单号
            showResult('cancel-order-result', 
                `撤单成功！撤单编号: ${data.clOrderId || 'N/A'}, 已撤数量: ${data.canceledQty || 0}, 累计成交: ${data.cumQty || 0}`, 
                'success');
            document.getElementById('cancel-order-form').reset();
        }
    } else {
        showResult('cancel-order-result', `撤单失败: ${result.error}`, 'error');
    }
});
