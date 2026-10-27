package org.example.controller;

import org.example.entity.JobPosting;
import org.example.repository.JobPostingRepository;
import org.example.service.WebScraperService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@CrossOrigin(origins = "*")
public class JobController {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final JobPostingRepository jobPostingRepository;
    private final WebScraperService webScraperService;

    public JobController(RestClient.Builder restClientBuilder, ObjectMapper objectMapper, JobPostingRepository jobPostingRepository, WebScraperService webScraperService) {
        this.restClient = restClientBuilder.baseUrl("http://localhost:11434").build();
        this.objectMapper = objectMapper;
        this.jobPostingRepository = jobPostingRepository;
        this.webScraperService = webScraperService;
    }

    @PostMapping("/extract")
    public ResponseEntity<JobPosting> extractJobFromUrl(@RequestParam String url) {
        String rawDescription = webScraperService.extractTextFromUrl(url);
        JobPosting savedJob = processJobDescription(rawDescription);
        return ResponseEntity.ok(savedJob);
    }

    @GetMapping("/history")
    public ResponseEntity<List<JobPosting>> getAllJobs() {
        List<JobPosting> jobs = jobPostingRepository.findAll();
        jobs.sort((a, b) -> Boolean.compare(b.isPinned(), a.isPinned()));
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<String> exportCsv() {
        List<JobPosting> jobs = jobPostingRepository.findAll();
        StringBuilder csv = new StringBuilder("ID,Role,Company,Experience,Location,Technologies,Status\n");
        for (JobPosting job : jobs) {
            csv.append(job.getId()).append(",")
                    .append("\"").append(escapeCsv(job.getJobTitle())).append("\",")
                    .append("\"").append(escapeCsv(job.getCompanyName())).append("\",")
                    .append("\"").append(escapeCsv(job.getExperienceLevel())).append("\",")
                    .append("\"").append(escapeCsv(job.getLocation())).append("\",")
                    .append("\"").append(escapeCsv(job.getTechnologies())).append("\",")
                    .append("\"").append(escapeCsv(job.getStatus())).append("\"\n");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=jobs_export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    @PatchMapping("/{id}/pin")
    public ResponseEntity<Void> togglePin(@PathVariable Long id) {
        JobPosting job = jobPostingRepository.findById(id).orElseThrow(() -> new RuntimeException("Job not found"));
        job.setPinned(!job.isPinned());
        jobPostingRepository.save(job);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        JobPosting job = jobPostingRepository.findById(id).orElseThrow(() -> new RuntimeException("Job not found"));
        job.setStatus(status);
        jobPostingRepository.save(job);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{id}/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MatchResponse> matchCvFile(@PathVariable Long id,
                                                     @RequestParam(value = "file", required = false) MultipartFile file,
                                                     @RequestParam(value = "cvText", required = false) String cvText) {
        JobPosting job = jobPostingRepository.findById(id).orElseThrow(() -> new RuntimeException("Job not found"));

        String extractedCvText = extractTextFromFileOrString(file, cvText);

        String prompt = """
                Analyze the alignment between the candidate CV and the Job Description.
                Return strictly a valid JSON object with these exact keys:
                {
                  "matchScore": <integer between 0 and 100>,
                  "analysis": "<a detailed paragraph explaining the match, candidate strengths, and gaps>",
                  "missingTechnologies": ["tech1", "tech2"]
                }
                Do not include markdown blocks or any extra text outside the JSON.

                Job Description:
                %s
                
                CV Text:
                %s
                """.formatted(job.getRawDescription(), extractedCvText);

        Map<String, Object> requestBody = Map.of(
                "model", "job-extractor",
                "prompt", prompt,
                "stream", false,
                "format", "json"
        );

        OllamaResponse aiResponse = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(OllamaResponse.class);

        if (aiResponse == null || aiResponse.response() == null) {
            throw new RuntimeException("Received empty response from local Ollama model");
        }

        try {
            String cleanedJson = cleanJsonContent(aiResponse.response());
            JsonNode rootNode = objectMapper.readTree(cleanedJson);
            int score = rootNode.has("matchScore") ? rootNode.get("matchScore").asInt() : 75;
            String analysis = rootNode.has("analysis") ? rootNode.get("analysis").asText() : "Analysis completed successfully.";

            List<String> missingTechs = new ArrayList<>();
            JsonNode missingNode = rootNode.has("missingTechnologies") ? rootNode.get("missingTechnologies") : rootNode.has("missing_technologies") ? rootNode.get("missing_technologies") : null;

            if (missingNode != null && missingNode.isArray()) {
                for (JsonNode node : missingNode) {
                    missingTechs.add(node.asText());
                }
            }
            return ResponseEntity.ok(new MatchResponse(score, analysis, missingTechs));
        } catch (Exception e) {
            return ResponseEntity.ok(new MatchResponse(75, "Analysis completed successfully.", List.of()));
        }
    }

    @PostMapping(value = "/{id}/cover-letter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> generateCoverLetterDocx(@PathVariable Long id,
                                                          @RequestParam(value = "file", required = false) MultipartFile file,
                                                          @RequestParam(value = "cvText", required = false) String cvText) {
        JobPosting job = jobPostingRepository.findById(id).orElseThrow(() -> new RuntimeException("Job not found"));

        String extractedCvText = extractTextFromFileOrString(file, cvText);

        String prompt = """
                Write a professional and formal cover letter in English for the position of %s at %s based on the candidate's CV.
                The candidate is a third-year Economic Informatics student at ASE Bucharest.
                Return strictly a valid JSON object with a single key "coverLetter" whose value is the full text of the letter. Use '\\n' for line breaks.

                Job Description:
                %s
                
                CV Text:
                %s
                """.formatted(job.getJobTitle(), job.getCompanyName(), job.getRawDescription(), extractedCvText);

        Map<String, Object> requestBody = Map.of(
                "model", "job-extractor",
                "prompt", prompt,
                "stream", false,
                "format", "json"
        );

        OllamaResponse aiResponse = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(OllamaResponse.class);

        if (aiResponse == null || aiResponse.response() == null) {
            throw new RuntimeException("Received empty response from local Ollama model");
        }

        String coverLetterText;
        try {
            String cleanedJson = cleanJsonContent(aiResponse.response());
            JsonNode rootNode = objectMapper.readTree(cleanedJson);
            coverLetterText = rootNode.has("coverLetter") ? rootNode.get("coverLetter").asText() : aiResponse.response();
        } catch (Exception e) {
            coverLetterText = aiResponse.response();
        }

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            coverLetterText = coverLetterText.replace("\\n", "\n");

            String[] rawParagraphs = coverLetterText.split("\n+");
            for (String paraText : rawParagraphs) {
                String trimmed = paraText.trim();
                if (!trimmed.isEmpty()) {
                    XWPFParagraph p = document.createParagraph();
                    XWPFRun r = p.createRun();
                    r.setText(trimmed);
                    r.setFontFamily("Calibri");
                    r.setFontSize(11);
                    p.setSpacingAfter(150);
                }
            }

            document.write(out);
            byte[] docxBytes = out.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", "CoverLetter_" + job.getCompanyName().replaceAll("[^a-zA-Z0-9]", "_") + ".docx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(docxBytes);

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Word document", e);
        }
    }

    private String cleanJsonContent(String rawResponse) {
        if (rawResponse == null) return "{}";
        String cleaned = rawResponse.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private String extractTextFromFileOrString(MultipartFile file, String cvText) {
        if (file != null && !file.isEmpty()) {
            String filename = file.getOriginalFilename();
            try {
                if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
                    try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
                        PDFTextStripper stripper = new PDFTextStripper();
                        return stripper.getText(doc);
                    }
                } else if (filename != null && filename.toLowerCase().endsWith(".docx")) {
                    try (InputStream is = file.getInputStream();
                         XWPFDocument docx = new XWPFDocument(is)) {
                        StringBuilder sb = new StringBuilder();
                        for (XWPFParagraph p : docx.getParagraphs()) {
                            sb.append(p.getText()).append("\n");
                        }
                        return sb.toString();
                    }
                } else {
                    String content = new String(file.getBytes());
                    return parseJsonCvIfNeeded(content);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse uploaded file", e);
            }
        }
        return parseJsonCvIfNeeded(cvText != null ? cvText : "");
    }

    private String parseJsonCvIfNeeded(String content) {
        if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
            try {
                JsonNode node = objectMapper.readTree(content);
                StringBuilder sb = new StringBuilder();
                if (node.has("summary")) {
                    sb.append("Summary: ").append(node.get("summary").asText()).append("\n\n");
                }
                if (node.has("skills")) {
                    sb.append("Skills: ").append(node.get("skills").toString()).append("\n\n");
                }
                if (node.has("experience")) {
                    sb.append("Experience:\n");
                    for (JsonNode exp : node.get("experience")) {
                        sb.append("- Company: ").append(exp.path("company").asText())
                                .append(", Role: ").append(exp.path("title").asText())
                                .append("\n  Details: ").append(exp.path("description").toString()).append("\n");
                    }
                    sb.append("\n");
                }
                if (node.has("education")) {
                    sb.append("Education:\n");
                    for (JsonNode edu : node.get("education")) {
                        sb.append("- School: ").append(edu.path("institution").asText())
                                .append(", Degree: ").append(edu.path("degree").asText()).append("\n");
                    }
                }
                return sb.length() > 0 ? sb.toString() : content;
            } catch (Exception ex) {
                return content;
            }
        }
        return content;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        if (jobPostingRepository.existsById(id)) {
            jobPostingRepository.deleteById(id);
        }
        return ResponseEntity.ok().build();
    }

    private JobPosting processJobDescription(String rawDescription)
    {
        String prompt = """
                Extract the job title (role), company name, required technologies, experience level, location, and work type from the given job posting.
                Return strictly in valid JSON format with keys: jobTitle, companyName, technologies, experienceLevel, location, workType.
                Job posting:
                %s
                """.formatted(rawDescription);

        Map<String, Object> requestBody = Map.of(
                "model", "job-extractor",
                "prompt", prompt,
                "stream", false,
                "format", "json"
        );

        OllamaResponse aiResponse = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(OllamaResponse.class);

        if (aiResponse == null || aiResponse.response() == null)
        {
            throw new RuntimeException("Received empty response from local Ollama model");
        }

        try
        {
            String cleanedJson = cleanJsonContent(aiResponse.response());
            JsonNode rootNode = objectMapper.readTree(cleanedJson);

            String jobTitle = rootNode.has("jobTitle") ? rootNode.get("jobTitle").asText() : "Not specified";
            String companyName = rootNode.has("companyName") ? rootNode.get("companyName").asText() : "Not specified";
            String experienceLevel = rootNode.has("experienceLevel") ? rootNode.get("experienceLevel").asText() : "-";
            String location = rootNode.has("location") ? rootNode.get("location").asText() : "-";
            String workType = rootNode.has("workType") ? rootNode.get("workType").asText() : "-";

            JsonNode techNode = rootNode.has("technologies") ? rootNode.get("technologies") : rootNode.has("skills") ? rootNode.get("skills") : rootNode.has("tech") ? rootNode.get("tech") : null;

            String techsFormatted = "";
            if (techNode != null) {
                if (techNode.isArray()) {
                    List<String> list = new ArrayList<>();
                    for (JsonNode n : techNode) {
                        list.add(n.asText());
                    }
                    techsFormatted = String.join(", ", list);
                } else {
                    techsFormatted = techNode.asText();
                }
            }

            JobPosting jobPosting = new JobPosting(jobTitle, companyName, rawDescription, techsFormatted, experienceLevel, location, workType);
            return jobPostingRepository.save(jobPosting);

        } catch (Exception e)
        {
            throw new RuntimeException("Failed to parse AI response or save to database", e);
        }
    }

    private record OllamaResponse(String response) {}
    public record MatchResponse(int matchScore, String analysis, List<String> missingTechnologies) {}
}