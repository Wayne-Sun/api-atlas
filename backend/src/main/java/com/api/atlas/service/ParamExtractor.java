package com.api.atlas.service;

import com.api.atlas.model.ParamDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ParamExtractor {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    public List<ParamDef> extract(String queryContent) {
        if (queryContent == null || queryContent.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = PARAM_PATTERN.matcher(queryContent);
        while (matcher.find()) {
            seen.add(matcher.group(1));
        }
        List<ParamDef> result = new ArrayList<>();
        int order = 0;
        for (String name : seen) {
            result.add(new ParamDef(name, "String", "", order++));
        }
        return result;
    }
}
