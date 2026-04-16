# Gamevaultr

![Logo](docs/media/0gvlogo.png)

Gamevaultr is a personal game vault: build your game collection, then track playthroughs, sessions, ratings, and notes in one place.

## Live Website - Fully useable

[gamevaultr.com](https://gamevaultr.com)

### Use the credentials below to explore the application
Username: demo<br>
Password: demopassword

## What You Can Do

- Search add games to your collection
- Track ownership, play status, and progress for each game
- Manage playthroughs history and current run context
- Log individual play sessions with notes and reflections
- Record ratings, reviews, notes and personal insights
- Track playtime with a built-in session timer

## Product Preview
### Home - your collection at a glance

![Home Screen](docs/media/Home.png)

### Search and Add - build your library quickly 

![Search and Add](docs/media/search-add.gif)

## Collection - organize and manage your games

![Collection organization](docs/media/collection.gif)

## Game tracking — pick up exactly where you left off
![Game page](docs/media/game.png)



## Session logging - record each play session
![Log play sessions](docs/media/log.png)

![Log play history](docs/media/log-history.png)

## Session Timer — track your playtime in real time
![Timer](docs/media/timer.png)

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

