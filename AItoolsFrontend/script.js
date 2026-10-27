let allJobsData = [];
let currentMatchJobId = null;

document.addEventListener("DOMContentLoaded", () => {
    loadHistory();
});

const showAlert = (message, type = "success") => {
    const alertBox = document.getElementById("alertBox");
    if (!alertBox) return;
    alertBox.textContent = message;
    alertBox.className = `p-3 rounded text-xs border font-mono transition-all duration-200 block ${
        type === "success" 
            ? "bg-emerald-950/40 border-emerald-800/60 text-emerald-300" 
            : "bg-red-950/40 border-red-800/60 text-red-300"
    }`;
    setTimeout(() => {
        alertBox.classList.add("hidden");
    }, 4000);
};

async function extractJob() {
    const urlInput = document.getElementById("jobUrl");
    const submitBtn = document.getElementById("submitBtn");
    const loading = document.getElementById("loading");
    const url = urlInput.value.trim();

    if (!url) {
        showAlert("Please enter a valid link before proceeding.", "error");
        return;
    }

    submitBtn.disabled = true;
    loading.classList.remove("hidden");

    try {
        const response = await fetch(`http://localhost:8080/api/jobs/extract?url=${encodeURIComponent(url)}`, {
            method: "POST"
        });

        if (!response.ok) {
            throw new Error("Failed to process the URL.");
        }

        const data = await response.json();
        displayResult(data);
        showAlert("Job successfully extracted and saved.", "success");
        urlInput.value = "";
        loadHistory();
    } catch (error) {
        showAlert("An error occurred while processing the link.", "error");
    } finally {
        submitBtn.disabled = false;
        loading.classList.add("hidden");
    }
}

function displayResult(job) {
    const card = document.getElementById("resultCard");
    if (!card) return;
    card.classList.remove("hidden");

    document.getElementById("resTitle").textContent = job.jobTitle || "Not specified";
    document.getElementById("resCompany").textContent = job.companyName || "Not specified";
    document.getElementById("resExp").textContent = job.experienceLevel || "-";
    document.getElementById("resLoc").textContent = job.location || "-";
    document.getElementById("resType").textContent = job.workType || "-";

    const techsContainer = document.getElementById("resTechs");
    techsContainer.innerHTML = renderTechnologies(job.technologies, 'res');
}

async function loadHistory() {
    const tbody = document.getElementById("historyTableBody");
    if (!tbody) return;
    tbody.innerHTML = `<tr><td colspan="8" class="py-6 text-center text-zinc-600 font-mono">Loading database records...</td></tr>`;

    try {
        const response = await fetch("http://localhost:8080/api/jobs/history");
        if (!response.ok) throw new Error("Failed to load history.");

        allJobsData = await response.json();
        
        allJobsData.sort((a, b) => (b.pinned === true) - (a.pinned === true));

        renderTable(allJobsData);
    } catch (error) {
        tbody.innerHTML = `<tr><td colspan="8" class="py-6 text-center text-red-500 font-mono">Failed to load records from database.</td></tr>`;
    }
}

