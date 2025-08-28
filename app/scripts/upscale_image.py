import sys
import os
import torch
import cv2
import numpy as np


from ESPCN import ESPCN
from EDSR import EDSR
from SRGAN import *

def _load_model(model_name, scale, model_path, device):
    """
    Загружает и инициализирует указанную модель, а также загружает ее веса.
    """
    model = None
    if model_name.lower() == 'espcn':
        model = ESPCN(upscale_factor=scale).to(device)
    elif model_name.lower() == 'edsr':
        model = EDSR(upscale_factor=scale).to(device)
    elif model_name.lower() == 'srgan':
        model = SRResNet(upscale=scale).to(device)
    else:
        raise ValueError(f"Неизвестное имя модели: {model_name}.")

    checkpoint = torch.load(model_path, map_location=device)

    if isinstance(checkpoint, dict) and 'state_dict' in checkpoint:
        model.load_state_dict(checkpoint['state_dict'])
    elif isinstance(checkpoint, dict):
        try:
            model.load_state_dict(checkpoint)
        except Exception as e:
            raise Exception(f"Ошибка при загрузке модели: Не удалось загрузить веса из словаря: {e}")
    else:
        model.load_state_dict(checkpoint)

    model.eval()
    return model

def _process_image_interpolation(input_path, output_path, scale, interpolation_method):
    """
    Масштабирует изображение с использованием классических методов интерполяции OpenCV.
    """
    print(f"Выполняется масштабирование методом интерполяции...", file=sys.stderr)
    lr_image = cv2.imread(input_path)
    if lr_image is None:
        raise FileNotFoundError(f"Не удалось прочитать изображение: {input_path}")

    h, w, _ = lr_image.shape
    new_w, new_h = w * scale, h * scale

    sr_image = cv2.resize(lr_image, (new_w, new_h), interpolation=interpolation_method)

    cv2.imwrite(output_path, sr_image)
    method_name = "Bilinear" if interpolation_method == cv2.INTER_LINEAR else "Bicubic"
    print(f"Изображение успешно увеличено ({method_name}) и сохранено в {output_path}", file=sys.stderr)

def _process_image_espcn(model, input_path, output_path, device):
    """
    Обрабатывает изображение с использованием модели ESPCN.
    """
    lr_image = cv2.imread(input_path).astype(np.float32) / 255.0
    ycrcb_image = cv2.cvtColor(lr_image, cv2.COLOR_BGR2YCrCb)
    y_channel, cr_channel, cb_channel = cv2.split(ycrcb_image)

    input_tensor = torch.from_numpy(y_channel).to(device).view(1, 1, y_channel.shape[0], y_channel.shape[1])
    with torch.no_grad():
        sr_y_tensor = model(input_tensor)

    sr_y = sr_y_tensor.squeeze().cpu().numpy()
    sr_y = np.clip(sr_y, 0.0, 1.0)
    h_upscaled, w_upscaled = sr_y.shape
    sr_cr = cv2.resize(cr_channel, (w_upscaled, h_upscaled), interpolation=cv2.INTER_CUBIC)
    sr_cb = cv2.resize(cb_channel, (w_upscaled, h_upscaled), interpolation=cv2.INTER_CUBIC)
    sr_ycrcb = cv2.merge([sr_y, sr_cr, sr_cb])
    final_output_bgr = cv2.cvtColor(sr_ycrcb, cv2.COLOR_YCrCb2BGR)

    cv2.imwrite(output_path, final_output_bgr * 255.0)

def _process_image_edsr(model, input_path, output_path, device):
    """
    Обрабатывает изображение с использованием модели EDSR.
    """
    lr_image = cv2.imread(input_path).astype(np.float32)
    rgb_image = cv2.cvtColor(lr_image, cv2.COLOR_BGR2RGB)

    input_tensor = torch.from_numpy(rgb_image.transpose(2, 0, 1)).unsqueeze(0).to(device)
    with torch.no_grad():
        output_tensor = model(input_tensor)

    final_output_rgb = output_tensor.squeeze(0).permute(1, 2, 0).cpu().numpy()
    final_output_rgb = np.clip(final_output_rgb, 0.0, 255.0)
    final_output_bgr = cv2.cvtColor(final_output_rgb, cv2.COLOR_RGB2BGR)

    cv2.imwrite(output_path, final_output_bgr)
    print(f"Изображение успешно увеличено (EDSR) и сохранено в {output_path}", file=sys.stderr)

def _process_image_srgan(model, input_path, output_path, device):
    """
    Обрабатывает изображение с использованием модели SRGAN.
    """
    lr_image = cv2.imread(input_path).astype(np.float32) / 255.0
    rgb_image = cv2.cvtColor(lr_image, cv2.COLOR_BGR2RGB)

    input_tensor = torch.from_numpy(rgb_image.transpose(2, 0, 1)).unsqueeze(0).to(device)
    with torch.no_grad():
        output_tensor = model(input_tensor)

    final_output_rgb = output_tensor.squeeze(0).permute(1, 2, 0).cpu().numpy()
    final_output_bgr = cv2.cvtColor(final_output_rgb, cv2.COLOR_RGB2BGR)

    cv2.imwrite(output_path, final_output_bgr * 255.0)
    print(f"Изображение успешно увеличено (SRGAN) и сохранено в {output_path}", file=sys.stderr)


def upscale_image(input_path, output_path, model_path, model_name, scale):
    """
    Масштабирует изображение, выбирая метод в зависимости от имени.
    """
    try:
        device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        model_name_lower = model_name.lower()

        if model_name_lower == 'bilinear':
            _process_image_interpolation(input_path, output_path, scale, cv2.INTER_LINEAR)
        elif model_name_lower == 'bicubic':
            _process_image_interpolation(input_path, output_path, scale, cv2.INTER_CUBIC)
        elif model_name_lower in ['espcn', 'edsr', 'srgan']:
            print(f"Используется устройство: {device}", file=sys.stderr)
            model = _load_model(model_name_lower, scale, model_path, device)

            if model_name_lower == 'espcn':
                _process_image_espcn(model, input_path, output_path, device)
            elif model_name_lower == 'edsr':
                _process_image_edsr(model, input_path, output_path, device)
            elif model_name_lower == 'srgan':
                _process_image_srgan(model, input_path, output_path, device)
        else:
            raise ValueError(f"Неизвестное имя модели/метода: {model_name}. "
                             f"Поддерживаются: espcn, edsr, srgan, bilinear, bicubic.")

        return 0

    except FileNotFoundError:
        print(f"Ошибка: Файл не найден по пути {input_path} или {model_path}", file=sys.stderr)
        return 1
    except ValueError as e:
        print(f"Ошибка параметров: {e}", file=sys.stderr)
        return 1
    except Exception as e:
        print(f"Произошла ошибка во время увеличения изображения: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc(file=sys.stderr)
        return 1

if __name__ == "__main__":
    if len(sys.argv) != 6:
        print("Использование: python upscale_image.py <путь_входного_изображения> <путь_выходного_изображения> <путь_весов_модели> <имя_модели> <масштаб>", file=sys.stderr)
        sys.exit(1)

    input_image_path = sys.argv[1]
    output_image_path = sys.argv[2]
    model_weights_path = sys.argv[3]
    model_name = sys.argv[4]
    scale = int(sys.argv[5])

    exit_code = upscale_image(input_image_path, output_image_path, model_weights_path, model_name, scale)
    sys.exit(exit_code)