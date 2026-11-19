package com.devmate.generation.model;
import java.util.List;
public record ProjectChangePlan(String summary,List<String> changes,List<String> risks) { }
