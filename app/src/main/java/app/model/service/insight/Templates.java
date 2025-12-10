package app.model.service.insight;

public final class Templates {
    private Templates() {}

    public static final String SYSTEM =
        "You are a data analyst. Summarize clearly, concise, bullet-first. Language: {{locale}}.";

    public static final String OVERALL_SENTIMENT = """
        We have overall daily sentiment counts for a disaster analysis run.
        Context:
        - Run ID: {{runId}}
        - Date range: {{range}}
        - Rows (day|pos|neg|neu): 
        {{rows}}
        Task:
        1) One-paragraph overview.
        2) 3–5 bullet insights (peaks, shifts, key days).
        3) A short recommendation.
        """;

    public static final String DAMAGE = """
        Damage categories and daily distribution.
        Run: {{runId}}
        Top categories: {{topDamage}}
        Daily (type|day|count):
        {{rows}}
        Summarize patterns and recommend priority actions.
        """;

    public static final String RELIEF = """
        Relief items distribution (counts).
        Run: {{runId}}
        Items (item|count):
        {{rows}}
        Summarize which items are well-covered vs lacking, and recommendations.
        """;

    public static final String TASK3 = """
        Satisfaction (positive/negative) by relief category.
        Run: {{runId}}
        Rows (category|pos|neg|total):
        {{rows}}
        Produce: 
        - Short overview
        - Bullet points for categories with high dissatisfaction (neg>pos)
        - Priority recommendations
        """;

    public static final String TASK4 = """
        Sentiment over time per relief category.
        Run: {{runId}} | Range: {{range}}
        Rows (date|category|avg_score[-1..1]):
        {{rows}}
        Summarize trends per category (↑/↓/stable), anomalies, and next steps.
        """;
}