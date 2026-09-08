# Suwa Sarana (සුව සරණ) - Blood Donation & Matching Platform

![Suwa Sarana Banner](https://via.placeholder.com/1200x300?text=Suwa+Sarana)

Suwa Sarana is a comprehensive, modern, trilingual (English, Sinhala, Tamil) blood donation and matching platform. The system bridges the gap between blood donors and recipients in real-time, utilizing geo-spatial matching, real-time notifications, and advanced eligibility deferral engines to save lives efficiently.

## 🚀 Key Features

* **Trilingual Support (i18n):** Full support for English, Sinhala, and Tamil out-of-the-box.
* **Smart Matching Engine:** Geo-spatial queries (PostGIS/Earthdistance) to find the nearest eligible donors for an urgent blood request.
* **Eligibility & Deferral Engine:** Rule-based engine to determine donor eligibility based on recent donations, medical history, and weight restrictions.
* **Real-time Notifications:** Server-Sent Events (SSE) provide instant alerts to donors when a matching blood request is raised in their vicinity.
* **Escalation Mechanism:** Automatic escalation of unmatched requests to a wider radius via cron-based background jobs.
* **Responsive Dashboard:** A modern, clean, responsive UI built with Next.js, TailwindCSS, and Radix UI primitives.

## 🛠️ Technology Stack

### Frontend
* **Framework:** Next.js 14 (App Router)
* **Styling:** Tailwind CSS
* **Components:** Radix UI, Lucide Icons
* **State Management:** React Context (for I18n)
* **Package Manager:** pnpm

### Backend
* **Framework:** Spring Boot 3.2 (Java 21)
* **Database:** PostgreSQL (with `cube` and `earthdistance` extensions)
* **ORM:** Hibernate / Spring Data JPA
* **Migrations:** Flyway
* **Security:** Spring Security (JWT-based, currently mocked for development ease)

### Infrastructure
* **Containerization:** Docker & Docker Compose
* **Build Tools:** Maven & pnpm

## 📦 Project Structure

This project uses a monorepo structure:
- `/apps/frontend`: The Next.js web application.
- `/apps/backend`: The Spring Boot API server.

## ⚙️ Setup Instructions

### Prerequisites
- Node.js (v18+)
- pnpm (v8+)
- Java 21 JDK
- Docker & Docker Compose

### 1. Start the Database
You can spin up the required PostgreSQL database using the provided docker-compose file:
```bash
docker-compose up -d postgres
```
*(The `cube` and `earthdistance` extensions are automatically enabled via Flyway migrations)*

### 2. Backend Setup
Navigate to the backend directory and run the Spring Boot application:
```bash
cd apps/backend
.\run.cmd # On Windows
# Or using Maven:
# ./mvnw spring-boot:run
```
The API will run on `http://localhost:8080`.

### 3. Frontend Setup
Navigate to the root directory or frontend directory to install dependencies and start the dev server:
```bash
pnpm install
pnpm --filter frontend dev
```
The application will be accessible at `http://localhost:3000`.

## 📖 Usage

1. **Register/Login:** Create a new donor profile or login.
2. **Dashboard:** View your donation statistics, upcoming appointments, and notifications.
3. **Blood Requests:** Hospitals or individuals can create a blood request. The matching engine will instantly notify nearby eligible donors.
4. **Notifications:** Donors receive real-time SSE alerts. They can accept or decline a request directly from their feed.
5. **Language Settings:** Toggle between English, Sinhala, and Tamil from the top right corner.

## 👨‍💻 Development Journey

This project focusing on building a highly scalable and robust architecture from scratch, including complex domain logic like geo-matching and real-time streaming.

## 📝 License

This project is licensed under the MIT License.
