const uploadForm = document.getElementById('uploadForm');
const statusMessage = document.getElementById('statusMessage');
const imageIdSpan = document.getElementById('imageId');
const imageResultsDiv = document.getElementById('imageResults');

let pollingInterval;
let currentDots = 0;

uploadForm.addEventListener('submit', function(event) {
    event.preventDefault();

    statusMessage.textContent = 'Uploading...';
    statusMessage.style.color = 'black';
    imageIdSpan.textContent = '';

    if (pollingInterval) {
        clearInterval(pollingInterval);
    }
    currentDots = 0;

    const formData = new FormData(uploadForm);

    fetch('http://localhost:8080/api/v1/images', {
        method: 'POST',
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(errorText => {
                 throw new Error(`HTTP error! status: ${response.status}, body: ${errorText}`);
            });
        }
        return response.text();
    })
    .then(imageId => {
        imageIdSpan.textContent = imageId; // Отображаем ID изображения
        console.log('Загружено. ID изображения:', imageId);

        // Информируем пользователя, что загрузка успешна и начинается обработка
        statusMessage.textContent = `Изображение загружено. ID: ${imageId}. Инициализация обработки...`;
        statusMessage.style.color = 'blue';

        // Начинаем опрос статуса
        pollingInterval = setInterval(() => checkImageStatus(imageId), 1000); // Опрос каждую 1 секунду
        // Вызываем проверку статуса немедленно, не дожидаясь первого интервала
        checkImageStatus(imageId);
    })
    .catch(error => {
        statusMessage.textContent = `Error: ${error.message}`;
        statusMessage.style.color = 'red';
        imageIdSpan.textContent = 'N/A';
        console.error('Upload error:', error);
    });
});

function checkImageStatus(imageId) {
    fetch(`/api/v1/images/${imageId}/status`) // Запрос к новому эндпоинту статуса
        .then(response => {
            if (!response.ok) {
                return response.text().then(errorText => {
                    throw new Error(`Status check failed! status: ${response.status}, body: ${errorText}`);
                });
            }
            return response.text();
        })
        .then(status => {
            console.log(`Статус изображения ${imageId}: ${status}`);

            // Определяем базовое сообщение без точек
            const baseMessage = `ID изображения: ${imageId}. Текущий статус: ${status}`;

            if (status === 'processed') {
                clearInterval(pollingInterval);
                currentDots = 0; // Сбрасываем
                statusMessage.textContent = `${baseMessage}. Обработка завершена!`; // Добавляем финальный текст
                statusMessage.style.color = 'green';
                displayProcessedImage(imageId);

            } else if (status === 'error') {
                clearInterval(pollingInterval);
                currentDots = 0; // Сбрасываем
                statusMessage.textContent = `${baseMessage}. Ошибка обработки! Пожалуйста, проверьте логи сервера.`; // Добавляем финальный текст
                statusMessage.style.color = 'red';

            } else if (status === 'uploaded' || status === 'processing') {
                // Увеличиваем количество точек и циклически меняем от 0 до 3
                currentDots = (currentDots % 3) + 1;
                const dotsString = '.'.repeat(currentDots); // Создаем строку из нужного количества точек
                statusMessage.textContent = `${baseMessage}${dotsString}`; // Обновляем текст
            }
        })
        .catch(error => {
            clearInterval(pollingInterval);
            currentDots = 0; // Сбрасываем
            statusMessage.textContent = `Ошибка проверки статуса: ${error.message}`;
            statusMessage.style.color = 'red';
            console.error('Ошибка опроса:', error);
        });
}

function displayProcessedImage(imageId) {
    const processedImageUrl = `/api/v1/images/${imageId}/result`;

    const processedImageContainer = document.createElement('div');
    processedImageContainer.className = 'image-container'; // Для будущих стилей
    processedImageContainer.innerHTML = `
        <h3>Processed Image</h3>
        <img src="${processedImageUrl}" alt="Processed Image" style="max-width: 300px; height: auto; border: 1px solid #ccc;">
        <p><a href="${processedImageUrl}" download="upscaled_image_${imageId}.jpg">Скачать</a></p>
    `;
    imageResultsDiv.appendChild(processedImageContainer);
}