function renderTable(jobs) {
    const tbody = document.getElementById("historyTableBody");
    if (!tbody) return;
    
    if (!jobs || jobs.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" class="py-6 text-center text-zinc-600 font-mono">No records found.</td></tr>`;
        return;
    }

    tbody.innerHTML = "";
    jobs.forEach((job, index) => {
        const tr = document.createElement("tr");
        tr.className = "hover:bg-zinc-900/50 transition";
        
        const pinColorClass = job.pinned ? "text-amber-400 fill-amber-400" : "text-zinc-500 hover:text-zinc-200";

        tr.innerHTML = `
            <td class="py-3 px-4 font-mono text-zinc-500">#${index + 1}</td>
            <td class="py-3 px-4 font-medium text-zinc-100">${job.jobTitle || "Not specified"}</td>
            <td class="py-3 px-4 text-zinc-400">${job.companyName || "Not specified"}</td>
            <td class="py-3 px-4 text-zinc-400">${job.experienceLevel || "-"}</td>
            <td class="py-3 px-4 text-zinc-400">${job.location || "-"}</td>
            <td class="py-3 px-4">${renderTechnologies(job.technologies, job.id)}</td>
            <td class="py-3 px-4">
                <select onchange="updateJobStatus(${job.id}, this.value)" class="bg-zinc-950 border border-zinc-800 text-xs rounded px-2 py-1 text-zinc-300 font-mono focus:outline-none focus:border-zinc-700 cursor-pointer">
                    <option value="Saved" ${job.status === 'Saved' ? 'selected' : ''}>Saved</option>
                    <option value="Applied" ${job.status === 'Applied' ? 'selected' : ''}>Applied</option>
                    <option value="Interviewing" ${job.status === 'Interviewing' ? 'selected' : ''}>Interviewing</option>
                    <option value="Rejected" ${job.status === 'Rejected' ? 'selected' : ''}>Rejected</option>
                </select>
            </td>
            <td class="py-3 px-4 text-right space-x-2">
                <button onclick="openMatchModal(${job.id})" class="text-zinc-500 hover:text-emerald-400 transition" title="AI Assistant">
                    <svg class="w-4 h-4 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>
                </button>
                <button onclick="togglePin(${job.id})" class="${pinColorClass} transition" title="Pin record">
                    <svg class="w-4 h-4 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"></path></svg>
                </button>
                <button onclick="deleteJob(${job.id})" class="text-zinc-500 hover:text-red-400 transition" title="Delete record">
                    <svg class="w-4 h-4 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"></path></svg>
                </button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function filterJobs() {
    const searchInput = document.getElementById("searchInput");
    if (!searchInput) return;
    const query = searchInput.value.toLowerCase();
    const filtered = allJobsData.filter(job => 
        (job.jobTitle && job.jobTitle.toLowerCase().includes(query)) ||
        (job.companyName && job.companyName.toLowerCase().includes(query)) ||
        (job.technologies && job.technologies.toLowerCase().includes(query))
    );
    renderTable(filtered);
}

function exportCsv() {
    window.location.href = "http://localhost:8080/api/jobs/export/csv";
}

async function updateJobStatus(id, newStatus) {
    try {
        const response = await fetch(`http://localhost:8080/api/jobs/${id}/status?status=${encodeURIComponent(newStatus)}`, {
            method: "PATCH"
        });
        if (!response.ok) throw new Error("Failed to update status.");
        showAlert("Status updated successfully.", "success");
    } catch (error) {
        showAlert("An error occurred while updating status.", "error");
    }
}

function openMatchModal(jobId) {
    currentMatchJobId = jobId;
    const cvTextInput = document.getElementById("cvTextInput");
    const cvFileInput = document.getElementById("cvFileInput");
    if (cvTextInput) cvTextInput.value = "";
    if (cvFileInput) cvFileInput.value = "";

    document.getElementById("modalFormContent").classList.remove("hidden");
    document.getElementById("modalLoading").classList.add("hidden");
    document.getElementById("modalResultContent").classList.add("hidden");
    document.getElementById("matchModal").classList.remove("hidden");
}

function closeMatchModal() {
    const matchModal = document.getElementById("matchModal");
    if (matchModal) matchModal.classList.add("hidden");
}

async function submitAiAction(actionType) {
    const fileInput = document.getElementById("cvFileInput");
    const cvText = document.getElementById("cvTextInput").value.trim();

    if ((!fileInput || fileInput.files.length === 0) && !cvText) {
        alert("Please upload a CV file or paste your CV text first.");
        return;
    }

    document.getElementById("modalFormContent").classList.add("hidden");
    document.getElementById("modalLoading").classList.remove("hidden");
    document.getElementById("loadingText").textContent = actionType === 'match' 
        ? "Running AI compatibility analysis..." 
        : "Generating professional cover letter document...";

    try {
        const endpoint = actionType === 'match' ? 'match' : 'cover-letter';
        const formData = new FormData();
        
        if (fileInput && fileInput.files.length > 0) {
            formData.append("file", fileInput.files[0]);
        } else {
            formData.append("cvText", cvText);
        }

        const response = await fetch(`http://localhost:8080/api/jobs/${currentMatchJobId}/${endpoint}`, {
            method: "POST",
            body: formData
        });

        if (!response.ok) throw new Error("Failed to process AI request.");

        if (actionType === 'cover-letter') {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `CoverLetter_${currentMatchJobId}.docx`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            window.URL.revokeObjectURL(url);
            closeMatchModal();
            showAlert("Cover letter Word document downloaded successfully.", "success");
            return;
        }

        const data = await response.json();
        document.getElementById("matchResultContainer").classList.remove("hidden");
        document.getElementById("coverLetterResultContainer").classList.add("hidden");

        document.getElementById("matchScoreBar").style.width = `${data.matchScore}%`;
        document.getElementById("matchScoreText").textContent = `${data.matchScore}% match`;
        document.getElementById("matchAnalysisText").textContent = data.analysis || "No analysis provided.";
        
        const missingContainer = document.getElementById("matchMissingTechs");
        missingContainer.innerHTML = "";
        if (data.missingTechnologies && data.missingTechnologies.length > 0) {
            data.missingTechnologies.forEach(tech => {
                missingContainer.innerHTML += `<span class="bg-red-950/60 border border-red-800/60 text-red-300 px-2 py-0.5 rounded text-[11px]">${tech}</span>`;
            });
        } else {
            missingContainer.innerHTML = '<span class="text-zinc-500">None! You match all listed technologies.</span>';
        }

        document.getElementById("modalLoading").classList.add("hidden");
        document.getElementById("modalResultContent").classList.remove("hidden");
    } catch (error) {
        alert("An error occurred during AI processing.");
        closeMatchModal();
    }
}

