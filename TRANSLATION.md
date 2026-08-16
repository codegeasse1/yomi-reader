# Translating Yomi

Thank you for your interest in translating Yomi! This guide will help you understand where to find translation files, how to use AI models to help translate them accurately, and how to submit your translations via a Pull Request (PR).

## 📂 Translation Files & Structure

Yomi's translation resources are stored in the `:i18n` module.
The base language of the app is English, and all other languages inherit from it.

- **Base English Resources:**
  - Strings: `i18n/src/commonMain/moko-resources/base/strings.xml`
  - Plurals: `i18n/src/commonMain/moko-resources/base/plurals.xml`

- **Target Language Resources:**
  - Translations are located in subdirectories named after their respective language codes (ISO 639-1) under `i18n/src/commonMain/moko-resources/`.
  - For example, Russian translations are in `ru/strings.xml` and `ru/plurals.xml`, and Ukrainian in `uk/strings.xml` and `uk/plurals.xml`.

---

## ✍️ How to Translate

### Step 1: Locate or Create Your Language Folder
Go to `i18n/src/commonMain/moko-resources/` and look for the folder matching your target language code (e.g., `de` for German, `fr` for French, `es` for Spanish).
If it doesn't exist, create it.

### Step 2: Copy the Base Strings
Copy the strings you want to translate from the base `strings.xml` or `plurals.xml` into your target language's files.
An XML entry looks like this:
```xml
<resources>
    <string name="action_settings">Settings</string>
    ...
</resources>
```

### Step 3: Translate the Content
Replace the English text inside the tag with the translation for your language:
```xml
<resources>
    <string name="action_settings">Настройки</string>
    ...
</resources>
```

> [!IMPORTANT]
> **Preserve XML Placeholders & Formatting tags:**
> If a string contains placeholders like `%s`, `%d`, `%1$s`, or HTML tags like `<b>` or `<i>`, they must be preserved exactly as-is in your translation.
> Example:
> - English: `<string name="update_check_available">Update available: %1$s</string>`
> - Translation: `<string name="update_check_available">Доступно обновление: %1$s</string>`

---

## 🤖 Using AI for Translations

AI models (like Claude, ChatGPT, Gemini, or DeepL) are incredibly helpful for bulk translation. Here is how to use them effectively:

### 💡 Tips for Best Results
1. **Provide Context:** Tell the AI what the application is (Yomi, a community fork of Aniyomi, which is an Anime, Manga, and Novel reader for Android). This helps it choose the right vocabulary (e.g., translating "chapters" or "sources" correctly in context).
2. **Explicit Instructions:** Instruct the AI to preserve XML tags and string placeholders.
3. **Review Output:** Always review the AI's output to ensure that slang or technical terms are translated appropriately and that placeholders weren't broken.

### 📝 Example Prompt to Use with AI
Copy and paste this prompt when asking an AI for translations:

```text
You are an expert translator. Translate the following English Android XML strings into [Target Language] (e.g., Russian).

Context:
- The app is "Yomi", a polished community fork of Aniyomi.
- It is an Android app for reading/viewing Anime, Manga, and Light Novels (Ranobe).
- Use a friendly, natural, and modern tone suitable for a reading application.

Instructions:
1. Translate only the text content between the <string>...</string> or <item>...</item> tags.
2. DO NOT modify the string/item "name" attributes or any XML attributes.
3. Keep all placeholders (e.g., %s, %d, %1$s, %2$d, etc.) exactly as they are. Place them naturally within the translated sentence structure.
4. Keep HTML tags (e.g., <b>, <i>, <br/>) intact.
5. Output only the final XML.

Here are the strings to translate:
[Paste your XML entries here]
```

---

## 🚀 Creating a Pull Request (PR)

Once you have completed your translations:

1. **Fork and Clone:** Fork the repository on GitHub and clone it locally.
2. **Create a Branch:** Create a new branch for your changes:
   ```bash
   git checkout -b translation/your-lang-code
   ```
3. **Apply Your Changes:** Save the updated XML files in the correct subdirectory under `i18n/src/commonMain/moko-resources/`.
4. **Format & Verify Code:**
   Run the spotless check to ensure your XML files conform to the project's formatting standard:
   ```bash
   ./gradlew spotlessApply
   ```
5. **Commit and Push:** Commit your changes using a clear message:
   ```bash
   git add .
   git commit -m "[i18n] Update translations for [Language Name]"
   git push origin translation/your-lang-code
   ```
6. **Open a PR:** Go to the GitHub repository and open a Pull Request to merge your translation branch into the main repository.

Thank you for making Yomi accessible to more readers around the world!
