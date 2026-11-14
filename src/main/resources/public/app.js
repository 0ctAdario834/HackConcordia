// Once the DOM is loaded we
document.addEventListener("DOMContentLoaded", () => {
    const results = document.getElementById("results");
    const template = document.getElementById("place-template");
    const dialog = document.getElementById("add-place-dialog");
    const openButton = document.getElementById("add-place");
    const cancelButton = document.getElementById("cancel-place");
    const form = document.getElementById("add-place-form");
    const errorBox = document.getElementById("form-error");
    const searchForm = document.querySelector("form.search-form");
    const searchInput = document.getElementById("search");
    const tagBoxes = document.querySelectorAll("header .tags input[type=checkbox]");

    const nearMeBox = document.getElementById("near-me");
    const nearMeStatus = document.getElementById("near-me-status");

    let userLocation = null; // { lat, lon } once the browser has given it to us
    let searchTimer = null;
    let latestRequest = null;

    function renderPlace(place) {
        const item = template.content.firstElementChild.cloneNode(true);
        item.querySelector("h2").textContent = place.name;
        const details = [place.description, (place.tags || []).join(", ")]
            .filter(Boolean)
            .join(" · ");
        item.querySelector("p:not(.place-meta)").textContent = details;
        const extra = [];
        if (place.address) {
            extra.push(place.address);
        }
        if (typeof place.distanceKm === "number") {
            extra.push(place.distanceKm < 1
                ? Math.round(place.distanceKm * 1000) + " m away"
                : place.distanceKm.toFixed(1) + " km away");
        }
        item.querySelector(".place-meta").textContent = extra.join(" · ");
        return item;
    }

    function renderPlaces(places, filtered) {
        results.replaceChildren();
        if (places.length === 0) {
            const empty = document.createElement("li");
            empty.textContent = filtered
                ? "No places match these filters."
                : "No places yet. Add the first one!";
            results.append(empty);
            return;
        }
        results.append(...places.map(renderPlace));
    }

    // Builds the query string from the search box and the checked tags.
    function currentFilters() {
        const params = new URLSearchParams();
        const q = searchInput.value.trim();
        if (q) {
            params.set("q", q);
        }
        tagBoxes.forEach((box) => {
            if (box.checked) {
                params.append("tags", box.value);
            }
        });
        return params.toString();
    }

    function nearMeActive() {
        return nearMeBox.checked && userLocation !== null;
    }

    async function loadPlaces() {
        // Cancel any earlier request so a slow old response can't overwrite a newer one.
        if (latestRequest) {
            latestRequest.abort();
        }
        const controller = new AbortController();
        latestRequest = controller;

        const params = new URLSearchParams(currentFilters());
        if (nearMeActive()) {
            params.set("lat", userLocation.lat);
            params.set("lon", userLocation.lon);
        }
        const query = currentFilters();
        const fullQuery = params.toString();
        try {
            const response = await fetch("/api/places" + (fullQuery ? "?" + fullQuery : ""), {
                signal: controller.signal,
            });
            if (!response.ok) {
                throw new Error("HTTP " + response.status);
            }
            renderPlaces(await response.json(), query !== "");
        } catch (err) {
            if (err.name === "AbortError") {
                return;
            }
            results.replaceChildren();
            const failed = document.createElement("li");
            failed.textContent = "Could not load places.";
            results.append(failed);
        }
    }

    searchForm.addEventListener("submit", (event) => {
        event.preventDefault();
        loadPlaces();
    });

    // Wait a moment after typing stops before searching.
    searchInput.addEventListener("input", () => {
        clearTimeout(searchTimer);
        searchTimer = setTimeout(loadPlaces, 250);
    });

    tagBoxes.forEach((box) => box.addEventListener("change", loadPlaces));

    nearMeBox.addEventListener("change", () => {
        nearMeStatus.textContent = "";
        if (!nearMeBox.checked) {
            loadPlaces();
            return;
        }
        if (userLocation) {
            loadPlaces();
            return;
        }
        if (!navigator.geolocation) {
            nearMeBox.checked = false;
            nearMeStatus.textContent = "Location is not supported by this browser.";
            return;
        }
        nearMeStatus.textContent = "Locating…";
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                userLocation = { lat: pos.coords.latitude, lon: pos.coords.longitude };
                nearMeStatus.textContent = "";
                loadPlaces();
            },
            () => {
                nearMeBox.checked = false;
                nearMeStatus.textContent = "Could not get your location.";
            },
            { timeout: 10000 }
        );
    });

    openButton.addEventListener("click", () => {
        form.reset();
        errorBox.textContent = "";
        dialog.showModal();
    });

    cancelButton.addEventListener("click", () => dialog.close());

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        errorBox.textContent = "";

        const data = new FormData(form);
        const payload = {
            name: String(data.get("name") || "").trim(),
            description: String(data.get("description") || "").trim(),
            tags: data.getAll("tags"),
            address: String(data.get("address") || "").trim(),
        };

        try {
            const response = await fetch("/api/places", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });
            if (!response.ok) {
                const problem = await response.json().catch(() => ({}));
                errorBox.textContent = problem.message || "Could not save the place.";
                return;
            }
            dialog.close();
            await loadPlaces();
        } catch (err) {
            errorBox.textContent = "Could not reach the server. Try again.";
        }
    });

    loadPlaces().then();
});