function renderTechnologies(technologies, identifier) {
    if (!technologies || technologies.length === 0) return '<span class="text-zinc-600">-</span>';

    const techArray = typeof technologies === 'string' 
        ? technologies.split(',').map(t => t.trim()).filter(t => t.length > 0) 
        : technologies;

    if (!Array.isArray(techArray) || techArray.length === 0) return '<span class="text-zinc-600">-</span>';

    const maxVisible = 3;
    const visibleTechs = techArray.slice(0, maxVisible);
    const hiddenTechs = techArray.slice(maxVisible);

    let html = '<div class="flex flex-wrap gap-1.5 items-center">';

    visibleTechs.forEach(tech => {
        html += `<span class="bg-zinc-950 border border-zinc-800 px-2 py-0.5 rounded text-[11px] text-zinc-300 font-mono">${tech}</span>`;
    });

    if (hiddenTechs.length > 0) {
        html += `<span id="more-btn-${identifier}" onclick="toggleTechs('${identifier}')" class="bg-zinc-800/60 hover:bg-zinc-800 border border-zinc-700/60 px-2 py-0.5 rounded text-[10px] text-zinc-400 font-mono cursor-pointer transition">
            +${hiddenTechs.length} more
        </span>`;

        html += `<span id="hidden-techs-${identifier}" class="hidden flex flex-wrap gap-1.5 items-center">`;
        hiddenTechs.forEach(tech => {
            html += `<span class="bg-zinc-950 border border-zinc-800 px-2 py-0.5 rounded text-[11px] text-zinc-300 font-mono">${tech}</span>`;
        });
        html += `</span>`;
    }

    html += '</div>';
    return html;
}

function toggleTechs(identifier) {
    const hiddenContainer = document.getElementById(`hidden-techs-${identifier}`);
    const moreBtn = document.getElementById(`more-btn-${identifier}`);
    
    if (hiddenContainer && hiddenContainer.classList.contains('hidden')) {
        hiddenContainer.classList.remove('hidden');
        if (moreBtn) moreBtn.style.display = 'none';
    }
}

async function deleteJob(id) {
    if (!confirm("Are you sure you want to delete this record?")) return;

    try {
        const response = await fetch(`http://localhost:8080/api/jobs/${id}`, {
            method: "DELETE"
        });

        if (!response.ok) throw new Error("Failed to delete record.");

        showAlert("Record deleted successfully.", "success");
        loadHistory();
    } catch (error) {
        showAlert("An error occurred while deleting the record.", "error");
    }
}

async function togglePin(id) {
    try {
        const response = await fetch(`http://localhost:8080/api/jobs/${id}/pin`, {
            method: "PATCH"
        });

        if (!response.ok) throw new Error("Failed to update pin status.");
        
        const job = allJobsData.find(j => j.id === id);
        if (job) {
            job.pinned = !job.pinned;
            
            allJobsData.sort((a, b) => (b.pinned === true) - (a.pinned === true));
            
            renderTable(allJobsData);
        } else {
            loadHistory();
        }
    } catch (error) {
        showAlert("An error occurred while pinning the record.", "error");
    }
}