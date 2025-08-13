window.api = window.api || {};

window.api.API_BASE_URL = 'http://localhost:8080/api/v1/images';

/**
 * Загружает изображение на сервер.
 * @param {FormData} formData - Объект FormData, содержащий файл и параметры.
 * @returns {Promise<string>} Promise, который разрешается с ID загруженного изображения.
 */
window.api.uploadImage = async function(formData) {
    const response = await fetch(window.api.API_BASE_URL, {
        method: 'POST',
        body: formData
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP error! Status: ${response.status}, Body: ${errorText}`);
    }
    return response.text();
};

/**
 * Получает текущий статус обработки изображения И МЕТАДАННЫЕ.
 * @param {string} imageId - ID изображения.
 * @returns {Promise<Object>} Promise, который разрешается с объектом, содержащим статус и метаданные.
 */
window.api.getImageStatus = async function(imageId) {
    const response = await fetch(`${window.api.API_BASE_URL}/${imageId}/status`);
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Status check failed! Status: ${response.status}, Body: ${errorText}`);
    }
    return response.json(); // ИЗМЕНЕНИЕ: Парсим ответ как JSON
};

/**
 * Получает URL обработанного изображения.
 * @param {string} imageId - ID изображения.
 * @returns {string} URL для загрузки обработанного изображения.
 */
window.api.getProcessedImageUrl = function(imageId) {
    return `${window.api.API_BASE_URL}/${imageId}/result`;
};