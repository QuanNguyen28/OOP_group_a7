package app.model.service.insight;

/** Kho template prompt cho phần tóm tắt bằng LLM (tiếng Việt). */
public final class Templates {
    private Templates() {}

    /** Tổng quan cảm xúc (Bài toán tổng thể). Có chèn DATA. */
    public static final String OVERALL_VI = """
            Bạn là nhà phân tích dữ liệu. Hãy tóm tắt xu hướng cảm xúc chung của công chúng cho bảng điều khiển ứng phó thảm họa.
            Tập trung vào số lượng tích cực/tiêu cực/trung tính và khoảng thời gian phân tích.

            RUN_ID: %s
            KHOẢNG THỜI GIAN: %s → %s

            DỮ LIỆU (CSV - ngày,pos,neg,neu):
            %s

            Yêu cầu đầu ra:
            • 3–5 gạch đầu dòng nêu phát hiện chính (xu hướng, đỉnh/đáy, lệch pha).
            • 1 khuyến nghị hành động cụ thể (ưu tiên, phân bổ, theo dõi thêm).
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Bài toán 2 — Thiệt hại phổ biến. Có chèn DATA. */
    public static final String DAMAGE_VI = """
            Tóm tắt các nhóm thiệt hại được công chúng đề cập nhiều nhất cho RUN_ID=%s.
            Nhóm chuẩn: Người bị ảnh hưởng; Gián đoạn kinh tế; Nhà/tòa nhà hư hỏng; Tài sản cá nhân mất; Cơ sở hạ tầng hư hỏng; Khác.

            DỮ LIỆU (CSV - danh_muc,so_luong):
            %s

            Yêu cầu:
            • 3 gạch đầu dòng: nhóm nào trội nhất, manh mối địa bàn/thời điểm nếu có (dựa trên dữ liệu), hệ quả vận hành.
            • 1 khuyến nghị ưu tiên phục hồi.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Hạng mục cứu trợ — mức độ đề cập/hiệu quả cảm nhận. Có chèn DATA. */
    public static final String RELIEF_VI = """
            Tóm tắt các hạng mục cứu trợ (tiền mặt, y tế, nhà ở, thực phẩm, giao thông) cho RUN_ID=%s.
            Mục tiêu: hạng mục nào được nhắc nhiều/ít, cảm nhận hiệu quả (tích cực/tiêu cực).

            DỮ LIỆU (CSV - hang_muc,so_luong):
            %s

            Yêu cầu:
            • 3 gạch đầu dòng: hạng mục nổi bật, bất cập, nguyên nhân suy đoán.
            • 1 khuyến nghị về điều phối/phân bổ.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Bài toán 3 — Hài lòng vs. không hài lòng theo hạng mục. Có chèn DATA. */
    public static final String TASK3_VI = """
            Bài toán 3: Đánh giá mức độ hài lòng/không hài lòng theo hạng mục cứu trợ cho RUN_ID=%s.
            Nhấn mạnh: hạng mục nào tiêu cực cao nhất (nhu cầu chưa đáp ứng), hạng mục nào tích cực cao nhất (phân phối hiệu quả).

            DỮ LIỆU (CSV - hang_muc,pos,neg,net):
            %s

            Yêu cầu:
            • 4 gạch đầu dòng: 2 điểm nóng tiêu cực + 2 điểm sáng tích cực (nêu lý do suy đoán).
            • 1 lời khuyên ưu tiên nguồn lực.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;

    /** Bài toán 4 — Diễn tiến cảm xúc theo thời gian cho từng hạng mục. Có chèn DATA. */
    public static final String TASK4_VI = """
            Bài toán 4: Theo dõi cảm xúc theo thời gian cho từng hạng mục cứu trợ — RUN_ID=%s, TỪ %s → ĐẾN %s.
            Xác định: nhóm đang cải thiện, nhóm đang xấu đi, nhóm luôn tích cực, nhóm luôn tiêu cực. Suy đoán tác nhân vận hành.

            DỮ LIỆU (CSV - ngay,hang_muc,pos,neg,net):
            %s

            Yêu cầu:
            • 4 gạch đầu dòng: (cải thiện / xấu đi / luôn tích cực / luôn tiêu cực) — kèm giả thuyết nguyên nhân.
            • 1 hành động tiếp theo ưu tiên điều tra/giải quyết.
            Trả lời ngắn gọn bằng tiếng Việt.
            """;
}