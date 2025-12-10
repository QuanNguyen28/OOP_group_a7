package app.model.service.insight;

import java.util.Map;

public interface PromptTemplate {
    String render(Map<String,Object> vars);
}