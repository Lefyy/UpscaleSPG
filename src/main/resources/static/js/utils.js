// js/utils.js
// Создаем глобальный объект 'utils', если его еще нет, или используем существующий
window.utils = window.utils || {};

/**
 * Форматирует размер файла в удобочитаемый формат (B, KB, MB, GB).
 * @param {number} bytes - Размер файла в байтах.
 * @param {number} decimals - Количество знаков после запятой (по умолчанию 2).
 * @returns {string} Отформатированная строка размера файла.
 */
window.utils.formatBytes = function(bytes, decimals = 2) {
    if (bytes === 0) return '0 Bytes';

    const k = 1024;
    const dm = decimals < 0 ? 0 : decimals;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];

    const i = Math.floor(Math.log(bytes) / Math.log(k));

    return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
};