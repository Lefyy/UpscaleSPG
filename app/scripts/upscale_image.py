import sys
import shutil
import os

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

    # --- Здесь будет основная логика обработки изображения ---
    # Пока просто скопируем входной файл в выходной, чтобы проверить пути
    try:
        # Убедимся, что директория для выходного файла существует
        output_dir = os.path.dirname(output_path)
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)

        shutil.copy(input_path, output_path)
        print(f"Successfully simulated processing. Copied '{input_path}' to '{output_path}'")

        # В реальном скрипте здесь был бы код для загрузки input_path,
        # применения модели/интерполяции с учетом method и scale_factor,
        # и сохранения результата в output_path.

        # Если все прошло успешно, выводим путь к результату (опционально, но полезно)
        # и завершаем с кодом 0
        print(f"RESULT_PATH:{output_path}") # Пример вывода пути к результату для парсинга в Java
        sys.exit(0) # Завершаем скрипт с кодом успеха

    except FileNotFoundError:
        print(f"Error: Input file not found at '{input_path}'", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"An error occurred during processing simulation: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()