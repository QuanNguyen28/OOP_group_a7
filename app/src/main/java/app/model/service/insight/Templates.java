package app.model.service.insight;

/** Template prompt tiếng Việt cho phần tóm tắt LLM. */
public final class Templates {
    private Templates() {}

    /** Tổng quan cảm xúc. Có chèn DATA + TOTALS + RANGE thực tế. */
    public static final String OVERALL_VI = """
            Bạn là nhà phân tích dữ liệu. Hãy tóm tắt xu hướng cảm xúc công chúng cho bảng điều khiển ứng phó thảm họa.
            Dữ liệu thật được cung cấp bên dưới; chỉ dựa vào dữ liệu này. KHÔNG nói rằng thiếu dữ liệu hay thiếu thời gian.
            
            RUN_ID: %s
            KHOẢNG THỜI GIAN (hiệu lực): %s → %s

            TỔNG HỢP: tích cực=%s, tiêu cực=%s, trung tính=%s

            DỮ LIỆU (CSV: ngay,pos,neg,neu) — đã sắp tăng dần theo ngày:
            %s

            Yêu cầu đầu ra:
            • 3–5 gạch đầu dòng về xu hướng (đỉnh/đáy, độ lệch, đảo chiều).
            • 1 khuyến nghị hành động cụ thể (ưu tiên, phân bổ, theo dõi).
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Bài toán 2 — Thiệt hại phổ biến. */
    public static final String DAMAGE_VI = """
            Tóm tắt các nhóm thiệt hại được công chúng đề cập nhiều nhất cho RUN_ID=%s.
            Nhóm chuẩn: Người bị ảnh hưởng; Gián đoạn kinh tế; Nhà/tòa nhà hư hỏng; Tài sản cá nhân mất; Cơ sở hạ tầng hư hỏng; Khác.
            KHÔNG nói rằng thiếu dữ liệu; chỉ dựa vào bảng đính kèm.

            DỮ LIỆU (CSV: danh_muc,so_luong):
            %s

            Yêu cầu:
            • 3 gạch đầu dòng: nhóm trội nhất, manh mối/diễn tiến nếu nhìn thấy, hệ quả vận hành.
            • 1 khuyến nghị ưu tiên phục hồi.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Hạng mục cứu trợ — mức độ đề cập/hiệu quả cảm nhận. */
    public static final String RELIEF_VI = """
            Tóm tắt các hạng mục cứu trợ (tiền mặt, y tế, nhà ở, thực phẩm, giao thông) cho RUN_ID=%s.
            CHỈ dựa vào dữ liệu kèm theo; KHÔNG nói rằng thiếu dữ liệu.

            DỮ LIỆU (CSV: hang_muc,so_luong):
            %s

            Yêu cầu:
            • 3 gạch đầu dòng: nổi bật/bất cập/nguyên nhân suy đoán.
            • 1 khuyến nghị điều phối/phân bổ.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Bài toán 3 — Hài lòng vs. không hài lòng theo hạng mục. */
    public static final String TASK3_VI = """
            Bài toán 3: Đánh giá mức độ hài lòng/không hài lòng theo hạng mục cứu trợ cho RUN_ID=%s.
            Nhấn mạnh: hạng mục tiêu cực cao nhất (nhu cầu chưa đáp ứng), hạng mục tích cực cao nhất (phân phối hiệu quả).
            KHÔNG nói rằng thiếu dữ liệu; chỉ dựa vào bảng đính kèm.

            DỮ LIỆU (CSV: hang_muc,pos,neg,net):
            %s

            Yêu cầu:
            • 4 gạch đầu dòng: 2 điểm nóng tiêu cực + 2 điểm sáng tích cực (nêu giả thuyết nguyên nhân).
            • 1 lời khuyên ưu tiên nguồn lực.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Bài toán 4 — Diễn tiến cảm xúc theo thời gian cho từng hạng mục. */
    public static final String TASK4_VI = """
            Bài toán 4: Theo dõi cảm xúc theo thời gian cho từng hạng mục cứu trợ — RUN_ID=%s, TỪ %s → ĐẾN %s.
            Xác định: nhóm đang cải thiện, nhóm đang xấu đi, nhóm luôn tích cực, nhóm luôn tiêu cực. Suy đoán tác nhân vận hành.
            KHÔNG nói rằng thiếu dữ liệu; chỉ dựa vào bảng đính kèm.

            DỮ LIỆU (CSV: ngay,hang_muc,pos,neg,net):
            %s

            Yêu cầu:
            • 4 gạch đầu dòng: (cải thiện / xấu đi / luôn tích cực / luôn tiêu cực) — kèm giả thuyết nguyên nhân.
            • 1 hành động tiếp theo ưu tiên điều tra/giải quyết.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;
}