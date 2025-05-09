import sys
import shutil
import os
from PIL import Image

# Ожидаем 4 аргумента:
# 1: Путь к входному файлу (оригиналу)
# 2: Путь куда сохранить выходной файл (увеличенный)
# 3: Метод обработки (interpolation или edsr и т.д.)
# 4: Кратность увеличения (целое число)

# sys.argv - это список аргументов командной строки.
# sys.argv[0] - это имя самого скрипта (upscale_image.py)
# sys.argv[1] - первый аргумент
# sys.argv[2] - второй аргумент и т.д.

def main():
    # Проверяем, что получено правильное количество аргументов (имя скрипта + 4 аргумента = 5 элементов в sys.argv)
    if len(sys.argv) != 5:
        print("Usage: python upscale_image.py <input_path> <output_path> <method> <scale>", file=sys.stderr)
        sys.exit(1) # Завершаем скрипт с кодом ошибки

    input_path = sys.argv[1]
    output_path = sys.argv[2]
    processing_method = sys.argv[3]
    scale_factor_str = sys.argv[4]

    try:
        scale_factor = int(scale_factor_str)
    except ValueError:
        print(f"Error: Scale factor must be an integer, but got '{scale_factor_str}'", file=sys.stderr)
        sys.exit(1) # Завершаем с кодом ошибки, если кратность не число

    print(f"Python script received:")
    print(f"  Input Path: {input_path}")
    print(f"  Output Path: {output_path}")
    print(f"  Method: {processing_method}")
    print(f"  Scale Factor: {scale_factor}")

    try:
        print(f"Opening image: {input_path}")
        with Image.open(input_path) as img:

            original_width, original_height = img.size
            new_width = original_width * scale_factor
            new_height = original_height * scale_factor

            print(f"Original size: {original_width}x{original_height}, New size: {new_width}x{new_height}")

            print(f"Resizing image using Pillow resize...")

            resample_filter = Image.BICUBIC # Старая константа, работает в более старых версиях
            if hasattr(Image, 'Resampling'): # Проверка для Pillow 9+ и выше
                 resample_filter = Image.Resampling.BICUBIC


            img_resized = img.resize((new_width, new_height), resample=resample_filter)
            print("Resizing complete.")

            output_dir = os.path.dirname(output_path)
            if not os.path.exists(output_dir):
                os.makedirs(output_dir)
                print(f"Created output directory: {output_dir}")

            print(f"Saving processed image to: {output_path}")
            img_resized.save(output_path)
            print("Saving complete.")

        print(f"Processing successful.")
        print(f"RESULT_PATH:{output_path}")
        sys.exit(0)

    except FileNotFoundError:
        print(f"Error: Input file not found at '{input_path}'", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"An error occurred during processing simulation: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()