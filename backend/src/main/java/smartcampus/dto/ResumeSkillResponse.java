package smartcampus.dto;

import smartcampus.entity.SkillCategory;
import smartcampus.entity.SkillProficiency;

public record ResumeSkillResponse(
    Long id, String name, SkillCategory category, SkillProficiency proficiency, int displayOrder) {}
