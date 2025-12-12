# Portfolio Generator Backend

Spring Boot backend with Ollama integration for AI-powered resume parsing.

## Prerequisites

- Java 17+
- Maven 3.6+
- Ollama running at http://localhost:11434 with llama3.2:3b model

## Setup Ollama

1. Install Ollama from https://ollama.ai
2. Pull the llama3.2:3b model:

```bash
ollama pull llama3.2:3b
```

3. Verify Ollama is running:

```bash
curl http://localhost:11434/api/generate
```

## Build & Run

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

Backend will start on http://localhost:8080

## API Endpoints

### Parse Resume

**POST** `/api/resume/parse`

**Parameters:**

- `file` (multipart): PDF/DOCX/TXT file
- OR `text` (string): Plain text resume

**Response:**

```json
{
  "portfolio": { ... },
  "analysis": { ... }
}
```

### Health Check

**GET** `/api/resume/health`

Returns: "Resume API is running"
