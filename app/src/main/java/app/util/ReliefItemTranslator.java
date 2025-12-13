package app.util;

/** Dịch tên hạng mục cứu trợ từ Tiếng Anh sang Tiếng Việt */
public class ReliefItemTranslator {
    
    private static final java.util.Map<String, String> RELIEF_NAMES = new java.util.HashMap<>();
    
    static {
        RELIEF_NAMES.put("shelter", "Nhà Ở");
        RELIEF_NAMES.put("food", "Thực Phẩm");
        RELIEF_NAMES.put("transportation", "Vận Chuyển");
        RELIEF_NAMES.put("water", "Nước");
        RELIEF_NAMES.put("communication", "Thông Tin Liên Lạc");
        RELIEF_NAMES.put("medicine", "Y Tế");
    }
    
    public static String translate(String englishName) {
        if (englishName == null) return englishName;
        String translated = RELIEF_NAMES.get(englishName.toLowerCase().trim());
        return translated != null ? translated : englishName;
    }
}
