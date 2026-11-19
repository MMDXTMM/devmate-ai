package com.devmate.generation.dto;
import java.util.List;
public record RuntimeGuideResponse(String language,List<String> evidence,List<Step> steps,List<String> notes) {
    public record Step(String title,String instruction,List<String> commands) { }
}
