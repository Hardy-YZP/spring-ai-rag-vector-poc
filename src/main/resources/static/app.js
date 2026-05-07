const fileInput = document.querySelector("#fileInput");
const pickFileButton = document.querySelector("#pickFileButton");
const uploadButton = document.querySelector("#uploadButton");
const uploadForm = document.querySelector("#uploadForm");
const fileName = document.querySelector("#fileName");
const fileHint = document.querySelector("#fileHint");
const uploadResult = document.querySelector("#uploadResult");
const searchForm = document.querySelector("#searchForm");
const queryInput = document.querySelector("#queryInput");
const topK = document.querySelector("#topK");
const searchResults = document.querySelector("#searchResults");
const statusText = document.querySelector("#statusText");
const embeddedFiles = document.querySelector("#embeddedFiles");
const refreshFilesButton = document.querySelector("#refreshFilesButton");
const githubForm = document.querySelector("#githubForm");
const githubRepoInput = document.querySelector("#githubRepoInput");
const githubRefInput = document.querySelector("#githubRefInput");
const githubTokenInput = document.querySelector("#githubTokenInput");
const githubResult = document.querySelector("#githubResult");

let selectedFile = null;

document.addEventListener("DOMContentLoaded", () => {
    loadEmbeddedFiles();
});

pickFileButton.addEventListener("click", () => fileInput.click());
uploadForm.addEventListener("click", (event) => {
    if (event.target !== pickFileButton && !pickFileButton.contains(event.target)) {
        fileInput.click();
    }
});

fileInput.addEventListener("change", () => {
    setSelectedFile(fileInput.files[0]);
});

uploadForm.addEventListener("dragover", (event) => {
    event.preventDefault();
    uploadForm.classList.add("dragging");
});

uploadForm.addEventListener("dragleave", () => {
    uploadForm.classList.remove("dragging");
});

uploadForm.addEventListener("drop", (event) => {
    event.preventDefault();
    uploadForm.classList.remove("dragging");
    setSelectedFile(event.dataTransfer.files[0]);
});

uploadButton.addEventListener("click", async () => {
    if (!selectedFile) {
        return;
    }

    const body = new FormData();
    body.append("file", selectedFile);

    setBusy("Vectorizing");
    uploadButton.disabled = true;
    uploadResult.textContent = "";

    try {
        const response = await fetch("/rag/files", {
            method: "POST",
            body
        });
        const payload = await readJson(response);
        uploadResult.textContent = `Saved ${payload.chunks} chunks from ${payload.filename}`;
        await loadEmbeddedFiles();
        setReady();
    }
    catch (error) {
        uploadResult.textContent = error.message;
        setReady("Needs attention");
    }
    finally {
        uploadButton.disabled = !selectedFile;
    }
});

refreshFilesButton.addEventListener("click", async () => {
    setBusy("Refreshing");
    await loadEmbeddedFiles();
    setReady();
});

embeddedFiles.addEventListener("click", async (event) => {
    const deleteButton = event.target.closest("[data-delete-upload-id]");
    if (!deleteButton) {
        return;
    }

    const uploadId = deleteButton.dataset.deleteUploadId;
    const filename = deleteButton.dataset.filename || "this file";
    const confirmed = window.confirm(`Delete embeddings for "${filename}"?`);
    if (!confirmed) {
        return;
    }

    deleteButton.disabled = true;
    setBusy("Deleting");

    try {
        const response = await fetch(`/rag/files/${encodeURIComponent(uploadId)}`, {
            method: "DELETE"
        });
        const payload = await readJson(response);
        uploadResult.textContent = `Deleted ${payload.deletedChunks} chunks from ${payload.filename}`;
        await loadEmbeddedFiles();
        setReady();
    }
    catch (error) {
        uploadResult.textContent = error.message;
        setReady("Needs attention");
    }
    finally {
        deleteButton.disabled = false;
    }
});

searchForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = queryInput.value.trim();
    if (!query) {
        queryInput.focus();
        return;
    }

    setBusy("Searching");
    searchResults.classList.remove("empty");
    searchResults.innerHTML = "";

    try {
        const params = new URLSearchParams({
            query,
            topK: topK.value
        });
        const response = await fetch(`/rag/search?${params}`);
        const results = await readJson(response);
        renderResults(results);
        setReady();
    }
    catch (error) {
        searchResults.classList.add("empty");
        searchResults.innerHTML = `<p>${escapeHtml(error.message)}</p>`;
        setReady("Needs attention");
    }
});

githubForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const repositoryUrl = githubRepoInput.value.trim();
    if (!repositoryUrl) {
        githubRepoInput.focus();
        return;
    }

    setBusy("Importing");
    githubResult.textContent = "Downloading repository archive...";

    try {
        const response = await fetch("/rag/github/repositories", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                repositoryUrl,
                ref: githubRefInput.value.trim(),
                token: githubTokenInput.value.trim()
            })
        });
        const payload = await readJson(response);
        githubResult.textContent = `Imported ${payload.chunks} chunks from ${payload.filename}`;
        githubTokenInput.value = "";
        await loadEmbeddedFiles();
        setReady();
    }
    catch (error) {
        githubResult.textContent = error.message;
        setReady("Needs attention");
    }
});

function setSelectedFile(file) {
    selectedFile = file || null;
    uploadButton.disabled = !selectedFile;
    fileName.textContent = selectedFile ? selectedFile.name : "Drop a file here";
    fileHint.textContent = selectedFile ? formatBytes(selectedFile.size) : "or click to browse";
}

async function loadEmbeddedFiles() {
    try {
        const response = await fetch("/rag/files");
        const files = await readJson(response);
        renderEmbeddedFiles(files);
    }
    catch (error) {
        embeddedFiles.classList.add("empty");
        embeddedFiles.innerHTML = `<p>${escapeHtml(error.message)}</p>`;
    }
}

function renderEmbeddedFiles(files) {
    if (!files.length) {
        embeddedFiles.classList.add("empty");
        embeddedFiles.innerHTML = "<p>No embedded files yet.</p>";
        return;
    }

    embeddedFiles.classList.remove("empty");
    embeddedFiles.innerHTML = files.map((file) => `
        <article class="file-row">
            <div>
                <strong>${escapeHtml(file.filename)}</strong>
                <span>${escapeHtml(file.contentType || "application/octet-stream")}</span>
            </div>
            <div class="file-stats">
                <span>${file.chunks} chunks</span>
                <span>${formatBytes(file.size)}</span>
                <span>${formatDate(file.uploadedAt)}</span>
                <button class="danger" type="button" data-delete-upload-id="${escapeHtml(file.uploadId)}" data-filename="${escapeHtml(file.filename)}">Delete</button>
            </div>
        </article>
    `).join("");
}

function renderResults(results) {
    if (!results.length) {
        searchResults.classList.add("empty");
        searchResults.innerHTML = "<p>No matching chunks yet.</p>";
        return;
    }

    searchResults.classList.remove("empty");
    searchResults.innerHTML = results.map((item) => {
        const filename = item.metadata?.filename || "unknown";
        const repoPath = item.metadata?.repositoryPath;
        const displayName = repoPath || filename;
        const chunkIndex = Number(item.metadata?.chunkIndex ?? 0) + 1;
        const chunkCount = item.metadata?.chunkCount || "?";
        const score = item.score == null ? "n/a" : Number(item.score).toFixed(4);

        return `
            <article class="result-card">
                <div class="result-meta">
                    <span class="pill">${escapeHtml(displayName)}</span>
                    <span>Chunk ${chunkIndex}/${chunkCount}</span>
                    <span>Score ${score}</span>
                </div>
                <div class="snippet">${escapeHtml(item.text || "")}</div>
            </article>
        `;
    }).join("");
}

async function readJson(response) {
    const text = await response.text();
    const payload = text ? JSON.parse(text) : {};
    if (!response.ok) {
        throw new Error(payload.message || payload.error || `Request failed: ${response.status}`);
    }
    return payload;
}

function setBusy(label) {
    statusText.textContent = label;
}

function setReady(label = "Ready") {
    statusText.textContent = label;
}

function formatBytes(bytes) {
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function formatDate(value) {
    if (!value) {
        return "Unknown date";
    }
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short"
    }).format(new Date(value));
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
