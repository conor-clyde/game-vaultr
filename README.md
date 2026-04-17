# Gamevaultr

![Gamevaultr logo](docs/media/0gvlogo.png)

**Gamevaultr** is a personal game vault: build your collection, then track playthroughs, sessions, ratings, and notes in one place.

## Live app

The site is fully usable at **[gamevaultr.com](https://gamevaultr.com)**.

Demo login:

```
Username: demo
Password: demopassword
```

## What you can do

- Search IGDB and add games to your collection
- Track ownership, play status, and per-game progress
- Manage playthrough history and your current run
- Log play sessions with notes and reflections
- Record ratings, reviews, and personal notes
- Measure playtime with a built-in session timer

---

## Product preview

### Home — your collection at a glance
<img src="docs/media/Home.png" width="700"/>

---
### Search and add — build your library quickly
<img src="docs/media/search-add.gif" width="700"/>

---
### Collection — organize and manage your games
<img src="docs/media/collection.gif" width="700"/>

---
### Game page — know exactly where you left off
<img src="docs/media/game.png" width="700"/>

---
### Intentions — define how you want to play
<img src="docs/media/intentions.png" width="550"/>

---
### Session logging — record each play session
<img src="docs/media/log-play.png" width="550"/>

#### Play history saved under playthroughs
<img src="docs/media/play-history.png" width="650"/>

---
### Session timer widget — track playtime in real time
<img src="docs/media/timer.png" width="400"/>

---
### Reflect and review
<img src="docs/media/reflect.png" width="700"/>

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Spring Boot 4 (MVC) |
| Language | Java 21 |
| Build | Maven (`mvn` / `./mvnw`) |
| Data | Spring Data JPA + Hibernate |
| Database | PostgreSQL (default), H2 (local profile) |
| Security | Spring Security |
| UI | Thymeleaf + static assets |
| External API | IGDB (Twitch credentials) |
| Caching | Caffeine (service-level cache) |
