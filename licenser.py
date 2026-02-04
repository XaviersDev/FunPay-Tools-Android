import os

# Текст лицензии (защищенный режим)
# Используем оба ника для надежности
HEADER_KOTLIN = """/*
 * Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
 *
 * This code is proprietary and confidential.
 * Modification, distribution, or use of this source code
 * without express written permission from the author is strictly prohibited.
 *
 * Decompiling, reverse engineering, or creating derivative works
 * based on this software is a violation of copyright law.
 */

"""

HEADER_XML = """<!--
  ~ Copyright (c) 2026 XaviersDev (AlliSighs). All rights reserved.
  ~
  ~ This code is proprietary. Modification and redistribution are prohibited.
  -->
"""

# Расширения файлов, которые будем обрабатывать
EXTENSIONS = {
    ".kt": HEADER_KOTLIN,
    ".kts": HEADER_KOTLIN,
    ".java": HEADER_KOTLIN,
    ".xml": HEADER_XML
}

def add_header():
    for root, dirs, files in os.walk("."):
        # Игнорируем папки гита, gradle и сборки
        if any(x in root for x in [".git", "build", ".gradle", ".idea"]):
            continue

        for file in files:
            ext = os.path.splitext(file)[1]
            if ext in EXTENSIONS:
                path = os.path.join(root, file)
                
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()

                # Проверяем, нет ли уже заголовка, чтобы не дублировать
                if "Copyright (c)" in content:
                    print(f"Skipped (already exists): {file}")
                    continue

                # Если это XML, вставляем ПОСЛЕ первой строки (<?xml...>), иначе файл сломается
                if ext == ".xml" and content.startswith("<?xml"):
                    first_line_end = content.find("\n") + 1
                    new_content = content[:first_line_end] + EXTENSIONS[ext] + content[first_line_end:]
                else:
                    new_content = EXTENSIONS[ext] + content

                with open(path, "w", encoding="utf-8") as f:
                    f.write(new_content)
                print(f"Updated: {file}")

if __name__ == "__main__":
    add_header()
    print("Done! Now run: git add . && git commit -m 'Add copyright headers' && git push")