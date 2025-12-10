package app.model.service.insight;

import java.util.Map;

public final class TemplateEngine {
    private TemplateEngine() {}
    public static String render(String tpl, Map<String,Object> vars) {
        String out = tpl;
        for (var e : vars.entrySet()) {
            out = out.replace("{{"+e.getKey()+"}}", String.valueOf(e.getValue()));
        }
        return out;
    }
}