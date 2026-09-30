# Result Desk: VIT semester results

A responsive web app for preparing one semester's result for VIT students, across four subjects, where each subject is marked with **MSE (30%)** and **ESE (70%)**.

- **Teachers** enter MSE and ESE marks for the subject they teach (typing into a grid, or importing a CSV).
- **Students** see their marksheet with grades, SGPA and pass/fail, once results are published.
- **The admin** (exam cell) manages people and subjects, reviews every result, publishes or withdraws results, exports a CSV, and sees an activity log.

## Stack

| Layer    | Tech |
|----------|------|
| Frontend | React 18 + React Router, built with Vite |
| Backend  | Spring Boot 4.1 (Java 21): Spring Web MVC, Spring Data JPA / Hibernate, Spring Security with JWT, Bean Validation |
| Database | PostgreSQL |

## How a result is calculated

- MSE and ESE are each entered **out of 100**.
- **Subject total** = 0.30 × MSE + 0.70 × ESE (out of 100).
- A subject is **passed** with a total of at least 40 **and** at least 40 in the ESE.
- **Grades** (10-point scale): O ≥ 90 (10), A+ ≥ 80 (9), A ≥ 70 (8), B+ ≥ 60 (7), B ≥ 50 (6), C ≥ 45 (5), P ≥ 40 (4), F (0).
- **SGPA** = Σ(credits × grade points) ÷ Σ credits, shown once all four subjects have both marks.

These rules live in one place, [`GradingPolicy.java`](backend/src/main/java/edu/vit/results/service/GradingPolicy.java), and are mirrored in [`client/src/grading.js`](client/src/grading.js) for the live preview while typing.

## Setup

You need **Java 21+**, **Node 18+** and **PostgreSQL** running on `localhost:5432`. Maven is not needed; the project includes its own wrapper.

```bash
npm install
```

The app connects as `postgres` / `1234` by default. Override with the `DB_URL`, `DB_USER` and `DB_PASSWORD` environment variables.

## Run

```bash
npm run dev
```

That single command:

1. checks that Java 21+ is installed and PostgreSQL is reachable (with a clear message if not),
2. creates the `vit_results` database if it doesn't exist,
3. starts the Spring Boot API on **8080** (or the next free port if 8080 is taken),
4. starts the React dev server on **5173** (or the next free port), with hot reload,
5. prints the address and opens it in your browser.

Press **Ctrl+C** to stop everything. To stop it opening the browser, set `BROWSER=none`.

`npm start` does the same with a production build: one server, React bundled into Spring Boot.

Tables are created automatically, and an empty database is filled with a demo semester: 4 subjects, 4 teachers and 12 students. To wipe everything and reload the demo data:

```bash
npm run reset-db
```

## Demo logins

| Role    | Email                 | Password   |
|---------|-----------------------|------------|
| Admin   | admin@vit.edu         | admin123   |
| Teacher | iyer@vit.edu          | teacher123 |
| Student | aarav.mehta@vit.edu   | student123 |

The other teachers are kapoor@, fernandes@ and kulkarni@vit.edu. Every student is `firstname.lastname@vit.edu` with password `student123`.

## Tests

```bash
npm test
```

This runs JUnit tests for the grading rules, plus end-to-end API tests against the `vit_results_test` database, which is created automatically. The API tests cover login, role checks, mark validation, grade calculation, publishing, user management and subject editing.

## API overview

| Method & path | Who | Purpose |
|---|---|---|
| `POST /api/auth/login` | anyone | Returns a JWT |
| `GET /api/student/result` | student | Own marksheet (if published) |
| `GET /api/subjects` | teacher/admin | Own subjects (teacher) or all subjects (admin) |
| `GET /api/subjects/{id}/marks` | teacher/admin | Marks for every student in a subject |
| `PUT /api/subjects/{id}/marks` | subject teacher | Save MSE/ESE marks in bulk |
| `GET /api/admin/results` | admin | Every student's semester result |
| `PUT /api/admin/publication` | admin | Publish or withdraw results |
| `GET/POST/PUT/DELETE /api/admin/users` | admin | Manage accounts |
| `PUT /api/admin/subjects/{id}` | admin | Edit a subject or assign its teacher |
| `GET /api/admin/stats`, `/api/admin/audit` | admin | Dashboard numbers and the activity log |

Set `JWT_SECRET` (at least 32 characters) before deploying anywhere real.
