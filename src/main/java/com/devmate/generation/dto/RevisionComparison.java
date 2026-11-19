package com.devmate.generation.dto;
import java.util.List;
public record RevisionComparison(String beforeId,String afterId,List<FileChange> files) {
    public record FileChange(String path,String change) { }
    public record FileDiff(String path,String before,String after) { }
}
