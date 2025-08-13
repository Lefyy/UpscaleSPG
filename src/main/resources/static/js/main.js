// js/main.js
let pollingInterval;
let currentDots = 0;

document.addEventListener('DOMContentLoaded', () => {
    if (window.ui && window.ui.initDOMElements) {
        window.ui.initDOMElements();
    } else {
        console.error("Window.ui или window.ui.initDOMElements не определены. Проверьте подключение js/ui.js.");
        return;
    }

    if (window.ui.DOMElements.uploadForm) {
        window.ui.DOMElements.uploadForm.addEventListener('submit', handleFormSubmit);
    } else {
        console.error("Элемент 'uploadForm' не найден после инициализации.");
    }

    if (window.ui.DOMElements.fileInput) {
        window.ui.DOMElements.fileInput.addEventListener('change', window.ui.handleFileInputChange);
    }

    if (window.ui.DOMElements.modelSelect) {
        window.ui.DOMElements.modelSelect.addEventListener('change', window.ui.syncModelAndScaleSelection);
    }

    if (window.ui.DOMElements.scaleInput) {
        window.ui.DOMElements.scaleInput.addEventListener('change', window.ui.syncModelAndScaleSelection);
    }

    window.ui.hideProcessingCard();
    window.ui.syncModelAndScaleSelection();
});

async function handleFormSubmit(event) {
    event.preventDefault();

    if (!window.ui || !window.api || !window.utils) {
        console.error("UI, API или Utils модули не загружены. Отмена обработки формы.");
        return;
    }

    const formData = new FormData(window.ui.DOMElements.uploadForm);
    const originalFile = window.ui.DOMElements.fileInput.files[0];
    const originalFileName = originalFile ? originalFile.name : 'unknown_file';
    const originalFileSize = originalFile ? originalFile.size : 0;
    const selectedModel = window.ui.DOMElements.modelSelect.value;
    const selectedScale = window.ui.DOMElements.scaleInput.value;

    window.ui.toggleFormState(false);
    window.ui.hideImageInfo();
    window.ui.showProcessingCard('Изображение загружается...');

    if (pollingInterval) {
        clearInterval(pollingInterval);
    }
    currentDots = 0;

    try {
        const imageId = await window.api.uploadImage(formData);

        window.ui.updateStatusMessage(`Изображение загружено. ID: ${imageId}. Инициализация обработки...`, 'blue');

        pollingInterval = setInterval(() => checkImageStatusAndDisplay(imageId, {
            originalFileName,
            originalFileSize,
            model: selectedModel,
            scale: selectedScale
        }), 1000);
        checkImageStatusAndDisplay(imageId, {
            originalFileName,
            originalFileSize,
            model: selectedModel,
            scale: selectedScale
        });

    } catch (error) {
        console.error('Ошибка загрузки:', error);
        window.ui.showErrorMessage(`Ошибка загрузки: ${error.message}`);
        window.ui.toggleFormState(true);
    }
}

async function checkImageStatusAndDisplay(imageId, initialInfo) {
    try {
        // ИЗМЕНЕНИЕ: теперь getImageStatus возвращает объект
        const responseData = await window.api.getImageStatus(imageId);
        const status = responseData.status; // Получаем статус из объекта

        console.log(`Статус изображения ${imageId}: ${status}`);

        const baseMessage = `Текущий статус: ${status.toUpperCase()}`;

        if (status === 'processed') {
            clearInterval(pollingInterval);
            currentDots = 0;
            window.ui.updateStatusMessage(`${baseMessage}. Обработка завершена!`, 'green', false);

            const processedImageUrl = window.api.getProcessedImageUrl(imageId);

            // ИЗМЕНЕНИЕ: используем данные, полученные от сервера
            const finalInfo = {
                originalResolution: responseData.originalResolution,
                upscaledResolution: responseData.upscaledResolution,
                originalFileSize: responseData.originalFileSize,
                upscaledFileSize: responseData.upscaledFileSize,
                model: responseData.model,
                scale: responseData.scale,
                originalFileName: responseData.originalFileName
            };

            window.ui.displayProcessedImageResult(processedImageUrl, finalInfo); // Передаем актуальные данные
            window.ui.toggleFormState(true);
            window.ui.DOMElements.fileNameSpan.textContent = 'Выберите файл...';
        } else if (status === 'error') {
            clearInterval(pollingInterval);
            currentDots = 0;
            window.ui.showErrorMessage(`${baseMessage}. Ошибка обработки! Пожалуйста, проверьте логи сервера.`);
            window.ui.toggleFormState(true);
        } else if (status === 'uploaded' || status === 'processing') {
            currentDots = (currentDots % 3) + 1;
            const dotsString = '.'.repeat(currentDots);
            window.ui.updateStatusMessage(`${baseMessage}${dotsString}`, 'blue', true);
        }
    } catch (error) {
        clearInterval(pollingInterval);
        currentDots = 0;
        window.ui.showErrorMessage(`Ошибка проверки статуса: ${error.message}`);
        window.ui.toggleFormState(true);
    }
}