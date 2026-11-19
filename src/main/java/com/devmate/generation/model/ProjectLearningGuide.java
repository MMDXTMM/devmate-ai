package com.devmate.generation.model;
import java.util.List;
public record ProjectLearningGuide(String purpose,List<Module> modules,List<Step> flow,List<Step> readingOrder,List<String> limits) {
    public record Module(String name,String responsibility,List<String> paths) { }
    public record Step(String title,String explanation,List<String> paths) { }
}
