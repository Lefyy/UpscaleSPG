window.api = window.api || {};

window.api.API_BASE_URL = 'http://localhost:8080/api/v1/images';

window.api.uploadImage = async function(formData) {
    const response = await fetch(window.api.API_BASE_URL, {
        method: 'POST',
        body: formData
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP error! Status: ${response.status}, Body: ${errorText}`);
    }
    const data = await response.json();
    return data.imageId;
};

window.api.getImageStatus = async function(imageId) {
    const response = await fetch(`${window.api.API_BASE_URL}/${imageId}/status`);
    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Status check failed! Status: ${response.status}, Body: ${errorText}`);
    }
    return response.json(); // ИЗМЕНЕНИЕ: Парсим ответ как JSON
};

window.api.getProcessedImageUrl = function(imageId) {
    return `${window.api.API_BASE_URL}/${imageId}/result`;
};