# HackConcordia

A small web app for finding and adding places to study, with a search box, tag filters, and a form for adding new places. It is built with Java, Javalin, and SQLite, and the page is plain HTML, CSS, and JavaScript.

- Browse saved places in a list.
- Search by name or description.
- Filter by tags (Study, Library, Café, Cafeteria).
- Add a new place with a name, an optional description, and tags.

---

## Contents

1. [Requirements](#requirements)
2. [Quick start](#quick-start)
3. [Project layout](#project-layout)
4. [Running the app](#running-the-app)
5. [HTTP API](#http-api)
6. [Database](#database)
7. [Front end](#front-end)
8. [Configuration](#configuration)
9. [Testing](#testing)
10. [Deploying](#deploying)
11. [Troubleshooting](#troubleshooting)
12. [Known limitations](#known-limitations)
13. [Contributing](#contributing)

---

## Requirements

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 27 (used in development) | `pom.xml` also sets the compiler plugin to 15 — see [Deploying](#deploying). |
| Maven | Any recent version | Used to build, test, and package. |
| Browser | Any modern browser | Uses `<dialog>`, `fetch`, and `AbortController`. |

Dependencies (managed by Maven):

| Library | Version | Purpose |
|---------|---------|---------|
| Javalin | 6.7.0 | HTTP server, routing, static files, JSON |
| Jackson (`jackson-databind`) | 2.22.4 | JSON (pulled in with Javalin) |
| slf4j-simple | 2.0.16 | Logging |
| sqlite-jdbc | 3.46.1.0 | SQLite driver |
| JUnit Jupiter | 5.14.0 | Tests (test scope only) |

---

## Quick start

```bash
git clone git@github.com:0ctAdario834/HackConcordia.git
cd HackConcordia
mvn test                       # run the test suite
```

Then run `Main` from your IDE (see [Running the app](#running-the-app)).

Then open **http://localhost:7000** in a browser.

The first run creates `app.db` in the folder you start the app from (the project root). It is git-ignored.

---

## Project layout

```
HackConcordia/
├── pom.xml                              Maven build and dependencies
├── README.md                            This file
├── ONBOARDING.md                        Team onboarding guide for Claude Code
├── app.db                               SQLite database (created at runtime, git-ignored)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── Main.java                Server setup, routes, validation
│   │   │   └── Database.java            SQLite connection, schema, queries
│   │   └── resources/
│   │       └── public/                  Served at the site root (/)
│   │           ├── index.html           The single page
│   │           ├── styles.css           Styles
│   │           └── app.js               Front-end logic
│   └── test/
│       └── java/
│           ├── AppServerTest.java       Server starts; pages and assets are served
│           ├── PlacesApiTest.java       API create, list, validation, and filters
│           ├── IndexHtmlTest.java       HTML structure and element checks
│           └── StylesheetTest.java      CSS checks against the HTML
└── target/                              Build output (git-ignored)
```

Both `Main` and `Database` are in the default package.

---

## Running the app

### From IntelliJ

Open the project, then run `Main.main`. This is the setup the project is developed with. Use the **Run** configuration's JVM options if you need to pass `-Dapp.db.url=...` (see [Configuration](#configuration)).

When the server starts, you should see:

```
Javalin started in ...
Listening on http://localhost:7000/
```

The `WARNING: A restricted method ... System::load` messages from the SQLite driver are harmless. To silence them, add `--enable-native-access=ALL-UNNAMED` to the JVM arguments.

### Stopping

Press `Ctrl+C` in the terminal, or stop the run in your IDE.

---

## HTTP API

All API routes are under `/api`. Responses are JSON. Errors are JSON with a `message` field.

### `GET /api/places`

Lists saved places, oldest first. Accepts two optional query parameters:

| Parameter | Type | Meaning |
|-----------|------|---------|
| `q` | string | Text to find in the place's name or description. Case-insensitive. Blank values are ignored. `%` and `_` are matched literally. |
| `tags` | string (repeatable) | Only return places that have **any** of these tags (OR). Must be one of the allowed tags. |

When both are given, a place must match the text **and** have one of the tags.

**Examples**

```bash
curl http://localhost:7000/api/places
curl "http://localhost:7000/api/places?q=coffee"
curl "http://localhost:7000/api/places?tags=Study&tags=Library"
curl "http://localhost:7000/api/places?q=quiet&tags=Study"
```

**Response `200 OK`**

```json
[
  {
    "id": 1,
    "name": "Quiet Corner",
    "description": "Good for exams",
    "tags": ["Study", "Library"]
  }
]
```

If nothing matches, the response is an empty array `[]`.

### `POST /api/places`

Adds a place.

**Request body** (`Content-Type: application/json`)

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `name` | string | yes | Not blank after trimming. At most 100 characters. Saved trimmed. |
| `description` | string | no | At most 500 characters. Saved trimmed. Defaults to `""`. |
| `tags` | array of strings | no | Each tag must be an allowed tag. Defaults to `[]`. |

```bash
curl -X POST http://localhost:7000/api/places \
     -H "Content-Type: application/json" \
     -d '{"name": "Quiet Corner", "description": "Good for exams", "tags": ["Study", "Library"]}'
```

**Response `201 Created`**

```json
{
  "id": 1,
  "name": "Quiet Corner",
  "description": "Good for exams",
  "tags": ["Study", "Library"]
}
```

### Allowed tags

`Study`, `Library`, `Café`, `Cafeteria`

These are defined in `Main.ALLOWED_TAGS` and must match the checkboxes in `index.html`. Tag values are compared exactly, including the accent on `Café`.

### Errors

Validation failures return `400 Bad Request`:

| Situation | Message |
|-----------|---------|
| Body is not valid JSON | `Request body must be valid JSON.` |
| `name` missing or blank | `Name is required.` |
| `name` longer than 100 characters | `Name must be 100 characters or fewer.` |
| `description` longer than 500 characters | `Description must be 500 characters or fewer.` |
| Tag not in the allowed list | `Unknown tag: <tag>` |

A request to an unknown path returns `404 Not Found`.

Errors that are not handled explicitly (for example, a database failure) return `500`. Their details are written to the server log, not to the response.

### Static pages

| Path | Serves |
|------|--------|
| `/` | `index.html` |
| `/styles.css` | Stylesheet |
| `/app.js` | Front-end script |

Files are served from `src/main/resources/public/` on the classpath. The `/public` prefix is not part of the URL.

---

## Database

SQLite, stored in a single file.

**Location:** `app.db` in the working directory, unless `app.db.url` is set (see [Configuration](#configuration)).

**Creation:** `Database.init()` runs every time the app starts (from `Main.createApp()`, before the server accepts requests). It uses `CREATE TABLE IF NOT EXISTS`, so existing data is kept.

### Tables

**`places`** — used by the app

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER, primary key | Auto-increments |
| `name` | TEXT, not null | |
| `description` | TEXT, not null | Defaults to `''` |
| `tags` | TEXT, not null | Comma-separated, for example `Study,Library`. Defaults to `''`. |

**`users`** — created at startup, not used by any route yet

| Column | Type | Notes |
|--------|------|-------|
| `id` | INTEGER, primary key | Auto-increments |
| `name` | TEXT, not null | |
| `email` | TEXT, unique | |

### How tags are stored and searched

Tags are stored as a single comma-separated string. To match a tag exactly (so `Café` does not match `Cafeteria`), the query wraps the stored value in commas and looks for `,Tag,`:

```sql
(',' || tags || ',') LIKE '%,Study,%'
```

### Inspecting the data

Use the **Database** tool window in IntelliJ: **+ → Data Source → SQLite**, and set the file to `app.db` in the project root. Start the app at least once first so the file exists.

### Resetting the data

Stop the app, delete `app.db`, and start the app again. The tables are recreated empty.

---

## Front end

The page is a single HTML file with a small amount of JavaScript.

### Layout

- **Header:** a search box and four tag checkboxes.
- **Main:** a list of places, rendered from a `<template>`, and an **Add new place** button.
- **Dialog:** the add-place form, opened with `<dialog>.showModal()`.
- **Footer:** attribution.

### Behaviour (`app.js`)

- **On load:** requests `/api/places` and renders the list.
- **Search:** runs 250 ms after you stop typing. Pressing Enter also runs it. The form's submit is intercepted, so the page does not reload.
- **Tag checkboxes:** run the search immediately when changed.
- **Filters:** the query string is built from the search box and the ticked tags.
- **Race handling:** starting a new request cancels the previous one (`AbortController`), so a slow old response cannot replace newer results.
- **Empty states:** "No places yet" when there are no places, and "No places match these filters" when filters are active.
- **Add place:** opens the dialog and resets the form. On save, the page posts the data and reloads the list. Server errors are shown inside the dialog.
- **Safety:** place names and descriptions are inserted with `textContent`, so HTML in them is shown as text.

### Changing the tags

To add or rename a tag, change all of these together:

1. `Main.ALLOWED_TAGS` in `Main.java`.
2. The header checkboxes in `index.html` (`value` attributes).
3. The dialog checkboxes in `index.html` (`value` attributes).
4. The tests in `IndexHtmlTest` and `PlacesApiTest` if they name the tags.

---

## Configuration

| Setting | How to set | Default | Purpose |
|---------|-----------|---------|---------|
| Database file | JVM system property `app.db.url` | `jdbc:sqlite:app.db` | Where SQLite stores data. The tests use a temporary file. |
| Port | Code: `createApp().start(7000)` in `Main.main` | `7000` | HTTP port. Not configurable at runtime yet. |
| Static file root | Code: `config.staticFiles.add("/public", ...)` | `/public` on the classpath | Front-end files. |

Example, to use a different database file:

```bash
java -Dapp.db.url=jdbc:sqlite:/path/to/other.db -cp ... Main
```

---

## Testing

### Running the tests

```bash
mvn test            # run all tests
mvn clean test      # same, after deleting build output
```

Tests also run during `mvn package` and `mvn verify`. A failing test stops the build.

Use `mvn clean test` after switching branches or deleting source files. Old compiled classes in `target/` can otherwise still run.

### What is tested

| Test class | Count | Covers |
|-----------|-------|--------|
| `AppServerTest` | 10 | The server starts on a random port. `/`, `/styles.css`, and `/app.js` are served with the right types. Unknown paths return 404. `/public/...` is not a URL prefix. |
| `PlacesApiTest` | 22 | `GET` and `POST /api/places`. Validation (required name, length limits, unknown tags, invalid JSON). Search (case-insensitive, description, literal `%` and `_`). Tag filters (OR, whole-tag matching). Search and tags combined. Unknown tags in filters. |
| `IndexHtmlTest` | 25 | Document structure (doctype, `lang`, charset, viewport, title, single landmarks). Search box, tag checkboxes and their labels, add-place dialog and form fields, template, and script placement. Unique IDs. Every local `href`/`src` points to a real file. |
| `StylesheetTest` | 7 | Balanced braces and comments. Every class and ID in the CSS exists in the HTML. A media query exists. |

### How the API tests get their data

`PlacesApiTest` and `AppServerTest` set `app.db.url` to a new temporary file before starting the server, so the real `app.db` is never touched. `PlacesApiTest` also seeds three places with names starting with `Filter` for the filter tests.

### Adding a test

- **API behaviour:** add a method to `PlacesApiTest`. Use the `send` and `filter` helpers, and make assertions on names that the test itself creates. Tests share one database within a class, so don't assume the list is empty.
- **HTML changes:** add a check to `IndexHtmlTest`.
- **CSS changes:** `StylesheetTest` checks that every selector refers to something in the HTML. Add the element to the HTML first.

---

## Deploying

This project has no deployment setup yet. It runs as a single Java process.

### Build a runnable artifact

```bash
mvn clean package
```

This runs the tests and writes classes to `target/`. The current `pom.xml` does not configure a jar with a main class or bundle the dependencies. To produce a runnable package, add the `maven-shade-plugin` or `maven-assembly-plugin` with `Main` as the main class.

### Run in production

- Use a fixed location for the database by setting `-Dapp.db.url=jdbc:sqlite:/var/lib/hackconcordia/app.db`. Make sure the process can write to that folder.
- Put a reverse proxy (for example, nginx) in front for HTTPS. The app itself serves plain HTTP on port 7000.
- Back up the database file. Stop the app first, or use SQLite's online backup, so you don't copy a half-written file.

### Compiler version

`pom.xml` sets `maven.compiler.source` and `target` to `27` in properties, but the `maven-compiler-plugin` configuration sets them to `15`. The plugin setting wins. The code avoids newer language features (such as records) so it compiles at 15. Check this setting before deploying to a server that runs a different JDK.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `Address already in use` on startup | Another process uses port 7000 | Stop the other process, or change the port in `Main.main` |
| Page loads but the list says "Could not load places." | The server is down, or `/api/places` returns an error | Check the server log and try `curl http://localhost:7000/api/places` |
| Adding a place says "Could not reach the server." | The server stopped or the browser cannot reach it | Restart the app and reload the page |
| `DB init failed` on startup | The database folder is not writable, or `app.db` is locked by another program | Close other programs using the file (for example, a DataGrip or IntelliJ connection that holds it open), or fix folder permissions |
| The new place is saved but not in the list | Your current filters hide it | Clear the search box and untick the tags |
| `WARNING: ... System::load` in the log | SQLite driver native-access warning | Harmless. Add `--enable-native-access=ALL-UNNAMED` to silence it |
| Tests fail with `NoClassDefFound` for a class that no longer exists | Old compiled classes in `target/` | Run `mvn clean test` |

---

## Known limitations

- **Tags are fixed.** The four tags are defined in code and in the HTML. Users cannot create tags.
- **No editing or deleting.** Places can be added but not changed or removed through the app. Use the database tool to correct entries.
- **No accounts or authentication.** Anyone who can reach the server can add places.
- **Search is case-insensitive only for plain ASCII letters.** SQLite's `LIKE` does not fold accented letters, so searching `cafe` will not find `Café`, and `CAFÉ` will not match `café`.
- **No pagination.** All matching places are returned in one response. This is fine for a small list but will get slow as the list grows.
- **Port is fixed** at 7000 in code.
- **The `users` table is unused.** It is created at startup, but no route reads or writes it.
- **No input sanitising beyond length and tag checks.** Text is stored as entered and shown as text on the page, so it is not executed as HTML. Do not render these values as HTML elsewhere without escaping them.

---

## Contributing

1. Create a branch from `master`.
2. Make your change, and add or update tests.
3. Run `mvn clean test` and make sure all tests pass.
4. Commit with a clear message, and open a pull request.

When changing tags, the API, or the database schema, update this README as well.

### Commit and file conventions

- Don't commit `app.db` or `target/`. Both are git-ignored.
- Keep `.idea/` changes out of feature commits. Your local IDE settings are not shared.
- Write the commit message in the imperative mood, for example "Add tag filter to search".
