// js/ui.js

// Создаем глобальный объект 'ui', если его еще нет, или используем существующий
window.ui = window.ui || {};

// DOMElements теперь будут инициализированы функцией initDOMElements
window.ui.DOMElements = {};

window.ui.initDOMElements = function() {
    window.ui.DOMElements = {
        uploadForm: document.getElementById('uploadForm'),
        fileInput: document.getElementById('file-upload'),
        fileNameSpan: document.getElementById('file-name'),
        modelSelect: document.getElementById('model-select'),
        scaleInput: document.getElementById('scale-input'),
        submitButton: document.querySelector('.btn-primary'),
        processingCard: document.getElementById('processing-card'),
        statusMessageText: document.getElementById('status-message-text'),
        spinner: document.querySelector('.spinner'),
        imageResultsContainer: document.getElementById('image-results-container'),
        processedImage: document.getElementById('processed-image'),
        originalResolutionSpan: document.getElementById('original-resolution'),
        upscaledResolutionSpan: document.getElementById('upscaled-resolution'),
        originalFileSizeSpan: document.getElementById('original-file-size'),
        upscaledFileSizeSpan: document.getElementById('upscaled-file-size'),
        processedModelSpan: document.getElementById('processed-model'),
        processedScaleSpan: document.getElementById('processed-scale'),
        downloadLink: document.getElementById('download-link')
    };

    // Добавим проверку, что все ключевые элементы найдены
    for (const key in window.ui.DOMElements) {
        if (window.ui.DOMElements[key] === null) {
            console.warn(`UI Warning: Element with ID/class "${key}" not found. Check HTML and js/ui.js.`);
        }
    }
};

// ======================= Управление отображением элементов =======================

/**
 * Скрывает карточку обработки и результатов.
 */
window.ui.hideProcessingCard = function() {
    window.ui.DOMElements.processingCard.classList.add('hidden');
    window.ui.DOMElements.imageResultsContainer.classList.add('hidden');
    window.ui.DOMElements.spinner.classList.add('hidden');
    window.ui.DOMElements.statusMessageText.textContent = '';
    window.ui.DOMElements.statusMessageText.classList.remove('hidden'); // Убедимся, что текстовый статус не скрыт
};

/**
 * Показывает карточку обработки и устанавливает начальное сообщение.
 */
window.ui.showProcessingCard = function(message) {
    window.ui.DOMElements.processingCard.classList.remove('hidden');
    window.ui.DOMElements.imageResultsContainer.classList.add('hidden');
    window.ui.DOMElements.spinner.classList.remove('hidden');
    window.ui.DOMElements.statusMessageText.textContent = message;
    window.ui.DOMElements.statusMessageText.style.color = 'black';
    window.ui.DOMElements.statusMessageText.classList.remove('hidden'); // Показываем текстовый статус
};

/**
 * Обновляет сообщение о статусе обработки.
 * @param {string} message - Текст сообщения.
 * @param {string} color - Цвет текста (например, 'blue', 'green', 'red').
 * @param {boolean} showSpinner - Показывать ли спиннер.
 */
window.ui.updateStatusMessage = function(message, color, showSpinner = true) {
    window.ui.DOMElements.statusMessageText.textContent = message;
    window.ui.DOMElements.statusMessageText.style.color = color;
    if (showSpinner) {
        window.ui.DOMElements.spinner.classList.remove('hidden');
    } else {
        window.ui.DOMElements.spinner.classList.add('hidden');
    }
    window.ui.DOMElements.statusMessageText.classList.remove('hidden'); // Убедимся, что текстовый статус виден
};

/**
 * Отображает результат обработки изображения.
 * @param {string} imageUrl - URL обработанного изображения.
 * @param {Object} info - Объект с информацией об изображении (разрешение, размер, модель, кратность).
 */
