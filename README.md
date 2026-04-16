# Gamevaultr
A game tracking app to organize your collection, decide what to play, track playthroughs, and reflection on your gaming experience.

![Logo](docs/media/0gvlogo.png)

---


## 🔗 Live Website
https://gamevaultr.com

### Demo Access
You can explore the app using demo credentials:

- Email: demo@demo.com  
- Password: demo123

## Features

- Search the IGDB database and add games to your collection
- Manage your collection with play status, platform, filters & search
- Track playthroughs per game with progress details
- Log individual play sessions within playthroughs
- Write reviews, reflections, and notes per title

## Product Demo Media

### Key Feature Walkthroughs

#### Add games to your collection in a few clicks.
![Search and Add](docs/media/search-add.gif)

#### Collection Management

```md
![Collection Management](docs/media/collection-manage.gif)
```
Track ownership and status to keep your library organized.



#### Playthrough and Session Logging

```md
![Playthrough and Sessions](docs/media/play-log.gif)
```
Record progress and sessions so your play history stays meaningful over time.

####  Home Dashboard


Quickly log sessions, track your activity, and find your next game — all from a single, streamlined dashboard.
### 3) UI Screenshots (Static)

Use static images for polished states and comparisons.

```md
![Home Screen](docs/media/home.png)
![Collection Detail](docs/media/collection-detail.png)
```

### 4) Full Walkthrough Video (Optional)

Host a longer video on YouTube/Loom and link it:

```md
[Watch full walkthrough](https://your-video-link-here)
```

## Tech Stack

- Backend framework: Spring Boot 4
- Language: Java 21
- Build tool: Maven (`mvn` / `./mvnw`)
- Data layer: Spring Data JPA + Hibernate
- Database: PostgreSQL (default), H2 (local profile)
- Security: Spring Security
- View layer: Thymeleaf + static assets
- External API: IGDB (via Twitch credentials)
- Caching: Caffeine

## Getting Started / Installation

### Prerequisites

- Java 21 installed
- Maven installed (or use the included Maven wrapper: `./mvnw`)
- Git

### Clone the repo

```bash
git clone https://github.com/<your-username>/playstate.git
cd playstate
```

### Install dependencies

```bash
./mvnw clean install
```

### Environment variables

For local development with the `local` profile, defaults are provided in `application-local.properties`.

For non-local runs, set these environment variables:

- `DATABASE_URL`
- `DB_USERNAME` (if needed)
- `DB_PASSWORD` (if needed)
- `IGDB_CLIENT_ID` (or `TWITCH_CLIENT_ID`)
- `IGDB_CLIENT_SECRET` (or `TWITCH_CLIENT_SECRET`)
- `SECURITY_USER_NAME` (optional)
- `SECURITY_USER_PASSWORD` (optional)
- `DEMO_ENABLED` (optional)
- `DEMO_USERNAME` / `DEMO_PASSWORD` (optional)

Example shell setup:

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/playstate
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export IGDB_CLIENT_ID=your_client_id
export IGDB_CLIENT_SECRET=your_client_secret
```

### Run the app

Local profile (H2 + local defaults):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Default profile (expects env vars):

```bash
./mvnw spring-boot:run
```

App URL: [http://localhost:8080](http://localhost:8080)

## Project Structure

Quick overview of the main folders:

```text
playstate/
├─ src/
│  ├─ main/
│  │  ├─ java/com/cocoding/playstate/
│  │  │  ├─ controller/     # MVC controllers and request handling
│  │  │  ├─ service/        # business logic and integrations
│  │  │  ├─ security/       # authentication and security config
│  │  │  ├─ domain/         # entities, enums, and core models
│  │  │  └─ ...             # repositories, config, and app bootstrap
│  │  └─ resources/
│  │     ├─ templates/      # Thymeleaf templates
│  │     ├─ static/         # CSS/JS/images
│  │     └─ application*.properties
│  └─ test/                 # unit/integration tests
├─ pom.xml                  # Maven build and dependencies
└─ README.md
```

## License

This project is currently unlicensed.

If you plan to make it public, consider adding an MIT license by creating a `LICENSE` file and updating this section.
