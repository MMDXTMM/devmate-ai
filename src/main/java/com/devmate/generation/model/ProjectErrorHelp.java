package com.devmate.generation.model;
import java.util.List;
public record ProjectErrorHelp(String explanation,List<String> possibleCauses,List<Action> steps,List<String> limits) {
    public record Action(String title,String instruction,String command) { }
}
