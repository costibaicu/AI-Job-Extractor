package org.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_postings")
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobTitle;
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String rawDescription;

    @Column(columnDefinition = "TEXT")
    private String technologies;

    private String experienceLevel;
    private String location;
    private String workType;

    private boolean pinned = false;
    private String status = "Saved";

    private LocalDateTime createdAt = LocalDateTime.now();

    public JobPosting() {

    }

    public JobPosting(String jobTitle, String companyName, String rawDescription, String technologies, String experienceLevel, String location, String workType) {
        this.jobTitle = jobTitle;
        this.companyName = companyName;
        this.rawDescription = rawDescription;
        this.technologies = technologies;
        this.experienceLevel = experienceLevel;
        this.location = location;
        this.workType = workType;
        this.status = "Saved";
        this.pinned = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getRawDescription() { return rawDescription; }
    public void setRawDescription(String rawDescription) { this.rawDescription = rawDescription; }

    public String getTechnologies() { return technologies; }
    public void setTechnologies(String technologies) { this.technologies = technologies; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

}