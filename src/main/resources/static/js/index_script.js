const uploadForm = document.getElementById('uploadForm');
const statusMessage = document.getElementById('statusMessage');
const imageIdSpan = document.getElementById('imageId');
const imageResults = document.getElementById('imageResults');

uploadForm.addEventListener('submit', function(event) {
    event.preventDefault();

    statusMessage.textContent = 'Uploading...';
    statusMessage.style.color = 'black';
    imageIdSpan.textContent = '';

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
        statusMessage.textContent = 'Upload successful! Processing started...';
        statusMessage.style.color = 'green';
        imageIdSpan.textContent = imageId;
        console.log('Uploaded Image ID:', imageId);

        const processedImageUrl = `/api/v1/images/${imageId}/result`;
                const processedImageContainer = document.createElement('div');
                processedImageContainer.className = 'image-container'; // Для стилей, если захочешь добавить
                processedImageContainer.innerHTML = `
                    <img src="${processedImageUrl}" alt="Processed Image" style="max-width: 300px; height: auto; border: 1px solid #ccc;">
                    <p><a href="${processedImageUrl}" download="upscaled_image_${imageId}.jpg">Скачать</a></p>
                    `;
                imageResults.appendChild(processedImageContainer);

    })
    .catch(error => {
        statusMessage.textContent = `Error: ${error.message}`;
        statusMessage.style.color = 'red';
        imageIdSpan.textContent = 'N/A';
        console.error('Upload error:', error);
    });
});