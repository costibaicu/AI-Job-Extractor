# 🚀 Job-Extractor.io | AI-Powered Job Dashboard

A full-stack web application designed to automatically extract job details from URLs using Jsoup, analyze candidate-to-job compatibility, calculate match scores, and generate professional `.docx` cover letters utilizing a local LLM via **Ollama**.

---

## 🛠️ Tech Stack
- **Backend:** Java, Spring Boot, Spring Data JPA, PostgreSQL, Jsoup, Apache PDFBox, Apache POI.
- **Frontend:** HTML5, Tailwind CSS, Vanilla JavaScript.
- **AI Integration:** Ollama (Local LLM - Qwen 2.5).

---

## ⚙️ How to Run Locally

### 1. Database Setup
Make sure you have PostgreSQL installed and running. Create a database named `job_db`:
```sql
CREATE DATABASE job_db;
```

Update your database credentials in `src/main/resources/application.properties` if necessary:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/job_db
spring.datasource.username=your_username
spring.datasource.password=your_password
```

### 2. AI Model Setup (Ollama)
Since the model weights file (`.gguf`) is too large for GitHub, you need to set it up locally:
1. Install and run [Ollama](https://ollama.com/).
2. Download your desired model file (e.g., `Qwen2.5-7B-Instruct.Q4_K_M.gguf`) from Hugging Face or use an existing Ollama tag.
3. Create the custom model using the provided configuration file in this repository:
   ```bash
   ollama create job-extractor -f ./Modelfile
   ```

### 3. Run Backend & Frontend
- **Backend:** Run the Spring Boot application from your favorite IDE or via terminal using Maven:
  ```bash
  mvn spring-boot:run
  ```
- **Frontend:** Open the `index.html` file in your browser or serve it via a local live server (like Live Server in VS Code).