window.ui.displayProcessedImageResult = function(imageUrl, info) {
    window.ui.DOMElements.statusMessageText.classList.add('hidden'); // Скрываем текстовый статус
    window.ui.DOMElements.spinner.classList.add('hidden'); // Скрываем спиннер

    window.ui.DOMElements.imageResultsContainer.classList.remove('hidden'); // Показываем контейнер результатов
    window.ui.DOMElements.processedImage.src = imageUrl;
    window.ui.DOMElements.downloadLink.href = imageUrl;

    window.ui.DOMElements.originalResolutionSpan.textContent = info.originalResolution || 'Неизвестно';
    window.ui.DOMElements.upscaledResolutionSpan.textContent = info.upscaledResolution || 'Неизвестно';
    window.ui.DOMElements.originalFileSizeSpan.textContent = info.originalFileSize ? window.utils.formatBytes(info.originalFileSize) : 'Неизвестно';
    window.ui.DOMElements.upscaledFileSizeSpan.textContent = info.upscaledFileSize ? window.utils.formatBytes(info.upscaledFileSize) : 'Неизвестно';
    window.ui.DOMElements.processedModelSpan.textContent = info.model || 'Неизвестно';
    window.ui.DOMElements.processedScaleSpan.textContent = info.scale || 'Неизвестно';

    let downloadFileName = info.originalFileName || 'upscaled_image';
    const fileExtension = downloadFileName.split('.').pop();
    downloadFileName = downloadFileName.replace(`.${fileExtension}`, '') + '_upscaled.' + fileExtension;
    window.ui.DOMElements.downloadLink.setAttribute('download', downloadFileName);
};

/**
 * Показывает сообщение об ошибке.
 * @param {string} message - Текст ошибки.
 */
window.ui.showErrorMessage = function(message) {
    window.ui.updateStatusMessage(`Ошибка: ${message}`, 'red', false);
    window.ui.hideImageInfo();
};

// ======================= Управление состоянием формы =======================

/**
 * Включает или отключает элементы формы и кнопку отправки.
 * @param {boolean} enable - Если true, включает; если false, отключает.
 */
window.ui.toggleFormState = function(enable) {
    window.ui.DOMElements.fileInput.disabled = !enable;
    window.ui.DOMElements.modelSelect.disabled = !enable;
    window.ui.DOMElements.scaleInput.disabled = !enable;
    window.ui.DOMElements.submitButton.disabled = !enable;
};

/**
 * Обрабатывает изменение выбора файла, обновляя текст метки.
 */
window.ui.handleFileInputChange = function() {
    const file = window.ui.DOMElements.fileInput.files[0];
    if (file) {
        window.ui.DOMElements.fileNameSpan.textContent = file.name;
    } else {
        window.ui.DOMElements.fileNameSpan.textContent = 'Выберите файл...';
    }
};

/**
 * Управляет состоянием кратности (scale) в зависимости от выбранной модели.
 * Srgan поддерживает только x2 и x4.
 */
window.ui.syncModelAndScaleSelection = function() {
    const selectedModel = window.ui.DOMElements.modelSelect.value;
    const selectedScale = parseInt(window.ui.DOMElements.scaleInput.value);

    if (selectedModel === 'srgan') {
        window.ui.DOMElements.scaleInput.min = 2;
        window.ui.DOMElements.scaleInput.max = 4;
        if (selectedScale === 3) {
            window.ui.DOMElements.scaleInput.value = '2'; // Сбрасываем на 2, если было 3
        }
    } else {
        window.ui.DOMElements.scaleInput.min = 2;
        window.ui.DOMElements.scaleInput.max = 4;
    }
};

/**
 * Скрывает информацию об изображении и очищает её.
 */
window.ui.hideImageInfo = function() {
    window.ui.DOMElements.imageResultsContainer.classList.add('hidden');
    window.ui.DOMElements.processedImage.src = '';
    window.ui.DOMElements.originalResolutionSpan.textContent = '';
    window.ui.DOMElements.upscaledResolutionSpan.textContent = '';
    window.ui.DOMElements.originalFileSizeSpan.textContent = '';
    window.ui.DOMElements.upscaledFileSizeSpan.textContent = '';
    window.ui.DOMElements.processedModelSpan.textContent = '';
    window.ui.DOMElements.processedScaleSpan.textContent = '';
    window.ui.DOMElements.downloadLink.href = '#';
    window.ui.DOMElements.downloadLink.removeAttribute('download');
    window.ui.DOMElements.statusMessageText.classList.remove('hidden');
};