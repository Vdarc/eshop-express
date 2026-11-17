// 工具函数 - 全局变量形式
function initStatDate() {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function isChineseText(text) {
    return /[\u4e00-\u9fa5]/.test(text);
}

function scrollToBottom(selector) {
    setTimeout(() => {
        const chatContainer = document.querySelector(selector);
        if (chatContainer) {
            chatContainer.scrollTop = chatContainer.scrollHeight;
        }
    }, 100);
}

function getCurrentTime() {
    return new Date().toLocaleTimeString();
}

function handleExport(translationResult, selectedVersion, form, format) {
    const d = translationResult;
    const content = selectedVersion === 'A' ? d.versionA : d.versionB;

    if (format === 'txt') {
        const txtContent = `【版本${selectedVersion}】\n${content}\n\n【核心卖点】\n${d.core_selling_points.join('；')}\n\n【文化提示】\n${d.cultural_notes || '无'}\n\n【SEO关键词】\n${(d.seo_keywords || []).join(', ')}`;
        saveAs(new Blob([txtContent], {type: 'text/plain;charset=utf-8'}), `翻译结果.txt`);
    }

    if (format === 'excel') {
        const excelData = [
            ['原文', form.text],
            [`版本${selectedVersion}`, content],
            ['核心卖点', d.core_selling_points.join('；')],
            ['文化提示', d.cultural_notes || '无'],
            ['SEO关键词', (d.seo_keywords || []).join(', ')]
        ];
        const ws = XLSX.utils.aoa_to_sheet(excelData);
        const wb = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(wb, ws, '翻译结果');
        XLSX.writeFile(wb, `翻译结果.xlsx`);
    }
}

// 确保saveAs和XLSX可用
if (typeof saveAs === 'undefined') {
    console.error('FileSaver.js is not loaded!');
}

if (typeof XLSX === 'undefined') {
    console.error('XLSX is not loaded!');
}
