// Once the DOM is loadede we
document.addEventListener("DOMContentLoaded", () => {
    const results = document.getElementById("results");
    const template = document.getElementById("place-template");
    const dialog = document.getElementById("add-place-dialog");
    const openButton = document.getElementById("add-place");
    const cancelButton = document.getElementById("cancel-place");
    const form = document.getElementById("add-place-form");
    const errorBox = document.getElementById("form-error");

    function renderPlace(place) {
        const item = template.content.firstElementChild.cloneNode(true);
        item.querySelector("h2").textContent = place.name;
        const details = [place.description, (place.tags || []).join(", ")]
            .filter(Boolean)
            .join(" · ");
        item.querySelector("p").textContent = details;
        return item;
    }

    function renderPlaces(places) {
        results.replaceChildren();
        if (places.length === 0) {
            const empty = document.createElement("li");
            empty.textContent = "No places yet. Add the first one!";
            results.append(empty);
            return;
        }
        results.append(...places.map(renderPlace));
    }

    async function loadPlaces() {
        try {
            const response = await fetch("/api/places");
            if (!response.ok) {
                throw new Error("HTTP " + response.status);
            }
            renderPlaces(await response.json());
        } catch (err) {
            results.replaceChildren();
            const failed = document.createElement("li");
            failed.textContent = "Could not load places.";
            results.append(failed);
        }
    }

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

    loadPlaces();
});
