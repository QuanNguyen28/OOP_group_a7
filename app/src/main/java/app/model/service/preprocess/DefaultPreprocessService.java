package app.model.service.preprocess;

import app.model.domain.CleanPost;
import app.model.domain.RawPost;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Optional;

public class DefaultPreprocessService implements PreprocessService {
    @Override
    public CleanPost preprocess(RawPost r) {
        String text = (r.text()==null ? "" : r.text()).trim();
        text = text.replaceAll("https?://\\S+","");     // bỏ URL
        text = stripDiacritics(text).toLowerCase();
        Instant ts = Instant.parse(r.createdAt());
        return new CleanPost(r.id(), text, r.lang()==null?"und":r.lang(), ts, Optional.ofNullable(r.userLoc()));
    }
    private static String stripDiacritics(String s){
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+","");
    }
}