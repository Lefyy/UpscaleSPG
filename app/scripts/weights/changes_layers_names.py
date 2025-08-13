import torch

# Замените на путь к вашему .pth файлу
# Если вы распаковали, используйте extracted_pth_file
# Если это был изначально .pth, используйте pth_tar_path
weights_path = 'SRGAN_4x.pth (1).tar'

# Загружаем state_dict
# map_location='cpu' полезно, если веса были сохранены на GPU,
# а вы загружаете их на CPU, чтобы избежать ошибок с устройством.
state_dict = torch.load(weights_path, map_location='cpu')

if 'model_state_dict' in state_dict:
    state_dict = state_dict['model_state_dict']
elif 'state_dict' in state_dict: # Часто используется в других фреймворках, например, mmcv
    state_dict = state_dict['state_dict']

print("Оригинальные ключи (названия слоев) в state_dict:")
for key in state_dict.keys():
    print(key)

new_state_dict = {}
for key, value in state_dict.items():
    new_key = key

    # Пример 1: Замена префикса
    if new_key.startswith('_orig_mod.'):
        new_key = new_key.replace('_orig_mod.', '')

    new_state_dict[new_key] = value

print("\nНовые ключи (названия слоев) в state_dict:")
for key in new_state_dict.keys():
    print(key)

output_path = 'SRGAN_4x.pth'
torch.save(new_state_dict, output_path)
print(f"\nПереименованные веса сохранены в '{output_path}'.")