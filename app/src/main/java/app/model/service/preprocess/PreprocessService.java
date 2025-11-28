package app.model.service.preprocess;

import app.model.domain.CleanPost;
import app.model.domain.RawPost;

public interface PreprocessService {
    CleanPost preprocess(RawPost raw);
}