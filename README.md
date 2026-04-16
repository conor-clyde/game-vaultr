# Gamevaultr

![Logo](docs/media/0gvlogo.png)

Gamevaultr is a personal game vault: build your game collection, then track playthroughs, sessions, ratings, and notes in one place.

## Live App

[gamevaultr.com](https://gamevaultr.com)

### Use the credentials below to explore the application
Username: demo<br>
Password: demopassword

## What You Can Do

- Search IGDB and add games to your collection
- Track status, ownership, and progress per game
- Manage playthroughs with history and current run context
- Log individual play sessions with notes
- Save ratings, reviews, and reflection tags

## Product Preview

### Search and Add Flow

![Search and Add](docs/media/search-add.gif)

## Collection Organization

![Collection organization](docs/media/collection.gif)

### Home Screen

![Home Screen](docs/media/Home.png)

## Tech Stack

- Backend: Spring Boot 4 (MVC)
- Language: Java 21
- Build: Maven (`mvn` / `./mvnw`)
- Data layer: Spring Data JPA + Hibernate
- Database: PostgreSQL (default), H2 (local profile)
- Security: Spring Security
- Views: Thymeleaf + static assets
- External API: IGDB (Twitch credentials)
- Caching: Caffeine (service-level cache)

